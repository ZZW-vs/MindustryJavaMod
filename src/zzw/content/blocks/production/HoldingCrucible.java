package zzw.content.blocks.production;

import arc.graphics.g2d.Draw;
import arc.scene.ui.layout.Table;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;
import zzw.content.mechanics.torque.modules.GraphHeatModule;
import zzw.content.mechanics.torque.ui.dialogs.CrucibleDialog;

/**
 * 坩埚容器 (PU132 unity.world.blocks.production.HoldingCrucible 移植)
 * <p>继承完整版 GraphBlock (zzw.content.mechanics.torque)。drawContents() 显示坩埚液体颜色,
 * UnityDrawf.drawHeat 渲染热力叠加, 使用 GraphBuildBase.crucible() 获取 GraphCrucibleModule。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>unity.world.blocks.GraphBlock → zzw.content.mechanics.torque.blocks.GraphBlock (完整版)</li>
 *   <li>unity.graphics.UnityDrawf → zzw.content.graphics.UnityDrawf</li>
 *   <li>unity.world.modules.GraphCrucibleModule → zzw.content.mechanics.torque.modules.GraphCrucibleModule</li>
 *   <li>增加 crucible()/heat() 的 null 检查, 防止未配置图时崩溃</li>
 * </ul></p>
 *
 * <p>注册配置 (PU132 UnityBlocks.java L2981-2987):
 * <pre>{@code
 * holdingCrucible = new HoldingCrucible("holding-crucible"){{
 *     requirements(Category.crafting, with(...));
 *     size = 4;
 *     health = 2400;
 *     addGraph(new GraphCrucible(50f, false).setAccept(0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0));
 *     addGraph(new GraphHeat(275f, 0.05f, 0.01f).setAccept(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
 * }};
 * }</pre></p>
 */
public class HoldingCrucible extends GraphBlock{

    public HoldingCrucible(String name){
        super(name);
        solid = true;
        // ★ 信息面板: 点击打开坩埚内容物图表 (与坩埚熔炉一致:
        //   温度条 + 内容物堆叠条 + 所有物品熔点参考列表)
        configurable = true;
    }

    public class HoldingCrucibleBuild extends GraphBuild{
        @Override
        public void buildConfiguration(Table table){
            table.button(Icon.chartBar, Styles.clearNonei, new CrucibleDialog(this)::show).size(50f);
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);
            drawContents();

            // 热力叠加 (heatRegion 由 GraphBlock.load() 加载, heat() 可能为 null)
            if(heatRegion != null){
                GraphHeatModule heat = heat();
                if(heat != null) UnityDrawf.drawHeat(heatRegion, x, y, 0f, heat.getTemp());
            }
            drawTeamTop();
        }

        /** 绘制坩埚内的液体 (颜色由 CrucibleGraph.color 决定) */
        void drawContents(){
            GraphCrucibleModule crucGraph = crucible();
            if(crucGraph != null && crucGraph.getVolumeContained() > 0f && crucGraph.getNetwork() != null){
                Draw.color(crucGraph.getNetwork().color);
                Draw.rect(liquidRegion, x, y);
            }
            Draw.color();
        }
    }
}
