package zzw.content;

import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * 机械系统类
 * 包含模组中添加的所有机械方块定义
 */
public class Z_Mechanics {
    // 机械动力源
    public static Block Water_Wheel;           // 水车
    public static Block Stress_Source;          // 应力源（沙盒模式专用）

    // 机械传输
    public static Block Mechanical_Shaft;      // 传动箱
    public static Block Small_Gear;            // 小齿轮

    // 机械应用
    // 暂时不需要应用机械

    /**
     * 加载所有自定义机械
     */
    public static void load() {
        // 创建动力源
        createPowerSources();

        // 创建传输组件
        createTransmission();

        // 创建应用机械
        createApplications();
    }

    /**
     * 创建动力源
     */
    private static void createPowerSources() {
        // 水车 - 只能放置在水中
        Water_Wheel = new Block("water_wheel") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 50, Z_Items.Iron, 40));
            size = 2;
            health = 200;
            solid = true;
            update = true;

            // 只能放置在水中的逻辑
            placeableLiquid = true;
        }};

        Water_Wheel.buildType = WaterWheelBuild::new;

        // 应力源 - 沙盒模式专用，可调节转速
        Stress_Source = new Block("stress_source") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 100, Items.copper, 80));
            size = 2;
            health = 500;
            solid = true;
            update = true;
            configurable = true;
            buildVisibility = BuildVisibility.sandboxOnly; // 仅限沙盒模式

            // 配置选项
            config(Float.class, (StressSourceBuild build, Float value) -> build.targetSpeed = Mathf.clamp(value, -256f, 256f));
        }};

        Stress_Source.buildType = StressSourceBuild::new;
    }

    /**
     * 创建传输组件
     */
    private static void createTransmission() {
        // 传动箱 - 远距离传输
        Mechanical_Shaft = new Block("transmission_box") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 10, Z_Items.Iron, 5));
            size = 1;
            health = 80;
            solid = true;
            update = true;
        }};

        Mechanical_Shaft.buildType = TransmissionBoxBuild::new;

        // 小齿轮 - 用于连接和传输动力
        Small_Gear = new Block("small_gear") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 5, Z_Items.Iron, 3));
            size = 1;
            health = 60;
            solid = true;
            update = true;
        }};

        Small_Gear.buildType = SmallGearBuild::new;


    }

    /**
     * 创建应用机械
     */
    private static void createApplications() {
        // 暂时不需要应用机械
    }

    /**
     * 机械组件基类 - 处理旋转速度和传输
     */
    public static class MechanicalComponentBuild extends Building {
        public float rotationSpeed = 0f;
        public float rotation = 0f;
        public float stress = 0f; // 添加应力值
        public boolean isSource = false;
        public boolean isSink = false;

        @Override
        public void update() {
            // 更新旋转角度
            rotation += rotationSpeed * Time.delta;

            // 如果不是动力源，尝试从相邻方块获取旋转速度和应力
            if (!isSource) {
                float maxInputSpeed = 0f;
                float maxInputStress = 0f;

                // 检查四个方向的相邻方块
                for (int i = 0; i < 4; i++) {
                    Building other = nearby(i);
                    if (other instanceof MechanicalComponentBuild component) {
                        // 只从动力源或已连接的组件获取动力
                        if (component.isSource || component.rotationSpeed > 0) {
                            // 检查是否面对面连接
                            if (component.nearby((i + 2) % 4) == this) {
                                maxInputSpeed = Math.max(maxInputSpeed, component.rotationSpeed);
                                maxInputStress = Math.max(maxInputStress, component.stress);
                            }
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

            // 绘制旋转指示器
            if (rotationSpeed > 0.01f) {
                // 使用更平滑的旋转速度，让视觉效果更舒适
                float visualRotationSpeed = rotationSpeed * 0.2f;
                Drawf.spinSprite(block.region, x, y, rotation * visualRotationSpeed);
            }
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
     * 水车实现
     */
    public static class WaterWheelBuild extends MechanicalComponentBuild {
        public float shallowWaterSpeed = 96f;      // 浅水中的转速
        public float deepWaterSpeed = 128f;        // 深水中的转速
        public float shallowWaterStress = 512f;    // 浅水中的应力
        public float deepWaterStress = 1024f;      // 深水中的应力

        @Override
        public void created() {
            super.created();
            isSource = true;

            // 根据水深设置初始转速
            updateRotationBasedOnWaterDepth();
        }

        @Override
        public void update() {
            super.update();

            // 检查是否在液体中
            boolean inLiquid = tile != null && tile.floor().liquidDrop != null;

            // 如果不在液体中则停止旋转
            if (!inLiquid) {
                rotationSpeed = 0f;
            } else {
                // 根据水深更新转速
                updateRotationBasedOnWaterDepth();
            }
        }

        /**
         * 根据水深更新转速和应力
         */
        private void updateRotationBasedOnWaterDepth() {
            if (tile == null) return;

            // 检查是否是深水（通过地板类型判断）
            boolean isDeepWater = tile.floor().name.contains("deep") || 
                                tile.floor().name.contains("water2") || 
                                tile.floor().liquidDrop != null && tile.floor().isLiquid;

            if (isDeepWater) {
                // 深水中设置更高的转速和应力
                rotationSpeed = deepWaterSpeed;
                stress = deepWaterStress;
            } else {
                // 浅水中设置较低的转速和应力
                rotationSpeed = shallowWaterSpeed;
                stress = shallowWaterStress;
            }
        }
    }



    /**
     * 传动箱实现 - 只传输应力和转速，不旋转
     */
    public static class TransmissionBoxBuild extends MechanicalComponentBuild {
        @Override
        public void draw() {
            super.draw();

            // 传动箱不显示旋转动画，只传输应力和转速
        }
    }

    /**
     * 小齿轮实现 - 自动对接防止穿模
     */
    public static class SmallGearBuild extends MechanicalComponentBuild {
        // 记录相邻齿轮的方向，用于绘制不同的齿轮朝向
        public boolean[] connectedSides = new boolean[4];

        @Override
        public void created() {
            super.created();
            updateConnections();
        }

        @Override
        public void onProximityUpdate() {
            super.onProximityUpdate();
            updateConnections();
        }

        /**
         * 更新连接状态，检测哪些方向有相邻的齿轮
         */
        private void updateConnections() {
            // 重置所有连接状态
            for(int i = 0; i < 4; i++) {
                connectedSides[i] = false;
            }

            // 检查四个方向
            for(int i = 0; i < 4; i++) {
                Building other = nearby(i);
                if(other instanceof MechanicalComponentBuild) {
                    connectedSides[i] = true;
                }
            }
        }

        @Override
        public void draw() {
            super.draw();

            // 根据连接方向绘制齿轮，确保不会穿模
            if(rotationSpeed > 0.01f) {
                // 使用更平滑的旋转速度，让视觉效果更舒适
                float visualRotationSpeed = rotationSpeed * 0.3f;

                // 绘制中心齿轮
                Drawf.spinSprite(block.region, x, y, rotation * visualRotationSpeed);

                // 根据连接方向绘制延伸齿轮，确保不会穿模
                for(int i = 0; i < 4; i++) {
                    if(connectedSides[i]) {
                        // 计算延伸方向
                        float dx = Mathf.cos((i * 90) * Mathf.degreesToRadians);
                        float dy = Mathf.sin((i * 90) * Mathf.degreesToRadians);

                        // 在连接方向绘制延伸齿轮部分，稍微偏移避免穿模
                        Drawf.spinSprite(block.region, x + dx * 0.3f, y + dy * 0.3f, rotation * visualRotationSpeed);
                    }
                }
            }
        }
    }

    /**
     * 应力源实现 - 沙盒模式专用
     */
    public static class StressSourceBuild extends MechanicalComponentBuild {
        public float targetSpeed = 0f;
        public float acceleration = 0.5f;

        @Override
        public void update() {
            super.update();

            // 标记为动力源
            isSource = true;

            // 平滑调整到目标速度
            if (Math.abs(rotationSpeed - targetSpeed) > 0.01f) {
                if (rotationSpeed < targetSpeed) {
                    rotationSpeed = Math.min(rotationSpeed + acceleration, targetSpeed);
                } else {
                    rotationSpeed = Math.max(rotationSpeed - acceleration, targetSpeed);
                }
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