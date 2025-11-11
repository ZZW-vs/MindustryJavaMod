package zzw.content;

import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;

/**
 * 自定义工厂类
 * 包含模组中添加的所有工厂方块定义
 */
public class Z_Factory {
    // 普通工厂（2x2大小）
    public static Block Plate_Maker_Iron;      // 铁板制造机
    public static Block Plate_Maker_Gold;      // 金板制造机
    public static Block Plate_Maker_Copper;    // 铜板制造机

    // 大型工厂（3x3大小，需要电力）
    public static Block Large_Plate_Maker_Iron;   // 大型铁板制造机
    public static Block Large_Plate_Maker_Gold;   // 大型金板制造机
    public static Block Large_Plate_Maker_Copper; // 大型铜板制造机
    
    // 钻头类工厂
    public static Block Pumpkin_Drill;           // 南瓜钻头
    /**
     * 加载所有自定义工厂
     */
    public static void load(){
        // 创建普通工厂
        createPlateMakers();

        // 创建大型工厂
        createLargePlateMakers();

        // 创建钻头
        createDrills();
    }

    /**
     * 创建普通板制造机（2x2大小，不需要电力）
     */
    private static void createPlateMakers() {
        // 铁板制造机
        Plate_Maker_Iron = new GenericCrafter("plate_maker_iron"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 90, Items.lead, 70, Z_Items.Iron, 30));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(Z_Items.Iron_Sheet, 2);
            consumeItem(Z_Items.Iron, 2);
            size = 2;
            hasItems = true;
            craftTime = 75f;
        }};

        // 金板制造机
        Plate_Maker_Gold = new GenericCrafter("plate_maker_gold"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 90, Items.lead, 70, Z_Items.Gold, 30));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(Z_Items.Gold_Sheet, 2);
            consumeItem(Z_Items.Gold, 2);
            size = 2;
            hasItems = true;
            craftTime = 75f;
        }};

        // 铜板制造机
        Plate_Maker_Copper = new GenericCrafter("plate_maker_copper"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 110, Items.lead, 70));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(Z_Items.Copper_Sheet, 2);
            consumeItem(Items.copper, 2);
            size = 2;
            hasItems = true;
            craftTime = 75f;
        }};
    }

    /**
     * 创建大型板制造机（3x3大小，需要电力）
     */
    private static void createLargePlateMakers() {
        // 大型铁板制造机
        Large_Plate_Maker_Iron = new GenericCrafter("large_plate_maker_iron"){{
            requirements(Category.crafting, ItemStack.with(Z_Items.Iron, 120, Items.lead, 100, Z_Items.Iron, 40,
                    Z_Items.Iron_Sheet, 23));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(Z_Items.Iron_Sheet, 4);
            consumeItem(Z_Items.Iron, 3);
            hasPower = true;
            consumePower(0.125f);
            size = 3;
            hasItems = true;
            craftTime = 60f;
        }};

        // 大型金板制造机
        Large_Plate_Maker_Gold = new GenericCrafter("large_plate_maker_gold"){{
            requirements(Category.crafting, ItemStack.with(Z_Items.Iron, 120, Items.lead, 100, Z_Items.Gold, 40,
                    Z_Items.Gold_Sheet, 30));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(Z_Items.Gold_Sheet, 4);
            consumeItem(Z_Items.Gold, 3);
            hasPower = true;
            consumePower(0.125f);
            size = 3;
            hasItems = true;
            craftTime = 60f;
        }};

        // 大型铜板制造机
        Large_Plate_Maker_Copper = new GenericCrafter("large_plate_maker_copper"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 180, Items.lead, 100,
                    Z_Items.Copper_Sheet, 50));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(Z_Items.Copper_Sheet, 4);
            consumeItem(Items.copper, 3);
            hasPower = true;
            consumePower(0.125f);
            size = 3;
            hasItems = true;
            craftTime = 60f;
        }};
    }

    /**
     * 创建钻头类工厂
     */
    private static void createDrills() {
        // 南瓜钻头
        Pumpkin_Drill = new Drill("pumpkin_drill"){{
            requirements(Category.production, ItemStack.with(Items.copper, 50, Items.lead, 30));
            size = 1;
            drillTime = 300f; // 5秒 = 300 ticks
            hasItems = true;
            itemCapacity = 10;
            returnItem = Z_Items.Pumpkin_Seeds;
            tier = 1;
            liquidCapacity = 10f;
            hasLiquids = true;
            drawRim = true;
            updateEffect = Fx.pulverizeSmall;
            drillEffect = Fx.pulverizeSmall;
            warmupSpeed = 0.02f;
            consumeLiquid(Liquids.water, 0.1f).boost();
        }};
    }
}