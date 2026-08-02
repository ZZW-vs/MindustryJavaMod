package zzw.util;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.TextureAtlas.*;
import arc.graphics.g3d.*;
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
 * ★ 完全重写版: 使用 GPU 深度缓冲 + FrameBuffer 离屏渲染
 * - 优势: 完美支持任意3D模型, 无面排序/z-fighting问题, 支持Y轴旋转
 * - 使用 Camera3D 透视投影 + 自定义 Shader (方向光照 + 纹理采样)
 * - OBJ 加载时构建 Mesh (position3 + normal + color + texCoords2), GPU 自动处理深度
 * - 支持材质纹理 (map_Kd) 和顶点颜色
 *
 * 公共 API 与旧版保持兼容 (字段/方法/内部类不变)
 *
 * @author EyeOfDarkness (原 CPU 软件渲染器)
 * @author 郑zip (GPU 深度缓冲重写)
 */
public class WavefrontObject{
    protected static final float zScale = 0.01f;
    protected static final float defaultScl = 4f;
    protected static final float perspectiveDistance = 350f;

    /** 每顶点浮点数: pos(3) + normal(3) + color(1 packed) + uv(2) = 9 */
    private static final int FLOATS_PER_VERT = 9;

    // ===== 静态 GPU 资源 (延迟初始化, 所有实例共享) =====
    private static Camera3D cam;
    private static FrameBuffer buffer;
    private static Mat3D transform = new Mat3D();
    private static ObjShader shader;
    private static boolean initialized = false;
    private static boolean shaderFailed = false;

    // ===== Mesh 数据 (GPU 缓冲) =====
    private Mesh mesh;
    private int numIndices;
    private float boundRadius;       // 模型边界球半径 (模型空间, 未缩放)
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
    public boolean cullBackfaces = false;
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
        cam = new Camera3D();
        cam.fov = 45f;
        cam.near = 0.1f;
        cam.far = 5000f;
        cam.up.set(Vec3.Y);
        buffer = new FrameBuffer(512, 512, true);
        // ★ 设置线性过滤, 减少 FBO 纹理放大时的锯齿
        buffer.getTexture().setFilter(Texture.TextureFilter.linear, Texture.TextureFilter.linear);
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
                        if(canLoadTex()){
                            String n = line.replaceFirst("map_Kd ", "").trim();
                            current.diffTex = Core.atlas.find("create-" + n);
                        }
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

        // 三角化面: quad→2三角形, n-gon→扇形三角化
        int totalTris = 0;
        for(Face f : faces){
            totalTris += f.verts.length - 2;
        }
        int vertCount = totalTris * 3;

        // 顶点可能超过 65535, 检查
        if(vertCount >= 65535){
            Log.warn("[Create] WavefrontObject '@' has @ verts (>65535), using non-indexed mesh", textureName, vertCount);
        }

        // 每顶点 9 float: pos(3) + normal(3) + color(1 packed) + uv(2)
        vertFloatCount = vertCount * FLOATS_PER_VERT;
        float[] vertData = new float[vertFloatCount];
        originalVerts = new float[vertFloatCount];
        distortData = new float[vertFloatCount];  // 预分配形变缓冲

        // 查找材质纹理 (取第一个有 diffTex 的材质)
        if(materials != null){
            for(Material mat : materials.values()){
                if(mat.diffTex != null && mat.diffTex.found()){
                    diffTexture = mat.diffTex;
                    hasDiffTexture = true;
                    break;
                }
            }
        }
        // 回退: 使用 textureName 查找的 texture
        if(!hasDiffTexture && texture != null && texture.found()){
            diffTexture = texture;
            hasDiffTexture = true;
        }

