package zzw.content.blocks;

import mindustry.content.Items;
import mindustry.game.EventType;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.distribution.Conveyor;
import arc.Events;
import mindustry.world.blocks.distribution.StackConveyor;
import zzw.content.Z_Items;

import static zzw.content.blocks.BlockMerger.checkAndReplace;

/**
 * 自定义方块类
 * 包含模组中添加的所有方块定义
 */
public class Z_Blocks {
    // 铜方块
    public static Block Copper_Block;        // 小铜块（1x1）
    public static Block Large_Copper_Block;  // 大铜块（2x2）
    
    // 铁方块
    public static Block Iron_Block;          // 小铁块（1x1）
    public static Block Large_Iron_Block;    // 大铁块（2x2）
    
    // 传送带
    public static Block PPC_Conveyor;        // 传送带
    public static Block Better_PPC_Conveyor;        // 传送带

    /**
     * 加载所有自定义方块
     */
    public static void load(){
        // 创建防御方块
        createDefenseBlocks();
        
        // 创建传送带
        createConveyor();
        
        // 注册事件监听器
        registerEventListeners();
    }
    
    /**
     * 创建防御方块（墙）
     */
    private static void createDefenseBlocks() {
        // 小铜块（1x1）
        Copper_Block = new Wall("copper_block"){{
            requirements(Category.defense, ItemStack.with(Z_Items.Copper_Sheet, 4, Items.copper, 3));
            size = 1;
            health = 380;
        }};
        
        // 大铜块（2x2）
        Large_Copper_Block = new Wall("large_copper_block"){{
            requirements(Category.defense, ItemStack.with(Z_Items.Copper_Sheet, 16, Items.copper, 12));
            size = 2;
            health = 1520;
        }};
        
        // 小铁块（1x1）
        Iron_Block = new Wall("iron_block"){{
            requirements(Category.defense, ItemStack.with(Z_Items.Iron_Sheet, 4, Items.copper, 3));
            size = 1;
            health = 400;
        }};
        
        // 大铁块（2x2）
        Large_Iron_Block = new Wall("large_iron_block"){{
            requirements(Category.defense, ItemStack.with(Z_Items.Iron_Sheet, 16, Z_Items.Iron, 12));
            size = 2;
            health = 1600;
        }};
    }
    
    /**
     * 创建传送带
     */
    private static void createConveyor() {
        PPC_Conveyor = new  Conveyor("ppc") {{
            requirements(Category.distribution, ItemStack.with(Items.lead, 1));
            health = 50;
            speed = 0.05f;
            displayedSpeed = 5f;
        }};
        Better_PPC_Conveyor = new  StackConveyor("b_ppc") {{
            requirements(Category.distribution, ItemStack.with(Items.lead, 3, Z_Items.Iron_Sheet, 2));
            health = 70;
            speed = 0.045f;
            itemCapacity = 16;
        }};
    }

    /**
     * 注册事件监听器，处理方块合并逻辑
     */
    private static void registerEventListeners() {
        // 添加方块放置事件监听
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            // 跳过拆除事件
            if(event.breaking) return;

            // 检查是否是可合并的墙块
            if(event.tile.block() == Copper_Block) {
                checkAndReplace(event.tile, Copper_Block, Large_Copper_Block);
            } else if(event.tile.block() == Iron_Block) {
                checkAndReplace(event.tile, Iron_Block, Large_Iron_Block);
            }
        });
    }
}
