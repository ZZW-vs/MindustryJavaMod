package zzw.util;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.TextureAtlas.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.graphics.*;

import java.io.*;
import java.util.*;

/**
 * Wavefront Object (.obj) GPU 3D 渲染器
 *
 * ★ 直接屏幕渲染版: 无 FBO, 直接渲染到屏幕深度缓冲
 * - 优势: 全屏幕分辨率, 无 FBO 锯齿/性能开销, 完美支持Y轴旋转
 * - 使用正交投影匹配2D相机 + 自定义 Shader (方向光照 + 纹理采样)
 * - OBJ 加载时构建 Mesh (position3 + normal + color + texCoords2), GPU 自动处理深度
 * - 支持材质纹理 (map_Kd) 和顶点颜色
 *
 * 公共 API 与旧版保持兼容 (字段/方法/内部类不变)
 *
 * @author EyeOfDarkness (原 CPU 软件渲染器)
 * @author 郑zip (GPU 深度缓冲重写 → 直接屏幕渲染)
 */
public class WavefrontObject{
    protected static final float zScale = 0.01f;
    protected static final float defaultScl = 4f;
    protected static final float perspectiveDistance = 350f;

    /** 每顶点浮点数: pos(3) + normal(3) + color(1 packed) + uv(2) = 9 */
    private static final int FLOATS_PER_VERT = 9;

    // ===== 静态 GPU 资源 (延迟初始化, 所有实例共享) =====
    private static Mat3D transform = new Mat3D();
    private static Mat3D projMat = new Mat3D();
    private static ObjShader shader;
    private static boolean initialized = false;
    private static boolean shaderFailed = false;

    // ===== Mesh 数据 (GPU 缓冲) =====
    /** ★ 多材质 Mesh 组: 按贴图分组, 每组独立 Mesh + Texture, 支持 MMD 等多贴图模型 */
    public static class MeshGroup{
        public Mesh mesh;
        public int vertFloatCount;
        public float[] originalVerts;   // CPU 顶点副本 (用于 cons 形变)
        public float[] distortData;     // applyDistortion 复用数组
        public Texture texture;         // 独立 Texture (null = 无贴图, 用顶点色)
        public boolean hasTexture;
        public boolean transparent;     // 是否含透明材质 (alpha<1), 透明组后渲染且不写入深度
        public boolean doubleSided;     // 是否双面渲染 (true=禁用背面剔除)
    }
    public Seq<MeshGroup> meshGroups = new Seq<>();
    /** 兼容旧 API: 指向第一个 MeshGroup (可能为 null) */
    private Mesh mesh;
    private int numIndices;
    public float boundRadius;       // 模型边界球半径 (模型空间, 未缩放)
    private float[] originalVerts;   // CPU 顶点副本 (用于 cons 形变)
    private int vertFloatCount;      // 顶点浮点数总数 (每顶点 9 float)
    private AtlasRegion diffTexture = null;  // 材质 diffuse 纹理 (map_Kd)
    private boolean hasDiffTexture = false;

    // ===== 性能优化 (可复用缓冲) =====
    private float[] distortData;     // applyDistortion 复用数组, 避免每帧 GC

    // ===== OBJ 数据 (保持公共 API 兼容) =====
    public Seq<Vec3> vertices = new Seq<>();
    public Seq<Vec2> uvs = new Seq<>();
    public Seq<Vec3> normals = new Seq<>();
    public Seq<Face> faces = new Seq<>();
    public String textureName = "";
    public ObjectMap<String, Material> materials;
    private final Seq<Vertex> drawnVertices = new Seq<>();
    private final Seq<Vec3> drawnNormals = new Seq<>();
    private AtlasRegion texture = null;
    private boolean hasMaterial = false;
    private boolean hasNormal = false;
    private boolean hasTexture = false;
    private boolean hasMaterialTex = false;
    private boolean odd = false;

    // ===== 渲染配置 (公共 API) =====
    public ShadingType shadingType = ShadingType.normalAngle;
    public Color lightColor = Color.white;
    public Color shadeColor = Color.black;
    public float size = 1f;
    public float shadingSmoothness = 2.8f;
    public float drawLayer = Layer.blockBuilding;
    /** ★ 最大暗化程度 (0~1) */
    public float maxShade = 0.75f;
    /** 是否启用 GPU 背面剔除 (true=剔除背面, false=双面渲染) */
    public boolean cullBackfaces = true;
    /** 保留 API 兼容 (GPU 深度缓冲自动处理, 不再需要) */
    public boolean singleZLayer = false;
    /** 实例间 Z 轴偏移 (用于 Draw.z 层级区分) */
    public float zOffset = 0f;
    protected int indexerA;
    protected float indexerZ;

