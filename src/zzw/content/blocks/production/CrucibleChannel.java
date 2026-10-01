package zzw.content.blocks.production;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import mindustry.gen.Building;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;
import zzw.content.util.GraphicUtils;

import static arc.Core.atlas;

/**
 * 坩埚通道 (PU_V8 unity.world.blocks.production.CrucibleChannel 移植)
 * <p>连接坩埚网络的通道方块, 按四方向连接状态挑选贴图变体, 并填充网络颜色。
 */
public class CrucibleChannel extends GraphBlock{
    /** 四方向连接组合的贴图 (8x2 切片) */
    TextureRegion[] regions;
    TextureRegion floorRegion;

    public CrucibleChannel(String name){
        super(name);
    }

    @Override
    public void load(){
        super.load();
        regions = GraphicUtils.getRegions(atlas.find(name + "-tiles"), 8, 2);
        floorRegion = atlas.find(name + "-floor");
    }

    public class CrucibleChannelBuild extends GraphBuild{
        int spriteIndex;

        @Override
        public void draw(){
            // 依据四方向连接状态计算贴图变体
            spriteIndex = 0;
            for(int i = 0; i < 4; i++){
                Building n = nearby((4 - i) % 4);
                if(n instanceof GraphBuildBase gb && gb.crucible() != null
                    && crucible() != null && gb.crucible().getNetwork() == crucible().getNetwork()){
                    spriteIndex += 1 << i;
                }
            }

            Draw.color();
            Draw.rect(floorRegion, x, y);
            if(crucible() != null && crucible().getNetwork() != null){
                Draw.color(crucible().getNetwork().color);
            }
            Fill.rect(x, y, 8f, 8f);
            Draw.color();
            Draw.rect(regions[spriteIndex], x, y);
            drawTeamTop();
        }
    }
}
