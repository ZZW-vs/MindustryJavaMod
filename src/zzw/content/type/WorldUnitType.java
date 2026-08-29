package zzw.content.type;

import arc.graphics.Color;
import arc.Graphics.Cursor.SystemCursor;
import arc.graphics.g2d.Batch;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.SpriteBatch;
import arc.math.Mat;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
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
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.input.Binding;
import mindustry.input.DesktopInput;
import mindustry.input.InputHandler;
import mindustry.ui.Styles;
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

    /**
     * 子世界渲染专用 batch (PU132 UnityDrawf.altBatch 方案, v155.4 适配).
     * <p>独立 SpriteBatch + Draw.sort(true) 顶点按 z 排序 —— 建筑内部自由切层
     * (炮台热度/电力连线/传送带物品动画等) 与原版渲染管线一致; 切换 Core.batch
     * 期间绘制的所有内容在主 batch 之后 flush, 天然显示在单位甲板之上。
     * (PU132 原版用 arc 的 SortedSpriteBatch, v155.4 arc 无此类,
     * 普通 SpriteBatch 开 sort 模式即等价的顶点 z 排序)</p>
     */
    public static final Batch altBatch = new SpriteBatch();

    public WorldUnitType(String name) {
        super(name);
        constructor = WorldUnitEntity::create;
    }

    /**
     * 重写drawBody: 在正常单位渲染后, 绘制子世界中的建筑物
     * <p>渲染 hack 原理 (PU132 UnityUnitType.drawBody):
     * <ol>
     *   <li>保存当前 camera.position 和 Draw.proj()</li>
     *   <li>计算偏移让子世界中心对齐单位平台位置 (subCX/subCY 含网格偏移)</li>
     *   <li>切换 Core.batch 到 SortedSpriteBatch, Draw.proj(camera) + rotate(r)</li>
     *   <li>Draw.sort(true) 开启 z 排序, 每个建筑画前重置 Draw.z(Layer.block)</li>
     *   <li>透明 quad 强制 batch 走完整混合管线 (PU132 blend 修复 trick)</li>
     *   <li>flush 后切回主 batch, 恢复 camera 和 proj</li>
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

                // ★ 子世界中心 (8x18 网格居中, 无偏移)
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

                // ★ PU132 渲染方案: 切换到 SortedSpriteBatch —— 建筑内部 Draw.z 切层
                //   生效 (动画/发光/连线正确分层), flush 在单位之后 → 建筑整体在甲板之上
                Batch old = Core.batch;
                Core.batch = altBatch;

                Draw.proj(Core.camera);

                Draw.proj().rotate(r);

                Draw.sort(true);

                // ★ 电力连接线修复: 建筑的 draw() 内部会用 world.build(links) 查链接目标
                //   (如 PowerNode 的激光连线), 渲染期间切到子世界, 查询才落在子世界建筑上
                World ow = Vars.world;
                Vars.world = world;

                // ★ 护盾半透明修复: animateShields 开启时原版护盾走 Renderer 的
                //   drawRange(Layer.shields) → effectBuffer 离屏缓冲 + Shaders.shield
                //   后处理管线 (半透明流光外观); 子世界手动直渲染没走该管线,
                //   Fill.poly 以 alpha=1 纯色画出 → 护盾/力墙变成不透明"图形"。
                //   渲染期间临时关闭 → 护盾走半透明分支 (0.09 alpha 填充 + 描边),
                //   画完立即恢复, 不影响主世界护盾动画
                boolean oldAnimateShields = Vars.renderer.animateShields;
                Vars.renderer.animateShields = false;

                // ★ 建造模式: 子世界边缘呼吸虚线框 (提示当前可建造/拆除)
                //   虚线框相对平台往左下各偏移半格, 与实际网格覆盖区域一致
                //   (gridOffX/Y = -0.5 格, 平台原点在中心偏右上)
                if (w.buildMode) {
                    Draw.z(Layer.plans + 1f);
                    Draw.color(Pal.accent, 0.6f + Mathf.absin(Time.time, 4f, 0.4f));
                    Drawf.dashRect(Pal.accent, -Vars.tilesize / 2f - 2f, -Vars.tilesize / 2f - 2f, w.platW() + 4f, w.platH() + 4f);
                    Draw.color();
                }

                // 绘制建筑 (含传送带物品、炮台旋转等内部动画; 每个建筑前重置 z,
                // 建筑内部再自由切到 turretHeat/power 等层)
                for (int i = 0; i < build.size; i++) {
                    Building b = build.get(i);
                    Draw.z(Layer.block);
                    b.draw();
                }

                Vars.renderer.animateShields = oldAnimateShields;

                // ★ 建造光束: 单位中心 → 正在建造/拆除的脚手架 (原版 BuilderComp.drawBuildingBeam 观感:
                // 橙色三角光束 + 目标方块角标 + 单位端脉冲方点, 拆除时红色)
                for (int i = 0; i < build.size; i++) {
                    Building b = build.get(i);
                    if (!(b instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild cons)) continue;
                    boolean decons = cons.current == cons.previous;
                    float cx0 = w.subCX(), cy0 = w.subCY();
                    float rad = cons.block.size * Vars.tilesize / 2f;

                    Lines.stroke(1f, decons ? Pal.remove : Pal.accent);
                    Draw.z(Layer.buildBeam);
                    Draw.alpha(0.7f);
                    Drawf.buildBeam(cx0, cy0, b.x, b.y, rad);
                    Fill.square(cx0, cy0, 1.8f + Mathf.absin(Time.time, 2.2f, 1.1f), w.rotation + 45f);
                    Draw.reset();
                }

                // ★ 悬停高亮: 光标下的子世界建筑画选择框/射程圈 (复刻原版悬停效果)
                if (hoveredSubBuild != null && !hoveredSubBuild.dead
                    && w.ownsBuilding(hoveredSubBuild)) {
                    Draw.z(Layer.overlayUI);
                    hoveredSubBuild.drawSelect();
                }

                // ★ 放置预览: 鼠标在本子世界区域且处于放置模式 → 在子世界网格上画 ghost
                //   (吸附到子世界自己的网格; 在投影上下文中绘制, 坐标即子世界坐标)
                if (buildPreviewUnit == w && buildPreviewBlock != null) {
                    Draw.z(Layer.plans);
                    Block pb = buildPreviewBlock;
                    // ★ 与原版 BuildPlan.drawx() 完全一致: tile 参考点 + 多方块偏移
                    //   (= tile*8 + offset, 建筑实际落位 drawx 同公式, ghost 与实位零偏差)
                    float px = buildPreviewX * Vars.tilesize + pb.offset;
                    float py = buildPreviewY * Vars.tilesize + pb.offset;

                    // ghost 贴图 (呼吸透明度, 同原版预览观感)
                    Draw.alpha(0.45f + Mathf.absin(Time.time, 3f, 0.1f));
                    Draw.rect(pb.fullIcon, px, py, pb.rotate ? buildPreviewRot * 90f : 0f);
                    Draw.alpha(1f);

                    // 无效位置: ghost 上叠半透明红色方块 (原版 drawPlanTop 无效计划反馈风格,
                    // 不再画虚线框; 建造模式下原版鼠标预览已被输入补丁抑制, 仅此一处预览)
                    if (!buildPreviewValid) {
                        Draw.color(Pal.remove, 0.3f);
                        Fill.square(px, py, pb.size * Vars.tilesize / 2f);
                        Draw.color();
                    }
                }

                // blend 修复 trick (PU132): 透明 quad 强制 batch 走完整混合管线,
                // 防止上一个 draw 的混合状态污染
                Draw.z(9999f);
                Draw.color(Color.clear);
                Fill.rect(0, 0, 0, 0);

                Draw.reset();
                Draw.flush();
                Draw.sort(false);

                Vars.world = ow;

                // 恢复 camera 和投影
                cam.set(camX, camY);
                Core.camera.update();
                Draw.proj(proj);
                Core.batch = old;
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

    // ===== 高亮系统 (拒绝实体化时提示罪魁方块) =====

    /** 当前高亮的建筑 (召唤被拒时高亮多余的 TerraCore) */
    private static Building highlightBuild;
    /** 高亮剩余时间 (秒) */
    private static float highlightTimer = 0f;

    /**
     * 高亮指定建筑 3 秒 (闪烁选中框 + 脉冲填充, 原版 Drawf.selected 风格).
     * <p>TerraCore 召唤检查失败时调用, 告诉玩家是哪个大地核心导致无法组装。</p>
     */
    public static void highlightBlock(Building b) {
        highlightBuild = b;
        highlightTimer = 3f;
    }

    /** 高亮渲染 (每帧由 Trigger.draw 调用) */
    private static void drawHighlight() {
        if (highlightBuild == null || highlightTimer <= 0f) return;
        highlightTimer -= Time.delta;
        Building b = highlightBuild;
        if (b.dead || b.tile == null || b.tile.build != b) {
            highlightBuild = null;
            return;
        }

        Draw.z(Layer.overlayUI);
        float half = b.block.size * Vars.tilesize / 2f;
        float pulse = Mathf.absin(Time.time, 5f, 1f);

        // 呼吸填充 + 闪烁线框 (原版选中/悬停高亮的观感)
        Draw.color(Pal.accent, 0.25f + Mathf.absin(Time.time, 6f, 0.15f));
        Fill.square(b.x, b.y, half);
        Draw.color(Pal.accent);
        Lines.stroke(2f);
        Lines.square(b.x, b.y, half + 2f + pulse);
        Draw.reset();
    }

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

        // ★ 建造模式 gate: 玩家附生该单位且建造模式已激活才能建造/拆除
        //   (未附生/未激活时只保留方块交互: 悬停/点击配置/物品界面)
        if (hit != null && hit.buildMode && hit == Vars.player.unit() && !Core.scene.hasMouse()) {
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

        // plans 清扫 (覆盖单点/拖线/蓝图粘贴): 建造模式的单位转译进子世界;
        // 未激活的单位也移除计划 —— 防止原版玩家单位把方块建到平台下方的主世界 tile 上
        sweepPlans();
    }

    /**
     * plans 清扫: 玩家建造队列中落在平台区域的计划, 按所属单位分别处理.
     * <p>★ 锚点布局保持: 同一批计划 (拖线/蓝图) 在主世界网格上是直线/矩形布局,
     * 逐点独立映射到旋转的子世界网格会变成"斜线" —— 这里以第一个落在平台的计划为锚点,
     * 其余计划按【相对锚点的 tile 偏移旋转到子世界朝向】落位, 保持布局的相对形状
     * (拖线仍是直线, 蓝图不扭曲)。</p>
     * <p>让位规则: 该处主世界 tile 有建筑时保留原版计划 (玩家想操作的是地面建筑)。</p>
     */
    private static void sweepPlans() {
        Unit pu = Vars.player.unit();
        if (pu == null || pu.plans.size <= 0) return;

        // ★ 附生建造模式的大地单位: 主世界建造完全屏蔽 (保留"只能往子世界放方块"特性) ——
        //   canBuild 覆写放行后原版输入会往 plans 里塞主世界计划, 这里把平台外的计划
        //   全部清掉, 平台上的计划照常转译进子世界; terra buildSpeed=0 无法自行推进,
        //   残留的主世界计划会让 BuilderComp 生成永远卡在 0% 的脚手架
        boolean controllingBuild = pu instanceof WorldUnitEntity cw && cw.buildMode;

        // 锚点: 第一个落在平台内 (且建造模式) 的计划; 同帧内同批计划围绕锚点布局
        WorldUnitEntity anchorOwner = null;
        int anchorPX = 0, anchorPY = 0, anchorSX = 0, anchorSY = 0;

        for (int i = pu.plans.size - 1; i >= 0; i--) {
            BuildPlan plan = pu.plans.get(i);
            // 让位: 主世界该处有建筑 → 保留原版计划 (玩家想操作的是地面建筑);
            //   附生建造模式时例外 —— 主世界计划一律清除, 建造全部收归子世界
            if (!controllingBuild && Vars.world.build(plan.x, plan.y) != null) continue;

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
            if (owner == null) {
                // 平台之外的纯主世界计划: 附生建造模式 → 移除 (屏蔽主世界建造)
                if (controllingBuild) pu.plans.removeIndex(i);
                continue;
            }

            pu.plans.removeIndex(i);

            // ★ 未激活建造模式 → 只移除 (防止主世界误建), 不转译
            if (!owner.buildMode) continue;

            int ptx, pty;
            if (anchorOwner != owner) {
                // 锚点重置: 新单位或第一批计划的第一个 → 光标映射落位
                ptx = World.toTile(tmpVec.x);
                pty = World.toTile(tmpVec.y);
                anchorOwner = owner;
                anchorPX = plan.x;
                anchorPY = plan.y;
                anchorSX = ptx;
                anchorSY = pty;
            } else {
                // 同批后续计划: 相对锚点的主世界 tile 偏移 → 旋转到子世界朝向 → 平移到锚点
                float dx = (plan.x - anchorPX) * Vars.tilesize;
                float dy = (plan.y - anchorPY) * Vars.tilesize;
                tmpVec.set(dx, dy).rotate(-(owner.rotation - 90f));
                ptx = World.toTile(anchorSX * Vars.tilesize + tmpVec.x);
                pty = World.toTile(anchorSY * Vars.tilesize + tmpVec.y);
            }

            if (plan.breaking) {
                owner.breakSub(ptx, pty);
            } else if (plan.block != null) {
                owner.placeSub(plan.block, ptx, pty,
                    subRotation(owner, plan.rotation), plan.config);
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

        // ★ 建造权限与附生绑定: 未被玩家附生的大地单位自动关闭建造模式 ——
        //   玩家一次只能附生一个单位, 天然保证"最多一个大地核心处于建造状态";
        //   玩家脱离附生 (换控其他单位/死亡) 时立即收回建造权限
        Unit pu = Vars.player.unit();
        for (Unit u : Groups.unit) {
            if (u instanceof WorldUnitEntity w && w.buildMode && u != pu) {
                w.buildMode = false;
            }
        }

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

                // ★ 点击主大地核心 → 弹出建造模式开关按钮 (不打开配置界面)
                WorldUnitEntity coreOwner = null;
                for (Unit unit : Groups.unit) {
                    if (unit instanceof WorldUnitEntity w && w.mainCore == found) {
                        coreOwner = w;
                        break;
                    }
                }
                if (coreOwner != null) {
                    // ★ 建造模式按钮仅对附生该单位的玩家开放 (未附生时点主核心无反应 ——
                    //   建造/拆除权限全部与附生状态绑定, 防止远处遥控开启建造模式)
                    if (coreOwner == Vars.player.unit()) {
                        showBuildModePrompt(coreOwner);
                    }
                    return;
                }

                // ★ 延迟打开配置界面 (Core.app.post): Trigger.update 早于原版输入处理,
                //   同帧原版点击空地会触发 tileTapped(null) → hideConfig 把刚打开的
                //   界面立即关掉 (表现为"配置界面打不开"); post 到帧末尾打开则不会被关
                Core.app.post(() -> {
                    if (found.dead) return;
                    if (found.block.configurable && found.shouldShowConfigure(Vars.player)) {
                        Vars.control.input.config.showConfig(found);
                    } else {
                        // 非配置建筑: 显示物品栏
                        Vars.control.input.inv.showFor(found);
                    }
                });
            }
        }
    }

    // ===== 建造模式开关按钮 (点击主核心弹出) =====

    /** 当前弹出的建造模式按钮面板 */
    private static Table buildModeTable;
    /** 面板对应的单位 */
    private static WorldUnitEntity buildModeUnit;
    /** 跳过弹出当次点击 (防止弹出帧的 justTouched 立即触发"点击外部收起") */
    private static boolean suppressPromptClick = false;

    /**
     * 弹出建造模式开关按钮面板 (屏幕中下方, 点击主核心时调用).
     * <p>按钮文本随当前状态切换: "进入建造模式" / "退出建造模式";
     * 点击按钮 toggle buildMode 并收起; 点击面板外任意处也收起;
     * 单位死亡自动收起。</p>
     */
    private static void showBuildModePrompt(WorldUnitEntity w) {
        hideBuildModePrompt();
        buildModeUnit = w;
        suppressPromptClick = true;

        buildModeTable = new Table(Tex.buttonEdge2);
        buildModeTable.defaults().pad(4f);
        buildModeTable.add("[accent]" + w.type.localizedName).padBottom(4f).row();
        buildModeTable.button(w.buildMode ? "退出建造模式" : "进入建造模式", () -> {
            w.buildMode = !w.buildMode;
            // ★ 唯一性: 开启时自动关闭其他大地核心的建造模式 (一次最多一个处于建造状态)
            if (w.buildMode) {
                for (Unit u : Groups.unit) {
                    if (u instanceof WorldUnitEntity o && o != w && o.buildMode) {
                        o.buildMode = false;
                    }
                }
            }
            hideBuildModePrompt();
        }).size(180f, 44f);

        buildModeTable.update(() -> {
            // 单位死亡 / 核心丢失 / 玩家脱离附生 → 收起面板
            if (buildModeUnit == null || buildModeUnit.dead || !buildModeUnit.isAdded()
                || buildModeUnit.mainCore == null || buildModeUnit.mainCore.dead
                || Vars.player.unit() != buildModeUnit) {
                hideBuildModePrompt();
                return;
            }
            // 跳过弹出当次点击
            if (suppressPromptClick) {
                suppressPromptClick = false;
                return;
            }
            // 点击面板外 (游戏世界) → 收起; 点击面板本身由按钮 handler 处理
            if (Core.input.justTouched() && !Core.scene.hasMouse()) {
                hideBuildModePrompt();
            }
        });

        buildModeTable.pack();
        Core.scene.add(buildModeTable);
        buildModeTable.setTranslation(Core.scene.getWidth() / 2f - buildModeTable.getWidth() / 2f,
                                      Core.scene.getHeight() * 0.3f);
        buildModeTable.toFront();
    }

    /** 收起建造模式按钮面板 */
    private static void hideBuildModePrompt() {
        if (buildModeTable != null) {
            buildModeTable.remove();
            buildModeTable = null;
        }
        buildModeUnit = null;
    }

    /**
     * 注册交互系统: 挂载 Trigger.update (交互轮询) 和 Trigger.draw (高亮渲染) 回调。
     * <p>世界单位的子世界建筑不在主世界 tile 上, 原版输入系统找不到它们,
     * 因此通过每帧轮询实现悬停/点击/建造交互 (与 PU132 原版思路一致)。</p>
     */
    public static void registerInteraction() {
        Events.run(Trigger.update, WorldUnitType::updateInteraction);
        Events.run(Trigger.draw, WorldUnitType::drawHighlight);
        installInputPatch();
    }

    /** 输入补丁是否已安装 (ClientLoadEvent 只触发一次, 标记防重入) */
    private static boolean inputPatched = false;

    /**
     * 替换桌面输入处理器, 抑制建造模式下的原版主世界网格建造渲染.
     * <p>★ 双预览问题: 玩家附生大地单位开启建造模式后, 原版 DesktopInput 会按
     * 主世界网格吸附在鼠标处画半透明 ghost, 而子世界网格 (随平台旋转) 上还有
     * drawBody 画的另一个预览 —— 两个预览位置/角度不一致, 视觉非常混乱。
     * 子世界预览吸附正确, 因此建造模式期间抑制原版预览, 只保留子世界预览。</p>
     * <p>★ 批量删除红框: 原版拆除时 drawTop() 里 drawBreakSelection 会按主世界
     * 网格画红色选择框 —— 子世界拆除由子世界网格接管, 该红框位置不对且视觉冗余,
     * 建造模式下整体跳过 drawTop 的选择框绘制。</p>
     * <p>原理: 建造模式下直接跳过原版 drawBottom()/drawTop() 的绘制分支 ——
     * 原版 ghost (isPlacing)、拖线预览 (linePlans)、蓝图预览 (selectPlans)、
     * 拆除红框 (drawBreakSelection) 全部不画; 输入逻辑 (update 阶段) 不受影响,
     * plans 由 sweepPlans 转译进子世界。</p>
     */
    private static void installInputPatch() {
        if (inputPatched || Vars.control == null || Vars.control.input == null) return;
        InputHandler old = Vars.control.input;
        // 仅接管原版桌面输入 (移动端输入流程不同不处理; 已是补丁实例则跳过)
        if (old.getClass() != DesktopInput.class) return;

        // 与原版 Control.setInput 相同的换装流程: 摘下旧处理器 → 装上新处理器
        boolean added = Core.input.getInputProcessors().contains(old);
        old.remove();
        DesktopInput patched = new DesktopInput() {
            /** 建造模式激活 (玩家附生大地单位且已开启) */
            private boolean subBuildMode() {
                Unit u = Vars.player.unit();
                return u instanceof WorldUnitEntity w && w.buildMode;
            }

            @Override
            public void drawBottom() {
                // 建造模式: 原版主世界网格的全部建造预览整体跳过
                // (ghost / 拖线 linePlans / 蓝图 selectPlans / splan —— 只保留子世界网格预览;
                //  已放置计划的选中框也不受影响, 建造模式下 plans 已被 sweepPlans 清空)
                if (subBuildMode()) return;
                super.drawBottom();
            }

            @Override
            public void drawTop() {
                // 建造模式: 跳过批量拆除红框 (drawBreakSelection) 和蓝图选框 ——
                // 拆除由子世界网格接管, 原版红框按主世界网格画, 位置不对且视觉混乱;
                // 保留光标类型重置 (跳过会残留上帧的非箭头光标)
                if (subBuildMode()) {
                    if (cursorType != SystemCursor.arrow && Core.scene.hasMouse()) {
                        Core.graphics.cursor(cursorType = SystemCursor.arrow);
                    }
                    Draw.reset();
                    return;
                }
                super.drawTop();
            }
        };
        patched.block = old.block;
        Vars.control.input = patched;
        if (added) patched.add();
        inputPatched = true;
    }
}
