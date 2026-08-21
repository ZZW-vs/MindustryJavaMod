package zzw.content.type;

import arc.graphics.g2d.Draw;
import arc.math.Mat;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Tmp;
import arc.util.Time;
import arc.Core;
import arc.Events;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.input.Binding;
import mindustry.input.InputHandler;
import mindustry.ui.fragments.PlacementFragment;
import mindustry.world.Block;
import zzw.content.units.entities.WorldUnitEntity;

import java.lang.reflect.Field;

import static mindustry.Vars.ui;

/**
 * 世界单位类型 (PU132 unity.type.UnityUnitType 中 Worldc 渲染部分移植)
 *
 * <p>继承 UnityUnitType, 添加:
 * <ul>
 *   <li>worldWidth / worldHeight: 子世界尺寸 (tile 数)</li>
 *   <li>drawBody() 渲染 hack: 让子世界中的建筑物跟随单位移动和旋转 (PU132 原版投影方案)</li>
 * </ul>
 *
 * <p>子世界原版化交互 (在原世界直接交互, 无自定义 UI):
 * <ul>
 *   <li>悬停: 右下角信息面板 = 原版 PlacementFragment —— 单位 display() 委托给光标下的
 *       子世界建筑 (WorldUnitEntity.display), 悬停建筑变化时反射刷新面板</li>
 *   <li>悬停高亮: 建筑的 drawSelect() 画在投影里 (炮台射程圈等, 与原版一致)</li>
 *   <li>点击: 打开原版配置界面 / 物品界面 (config.showConfig / inv.showFor)</li>
 *   <li>建造: 光标落在平台范围时放置/拆除/预览直接作用于子世界,
 *       预览自动吸附到子世界网格 (连续坐标映射, 平台可自由移动和旋转)</li>
 * </ul>
 *
 * <p>适配 v155.4 改动:
 * <ul>
 *   <li>去掉 altBatch (用普通 batch, 接受可能的半透明排序 bug)</li>
 *   <li>constructor 设为 WorldUnitEntity::create</li>
 *   <li>Draw.sort(false) 确保建筑在单位甲板之上</li>
 * </ul>
 */
public class WorldUnitType extends UnityUnitType {

    // World units
    /** 子世界宽度 (tile 数) */
    public int worldWidth, worldHeight;

    public WorldUnitType(String name) {
        super(name);
        constructor = WorldUnitEntity::create;
    }

