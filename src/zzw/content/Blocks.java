package zzw.content;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.distribution.Conveyor;

public class Blocks {
    public static Block Copper_Wall, Large_Copper_Wall, Iron_Wall, Large_Iron_Wall, PPC_Conveyor;
    public static void load(){
        //TODO 墙
        Copper_Wall = new Wall("copper_wall"){{
            requirements(Category.defense, ItemStack.with(zzw.content.items.Copper_Sheet, 6, Items.copper, 1));
            size = 1;
            health = 380;
        }};
        Large_Copper_Wall =  new Wall("large_copper_wall"){{
            requirements(Category.defense, ItemStack.with(zzw.content.items.Copper_Sheet, 16, Items.copper, 6));
            size = 2;
            health = 1520;
        }};
        Iron_Wall = new Wall("iron_wall"){{
            requirements(Category.defense, ItemStack.with(zzw.content.items.Iron_Sheet, 6, Items.copper, 1));
            size = 1;
            health = 400;
        }};
        Large_Iron_Wall = new Wall("large_iron_wall"){{
            requirements(Category.defense, ItemStack.with(zzw.content.items.Iron_Sheet, 16, zzw.content.items.Iron, 6));
            size = 2;
            health = 1600;
        }};

        //TODO传送带
        PPC_Conveyor = new Conveyor("ppc") {{
            requirements(Category.distribution, ItemStack.with(Items.lead, 1));
            health = 55;
            speed = 0.05f;
            displayedSpeed = 0.2f;
            buildCostMultiplier = 2f;
        }};
    }
}
