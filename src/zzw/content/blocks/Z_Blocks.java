package zzw.content.blocks;

import mindustry.content.Items;
import mindustry.game.EventType;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.distribution.Conveyor;
import arc.Events;
import zzw.content.Z_Items;

import static zzw.content.blocks.BlockMerger.checkAndReplace;

public class Z_Blocks {
    public static Block Copper_Block, Large_Copper_Block, Iron_Block, Large_Iron_Block, PPC_Conveyor;

    public static void load(){
        //TODO 墙
        Copper_Block = new Wall("copper_block"){{
            requirements(Category.defense, ItemStack.with(Z_Items.Copper_Sheet, 4, Items.copper, 3));
            size = 1;
            health = 380;
        }};
        Large_Copper_Block =  new Wall("large_copper_block"){{
            requirements(Category.defense, ItemStack.with(Z_Items.Copper_Sheet, 16, Items.copper, 12));
            size = 2;
            health = 1520;
        }};
        Iron_Block = new Wall("iron_block"){{
            requirements(Category.defense, ItemStack.with(Z_Items.Iron_Sheet, 4, Items.copper, 3));
            size = 1;
            health = 400;
        }};
        Large_Iron_Block = new Wall("large_iron_block"){{
            requirements(Category.defense, ItemStack.with(Z_Items.Iron_Sheet, 16, Z_Items.Iron, 12));
            size = 2;
            health = 1600;
        }};

        //TODO传送带
        PPC_Conveyor = new Conveyor("ppc") {{
            requirements(Category.distribution, ItemStack.with(Items.lead, 1));
            health = 55;
            speed = 0.06f;
            displayedSpeed = 0.03f;
            buildCostMultiplier = 2f;
        }};

        // 添加方块放置事件监听
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if(event.breaking) return;

            // 检查是否是铜墙或铁墙
            if(event.tile.block() == Copper_Block) {
                checkAndReplace(event.tile, Copper_Block, Large_Copper_Block);
            } else if(event.tile.block() == Iron_Block) {
                checkAndReplace(event.tile, Iron_Block, Large_Iron_Block);
            }
        });
    }
}
