package zzw.content;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;

public class blocks {
    public static Block  Plate_Maker, Large_Plate_Maker;
    public static void load(){
        Plate_Maker = new GenericCrafter("plate_maker"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 90, Items.lead, 70, items.Iron, 35));

            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(items.Iron_Sheet, 1);
            consumeItem(items.Iron, 1);

            size = 2;
            hasItems = true;
            craftTime = 80f;
        }};

        Large_Plate_Maker = new GenericCrafter("large_plate_maker"){{
            requirements(Category.crafting, ItemStack.with(items.Iron, 130, Items.lead, 100, items.Iron, 40, items.Iron_Sheet, 25));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(items.Iron_Sheet, 3);
            consumeItem(items.Iron, 2);

            size = 3;
            hasItems = true;
            craftTime = 60f;
        }};

    }
}
