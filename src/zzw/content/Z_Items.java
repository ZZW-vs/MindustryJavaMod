package zzw.content;

import arc.graphics.Color;
import mindustry.type.Item;

public class Z_Items {
    public static Item Iron;
    public static Item Gold;
    public static Item Andesite_Alloy;
    public static Item Brass;
    public static Item Zinc;
    public static Item Copper;

    public static Item Iron_Sheet;
    public static Item Gold_Sheet;
    public static Item Copper_Sheet;
    public static Item Brass_Sheet;

    public static Item Pumpkin_Seeds;
    public static Item Pulp;

    public static Item Text_An;

    public static void load(){
        Iron = createBasicItem("iron", "cfcfcf");
        Gold = createBasicItem("gold", "f2df82");
        Andesite_Alloy = createBasicItem("andesite_alloy", "cfcfcf");
        Brass = createBasicItem("brass", "f2df82");
        Zinc = createBasicItem("zinc", "f2df82");

        Iron_Sheet = createBasicItem("iron_sheet", "cfcfcf");
        Gold_Sheet = createBasicItem("gold_sheet", "f2df82");
        Copper_Sheet = createBasicItem("copper_sheet", "c4a638");
        Brass_Sheet = createBasicItem("brass_sheet", "f2df82");

        Pumpkin_Seeds = createBasicItem("pumpkin_seeds", "ffcc00");
        Pulp = createBasicItem("pulp", "cfcfcf");

        Text_An = new Item("campfire_fire", Color.valueOf("cfcfcf")){{
            frames = 5;
            transitionFrames = 1;
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
    }

    private static Item createBasicItem(String name, String color) {
        return new Item(name, Color.valueOf(color)){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
    }
}
