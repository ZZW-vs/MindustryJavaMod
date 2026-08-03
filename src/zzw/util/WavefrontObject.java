package zzw.util;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.TextureAtlas.*;
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
    protected static final float perspectiveDistance = 350f;

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

                    face.data = new float[face.size];

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
        }

        Log.info("[Create] WavefrontObject loaded: " + drawnVertices.size + " verts, " + faces.size + " faces, boundRadius=" + boundRadius);
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
        float oz = Draw.z();
        for(int i = 0; i < drawnVertices.size; i++){
            Vec3 v = drawnVertices.get(i).source;
            v.set(vertices.get(i));
            if(cons != null) cons.get(v);
            v.scl(defaultScl * size).rotate(Vec3.X, rX).rotate(Vec3.Y, rY).rotate(Vec3.Z, rZ);
            float depth = Math.max(0f, (perspectiveDistance + v.z) / perspectiveDistance);
            v.scl(depth);
            
            v.add(x, y, 0f);
            if(i <= drawnNormals.size - 1){
                drawnNormals.get(i).set(normals.get(i)).rotate(Vec3.X, rX).rotate(Vec3.Y, rY).rotate(Vec3.Z, rZ);
            }
        }

        // ★ singleZLayer 模式: 按面深度排序 (远的先画, 近的后画), 保证前面覆盖后面
        // 排序后每个面仍按自己的 z 值设置 Draw.z (与非 singleZLayer 一致), 避免 Y 轴旋转对称模型面搅和
        // ★ 排序结果存入临时数组 drawOrder, 不修改原始 faces (避免多实例共享模型竞态)
        Face[] drawOrder;
        if(singleZLayer){
            int n = faces.size;
            float[] zVals = new float[n];
            for(int i = 0; i < n; i++){
                float z = 0;
                Face f = faces.get(i);
                for(Vertex v : f.verts) z += v.source.z;
                // ★ 加索引微偏移保证 z 值唯一, 避免纯 Float.compare 返回 0 时帧间排序跳变
                //   (不能用混合比较规则, 否则违反传递性 → TimSort 抛 IllegalArgumentException)
                zVals[i] = z / f.verts.length + i * 1e-6f;
            }
            Integer[] indices = new Integer[n];
            for(int i = 0; i < n; i++) indices[i] = i;
            Arrays.sort(indices, (a, b) -> Float.compare(zVals[a], zVals[b]));
            drawOrder = new Face[n];
            for(int i = 0; i < n; i++) drawOrder[i] = faces.get(indices[i]);
        }else{
            drawOrder = faces.toArray(Face.class);
        }

        for(Face face : drawOrder){
            // 所有模式都按面z值设置Draw.z, 让 batch 能区分面层次
            indexerA = 0;
            indexerZ = 0f;
            for(Vertex vert : face.verts){
                indexerZ += vert.source.z;
                indexerA++;
            }
            indexerZ /= indexerA;
            float z = (indexerZ * zScale) + drawLayer + zOffset;
            Draw.z(z);

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

    protected void normalAngleDraw(Face face){
        if(!hasNormal){
            Draw.color(lightColor);
            return;
        }
        Vec3 tmp = Tmp.v31.setZero();
        indexerA = 0;
        for(Vec3 n : face.normal){
            tmp.add(n);
            indexerA++;
        }
        tmp.scl(1f / indexerA);

        boolean matB = face.mat != null && face.mat.hasColor;
        if(matB){
            // ★ 亮部 = 材质diffuse色 (不乘lightColor，避免过暗)
            Tmp.c3.rgba8888(face.mat.diffuseCol);
            // ★ 暗部 = 材质diffuse色的暗色版本 (而非ambientCol×shadeColor)
            Tmp.c2.set(Tmp.c3.r * 0.3f, Tmp.c3.g * 0.3f, Tmp.c3.b * 0.3f, 1f);
            // 自发光插值 (emit不为0时暗部接近亮部)
            Tmp.c4.rgba8888(face.mat.emitCol);
            Tmp.c2.r = Mathf.lerp(Tmp.c2.r, Tmp.c3.r, Tmp.c4.r);
            Tmp.c2.g = Mathf.lerp(Tmp.c2.g, Tmp.c3.g, Tmp.c4.g);
            Tmp.c2.b = Mathf.lerp(Tmp.c2.b, Tmp.c3.b, Tmp.c4.b);
        }

        float angle = (Math.abs(tmp.angleRad(Vec3.Z)) / (45f * Mathf.degRad)) / shadingSmoothness;
        // ★ 限制暗化程度 (max maxShade), 避免面完全变成 shadeColor 看起来透明
        Tmp.c1.set(matB ? Tmp.c3 : lightColor).lerp(matB ? Tmp.c2 : shadeColor, Mathf.clamp(angle, 0f, maxShade));
        Draw.color(Tmp.c1);
    }

    /** ★ 顶光着色: 模拟从上方(Y轴正方向)照射的环境光
     *  法线Y分量决定明暗: 朝上=亮, 朝下=暗, 侧面=中间
     *  适用于俯视游戏中法线朝Y轴的模型(如飞轮), 有强烈3D感 */
    protected void topLightDraw(Face face){
        if(!hasNormal){
            Draw.color(lightColor);
            return;
        }
        Vec3 tmp = Tmp.v31.setZero();
        indexerA = 0;
        for(Vec3 n : face.normal){
            tmp.add(n);
            indexerA++;
        }
        tmp.scl(1f / indexerA);

        boolean matB = face.mat != null && face.mat.hasColor;
        if(matB){
            // ★ 亮部 = 材质diffuse色 (不乘lightColor，避免过暗)
            Tmp.c3.rgba8888(face.mat.diffuseCol);
            // ★ 暗部 = 材质diffuse色的暗色版本 (而非ambientCol×shadeColor)
            Tmp.c2.set(Tmp.c3.r * 0.3f, Tmp.c3.g * 0.3f, Tmp.c3.b * 0.3f, 1f);
            // 自发光插值 (emit不为0时暗部接近亮部)
            Tmp.c4.rgba8888(face.mat.emitCol);
            Tmp.c2.r = Mathf.lerp(Tmp.c2.r, Tmp.c3.r, Tmp.c4.r);
            Tmp.c2.g = Mathf.lerp(Tmp.c2.g, Tmp.c3.g, Tmp.c4.g);
            Tmp.c2.b = Mathf.lerp(Tmp.c2.b, Tmp.c3.b, Tmp.c4.b);
        }

        // 法线Y分量: 1=朝上(亮), 0=侧面, -1=朝下(暗)
        // shade = (1 - Y) / 2, 范围 0~1
        float shade = Mathf.clamp((1f - tmp.y) * 0.5f, 0f, maxShade);
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
                // ★ 独立 Texture: UV 直接用 [0,1], V 翻转匹配 OpenGL
                dface[s + 3] = face.vertexTexture[i].x;
                dface[s + 4] = 1f - face.vertexTexture[i].y;
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
        public Vec2[] vertexTexture;
        public float shadingValue = 0f;
        public int size = 0;
        public float[] data;
        /** ★ 三角形→quad 扩展缓冲 (degenerate quad), 兼容 SortedSpriteBatch 的 24-float 对齐 */
        private float[] quadBuffer;

        protected void draw(){
            AtlasRegion textureB = texture, region = Core.atlas.white();
            Texture indepTex = null;
            for(int f = 0; f < (mat != null && mat.emitTex != null ? 2 : 1); f++){
                boolean emit = f > 0;

                if(mat != null){
                    if(f <= 0){
                        // ★ 优先独立 Texture (PMX), 其次 atlas diffTex
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

                for(int i = 0; i < verts.length; i++){
                    int s = i * 6;
                    if(emit) data[s + 2] = Color.whiteFloatBits;
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

                // ★ 三角形 (3 顶点, 18 floats) 必须扩展为 degenerate quad (4 顶点, 24 floats)
                // 原因: SortedSpriteBatch.draw(Texture,float[],int,int) 按 24 floats 步长遍历,
                //   三角形 18 floats 会导致 System.arraycopy 越界 → MMD 模型无法渲染
                // 方案: 第 4 顶点 = 第 1 顶点 (形成面积为 0 的退化三角形, 不可见)
                if(data.length == 18){
                    if(quadBuffer == null) quadBuffer = new float[24];
                    System.arraycopy(data, 0, quadBuffer, 0, 18);
                    System.arraycopy(data, 0, quadBuffer, 18, 6);  // 第 4 顶点 = 第 1 顶点
                    Draw.vert(submitTex, quadBuffer, 0, 24);
                }else{
                    Draw.vert(submitTex, data, 0, data.length);
                }
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
}
