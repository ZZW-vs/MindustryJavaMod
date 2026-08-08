package zzw.util;

import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.Charset;

/**
 * PMX (MMD) 模型加载器
 * 解析 PMX 二进制格式, 填充 WavefrontObject 的 faces/vertices/normals/uvs 结构
 * 使用 CPU 软件渲染 (painter's algorithm + 面排序), 不构建 GPU Mesh
 *
 * PMX 格式参考: https://gist.github.com/felixjones/f8a06bd6809f2aa0fc12
 */
public class PMXLoader{

    /**
     * 从 PMX 文件加载模型到 WavefrontObject
     * @param obj 目标 WavefrontObject (会被填充 vertices/uvs/normals/faces/materials)
     * @param file PMX 文件
     */
    public static void load(WavefrontObject obj, Fi file){
        try{
            byte[] bytes = file.readBytes();
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

            // ===== Header =====
            // Magic "PMX "
            byte[] magic = new byte[4];
            buf.get(magic);
            String magicStr = new String(magic);
            if(!magicStr.equals("PMX ")){
                throw new RuntimeException("Not a PMX file: magic=" + magicStr);
            }

            // ★ PMX version 是 float (2.0/2.1), 不是 int
            float version = buf.getFloat();
            // globals count
            int globalsCount = buf.get();
            byte[] globals = new byte[globalsCount];
            buf.get(globals);

            int textEncoding = globals[0];  // 0=UTF-16LE, 1=UTF-8
            int extVecIdx = globals[1];     // 1/2/4
            int extTexIdx = globals[2];     // 1/2/4
            int extMatIdx = globals[3];     // 1/2/4
            int extBoneIdx = globals[4];    // 1/2/4
            int extMorphIdx = globals[5];   // 1/2/4
            int extRigidIdx = globals[6];   // 1/2/4

            Charset charset = textEncoding == 0 ? Charset.forName("UTF-16LE") : Charset.forName("UTF-8");

            // ===== Model name =====
            String nameJp = readText(buf, charset);
            String nameEn = readText(buf, charset);
            String commentJp = readText(buf, charset);
            String commentEn = readText(buf, charset);

            Log.info("[Create] PMX model: " + nameJp + " (v" + Strings.fixed(version, 1) + ")");

            // ===== Vertices =====
            int vertexCount = buf.getInt();
            float[] positions = new float[vertexCount * 3];
            float[] normals = new float[vertexCount * 3];
            float[] uvs = new float[vertexCount * 2];

            // ★ Additional UV Count 是全局值 (globals[1]), 不是每个顶点都读 1 字节!
            //   每个顶点有 additionalUVCount * 4 floats 的额外 UV 数据
            int additionalUVCount = globals[1] & 0xFF;

            for(int i = 0; i < vertexCount; i++){
                positions[i * 3]     = buf.getFloat();
                positions[i * 3 + 1] = buf.getFloat();
                positions[i * 3 + 2] = buf.getFloat();

                normals[i * 3]     = buf.getFloat();
                normals[i * 3 + 1] = buf.getFloat();
                normals[i * 3 + 2] = buf.getFloat();

                uvs[i * 2]     = buf.getFloat();
                uvs[i * 2 + 1] = buf.getFloat();

                // ★ 跳过全局 additionalUVCount * 4 floats (每顶点固定数量)
                for(int a = 0; a < additionalUVCount; a++){
                    buf.getFloat(); buf.getFloat(); buf.getFloat(); buf.getFloat();
                }

                // 跳过骨骼权重 (类型 + 数据)
                int weightType = buf.get() & 0xFF;
                skipBoneWeight(buf, weightType, extBoneIdx);

                // ★ Edge scale (1 float, 在 weight data 之后, 每个 vertex 结尾)
                buf.getFloat();
            }

            // ===== Faces (三角形索引) =====
            int faceIndexCount = buf.getInt();
            int[] indices = new int[faceIndexCount];
            for(int i = 0; i < faceIndexCount; i++){
                indices[i] = readIndex(buf, extVecIdx);
            }

            // ===== Textures =====
            int textureCount = buf.getInt();
            String[] texturePaths = new String[textureCount];
            for(int i = 0; i < textureCount; i++){
                String raw = readText(buf, charset);
                texturePaths[i] = raw.replace('\\', '/');
                Log.info("[Create] PMX texture[" + i + "]: " + texturePaths[i]);
            }

            // ===== Materials =====
            int materialCount = buf.getInt();
            WavefrontObject.Material[] mats = new WavefrontObject.Material[materialCount];
            int[] matFaceCounts = new int[materialCount];
            String modelDir = file.parent().path().replace('\\', '/');

            for(int m = 0; m < materialCount; m++){
                String matNameJp = readText(buf, charset);
                String matNameEn = readText(buf, charset);
                String matName = (matNameJp != null && !matNameJp.isEmpty()) ? matNameJp : matNameEn;

                // ★ PMX 材质格式 (必须严格按顺序读取, 漏一个字段会导致整个流错位)
                float dr = buf.getFloat(), dg = buf.getFloat(), db = buf.getFloat(), da = buf.getFloat();  // diffuse RGBA
                float sr = buf.getFloat(), sg = buf.getFloat(), sb = buf.getFloat();  // specular RGB
                float specStrength = buf.getFloat();  // ★ specular strength (之前漏了!)
                float ar = buf.getFloat(), ag = buf.getFloat(), ab = buf.getFloat();  // ★ ambient RGB (之前漏了!)
                int drawFlags = buf.get() & 0xFF;  // draw flags (双面/地面阴影/自身阴影/边缘)
                float er = buf.getFloat(), eg = buf.getFloat(), eb = buf.getFloat(), ea = buf.getFloat();  // edge color RGBA
                float edgeSize = buf.getFloat();  // edge size
                int texIdx = readIndex(buf, extTexIdx);  // texture index
                // ★ 注意: 标准 PMX 规范没有 sub-texture index 字段!
                //   Texture Index 之后直接是 Sphere Texture Index (76c98da 误加 subTexIdx 导致流错位)
                int sphereTexIdx = readIndex(buf, extTexIdx);  // sphere texture index
                int sphereMode = buf.get() & 0xFF;  // sphere mode
                int toonMode = buf.get() & 0xFF;  // ★ shared toon flag (0=toon texture, 1=shared toon)
                int toonIdx = (toonMode == 1) ? (buf.get() & 0xFF) : readIndex(buf, extTexIdx);  // toon index
                readText(buf, charset);  // memo
                int faceCount = buf.getInt();  // 该材质的面数 (索引数)
                matFaceCounts[m] = faceCount;

                // ★ OFF 开关材质跳过 (穿衣隐藏几何)
                boolean isOffSwitch = (da <= 0.01f) && (matName.contains("OFF") || matName.contains("off") || matName.contains("Off"));
                if(isOffSwitch){
                    mats[m] = null;
                    Log.info("[Create] PMX mat[" + m + "] " + matName + " | da=0 OFF开关, 跳过");
                    continue;
                }

                // da=0 但非 OFF → 强制 1.0 (PMX 导出器 alpha 未正确设置)
                float realDa = da <= 0.01f ? 1.0f : da;

                WavefrontObject.Material mat = new WavefrontObject.Material();
                mat.name = matName;
                mat.hasColor = true;
                // diffuse 颜色
                Tmp.c1.set(dr, dg, db, 1f);
                mat.diffuseCol = Tmp.c1.rgba8888();
                // ambient = diffuse
                mat.ambientCol = mat.diffuseCol;
                // emit (Ke) - PMX 没有 emit, 用 edge 颜色模拟
                mat.emitCol = 0x00000000;
                mat.alpha = realDa;

                // 加载贴图
                if(texIdx >= 0 && texIdx < texturePaths.length){
                    Texture tex = loadTexture(texturePaths[texIdx], modelDir);
                    if(tex != null){
                        mat.independentTex = tex;
                    }
                }

                mats[m] = mat;

                Log.info("[Create] PMX mat[" + m + "] " + matName
                    + " | da_orig=" + Strings.fixed(da, 2)
                    + " | da=" + Strings.fixed(realDa, 2)
                    + " | texIdx=" + texIdx
                    + " | texFound=" + (mat.independentTex != null)
                    + " | tris=" + (faceCount / 3));
            }

            // ===== 填充 WavefrontObject 数据结构 =====
            // 计算边界半径
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for(int i = 0; i < vertexCount; i++){
                float px = positions[i * 3], py = positions[i * 3 + 1], pz = positions[i * 3 + 2];
                minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);
            }
            float cx = (minX + maxX) * 0.5f, cy = (minY + maxY) * 0.5f, cz = (minZ + maxZ) * 0.5f;
            float radius = 0f;
            for(int i = 0; i < vertexCount; i++){
                float dx = positions[i * 3] - cx, dy = positions[i * 3 + 1] - cy, dz = positions[i * 3 + 2] - cz;
                radius = Math.max(radius, dx * dx + dy * dy + dz * dz);
            }
            obj.boundRadius = Mathf.sqrt(radius);

            // 填充 vertices, normals, uvs (clear 后 add, 因为 drawnVertices/drawnNormals 是 final)
            obj.vertices = new Seq<>(vertexCount);
            obj.normals = new Seq<>(vertexCount);
            obj.uvs = new Seq<>(vertexCount);
            obj.drawnVertices.clear();
            obj.drawnNormals.clear();

            for(int i = 0; i < vertexCount; i++){
                float px = positions[i * 3] - cx;
                float py = positions[i * 3 + 1] - cy;
                float pz = positions[i * 3 + 2] - cz;
                Vec3 v = new Vec3(px, py, pz);
                obj.vertices.add(v);
                obj.drawnVertices.add(new WavefrontObject.Vertex(px, py, pz));

                Vec3 n = new Vec3(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
                obj.normals.add(n);
                obj.drawnNormals.add(new Vec3(n));

                Vec2 uv = new Vec2(uvs[i * 2], uvs[i * 2 + 1]);
                obj.uvs.add(uv);
            }

            // 填充 materials (ObjectMap)
            obj.materials = new ObjectMap<>();
            for(int m = 0; m < materialCount; m++){
                if(mats[m] != null){
                    obj.materials.put(mats[m].name, mats[m]);
                }
            }

            // 填充 faces (三角形)
            obj.faces = new Seq<>(faceIndexCount / 3);
            int faceOffset = 0;
            for(int m = 0; m < materialCount; m++){
                WavefrontObject.Material mat = mats[m];
                int matFaceCount = matFaceCounts[m];
                if(mat == null){
                    faceOffset += matFaceCount;
                    continue;
                }

                for(int t = 0; t < matFaceCount / 3; t++){
                    int i0 = indices[faceOffset + t * 3];
                    int i1 = indices[faceOffset + t * 3 + 1];
                    int i2 = indices[faceOffset + t * 3 + 2];

                    WavefrontObject.Face face = obj.new Face();
                    face.verts = new WavefrontObject.Vertex[3];
                    face.normal = new Vec3[3];
                    face.vertexTexture = new Vec2[3];
                    face.mat = mat;
                    face.size = 3 * 6;  // 3 verts * 6 floats per vert
                    face.data = new float[face.size];

                    int[] idx = {i0, i1, i2};
                    for(int vi = 0; vi < 3; vi++){
                        face.verts[vi] = obj.drawnVertices.get(idx[vi]);
                        face.normal[vi] = obj.drawnNormals.get(idx[vi]);
                        face.vertexTexture[vi] = obj.uvs.get(idx[vi]);
                    }

                    obj.faces.add(face);
                }
                faceOffset += matFaceCount;
            }

            // 设置渲染参数
            obj.hasMaterial = true;
            obj.hasNormal = true;
            obj.hasTexture = true;
            obj.textureName = nameJp;
            // ★ MMD 模型必须双面渲染 (薄壳几何), 关闭背面剔除
            obj.cullBackfaces = false;
            // ★ 启用面排序 (painter's algorithm), 远的先画
            obj.singleZLayer = true;

            // ★ 构建 GPU Mesh (高面数模型用 GPU 渲染, 兼容手机端)
            obj.buildGpuMesh();

            Log.info("[Create] PMX loaded: " + vertexCount + " verts, " + (faceIndexCount / 3) + " tris, "
                + materialCount + " materials, " + obj.faces.size + " faces, boundRadius=" + obj.boundRadius);

        }catch(Throwable t){
            Log.err("[Create] PMX load failed: " + file, t);
        }
    }

