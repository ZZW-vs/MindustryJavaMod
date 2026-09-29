package zzw.util;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Color;
import arc.math.geom.Vec3;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.graphics.Layer;

/**
 * WavefrontObject (.obj 文件) 加载管理器
 *
 * 移植自 PU_V8 UnityObjs (annotation processor 生成)
 * - cube.obj (cube 炮台用)
 * - wavefront.obj (wavefront 炮台用)
 * - prism.obj (prism 炮台用)
 * - flywheel.obj (飞轮展示方块, MC Create 模型)
 *
 * 加载时机: FileTreeInitEvent 后用 Core.app.post() 延迟一帧,
 * 确保 atlas 已填充模组贴图 (修复 wavefront 贴图加载失败的问题)
 *
 * 颜色/大小配置移植自 PU_V8 assets/objects/objects.properties
 */
public class ZObjs {
    // ★ 临时诊断：改成 true 时，所有模型用纯白渲染（无纹理）
    public static final boolean DEBUG_NO_TEXTURE = false;
    public static WavefrontObject cube;
    public static WavefrontObject wavefront;
    public static WavefrontObject prism;
    public static WavefrontObject flywheel;
    public static WavefrontObject waterWheel;
    public static WavefrontObject crushingWheel;
    public static WavefrontObject cogwheel;
    public static WavefrontObject largeCogwheel;
    /** ★ MMD 模型 (PMX): 2个人物各2形态, 从 blander/ 加载，废弃 */
//    public static WavefrontObject mikuBlack, mikuWhite, tetoNormal, tetoYandere;
    /** ★ 初音模型 (OBJ): 2种服装颜色, 从 blander/ 加载 */
//    public static WavefrontObject mikuWhiteObj, mikuBlackObj;

    private static boolean loaded = false;

