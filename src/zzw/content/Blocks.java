package zzw.content;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;

public class Blocks {
    public static Block Copper_Wall, Large_Copper_Wall, Iron_Wall, Large_Iron_Wall;
    public static void load(){
        Copper_Wall = new GenericCrafter("copper_wall"){{
            requirements(Category.defense, ItemStack.with(items.Copper_Sheet, 6, Items.copper, 1));
            size = 1;
            health = 380;
        }};
        Large_Copper_Wall =  new GenericCrafter("large_copper_wall"){{
            requirements(Category.defense, ItemStack.with(items.Copper_Sheet, 16, Items.copper, 6));
            size = 2;
            health = 1520;
        }};
        Iron_Wall = new GenericCrafter("iron_wall"){{
            requirements(Category.defense, ItemStack.with(items.Iron_Sheet, 6, Items.copper, 1));
            size = 1;
            health = 400;
        }};
        Large_Iron_Wall = new GenericCrafter("large_iron_wall"){{
            requirements(Category.defense, ItemStack.with(items.Iron_Sheet, 16, items.Iron, 6));
            size = 2;
            health = 1600;
        }};
    }
}
