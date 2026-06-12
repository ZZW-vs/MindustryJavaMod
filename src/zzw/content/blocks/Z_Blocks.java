package zzw.content.blocks;

import mindustry.content.Items;
import mindustry.game.EventType;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.StackConveyor;
import mindustry.world.meta.BuildVisibility;
import zzw.content.Z_Items;

import arc.Events;

public class Z_Blocks {
    // 铜方块
    public static Block Copper_Block, Large_Copper_Block;
    // 铁方块
    public static Block Iron_Block, Large_Iron_Block;
    // 传送带
    public static Block PPC_Conveyor, Better_PPC_Conveyor;
    // 其他方块
    public static Block Pumpkin, Carved_Pumpkin;

    public static void load() {
        createDefenseBlocks();
        createConveyors();
        createDecorativeBlocks();
        registerEventListeners();
    }

    private static Wall wall(String name, ItemStack[] requirements, int size, int health) {
        return new Wall(name) {{
            requirements(Category.defense, requirements);
            this.size = size;
            this.health = health;
        }};
    }

    private static void createDefenseBlocks() {
        Copper_Block = wall("copper_block",
                ItemStack.with(Z_Items.Copper_Sheet, 4, Items.copper, 3), 1, 380);
        Large_Copper_Block = wall("large_copper_block",
                ItemStack.with(Z_Items.Copper_Sheet, 16, Items.copper, 12), 2, 1520);

        Iron_Block = wall("iron_block",
                ItemStack.with(Z_Items.Iron_Sheet, 4, Items.copper, 3), 1, 400);
        Large_Iron_Block = wall("large_iron_block",
                ItemStack.with(Z_Items.Iron_Sheet, 16, Z_Items.Iron, 12), 2, 1600);
    }

    private static void createConveyors() {
        PPC_Conveyor = new Conveyor("ppc") {{
            requirements(Category.distribution, ItemStack.with(Items.lead, 1));
            health = 50;
            speed = 0.05f;
            displayedSpeed = 7f;
        }};
        Better_PPC_Conveyor = new StackConveyor("b_ppc") {{
            requirements(Category.distribution, ItemStack.with(Items.lead, 3, Z_Items.Iron_Sheet, 2));
            health = 70;
            speed = 0.045f;
            itemCapacity = 16;
        }};
    }

    private static Block decorative(String name, ItemStack[] requirements, int health) {
        return new Block(name) {{
            requirements(Category.crafting, requirements);
            size = 1;
            this.health = health;
            buildVisibility = BuildVisibility.shown;
            destructible = true;
            allowDiagonal = true;
            solid = true;
            canOverdrive = false;
            instantDeconstruct = true;
        }};
    }

    private static void createDecorativeBlocks() {
        Pumpkin = decorative("pumpkin",
                ItemStack.with(Z_Items.Copper_Sheet, 4, Items.copper, 3), 380);
        Carved_Pumpkin = decorative("carved_pumpkin",
                ItemStack.with(Z_Items.Copper_Sheet, 4, Items.copper, 3), 350);
    }

    private static void registerEventListeners() {
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking) return;
            if (event.tile.block() == Copper_Block) {
                BlockMerger.checkAndReplace(event.tile, Copper_Block, Large_Copper_Block);
            } else if (event.tile.block() == Iron_Block) {
                BlockMerger.checkAndReplace(event.tile, Iron_Block, Large_Iron_Block);
            }
        });
    }
}
