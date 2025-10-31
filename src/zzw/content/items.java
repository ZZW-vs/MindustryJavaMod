package zzw.content;

import mindustry.type.Item;

public class items {
    public static Item Iron, Iron_Plate, Nails;
    public static void load(){
        Iron = new Item("iron"){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = true;
        }};
        Iron_Plate = new Item("iron_plate"){{
            hardness = 1;
            cost = 0.6f;
            alwaysUnlocked = false;
        }};
        Nails = new Item("nails"){{
            hardness = 1;
            cost = 0.5f;
            alwaysUnlocked = false;
        }};
    }
}