    // ===== 初始化 =====
    private static void init(){
        if(initialized) return;
        initialized = true;
        try{
            shader = new ObjShader();
        }catch(Throwable t){
            Log.err("[Create] WavefrontObject shader compile failed, GPU rendering disabled", t);
            shaderFailed = true;
        }
    }

    // ===== OBJ 加载 (解析 + 构建 Mesh) =====
    public void load(Fi file, @Nullable Fi material){
        if(material != null){
            BufferedReader matR = material.reader(64);
            Material current = null;
            while(true){
                try{
                    String line = matR.readLine();
                    if(line == null) break;
                    if(line.startsWith("#")) continue;

                    if(line.startsWith("newmtl ")){
                        current = new Material();
                        current.name = line.replaceFirst("newmtl ", "");

                        if(materials == null) materials = new ObjectMap<>();
                        materials.put(current.name, current);
                        hasMaterial = true;
                    }

                    if(line.startsWith("Ka ") && current != null){
                        String[] val = line.replaceFirst("Ka ", "").split("\\s+");
                        float[] col = new float[3];
                        if(val.length != 3) throw new IllegalStateException("'Ka' must be followed with 3 arguments. Required: [r, g, b], found: " + Arrays.toString(val));
                        for(int i = 0; i < 3; i++){
                            col[i] = Strings.parseFloat(val[i], 0f);
                        }
                        Tmp.c1.set(col[0], col[1], col[2]).a(1f);
                        current.ambientCol = Tmp.c1.rgba8888();
                        if(!Tmp.c1.equals(Color.white)){
                            current.hasColor = true;
                        }
                    }

                    if(line.startsWith("Kd ") && current != null){
                        String[] val = line.replaceFirst("Kd ", "").split("\\s+");
                        float[] col = new float[3];
                        if(val.length != 3) throw new IllegalStateException("'Kd' must be followed with 3 arguments. Required: [r, g, b], found: " + Arrays.toString(val));
                        for(int i = 0; i < 3; i++){
                            col[i] = Strings.parseFloat(val[i], 0f);
                        }
                        Tmp.c1.set(col[0], col[1], col[2]).a(1f);
                        current.diffuseCol = Tmp.c1.rgba8888();
                        if(!Tmp.c1.equals(Color.white)){
                            current.hasColor = true;
                        }
                    }

                    if(line.startsWith("Ke ") && current != null){
                        String[] val = line.replaceFirst("Ke ", "").split("\\s+");
                        float[] col = new float[3];
                        if(val.length != 3) throw new IllegalStateException("'Ke' must be followed with 3 arguments. Required: [r, g, b], found: " + Arrays.toString(val));
                        for(int i = 0; i < 3; i++){
                            col[i] = Strings.parseFloat(val[i], 0f);
                        }
                        Tmp.c1.set(col[0], col[1], col[2]).a(1f);
                        current.emitCol = Tmp.c1.rgba8888();
                        if(!Tmp.c1.equals(Color.black)){
                            current.hasColor = true;
                        }
                    }

                    if(line.contains("map_Kd ") && current != null){
                        hasTexture = true;
                        hasMaterialTex = true;
                        String n = line.replaceFirst("map_Kd ", "").trim();
                        // ★ 优先用 atlas 查找 (兼容旧模型), 找不到则记录文件名延迟加载独立 Texture
                        if(canLoadTex()){
                            current.diffTex = Core.atlas.find("create-" + n);
                            if(!current.diffTex.found()){
                                current.diffTex = null;
                            }
                        }
                        // ★ 记录贴图文件名, buildMesh 时尝试从文件加载独立 Texture (MMD 大贴图)
                        current.diffTexName = n;
                    }

                    // ★ 解析 d 值 (透明度): MMD/Blender 导出的 d=0 通常是未设置而非真透明
                    if((line.startsWith("d ") || line.startsWith("d\t")) && current != null){
                        String val = line.replaceFirst("d\\s+", "").trim();
                        current.alpha = Strings.parseFloat(val, 1f);
                    }

                    if(line.contains("map_Ke ") && current != null && canLoadTex()){
                        String n = line.replaceFirst("map_Ke ", "").trim();
                        current.emitTex = Core.atlas.find("create-" + n);
                    }
                }catch(Throwable e){
                    throw new RuntimeException(e);
                }
            }
        }

        BufferedReader reader = file.reader(64);
        Material current = null;
        while(true){
            try{
                String line = reader.readLine();
                if(line == null) break;
                if(line.startsWith("#")) continue;
                if(line.trim().isEmpty()) continue;

                if(line.startsWith("v ")){
                    String[] pos = line.replaceFirst("v ", "").split("\\s+");
                    if(pos.length != 3) throw new IllegalStateException("'v' must define all 3 vector points");

                    float[] vec = new float[3];
                    for(int i = 0; i < 3; i++){
                        vec[i] = Strings.parseFloat(pos[i], 0f);
                    }

                    drawnVertices.add(new Vertex(vec[0], vec[1], vec[2]));
                    vertices.add(new Vec3(vec[0], vec[1], vec[2]));
                }

                if(line.startsWith("vt ")){
                    if(!hasTexture) hasTexture = true;
                    String[] pos = line.replaceFirst("vt ", "").split("\\s+");
                    Vec2 uv = new Vec2();
                    uv.x = Strings.parseFloat(pos[0], 0f);
                    uv.y = Strings.parseFloat(pos[1], 0f);
                    uvs.add(uv);
                }

                if(line.startsWith("vn ")){
                    if(!hasNormal) hasNormal = true;
                    String[] pos = line.replaceFirst("vn ", "").split("\\s+");
                    if(pos.length != 3) throw new IllegalStateException("'vn' must define all 3 vector points");

                    float[] vec = new float[3];
                    for(int i = 0; i < 3; i++){
                        vec[i] = Strings.parseFloat(pos[i], 0f);
                    }

                    drawnNormals.add(new Vec3(vec[0], vec[1], vec[2]));
                    normals.add(new Vec3(vec[0], vec[1], vec[2]));
                }

                if(hasMaterial && line.startsWith("usemtl ")){
                    String key = line.replace("usemtl ", "");
                    current = materials.get(key);
                }

                if(line.startsWith("f ")){
                    String[] segments = line.replace("f ", "").split("\\s+");
                    Face face = new Face();
                    face.verts = new Vertex[segments.length];
                    if(hasNormal) face.normal = new Vec3[segments.length];
                    if(hasTexture) face.vertexTexture = new Vec2[segments.length];
                    if(hasMaterial && current != null) face.mat = current;
                    if(segments.length != 4) odd = true;

                    int[] i = {0};
                    for(String segment : segments){
                        String[] faceIndex = segment.split("/");
                        Vertex vert = drawnVertices.get(getFaceVal(faceIndex[0]));
                        face.verts[i[0]] = vert;
                        if(hasNormal && faceIndex.length > 2 && !faceIndex[2].isEmpty()){
                            face.normal[i[0]] = drawnNormals.get(getFaceVal(faceIndex[2]));
                        }
                        if(hasTexture && faceIndex.length > 1 && !faceIndex[1].isEmpty()){
                            face.vertexTexture[i[0]] = uvs.get(getFaceVal(faceIndex[1]));
                        }

                        for(int sign : Mathf.signs){
                            Vertex v = drawnVertices.get(faceVertIndex(segments[Mathf.mod(sign + i[0], segments.length)]));
                            if(!face.verts[i[0]].neighbors.contains(v)){
                                face.verts[i[0]].neighbors.add(v);
                            }
                        }
                        face.size += 6;
                        i[0]++;
                    }

                    face.data = new float[face.size];

                    i[0] = 0;
                    for(Vertex vt : face.verts){
                        vt.neighbors.each(vs -> {
                            for(Vertex vc : face.verts){
                                if(vs == vc) return true;
                            }
                            return false;
                        }, vs -> {
                            face.shadingValue += vt.source.dst(vs.source);
                            i[0]++;
                        });
                    }

                    face.shadingValue /= i[0];
                    faces.add(face);
                }
            }catch(Throwable e){
                throw new RuntimeException(e);
            }
        }
        if(canLoadTex()){
            texture = Core.atlas.find("create-" + textureName + "-tex");
        }

        if(hasTexture && texture != null && !texture.found()){
            Log.warn("[Create] WavefrontObject: texture 'create-@-tex' not found, disabling texture rendering", textureName);
            hasTexture = false;
            texture = null;
        }

        // ★ 构建 GPU Mesh
        buildMesh();

        Log.info("[Create] WavefrontObject loaded: " + drawnVertices.size + " verts, " + faces.size + " faces, GPU mesh: " + (mesh != null ? "OK" : "FAILED") + ", texture: " + (hasDiffTexture ? "YES" : "NO"));
    }

