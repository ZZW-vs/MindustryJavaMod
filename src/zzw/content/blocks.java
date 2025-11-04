package zzw.content;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.production.GenericCrafter;

import static mindustry.type.ItemStack.with;


public class blocks {
    public static Block Plate_Maker_Iron, Large_Plate_Maker_Iron, Plate_Maker_Gold,
            Large_Plate_Maker_Gold, PPC_Conveyor;
    public static void load(){
        Plate_Maker_Iron = new GenericCrafter("plate_maker_iron"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 90, Items.lead, 70, items.Iron, 35));

            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;

            outputItem = new ItemStack(items.Iron_Sheet, 2);
            consumeItem(items.Iron, 2 );

            size = 2;
            hasItems = true;
            craftTime = 70f;
        }};

        Large_Plate_Maker_Iron = new GenericCrafter("large_plate_maker_iron"){{
            requirements(Category.crafting, ItemStack.with(items.Iron, 130, Items.lead, 100, items.Iron, 40,
                    items.Iron_Sheet, 25));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;

            outputItem = new ItemStack(items.Iron_Sheet, 4);
            consumeItem(items.Iron, 3);

            hasPower = true;
            consumePower(1f);

            size = 3;
            hasItems = true;
            craftTime = 60f;
        }};

        Plate_Maker_Gold = new GenericCrafter("plate_maker_gold"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 95, Items.lead, 75, items.Gold, 30));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;

            outputItem = new ItemStack(items.Gold_Sheet, 2);
            consumeItem(items.Gold, 2);


            size = 2;
            hasItems = true;
            craftTime = 80f;
        }};

        Large_Plate_Maker_Gold = new GenericCrafter("large_plate_maker_gold"){{
            requirements(Category.crafting, ItemStack.with(items.Iron, 140, Items.lead, 110, items.Gold, 40,
                    items.Gold_Sheet, 25));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;

            outputItem = new ItemStack(items.Gold_Sheet, 4);
            consumeItem(items.Gold, 3);

            hasPower = true;
            consumePower(1f);

            size = 3;
            hasItems = true;
            craftTime = 70f;
        }};

        PPC_Conveyor = new Conveyor("ppc") {{
            requirements(Category.distribution, with(items.Iron, 1));
            health = 55;
            speed = 0.5f;
            displayedSpeed = 4.2f;
            buildCostMultiplier = 2f;
            researchCost = with(items.Iron, 5);
        }};
    }
}