    public static void init() {
        // 创建占位实例并配置渲染参数 (对应 PU_V8 objects.properties)
        // cube: UnityPal.advance=a3e3ff, UnityPal.advanceDark=59a7ff
        cube = new WavefrontObject();
        cube.textureName = "cube";
        cube.size = 4f;
        cube.lightColor = Color.valueOf("a3e3ff");
        cube.shadeColor = Color.valueOf("59a7ff");
        cube.drawLayer = Layer.turret;

        // wavefront: Color.white, UnityPal.wavefrontDark=9e9f9f
        wavefront = new WavefrontObject();
        wavefront.textureName = "wavefront";
        // size=15 炮台, defaultScl=4f, wavefront.obj 顶点范围 ~2.5x2.5x0.5
        // 需要更大的 size 使模型可见 (size=15 炮台占地 120 单位, 模型需 ~60 单位)
        wavefront.size = 12f;  // 4 * 12 = 48 倍缩放, 模型更大更显眼
        wavefront.shadingSmoothness = 1f;
        wavefront.lightColor = Color.white;
        wavefront.shadeColor = Color.valueOf("9e9f9f");
        wavefront.drawLayer = Layer.turret;
        
        // ★ 让 wavefront 炮台"躺下"（波浪沿地面延伸）
        wavefront.extraRotX = -90f;
        // ★ 翻转前后朝向
        wavefront.extraRotY = 180f;
        // ★ 翻转朝向（开口朝向鼠标）
        wavefront.extraRotZ = 180f;   // 原来是 270f

        // prism: UnityPal.monolith=87ceeb, UnityPal.monolithDark=6586b0
        // ★ 原版钻石形 (6顶点+8面), 顶点范围 ~2x2x2.5 (高度 2.5)
        // size=2.5f: defaultScl(4) * 2.5 = 10倍缩放, 模型实际高度 2.5 * 10 = 25单位 (匹配炮台size=5占地50单位的1/2)
        // PrismTurret 中 prismOffset=10f (距炮台中心10单位), 模型高度25单位, 总占用35单位 (合理)
        // ★ 使用 topLight 着色: 法线Y分量决定明暗, 避免旋转时面因法线与Z轴夹角大而变暗(看起来透明)
        prism = new WavefrontObject();
        prism.textureName = "prism";
        prism.size = 2.5f;
        prism.shadingType = WavefrontObject.ShadingType.topLight;
        prism.lightColor = Color.valueOf("87ceeb");
        prism.shadeColor = Color.valueOf("6586b0");
        prism.maxShade = 0.8f;
        prism.drawLayer = Layer.turret;
        // ★ 关闭 cullBackfaces: 旋转后某些面法线朝下会被剔除, 导致"透明"
        prism.cullBackfaces = false;
        // ★ singleZLayer=true: 多个棱镜炮台同时存在时, 每个实例整体用一个 z 渲染, 避免交叉穿插
        prism.singleZLayer = true;
        // ★ 跳过 Y-up → Z-up 转换: 保持 obj 原始坐标轴
        //   原版 PU 的旋转语义基于 obj 轴 (瞄准轴=objZ, 自转轴=objY, 长轴=objY)
        //   mode B 转换会把 objY(长轴) 映射到 localZ, 导致瞄准角 rZ 变成绕自身长轴自转 (轴错位)
        //   PrismTurret 的 rZ=90-rotation / rY=prismRotation 是按 PU 语义写的, 必须配 obj 轴
        prism.enableYUpConversion = false;

        // flywheel: MC Create 飞轮模型 (258顶点/186面, 金属灰色)
        // 顶点范围 ~0~1.5 (1.5单位立方体), size=3f: defaultScl(4)*3=12倍缩放, 模型 ~18单位 (size=2方块占地16单位)
        // ★ 使用 topLight 着色: 模拟从上方照射的环境光, 法线Y分量决定明暗
        //   朝上的面亮(材质Kd), 朝下的面暗(shadeColor), 有强烈3D感
        //   shadeColor=404048 深灰, maxShade=0.8 允许较大明暗对比
        flywheel = new WavefrontObject();
        flywheel.textureName = "flywheel";
        flywheel.size = 3f;
        flywheel.shadingType = WavefrontObject.ShadingType.topLight;
        flywheel.lightColor = Color.white;
        flywheel.shadeColor = Color.valueOf("404048");
        flywheel.maxShade = 0.8f;
        flywheel.drawLayer = Layer.block;
        // ★ singleZLayer=true: 多个飞轮方块同时存在时, 每个实例整体用一个 z 渲染, 避免交叉穿插
        flywheel.singleZLayer = true;
        flywheel.cullBackfaces = false;

        // waterWheel: MC Create 水车模型 (绕Y轴旋转, 棕色木质)
        waterWheel = new WavefrontObject();
        waterWheel.textureName = "water_wheel";
        waterWheel.size = 3f;
        waterWheel.shadingType = WavefrontObject.ShadingType.topLight;
        waterWheel.lightColor = Color.valueOf("8B7355");
        waterWheel.shadeColor = Color.valueOf("4A3B2A");
        waterWheel.maxShade = 0.7f;
        waterWheel.drawLayer = Layer.block;
        waterWheel.singleZLayer = true;
        waterWheel.cullBackfaces = false;

        // crushingWheel: MC Create 粉碎轮 (绕Z轴旋转, 灰色石质)
        crushingWheel = new WavefrontObject();
        crushingWheel.textureName = "crushing_wheel";
        crushingWheel.size = 3f;
        crushingWheel.shadingType = WavefrontObject.ShadingType.topLight;
        crushingWheel.lightColor = Color.valueOf("9E9E9E");
        crushingWheel.shadeColor = Color.valueOf("404040");
        crushingWheel.maxShade = 0.7f;
        crushingWheel.drawLayer = Layer.block;
        crushingWheel.singleZLayer = true;
        crushingWheel.cullBackfaces = false;

        // cogwheel: 小齿轮 (8齿, 棕色木质, 绕Y轴旋转)
        cogwheel = new WavefrontObject();
        cogwheel.textureName = "cogwheel";
        cogwheel.size = 3f;
        cogwheel.shadingType = WavefrontObject.ShadingType.topLight;
        cogwheel.lightColor = Color.white;
        cogwheel.shadeColor = Color.valueOf("3A2D20");
        cogwheel.maxShade = 0.75f;
        cogwheel.drawLayer = Layer.block;
        cogwheel.singleZLayer = true;
        cogwheel.cullBackfaces = false;

        // largeCogwheel: 大齿轮 (12齿, 深棕色木质, 绕Y轴旋转)
        largeCogwheel = new WavefrontObject();
        largeCogwheel.textureName = "large_cogwheel";
        largeCogwheel.size = 3f;
        largeCogwheel.shadingType = WavefrontObject.ShadingType.topLight;
        largeCogwheel.lightColor = Color.white;
        largeCogwheel.shadeColor = Color.valueOf("2A2015");
        largeCogwheel.maxShade = 0.75f;
        largeCogwheel.drawLayer = Layer.block;
        largeCogwheel.singleZLayer = true;
        largeCogwheel.cullBackfaces = false;
        /*
        // ★ MMD 角色 (PMX): 4个模型, 2个人物各2形态
        // PMX 模型 boundRadius ~10, size=0.3: defaultScl(4)*0.3=1.2倍缩放, 模型高度 ~24单位
        // topLight 着色 + maxShade=0.3 保留贴图原色
        mikuBlack = createMmd("mikuBlack");
        mikuWhite = createMmd("mikuWhite");
        tetoNormal = createMmd("tetoNormal");
        tetoYandere = createMmd("tetoYandere");
        // 初音OBJ模型
        mikuWhiteObj = createMikuObj("mikuWhiteObj");
        mikuBlackObj = createMikuObj("mikuBlackObj");
         */

        // ★ 此事件是所有 WavefrontObject (含简单模型 cube/prism/flywheel 等) 的 .obj 数据加载入口,
        //   不能随 MMD 模型一起注释, 否则简单模型只有占位配置、无顶点数据, 完全不渲染
        Events.on(EventType.ClientLoadEvent.class, e -> {
            // ClientLoadEvent 时 atlas 贴图区域已注册, 避免 wavefront 等 hasTexture=true 对象加载失败
            Core.app.post(ZObjs::load);
        });
    }