    private boolean canLoadTex(){
        return !Vars.headless && Core.atlas != null && hasTexture;
    }

    // ===== 构建 GPU Mesh =====
    /** 将 OBJ 面数据构建为 arc Mesh (position3 + normal + color + texCoords2), 三角化后上传 GPU */
    private void buildMesh(){
        if(Vars.headless || faces.isEmpty()) return;

        // 计算边界框 (用于居中和相机定位)
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for(Vec3 v : vertices){
            minX = Math.min(minX, v.x); minY = Math.min(minY, v.y); minZ = Math.min(minZ, v.z);
            maxX = Math.max(maxX, v.x); maxY = Math.max(maxY, v.y); maxZ = Math.max(maxZ, v.z);
        }
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;

        // 边界球半径 (居中后)
        float maxR = 0f;
        for(Vec3 v : vertices){
            float dx = v.x - cx, dy = v.y - cy, dz = v.z - cz;
            float r = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
            if(r > maxR) maxR = r;
        }
        boundRadius = Math.max(maxR, 0.1f);

        // ★ 回退: 使用 textureName 查找的 texture (兼容旧模型无 mtl 但有贴图的情况)
        if(materials == null || materials.isEmpty()){
            if(texture != null && texture.found()){
                diffTexture = texture;
                hasDiffTexture = true;
            }
        }

        // ★ 按贴图分组面: 同一贴图的面合并到一个 MeshGroup
        // 贴图 key: 优先用 atlas region, 其次独立 Texture, 最后无贴图
        ObjectMap<String, Seq<Face>> faceGroups = new ObjectMap<>();
        ObjectMap<String, Texture> groupTextures = new ObjectMap<>();
        ObjectMap<String, AtlasRegion> groupAtlasRegions = new ObjectMap<>();
        ObjectMap<String, Boolean> groupTransparent = new ObjectMap<>();

        for(Face f : faces){
            String texKey = "__no_texture__";
            boolean isTransparent = false;
            if(f.mat != null){
                // 优先 atlas region
                if(f.mat.diffTex != null && f.mat.diffTex.found()){
                    texKey = "atlas:" + f.mat.diffTex.name;
                    groupAtlasRegions.put(texKey, f.mat.diffTex);
                }
                // 其次独立 Texture (延迟加载, buildMesh 时尝试加载)
                else if(f.mat.diffTexName != null && !f.mat.diffTexName.isEmpty()){
                    texKey = "file:" + f.mat.diffTexName;
                    // ★ 延迟加载独立 Texture (从 mod 文件树查找)
                    if(!groupTextures.containsKey(texKey)){
                        Texture tex = loadIndependentTexture(f.mat.diffTexName);
                        if(tex != null){
                            groupTextures.put(texKey, tex);
                        }else{
                            texKey = "__no_texture__";  // 加载失败, 回退到无贴图
                        }
                    }
                }
                // ★ 检查材质透明度: d<1 视为透明
                if(f.mat.alpha < 0.99f){
                    isTransparent = true;
                }
            }
            // 同组任一面透明则整组透明
            Boolean cur = groupTransparent.get(texKey);
            if(cur == null || isTransparent){
                groupTransparent.put(texKey, isTransparent);
            }
            Seq<Face> group = faceGroups.get(texKey);
            if(group == null){
                group = new Seq<>();
                faceGroups.put(texKey, group);
            }
            group.add(f);
        }

        // ★ 兼容旧 API: 设置 diffTexture/hasDiffTexture (取第一个 atlas 贴图)
        if(materials != null){
            for(Material mat : materials.values()){
                if(mat.diffTex != null && mat.diffTex.found()){
                    diffTexture = mat.diffTex;
                    hasDiffTexture = true;
                    break;
                }
            }
        }

        // ★ 为每个贴图分组构建独立 Mesh
        int totalVerts = 0;
        for(ObjectMap.Entry<String, Seq<Face>> entry : faceGroups.entries()){
            Seq<Face> groupFaces = entry.value;
            String texKey = entry.key;

            // 三角化面计数
            int totalTris = 0;
            for(Face f : groupFaces){
                totalTris += f.verts.length - 2;
            }
            int vertCount = totalTris * 3;
            if(vertCount == 0) continue;

            int groupVertFloatCount = vertCount * FLOATS_PER_VERT;
            float[] vertData = new float[groupVertFloatCount];

            // 确定该组的贴图
            AtlasRegion atlasReg = groupAtlasRegions.get(texKey);
            Texture indepTex = groupTextures.get(texKey);
            boolean useAtlas = atlasReg != null && atlasReg.found();
            boolean useIndep = indepTex != null;

            int vi = 0;
            for(Face f : groupFaces){
                // 面法线
                Vec3 faceNormal = Tmp.v31.setZero();
                if(hasNormal && f.normal != null && f.normal.length > 0){
                    for(Vec3 n : f.normal) faceNormal.add(n);
                    faceNormal.scl(1f / f.normal.length).nor();
                }else{
                    Vec3 v0 = f.verts[0].source;
                    Vec3 v1 = f.verts[1].source;
                    Vec3 v2 = f.verts[2].source;
                    faceNormal.set(v1.x - v0.x, v1.y - v0.y, v1.z - v0.z)
                        .crs(v2.x - v0.x, v2.y - v0.y, v2.z - v0.z).nor();
                }

                // 面颜色 + alpha (材质 Kd + d 透明度)
                float packedColor;
                if(f.mat != null && f.mat.hasColor){
                    Tmp.c1.rgba8888(f.mat.diffuseCol);
                    Tmp.c1.a = f.mat.alpha;  // ★ 应用 d 透明度
                    packedColor = Tmp.c1.toFloatBits();
                }else{
                    packedColor = Color.whiteFloatBits;
                }

                // 扇形三角化
                for(int t = 0; t < f.verts.length - 2; t++){
                    int[] idx = {0, t + 1, t + 2};
                    for(int vIdx : idx){
                        Vertex vert = f.verts[vIdx];
                        Vec3 pos = vert.source;
                        Vec3 norm;
                        if(hasNormal && f.normal != null && vIdx < f.normal.length && f.normal[vIdx] != null){
                            norm = f.normal[vIdx];
                        }else{
                            norm = faceNormal;
                        }

                        float u = 0f, v = 0f;
                        if(hasTexture && f.vertexTexture != null && vIdx < f.vertexTexture.length && f.vertexTexture[vIdx] != null){
                            u = f.vertexTexture[vIdx].x;
                            v = f.vertexTexture[vIdx].y;
                        }

                        // ★ UV 映射:
                        // atlas 贴图: UV[0,1] → atlas region UV (U线性, V翻转)
                        // 独立 Texture: UV[0,1] → [0,1] 直接映射 (V翻转, OBJ V轴朝上 OpenGL V朝下)
                        if(useAtlas && u >= 0f && u <= 1f && v >= 0f && v <= 1f){
                            u = Mathf.lerp(atlasReg.u, atlasReg.u2, u);
                            v = Mathf.lerp(atlasReg.v2, atlasReg.v, v);
                        }else if(useIndep){
                            // 独立 Texture: V 翻转 (OBJ V朝上, OpenGL V朝下)
                            v = 1f - v;
                        }

                        vertData[vi]     = pos.x - cx;
                        vertData[vi + 1] = pos.y - cy;
                        vertData[vi + 2] = pos.z - cz;
                        vertData[vi + 3] = norm.x;
                        vertData[vi + 4] = norm.y;
                        vertData[vi + 5] = norm.z;
                        vertData[vi + 6] = packedColor;
                        vertData[vi + 7] = u;
                        vertData[vi + 8] = v;
                        vi += FLOATS_PER_VERT;
                    }
                }
            }

            // 创建 MeshGroup
            try{
                Mesh m = new Mesh(false, vertCount, 0,
                    VertexAttribute.position3,
                    VertexAttribute.normal,
                    VertexAttribute.color,
                    VertexAttribute.texCoords
                );
                m.setVertices(vertData, 0, groupVertFloatCount);

                MeshGroup mg = new MeshGroup();
                mg.mesh = m;
                mg.vertFloatCount = groupVertFloatCount;
                mg.originalVerts = new float[groupVertFloatCount];
                System.arraycopy(vertData, 0, mg.originalVerts, 0, groupVertFloatCount);
                mg.distortData = new float[groupVertFloatCount];
                mg.texture = useIndep ? indepTex : null;
                mg.hasTexture = useIndep || useAtlas;
                mg.transparent = groupTransparent.get(texKey, false);
                mg.doubleSided = !cullBackfaces;  // OBJ 模型: 由全局 cullBackfaces 决定
                meshGroups.add(mg);

                totalVerts += vertCount;
            }catch(Throwable t){
                Log.err("[Create] WavefrontObject mesh creation failed for group '" + texKey + "' in '" + textureName + "'", t);
            }
        }

        // ★ 按 transparent 排序: 不透明组(false)在前, 透明组(true)在后
        // 渲染时先画不透明组写入深度, 再画透明组不写入深度, 避免透明面遮挡后面的不透明面
        meshGroups.sort((a, b) -> Boolean.compare(a.transparent, b.transparent));

        // ★ 兼容旧 API: mesh 指向第一个 MeshGroup
        if(!meshGroups.isEmpty()){
            mesh = meshGroups.first().mesh;
            originalVerts = meshGroups.first().originalVerts;
            vertFloatCount = meshGroups.first().vertFloatCount;
            distortData = meshGroups.first().distortData;
        }

        if(totalVerts >= 65535){
            Log.warn("[Create] WavefrontObject '@' has @ verts (>65535), using non-indexed mesh", textureName, totalVerts);
        }

        Log.info("[Create] WavefrontObject loaded: " + drawnVertices.size + " verts, " + faces.size + " faces, " + meshGroups.size + " mesh groups, " + totalVerts + " GPU verts");
    }

