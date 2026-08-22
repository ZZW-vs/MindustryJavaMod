package zzw.content.blocks.draw;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import mindustry.graphics.Drawf;
import mindustry.gen.Building;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.draw.DrawBlock;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumeLiquidBase;

/**
 * 液体绘制器 (Mindustry v132 mindustry.world.draw.DrawLiquid 移植)
 *
 * <p>v155.4 已移除该类, PU132 的岩浆冶炼厂 (lava-smelter) 使用它绘制
 * 输入液体液位。按 v132 原版源码原样移植。</p>
 *
 * <p>v155.4 适配:
 * <ul>
 *   <li>draw(GenericCrafterBuild) → draw(Building) (DrawBlock 签名变更)</li>
 *   <li>consumes.has(ConsumeType.liquid) → findConsumer (消耗器 API 变更)</li>
 * </ul></p>
 */
public class DrawLiquid extends DrawBlock{
    /** 输入液体贴图 ({@code 方块名-input-liquid}) */
    public TextureRegion inLiquid, liquid, top;
    /** 输出液体是否使用主液体贴图 */
    public boolean useOutputSprite = false;

    public DrawLiquid(){
    }

    public DrawLiquid(boolean useOutputSprite){
        this.useOutputSprite = useOutputSprite;
    }

    @Override
    public void draw(Building build){
        Draw.rect(build.block.region, build.x, build.y);
        GenericCrafter type = (GenericCrafter)build.block;

        ConsumeLiquidBase con = type.findConsumer(c -> c instanceof ConsumeLiquid);
        if((inLiquid.found() || useOutputSprite) && con != null){
            Liquid input = ((ConsumeLiquid)con).liquid;
            Drawf.liquid(useOutputSprite ? liquid : inLiquid, build.x, build.y,
                build.liquids.get(input) / type.liquidCapacity,
                input.color
            );
        }

        if(type.outputLiquid != null && build.liquids.get(type.outputLiquid.liquid) > 0){
            Drawf.liquid(liquid, build.x, build.y,
                build.liquids.get(type.outputLiquid.liquid) / type.liquidCapacity,
                type.outputLiquid.liquid.color
            );
        }

        if(top.found()) Draw.rect(top, build.x, build.y);
    }

    @Override
    public void load(Block block){
        top = Core.atlas.find(block.name + "-top");
        liquid = Core.atlas.find(block.name + "-liquid");
        inLiquid = Core.atlas.find(block.name + "-input-liquid");
    }

    @Override
    public TextureRegion[] icons(Block block){
        return top.found() ? new TextureRegion[]{block.region, top} : new TextureRegion[]{block.region};
    }
}
