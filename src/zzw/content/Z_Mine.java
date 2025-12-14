package zzw.content;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.production.Drill;

/**
 * 矿物类
 * 定义模组中的矿物和钻头
 */
public class Z_Mine {
    // 安山岩矿物
    public static Block Andesite;
    
    // 安山岩钻头
    public static Block Andesite_Drill;
    
    /**
     * 加载所有矿物和钻头
     */
    public static void load() {
        createOres();
        createDrills();
    }
    
    /**
     * 创建矿物
     */
    private static void createOres() {
        // 安山岩矿物
        Andesite = new OreBlock("andesite"){ {
            itemDrop = Z_Items.Andesite; // 掉落安山岩物品
            variants = 3;
        }};
    }
    
    /**
     * 创建钻头
     */
    private static void createDrills() {
        // 安山岩钻头
        Andesite_Drill = new Drill("andesite_drill"){{
            requirements(Category.production, ItemStack.with(Items.copper, 80, Items.lead, 60));
            size = 2;
            drillTime = 250f;
            hasItems = true;
            itemCapacity = 15;
            returnItem = Z_Items.Andesite; // 返回安山岩物品
            tier = 2;
            liquidCapacity = 15f;
            hasLiquids = true;
            drawRim = true;
            updateEffect = mindustry.content.Fx.pulverizeSmall;
            drillEffect = mindustry.content.Fx.pulverizeSmall;
            warmupSpeed = 0.02f;
            consumeLiquid(mindustry.content.Liquids.water, 0.15f).boost();
        }};
    }
}
