package zzw.content;

import arc.graphics.Color;
import mindustry.type.Item;

public class Z_Items {
    // 基础金属
    public static Item Iron, Gold, Andesite, Andesite_Alloy, Brass, Zinc;
    // 金属板材
    public static Item Iron_Sheet, Gold_Sheet, Copper_Sheet, Brass_Sheet;
    // 农作物相关
    public static Item Pumpkin_Seeds, Pulp;
    // 特殊物品
    public static Item Text_An;

    public static void load() {
        // 基础金属
        Iron = basic("iron", "cfcfcf");
        Gold = basic("gold", "f2df82");
        Andesite = basic("andesite", "8b8680");
        Andesite_Alloy = basic("andesite_alloy", "cfcfcf");
        Brass = basic("brass", "f2df82");
        Zinc = basic("zinc", "f2df82");

        // 金属板材
        Iron_Sheet = basic("iron_sheet", "cfcfcf");
        Gold_Sheet = basic("gold_sheet", "f2df82");
        Copper_Sheet = basic("copper_sheet", "c4a638");
        Brass_Sheet = basic("brass_sheet", "f2df82");

        // 农作物相关
        Pumpkin_Seeds = basic("pumpkin_seeds", "ffcc00");
        Pulp = basic("pulp", "cfcfcf");

        // 特殊：动态贴图
        Text_An = new Item("campfire_fire", Color.valueOf("cfcfcf")) {{
            frames = 5;
            transitionFrames = 1;
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
    }

    private static Item basic(String name, String color) {
        return new Item(name, Color.valueOf(color)) {{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
    }
}