    public static void load() {
        Log.info("[Create] ZObjs.load() 开始调用");
        if (loaded) {
            Log.info("[Create] ZObjs.load() 已加载过，跳过");
            return;
        }
        loaded = true;
        Log.info("[Create] ZObjs.load() 开始加载模型");
        
        // ★ Y-up → Z-up 转换测试开关：1=A, 2=B, 3=C, 4=D, 0=不转换
        WavefrontObject.globalYUpMode = 2;  // 默认方案 B
        loadObj(cube, "cube");
        loadObj(wavefront, "wavefront");
        loadObj(prism, "prism");
        loadObj(flywheel, "flywheel");
        loadObj(waterWheel, "water_wheel");
        loadObj(crushingWheel, "crushing_wheel");
        loadObj(cogwheel, "cogwheel");
        loadObj(largeCogwheel, "large_cogwheel");

        // ★ OBJ坐标诊断：打印所有模型的坐标范围
        Log.info("[Diag] ============ OBJ模型坐标诊断 ============");
        printDiag("cube", cube);
        printDiag("wavefront", wavefront);
        printDiag("prism", prism);
        printDiag("flywheel", flywheel);
        printDiag("water_wheel", waterWheel);
        printDiag("crushing_wheel", crushingWheel);
        printDiag("cogwheel", cogwheel);
        printDiag("large_cogwheel", largeCogwheel);
        Log.info("[Diag] =========================================");

        /*
        // 加载OBJ模型 (暂时禁用初音的两个MMD模型)
        // loadObj(mikuBlack, "blander/初音未来/Black");
        // centerMmd(mikuBlack);
        // loadObj(mikuWhite, "blander/初音未来/White");
        // centerMmd(mikuWhite);
        loadObj(tetoNormal, "blander/重音teto/重音Teto normal");
        centerMmd(tetoNormal);
        loadObj(tetoYandere, "blander/重音teto/重音Teto yandere");
        centerMmd(tetoYandere);
        // 加载初音OBJ模型
        loadObj(mikuWhiteObj, "blander/初音未来/初音未来_白");
        centerMmd(mikuWhiteObj);
        loadObj(mikuBlackObj, "blander/初音未来/初音未来_黑");
        centerMmd(mikuBlackObj);
         */
        
        // ★ groundAtLoad 配置：薄片型模型贴地，立体型模型居中
        // 薄片型模型：Z 本来就贴近 0（或略负），需要贴地
        flywheel.groundAtLoad = true;
        waterWheel.groundAtLoad = true;
        crushingWheel.groundAtLoad = true;
        cogwheel.groundAtLoad = true;
        largeCogwheel.groundAtLoad = true;
        
        // 立体型模型：保持原始 Z 居中，不贴地
        cube.groundAtLoad = false;      // 默认就是 false，可以不写
        prism.groundAtLoad = false;
        wavefront.groundAtLoad = false; // wavefront 有 extraRotX 处理，不贴地
        
        // ★ 金属度配置：不同材质的高光强度
        // 金属度高 → 高光强
        flywheel.metalness = 0.6f;      // 金属飞轮，高光强
        largeCogwheel.metalness = 0.4f; // 金属齿轮
        cogwheel.metalness = 0.4f;
        crushingWheel.metalness = 0.5f; // 石质粉碎轮，中等金属
        waterWheel.metalness = 0.2f;    // 木质水车，低金属
        wavefront.metalness = 0.3f;
        prism.metalness = 0.5f;         // 棱镜，高光
        cube.metalness = 0.3f;

        // 光照开关（先都开）
        flywheel.useRealLighting = true;
        largeCogwheel.useRealLighting = true;
        cogwheel.useRealLighting = true;
        crushingWheel.useRealLighting = true;
        waterWheel.useRealLighting = true;
        wavefront.useRealLighting = true;
        prism.useRealLighting = true;
        // ★ 模型空间光照: 棱镜持续自转, 用本地法线着色, 避免旋转时同一面忽明忽暗(看着透明)
        prism.lightInModelSpace = true;
        cube.useRealLighting = true;
        
    }

