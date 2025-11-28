package zzw.content.mechanics;

import arc.math.Mathf;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import zzw.content.Z_Items;

/**
 * 机械系统类 - 优化版
 * 提供高效的机械传动系统实现
 * 优化点：
 * 1. 使用IntSet缓存已访问的方块，避免重复计算
 * 2. 优化邻居检测逻辑，减少内存分配
 * 3. 添加机械效率计算，使系统更真实
 * 4. 改进UI更新机制，减少不必要的渲染
 * 5. 模块化设计，将不同组件分离到单独文件中
 */
public class Z_Mechanics {
    // 机械方块定义
    public static Block stressSource;      // 应力源
    public static Block mechanicalShaft;   // 传动箱
    public static Block cogwheel;          // 齿轮

    /**
     * 加载所有自定义机械
     */
    public static void load() {
        createPowerSources();
        createTransmission();
        createCogwheels();
    }

    /**
     * 创建动力源 - 应力源
     */
    private static void createPowerSources() {
        stressSource = new Block("stress_source") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 100, Items.copper, 80));
            size = 1;
            health = 500;
            solid = true;
            update = true;
            configurable = true;
            buildVisibility = BuildVisibility.sandboxOnly;

            config(Float.class, (StressSourceBuild build, Float value) ->
                build.setTargetSpeed(Mathf.clamp(value, 0f, 256f)));
        }};

        stressSource.buildType = StressSourceBuild::new;
    }

    /**
     * 创建传输组件 - 传动箱
     */
    private static void createTransmission() {
        mechanicalShaft = new Block("transmission_box") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 10, Z_Items.Iron, 5));
            size = 1;
            health = 80;
            solid = true;
            update = true;
        }};

        mechanicalShaft.buildType = TransmissionBoxBuild::new;
    }

    /**
     * 创建传输组件 - 齿轮
     */
    private static void createCogwheels() {
        cogwheel = new Block("zzw-cogwheel") {{
            requirements(Category.crafting, ItemStack.with(Items.copper, 15, Z_Items.Iron, 10));
            size = 1;
            health = 120;
            solid = true;
            update = true;
        }};

        cogwheel.buildType = CogwheelBuild::new;
    }
}
