package zzw.content;

import arc.graphics.Color;
import mindustry.type.Item;

/**
 * 自定义物品类
 * 包含模组中添加的所有物品定义
 */
public class Z_Items {
    // 基础矿物
    public static Item Iron; // 铁
    public static Item Gold; // 金
    public static Item Andesite_Alloy; // 安山合金
    public static Item Brass; // 黄铜
    public static Item Zinc; // 锌

    // 板材
    public static Item Iron_Sheet; // 铁板
    public static Item Gold_Sheet; // 金板
    public static Item Copper_Sheet; // 铜板
    public static Item Brass_Sheet; // 黄铜板

    // 其他物品
    public static Item Pumpkin_Seeds; // 南瓜种子
    public static Item Pulp; // 纸浆

    // 测试物品
    public static Item Text_An; // 篝火火焰（动态贴图测试物品）
    
    /**
     * 加载所有自定义物品
     */
    public static void load(){
        // 基础矿物
        Iron = createBasicItem("iron", "cfcfcf");
        Gold = createBasicItem("gold", "f2df82");
        Andesite_Alloy = createBasicItem("andesite_alloy", "cfcfcf");
        Brass = createBasicItem("brass", "f2df82");
        Zinc = createBasicItem("zinc", "f2df82");
        
        // 板材
        Iron_Sheet = createBasicItem("iron_sheet", "cfcfcf");
        Gold_Sheet = createBasicItem("gold_sheet", "f2df82");
        Copper_Sheet = createBasicItem("copper_sheet", "c4a638");
        Brass_Sheet = createBasicItem("brass_sheet", "f2df82");

        // 其他物品
        Pumpkin_Seeds = createBasicItem("pumpkin_seeds", "ffcc00");
        Pulp = createBasicItem("pulp", "cfcfcf");

        // 测试物品
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