    /** ★ 从 mod 文件树加载独立 Texture (不通过 atlas, 用于 MMD 大贴图) */
    private Texture loadIndependentTexture(String name){
        if(Vars.headless) return null;
        // 尝试多种路径: 直接文件名、objects/目录、mtl 同目录
        String[] paths = {name, "objects/" + name, "blander/text_g/" + name, "blander/" + name};
        for(String path : paths){
            Fi fi = Vars.tree.get(path);
            if(fi.exists()){
                try{
                    Texture tex = new Texture(fi);
                    tex.setFilter(Texture.TextureFilter.linear, Texture.TextureFilter.linear);
                    Log.info("[Create] Loaded independent texture: " + path + " (" + tex.width + "x" + tex.height + ")");
                    return tex;
                }catch(Throwable t){
                    Log.err("[Create] Failed to load texture: " + path, t);
                }
            }
        }
        Log.warn("[Create] Independent texture not found: " + name);
        return null;
    }

    // ===== 渲染 =====
    public void draw(float x, float y, float rX, float rY, float rZ){
        draw(x, y, rX, rY, rZ, null);
    }

    public void draw(float x, float y, float rX, float rY, float rZ, Cons<Vec3> cons){
        if(meshGroups.isEmpty() || faces.isEmpty()) return;
        if(Vars.headless) return;

        init();
        if(shaderFailed || shader == null) return;

        // ★ 捕获调用时的参数值 — Draw.draw() 延迟执行,
        // 调用者 (如 ObjDisplayBlock) 会在 draw() 返回后恢复 obj 的字段,
        // 所以不能在 lambda 内读 this 的字段, 必须捕获当前值
        // ★ 必须用 cpy() 创建副本: 多个 DisplayBuild 共享同一 ZObjs 静态实例,
        // 实例字段会被后续调用覆盖, 导致延迟 lambda 读到错误值
        final float capturedSize = size;
        final Color capturedLight = lightColor.cpy();
        final Color capturedShade = shadeColor.cpy();
        final float capturedMaxShade = maxShade;
        final float capturedZOffset = zOffset;

        // ★ 捕获相机视界 (Draw.draw 延迟执行时相机可能变化)
        Rect bounds = Core.camera.bounds(Tmp.r1);
        final float camLeft = bounds.x;
        final float camRight = bounds.x + bounds.width;
        final float camBottom = bounds.y;
        final float camTop = bounds.y + bounds.height;

        // ★ 使用 Draw.draw() 参与 SortedSpriteBatch 的排序管线
        // 直接渲染到屏幕深度缓冲, 无需 FBO
        Draw.draw(drawLayer + capturedZOffset, () -> {
            // 处理顶点形变 (cons)
            if(cons != null){
                applyDistortion(cons);
            }

            float scl = defaultScl * capturedSize;
            float worldSize = boundRadius * 2f * scl;

            // ★ 视锥裁剪: 模型不在屏幕内则跳过
            if(x + worldSize < camLeft || x - worldSize > camRight
                || y + worldSize < camBottom || y - worldSize > camTop){
                if(cons != null) restoreVertices();
                return;
            }

            // ★ 动态 Z 范围: 根据模型实际大小收紧, 最大化深度缓冲精度, 消除 z-fighting
            float zRange = Math.max(boundRadius * scl, 10f);
            projMat.setToOrtho(camLeft, camRight, camBottom, camTop, -zRange, zRange);

            // ★★★ 核心修复: 反转投影矩阵 Z 轴方向 ★★★
            // 2D 渲染中 Z 朝向相机 (z 越大越靠前), 但 setToOrtho 默认 OpenGL 约定 (z 越大越远)
            // 不反转的话, 模型后部面 (z<0) 深度值更小 (更"近"), 会遮挡前部面 → 透视假象
            // 反转 Z 行后: z>0 (前部) → 深度 0 (最近), z<0 (后部) → 深度 1 (最远), 配合 GL_LESS 正确剔除
            projMat.val[10] = -projMat.val[10];
            projMat.val[14] = -projMat.val[14];

            // ★ 模型变换矩阵: 平移到世界位置 + 旋转 + 缩放
            transform.idt();
            transform.translate(x, y, 0);
            transform.rotate(Vec3.Z, rZ);
            transform.rotate(Vec3.Y, rY);
            transform.rotate(Vec3.X, rX);
            transform.scale(scl, scl, scl);

            Draw.flush();

            // ★ GL 状态: alpha 混合 + 深度测试
            Gl.enable(Gl.blend);
            Gl.blendFunc(Gl.srcAlpha, Gl.oneMinusSrcAlpha);
            Gl.enable(Gl.depthTest);
            Gl.clear(Gl.depthBufferBit);

            // ★ 渲染: 每材质独立处理双面/剔除, 不透明先写深度, 透明后不写深度
            try{
                shader.bind();
                shader.setUniformMatrix4("u_proj", projMat.val);
                shader.setUniformMatrix4("u_trans", transform.val);
                setLightingUniformsCaptured(capturedLight, capturedShade, capturedMaxShade);

                // ★ 第一遍: 不透明组 (写入深度缓冲)
                Gl.depthMask(true);
                for(MeshGroup mg : meshGroups){
                    if(mg == null || mg.mesh == null || mg.transparent) continue;
                    setCullState(mg.doubleSided);
                    renderMeshGroup(mg);
                }

                // ★ 第二遍: 透明组 (只测试深度不写入, 避免透明面遮挡后面的面)
                Gl.depthMask(false);
                for(MeshGroup mg : meshGroups){
                    if(mg == null || mg.mesh == null || !mg.transparent) continue;
                    setCullState(mg.doubleSided);
                    renderMeshGroup(mg);
                }
                Gl.depthMask(true);
            }catch(Throwable t){
                Log.err("[Create] WavefrontObject render error", t);
            }

            // 恢复 GL 状态
            Gl.disable(Gl.cullFace);
            Gl.depthMask(false);
            Gl.disable(Gl.depthTest);
            Gl.enable(Gl.blend);
            Gl.blendFunc(Gl.srcAlpha, Gl.oneMinusSrcAlpha);

            // 恢复顶点 (如果有形变)
            if(cons != null){
                restoreVertices();
            }
        });
    }

