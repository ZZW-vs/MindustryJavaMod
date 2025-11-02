package zzw.content;

import mindustry.type.Item;

public class items {
    public static Item Iron, Iron_Sheet, Gold, Gold_Sheet;
    public static void load(){
        Iron = new Item("iron"){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
        Iron_Sheet = new Item("iron_sheet"){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
        Gold = new Item("gold"){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
        Gold_Sheet = new Item("gold_sheet"){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
    }
}
