package zzw.content.mechanics;

import arc.math.Mathf;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import zzw.content.Z_Items;

/**
 * 机械系统方块定义
 */
public class Z_Mechanics {
    public static Block stressSource;
    public static Block transmissionBox;
    public static Block cogwheel;

    public static void load() {
        // 应力源: 提供动力, 可配置转速 (0-256)
        stressSource = new Block("stress_source") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 100, Items.copper, 80));
            size = 1;
            health = 500;
            solid = true;
            update = true;
            configurable = true;
            config(Float.class, (MechanicalBuilds.StressSourceBuild build, Float value) ->
                    build.setTargetSpeed(Mathf.clamp(value, 0f, 256f)));
        }};
        stressSource.buildType = MechanicalBuilds.StressSourceBuild::new;

        // 传动箱: 传递机械动力, 也为相邻工厂提供加速
        transmissionBox = new Block("transmission_box") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 10, Z_Items.Iron, 5));
            size = 1;
            health = 80;
            solid = true;
            update = true;
        }};
        transmissionBox.buildType = MechanicalBuilds.TransmissionBoxBuild::new;

        // 齿轮: 视觉上旋转表示动力流动
        cogwheel = new Block("cogwheel-z") {{
            requirements(Category.crafting, ItemStack.with(Items.copper, 15, Z_Items.Iron, 10));
            size = 1;
            health = 120;
            solid = true;
            update = true;
        }};
        cogwheel.buildType = MechanicalBuilds.CogwheelBuild::new;
    }
}