    /** 设置背面剔除状态 (每材质独立) */
    private void setCullState(boolean doubleSided){
        if(doubleSided){
            Gl.disable(Gl.cullFace);
        }else{
            Gl.enable(Gl.cullFace);
            Gl.cullFace(Gl.back);
        }
    }

    /** 渲染单个 MeshGroup: bind 贴图后 render mesh */
    private void renderMeshGroup(MeshGroup mg){
        if(mg.hasTexture){
            if(mg.texture != null){
                // 独立 Texture (MMD 大贴图)
                mg.texture.bind(0);
                shader.setUniformi("u_texture", 0);
                shader.setUniformi("u_hasTexture", 1);
            }else if(hasDiffTexture && diffTexture != null && diffTexture.found()){
                // atlas 贴图
                diffTexture.texture.bind();
                shader.setUniformi("u_texture", 0);
                shader.setUniformi("u_hasTexture", 1);
            }else{
                shader.setUniformi("u_hasTexture", 0);
            }
        }else{
            shader.setUniformi("u_hasTexture", 0);
        }
        shader.apply();
        mg.mesh.render(shader, Gl.triangles);
    }

    /** 使用捕获的光照参数设置 uniform (用于 Draw.draw 延迟执行) */
    private void setLightingUniformsCaptured(Color light, Color shade, float maxSh){
        Vec3 lightDir;
        switch(shadingType){
            case topLight:
                lightDir = Vec3.Y;
                break;
            case normalAngle:
            case zMedian:
            case zDistance:
                lightDir = Vec3.Z;
                break;
            case noShading:
            default:
                lightDir = Vec3.Y;
                break;
        }
        shader.setUniformf("u_lightDir", lightDir.x, lightDir.y, lightDir.z);
        shader.setUniformf("u_lightColor", light.r, light.g, light.b);
        shader.setUniformf("u_shadeColor", shade.r, shade.g, shade.b);
        shader.setUniformf("u_maxShade", shadingType == ShadingType.noShading ? 0f : maxSh);
    }

