package zzw.ui;

import arc.Core;
import arc.Events;
import arc.math.Interp;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.ui.layout.Table;
import mindustry.content.Blocks;
import mindustry.game.EventType.ResetEvent;
import mindustry.gen.Building;

import static mindustry.Vars.player;
import static mindustry.Vars.tilesize;

/**
 * 子世界建筑配置界面 (v158 BlockConfigFragment 子世界适配版).
 *
 * <p>为什么需要独立 Fragment: 原版配置可视化 (电力节点范围圈/可连节点方块) 由
 * OverlayRenderer 在主世界上下文调用 {@code config.getSelected().drawConfigure()} ——
 * 子世界建筑的 x/y 是子世界坐标, 会画到地图原点、world.build 查询也落在主世界。
 * 子世界建筑改用本 Fragment 打开配置后, 原版 OverlayRenderer 不再感知 (isShown 恒 false),
 * 可视化改由 WorldUnitType.drawBody 在投影 + 子世界上下文中绘制。</p>
 *
 * <p>定位: 原版 updateTableAlign 按建筑 x/y 换算屏幕坐标 (子世界坐标 → 地图原点),
 * 这里改用投影后的主世界坐标 (WorldUnitType 注入的解析器)。</p>
 */
public class SubConfigFragment{
    Table table = new Table();
    Building selected;

    public void build(Group parent){
        table.visible = false;
        parent.addChild(table);

        Events.on(ResetEvent.class, e -> forceHide());
    }

    public void forceHide(){
        table.visible = false;
        selected = null;
    }

    public boolean isShown(){
        return table.visible && selected != null;
    }

    public Building getSelected(){
        return selected;
    }

    public void showConfig(Building tile){
        if(selected != null) selected.onConfigureClosed();
        if(tile.configTapped()){
            selected = tile;

            table.visible = true;
            table.clear();
            table.background(null);
            tile.buildConfiguration(table);
            table.pack();
            table.setTransform(true);
            table.actions(Actions.scaleTo(0f, 1f), Actions.visible(true),
            Actions.scaleTo(1f, 1f, 0.07f, Interp.pow3Out));

            table.update(() -> {
                if(selected != null && selected.shouldHideConfigure(player)){
                    hideConfig();
                    return;
                }

                table.setOrigin(arc.util.Align.center);
                if(selected == null || selected.block == Blocks.air || !selected.isValid()){
                    hideConfig();
                }else{
                    updateAlign();
                }
            });
        }
    }

    /** 子世界建筑: 用投影后的主世界坐标定位 (等价原版 updateTableAlign 公式) */
    private void updateAlign(){
        Vec2 proj = SubInventoryFragment.subPositionResolver == null
                    ? null : SubInventoryFragment.subPositionResolver.get(selected);
        if(proj != null){
            Vec2 pos = Core.input.mouseScreen(proj.x,
                proj.y - selected.block.size * tilesize / 2f - 1);
            table.setPosition(pos.x, pos.y, arc.util.Align.top);
        }else{
            // 兜底: 解析器未注入 (理论上不发生), 行为同原版
            selected.updateTableAlign(table);
        }
    }

    public boolean hasConfigMouse(){
        Element e = Core.scene.getHoverElement();
        return e != null && (e == table || e.isDescendantOf(table));
    }

    public void hideConfig(){
        if(selected != null) selected.onConfigureClosed();
        selected = null;
        table.actions(Actions.scaleTo(0f, 1f, 0.06f, Interp.pow3Out), Actions.visible(false));
    }
}
