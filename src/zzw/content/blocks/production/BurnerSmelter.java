package zzw.content.blocks.production;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.world.consumers.ConsumeItemFilter;
import mindustry.world.meta.Stat;

import static arc.Core.bundle;

/**
 * 燃烧冶炼炉 (PU132 unity.world.blocks.production.BurnerSmelter 移植)
 * <p>基于物品可燃性 (flammability) 驱动的冶炼炉。
 * 投入的燃料物品的 flammability 决定生产效率，itemDuration 计时器到期后消耗下一份燃料。
 * progress 基于效率推进，acceptItem 检查物品是否为燃料或输入。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>继承 StemGenericCrafter 替代 PU132 @Merge(Stemc) 生成类</li>
 *   <li>★ input 允许为 null (无材料输入, 纯烧燃料产液体, 熔化器用);
 *       PU132 默认 input = UnityItems.stone, 用户要求熔化器不消耗任何材料</li>
 *   <li>PU132 的 ItemModule.nextIndex/takeIndex 在 v155.4+ 不存在,
 *       改为遍历 content.items() 查找第一个非 input 且通过过滤器的物品作为燃料</li>
 *   <li>★ setStats 移除手动 stats.add(Stat.input, input) —— v155.4 的 super.setStats()
 *       会自动生成消耗品统计, 手动添加导致"输入"重复显示</li>
 * </ul></p>
 */
public class BurnerSmelter extends StemGenericCrafter{
    /** 输入物品 (会被冶炼消耗); null = 不消耗材料 (纯燃料驱动) */
    public Item input;
    /** 最小效率阈值 (低于此值的物品不会被接受为燃料) */
    public float minEfficiency = 0.6f;
    /** 效率放大系数 */
    public float boostScale = 1.25f;
    /** 效率常数偏移 */
    public float boostConstant = -0.75f;

    public BurnerSmelter(String name){
        super(name);
    }

    @Override
    public void init(){
        // 若未手动添加燃料过滤器, 自动添加一个 optional 的 ConsumeItemFilter
        if(findConsumer(c -> c instanceof ConsumeItemFilter) == null){
            consume(new ConsumeItemFilter(item -> getItemEfficiency(item) > minEfficiency)).update(false).optional(true, false);
        }

        super.init();
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("efficiency", (BurnerSmelterBuild build) -> new Bar(
            () -> bundle.format("bar.efficiency", (int)(100 * build.productionEfficiency)),
            () -> Pal.lighterOrange,
            () -> build.productionEfficiency
        ));
    }

    /** 物品作为燃料的效率 (默认使用 flammability) */
    protected float getItemEfficiency(Item item){
        return item.flammability;
    }

    public class BurnerSmelterBuild extends StemGenericCrafterBuild{
        /** 当前燃料剩余持续时间 */
        public float itemDuration;
        /** 当前生产效率 (由燃料 flammability 决定) */
        public float productionEfficiency;

        @Override
        public void updateTile(){
            // input == null 时纯燃料驱动 (不检查材料)
            if((input == null || items.has(input)) && itemDuration > 0f){
                // 有输入且有燃料: 按效率推进进度
                progress += getProgressIncrease(craftTime) * productionEfficiency;
                itemDuration -= delta();

                totalProgress += delta();
                warmup = Mathf.lerpDelta(warmup, 1f, 0.02f);

                if(Mathf.chanceDelta(updateEffectChance)) updateEffect.at(x + Mathf.range(size * 4f), y + Mathf.range(size * 4f));
            }else{
                if(itemDuration <= 0f){
                    // 燃料耗尽: 尝试消耗下一个燃料物品
                    productionEfficiency = 0f;

                    // PU132 的 ItemModule.nextIndex/takeIndex 在 v155.4+ 不存在,
                    // 改为遍历 content.items() 查找第一个非 input 且通过过滤器的物品作为燃料
                    if((input == null || items.has(input)) && shouldConsume()){
                        Item fuelItem = null;
                        for(int i = 0, n = Vars.content.items().size; i < n; i++){
                            Item it = Vars.content.item(i);
                            // 跳过输入物品, 仅接受通过 itemFilter (可燃物) 的物品
                            if(it != input && items.has(it) && block.itemFilter[it.id]){
                                fuelItem = it;
                                break;
                            }
                        }

                        if(fuelItem != null){
                            productionEfficiency = getItemEfficiency(fuelItem) * boostScale + boostConstant;
                            items.remove(fuelItem, 1);
                            itemDuration = craftTime;
                        }
                    }
                }else{
                    itemDuration -= delta();
                }

                warmup = Mathf.lerp(warmup, 0f, 0.02f);
            }

            // 进度满: 消耗输入 (无 input 时跳过), 输出液体
            if(progress >= 1f){
                if(input != null) items.remove(input, 1);

                if(outputLiquid != null) handleLiquid(this, outputLiquid.liquid, outputLiquid.amount);

                craftEffect.at(x, y);
                progress = 0f;
            }

            if(outputLiquid != null){
                dumpLiquid(outputLiquid.liquid);
            }

            super.updateTile();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            // 接受燃料 (通过过滤器) 或输入物品
            return (block.itemFilter[item.id] || item == input) && items.get(item) < getMaximumAccepted(item);
        }
    }
}