    /** 设置着色器光照 uniform (基于 shadingType) */
    private void setLightingUniforms(){
        Vec3 lightDir;
        switch(shadingType){
            case topLight:
                lightDir = Vec3.Y;  // 光从上方照射
                break;
            case normalAngle:
            case zMedian:
            case zDistance:
                lightDir = Vec3.Z;  // 光从屏幕方向照射 (朝向相机)
                break;
            case noShading:
            default:
                lightDir = Vec3.Y;
                break;
        }
        shader.setUniformf("u_lightDir", lightDir.x, lightDir.y, lightDir.z);
        shader.setUniformf("u_lightColor", lightColor.r, lightColor.g, lightColor.b);
        shader.setUniformf("u_shadeColor", shadeColor.r, shadeColor.g, shadeColor.b);
        shader.setUniformf("u_maxShade", shadingType == ShadingType.noShading ? 0f : maxShade);
    }

    /** 应用顶点形变 (cons 回调), 复用 distortData 数组避免 GC */
    private void applyDistortion(Cons<Vec3> cons){
        Vec3 v = Tmp.v31;
        for(MeshGroup mg : meshGroups){
            if(mg == null || mg.mesh == null) continue;
            System.arraycopy(mg.originalVerts, 0, mg.distortData, 0, mg.vertFloatCount);
            for(int i = 0; i < mg.vertFloatCount; i += FLOATS_PER_VERT){
                v.set(mg.distortData[i], mg.distortData[i + 1], mg.distortData[i + 2]);
                cons.get(v);
                mg.distortData[i] = v.x;
                mg.distortData[i + 1] = v.y;
                mg.distortData[i + 2] = v.z;
            }
            mg.mesh.setVertices(mg.distortData, 0, mg.vertFloatCount);
        }
    }

