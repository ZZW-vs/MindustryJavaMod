package zzw.content;

import arc.graphics.Color;
import mindustry.type.Item;

public class items {
    public static Item Iron, Iron_Sheet, Gold, Gold_Sheet;
    public static void load(){
        Iron = new Item("iron", Color.valueOf("cfcfcf")){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
        Iron_Sheet = new Item("iron_sheet", Color.valueOf("cfcfcf")){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
        Gold = new Item("gold", Color.valueOf("f2df82")){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
        Gold_Sheet = new Item("gold_sheet", Color.valueOf("f2df82")){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
    }
}
