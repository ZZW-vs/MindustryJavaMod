package zzw.content.blocks.production;

import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.ui.Bar;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumeLiquidBase;

import static arc.Core.bundle;

/**
 * 多液体冶炼炉 (PU132 unity.world.blocks.production.LiquidsSmelter 移植)
 * <p>继承 StemGenericCrafter。init() 中检查是否有 ConsumeLiquids,
 * setBars() 中移除默认 liquid bar, 为每种液体添加单独的 bar。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>@Merge(Stemc) → extends StemGenericCrafter</li>
 *   <li>consumes API: v132 ConsumeType.liquid → v155.4 findConsumer</li>
 *   <li>bars.add/remove → addBar/removeBar (v155.4 barMap API)</li>
 * </ul></p>
 */
public class LiquidsSmelter extends StemGenericCrafter{
    protected Liquid[] liquids;

    public LiquidsSmelter(String name){
        super(name);
    }

    @Override
    public void init(){
        ConsumeLiquidBase consume = findConsumer(c -> c instanceof ConsumeLiquidBase);
        if(consume == null){
            throw new RuntimeException("LiquidSmelter must have a ConsumeLiquid. Note that filters are not supported.");
        }

        // 由于ConsumeLiquid没有liquids数组，我们直接使用传入的液体
        if(consume instanceof ConsumeLiquid){
            ConsumeLiquid liquidConsume = (ConsumeLiquid)consume;
            liquids = new Liquid[]{liquidConsume.liquid};
        }

        super.init();
    }

    @Override
    public void setBars(){
        super.setBars();

        // 移除默认的 liquid bar, 为每种液体单独添加
        removeBar("liquid");
        for(Liquid liquid : liquids){
            addBar(liquid.name, build -> new Bar(
                () -> build.liquids.get(liquid) <= 0.001f ? bundle.get("bar.liquid") : liquid.localizedName,
                liquid::barColor,
                () -> build.liquids.get(liquid) / liquidCapacity
            ));
        }
    }
}
