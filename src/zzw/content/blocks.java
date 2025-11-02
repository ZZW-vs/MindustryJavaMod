package zzw.content;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;

import static mindustry.world.meta.StatUnit.items;
import static zzw.content.items.Iron;
import static zzw.content.items.Iron_Plate;

public class blocks {
    public static Block Furnace, Processing_Factory;
    public static void load(){
        Furnace = new GenericCrafter("furnace"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 100, Items.lead, 80,  Items.Iron, 30));
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(items.Iron_Plate, 1);
            consumeItem(items.Iron, 1);

            size = 2;
            hasItems = true;
            craftTime = 90;
        }};

        Processing_Factory = new GenericCrafter("processing_factory"){{

        }};

    }
}
