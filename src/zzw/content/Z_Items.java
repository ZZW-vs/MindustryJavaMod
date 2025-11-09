package zzw.content;

import arc.graphics.Color;
import mindustry.type.Item;

/**
 * 自定义物品类
 * 包含模组中添加的所有物品定义
 */
public class Z_Items {
    // 金属原料
    public static Item Iron; // 铁
    public static Item Gold; // 金
    
    // 金属板材
    public static Item Iron_Sheet; // 铁板
    public static Item Gold_Sheet; // 金板
    public static Item Copper_Sheet; // 铜板
    
    // 特殊物品
    public static Item Text_An; // 篝火火焰（特殊纹理物品）
    
    /**
     * 加载所有自定义物品
     */
    public static void load(){
        // 基础金属
        Iron = createBasicItem("iron", "cfcfcf");
        Gold = createBasicItem("gold", "f2df82");
        
        // 金属板材
        Iron_Sheet = createBasicItem("iron_sheet", "cfcfcf");
        Gold_Sheet = createBasicItem("gold_sheet", "f2df82");
        Copper_Sheet = createBasicItem("copper_sheet", "c4a638");
        
        // 特殊物品
        Text_An = new Item("campfire_fire", Color.valueOf("cfcfcf")){{
            frames = 5;
            transitionFrames = 1;
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
    }
    
    /**
     * 创建一个基础物品，带有默认属性
     * @param name 物品名称
     * @param color 颜色代码
     * @return 创建的物品
     */
    private static Item createBasicItem(String name, String color) {
        return new Item(name, Color.valueOf(color)){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
    }
}