    /** 恢复原始顶点 */
    private void restoreVertices(){
        for(MeshGroup mg : meshGroups){
            if(mg == null || mg.mesh == null) continue;
            mg.mesh.setVertices(mg.originalVerts, 0, mg.vertFloatCount);
        }
    }

    protected static int faceVertIndex(String node){
        return getFaceVal(node.split("/")[0]);
    }

    protected static int getFaceVal(String value){
        return Strings.parseInt(value, 1) - 1;
    }

    @Override
    public String toString(){
        return "WavefrontObject{" +
        "vertices=" + vertices.size +
        ", faces=" + faces.size +
        ", shadingType=" + shadingType +
        ", gpuMesh=" + (mesh != null) +
        ", texture=" + (hasDiffTexture ? diffTexture.name : "none") +
        '}';
    }

    // ===== 着色器类 =====
    public static class ObjShader extends Shader{
        public ObjShader(){
            super(Vars.tree.get("shaders/obj3d.vert"), Vars.tree.get("shaders/obj3d.frag"));
        }
    }

    // ===== 内部类 (保持 API 兼容) =====
    public class Face{
        public Material mat;
        public Vertex[] verts;
        public Vec3[] normal;
        public Vec2[] vertexTexture;
        public float shadingValue = 0f;
        public int size = 0;
        public float[] data;

        protected void draw(){
            // 旧版 CPU 渲染 (已弃用, 保留 API 兼容)
        }
    }

    public static class Vertex{
        public Vec3 source;
        public Seq<Vertex> neighbors = new Seq<>();

        public Vertex(float x, float y, float z){
            source = new Vec3(x, y, z);
        }
    }

    public static class Material{
        public String name;
        public int ambientCol = 0xffffffff, diffuseCol = 0xffffffff, emitCol = 0x00000000;
        public boolean hasColor = false;
        public AtlasRegion diffTex, emitTex;
        /** ★ 独立 Texture (不通过 atlas, 用于 MMD 等大贴图模型) */
        public Texture diffTexture;
        /** ★ 透明度 (d 值, 0=透明 1=不透明) */
        public float alpha = 1f;
        /** ★ 贴图文件名 (map_Kd 行的值, 延迟加载) */
        public String diffTexName;
    }

    public enum ShadingType{
        zMedian,
        zDistance,
        normalAngle,
        topLight,
        noShading
    }
}
