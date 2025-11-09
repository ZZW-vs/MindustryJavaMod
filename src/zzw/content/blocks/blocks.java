package zzw.content.blocks;

import mindustry.content.Items;
import mindustry.game.EventType;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.distribution.Conveyor;
import arc.Events;

import static zzw.content.blocks.BlockMerger.checkAndReplace;

public class blocks {
    public static Block Copper_Block, Large_Copper_Block, Iron_Block, Large_Iron_Block, PPC_Conveyor;

    public static void load(){
        //TODO 墙
        Copper_Block = new Wall("copper_wall"){{
            requirements(Category.defense, ItemStack.with(zzw.content.items.Copper_Sheet, 6, Items.copper, 1));
            size = 1;
            health = 380;
        }};
        Large_Copper_Block =  new Wall("large_copper_wall"){{
            requirements(Category.defense, ItemStack.with(zzw.content.items.Copper_Sheet, 16, Items.copper, 6));
            size = 2;
            health = 1520;
        }};
        Iron_Block = new Wall("iron_wall"){{
            requirements(Category.defense, ItemStack.with(zzw.content.items.Iron_Sheet, 6, Items.copper, 1));
            size = 1;
            health = 400;
        }};
        Large_Iron_Block = new Wall("large_iron_wall"){{
            requirements(Category.defense, ItemStack.with(zzw.content.items.Iron_Sheet, 16, zzw.content.items.Iron, 6));
            size = 2;
            health = 1600;
        }};

        //TODO传送带
        PPC_Conveyor = new Conveyor("ppc") {{
            requirements(Category.distribution, ItemStack.with(Items.lead, 1));
            health = 55;
            speed = 0.06f;
            displayedSpeed = 0.06f;
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