    /**
     * 重写drawBody: 在正常单位渲染后, 绘制子世界中的建筑物
     * <p>渲染 hack 原理 (PU132 UnityUnitType.drawBody):
     * <ol>
     *   <li>保存当前 camera.position 和 Draw.proj()</li>
     *   <li>计算偏移让子世界中心对齐单位平台位置 (subCX/subCY 含网格半格偏移)</li>
     *   <li>Draw.proj(camera) + Draw.proj().rotate(r) 旋转投影</li>
     *   <li>Draw.sort(false) 关闭 z 排序, 按 call order 绘制</li>
     *   <li>遍历 buildings 调用 b.draw() (建筑内部自定 z)</li>
     *   <li>恢复 camera 和 proj</li>
     * </ol>
     *
     * <p>★ 子世界炮台发射的子弹不在投影里绘制 —— 子弹生成时就在主世界坐标,
     * 由主管线 (Layer.bullet) 自然渲染, 位置天然正确。</p>
     */
    @Override
    public void drawBody(Unit unit) {
        float z = Draw.z();

        // 先执行正常的单位 body 渲染
        super.drawBody(unit);

        // 世界单位: 渲染子世界建筑物
        if (unit instanceof WorldUnitEntity w) {
            Draw.draw(z + 0.0001f, () -> {
                Seq<Building> build = w.buildings;
                World world = w.unitWorld;
                if (world == null || build == null || build.isEmpty()) return;

                // ★ 含网格偏移的中心: 9x19 奇数尺寸世界偏移半格对齐 8x18 甲板贴图
                float cx = w.subCX(), cy = w.subCY();
                float r = w.rotation - 90f;

                // 保存当前投影矩阵
                Mat proj = Tmp.m1.set(Draw.proj());
                Vec2 cam = Core.camera.position;
                float camX = cam.x, camY = cam.y;
                float cw = Core.camera.width / 2f, ch = Core.camera.height / 2f;
                Tmp.v2.set(-cx, -cy).rotate(r);
                Tmp.v1.set(unit).sub(camX, camY).add(cw, ch).add(Tmp.v2);
                cam.set(cw - Tmp.v1.x, ch - Tmp.v1.y);
                Core.camera.update();
                Draw.flush();

                Draw.proj(Core.camera);

                Draw.proj().rotate(r);

                // ★ 关闭 z 排序: 按 call order 绘制, 确保建筑在单位甲板之上
                Draw.sort(false);

                // ★ 电力连接线修复: 建筑的 draw() 内部会用 world.build(links) 查链接目标
                //   (如 PowerNode 的激光连线), 渲染期间切到子世界, 查询才落在子世界建筑上
                World ow = Vars.world;
                Vars.world = world;

                // 绘制建筑 (含传送带物品、炮台旋转等内部动画)
                for (int i = 0; i < build.size; i++) {
                    build.get(i).draw();
                }

                // ★ 悬停高亮: 光标下的子世界建筑画选择框/射程圈 (复刻原版悬停效果)
                if (hoveredSubBuild != null && !hoveredSubBuild.dead
                    && w.ownsBuilding(hoveredSubBuild)) {
                    hoveredSubBuild.drawSelect();
                }

                // ★ 放置预览: 鼠标在本子世界区域且处于放置模式 → 在子世界网格上画 ghost
                //   (吸附到子世界自己的网格; 在投影上下文中绘制, 坐标即子世界坐标)
                if (buildPreviewUnit == w && buildPreviewBlock != null) {
                    Block pb = buildPreviewBlock;
                    // 锚点 tile 中心 + 多方块偏移 (与原版建筑 drawx 一致)
                    float px = buildPreviewX * Vars.tilesize + Vars.tilesize / 2f + pb.offset;
                    float py = buildPreviewY * Vars.tilesize + Vars.tilesize / 2f + pb.offset;

                    // ghost 贴图 (呼吸透明度, 同原版预览观感)
                    Draw.alpha(0.45f + Mathf.absin(Time.time, 3f, 0.1f));
                    Draw.rect(pb.fullIcon, px, py, pb.rotate ? buildPreviewRot * 90f : 0f);
                    Draw.alpha(1f);

                    // 有效绿框 / 无效红框 (覆盖原版基于主世界地形的红框)
                    float half = pb.size * Vars.tilesize / 2f;
                    Drawf.dashRect(buildPreviewValid ? Pal.accent : Pal.remove, px - half, py - half, half * 2f, half * 2f);
                }

                Draw.flush();
                Draw.sort(true);

                Vars.world = ow;

                // 恢复 camera 和投影
                cam.set(camX, camY);
                Core.camera.update();
                Draw.proj(proj);
            });
        }

        Draw.z(z);
    }

    // ===== 子世界原版化交互 =====
    //
    // 设计目标: 子世界和原世界的交互体验完全一样, 不用任何自定义 UI:
    // <ul>
    //   <li>悬停信息 → 原版右下角面板 (PlacementFragment), 由 WorldUnitEntity.display()
    //       委托给光标下的子世界建筑; 悬停建筑变化时反射失效面板缓存强制重建</li>
    //   <li>点击 → 原版配置界面 / 物品界面 (与原版点击方块相同)</li>
    //   <li>放置/拆除 → 平台区域整体接管: 光标在平台范围内时作用于子世界,
    //       预览吸附到子世界网格 (连续坐标映射, 支持平台自由移动和旋转)</li>
    //   <li>让位规则: 主世界该处有建筑时不接管 (玩家操作的是地面建筑)</li>
    // </ul>

    /** 当前悬停的子世界建筑 (悬停高亮 + 信息面板刷新用; drawBody 读取) */
    static Building hoveredSubBuild;
    /** 上次触发面板刷新的建筑 (变化时才反射刷新原版信息面板) */
    private static Building lastPanelBuild;
    /** 原版 PlacementFragment.lastDisplayState 字段缓存 (反射强制刷新悬停面板) */
    private static Field lastDisplayStateField;

    // ===== 建造接管状态 =====

