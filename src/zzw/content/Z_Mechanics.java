
package zzw.content;

import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * 机械系统类
 * 包含模组中添加的所有机械方块定义
 */
public class Z_Mechanics {
    /** 应力源 - 沙盒模式专用 */
    public static Block stressSource;

    /** 传动箱 */
    public static Block mechanicalShaft;

    /**
     * 加载所有自定义机械
     */
    public static void load() {
        // 创建动力源
        createPowerSources();

        // 创建传输组件
        createTransmission();
    }

    /**
     * 创建动力源
     */
    private static void createPowerSources() {
        // 应力源 - 沙盒模式专用，可调节转速
        stressSource = new Block("stress_source") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 100, Items.copper, 80));
            size = 1;
            health = 500;
            solid = true;
            update = true;
            configurable = true;
            buildVisibility = BuildVisibility.sandboxOnly; // 仅限沙盒模式

            // 配置选项
            config(Float.class, (StressSourceBuild build, Float value) -> build.targetSpeed = Mathf.clamp(value, -256f, 256f));
        }};

        stressSource.buildType = StressSourceBuild::new;
    }

    /**
     * 创建传输组件
     */
    private static void createTransmission() {
        // 传动箱
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
     * 机械组件基类 - 处理旋转速度和传输
     */
    public static class MechanicalComponentBuild extends Building {
        /** 旋转速度 */
        public float rotationSpeed = 0f;
        /** 旋转角度 */
        public float rotation = 0f;
        /** 应力值 */
        public float stress = 0f;
        /** 是否为动力源 */
        public boolean isSource = false;
        /** 是否为负载端 */
        public boolean isSink = false;

        // 缓存相邻组件，避免每帧重复计算
        private final MechanicalComponentBuild[] cachedNeighbors = new MechanicalComponentBuild[4];
        private int lastCacheFrame = -1;

        @Override
        public void update() {
            // 如果不是动力源，尝试从相邻方块获取旋转速度和应力
            if (!isSource) {
                float maxInputSpeed = 0f;
                float maxInputStress = 0f;

                // 缓存相邻组件以提高性能
                if (lastCacheFrame != (int)Time.time) {
                    lastCacheFrame = (int)Time.time;
                    for (int i = 0; i < 4; i++) {
                        Building other = nearby(i);
                        cachedNeighbors[i] = (other instanceof MechanicalComponentBuild) ? 
                            (MechanicalComponentBuild) other : null;
                    }
                }

                // 检查四个方向的相邻方块
                for (int i = 0; i < 4; i++) {
                    MechanicalComponentBuild component = cachedNeighbors[i];
                    if (component != null) {
                        // 只从动力源或已连接的组件获取动力
                        if (component.isSource || component.rotationSpeed > 0) {
                            // 移除面对面连接限制，允许所有相邻组件接收动力
                            maxInputSpeed = Math.max(maxInputSpeed, component.rotationSpeed);
                            maxInputStress = Math.max(maxInputStress, component.stress);
                        }
                    }
                }

                // 更新旋转速度和应力
                rotationSpeed = maxInputSpeed;
                stress = maxInputStress;
            }

            // 如果是应用机械，处理生产逻辑
            if (isSink && rotationSpeed > 0) {
                handleMechanicalOperation();
            }
        }

        @Override
        public void draw() {
            super.draw();
            // 基础组件不显示旋转动画
        }

        @Override
        public void display(Table table) {
            super.display(table);

            // 添加应力和转速信息显示
            table.row();
            table.add("[accent]应力: [white]" + (int)stress + " us");
            table.row();
            table.add("[accent]转速: [white]" + (int)rotationSpeed + " bpm");
        }

        /**
         * 处理机械操作，由子类实现
         */
        protected void handleMechanicalOperation() {
            // 基础实现为空，由子类重写
        }
    }

    /**
     * 传动箱实现 - 只传输应力和转速，不旋转
     */
    public static class TransmissionBoxBuild extends MechanicalComponentBuild {
        // 传动箱继承基类的draw方法，不显示旋转动画
    }

    /**
     * 应力源实现 - 沙盒模式专用
     */
    public static class StressSourceBuild extends MechanicalComponentBuild {
        /** 目标转速 */
        public float targetSpeed = 0f;
        /** 加速度 */
        public float acceleration = 0.5f;

        // 优化：添加速度变化阈值，避免微小变化时的频繁计算
        private static final float SPEED_THRESHOLD = 0.01f;

        @Override
        public void update() {
            super.update();

            // 标记为动力源
            isSource = true;

            // 平滑调整到目标速度
            float speedDifference = targetSpeed - rotationSpeed;
            if (Math.abs(speedDifference) > SPEED_THRESHOLD) {
                // 使用线性插值使速度变化更平滑
                rotationSpeed += speedDifference * acceleration * Time.delta / 60f;
            } else {
                // 当接近目标速度时，直接设为目标值
                rotationSpeed = targetSpeed;
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.slider(-256f, 256f, 1f, targetSpeed, this::configure).row();
        }

        @Override
        public Float config() {
            return targetSpeed;
        }
    }
}

