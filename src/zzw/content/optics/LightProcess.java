package zzw.content.optics;

import arc.math.geom.QuadTree;
import arc.math.geom.Rect;
import arc.struct.Seq;

import mindustry.core.World;

import mindustry.world.Tile;

import static mindustry.Vars.*;

/**
 * 光照处理系统 (PU132 unity.async.LightProcess 移植)
 *
 * <p>PU132 用 AsyncProcess + 实体池 (Lightc) 在逻辑线程传播光照;
 * 本移植改为同步实现 (cast 在主线程 update 阶段执行), Light 为普通对象
 * 挂在持有者上, 行为与原版一致 (snap→cast→queuePoint→interact)。</p>
 *
 * <p>★ begin/process 合并为 {@link #update()}: 每 tick 先执行上一帧
 * queuePoint 的延迟任务 (对应原版 queue.post), 再 snap 全部光并 cast。</p>
 */
public class LightProcess {
    /** 光命中建筑处理队列 (原版 TaskQueue.post, 同步化) */
    public final Seq<Runnable> queue = new Seq<>();
    /** 世界中全部光 */
    public final Seq<Light> all = new Seq<>(Light.class);
    /** 光的四叉树 (终点查找已有光合并父子) */
    public final QuadTree<Light> quad = new QuadTree<>(new Rect());

    private boolean ready = false;

    /**
     * 每 tick 调用 (TestMod ClientLoadEvent 后由 Trigger.update 驱动)。
     */
    public void update() {
        // 1. 执行上一帧 cast 期间排队的交互任务 (原版 begin 的 queue.run)
        if (!queue.isEmpty()) {
            Seq<Runnable> q = new Seq<>(queue);
            queue.clear();
            for (Runnable r : q) r.run();
        }

        // 2. 收集全部存活的光并 snap (同步输入 → cast 输入)
        all.clear();
        for (Light l : Light.active) {
            if (l.removed) continue;
            l.snap();
            all.add(l);
        }

        // 3. 光传播 (同步执行原 cast 的异步部分)
        for (int i = 0; i < all.size; i++) {
            all.items[i].cast();
        }

        // 4. 执行本帧 cast 期间排队的任务 (原版下一帧 begin 执行; 提前到本帧尾,
        //    使 interact(child 注册) 当帧生效, 反射镜当帧出反射光)
        if (!queue.isEmpty()) {
            Seq<Runnable> q = new Seq<>(queue);
            queue.clear();
            for (Runnable r : q) r.run();
        }
    }

    /** 世界重置 */
    public void reset() {
        queue.clear();
        quad.clear();
        all.clear();
        Light.active.clear();
        ready = false;
    }

    /** 初始化四叉树边界 (地图加载后) */
    public void init() {
        queue.clear();
        quad.clear();
        float b = Light.yield * 2f;
        quad.bounds.set(-b, -b, world.unitWidth() + b * 2f, world.unitHeight() + b * 2f);
        ready = true;
    }

    public boolean isReady() {
        return ready;
    }

    public void quad(arc.func.Cons<QuadTree<Light>> cons) {
        synchronized (quad) {
            cons.get(quad);
        }
    }

    /** 光命中 (或打到实心方块) — 处理指向建筑的注册/交互 (原版 queuePoint) */
    public void queuePoint(Light light, LightHoldBlock.LightHoldBuild hold) {
        if (hold == null) {
            queue.add(() -> {
                light.clearChildren();

                LightHoldBlock.LightHoldBuild pointed = light.pointed;
                if (pointed != null) {
                    pointed.removeLight(light);
                    light.pointed = null;
                }
            });
        } else {
            queue.add(() -> {
                LightHoldBlock.LightHoldBuild pointed = light.pointed;
                if (light.rotationChanged || pointed != hold || hold.needsReinteract) {
                    light.clearChildren();

                    if (pointed != null) pointed.removeLight(light);
                    light.pointed = hold;
                    hold.addLight(light, World.toTile(light.endX), World.toTile(light.endY));

                    hold.interact(light);
                    light.rotationChanged = false;
                }
            });
        }
    }

    public void queueAdd(Light light) {
        if (ready) {
            queue.add(light::add);
        } else {
            light.add();
        }
    }

    public void queueRemove(Light light) {
        if (ready) {
            queue.add(() -> {
                if (light.pointed != null) light.pointed.removeLight(light);
                light.remove();
            });
        } else {
            light.remove();
        }
    }

    // ===== 静态实例与驱动 =====

    /** 全局光照系统实例 */
    public static final LightProcess lights = new LightProcess();

    private static boolean registered = false;

    /** 注册每 tick 驱动 (ClientLoadEvent 后调用一次) */
    public static void register() {
        if (registered) return;
        registered = true;

        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class, e -> lights.init());
        arc.Events.on(mindustry.game.EventType.ResetEvent.class, e -> lights.reset());

        // 与 WorldUnitType.updateInteraction 相同的每 tick 驱动方式
        arc.Events.run(mindustry.game.EventType.Trigger.update, () -> {
            if (!state.isPaused() && !headless) lights.update();
        });

        // 光束渲染 (替代原版 Drawc 实体自动绘制)
        arc.Events.run(mindustry.game.EventType.Trigger.draw, LightRenderer::draw);

        // light-forge 四角受光贴图 (atlas 此时已就绪)
        for (int i = 0; i < 4; i++) {
            Z_Optics.forgeTopRegions[i] = arc.Core.atlas.find("create-light-forge-top" + (i + 1));
        }
    }
}