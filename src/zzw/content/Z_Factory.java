package zzw.content;

import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;

public class Z_Factory {
    public static Block Plate_Maker_Iron;
    public static Block Plate_Maker_Gold;
    public static Block Plate_Maker_Copper;

    public static Block Large_Plate_Maker_Iron;
    public static Block Large_Plate_Maker_Gold;
    public static Block Large_Plate_Maker_Copper;

    public static Block Pumpkin_Drill;

    public static void load(){
        createPlateMakers();
        createLargePlateMakers();
        createDrills();
    }

    private static void createPlateMakers() {
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

    private static void createLargePlateMakers() {
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

    private static void createDrills() {
        Pumpkin_Drill = new Drill("pumpkin_drill"){{
            requirements(Category.production, ItemStack.with(Items.copper, 50, Items.lead, 30));
            size = 1;
            drillTime = 300f;
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
