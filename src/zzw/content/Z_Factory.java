package zzw.content;

import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;
import zzw.content.mechanics.FactoryBoost;

public class Z_Factory {
    public static Block Plate_Maker_Iron, Plate_Maker_Gold, Plate_Maker_Copper;
    public static Block Large_Plate_Maker_Iron, Large_Plate_Maker_Gold, Large_Plate_Maker_Copper;
    public static Block Pumpkin_Drill;

    public static void load() {
        createPlateMakers();
        createLargePlateMakers();
        createDrills();
    }

    private static FactoryBoost.BoostedGenericCrafter plateMaker(String name,
            ItemStack[] requirements, mindustry.type.Item input, mindustry.type.Item output, int size_, float craftTime_) {
        return new FactoryBoost.BoostedGenericCrafter(name) {{
            buildType = BoostedGenericCrafterBuild::new;
            requirements(Category.crafting, requirements);
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(output, 2);
            consumeItem(input, 2);
            size = size_;
            hasItems = true;
            craftTime = craftTime_;
        }};
    }

    private static FactoryBoost.BoostedGenericCrafter largePlateMaker(String name,
            ItemStack[] requirements, mindustry.type.Item input, mindustry.type.Item output, float craftTime_) {
        return new FactoryBoost.BoostedGenericCrafter(name) {{
            buildType = BoostedGenericCrafterBuild::new;
            requirements(Category.crafting, requirements);
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(output, 4);
            consumeItem(input, 3);
            hasPower = true;
            consumePower(0.125f);
            size = 3;
            hasItems = true;
            craftTime = craftTime_;
        }};
    }

    private static void createPlateMakers() {
        Plate_Maker_Iron = plateMaker("plate_maker_iron",
                ItemStack.with(Items.copper, 90, Items.lead, 70, Z_Items.Iron, 30),
                Z_Items.Iron, Z_Items.Iron_Sheet, 2, 75f);

        Plate_Maker_Gold = plateMaker("plate_maker_gold",
                ItemStack.with(Items.copper, 90, Items.lead, 70, Z_Items.Gold, 30),
                Z_Items.Gold, Z_Items.Gold_Sheet, 2, 75f);

        Plate_Maker_Copper = plateMaker("plate_maker_copper",
                ItemStack.with(Items.copper, 110, Items.lead, 70),
                Items.copper, Z_Items.Copper_Sheet, 2, 75f);
    }

    private static void createLargePlateMakers() {
        Large_Plate_Maker_Iron = largePlateMaker("large_plate_maker_iron",
                ItemStack.with(Z_Items.Iron_Sheet, 23, Items.lead, 100, Z_Items.Iron, 40),
                Z_Items.Iron, Z_Items.Iron_Sheet, 60f);

        Large_Plate_Maker_Gold = largePlateMaker("large_plate_maker_gold",
                ItemStack.with(Z_Items.Gold_Sheet, 30, Items.lead, 100, Z_Items.Gold, 40),
                Z_Items.Gold, Z_Items.Gold_Sheet, 60f);

        Large_Plate_Maker_Copper = largePlateMaker("large_plate_maker_copper",
                ItemStack.with(Z_Items.Copper_Sheet, 50, Items.copper, 180, Items.lead, 100),
                Items.copper, Z_Items.Copper_Sheet, 60f);
    }

    private static void createDrills() {
        Pumpkin_Drill = new Drill("pumpkin_drill") {{
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
