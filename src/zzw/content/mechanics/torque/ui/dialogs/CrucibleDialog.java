package zzw.content.mechanics.torque.ui.dialogs;

import mindustry.graphics.Pal;
import mindustry.ui.dialogs.BaseDialog;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;

import static arc.Core.*;

/**
 * 坩埚对话框 (PU132 unity.ui.dialogs.CrucibleDialog 移植)
 * <p>
 * 显示坩埚网络的温度条 (IconBar) 和内容物堆叠图 (StackedBarChart)。
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
                t.add(build.crucible().getStackedBars()).padTop(4f).growX();
            };

            set.run();
            t.update(() -> { //TODO detect whether things have changed or not
                set.run();
            });
        }).width(w);

        addCloseButton();
    }
}
