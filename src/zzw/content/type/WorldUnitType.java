package zzw.content.type;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mat;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Tmp;
import arc.Core;
import arc.Events;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import zzw.content.units.entities.WorldUnitEntity;

import static mindustry.Vars.ui;

/**
 * 世界单位类型 (PU132 unity.type.UnityUnitType 中 Worldc 渲染部分移植)
 *
 * <p>继承 UnityUnitType, 添加:
 * <ul>
 *   <li>worldWidth / worldHeight: 子世界尺寸 (tile 数)</li>
 *   <li>drawBody() 渲染 hack: 让子世界中的建筑物跟随单位移动和旋转</li>
 * </ul>
 *
 * <p>适配 v155.4 改动:
 * <ul>
 *   <li>去掉 altBatch (用普通 batch, 接受可能的半透明排序 bug)</li>
 *   <li>constructor 设为 WorldUnitEntity::create</li>
 *   <li>Draw.sort(false) 确保建筑和子弹在单位之上</li>
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
     * 重写 drawBody: 在正常单位渲染后, 绘制子世界中的建筑物
     * <p>渲染 hack 原理 (PU132 UnityUnitType.drawBody):
     * <ol>
     *   <li>保存当前 camera.position 和 Draw.proj()</li>
     *   <li>计算偏移让子世界中心对齐单位位置</li>
     *   <li>Draw.proj(camera) + Draw.proj().rotate(r) 旋转投影</li>
     *   <li>Draw.sort(false) 关闭 z 排序, 按 call order 绘制</li>
     *   <li>遍历 buildings 调用 b.draw() (建筑内部自定 z)</li>
     *   <li>遍历 Groups.bullet 绘制子世界炮台发射的子弹</li>
     *   <li>恢复 camera 和 proj</li>
     * </ol>
     *
     * <p>★ 关键修复: 用 Draw.sort(false) 替代原来的 Draw.sort(true),
     * 确保所有建筑和子弹都在单位 body 之上绘制, 不会被 z 排序到单位下方。</p>
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

                float cx = world.width() * Vars.tilesize / 2f, cy = world.height() * Vars.tilesize / 2f;
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

                // ★ 关闭 z 排序: 按 call order 绘制, 确保建筑和子弹都在单位之上
                Draw.sort(false);

                // ★ 电力连接线修复: 建筑的 draw() 内部会用 world.build(links) 查链接目标
                //   (如 PowerNode 的激光连线), 渲染期间切到子世界, 查询才落在子世界建筑上
                mindustry.core.World ow = Vars.world;
                Vars.world = world;

                // 绘制建筑 (含传送带物品、炮台旋转等内部动画)
                for (int i = 0; i < build.size; i++) {
                    build.get(i).draw();
                }

                // 绘制子世界炮台发射的子弹 (在建筑之上)
                // 遍历所有子弹, 检查 owner 是否为子世界建筑 (O(1) 集合查询)
                for (Bullet bullet : Groups.bullet) {
                    Object owner = bullet.owner;
                    if (owner instanceof Building b && w.ownsBuilding(b)) {
                        bullet.draw();
                    }
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

    // ===== 鼠标交互 (不受单位碰撞箱限制) =====

    /** 当前悬停的子世界建筑 */
    private static Building hoveredBuild;
    /** 悬停信息表 */
    private static Table hoverTable;

    /**
     * 每帧更新: 检测鼠标是否在某个世界单位的子世界建筑上, 并处理交互。
     * <p>需要在模组初始化时用 Events.run(Trigger.update, WorldUnitType::updateInteraction) 注册。</p>
     * <ul>
     *   <li>悬停: 显示建筑信息 (名称/血量/物品/电力/液体), 跟随鼠标</li>
     *   <li>点击: 打开建筑配置 UI / 物品界面 (同原版点击方块)</li>
     * </ul>
     * <p>玩家正在放置方块或框选时 (input.isPlacing / input.isBreaking / input.isDragging)
     * 不拦截点击, 避免和原版操作冲突。</p>
     */
    public static void updateInteraction() {
        if (!Vars.state.isPlaying()) return;

        float mx = Core.input.mouseWorldX(), my = Core.input.mouseWorldY();
        Building found = null;

        // 遍历所有世界单位, 查找鼠标下的子世界建筑
        for (Unit unit : Groups.unit) {
            if (unit instanceof WorldUnitEntity w && w.unitWorld != null && !w.buildings.isEmpty()) {
                Building b = w.buildingAt(mx, my);
                if (b != null) {
                    found = b;
                    break;
                }
            }
        }

        if (found != null) {
            // 建筑变了 → 重建悬停表
            if (hoveredBuild != found) {
                hoveredBuild = found;
                if (hoverTable != null) hoverTable.remove();
                hoverTable = new Table(Styles.grayPanel);
                hoverTable.defaults().left().pad(2);
                hoverTable.image(found.block.uiIcon).size(24f);
                hoverTable.add(found.block.localizedName).left().row();
                hoverTable.add("[lightgray]HP: " + (int)found.health + "/" + (int)found.maxHealth).left().row();
                if (found.items != null && found.items.total() > 0) {
                    hoverTable.add("[lightgray]物品: " + found.items.total()).left().row();
                }
                if (found.power != null) {
                    hoverTable.add("[lightgray]电力: " + (int)(found.power.status * 100) + "%").left().row();
                }
                if (found.liquids != null && found.liquids.currentAmount() > 0) {
                    hoverTable.add("[lightgray]液体: " + (int)found.liquids.currentAmount()).left().row();
                }
                hoverTable.pack();
                Core.scene.add(hoverTable);
                hoverTable.toFront();
            }
            // 跟随鼠标定位
            if (hoverTable != null && hoverTable.parent != null) {
                float sx = Core.input.mouseX() + 16;
                float sy = Core.scene.getHeight() - Core.input.mouseY() - hoverTable.getHeight() - 8;
                hoverTable.setPosition(sx, sy);
            }

            // 点击: 打开配置 UI (同原版点击方块); 放置/拆除模式下不拦截
            boolean busy = Vars.control.input != null && (Vars.control.input.isPlacing() || Vars.control.input.isBreaking());
            if (Core.input.justTouched() && !Core.scene.hasMouse() && !busy) {
                if (found.block.configurable && found.shouldShowConfigure(Vars.player)) {
                    Vars.control.input.config.showConfig(found);
                } else {
                    // 非配置建筑: 显示物品栏
                    Vars.control.input.inv.showFor(found);
                }
            }
        } else {
            // ★ 让位检查: 鼠标处有主世界建筑时轮询层完全退出 ——
            //   部署后单位 hitbox 覆盖整个区域, 不让位会挡住原版的建筑 hover 提示和点击配置
            if (Vars.world.buildWorld(mx, my) != null) {
                hoveredBuild = null;
                if (hoverTable != null) {
                    hoverTable.remove();
                    hoverTable = null;
                }
                return;
            }

            // 鼠标不在子世界建筑上 → 检查是否悬停在 Terra 本体上
            WorldUnitEntity hoveredUnit = null;
            for (Unit unit : Groups.unit) {
                if (unit instanceof WorldUnitEntity w && w.unitWorld != null && unit.within(mx, my, unit.hitSize / 2f + 8f)) {
                    hoveredUnit = w;
                    break;
                }
            }

            if (hoveredUnit != null) {
                // 悬停 Terra 本体: 显示状态信息 (建筑数/部署状态)
                if (hoverTable == null || hoveredBuild == null) {
                    hoveredBuild = null; // 清除建筑引用, 标记当前是单位悬停
                    if (hoverTable != null) hoverTable.remove();
                    WorldUnitEntity w = hoveredUnit;
                    hoverTable = new Table(Styles.grayPanel);
                    hoverTable.defaults().left().pad(4);
                    hoverTable.add("[accent]" + w.type.localizedName + "[]").left().row();
                    hoverTable.add("[lightgray]子世界建筑: " + w.buildings.size + " 座").left().row();
                    hoverTable.add(w.deployed ? "[gold]已部署 - 点击单位打开控制面板[]" : "[lightgray]移动形态 - 点击单位打开控制面板[]").left();
                    hoverTable.pack();
                    Core.scene.add(hoverTable);
                    hoverTable.toFront();
                }
                // 跟随鼠标
                if (hoverTable != null && hoverTable.parent != null) {
                    float sx = Core.input.mouseX() + 16;
                    float sy = Core.scene.getHeight() - Core.input.mouseY() - hoverTable.getHeight() - 8;
                    hoverTable.setPosition(sx, sy);
                }

                // 点击本体: 打开部署/收起面板 (玩家正在操控该单位时不拦截, 避免影响移动指令)
                boolean busy = Vars.control.input != null && (Vars.control.input.isPlacing() || Vars.control.input.isBreaking());
                if (Core.input.justTouched() && !Core.scene.hasMouse() && !busy && !hoveredUnit.isPlayer()) {
                    showUnitPanel(hoveredUnit);
                }
            } else {
                // 鼠标不在任何子世界建筑/世界单位上
                hoveredBuild = null;
                if (hoverTable != null) {
                    hoverTable.remove();
                    hoverTable = null;
                }
            }
        }
    }

    /**
     * 打开世界单位控制面板: 部署 / 收起.
     * <p>部署: 子世界建筑落到主世界真实 tile, 原版输入 (建造菜单/配置/框选/修理) 全部可用;
     * 收起: 范围内主世界建筑吸回子世界, 单位恢复移动。</p>
     */
    public static void showUnitPanel(WorldUnitEntity w) {
        BaseDialog dialog = new BaseDialog("Terra 控制");

        dialog.cont.add("[accent]" + w.type.localizedName).row();
        dialog.cont.image().color(Color.gray).fillX().pad(6f).row();
        dialog.cont.add(w.deployed
            ? "[gold]当前状态: 已部署[]\n[lightgray]建筑已落在主世界, 可用原版建造菜单和交互。\n收起后单位才能移动。"
            : "[lightgray]当前状态: 移动形态\n子世界建筑跟随单位移动, 点击建筑可查看和配置。\n部署后建筑落到地面, 原版交互全部可用。"
        ).labelAlign(Align.center).pad(8f).row();

        // 部署/收起按钮
        dialog.cont.button(w.deployed ? "收起建筑" : "部署建筑", () -> {
            if (w.deployed) {
                int n = w.undeploy();
                ui.showInfoFade("已收起 " + n + " 座建筑");
            } else {
                int n = w.deploy();
                ui.showInfoFade(n > 0 ? "已部署 " + n + " 座建筑" : "没有可部署的建筑");
            }
            dialog.hide();
        }).size(220f, 50f).pad(8f).row();

        dialog.cont.button("@close", dialog::hide).size(220f, 44f).pad(4f);
        dialog.show();
    }

    /**
     * 注册交互系统: 挂载 Trigger.update 回调。
     * <p>世界单位的子世界建筑不在主世界 tile 上, 原版输入系统找不到它们,
     * 因此通过每帧轮询实现悬停/点击交互 (与 PU132 原版思路一致)。</p>
     * <p>部署态时建筑在主世界 tile 上, 由原版输入直接处理, 本层自动让位。</p>
     */
    public static void registerInteraction() {
        Events.run(Trigger.update, WorldUnitType::updateInteraction);
    }
}
