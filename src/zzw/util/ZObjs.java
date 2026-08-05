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
    public static WavefrontObject cube;
    public static WavefrontObject wavefront;
    public static WavefrontObject prism;
    public static WavefrontObject flywheel;
    public static WavefrontObject waterWheel;
    public static WavefrontObject crushingWheel;
    public static WavefrontObject cogwheel;
    public static WavefrontObject largeCogwheel;
    /** ★ MMD 模型 (PMX): 2个人物各2形态, 从 blander/ 加载 */
    public static WavefrontObject mikuBlack, mikuWhite, tetoNormal, tetoYandere;

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

        // ★ MMD 角色 (PMX): 4个模型, 2个人物各2形态
        // PMX 模型 boundRadius ~10, size=0.3: defaultScl(4)*0.3=1.2倍缩放, 模型高度 ~24单位
        // topLight 着色 + maxShade=0.3 保留贴图原色
        mikuBlack = createMmd("mikuBlack");
        mikuWhite = createMmd("mikuWhite");
        tetoNormal = createMmd("tetoNormal");
        tetoYandere = createMmd("tetoYandere");

        Events.on(EventType.ClientLoadEvent.class, e -> {
            // ClientLoadEvent 时 atlas 贴图区域已注册, 避免 wavefront 等 hasTexture=true 对象加载失败
            Core.app.post(ZObjs::load);
        });
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        loadObj(cube, "cube");
        loadObj(wavefront, "wavefront");
        loadObj(prism, "prism");
        loadObj(flywheel, "flywheel");
        loadObj(waterWheel, "water_wheel");
        loadObj(crushingWheel, "crushing_wheel");
        loadObj(cogwheel, "cogwheel");
        loadObj(largeCogwheel, "large_cogwheel");
        // ★ MMD 角色: PMX 加载 (4个模型)
        loadPMX(mikuBlack, "blander/初音未来/Black.pmx");
        centerMmd(mikuBlack);
        loadPMX(mikuWhite, "blander/初音未来/White.pmx");
        centerMmd(mikuWhite);
        loadPMX(tetoNormal, "blander/重音teto/Teto normal ver.pmx");
        centerMmd(tetoNormal);
        loadPMX(tetoYandere, "blander/重音teto/Teto yandere ver.pmx");
        centerMmd(tetoYandere);
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

    /** 平移 MMD 模型使脚底在原点 (PMX 模型中心通常在原点, 需下移 boundRadius) */
    private static void centerMmd(WavefrontObject obj){
        if(obj.vertices == null || obj.vertices.isEmpty()) return;
        float minY = Float.MAX_VALUE;
        for(Vec3 v : obj.vertices) minY = Math.min(minY, v.y);
        for(Vec3 v : obj.vertices) v.y -= minY;
    }

    /** 加载 PMX (MMD) 模型文件 */
    private static void loadPMX(WavefrontObject obj, String path) {
        Fi file = Vars.tree.get(path);
        if (!file.exists()) {
            Log.err("[Create] PMX file not found: " + path);
            return;
        }
        try {
            PMXLoader.load(obj, file);
        } catch (Throwable t) {
            Log.err("[Create] Failed to load PMX: " + path, t);
        }
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