    /** 打印单个模型的坐标诊断信息 */
    private static void printDiag(String name, WavefrontObject obj) {
        if(obj == null || obj.vertices.isEmpty()) {
            Log.info("[Diag] " + name + " 未加载或无顶点");
            return;
        }
        // 注意: Arc 的 Log.info 只认 "@" 占位符, 不认 String.format 的 %s/%f/%d,
        // 所以这里必须先用 String.format 拼好再传给 Log.info,
        // 否则日志里会原样打印出模板而丢掉所有参数。
        Log.info(String.format("[Diag] %s  X=[%.2f,%.2f]  Y=[%.2f,%.2f]  Z=[%.2f,%.2f]  bound=%.2f  verts=%d  faces=%d",
            name,
            obj.minX, obj.maxX,
            obj.minY, obj.maxY,
            obj.minZ, obj.maxZ,
            obj.boundRadius,
            obj.vertices.size,
            obj.faces.size));
    }

    /** 创建 MMD 模型配置 (topLight 着色, 双面渲染, 单 Z 层) */
    private static WavefrontObject createMmd(String name){
        WavefrontObject obj = new WavefrontObject();
        obj.textureName = name;
        obj.size = 0.3f;
        obj.shadingType = WavefrontObject.ShadingType.topLight;
        obj.lightColor = Color.white;
        obj.shadeColor = Color.valueOf("808080");
        obj.maxShade = 0.3f;
        obj.drawLayer = Layer.flyingUnit;
        obj.singleZLayer = true;
        obj.cullBackfaces = false;
        return obj;
    }

    /** 创建 初音OBJ模型配置 (topLight 着色, 双面渲染, 单 Z 层) */
    private static WavefrontObject createMikuObj(String name){
        WavefrontObject obj = new WavefrontObject();
        obj.textureName = name;
        obj.size = 0.3f;
        obj.shadingType = WavefrontObject.ShadingType.topLight;
        obj.lightColor = Color.white;
        obj.shadeColor = Color.valueOf("808080");
        obj.maxShade = 0.3f;
        obj.drawLayer = Layer.flyingUnit;
        obj.singleZLayer = true;
        obj.cullBackfaces = false;
        return obj;
    }

    /** 平移 MMD 模型使脚底在原点 (PMX 模型中心通常在原点, 需下移 boundRadius) */
    private static void centerMmd(WavefrontObject obj){
        if(obj.vertices == null || obj.vertices.isEmpty()) return;
        float minY = Float.MAX_VALUE;
        for(Vec3 v : obj.vertices) minY = Math.min(minY, v.y);
        for(Vec3 v : obj.vertices) v.y -= minY;
    }

    

    private static void loadObj(WavefrontObject obj, String name) {
        // ★ 路径解析: name 含 "/" 视为完整相对路径 (如 "blander/text_g/gale"), 否则拼 objects/ 前缀
        String basePath = name.contains("/") ? name : "objects/" + name;
        Fi file = Vars.tree.get(basePath + ".obj");
        if (!file.exists()) {
            Log.err("[Create] WavefrontObject file not found: " + basePath + ".obj");
            return;
        }
        Fi material = Vars.tree.get(basePath + ".mtl");
        if (!material.exists()) material = null;
        try {
            obj.load(file, material);
        } catch (Throwable t) {
            Log.err("[Create] Failed to load WavefrontObject: " + name, t);
        }
    }
}
