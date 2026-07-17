package zzw.util;

import arc.Events;
import arc.files.Fi;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;

/**
 * WavefrontObject (.obj 文件) 加载管理器
 *
 * 移植自 PU_V8 UnityObjs (annotation processor 生成)
 * - cube.obj (cube 炮台用)
 * - wavefront.obj (wavefront 炮台用)
 *
 * 加载时机: FileTreeInitEvent 后立即加载 (此时 atlas 已就绪)
 */
public class ZObjs {
    public static WavefrontObject cube;
    public static WavefrontObject wavefront;

    private static boolean loaded = false;

    public static void init() {
        // 创建占位实例, 引用会在 load() 后填充
        cube = new WavefrontObject();
        cube.textureName = "cube";
        wavefront = new WavefrontObject();
        wavefront.textureName = "wavefront";

        Events.on(EventType.FileTreeInitEvent.class, e -> {
            // 延迟到 FileTreeInitEvent, 确保文件树可用
            load();
        });
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        loadObj(cube, "cube");
        loadObj(wavefront, "wavefront");
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
