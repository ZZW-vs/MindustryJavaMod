package zzw.content.blocks.production;

import arc.graphics.g2d.Draw;
import zzw.content.blocks.GraphBlock;
import zzw.content.graphics.UnityDrawf;
import zzw.content.modules.GraphCrucibleModule;
import zzw.content.modules.GraphHeatModule;

/**
 * 坩埚容器 (PU132 unity.world.blocks.production.HoldingCrucible 移植)
 * <p>继承 GraphBlock。drawContents() 显示坩埚液体颜色,
 * UnityDrawf.drawHeat 渲染热力叠加, 使用 GraphBuildBase.crucible() 获取 GraphCrucibleModule。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>unity.world.blocks.GraphBlock → zzw.content.blocks.GraphBlock</li>
 *   <li>unity.graphics.UnityDrawf → zzw.content.graphics.UnityDrawf</li>
 *   <li>unity.world.modules.GraphCrucibleModule → zzw.content.modules.GraphCrucibleModule</li>
 *   <li>增加 crucible()/heat() 的 null 检查, 防止未配置图时崩溃</li>
 * </ul></p>
 */
public class HoldingCrucible extends GraphBlock{

    public HoldingCrucible(String name){
        super(name);
        solid = true;
    }

    public class HoldingCrucibleBuild extends GraphBuild{
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

        /** 绘制坩埚内的液体 (颜色由 GraphCrucibleModule.getNetwork().color 决定) */
        void drawContents(){
            GraphCrucibleModule crucGraph = crucible();
            if(crucGraph != null && crucGraph.getVolumeContained() > 0f){
                Draw.color(crucGraph.getNetwork().color);
                Draw.rect(liquidRegion, x, y);
            }
            Draw.color();
        }
    }
}