    /** 建造预览状态 (handleSubWorldBuildInput 每帧写入, drawBody 读取绘制) */
    private static WorldUnitEntity buildPreviewUnit;
    private static Block buildPreviewBlock;
    /** 子世界内部朝向 (已扣除单位朝向, 渲染投影后与玩家选的朝向一致) */
    private static int buildPreviewRot;
    private static int buildPreviewX, buildPreviewY;
    private static boolean buildPreviewValid;
    /** 防按住拆解键重复拆除 (子世界 tile packed 坐标) */
    private static int lastBreakPos = -1;
    /** 临时坐标缓冲 (避免每帧分配) */
    private static final Vec2 tmpVec = new Vec2();

    /**
     * 主世界朝向 → 子世界内部朝向换算.
     * <p>子世界渲染时投影旋转 r = 单位朝向 - 90°, 建筑视觉朝向 = 内部朝向×90° + r;
     * 要求视觉朝向 = 玩家选择的朝向, 反解内部朝向 (按最近 90° 步进近似)。</p>
     */
    private static int subRotation(WorldUnitEntity w, int rot) {
        int steps = Math.round(w.rotation / 90f) & 3;
        return Mathf.mod(rot - steps + 1, 4);
    }

    /**
     * 建造接管主逻辑 (每帧由 {@link #updateInteraction} 调用).
     * <p>光标落在平台范围内时, 放置/拆除/预览直接作用于子世界 ——
     * 平台可自由移动和旋转, 通过连续坐标映射 ({@link WorldUnitEntity#worldToSubPixel})
     * 把光标换算到子世界网格, 预览自动吸附。</p>
     */
    private static void handleSubWorldBuildInput(float mx, float my) {
        buildPreviewUnit = null;
        buildPreviewBlock = null;
        InputHandler in = Vars.control == null ? null : Vars.control.input;
        if (in == null || Vars.player == null || Vars.player.dead()) return;

        int mtx = World.toTile(mx), mty = World.toTile(my);

        // 查找光标所在的平台 (多单位时取第一个);
        // ★ 让位规则: 主世界该处 tile 上有建筑时不接管 (玩家想操作的是地面建筑)
        WorldUnitEntity hit = null;
        if (Vars.world.build(mtx, mty) == null) {
            for (Unit u : Groups.unit) {
                if (u instanceof WorldUnitEntity w && w.unitWorld != null
                    && w.team() == Vars.player.team()
                    && w.worldToSubPixel(mx, my, tmpVec)) {
                    hit = w;
                    break;
                }
            }
        }

        if (hit != null && !Core.scene.hasMouse()) {
            int tx = World.toTile(tmpVec.x), ty = World.toTile(tmpVec.y);

            // 即时放置: 按住放置键 → 放进子世界 (平台区域整体接管, 与主世界地形无关;
            // 重复按住由 canBuildSub 的占用检查天然去重)
            if (in.isPlacing() && in.block != null && Core.input.keyDown(Binding.select)) {
                hit.placeSub(in.block, tx, ty, subRotation(hit, in.rotation), null);
            }

            // 即时拆除: 按住拆解键, 光标每进入一个新 tile 拆一次 (拖动连拆)
            if (Core.input.keyDown(Binding.breakBlock)) {
                int pos = Point2.pack(tx, ty);
                if (pos != lastBreakPos) {
                    lastBreakPos = pos;
                    hit.breakSub(tx, ty);
                }
            } else {
                lastBreakPos = -1;
            }

            // 预览状态 (光标在平台范围内 → ghost 吸附到子世界网格)
            if (in.isPlacing() && in.block != null) {
                buildPreviewUnit = hit;
                buildPreviewBlock = in.block;
                buildPreviewRot = subRotation(hit, in.rotation);
                buildPreviewX = tx;
                buildPreviewY = ty;
                buildPreviewValid = hit.canBuildSub(in.block, tx, ty);
            }
        } else {
            lastBreakPos = -1;
        }

        // plans 清扫: 玩家建造队列中落在平台区域的计划 → 转译 (覆盖单点/拖线/蓝图粘贴),
        // 同时防止原版把这些方块建到平台下方的主世界 tile 上;
        // 让位规则同上: 该处主世界 tile 有建筑时保留原版计划
        Unit pu = Vars.player.unit();
        if (pu != null && pu.plans.size > 0) {
            for (int i = pu.plans.size - 1; i >= 0; i--) {
                BuildPlan plan = pu.plans.get(i);
                if (Vars.world.build(plan.x, plan.y) != null) continue;
                WorldUnitEntity owner = null;
                for (Unit u : Groups.unit) {
                    if (u instanceof WorldUnitEntity w && w.unitWorld != null
                        && w.team() == Vars.player.team()
                        && w.worldToSubPixel(plan.x * Vars.tilesize + Vars.tilesize / 2f,
                                             plan.y * Vars.tilesize + Vars.tilesize / 2f, tmpVec)) {
                        owner = w;
                        break;
                    }
                }
                if (owner == null) continue;
                pu.plans.removeIndex(i);
                int ptx = World.toTile(tmpVec.x), pty = World.toTile(tmpVec.y);
                if (plan.breaking) {
                    owner.breakSub(ptx, pty);
                } else if (plan.block != null) {
                    owner.placeSub(plan.block, ptx, pty,
                        subRotation(owner, plan.rotation), plan.config);
                }
            }
        }
    }