        // 预计算每个面的法线 (如果 OBJ 没有法线)
        int vi = 0;
        for(Face f : faces){
            // 面法线 (如果没有顶点法线, 计算面法线)
            Vec3 faceNormal = Tmp.v31.setZero();
            if(hasNormal && f.normal != null && f.normal.length > 0){
                for(Vec3 n : f.normal) faceNormal.add(n);
                faceNormal.scl(1f / f.normal.length).nor();
            }else{
                // 从前3个顶点计算面法线
                Vec3 v0 = f.verts[0].source;
                Vec3 v1 = f.verts[1].source;
                Vec3 v2 = f.verts[2].source;
                faceNormal.set(v1.x - v0.x, v1.y - v0.y, v1.z - v0.z)
                    .crs(v2.x - v0.x, v2.y - v0.y, v2.z - v0.z).nor();
            }

            // 面颜色 (材质 Kd 或白色)
            float packedColor;
            if(f.mat != null && f.mat.hasColor){
                Tmp.c1.rgba8888(f.mat.diffuseCol);
                packedColor = Tmp.c1.toFloatBits();
            }else{
                packedColor = Color.whiteFloatBits;
            }

            // 扇形三角化: (0, i, i+1) for i in 1..n-2
            for(int t = 0; t < f.verts.length - 2; t++){
                int[] idx = {0, t + 1, t + 2};
                for(int vIdx : idx){
                    Vertex vert = f.verts[vIdx];
                    Vec3 pos = vert.source;
                    // 法线: 顶点法线 (如果有) 或面法线
                    Vec3 norm;
                    if(hasNormal && f.normal != null && vIdx < f.normal.length && f.normal[vIdx] != null){
                        norm = f.normal[vIdx];
                    }else{
                        norm = faceNormal;
                    }

                    // UV: 顶点 UV (如果有) 或默认 (0,0)
                    float u = 0f, v = 0f;
                    if(hasTexture && f.vertexTexture != null && vIdx < f.vertexTexture.length && f.vertexTexture[vIdx] != null){
                        u = f.vertexTexture[vIdx].x;
                        v = f.vertexTexture[vIdx].y;
                    }

                    // ★ UV 映射规则 (PU132 原版还原):
                    // OBJ UV 范围 [0,1] 是归一化坐标, 需映射到 atlas region UV 空间
                    // 因为 texture.bind() 绑定的是整个图集纹理
                    //
                    // PU132 原版 (WavefrontObject.updateFace):
                    //   u = Mathf.lerp(u, u2, objU)   // U: 线性映射
                    //   v = Mathf.lerp(v2, v, objV)   // V: Y翻转! (OBJ V轴朝上, atlas V轴朝下)
                    //
                    // 旧代码 v = v*(v2-v)+v 缺少 Y 翻转, 导致贴图上下颠倒/采样到透明 padding
                    if(hasDiffTexture && diffTexture != null && diffTexture.found()
                        && u >= 0f && u <= 1f && v >= 0f && v <= 1f){
                        u = Mathf.lerp(diffTexture.u, diffTexture.u2, u);   // U: 线性映射
                        v = Mathf.lerp(diffTexture.v2, diffTexture.v, v);   // V: Y翻转!
                    }

                    vertData[vi]     = pos.x - cx;  // 居中
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

        // 备份原始顶点 (用于 cons 形变恢复)
        System.arraycopy(vertData, 0, originalVerts, 0, vertFloatCount);

        // 创建 Mesh (非索引, 直接三角形列表)
        try{
            mesh = new Mesh(false, vertCount, 0,
                VertexAttribute.position3,
                VertexAttribute.normal,
                VertexAttribute.color,
                VertexAttribute.texCoords
            );
            mesh.setVertices(vertData, 0, vertFloatCount);
        }catch(Throwable t){
            Log.err("[Create] WavefrontObject mesh creation failed for '" + textureName + "'", t);
            mesh = null;
        }
    }

    // ===== 渲染 =====
    public void draw(float x, float y, float rX, float rY, float rZ){
        draw(x, y, rX, rY, rZ, null);
    }

    public void draw(float x, float y, float rX, float rY, float rZ, Cons<Vec3> cons){
        if(mesh == null || faces.isEmpty()) return;
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

        // ★ 使用 Draw.draw() 参与 SortedSpriteBatch 的排序管线
        // 旧方案 (Draw.z + Draw.flush) 会 flush ALL pending DrawRequests,
        // 包括 bloom capture (z=99.98) 和 render (z=110.02), 导致:
        // 1. bloom 在 z=25-35 时就 capture, 场景内容不完整 → 光效强度失效
        // 2. bloom render 在错误时机执行 → 模糊失效
        //
        // Draw.draw(z, runnable) 将整个 FBO 操作注册为 DrawRequest,
        // 在 flushRequests() 回放时执行. 此时 flushing=true:
        // - flushRequests() 不会重入 (由 flushing 标志保护)
        // - Draw.flush() 只调用 super.flush() 渲染 mesh buffer (cheap)
        // - Draw.rect() 直接走 super.draw() (绕过队列, 直接写入 mesh)
        Draw.draw(drawLayer + capturedZOffset, () -> {
            // 处理顶点形变 (cons)
            if(cons != null){
                applyDistortion(cons);
            }

            float scl = defaultScl * capturedSize;
            // worldSize = 边界球直径 * 缩放 (旋转后最大投影)
            float worldSize = boundRadius * 2f * scl;
            // ★ 固定 FBO 分辨率 1024, 不再自适应 — 避免 multi-instance 场景下反复 resize
            // (旧方案: 不同大小模型导致每帧 resize, GPU 内存碎片, 帧数越来越低)
            final int fboRes = 1024;

            // 仅在初始化或分辨率变化时 resize (固定 1024 后只执行一次)
            if(buffer.getWidth() != fboRes || buffer.getHeight() != fboRes){
                buffer.resize(fboRes, fboRes);
                buffer.getTexture().setFilter(Texture.TextureFilter.linear, Texture.TextureFilter.linear);
            }

            // ===== FBO 离屏渲染 =====
            // buffer.begin() 内部调用 Draw.flush(), 但 flushing=true 时
            // flushRequests() 是 no-op, super.flush() 只渲染空 mesh buffer
            buffer.begin(Color.clear);

            // Camera3D 透视投影
            // camDist = 3 * boundRadius * scl: 模型占 FBO ~80%, 避免边缘裁剪
            float camDist = boundRadius * scl * 3f;
            cam.position.set(0, 0, camDist);
            cam.lookAt(Vec3.Zero);
            cam.up.set(Vec3.Y);
            cam.resize(fboRes, fboRes);
            cam.update();

            // 模型变换矩阵 (旋转 + 缩放)
            transform.idt();
            transform.rotate(Vec3.Z, rZ);
            transform.rotate(Vec3.Y, rY);
            transform.rotate(Vec3.X, rX);
            transform.scale(scl, scl, scl);

            // GL 状态: 禁用混合 (FBO 内 3D 渲染用深度缓冲处理遮挡)
            Gl.disable(Gl.blend);
            Gl.enable(Gl.depthTest);
            Gl.depthMask(true);
            Gl.clear(Gl.depthBufferBit);
            if(cullBackfaces){
                Gl.enable(Gl.cullFace);
                Gl.cullFace(Gl.back);
            }

            // 渲染 Mesh
            try{
                shader.bind();
                shader.setUniformMatrix4("u_proj", cam.combined.val);
                shader.setUniformMatrix4("u_trans", transform.val);
                // ★ 使用捕获的光照参数
                setLightingUniformsCaptured(capturedLight, capturedShade, capturedMaxShade);

                if(hasDiffTexture && diffTexture != null && diffTexture.found()){
                    diffTexture.texture.bind();
                    shader.setUniformi("u_texture", 0);
                    shader.setUniformi("u_hasTexture", 1);
                }else{
                    shader.setUniformi("u_hasTexture", 0);
                }

                shader.apply();
                mesh.render(shader, Gl.triangles);
            }catch(Throwable t){
                Log.err("[Create] WavefrontObject render error", t);
            }

            // 恢复 GL 状态 (遵循 PlanetRenderer 模式)
            if(cullBackfaces){
                Gl.disable(Gl.cullFace);
            }
            Gl.depthMask(false);
            Gl.disable(Gl.depthTest);
            Gl.enable(Gl.blend);
            Gl.blendFunc(Gl.srcAlpha, Gl.oneMinusSrcAlpha);

            buffer.end();

            // 将 FBO 纹理绘制到 2D 场景
            // ★ 负高度翻转 (FBO 纹理在 OpenGL 中上下颠倒)
            // flushing=true 时 Draw.rect() 直接走 super.draw() (绕过队列)
            Draw.rect(Draw.wrap(buffer.getTexture()), x, y, worldSize, -worldSize);

            // ★ 立即 flush: FBO 是共享静态资源, 必须在下一个模型渲染前提交
            // flushing=true 时只调用 super.flush() 渲染 mesh buffer
            Draw.flush();

            // 恢复顶点 (如果有形变)
            if(cons != null){
                restoreVertices();
            }
        });
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
        System.arraycopy(originalVerts, 0, distortData, 0, vertFloatCount);
        Vec3 v = Tmp.v31;  // 复用 Tmp 避免分配
        for(int i = 0; i < vertFloatCount; i += FLOATS_PER_VERT){
            v.set(distortData[i], distortData[i + 1], distortData[i + 2]);
            cons.get(v);
            distortData[i] = v.x;
            distortData[i + 1] = v.y;
            distortData[i + 2] = v.z;
        }
        mesh.setVertices(distortData, 0, vertFloatCount);
    }

    /** 恢复原始顶点 */
    private void restoreVertices(){
        mesh.setVertices(originalVerts, 0, vertFloatCount);
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
    }

    public enum ShadingType{
        zMedian,
        zDistance,
        normalAngle,
        topLight,
        noShading
    }
}
