package zzw.content.mechanics;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;

/**
 * 机械组件构建类集合
 * 包含所有机械组件的实现
 */
public class MechanicalBuilds {
    // 公共常量
    private static final float FRAME_TIME = 0.016f; // 假设60fps
    private static final float STRESS_THRESHOLD = 0.5f; // 高应力阈值
    private static final Color STRESS_COLOR = new Color(1f, 0.7f, 0.4f, 1f); // 高应力颜色
    private static final int SNAP_INTERVAL = 32; // 转速吸附间隔
    private static final float INFINITY_STRESS = 10000f; // 无大应力阈值

    /**
     * 齿轮方块实现
     * 特性：
     * 1. 整个方块旋转动画，转速越大转得越快
     * 2. 机械动力传输
     */
    public static class CogwheelBuild extends MechanicalComponentBuild {
        // 齿轮特有属性
        private float rotation = 0f;              // 当前旋转角度
        private TextureRegion gearRegion;         // 齿轮纹理

        @Override
        public void created() {
            super.created();
            // 加载齿轮纹理 - 使用完整的子目录路径
            gearRegion = Core.atlas.find("mechanical-cogwheel-z");
            // 如果找不到专用齿轮纹理，则使用方块区域
            if (gearRegion == null || !gearRegion.found()) {
                gearRegion = block.region;
            }
        }

        @Override
        public void update() {
            super.update();

            // 更新旋转角度 - 转速越大，旋转越快
            if (rotationSpeed > SPEED_THRESHOLD) {
                rotation += rotationSpeed * FRAME_TIME;
            }
        }

        @Override
        public void draw() {
            // 根据转速选择是否旋转
            boolean isRotating = rotationSpeed > SPEED_THRESHOLD;
            float drawRotation = isRotating ? rotation : 0f;

            // 绘制齿轮纹理
            TextureRegion region = gearRegion != null ? gearRegion : block.region;
            Draw.rect(region, x, y, drawRotation);

            // 绘制应力指示器
            if (stress > STRESS_THRESHOLD) {
                Draw.color(STRESS_COLOR);
                float strokeWidth = isRotating ? 2f * stress : 1.5f * stress;
                Lines.stroke(strokeWidth);

                // 根据转速调整线条长度
                float lineLen = isRotating ? 8f + rotationSpeed * 3f : 8f;

                // 绘制4个方向的应力指示线
                for (int i = 0; i < 4; i++) {
                    float angle = isRotating ? rotation + i * 90f : i * 90f;
                    Lines.lineAngle(x, y, angle, lineLen);
                }

                // 绘制中心点
                Fill.circle(x, y, 3f + stress * 2f);
                Draw.color();
            }

            // 绘制团队颜色（如果有）
            if (team != null) {
                Draw.color(team.color);
                Draw.rect(block.teamRegion, x, y, drawRotation);
                Draw.color();
            }
        }

        @Override
        public void display(Table table) {
            super.display(table);
            addStatusDisplay(table, stress, rotationSpeed);
        }
    }

    /**
     * 应力源实现
     * 功能特性：
     * 1. 可配置的转速
     * 2. 作为动力源，向连接的机械组件提供动力
     */
    public static class StressSourceBuild extends MechanicalComponentBuild {
        private float targetSpeed = 0f;          // 目标转速

        @Override
        public void update() {
            super.update();

            // 标记为动力源
            isSource = true;

            // 设置应力源的应力值为1000000
            stress = INFINITY_STRESS;

            // 平滑调整到目标速度
            adjustSpeed();
        }

        /**
         * 直接设置速度，去掉过渡效果
         */
        private void adjustSpeed() {
            // 直接设置为目标速度，去掉过渡效果
            rotationSpeed = targetSpeed;
        }

        /**
         * 设置目标速度
         */
        public void setTargetSpeed(float speed) {
            targetSpeed = speed;
            // 标记网络需要更新
            markNetworkForUpdate();
        }

        @Override
        public void buildConfiguration(Table table) {
            // 滑块范围调整为0到256，步长为1
            table.slider(0, 256, 1f, targetSpeed, this::configure).width(200f).row();
        }

        /**
         * 配置滑块值，自动吸附到指定间隔的倍速
         */
        public void configure(float value) {
            // 自动吸附到指定间隔的倍速
            float snappedValue = Mathf.round(value / SNAP_INTERVAL) * SNAP_INTERVAL;
            // 只有当值实际改变时才更新
            if (Math.abs(snappedValue - targetSpeed) > 0.1f) {
                setTargetSpeed(snappedValue);
            }
        }

        @Override
        public Float config() {
            return targetSpeed;
        }

        @Override
        public void display(Table table) {
            // 创建信息面板容器
            Table infoPanel = new Table();
            infoPanel.margin(4);

            // 创建应力显示标签
            infoPanel.add("[accent]应力: [white]∞ us").width(160).left().row();
            // 创建转速显示标签
            infoPanel.add("[accent]转速: [white]" + (int)rotationSpeed + " rpm").width(160).left().row();

            // 将信息面板添加到主表格
            table.add(infoPanel).growX().row();
        }
    }

    /**
     * 简化版传动箱实现
     * 特性：
     * 1. 简单的应力传递机制
     * 2. 显示传输状态
     */
    public static class TransmissionBoxBuild extends MechanicalComponentBuild {
        // 传动箱没有特殊的视觉参数

        @Override
        public void update() {
            super.update();
            // 传动箱不需要更新旋转角度，因为父类已经处理了转速和应力的传递
        }

        @Override
        public void display(Table table) {
            super.display(table);
            addStatusDisplay(table, stress, rotationSpeed);
        }

        // 重写应力计算方法，使传动箱可以传输应力
        @Override
        protected PowerSourceInfo findPowerSource() {
            PowerSourceInfo info = super.findPowerSource();

            // 如果找到了有效的动力源，计算传动箱的应力传输
            if (info.hasValidSource) {
                // 传动箱可以传输应力，但效率较低
                float efficiency = Math.max(MechanicalComponentBuild.MIN_EFFICIENCY, 0.8f - (info.distance * MechanicalComponentBuild.EFFICIENCY_LOSS_PER_BLOCK));
                info.stress = info.stress * efficiency;
            }

            return info;
        }
    }

    /**
     * 添加状态显示到UI表格（带参数）
     * @param table UI表格
     * @param stress 应力值
     * @param rotationSpeed 转速值
     */
    private static void addStatusDisplay(Table table, float stress, float rotationSpeed) {
        // 创建信息面板容器
        Table infoPanel = new Table();
        infoPanel.margin(2).marginLeft(-330); // 减小边距并进一步左移

        // 创建应力显示标签
        String stressText = stress >= INFINITY_STRESS * 10 ? "∞ us" : (int)stress + " us";
        infoPanel.add("[accent]应力: [white]" + stressText).width(160).left().row();
        // 创建转速显示标签
        infoPanel.add("[accent]转速: [white]" + (int)rotationSpeed + " rpm").width(160).left().row();

        // 将信息面板添加到主表格
        table.add(infoPanel).growX().row();
    }
}