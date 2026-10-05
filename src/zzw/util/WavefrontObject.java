package zzw.util;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.TextureAtlas.*;
import arc.graphics.gl.Shader;
import arc.graphics.Mesh;
import arc.graphics.VertexAttribute;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.graphics.*;

import java.io.*;
import java.util.*;

/**
 * Wavefront Object Converter and Renderer for Arc/libGDX
 * The faces should not intersect. (no crashes, its just that the renderer doesn't support it.)
 * @author EyeOfDarkness
 * @author GlennFolker
 */
public class WavefrontObject{
    protected static final float zScale = 0.01f;
    protected static final float defaultScl = 4f;
    protected static final float perspectiveDistance = 2000f;

    public Seq<Vec3> vertices = new Seq<>();
    public Seq<Vec2> uvs = new Seq<>();
    public Seq<Vec3> normals = new Seq<>();
    public Seq<Face> faces = new Seq<>();
    public String textureName = "";
    public ObjectMap<String, Material> materials;
    public final Seq<Vertex> drawnVertices = new Seq<>();
    public final Seq<Vec3> drawnNormals = new Seq<>();
    public AtlasRegion texture = null;
    public boolean hasMaterial = false;
    public boolean hasNormal = false;
    public boolean hasTexture = false;
    public boolean hasMaterialTex = false;
    public boolean odd = false;
    /** 模型边界球半径 (模型空间, 未缩放), 用于阴影大小计算 */
    public float boundRadius = 1f;
    
    // ★ 坐标系诊断变量
    public float minX, maxX, minY, maxY, minZ, maxZ;
    
    // ★ Y-up → Z-up 转换方案：0=不转, 1=A, 2=B, 3=C, 4=D
    public int yUpConversion = 2;   // 默认方案 B
    /** 是否启用坐标转换 */
    public boolean enableYUpConversion = true;
    /** 全局转换模式（优先级高于单个模型的yUpConversion） */
    public static int globalYUpMode = 2;
    
    /** 每个模型独立的额外旋转偏移（度），叠加在调用方传入的 rX/rY/rZ 之上 */
    public float extraRotX = 0f;
    public float extraRotY = 0f;
    public float extraRotZ = 0f;
    
    /** 临时诊断：是否跳过纹理渲染，用纯白替代 */
    public boolean debugNoTexture = false;
    
    /** 金属度（0=非金属，1=全金属），控制高光强度 */
    public float metalness = 0.3f;
    
    /** 是否启用真光照（false 时回退到旧的假 shading） */
    public boolean useRealLighting = true;

    /** ★ 模型空间光照：用模型本地法线着色，不跟模型旋转
     *  <p>默认 false（世界空间光照，法线随模型旋转 → 转向/自转时同一面会忽明忽暗）</p>
     *  <p>设为 true 后，模型的明暗固定在自己身上，自转/瞄准时亮度稳定不闪</p>
     *  <p>适用于持续旋转的模型（棱镜、飞轮、水车、齿轮等）</p>
     *  <p>注意：背面剔除仍使用旋转后的法线，不受此开关影响</p>
     */
    public boolean lightInModelSpace = false;
    
    /** 是否在加载时把模型 Z 贴地（minZ 移到 0）
     *  默认 false（模型保持原始 Z 居中）
     *  只有"薄片型"模型（飞轮、水车、齿轮等）需要 true
     */
    public boolean groundAtLoad = false;
    
    /** 真透视相机。null 时 fallback 到旧的假透视 */
    public rbmk.gfx.Cam cam = null;
    
    // ★ zOffset 防御补丁
    /** 同一帧内的实例序号，用于自动分配 zOffset */
    private static int drawSeq = 0;
    /** 上一帧的帧号，用来判断是否是新的一帧 */
    private static long lastFrameId = -1L;
    /** 当前实例的有效 zOffset（自动分配或手动设置） */
    private float effectiveZOffset = 0f;

    public ShadingType shadingType = ShadingType.normalAngle;
    public Color lightColor = Color.white;
    public Color shadeColor = Color.black;
    public float size = 1f;
    public float shadingSmoothness = 2.8f;
    public float drawLayer = Layer.blockBuilding;
    /** ★ 最大暗化程度 (0~1), 控制 normalAngle 着色中法线垂直时面最多变暗多少
     *  默认 0.75, 对于法线朝Y轴的模型(如飞轮)建议设为 0.4 避免全灰 */
    public float maxShade = 0.75f;
    /** 是否启用屏幕法线背面剔除 (默认 false - 伪3D 中屏幕 Z 轴剔除不适用俯视相机) */
    public boolean cullBackfaces = false;
    /** 是否用单一 z 层渲染整个模型 (默认 false, 按面 z 排序)
     *  ★ 设为 true 时所有 face 用同一 z 值, 避免多个实例的 face 在 batch 中交叉穿插
     *  适用于: 多个同类方块同时存在时的展示模型 */
    public boolean singleZLayer = false;
    /** ★ 实例间 Z 轴偏移 (由调用方在 draw 前设置, draw 后重置)
     *  用于多实例场景: 不同实例用不同 zOffset, 避免 batch 中 face 互相穿插
     *  推荐值: id * 0.0001f (范围 0~0.1, 不跨层) */
    public float zOffset = 0f;
    protected int indexerA;
    protected float indexerZ;

    // ===== 光照升级 1.3 =====
    /** ★ 当前帧的旋转矩阵 (由 draw() 写入, 供着色阶段旋转法线复用)
     *  <p>把矩阵存到实例字段后, 着色阶段可以对"加载时预计算的模型空间法线"只做一次旋转,
     *  而不用再对每个面的 face.normal[] 求平均 + 开方。高面数模型每帧可省下大量运算。</p> */
    protected float rm00, rm01, rm02, rm10, rm11, rm12, rm20, rm21, rm22;
    /** ★ 当前面的世界空间单位法线 (faceNormal() 的输出, 用字段返回避免装箱/构造) */
    protected float nrmX, nrmY, nrmZ;

    /** ★ 高光是否按金属度混合材质本色 (1.3 新增)
     *  <p>true: 金属高光偏材质本色, 非金属偏白, 避免纯白高光把有色材质"洗白"</p>
     *  <p>false: 沿用旧版纯白高光</p> */
    public boolean tintedSpecular = true;

    // ===== GPU Mesh 渲染 (高面数模型用, 兼容手机端 GLES 2.0) =====
    /** GPU Shader (所有实例共享, 兼容 GLES 2.0) */
    protected static Shader gpuShader;
    /** 按材质分组的 GPU Mesh */
    protected Seq<GpuMeshGroup> gpuGroups;
    /** 面 z 值缓存 (排序用, 避免每帧分配) */
    protected float[] gpuZVals;
    /** 面排序索引缓存 */
    protected Integer[] gpuOrder;
    /** faceIndex -> groupIndex 映射 (全局排序后按顺序填充到各组) */
    protected int[] faceToGroup;

    /** GPU Mesh 分组 (一个材质一个 Mesh, 一次 draw call) */
    protected static class GpuMeshGroup{
        public Material material;
        public Mesh mesh;
        public float[] vertices;
        public int vertexCount;
    }

