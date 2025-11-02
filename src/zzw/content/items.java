package zzw.content;

import mindustry.type.Item;

public class items {
    public static Item Iron, Iron_Sheet, Nails;
    public static void load(){
        Iron = new Item("iron"){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = true;
        }};
        Iron_Sheet = new Item("iron_sheet"){{
            hardness = 1;
            cost = 0.6f;
            alwaysUnlocked = false;
        }};
    }
}
