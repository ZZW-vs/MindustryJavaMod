package zzw.content.blocks.production;

import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.ui.Bar;
import mindustry.world.consumers.ConsumeLiquids;

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
        ConsumeLiquids consume = findConsumer(c -> c instanceof ConsumeLiquids);
        if(consume == null){
            throw new RuntimeException("LiquidSmelter must have a ConsumeLiquids. Note that filters are not supported.");
        }

        LiquidStack[] stacks = consume.liquids;
        liquids = new Liquid[stacks.length];
        for(int i = 0; i < liquids.length; i++) liquids[i] = stacks[i].liquid;

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
