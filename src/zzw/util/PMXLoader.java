package zzw.util;

import arc.files.Fi;
import arc.graphics.*;
import arc.graphics.gl.*;
import arc.struct.*;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * PMX (MMD 模型) 二进制格式加载器
 *
 * 直接解析 .pmx 文件, 无需 Blender 导出为 OBJ
 * - 解析顶点(位置/法线/UV)、面索引、材质、贴图
 * - 跳过骨骼/形变/物理 (静态 bind-pose 渲染)
 * - 按材质分组构建 MeshGroup, 支持 MMD 多贴图模型
 *
 * PMX 坐标系: 左手系 Y-up, 面绕序为顺时针 (从外侧看)
 * OpenGL: 右手系, 逆时针为正面
 * → 加载时反转三角形绕序 (i0,i1,i2 → i0,i2,i1), 配合背面剔除
 *
 * @author 郑zip
 */
public class PMXLoader{

    private static final int FLOATS_PER_VERT = 9; // pos(3) + normal(3) + color(1) + uv(2)

    /**
     * 加载 PMX 文件到 WavefrontObject
     * @param obj 目标对象 (meshGroups/boundRadius 等字段会被填充)
     * @param file PMX 文件
     */
    public static void load(WavefrontObject obj, Fi file){
        byte[] data = file.readBytes();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // ===== Header =====
        byte[] magic = new byte[4];
        buf.get(magic);
        if(magic[0] != 'P' || magic[1] != 'M' || magic[2] != 'X' || magic[3] != ' '){
            throw new RuntimeException("Not a PMX file: " + file);
        }
        float version = buf.getFloat();

        // Globals
        int globalsCount = buf.get() & 0xFF;
        byte encoding = buf.get();               // 0=UTF16LE, 1=UTF8
        int additionalUVCount = buf.get() & 0xFF;
        int vertexIndexSize = buf.get() & 0xFF;   // 1/2/4
        int textureIndexSize = buf.get() & 0xFF;
        int materialIndexSize = buf.get() & 0xFF;
        int boneIndexSize = buf.get() & 0xFF;
        int morphIndexSize = buf.get() & 0xFF;
        int rigidbodyIndexSize = buf.get() & 0xFF;

        Charset charset = encoding == 0 ? Charset.forName("UTF-16LE") : Charset.forName("UTF-8");

        // Skip model name / comment
        readText(buf, charset); // name JP
        readText(buf, charset); // name EN
        readText(buf, charset); // comment JP
        readText(buf, charset); // comment EN

        // ===== Vertices =====
        int vertexCount = buf.getInt();
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] uvs = new float[vertexCount * 2];

        for(int i = 0; i < vertexCount; i++){
            positions[i * 3]     = buf.getFloat();
            positions[i * 3 + 1] = buf.getFloat();
            positions[i * 3 + 2] = buf.getFloat();
            normals[i * 3]       = buf.getFloat();
            normals[i * 3 + 1]   = buf.getFloat();
            normals[i * 3 + 2]   = buf.getFloat();
            uvs[i * 2]           = buf.getFloat();
            uvs[i * 2 + 1]       = buf.getFloat();

            // Additional UVs
            for(int a = 0; a < additionalUVCount; a++){
                buf.getFloat(); buf.getFloat(); buf.getFloat(); buf.getFloat();
            }

            // Bone weight (skip, static render)
            int weightType = buf.get() & 0xFF;
            skipWeightData(buf, weightType, boneIndexSize);

            // Edge scale
            buf.getFloat();
        }

        // ===== Faces (vertex indices) =====
        int faceIndexCount = buf.getInt();
        int[] indices = new int[faceIndexCount];
        for(int i = 0; i < faceIndexCount; i++){
            indices[i] = readUnsignedIndex(buf, vertexIndexSize);
        }

