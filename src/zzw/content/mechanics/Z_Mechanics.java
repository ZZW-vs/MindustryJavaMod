package zzw.content.mechanics;

import arc.math.Mathf;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import zzw.content.Z_Items;

public class Z_Mechanics {
    public static Block stressSource;
    public static Block mechanicalShaft;
    public static Block cogwheel;

    public static void load() {
        stressSource = createConfigurableBlock("stress_source",
                ItemStack.with(Items.lead, 100, Items.copper, 80),
                500, block -> block.config(Float.class,
                        (MechanicalBuilds.StressSourceBuild build, Float value) ->
                                build.setTargetSpeed(Mathf.clamp(value, 0f, 256f))));
        stressSource.buildType = MechanicalBuilds.StressSourceBuild::new;

        mechanicalShaft = createBlock("transmission_box",
                ItemStack.with(Items.lead, 10, Z_Items.Iron, 5), 80);
        mechanicalShaft.buildType = MechanicalBuilds.TransmissionBoxBuild::new;

        cogwheel = createBlock("cogwheel-z",
                ItemStack.with(Items.copper, 15, Z_Items.Iron, 10), 120);
        cogwheel.buildType = MechanicalBuilds.CogwheelBuild::new;
    }

    private static Block createBlock(String name, ItemStack[] requirements, int health) {
        return new Block(name) {{
            requirements(Category.crafting, requirements);
            size = 1;
            this.health = health;
            solid = true;
            update = true;
            configurable = false;
            buildVisibility = BuildVisibility.shown;
        }};
    }

    private static Block createConfigurableBlock(String name, ItemStack[] requirements,
                                                  int health, BlockConfigurator config) {
        Block block = new Block(name) {{
            requirements(Category.crafting, requirements);
            size = 1;
            this.health = health;
            solid = true;
            update = true;
            configurable = true;
            buildVisibility = BuildVisibility.sandboxOnly;
        }};
        config.configure(block);
        return block;
    }

    @FunctionalInterface
    private interface BlockConfigurator {
        void configure(Block block);
    }
}