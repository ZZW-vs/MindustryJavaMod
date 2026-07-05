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
    }
}
