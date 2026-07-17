package zzw.util;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Color;
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
        wavefront.size = 8f;  // 4 * 8 = 32 倍缩放, 2.5 * 32 = 80 单位 (合适)
        wavefront.shadingSmoothness = 1f;
        wavefront.lightColor = Color.white;
        wavefront.shadeColor = Color.valueOf("9e9f9f");
        wavefront.drawLayer = Layer.turret;

        // prism: UnityPal.monolith=87ceeb, UnityPal.monolithDark=6586b0
        // ★ 原版 scale=0.6f (ModelInstance transform scale), 在 WavefrontObject 中等效 size = 0.6 / 4 = 0.15f
        // 但 0.15f 太小看不清, 折中用 1.0f (模型约 8 单位高, 炮台 32 单位的 1/4)
        prism = new WavefrontObject();
        prism.textureName = "prism";
        prism.size = 1.0f;
        prism.shadingSmoothness = 1f;
        prism.lightColor = Color.valueOf("87ceeb");
        prism.shadeColor = Color.valueOf("6586b0");
        prism.drawLayer = Layer.turret;
        // prism.obj 已添加法线 (vn), 用默认 normalAngle 着色 + 背面剔除

        Events.on(EventType.FileTreeInitEvent.class, e -> {
            // 延迟到下一帧, 确保 atlas 已就绪 (PU_V8 同样用 Core.app.post 包裹)
            Core.app.post(ZObjs::load);
        });
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        loadObj(cube, "cube");
        loadObj(wavefront, "wavefront");
        loadObj(prism, "prism");
    }

    private static void loadObj(WavefrontObject obj, String name) {
        Fi file = Vars.tree.get("objects/" + name + ".obj");
        if (!file.exists()) {
            Log.err("[Create] WavefrontObject file not found: objects/" + name + ".obj");
            return;
        }
        Fi material = Vars.tree.get("objects/" + name + ".mtl");
        if (!material.exists()) material = null;
        try {
            obj.load(file, material);
        } catch (Throwable t) {
            Log.err("[Create] Failed to load WavefrontObject: " + name, t);
        }
    }
}
