package zzw.content.mechanics.torque.ui.dialogs;

import arc.graphics.Color;
import arc.util.Strings;
import mindustry.graphics.Pal;
import mindustry.ui.dialogs.BaseDialog;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;
import zzw.content.mechanics.torque.meta.MeltInfo;

import static arc.Core.*;
import static mindustry.Vars.iconSmall;

/**
 * 坩埚对话框 (PU132 unity.ui.dialogs.CrucibleDialog 移植)
 * <p>
 * 显示坩埚网络的温度条 (IconBar) 和内容物堆叠图 (StackedBarChart)。
 * <p>
 * 移植增强: 追加"熔点参考"列表 —— 列出所有可熔物品的图标 / 名称 / 熔点 (°C),
 * 玩家无需查资料即可知道坩埚该烧到多少度 (参考 PU_V8 信息面板的完整描述风格)。
 * <p>
 * 适配说明: PU132 原版接收 CrucibleBuild, 此处接收 GraphBuildBase 接口
 * (任何配置了 GraphCrucible 的图方块 build 均可打开), 解耦对具体方块类的依赖。
 */
public class CrucibleDialog extends BaseDialog{
    private final GraphBuildBase build;

    public CrucibleDialog(GraphBuildBase build){
        super("@info.title");

        this.build = build;

        shown(() -> {
            app.post(this::setup);
        });

        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        cont.clear();
        buttons.clear();

        float w = graphics.isPortrait() ? 320f : 640f;

        cont.table(t -> {
            Runnable set = () -> { //TODO also show the contents in a form of list
                t.clearChildren();
                t.left();

                t.label(() -> bundle.get("stat.unity.crucible.temp", "Crucible Temperature")).color(Pal.accent).growX().row();
                t.add(build.crucible().getIconBar()).padTop(4f).growX().row();

                t.label(() -> bundle.get("stat.unity.crucible.contents", "Crucible Contents")).color(Pal.accent).growX().row();
                t.add(build.crucible().getStackedBars()).padTop(4f).growX().row();

                // ★ 熔点参考列表: 所有可熔物品的图标 + 名称 + 熔点 (°C)
                //   跳过碳等中间产物 (item=null) 和煤/石墨添加剂 (additive=true)
                t.label(() -> bundle.get("stat.unity.crucible.meltlist", "Melt Points")).color(Pal.accent).growX().padTop(8f).row();
                t.table(list -> {
                    list.left().top();
                    for(MeltInfo m : MeltInfo.all){
                        if(m == null || m.item == null || m.additive || m.meltPoint <= 0f) continue;

                        list.image(m.item.uiIcon).size(iconSmall).padRight(6f);
                        list.add(m.item.localizedName).color(Color.lightGray);
                        list.add(Strings.fixed(m.meltPoint - 273f, 0) + "°C").color(Color.lightGray).padLeft(8f).row();
                    }
                }).padTop(4f).growX();
            };

            set.run();
            t.update(() -> { //TODO detect whether things have changed or not
                set.run();
            });
        }).width(w);

        addCloseButton();
    }
}