    /** 读取 PMX 变长文本 (4字节长度 + 数据) */
    private static String readText(ByteBuffer buf, Charset charset){
        int len = buf.getInt();
        if(len <= 0) return "";
        byte[] data = new byte[len];
        buf.get(data);
        return new String(data, charset);
    }

    /** 读取 PMX 变长索引 */
    private static int readIndex(ByteBuffer buf, int size){
        switch(size){
            case 1: return buf.get() & 0xFF;
            case 2: return buf.getShort() & 0xFFFF;
            case 4: return buf.getInt();
            default: throw new RuntimeException("Unknown index size: " + size);
        }
    }

    /** 跳过骨骼权重数据 */
    private static void skipBoneWeight(ByteBuffer buf, int type, int boneIdxSize){
        switch(type){
            case 0: // BDEF1
                readIndex(buf, boneIdxSize);
                break;
            case 1: // BDEF2
                readIndex(buf, boneIdxSize);
                readIndex(buf, boneIdxSize);
                buf.getFloat();
                break;
            case 2: // BDEF4
                readIndex(buf, boneIdxSize);
                readIndex(buf, boneIdxSize);
                readIndex(buf, boneIdxSize);
                readIndex(buf, boneIdxSize);
                buf.getFloat();
                buf.getFloat();
                buf.getFloat();
                buf.getFloat();
                break;
            case 3: // SDEF (Spherical Deformation)
                // ★ boneIdx1, boneIdx2, weight, C(vec3), R0(vec3), R1(vec3) = 2 idx + 10 floats
                readIndex(buf, boneIdxSize);
                readIndex(buf, boneIdxSize);
                buf.getFloat();  // weight
                buf.getFloat(); buf.getFloat(); buf.getFloat();  // C (vec3)
                buf.getFloat(); buf.getFloat(); buf.getFloat();  // R0 (vec3)
                buf.getFloat(); buf.getFloat(); buf.getFloat();  // R1 (vec3)
                break;
            case 4: // QDEF
                readIndex(buf, boneIdxSize);
                readIndex(buf, boneIdxSize);
                readIndex(buf, boneIdxSize);
                readIndex(buf, boneIdxSize);
                buf.getFloat();
                buf.getFloat();
                buf.getFloat();
                buf.getFloat();
                break;
            default:
                // 未知骨骼权重类型，跳过该权重数据
                Log.warn("[PMXLoader] Unknown bone weight type: " + type + ", skipping this weight data");
                // 跳过剩余的权重数据
                int skipBytes = 0;
                switch(type){
                    case 0: skipBytes = 24; break;  // BDEF1
                    case 1: skipBytes = 12; break;  // BDEF2
                    case 2: skipBytes = 12; break;  // BDEF3
                    case 4: skipBytes = 8; break;   // SDEF
                    case 5: skipBytes = 16; break;  // QDEF
                }
                if(skipBytes > 0){
                    buf.position(buf.position() + skipBytes);
                } else {
                    // 默认跳过4个字节
                    buf.position(buf.position() + 4);
                }
                break;
        }
    }

