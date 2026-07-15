package zzw.content;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.production.Drill;

public class Z_Mine {
    public static Block Andesite;
    public static Block Andesite_Drill;

    // PU132 矿石 (产物已在 Z_Items 中定义)
    public static Block oreNickel, oreUmbrium, oreLuminum, oreImberium, oreMonolite;

    public static void load() {
        Andesite = new OreBlock("andesite", Z_Items.Andesite);

        Andesite_Drill = new Drill("andesite_drill") {{
            requirements(Category.production, ItemStack.with(Items.copper, 80, Items.lead, 60));
            size = 2;
            drillTime = 250f;
            hasItems = true;
            itemCapacity = 15;
            returnItem = Z_Items.Andesite;
            tier = 2;
            liquidCapacity = 15f;
            hasLiquids = true;
            drawRim = true;
            updateEffect = Fx.pulverizeSmall;
            drillEffect = Fx.pulverizeSmall;
            warmupSpeed = 0.02f;
            consumeLiquid(Liquids.water, 0.15f).boost();
        }};

        // ===== PU132 矿石移植 (oreDefault=false, 不在默认地形生成) =====
        // 贴图: assets/sprites/矿石/nickel1-3.png 等 (PU132 blocks/environment/ 复制)
        // 名称: 使用物品名作为方块名, OreBlock自动加载 name+1/2/3 变体贴图
        // 翻译: OreBlock自动使用itemDrop.localizedName, 无需单独block翻译

        oreNickel = new OreBlock("nickel", Z_Items.nickel) {{
            oreScale = 24.77f;
            oreThreshold = 0.913f;
        }};

        oreUmbrium = new OreBlock("umbrium", Z_Items.umbrium) {{
            oreScale = 23.77f;
            oreThreshold = 0.813f;
        }};

        oreLuminum = new OreBlock("luminum", Z_Items.luminum) {{
            oreScale = 23.77f;
            oreThreshold = 0.81f;
        }};

        oreImberium = new OreBlock("imberium", Z_Items.imberium) {{
            oreScale = 23.77f;
            oreThreshold = 0.807f;
        }};

        oreMonolite = new OreBlock("monolite", Z_Items.monolite) {{
            oreScale = 23.77f;
            oreThreshold = 0.807f;
        }};
    }
}
