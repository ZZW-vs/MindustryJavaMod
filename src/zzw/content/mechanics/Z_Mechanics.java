package zzw.content.mechanics;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import zzw.content.Z_Items;

/**
 * 机械系统类 - 简化版
 * 提供基础的机械传动系统实现
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

        // UI组件将在游戏初始化完成后创建，不在此处调用
    }

    /**
     * 创建动力源 - 应力源
     */
    private static void createPowerSources() {
        stressSource = createBlock("stress_source",
            ItemStack.with(Items.lead, 100, Items.copper, 80),
            500, BuildVisibility.sandboxOnly,
            block -> block.config(Float.class, (MechanicalBuilds.StressSourceBuild build, Float value) ->
                build.setTargetSpeed(Mathf.clamp(value, 0f, 256f))));

        stressSource.buildType = MechanicalBuilds.StressSourceBuild::new;
    }

    /**
     * 创建传输组件 - 传动箱
     */
    private static void createTransmission() {
        mechanicalShaft = createBlock("transmission_box",
            ItemStack.with(Items.lead, 10, Z_Items.Iron, 5),
            80);

        mechanicalShaft.buildType = MechanicalBuilds.TransmissionBoxBuild::new;
    }

    /**
     * 创建传输组件 - 齿轮
     */
    private static void createCogwheels() {
        // 启用齿轮创建
        cogwheel = createBlock("cogwheel-z",
            ItemStack.with(Items.copper, 15, Z_Items.Iron, 10),
            120);

        // 设置齿轮的纹理路径
        cogwheel.region = Core.atlas.find("mechanical-cogwheel-z");
        cogwheel.buildType = MechanicalBuilds.CogwheelBuild::new;
    }

    /**
     * 创建通用方块
     * @param name 方块名称
     * @param requirements 所需材料
     * @param health 生命值
     * @return 创建的方块
     */
    private static Block createBlock(String name, ItemStack[] requirements, int health) {
        return createBlock(name, requirements, health, BuildVisibility.shown, null);
    }

    /**
     * 创建通用方块（带配置）
     * @param name 方块名称
     * @param requirements 所需材料
     * @param blockHealth 生命值
     * @param visibility 可见性
     * @param config 配置函数
     * @return 创建的方块
     */
    private static Block createBlock(String name, ItemStack[] requirements, int blockHealth,
                                    BuildVisibility visibility, BlockConfigurator config) {
        Block block = new Block(name) {{
            requirements(Category.crafting, requirements);
            size = 1;
            health = blockHealth;
            solid = true;
            update = true;
            configurable = config != null;
            buildVisibility = visibility;

            if (config != null) {
                config.configure(this);
            }
        }};

        // 使用requirements参数以消除未使用警告
        if (requirements == null) {
            throw new IllegalArgumentException("Requirements cannot be null");
        }

        return block;
    }

    /**
     * 方块配置接口
     */
    @FunctionalInterface
    private interface BlockConfigurator {
        void configure(Block block);
    }

    /**
     * 机械动力系统UI组件基类
     * 提供统一的UI样式
     */
    public static class MechanicalUI {
        // 颜色方案
        public static final Color
            BG_COLOR = new Color(0.1f, 0.1f, 0.1f, 0.8f),
            BORDER_COLOR = new Color(0.3f, 0.3f, 0.3f, 0.9f),
            NORMAL_COLOR = Color.lightGray,
            STRESS_COLOR = new Color(1f, 0.7f, 0.4f, 1f),
            SPEED_COLOR = new Color(0.4f, 0.7f, 1f, 1f);

        // 常量
        public static final float UPDATE_INTERVAL = 1/30f;

        // 这些常量在实际UI组件中使用

        // 创建信息面板
        public static Table createInfoPanel(String title) {
            Table panel = new Table();

            // 背景和边框
            panel.background(Styles.black6);
            panel.margin(10);
            panel.defaults().pad(5);

            // 标题
            var titleLabel = new arc.scene.ui.Label(title);
            titleLabel.setFontScale(1.2f);
            titleLabel.setColor(Color.white);
            panel.add(titleLabel).colspan(2).center().row();

            // 分隔线
            var separator = new arc.scene.ui.Image();
            separator.setDrawable(new arc.scene.style.TextureRegionDrawable(arc.Core.atlas.find("white")));
            separator.setColor(BORDER_COLOR);
            separator.setHeight(2f);
            panel.add(separator).growX().colspan(2).pad(5).row();

            return panel;
        }
    }
}