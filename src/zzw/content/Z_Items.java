package zzw.content;

import arc.graphics.Color;
import mindustry.type.Item;

/**
 * 模组物品类
 * 定义模组中所有的物品
 */
public class Z_Items {
    // 基础金属
    public static Item Iron;
    public static Item Gold;
    public static Item Andesite;
    public static Item Andesite_Alloy;
    public static Item Brass;
    public static Item Zinc;

    // 金属板材
    public static Item Iron_Sheet;
    public static Item Gold_Sheet;
    public static Item Copper_Sheet;
    public static Item Brass_Sheet;

    // 农作物相关
    public static Item Pumpkin_Seeds;
    public static Item Pulp;

    // 特殊物品
    public static Item Text_An;

    /**
     * 加载所有物品
     */
    public static void load(){
        // 基础金属
        Iron = createBasicItem("iron", "cfcfcf");
        Gold = createBasicItem("gold", "f2df82");
        Andesite = createBasicItem("andesite", "8b8680");
        Andesite_Alloy = createBasicItem("andesite_alloy", "cfcfcf");
        Brass = createBasicItem("brass", "f2df82");
        Zinc = createBasicItem("zinc", "f2df82");

        // 金属板材
        Iron_Sheet = createBasicItem("iron_sheet", "cfcfcf");
        Gold_Sheet = createBasicItem("gold_sheet", "f2df82");
        Copper_Sheet = createBasicItem("copper_sheet", "c4a638");
        Brass_Sheet = createBasicItem("brass_sheet", "f2df82");

        // 农作物相关
        Pumpkin_Seeds = createBasicItem("pumpkin_seeds", "ffcc00");
        Pulp = createBasicItem("pulp", "cfcfcf");

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
     * 创建基础物品
     * @param name 物品名称
     * @param color 物品颜色
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