        // ===== Textures =====
        int textureCount = buf.getInt();
        String[] texturePaths = new String[textureCount];
        for(int i = 0; i < textureCount; i++){
            // PMX 贴图路径可能是绝对路径 (C:\...) 或相对路径 (含 ..\, tex\)
            // → 反斜杠统一为正斜杠
            String raw = readText(buf, charset);
            texturePaths[i] = raw.replace('\\', '/');
            Log.info("[Create] PMX texture[" + i + "]: " + texturePaths[i]);
        }

        // ===== Bounding box =====
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for(int i = 0; i < vertexCount; i++){
            float px = positions[i * 3], py = positions[i * 3 + 1], pz = positions[i * 3 + 2];
            minX = Math.min(minX, px); minY = Math.min(minY, py); minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px); maxY = Math.max(maxY, py); maxZ = Math.max(maxZ, pz);
        }
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;
        float maxR = 0f;
        for(int i = 0; i < vertexCount; i++){
            float dx = positions[i * 3] - cx, dy = positions[i * 3 + 1] - cy, dz = positions[i * 3 + 2] - cz;
            float r = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            if(r > maxR) maxR = r;
        }
        obj.boundRadius = Math.max(maxR, 0.1f);

        // ===== Materials =====
        int materialCount = buf.getInt();
        int faceOffset = 0;
        boolean anyDoubleSided = false;
        int totalVerts = 0;

        // 模型目录 (用于贴图路径解析)
        String modelDir = file.parent().path().replace('\\', '/');

        for(int m = 0; m < materialCount; m++){
            String nameJp = readText(buf, charset); // name JP
            String nameEn = readText(buf, charset); // name EN
            String matName = (nameJp != null && !nameJp.isEmpty()) ? nameJp : nameEn;

            // Diffuse RGBA
            float dr = buf.getFloat(), dg = buf.getFloat(), db = buf.getFloat(), da = buf.getFloat();

            // Specular
            buf.getFloat(); buf.getFloat(); buf.getFloat();
            buf.getFloat(); // specular strength

            // Ambient
            buf.getFloat(); buf.getFloat(); buf.getFloat();

            // Draw flags
            int drawFlags = buf.get() & 0xFF;
            boolean doubleSided = (drawFlags & 1) != 0;
            if(doubleSided) anyDoubleSided = true;

            // Edge
            buf.getFloat(); buf.getFloat(); buf.getFloat(); buf.getFloat(); // edge color
            buf.getFloat(); // edge size

            // Texture index
            int texIdx = readSignedIndex(buf, textureIndexSize);

            // Sphere texture
            int sphereIdx = readSignedIndex(buf, textureIndexSize);
            byte sphereMode = buf.get();

            // Toon
            byte sharedToon = buf.get();
            if(sharedToon == 0){
                readSignedIndex(buf, textureIndexSize);
            }else{
                buf.get(); // shared toon index (1 byte, 0-9)
            }

            // Memo
            readText(buf, charset);

            // Face count (vertex count for this material, /3 = triangles)
            int matFaceCount = buf.getInt();

            if(matFaceCount == 0) continue;

            // 构建该材质的 Mesh
            int triCount = matFaceCount / 3;
            int vertCount = triCount * 3;
            float[] vertData = new float[vertCount * FLOATS_PER_VERT];

            // 材质颜色 (diffuse)
            Color matColor = new Color(dr, dg, db, da);
            float packedColor = matColor.toFloatBits();
            // ★ 真透明: da<0.5 才标记 transparent (深度不写入)
            // da>=0.5 只是边缘羽化或半透明颜色, 照常写入深度避免深度竞争
            boolean transparent = da < 0.5f;

            final String fm = matName;
            final float fda = da;
            final boolean fds = doubleSided;

            int vi = 0;
            for(int t = 0; t < triCount; t++){
                int i0 = indices[faceOffset + t * 3];
                int i1 = indices[faceOffset + t * 3 + 1];
                int i2 = indices[faceOffset + t * 3 + 2];

                // ★ 反转绕序: MMD 顺时针 → OpenGL 逆时针 (正面朝外)
                int[] idx = {i0, i2, i1};

                for(int vIdx : idx){
                    vertData[vi]     = positions[vIdx * 3]     - cx;
                    vertData[vi + 1] = positions[vIdx * 3 + 1] - cy;
                    vertData[vi + 2] = positions[vIdx * 3 + 2] - cz;
                    vertData[vi + 3] = normals[vIdx * 3];
                    vertData[vi + 4] = normals[vIdx * 3 + 1];
                    vertData[vi + 5] = normals[vIdx * 3 + 2];
                    vertData[vi + 6] = packedColor;
                    vertData[vi + 7] = uvs[vIdx * 2];
                    // ★ V 翻转: PMX V 朝上, OpenGL V 朝下
                    vertData[vi + 8] = 1f - uvs[vIdx * 2 + 1];
                    vi += FLOATS_PER_VERT;
                }
            }
            faceOffset += matFaceCount;

            try{
                Mesh mesh = new Mesh(false, vertCount, 0,
                    VertexAttribute.position3,
                    VertexAttribute.normal,
                    VertexAttribute.color,
                    VertexAttribute.texCoords
                );
                mesh.setVertices(vertData, 0, vertCount * FLOATS_PER_VERT);

                WavefrontObject.MeshGroup mg = new WavefrontObject.MeshGroup();
                mg.mesh = mesh;
                mg.vertFloatCount = vertCount * FLOATS_PER_VERT;
                mg.originalVerts = new float[vertCount * FLOATS_PER_VERT];
                System.arraycopy(vertData, 0, mg.originalVerts, 0, vertCount * FLOATS_PER_VERT);
                mg.distortData = new float[vertCount * FLOATS_PER_VERT];
                mg.transparent = transparent;
                mg.doubleSided = doubleSided;  // 每材质独立双面标志

                boolean texFound = false;
                // 加载贴图
                if(texIdx >= 0 && texIdx < texturePaths.length){
                    Texture tex = loadTexture(texturePaths[texIdx], modelDir);
                    if(tex != null){
                        mg.texture = tex;
                        mg.hasTexture = true;
                        texFound = true;
                    }
                }

                obj.meshGroups.add(mg);
                totalVerts += vertCount;

                // 材质诊断日志
                Log.info("[Create] PMX mat[" + m + "] " + fm
                    + " | da=" + Strings.fixed(fda, 2)
                    + " | doubleSided=" + fds
                    + " | transparent=" + transparent
                    + " | texIdx=" + texIdx
                    + " | texFound=" + texFound
                    + " | tris=" + triCount);
            }catch(Throwable t){
                Log.err("[Create] PMX mesh creation failed for material " + m, t);
            }
        }

        // ★ 排序: 不透明在前, 透明在后
        obj.meshGroups.sort((a, b) -> Boolean.compare(a.transparent, b.transparent));

        // 全局 cullBackfaces 仅作 API 兼容 (实际渲染按 MeshGroup.doubleSided 处理)
        obj.cullBackfaces = !anyDoubleSided;

        // API 兼容: ObjDisplayBlock 检查 faces.size > 0
        if(obj.faces.isEmpty()){
            obj.faces.add(obj.new Face());
        }

        int texLoadedCount = 0;
        for(WavefrontObject.MeshGroup mg : obj.meshGroups) if(mg.hasTexture) texLoadedCount++;
        Log.info("[Create] PMX loaded: " + vertexCount + " verts, " + (faceIndexCount / 3) + " tris, "
            + materialCount + " materials, " + obj.meshGroups.size + " mesh groups, "
            + texLoadedCount + "/" + textureCount + " textures, "
            + "boundRadius=" + obj.boundRadius + ", doubleSided=" + anyDoubleSided);
    }

    /** 从 mod 文件树加载贴图 (尝试多种路径, 处理绝对路径/父目录/盘符前缀) */
    private static Texture loadTexture(String path, String modelDir){
        if(path == null || path.isEmpty()) return null;
        String normalized = path.replace('\\', '/');

        // 去掉盘符前缀 (C:/  /C:/ 等)
        if(normalized.matches("^[A-Za-z]:/.*")){
            normalized = normalized.substring(normalized.indexOf(':') + 2);
        }
        while(normalized.startsWith("/")) normalized = normalized.substring(1);

        // 去掉 ../ ./ 前缀
        while(normalized.startsWith("../")) normalized = normalized.substring(3);
        while(normalized.startsWith("./")) normalized = normalized.substring(2);

        // 去文件名 (用于候选)
        String filename = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        // 去文件名 (去掉第一层目录后的部分, 用于 xxx/texture.png → texture.png)
        String afterLastSlash = filename;

        // 尝试多种路径 (按优先级从高到低)
        Seq<String> candidates = new Seq<>();
        candidates.add(normalized);
        candidates.add(modelDir + "/" + normalized);
        candidates.add("blander/text_g/" + normalized);
        candidates.add("blander/" + normalized);
        candidates.add("objects/" + normalized);
        candidates.add(afterLastSlash);
        candidates.add("blander/text_g/" + afterLastSlash);
        candidates.add("blander/" + afterLastSlash);

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

        Log.warn("[Create] PMX texture not found (orig=\"" + path + "\", norm=\"" + normalized + "\", file=\"" + afterLastSlash + "\")");
        return null;
    }

    // ===== 二进制读取工具 =====

    private static String readText(ByteBuffer buf, Charset charset){
        int len = buf.getInt();
        if(len <= 0) return "";
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, charset);
    }

    /** 无符号索引读取 (顶点索引, 始终 >= 0) */
    private static int readUnsignedIndex(ByteBuffer buf, int size){
        switch(size){
            case 1: return buf.get() & 0xFF;
            case 2: return buf.getShort() & 0xFFFF;
            case 4: return buf.getInt();
            default: throw new RuntimeException("Unknown index size: " + size);
        }
    }

    /** 有符号索引读取 (骨骼/贴图/材质索引, -1 = 无) */
    private static int readSignedIndex(ByteBuffer buf, int size){
        switch(size){
            case 1: return buf.get();
            case 2: return buf.getShort();
            case 4: return buf.getInt();
            default: throw new RuntimeException("Unknown index size: " + size);
        }
    }

    private static void skipWeightData(ByteBuffer buf, int weightType, int boneIndexSize){
        switch(weightType){
            case 0: // BDEF1
                readSignedIndex(buf, boneIndexSize);
                break;
            case 1: // BDEF2
                readSignedIndex(buf, boneIndexSize);
                readSignedIndex(buf, boneIndexSize);
                buf.getFloat();
                break;
            case 2: // BDEF4
                readSignedIndex(buf, boneIndexSize);
                readSignedIndex(buf, boneIndexSize);
                readSignedIndex(buf, boneIndexSize);
                readSignedIndex(buf, boneIndexSize);
                buf.getFloat(); buf.getFloat(); buf.getFloat(); buf.getFloat();
                break;
            case 3: // SDEF
                readSignedIndex(buf, boneIndexSize);
                readSignedIndex(buf, boneIndexSize);
                buf.getFloat();
                buf.getFloat(); buf.getFloat(); buf.getFloat(); // c
                buf.getFloat(); buf.getFloat(); buf.getFloat(); // r0
                buf.getFloat(); buf.getFloat(); buf.getFloat(); // r1
                break;
            case 4: // QDEF
                readSignedIndex(buf, boneIndexSize);
                readSignedIndex(buf, boneIndexSize);
                readSignedIndex(buf, boneIndexSize);
                readSignedIndex(buf, boneIndexSize);
                buf.getFloat(); buf.getFloat(); buf.getFloat(); buf.getFloat();
                break;
            default:
                throw new RuntimeException("Unknown weight type: " + weightType);
        }
    }
}
