package zzw.content.blocks.production;

import arc.graphics.g2d.Draw;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;
import zzw.content.mechanics.torque.modules.GraphHeatModule;

/**
 * 坩埚容器 (PU132 unity.world.blocks.production.HoldingCrucible 移植)
 * <p>不参与合成的坩埚容器 (GraphCrucible(capacity, false)): 只缓存熔融物,
 * 显示网络颜色与热量叠加。内容物在信息面板中通过 {@link GraphCrucibleModule#displayBars} 展示。
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
