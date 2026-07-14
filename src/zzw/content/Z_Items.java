package zzw.content;

import arc.graphics.Color;
import mindustry.type.Item;

public class Z_Items {
    // ProjectUnity物品 (faction-alloys)
    public static Item advanceAlloy, cupronickel, darkAlloy, dirium, lightAlloy, monolithAlloy, archDebris,
            plagueAlloy, sparkAlloy, superAlloy, terminaAlloy, terminationFragment, terminum;
    // ProjectUnity物品 (faction items)
    public static Item contagium, denseAlloy, imberium, irradiantSurge, luminum, monolite, nickel, steel, stone,
            umbrium, xenium, uranium;
    // 基础金属
    public static Item Iron, Gold, Andesite, Andesite_Alloy, Brass, Zinc;
    // 金属板材
    public static Item Iron_Sheet, Gold_Sheet, Copper_Sheet, Brass_Sheet;
    // 农作物相关
    public static Item Pumpkin_Seeds, Pulp;
    // 特殊物品
    public static Item Campfire_Fire;

    public static void load() {
        //region ProjectUnity faction-alloys
        advanceAlloy = new Item("advance-alloy", Color.valueOf("748096")) {{
            cost = 1.4f;
            radioactivity = 0.1f;
        }};

        cupronickel = new Item("cupronickel", Color.valueOf("a19975")) {{
            cost = 2f;
        }};

        darkAlloy = new Item("dark-alloy", Color.valueOf("716264")) {{
            cost = 1.4f;
            radioactivity = 0.11f;
        }};

        dirium = new Item("dirium", Color.valueOf("96f7c3")) {{
            cost = 0.3f;
            hardness = 9;
        }};

        lightAlloy = new Item("light-alloy", Color.valueOf("e0ecee")) {{
            cost = 1.4f;
            radioactivity = 0.08f;
        }};

        // AnimatedItem 用 Item + frames/transitionFrames/frameTime 模拟
        monolithAlloy = new Item("monolith-alloy", Color.valueOf("b3b3b3")) {{
            cost = 1.4f;
            flammability = 0.1f;
            radioactivity = 0.12f;
            frames = 14;
            frameTime = 1f;
            transitionFrames = 3;
        }};

        archDebris = new Item("archaic-debris", Color.valueOf("4d4d4d")) {{
            cost = 1.3f;
            radioactivity = 0.1f;
            frames = 7;
            frameTime = 3f;
            transitionFrames = 1;
        }};

        plagueAlloy = new Item("plague-alloy", Color.valueOf("6a766a")) {{
            cost = 1.4f;
            radioactivity = 0.16f;
        }};

        sparkAlloy = new Item("spark-alloy", Color.valueOf("f4ff61")) {{
            cost = 1.3f;
            radioactivity = 0.01f;
            explosiveness = 0.1f;
        }};

        superAlloy = new Item("super-alloy", Color.valueOf("67a8a0")) {{
            cost = 2.5f;
        }};

        terminaAlloy = new Item("termina-alloy", Color.valueOf("9e6d74")) {{
            cost = 4.2f;
            radioactivity = 1.74f;
        }};

        terminationFragment = new Item("termination-fragment", Color.valueOf("f9504f")) {{
            cost = 1.2f;
            radioactivity = 3.64f;
        }};

        terminum = new Item("terminum", Color.valueOf("f53036")) {{
            cost = 3.2f;
            radioactivity = 1.32f;
        }};
        //endregion

        //region ProjectUnity faction items
        contagium = new Item("contagium", Color.valueOf("68985e")) {{
            cost = 1.5f;
            hardness = 3;
            radioactivity = 0.7f;
        }};

        denseAlloy = new Item("dense-alloy", Color.valueOf("a68a84")) {{
            hardness = 2;
            cost = 2f;
        }};

        imberium = new Item("imberium", Color.valueOf("f6ff7d")) {{
            cost = 1.4f;
            hardness = 3;
            radioactivity = 0.6f;
        }};

        irradiantSurge = new Item("irradiant-surge", Color.valueOf("3d423e")) {{
            cost = 2f;
            frames = 2;
            frameTime = 3f;
            transitionFrames = 30;
        }};

        luminum = new Item("luminum", Color.valueOf("e9eaf1")) {{
            cost = 1.2f;
            hardness = 3;
            radioactivity = 0.1f;
        }};

        monolite = new Item("monolite", Color.valueOf("4d4d4d")) {{
            cost = 1.5f;
            hardness = 3;
            radioactivity = 0.2f;
            flammability = 0.2f;
        }};

        nickel = new Item("nickel", Color.valueOf("6e9675")) {{
            hardness = 3;
            cost = 2.5f;
        }};

        steel = new Item("steel", Color.valueOf("e1e3ed")) {{
            hardness = 4;
            cost = 2.5f;
        }};

        stone = new Item("stone", Color.valueOf("8a8a8a")) {{
            hardness = 1;
            cost = 0.4f;
            lowPriority = true;
        }};

        umbrium = new Item("umbrium", Color.valueOf("8c3d3b")) {{
            cost = 1.2f;
            hardness = 3;
            radioactivity = 0.2f;
        }};

        xenium = new Item("xenium", Color.valueOf("9dddff")) {{
            cost = 1.2f;
            hardness = 3;
            radioactivity = 0.6f;
        }};

        uranium = new Item("uranium", Color.valueOf("ace284")) {{
            cost = 2f;
            hardness = 3;
            radioactivity = 1f;
        }};
        //endregion

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
        Campfire_Fire = new Item("campfire_fire", Color.valueOf("cfcfcf")) {{
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