    /** 从 mod 文件树加载贴图 */
    private static Texture loadTexture(String path, String modelDir){
        if(path == null || path.isEmpty()) return null;
        String normalized = path.replace('\\', '/');

        // 去掉盘符前缀
        if(normalized.matches("^[A-Za-z]:/.*")){
            normalized = normalized.substring(normalized.indexOf(':') + 2);
        }
        while(normalized.startsWith("/")) normalized = normalized.substring(1);
        while(normalized.startsWith("../")) normalized = normalized.substring(3);
        while(normalized.startsWith("./")) normalized = normalized.substring(2);

        String filename = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;

        // 候选路径
        String[] candidates = {
            normalized,
            modelDir + "/" + normalized,
            "blander/text_g/" + normalized,
            "blander/" + normalized,
            "objects/" + normalized,
            filename,
            "blander/text_g/" + filename,
            "blander/" + filename
        };

        for(String p : candidates){
            Fi fi = Vars.tree.get(p);
            if(fi.exists()){
                try{
                    Texture tex = new Texture(fi);
                    tex.setFilter(Texture.TextureFilter.linear, Texture.TextureFilter.linear);
                    Log.info("[Create] PMX texture loaded: " + p + " (" + tex.width + "x" + tex.height + ")");
                    return tex;
                }catch(Throwable t){
                    Log.err("[Create] PMX texture load failed: " + p, t);
                }
            }
        }

        Log.warn("[Create] PMX texture not found: " + path + " (norm=" + normalized + ")");
        return null;
    }
}