    // ===== 排序缓存 (手机端崩溃修复: 避免每帧分配大数组) =====
    /** drawBatched 排序缓存 (复用, 小模型也复用): 7万面模型每帧 new float[7万] + 装箱整数
     *  = 60fps 下 84MB/s 垃圾分配, Android GC 被打爆 → 卡死/OOM 崩溃
     *  <p>注: 原先残留的 batchZVals/batchOrder/sortIndices/sortedFaces 字段已无任何引用
     *  (随 singleZLayer 排序死代码一并移除), 这里只保留真正在用的缓存。</p> */
    protected float[] sortZVals;
    /** int[] 归并排序缓存，避免 Integer 装箱开销 */
    protected int[] sortIdxInt;
    protected int[] sortTmpInt;

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
                        // ★ MMD OFF 开关材质跳过 (穿衣隐藏几何)
                        if(current.name.contains("OFF") || current.name.contains("off") || current.name.contains("Off")){
                            current.skip = true;
                        }

                        if(materials == null) materials = new ObjectMap<>();
                        materials.put(current.name, current);
                        hasMaterial = true;
                    }

                    // ★ d 字段 (不透明度, 0=透明 1=不透明)
                    if(line.startsWith("d ") && current != null){
                        current.alpha = Strings.parseFloat(line.replaceFirst("d ", "").trim(), 1f);
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
                            // ★ atlas 找不到时, 从文件系统加载独立 Texture (MMD 等非 atlas 贴图)
                            if(!current.diffTex.found() && material != null){
                                loadIndependentTexture(current, n, material.parent());
                            }
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
                // 跳过注释行, 避免 contains("vt ") 等误匹配注释中的文本
                if(line.startsWith("#")) continue;
                // 跳过空行
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
                    if(pos.length != 3) throw new IllegalStateException("'v' must define all 3 vector points");

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
                    // ★ 跳过 OFF 材质的面 (MMD 穿衣开关)
                    if(current != null && current.skip) continue;
                    String[] segments = line.replace("f ", "").split("\\s+");
                    Face face = new Face();
                    face.verts = new Vertex[segments.length];
                    if(hasNormal) face.normal = new Vec3[segments.length];
                    if(hasTexture) face.vertexTexture = new Vec2[segments.length];
                    if(hasMaterial && current != null) face.mat = current;
                    if(segments.length != 4) odd = true;

                    // ★ neighbors 和 shadingValue 只在 zMedian/zDistance 着色时需要
                    // topLight/normalAngle 跳过以加速加载 (gale 6万顶点否则卡几十秒)
                    boolean needNeighbors = shadingType == ShadingType.zMedian || shadingType == ShadingType.zDistance;

                    int[] i = {0};
                    for(String segment : segments){
                        String[] faceIndex = segment.split("/");
                        Vertex vert = drawnVertices.get(getFaceVal(faceIndex[0]));
                        face.verts[i[0]] = vert;
                        if(hasNormal){
                            face.normal[i[0]] = drawnNormals.get(getFaceVal(faceIndex[2]));
                        }
                        if(hasTexture){
                            face.vertexTexture[i[0]] = uvs.get(getFaceVal(faceIndex[1]));
                        }

                        if(needNeighbors){
                            for(int sign : Mathf.signs){
                                Vertex v = drawnVertices.get(faceVertIndex(segments[Mathf.mod(sign + i[0], segments.length)]));
                                if(!face.verts[i[0]].neighbors.contains(v)){
                                    face.verts[i[0]].neighbors.add(v);
                                }
                            }
                        }
                        face.size += 6;
                        i[0]++;
                    }

                    // ★ 三角形(3顶点) 预扩展为 degenerate quad (4顶点, 24 floats)
                    //   避免每帧 System.arraycopy 扩展 (78580面 × 60fps = 470万次/秒)
                    face.data = new float[(segments.length == 3 ? 4 : segments.length) * 6];

                    if(needNeighbors){
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
                    }
                    faces.add(face);
                }
            }catch(Throwable e){
                throw new RuntimeException(e);
            }
        }
        if(canLoadTex()){
            texture = Core.atlas.find("create-" + textureName + "-tex");
        }

        // ★ 如果贴图未找到 (obj 有 vt 但 atlas 无对应贴图), 禁用贴图渲染
        //   避免使用 missing texture (紫黑格) 导致模型颜色错误
        if(hasTexture && texture != null && !texture.found()){
            Log.warn("[Create] WavefrontObject: texture 'create-@-tex' not found, disabling texture rendering", textureName);
            hasTexture = false;
            texture = null;
        }

        // ★ 计算 boundRadius (用于阴影大小和相机定位)
        if(!vertices.isEmpty()){
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for(Vec3 v : vertices){
                minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
                minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
                minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z);
            }
            float cx = (minX + maxX) * 0.5f, cy = (minY + maxY) * 0.5f, cz = (minZ + maxZ) * 0.5f;
            float maxR = 0f;
            for(Vec3 v : vertices){
                float dx = v.x - cx, dy = v.y - cy, dz = v.z - cz;
                float r = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
                if(r > maxR) maxR = r;
            }
            boundRadius = Math.max(maxR, 0.1f);
            
            // 保存用于诊断
            this.minX = minX; this.maxX = maxX;
            this.minY = minY; this.maxY = maxY;
            this.minZ = minZ; this.maxZ = maxZ;
            
            // ★ Y-up → Z-up 坐标转换
            if(enableYUpConversion && globalYUpMode > 0){
                convertYUpToZUp(globalYUpMode);
                // 转换后需要重新计算 boundRadius 和 min/max
                recomputeBounds();
            }
            
            // ★ 模型级额外旋转（加载时一次性应用到顶点）
            applyExtraRotations();
            recomputeBounds();
            
            // ★ 模型几何中心居中（X/Y 居中，Z 保持贴地）
            recenterModel();
            // ★ Z轴贴地修正（避免旋转后沉入地下）
            if(groundAtLoad){
                groundModel();
            }

            // ★ 预计算模型空间法线（必须在所有加载期顶点/法线变换之后调用）
            buildLocalFaceNormals();
        }

        Log.info("[Create] WavefrontObject loaded: " + drawnVertices.size + " verts, " + faces.size + " faces, boundRadius=" + boundRadius);
        
        // ★ 坐标系诊断：打印顶点范围
        if(!vertices.isEmpty()){
            Log.info("[Diag] " + textureName
                + " X=[" + minX + "," + maxX + "]"
                + " Y=[" + minY + "," + maxY + "]"
                + " Z=[" + minZ + "," + maxZ + "]");
        }

        // ★ 手机端崩溃修复: 不再构建 GPU Mesh
        //   drawGpuMesh 渲染路径已弃用 (全项目无调用者, GPU Shader 手机端兼容性问题已回退 CPU 路径),
        //   但 buildGpuMesh 仍会为每个模型分配 顶点数组(~8MB/模型 堆) + Mesh GPU 原生缓冲(~8MB/模型),
        //   4 个角色模型合计浪费 ~58MB, 在 512MB 内存的手机上直接 OOM 崩溃
        // ★ 自动初始化真透视相机
    if(cam == null){
        float r = boundRadius * defaultScl * size;
        cam = new rbmk.gfx.Cam(Math.max(r * 1.2f, 4f));
    }
    
    // buildGpuMesh();
    }
    
    /** Y-up → Z-up 坐标转换 */
    private void convertYUpToZUp(int mode){
        // 4 种方案 (oldX, oldY, oldZ) -> (newX, newY, newZ)
        // A: newX=ox, newY=oz,  newZ=oy     （直接交换 Y/Z）
        // B: newX=ox, newY=-oz, newZ=oy     （交换 Y/Z 并翻转 Y，Blender 默认）
        // C: newX=ox, newY=oz,  newZ=-oy    （交换 Y/Z 并翻转 Z）
        // D: newX=ox, newY=-oz, newZ=-oy    （交换并双翻转）
        for(int i = 0; i < vertices.size; i++){
            Vec3 v = vertices.get(i);
            float ox = v.x, oy = v.y, oz = v.z;
            switch(mode){
                case 1 -> { v.x = ox; v.y =  oz; v.z =  oy; }
                case 2 -> { v.x = ox; v.y = -oz; v.z =  oy; }
                case 3 -> { v.x = ox; v.y =  oz; v.z = -oy; }
                case 4 -> { v.x = ox; v.y = -oz; v.z = -oy; }
            }
        }
        // 法线用同样的规则
        for(int i = 0; i < normals.size; i++){
            Vec3 n = normals.get(i);
            float ox = n.x, oy = n.y, oz = n.z;
            switch(mode){
                case 1 -> { n.x = ox; n.y =  oz; n.z =  oy; }
                case 2 -> { n.x = ox; n.y = -oz; n.z =  oy; }
                case 3 -> { n.x = ox; n.y =  oz; n.z = -oy; }
                case 4 -> { n.x = ox; n.y = -oz; n.z = -oy; }
            }
        }
        // drawnVertices 和 drawnNormals 是 final 字段，改内部 Vec3
        for(int i = 0; i < drawnVertices.size; i++){
            Vec3 v = drawnVertices.get(i).source;
            Vec3 v0 = vertices.get(i);
            v.set(v0.x, v0.y, v0.z);
        }
        for(int i = 0; i < drawnNormals.size; i++){
            Vec3 n = drawnNormals.get(i);
            Vec3 n0 = normals.get(i);
            n.set(n0.x, n0.y, n0.z);
        }
    }
    
    /** 重新计算边界球和坐标范围 */
    private void recomputeBounds(){
        if(vertices.isEmpty()) return;
        
        // 重新计算 min/max
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;
        
        for(Vec3 v : vertices){
            minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
            minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
            minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z);
        }
        this.minX = minX; this.maxX = maxX;
        this.minY = minY; this.maxY = maxY;
        this.minZ = minZ; this.maxZ = maxZ;
        
        // 重新计算 boundRadius
        float cx = (minX + maxX) * 0.5f, cy = (minY + maxY) * 0.5f, cz = (minZ + maxZ) * 0.5f;
        float maxR = 0f;
        for(Vec3 v : vertices){
            float dx = v.x - cx, dy = v.y - cy, dz = v.z - cz;
            float r = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            if(r > maxR) maxR = r;
        }
        boundRadius = Math.max(maxR, 0.1f);
        
    }

    /** 模型几何中心居中（X/Y 居中，Z 保持贴地） */
    private void recenterModel(){
        if(vertices.isEmpty()) return;

        // 只居中 X 和 Y（竖直旋转轴是 Z，Z 保持贴地不动）
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for(Vec3 v : vertices){
            minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
            minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
        }

        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;

        for(Vec3 v : vertices){
            v.x -= cx;
            v.y -= cy;
        }

        // 同步 drawnVertices
        for(int i = 0; i < drawnVertices.size; i++){
            Vec3 v = drawnVertices.get(i).source;
            Vec3 v0 = vertices.get(i);
            v.set(v0.x, v0.y, v0.z);
        }

        // 法线是方向量，不需要平移

        // 重新计算 boundRadius 和 min/max
        recomputeBounds();

        Log.info("[Recenter] @ cx=@ cy=@", textureName, cx, cy);
    }

    /** 模型Z轴贴地修正（避免旋转后沉入地下） */
    private void groundModel(){
        if(vertices.isEmpty()) return;
        float minZ = Float.MAX_VALUE;
        for(Vec3 v : vertices) minZ = Math.min(minZ, v.z);
        if(Math.abs(minZ) < 1e-6f) return;   // 已经贴地

        for(Vec3 v : vertices) v.z -= minZ;
        for(int i = 0; i < drawnVertices.size; i++){
            Vec3 v = drawnVertices.get(i).source;
            Vec3 v0 = vertices.get(i);
            v.set(v0.x, v0.y, v0.z);
        }
        recomputeBounds();
        Log.info("[Ground] @ minZ was @", textureName, minZ);
    }

    /** ★ 预计算每个面的"模型空间法线"（加载时一次）
     *  <p>供 lightInModelSpace 着色使用：直接用这份本地法线，模型旋转时明暗不跟着变。</p>
     *  <p>必须在所有加载期变换（Y-up 转换 / extraRot）之后调用，因为那时 face.normal
     *  仍指向尚未被 draw() 旋转覆盖的本地法线。</p>
     *  <p>顺带消掉了每帧"平均法线 + sqrt 归一化"的开销。</p>
     */
    private void buildLocalFaceNormals(){
        if(!hasNormal || faces.isEmpty()) return;

        for(Face face : faces){
            if(face.normal == null || face.normal.length == 0){
                face.localNormal = null;
                continue;
            }
            float ax = 0f, ay = 0f, az = 0f;
            for(Vec3 n : face.normal){
                ax += n.x; ay += n.y; az += n.z;
            }
            float inv = 1f / face.normal.length;
            ax *= inv; ay *= inv; az *= inv;

            float len = (float)Math.sqrt(ax * ax + ay * ay + az * az);
            face.localNormal = len < 1e-6f ? null : new Vec3(ax / len, ay / len, az / len);
        }
        Log.info("[LocalNormal] @ 预计算 @ 个面的模型空间法线", textureName, faces.size);
    }

    /** 把 extraRotX/Y/Z 一次性应用到顶点上（加载时） */
    private void applyExtraRotations(){
        if(extraRotX == 0f && extraRotY == 0f && extraRotZ == 0f) return;

        float cx = Mathf.cosDeg(extraRotX), sx = -Mathf.sinDeg(extraRotX);
        float cy = Mathf.cosDeg(extraRotY), sy = -Mathf.sinDeg(extraRotY);
        float cz = Mathf.cosDeg(extraRotZ), sz = -Mathf.sinDeg(extraRotZ);

        // 组合矩阵 Rz * Ry * Rx（和 draw() 里同一个公式）
        float m00 = 1, m01 = 0, m02 = 0;
        float m10 = 0, m11 = cx, m12 = -sx;
        float m20 = 0, m21 = sx, m22 = cx;

        float n00 = cy*m00 + sy*m20,   n01 = cy*m01 + sy*m21,   n02 = cy*m02 + sy*m22;
        float n10 = m10,               n11 = m11,               n12 = m12;
        float n20 = -sy*m00 + cy*m20,  n21 = -sy*m01 + cy*m21,  n22 = -sy*m02 + cy*m22;

        float p00 = cz*n00 - sz*n10,   p01 = cz*n01 - sz*n11,   p02 = cz*n02 - sz*n12;
        float p10 = sz*n00 + cz*n10,   p11 = sz*n01 + cz*n11,   p12 = sz*n02 + cz*n12;
        float p20 = n20,               p21 = n21,               p22 = n22;

        for(Vec3 v : vertices){
            float ox = v.x, oy = v.y, oz = v.z;
            v.x = p00*ox + p01*oy + p02*oz;
            v.y = p10*ox + p11*oy + p12*oz;
            v.z = p20*ox + p21*oy + p22*oz;
        }
        // 法线也要旋转
        for(Vec3 n : normals){
            float ox = n.x, oy = n.y, oz = n.z;
            n.x = p00*ox + p01*oy + p02*oz;
            n.y = p10*ox + p11*oy + p12*oz;
            n.z = p20*ox + p21*oy + p22*oz;
        }
        // 同步 drawnVertices / drawnNormals
        for(int i = 0; i < drawnVertices.size; i++){
            Vec3 v = drawnVertices.get(i).source;
            Vec3 v0 = vertices.get(i);
            v.set(v0.x, v0.y, v0.z);
        }
        for(int i = 0; i < drawnNormals.size; i++){
            Vec3 n = drawnNormals.get(i);
            Vec3 n0 = normals.get(i);
            n.set(n0.x, n0.y, n0.z);
        }
        Log.info("[ExtraRot] @ applied rX=@ rY=@ rZ=@",
            textureName, extraRotX, extraRotY, extraRotZ);
    }

    /** ★ 从 mod 文件树加载独立 Texture (非 atlas 贴图, 用于 MMD 等多贴图模型) */
    protected void loadIndependentTexture(Material mat, String name, Fi mtlDir){
        if(name == null || name.isEmpty()) return;
        String normalized = name.replace('\\', '/');
        String filename = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        String dir = mtlDir != null ? mtlDir.path().replace('\\', '/') : "";
        String[] candidates = {normalized, filename, dir + "/" + normalized, dir + "/" + filename};
        for(String p : candidates){
            Fi fi = Vars.tree.get(p);
            if(!fi.exists() && mtlDir != null) fi = mtlDir.child(p);
            if(fi.exists()){
                try{
                    Texture tex = new Texture(fi);
                    tex.setFilter(Texture.TextureFilter.linear, Texture.TextureFilter.linear);
                    mat.independentTex = tex;
                    return;
                }catch(Throwable t){
                    Log.err("[Create] Failed to load independent texture: " + p, t);
                }
            }
        }
    }

    private boolean canLoadTex(){
        return !Vars.headless && Core.atlas != null && hasTexture;
    }

    public void draw(float x, float y, float rX, float rY, float rZ){
        draw(x, y, rX, rY, rZ, null);
    }

    public void draw(float x, float y, float rX, float rY, float rZ, Cons<Vec3> cons){
        // ★ zOffset 防御补丁：每帧重置实例序号，保证同一帧内不同实例有唯一 zOffset
        long frameId = Core.graphics != null ? Core.graphics.getFrameId() : 0L;
        if(frameId != lastFrameId){
            lastFrameId = frameId;
            drawSeq = 0;
        }
        
        // ★ 调用方没设 zOffset 时，自动分配一个微小偏移，避免排序歧义
        //   范围：0 ~ 0.0001，小于单个模型的内部 z 精度，不会跨层
        this.effectiveZOffset = (zOffset != 0f) ? zOffset : (drawSeq * 0.00001f);
        drawSeq++;
        
        float oz = Draw.z();
        
        // 预计算旋转矩阵 (Rz * Ry * Rx)，避免每个顶点重复算三角函数
        // ★ arc 的 Vec3.rotate(axis, θ) 实际执行的是标准旋转的逆（矩阵转置），
        //   为了让手写矩阵与链式调用等价，sin 全部取反（cos 不变）
        float cx = Mathf.cosDeg(rX), sx = -Mathf.sinDeg(rX);
        float cy = Mathf.cosDeg(rY), sy = -Mathf.sinDeg(rY);
        float cz = Mathf.cosDeg(rZ), sz = -Mathf.sinDeg(rZ);
        // Rx
        float m00 = 1,  m01 = 0,  m02 = 0;
        float m10 = 0,  m11 = cx, m12 = -sx;
        float m20 = 0,  m21 = sx, m22 = cx;
        // Ry * Rx
        float n00 = cy*m00 + sy*m20,        n01 = cy*m01 + sy*m21,        n02 = cy*m02 + sy*m22;
        float n10 = m10,                    n11 = m11,                    n12 = m12;
        float n20 = -sy*m00 + cy*m20,       n21 = -sy*m01 + cy*m21,       n22 = -sy*m02 + cy*m22;
        // Rz * (Ry * Rx)
        float p00 = cz*n00 - sz*n10,  p01 = cz*n01 - sz*n11,  p02 = cz*n02 - sz*n12;
        float p10 = sz*n00 + cz*n10,  p11 = sz*n01 + cz*n11,  p12 = sz*n02 + cz*n12;
        float p20 = n20,              p21 = n21,              p22 = n22;
        // ★ 光照升级 1.3: 把旋转矩阵存到实例字段, 供着色阶段旋转预计算法线 (见 realLightDraw)
        rm00 = p00; rm01 = p01; rm02 = p02;
        rm10 = p10; rm11 = p11; rm12 = p12;
        rm20 = p20; rm21 = p21; rm22 = p22;
        float scl = defaultScl * size;
        
        for(int i = 0; i < drawnVertices.size; i++){
            Vec3 src = vertices.get(i);
            float vx = src.x, vy = src.y, vz = src.z;
            // 旋转 + 缩放
            float X = (p00*vx + p01*vy + p02*vz) * scl;
            float Y = (p10*vx + p11*vy + p12*vz) * scl;
            float Z = (p20*vx + p21*vy + p22*vz) * scl;
            // 平移到屏幕位置
            Vec3 v = drawnVertices.get(i).source;
            if(cam != null){
                // ★ 不要直接用 cam.sy(Y, Z)，它带了 cy 常数偏移。
                //   用缩放系数 s = D/(D-Z) 直接投影，语义是"相对方块中心的偏移"。
                float s = cam.D / (cam.D - Z);
                v.set(x + X * s, y + Y * s, Z);
            }else{
                // fallback：旧的假透视
                float depth = Math.max(0.01f, (perspectiveDistance + Z) / perspectiveDistance);
                v.set(x + X * depth, y + Y * depth, Z);
            }

            if(cons != null) cons.get(v);

            // ★ 性能优化 (光照升级 1.3): 逐顶点法线旋转只在"需要背面剔除"时才做。
            //   着色现已改用加载期预计算的 face.localNormal (见 faceNormal), 不再依赖这里的
            //   drawnNormals; 而 face.normal[] 在运行时的唯一消费者就是 cullBackfaces 判定。
            //   因此 cullBackfaces=false (默认) 时, 这一整圈法线矩阵变换都是白做的, 直接跳过。
            if(!cullBackfaces) continue;

            // 法线也用同一矩阵旋转（注意：法线不缩放、不平移）
            if(i <= drawnNormals.size - 1){
                Vec3 nsrc = normals.get(i);
                float nx = nsrc.x, ny = nsrc.y, nz = nsrc.z;
                drawnNormals.get(i).set(
                    p00*nx + p01*ny + p02*nz,
                    p10*nx + p11*ny + p12*nz,
                    p20*nx + p21*ny + p22*nz
                );
            }
        }

        // ★ 所有singleZLayer模型都用 drawBatched 批量渲染
        //   Draw.draw(z, runnable) 包裹整个模型, 只创建 1 个 DrawRequest (非78580个)
        //   runnable 在 flush 阶段执行 (flushing=true), Draw.vert 走 super.draw 直接渲染
        //   不用 GPU Mesh (GPU Shader 兼容性问题导致 MMD 无法显示, 回退到 CPU 路径)
        if(singleZLayer){
            drawBatched();
            Draw.z(oz);
            return;
        }

        // ★ 性能优化 (光照升级 1.3): 走到这里必然不是 singleZLayer (上面已提前 return),
        //   直接用模型原始面顺序遍历即可。
        //   旧代码此处有一段 `if(singleZLayer){...}else{ drawOrder = faces.toArray(Face.class); }`,
        //   其中 singleZLayer 分支永远不可达 (死代码), 而 else 分支每帧都分配一个 Face[] →
        //   7 万面模型 60fps 下产生大量垃圾。现改为直接遍历 faces, 零分配。
        for(Face face : faces){
            // 所有模式都按面z值设置Draw.z, 让 batch 能区分面层次
            indexerA = 0;
            indexerZ = 0f;
            for(Vertex vert : face.verts){
                indexerZ += vert.source.z;
                indexerA++;
            }
            indexerZ /= indexerA;
            float z = (indexerZ * zScale) + drawLayer + effectiveZOffset;
            Draw.z(z);

            // TODO [已知问题 2026-09-27]: 斜视时部分面变透明
            //   现象：从正上方看正常，相机斜视 45° 以上时某些面像是被剔除，
            //         能透过模型看到背景
            //   可能原因：cullBackfaces 剔除过严，或 singleZLayer 排序在斜视时错位
            //   影响：视觉瑕疵，不影响游戏性
            //   优先级：低（等光照升级 1.3 完成后再排查）
            if(cullBackfaces && hasNormal){
                if(Math.abs(face.normal[0].angle(Vec3.Z)) >= 90f) continue;
            }

            switch(shadingType){
                case zMedian -> zMedianDraw(face);
                case zDistance -> zDistanceDraw(face);
                case normalAngle -> normalAngleDraw(face);
                case topLight -> topLightDraw(face);
                default -> Draw.color(lightColor);
            }

            float color = Draw.getColor().toFloatBits();
            float mColor = Draw.getMixColor().toFloatBits();

            updateFace(face, color, mColor);

            // ★ 直接渲染, 不用 Draw.draw(z, ...) 延迟
            // 延迟渲染会导致多个 WavefrontObject 实例的 face 在同一 z 队列中混合排序, 互相穿插 (拉丝)
            face.draw();
        }
        Draw.reset();
        Draw.z(oz);
    }

    /** ★ 高面数模型批量渲染: 用 Draw.draw 包裹整个模型, 只创建 1 个 DrawRequest
     *  SortedSpriteBatch 在 sort 模式下, 每个 Draw.vert 调用都创建一个 DrawRequest
     *  78580 面 = 78580 个 DrawRequest, 排序巨卡
     *  用 Draw.draw(z, runnable) 包裹后, runnable 在 flush 阶段执行 (flushing=true)
     *  此时 Draw.vert 走 super.draw (直接渲染, 不创建 DrawRequest)
     *  面的渲染顺序由 drawBatched 内部按 z 排序控制 (远的先画) */
    protected void drawBatched(){
        int n = faces.size;

        // ★ 手机端崩溃修复: 排序数组缓存复用, 只在面数变化时重新分配
        //   int 原始类型首次填充后一直复用, 后续帧零分配
        if(sortIdxInt == null || sortIdxInt.length != n){
            sortIdxInt = new int[n];
            sortTmpInt = new int[n];
            sortZVals = new float[n];
            for(int i = 0; i < n; i++) sortIdxInt[i] = i;
        }
        float[] zVals = sortZVals;

        // 1. 计算每个面的 z 值 (加索引微偏移保证唯一性)
        for(int i = 0; i < n; i++){
            float z = 0;
            Face f = faces.get(i);
            for(Vertex v : f.verts) z += v.source.z;
            zVals[i] = z / f.verts.length + i * 1e-6f;
        }

        // 2. 全局排序 (所有面按 z 排序, 远的先画)
        for(int i = 0; i < n; i++) sortIdxInt[i] = i;
        mergeSortInt(sortIdxInt, sortTmpInt, zVals, 0, n);

        // ★ 立即提交：不做延迟，避免共享数据被下一个实例覆写
    //   代价：每个 face 会创建 1 个 DrawRequest（性能略降但正确）
    float modelZ = (zVals[sortIdxInt[0]] * zScale) + drawLayer + effectiveZOffset;
    float oz = Draw.z();
    Draw.z(modelZ);
    for(int i = 0; i < n; i++){
        int idx = sortIdxInt[i];
        Face f = faces.get(idx);

        switch(shadingType){
            case zMedian -> zMedianDraw(f);
            case zDistance -> zDistanceDraw(f);
            case normalAngle -> normalAngleDraw(f);
            case topLight -> topLightDraw(f);
            default -> Draw.color(lightColor);
        }
        float color = Draw.getColor().toFloatBits();
        float mColor = Draw.getMixColor().toFloatBits();

        updateFace(f, color, mColor);
        f.draw();    // ★ 立即提交，不再延迟
    }
    Draw.z(oz);
    }

    // ===== GPU Mesh 渲染方法 =====

    /** 获取/创建 GPU Shader (GLES 2.0 兼容, 不用 #version, arc 自动处理)
     *  <p>着色器源码统一收纳在 {@link zzw.content.graphics.ShaderLib.PassThroughShader},
     *  这里只做惰性创建 (所有实例共享一个)。</p> */
    protected static Shader getGpuShader(){
        if(gpuShader == null){
            gpuShader = new zzw.content.graphics.ShaderLib.PassThroughShader();
        }
        return gpuShader;
    }

    /** 构建 GPU Mesh (load 后调用, 按材质分组) */
    protected void buildGpuMesh(){
        if(faces.isEmpty()) return;

        // 按材质分组 (mat 为 null 时归到 nullKey 组, ObjectMap 不允许 null key)
        ObjectMap<Material, IntSeq> groups = new ObjectMap<>();
        IntSeq nullGroup = null;
        for(int i = 0; i < faces.size; i++){
            Material m = faces.get(i).mat;
            if(m == null){
                if(nullGroup == null) nullGroup = new IntSeq();
                nullGroup.add(i);
            }else{
                IntSeq group = groups.get(m);
                if(group == null){ group = new IntSeq(); groups.put(m, group); }
                group.add(i);
            }
        }

        gpuGroups = new Seq<>();
        faceToGroup = new int[faces.size];
        int gIdx = 0;

        // 先处理 null 材质组
        if(nullGroup != null){
            GpuMeshGroup g = new GpuMeshGroup();
            g.material = null;
            int maxVerts = 0;
            for(int i = 0; i < nullGroup.size; i++){
                int fi = nullGroup.get(i);
                faceToGroup[fi] = gIdx;
                Face f = faces.get(fi);
                maxVerts += (f.verts.length == 3) ? 3 : 6;
            }
            if(maxVerts > 0){
                g.mesh = new Mesh(false, maxVerts, 0,
                    VertexAttribute.position, VertexAttribute.color, VertexAttribute.texCoords);
                g.vertices = new float[maxVerts * 5];
                gpuGroups.add(g);
                gIdx++;
            }
        }

        for(ObjectMap.Entry<Material, IntSeq> entry : groups){
            GpuMeshGroup g = new GpuMeshGroup();
            g.material = entry.key;

            // 计算最大顶点数 (三角形=3顶点, quad拆2个三角形=6顶点)
            int maxVerts = 0;
            for(int i = 0; i < entry.value.size; i++){
                int fi = entry.value.get(i);
                faceToGroup[fi] = gIdx;
                Face f = faces.get(fi);
                maxVerts += (f.verts.length == 3) ? 3 : 6;
            }
            if(maxVerts == 0) continue;

            // 顶点格式: position(2) + color(packed) + texCoord(2) = 5 floats
            g.mesh = new Mesh(false, maxVerts, 0,
                VertexAttribute.position, VertexAttribute.color, VertexAttribute.texCoords);
            g.vertices = new float[maxVerts * 5];
            gpuGroups.add(g);
            gIdx++;
        }

        // 预分配排序数组
        int n = faces.size;
        gpuZVals = new float[n];
        gpuOrder = new Integer[n];
        for(int i = 0; i < n; i++) gpuOrder[i] = i;

        Log.info("[Create] GPU Mesh built: " + gpuGroups.size + " groups, " + n + " faces");
    }

    /** GPU Mesh 渲染: 按材质分组, 每组一次 draw call
     *  ★ 关键: 全局排序后按顺序填充到各组的顶点数组, 保证 painter's algorithm
     *  用 Draw.draw(z, runnable) 包裹, 只创建 1 个 DrawRequest */
    protected void drawGpuMesh(){
        if(gpuGroups == null || gpuGroups.isEmpty()) return;

        int n = faces.size;
        int numGroups = gpuGroups.size;

        // 1. 计算每个面的 z 值 (加微小偏移避免并列, 稳定排序)
        for(int i = 0; i < n; i++){
            float z = 0;
            Face f = faces.get(i);
            for(Vertex v : f.verts) z += v.source.z;
            gpuZVals[i] = z / f.verts.length + i * 1e-6f;
        }

        // 2. 全局排序 (远的先画, 保证 painter's algorithm)
        for(int i = 0; i < n; i++) gpuOrder[i] = i;
        Arrays.sort(gpuOrder, (a, b) -> Float.compare(gpuZVals[a], gpuZVals[b]));

        // 3. 重置每组顶点偏移
        int[] groupVi = new int[numGroups];

        // 4. 按全局排序顺序遍历所有面, 填充到对应材质组的顶点数组
        //    这样每组内部的顶点顺序 = 全局 z 排序顺序, 保证远的先画
        for(int idx : gpuOrder){
            int g = faceToGroup[idx];
            GpuMeshGroup group = gpuGroups.get(g);
            Face f = faces.get(idx);
            int vi = groupVi[g];

            // 着色
            switch(shadingType){
                case zMedian -> zMedianDraw(f);
                case zDistance -> zDistanceDraw(f);
                case normalAngle -> normalAngleDraw(f);
                case topLight -> topLightDraw(f);
                default -> Draw.color(lightColor);
            }
            float colorBits = Draw.getColor().toFloatBits();

            // 材质 alpha 调制
            if(f.mat != null && f.mat.alpha < 1f){
                Tmp.c4.set(Draw.getColor()).a(Draw.getColor().a * f.mat.alpha);
                colorBits = Tmp.c4.toFloatBits();
            }

            // UV 选择
            boolean useIndep = f.mat != null && f.mat.independentTex != null;

            // 填充顶点 (三角形=3顶点, quad拆2个三角形=6顶点)
            if(f.verts.length == 3){
                for(int i = 0; i < 3; i++){
                    vi = fillGpuVertex(group.vertices, vi, f, i, colorBits, useIndep);
                }
            }else{
                // quad 拆分成 2 个三角形: (0,1,2) 和 (0,2,3)
                int[] tri0 = {0, 1, 2};
                int[] tri1 = {0, 2, 3};
                for(int i : tri0) vi = fillGpuVertex(group.vertices, vi, f, i, colorBits, useIndep);
                for(int i : tri1) vi = fillGpuVertex(group.vertices, vi, f, i, colorBits, useIndep);
            }
            groupVi[g] = vi;
        }

        // 5. 绑定 shader
        Shader shader = getGpuShader();
        shader.bind();
        shader.setUniformMatrix4("u_projTrans", Core.camera.mat);
        shader.setUniformi("u_texture", 0);

        // 6. 设置正确的 alpha 混合 (避免透明面叠加变亮)
        Gl.enable(Gl.blend);
        Gl.blendFunc(Gl.srcAlpha, Gl.oneMinusSrcAlpha);

        // 7. 按组渲染 (每组一次 draw call)
        for(int g = 0; g < numGroups; g++){
            GpuMeshGroup group = gpuGroups.get(g);
            int vi = groupVi[g];
            if(vi == 0) continue;

            // 更新 Mesh 顶点数据
            group.mesh.setVertices(group.vertices, 0, vi);

            // 绑定 texture
            Texture tex = Core.atlas.white().texture;
            if(group.material != null){
                if(group.material.independentTex != null){
                    tex = group.material.independentTex;
                }else if(group.material.diffTex != null && group.material.diffTex.found()){
                    tex = group.material.diffTex.texture;
                }
            }else if(texture != null && texture.found()){
                tex = texture.texture;
            }
            tex.bind(0);

            // 渲染 (4 = GL_TRIANGLES)
            group.mesh.render(shader, 4, 0, vi / 5);
        }
    }

    /** 填充一个 GPU 顶点到 vertices 数组, 返回新的 vi */
    protected int fillGpuVertex(float[] vertices, int vi, Face f, int i, float colorBits, boolean useIndep){
        vertices[vi++] = f.verts[i].source.x;
        vertices[vi++] = f.verts[i].source.y;
        vertices[vi++] = colorBits;
        if(useIndep){
            // 独立 Texture: UV 翻转 V 轴 (libGDX Pixmap Y=0 在顶部)
            vertices[vi++] = f.vertexTexture[i].x;
            vertices[vi++] = 1f - f.vertexTexture[i].y;
        }else if(hasTexture && texture != null && texture.found()){
            float u = texture.u, v = texture.v;
            float u2 = texture.u2, v2 = texture.v2;
            vertices[vi++] = Mathf.lerp(u, u2, f.vertexTexture[i].x);
            vertices[vi++] = Mathf.lerp(v2, v, f.vertexTexture[i].y);
        }else{
            // ★ 无贴图: 用 atlas white 区域的 UV, 保证 GPU 采样到白色 (UV=(0,0) 会采样到 atlas 边界外)
            AtlasRegion white = Core.atlas.white();
            vertices[vi++] = white.u;
            vertices[vi++] = white.v;
        }
        return vi;
    }

    /** 法线着色 (normalAngle 语义) —— 光照升级 1.3 后统一走真光照, 仅 useRealLighting=false 时回退旧版 */
    protected void normalAngleDraw(Face face){
        if(!useRealLighting){
            legacyNormalAngleDraw(face);
            return;
        }
        realLightDraw(face);
    }

    /** 顶光着色 (topLight 语义) —— 光照升级 1.3 后统一走真光照, 仅 useRealLighting=false 时回退旧版 */
    protected void topLightDraw(Face face){
        if(!useRealLighting){
            legacyTopLightDraw(face);
            return;
        }
        realLightDraw(face);
    }

    /** 取出该面的世界空间单位法线, 结果写入 nrmX/nrmY/nrmZ
     *  <p>光照升级 1.3 的性能核心: 优先使用加载时预计算的模型空间法线 {@code face.localNormal},
     *  每帧只需用当前帧的旋转矩阵旋转<b>这一个</b>向量; 旧实现则要对 face.normal[] 逐顶点求平均
     *  再做一次 sqrt 归一化。高面数模型(数万面)每帧可省下海量运算。</p>
     *  @return 法线是否有效; false 时调用方应回退到纯色绘制 */
    protected boolean faceNormal(Face face){
        if(!hasNormal) return false;

        if(face.localNormal != null){
            float x = face.localNormal.x, y = face.localNormal.y, z = face.localNormal.z;
            if(lightInModelSpace){
                // 模型空间光照: 直接用本地法线 (模型自转时明暗稳定)
                nrmX = x; nrmY = y; nrmZ = z;
            }else{
                // 世界空间光照: 用当前帧旋转矩阵把本地法线转到世界空间
                nrmX = rm00*x + rm01*y + rm02*z;
                nrmY = rm10*x + rm11*y + rm12*z;
                nrmZ = rm20*x + rm21*y + rm22*z;
            }
            return true;
        }

        // 兜底: 极少数没有预计算法线的面, 现场求平均 + 归一化
        Vec3 tmp = Tmp.v31.setZero();
        indexerA = 0;
        for(Vec3 n : face.normal){
            tmp.add(n);
            indexerA++;
        }
        if(indexerA == 0) return false;
        tmp.scl(1f / indexerA);
        float len = (float)Math.sqrt(tmp.x*tmp.x + tmp.y*tmp.y + tmp.z*tmp.z);
        if(len < 1e-6f) return false;
        nrmX = tmp.x / len; nrmY = tmp.y / len; nrmZ = tmp.z / len;
        return true;
    }

    /** ★ 光照升级 1.3: 统一的真光照着色 (normalAngle 与 topLight 共用同一份实现)
     *  <p>光照模型: 环境光 + Lambert 漫反射 (来自 {@link rbmk.gfx.Light#diffuse}) 决定明暗;
     *  Blinn 镜面高光 (来自 {@link rbmk.gfx.Light#spec}) 叠加高亮。</p>
     *  <p>相比旧版重复的两份代码, 本次升级:</p>
     *  <ol>
     *    <li>统一为一份实现, 消除 normalAngle / topLight 两套光照公式的差异;</li>
     *    <li>高光可按金属度在"白"与"材质本色"之间取色 ({@code tintedSpecular}),
     *        避免纯白高光把有色材质洗白 (金属反射带本色, 非金属偏白);</li>
     *    <li>法线复用加载期预计算结果, 只做一次矩阵旋转 (见 {@link #faceNormal})。</li>
     *  </ol>
     */
    protected void realLightDraw(Face face){
        if(!faceNormal(face)){
            Draw.color(lightColor);
            return;
        }

        // 基础颜色 (材质色优先, 否则用 lightColor)
        float baseR = lightColor.r, baseG = lightColor.g, baseB = lightColor.b;
        if(face.mat != null && face.mat.hasColor){
            Tmp.c3.rgba8888(face.mat.diffuseCol);
            baseR = Tmp.c3.r; baseG = Tmp.c3.g; baseB = Tmp.c3.b;
        }

        // Light.diffuse 输出区间约 [0.5, 1.04] → 归一到 [0,1] 亮度
        float diff = rbmk.gfx.Light.diffuse(nrmX, nrmY, nrmZ);
        float lum = Mathf.clamp((diff - 0.5f) / 0.54f, 0f, 1f);
        float shadeAmt = (1f - lum) * maxShade;

        float r = Mathf.lerp(baseR, shadeColor.r, shadeAmt);
        float g = Mathf.lerp(baseG, shadeColor.g, shadeAmt);
        float b = Mathf.lerp(baseB, shadeColor.b, shadeAmt);

        // 高光: 按金属度在"白"与"材质本色"之间取色
        float sp = rbmk.gfx.Light.spec(nrmX, nrmY, nrmZ, metalness);
        if(sp != 0f){
            if(tintedSpecular){
                float tR = Mathf.lerp(1f, baseR, metalness);
                float tG = Mathf.lerp(1f, baseG, metalness);
                float tB = Mathf.lerp(1f, baseB, metalness);
                r += sp * tR; g += sp * tG; b += sp * tB;
            }else{
                r += sp; g += sp; b += sp;
            }
        }

        Draw.color(
            Mathf.clamp(r, 0f, 1f),
            Mathf.clamp(g, 0f, 1f),
            Mathf.clamp(b, 0f, 1f),
            1f);
    }

    /** 旧版 normalAngle 着色 (useRealLighting=false 时的回退路径) */
    protected void legacyNormalAngleDraw(Face face){
        if(!faceNormal(face)){
            Draw.color(lightColor);
            return;
        }
        boolean matB = face.mat != null && face.mat.hasColor;
        if(matB){
            Tmp.c3.rgba8888(face.mat.diffuseCol);
            Tmp.c2.set(Tmp.c3.r * 0.3f, Tmp.c3.g * 0.3f, Tmp.c3.b * 0.3f, 1f);
            Tmp.c4.rgba8888(face.mat.emitCol);
            Tmp.c2.r = Mathf.lerp(Tmp.c2.r, Tmp.c3.r, Tmp.c4.r);
            Tmp.c2.g = Mathf.lerp(Tmp.c2.g, Tmp.c3.g, Tmp.c4.g);
            Tmp.c2.b = Mathf.lerp(Tmp.c2.b, Tmp.c3.b, Tmp.c4.b);
        }
        float angle = (Math.abs((float)Math.acos(Mathf.clamp(nrmZ, -1f, 1f))) / (45f * Mathf.degRad)) / shadingSmoothness;
        Tmp.c1.set(matB ? Tmp.c3 : lightColor).lerp(matB ? Tmp.c2 : shadeColor, Mathf.clamp(angle, 0f, maxShade));
        Draw.color(Tmp.c1);
    }

    /** 旧版 topLight 着色 (useRealLighting=false 时的回退路径) */
    protected void legacyTopLightDraw(Face face){
        if(!faceNormal(face)){
            Draw.color(lightColor);
            return;
        }
        float shade = Mathf.clamp((1f - nrmY) * 0.5f, 0f, maxShade);
        boolean matB = face.mat != null && face.mat.hasColor;
        if(matB){
            Tmp.c3.rgba8888(face.mat.diffuseCol);
            Tmp.c2.set(Tmp.c3.r * 0.3f, Tmp.c3.g * 0.3f, Tmp.c3.b * 0.3f, 1f);
            Tmp.c4.rgba8888(face.mat.emitCol);
            Tmp.c2.r = Mathf.lerp(Tmp.c2.r, Tmp.c3.r, Tmp.c4.r);
            Tmp.c2.g = Mathf.lerp(Tmp.c2.g, Tmp.c3.g, Tmp.c4.g);
            Tmp.c2.b = Mathf.lerp(Tmp.c2.b, Tmp.c3.b, Tmp.c4.b);
        }
        Tmp.c1.set(matB ? Tmp.c3 : lightColor).lerp(matB ? Tmp.c2 : shadeColor, shade);
        Draw.color(Tmp.c1);
    }

    protected void zMedianDraw(Face face){
        indexerA = 0;
        indexerZ = 0;
        for(Vertex vert : face.verts){
            indexerZ += -vert.source.z;
            indexerA++;
        }
        indexerZ /= indexerA;

        Tmp.c1.set(lightColor).lerp(shadeColor, Mathf.clamp(indexerZ / face.shadingValue / (shadingSmoothness * defaultScl)));
        Draw.color(Tmp.c1);
    }

    protected void zDistanceDraw(Face face){
        indexerA = 0;
        indexerZ = 0;
        for(Vertex vert : face.verts){
            vert.neighbors.each(vertex -> {
                for(Vertex v : face.verts){
                    if(v == vertex) return true;
                }
                return false;
            }, vertex -> {
                indexerZ += Math.abs(vertex.source.z - vert.source.z) / face.shadingValue / (shadingSmoothness * defaultScl);
                indexerA++;
            });
        }
        indexerZ /= indexerA;

        Tmp.c1.set(lightColor).lerp(shadeColor, Mathf.clamp(indexerZ));
        Draw.color(Tmp.c1);
    }

    protected void updateFace(Face face, float color, float mColor){
        float[] dface = face.data;

        AtlasRegion textureB = texture, region = Core.atlas.white();
        Texture indepTex = null;
        boolean useIndep = false;

        if(face.mat != null){
            // ★ 独立 Texture 优先 (PMX 等非 atlas 贴图)
            if(face.mat.independentTex != null){
                indepTex = face.mat.independentTex;
                useIndep = true;
            }else if(face.mat.diffTex != null){
                textureB = face.mat.diffTex;
            }
        }

        // ★ 材质 alpha 调制颜色 (PMX 透明材质)
        if(face.mat != null && face.mat.alpha < 1f){
            Tmp.c4.rgba8888((int)color);
            Tmp.c4.a *= face.mat.alpha;
            color = Tmp.c4.toFloatBits();
        }

        for(int i = 0; i < face.verts.length; i++){
            int s = i * 6;
            dface[s] = face.verts[i].source.x;
            dface[s + 1] = face.verts[i].source.y;
            dface[s + 2] = color;
            if(useIndep){
                // ★ 独立 Texture: UV 翻转 V 轴
                //   libGDX 从 Pixmap 创建 Texture 时, Pixmap Y=0 在顶部 → Texture V=0 在顶部
                //   OBJ UV V=0 在底部, 需翻转: 1f - y
                dface[s + 3] = face.vertexTexture[i].x;
                dface[s + 4] = 1f - face.vertexTexture[i].y;
            }else if(debugNoTexture || ZObjs.DEBUG_NO_TEXTURE){
                // ★ 诊断：强制用白色区域 UV
                AtlasRegion white = Core.atlas.white();
                dface[s + 3] = white.u;
                dface[s + 4] = white.v;
            }else if(!hasTexture || textureB == null){
                dface[s + 3] = region.u;
                dface[s + 4] = region.v;
            }else{
                float u = textureB.u, v = textureB.v;
                float u2 = textureB.u2, v2 = textureB.v2;
                dface[s + 3] = Mathf.lerp(u, u2, face.vertexTexture[i].x);
                dface[s + 4] = Mathf.lerp(v2, v, face.vertexTexture[i].y);
            }
            dface[s + 5] = mColor;
        }
        // ★ 三角形: 第4顶点 = 第1顶点 (degenerate quad, load 时已预分配 24 floats)
        if(face.verts.length == 3){
            dface[18] = dface[0]; dface[19] = dface[1]; dface[20] = dface[2];
            dface[21] = dface[3]; dface[22] = dface[4]; dface[23] = dface[5];
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
        '}';
    }

    public class Face{
        public Material mat;
        public Vertex[] verts;
        public Vec3[] normal;
        /** ★ 模型空间法线（加载时预计算并归一化），供 lightInModelSpace 着色使用 */
        public Vec3 localNormal;
        public Vec2[] vertexTexture;
        public float shadingValue = 0f;
        public int size = 0;
        public float[] data;

        protected void draw(){
            AtlasRegion textureB = texture, region = Core.atlas.white();
            Texture indepTex = null;
            for(int f = 0; f < (mat != null && mat.emitTex != null ? 2 : 1); f++){
                boolean emit = f > 0;

                if(mat != null){
                    if(f <= 0){
                        // ★ 优先独立 Texture (MMD), 其次 atlas diffTex
                        if(mat.independentTex != null){
                            indepTex = mat.independentTex;
                            textureB = null;
                        }else{
                            textureB = mat.diffTex;
                            indepTex = null;
                        }
                    }else{
                        textureB = mat.emitTex;
                        indepTex = null;
                    }
                }

                if(emit){
                    for(int i = 0; i < verts.length; i++){
                        data[i * 6 + 2] = Color.whiteFloatBits;
                    }
                }

                // ★ 选择提交的 texture: 独立 > atlas > 白色默认
                Texture submitTex;
                if(indepTex != null){
                    submitTex = indepTex;
                }else if(textureB != null && hasTexture){
                    submitTex = textureB.texture;
                }else{
                    submitTex = region.texture;
                }

                // ★ data 已在 load 时预扩展为 24 floats (三角形→degenerate quad)
                Draw.vert(submitTex, data, 0, data.length);
            }
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
        /** ★ 独立 Texture (PMX 等非 atlas 贴图), 优先级高于 diffTex */
        public Texture independentTex;
        /** 材质 alpha (0~1), <1 时半透明渲染 */
        public float alpha = 1f;
        /** ★ 是否跳过该材质的面 (MMD OFF 开关材质, 穿衣隐藏几何) */
        public boolean skip = false;
    }

    public enum ShadingType{
        zMedian,
        zDistance,
        normalAngle,
        topLight,
        noShading
    }

    /** int[] 归并排序，避免 Integer 装箱开销 */
    private static void mergeSortInt(int[] a, int[] tmp, float[] keys, int lo, int hi){
        if(hi - lo <= 1) return;
        int mid = (lo + hi) >>> 1;
        mergeSortInt(a, tmp, keys, lo, mid);
        mergeSortInt(a, tmp, keys, mid, hi);
        int i = lo, j = mid, o = lo;
        while(i < mid && j < hi) tmp[o++] = (keys[a[i]] <= keys[a[j]]) ? a[i++] : a[j++];
        while(i < mid) tmp[o++] = a[i++];
        while(j < hi) tmp[o++] = a[j++];
        System.arraycopy(tmp, lo, a, lo, hi - lo);
    }

    
}
