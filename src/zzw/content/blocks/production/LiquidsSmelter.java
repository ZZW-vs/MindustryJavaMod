package zzw.content.blocks.production;

import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.ui.Bar;
import mindustry.world.consumers.ConsumeLiquidBase;
import mindustry.world.consumers.ConsumeLiquids;

import static arc.Core.bundle;

/**
 * 多液体冶炼炉 (PU132 unity.world.blocks.production.LiquidsSmelter 移植)
 *
 * <p>要求消耗器为 {@link ConsumeLiquids} (多液体), init() 中提取所有消耗液体,
 * setBars() 中移除默认液体条并为每种液体单独添加进度条 (固化器同时显示岩浆和水)。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>@Merge(Stemc) → extends StemGenericCrafter</li>
 *   <li>v132 ConsumeType.liquid → v155.4 findConsumer (消耗器 API 变更)</li>
 *   <li>v132 bars.add/remove → v155.4 addBar/removeBar</li>
 * </ul></p>
 */
public class LiquidsSmelter extends StemGenericCrafter{
    /** 所有消耗的液体 (init 时从 ConsumeLiquids 提取) */
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