    /**
     * 反射清空原版 PlacementFragment.lastDisplayState, 强制悬停信息面板重建.
     * <p>面板按 displayState 对象是否变化决定是否重建 —— 悬停平台上的不同建筑时
     * displayState 恒为本单位, 需手动失效才能切换显示的建筑。</p>
     */
    private static void invalidateHoverPanel() {
        try {
            if (lastDisplayStateField == null) {
                lastDisplayStateField = PlacementFragment.class.getDeclaredField("lastDisplayState");
                lastDisplayStateField.setAccessible(true);
            }
            lastDisplayStateField.set(ui.hudfrag.blockfrag, null);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 每帧更新: 子世界原版化交互主入口.
     * <p>需要在模组初始化时用 Events.run(Trigger.update, WorldUnitType::updateInteraction) 注册。</p>
     */
    public static void updateInteraction() {
        if (!Vars.state.isPlaying()) return;

        float mx = Core.input.mouseWorldX(), my = Core.input.mouseWorldY();

        // ★ 建造接管: 光标在平台范围时, 放置/拆除/预览直接作用于子世界
        handleSubWorldBuildInput(mx, my);

        // 悬停检测: 查找光标下的子世界建筑
        hoveredSubBuild = null;
        if (!Core.scene.hasMouse()) {
            for (Unit unit : Groups.unit) {
                if (unit instanceof WorldUnitEntity w && w.unitWorld != null && !w.buildings.isEmpty()) {
                    Building b = w.buildingAt(mx, my);
                    if (b != null) {
                        hoveredSubBuild = b;
                        break;
                    }
                }
            }
        }

        // ★ 悬停建筑变化 → 反射刷新原版信息面板 (面板重建时调用单位 display() → 委托给悬停建筑)
        if (hoveredSubBuild != lastPanelBuild) {
            lastPanelBuild = hoveredSubBuild;
            invalidateHoverPanel();
        }

        // ★ 点击子世界建筑: 打开原版配置 UI / 物品界面 (同原版点击方块);
        // 放置/拆除模式下不拦截, 避免和原版操作冲突
        if (hoveredSubBuild != null) {
            boolean busy = Vars.control.input != null &&
                           (Vars.control.input.isPlacing() || Vars.control.input.isBreaking());
            if (Core.input.justTouched() && !Core.scene.hasMouse() && !busy) {
                Building found = hoveredSubBuild;
                if (found.block.configurable && found.shouldShowConfigure(Vars.player)) {
                    Vars.control.input.config.showConfig(found);
                } else {
                    // 非配置建筑: 显示物品栏
                    Vars.control.input.inv.showFor(found);
                }
            }
        }
    }

    /**
     * 注册交互系统: 挂载 Trigger.update 回调。
     * <p>世界单位的子世界建筑不在主世界 tile 上, 原版输入系统找不到它们,
     * 因此通过每帧轮询实现悬停/点击/建造交互 (与 PU132 原版思路一致)。</p>
     */
    public static void registerInteraction() {
        Events.run(Trigger.update, WorldUnitType::updateInteraction);
    }
}
