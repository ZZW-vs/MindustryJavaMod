package zzw.content.mechanics;

import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.Timer;

/**
 * 应力源实现
 * 功能特性：
 * 1. 可配置的转速，支持正反转
 * 2. 平滑的转速变化，避免突变
 * 3. 作为动力源，向连接的机械组件提供动力
 */
public class StressSourceBuild extends MechanicalComponentBuild {
    private float targetSpeed = 0f;          // 目标转速
    // 使用静态常量ACCELERATION替代实例变量，节省内存
    private static final float ACCELERATION = 1.5f;
    private float drillSpeedMultiplier = 1.0f; // 钻速倍率，随时间增长
    private static final float DRILL_SPEED_GROWTH_RATE = 0.001f; // 钻速增长率

    // 使用父类中的SPEED_THRESHOLD常量

    @Override
    public void update() {
        super.update();

        // 标记为动力源
        isSource = true;

        // 设置应力源为无限应力
        stress = Float.MAX_VALUE;

        // 平滑调整到目标速度
        adjustSpeed();

        // 增长转速倍率（内部使用）
        if (rotationSpeed > 0) {
            drillSpeedMultiplier += DRILL_SPEED_GROWTH_RATE * Time.delta;
            drillSpeedMultiplier = Math.min(drillSpeedMultiplier, 10.0f); // 限制最大倍率为10
        }
    }

    /**
     * 平滑调整速度 - 优化版本
     */
    private void adjustSpeed() {
        float speedDifference = targetSpeed - rotationSpeed;
        if (Math.abs(speedDifference) > SPEED_THRESHOLD) {
            rotationSpeed += speedDifference * ACCELERATION * Time.delta / 60f;
            needsUpdate = true;
        } else if (rotationSpeed != targetSpeed) {
            rotationSpeed = targetSpeed;
            needsUpdate = true;
        }
    }

    /**
     * 设置目标速度
     */
    public void setTargetSpeed(float speed) {
        if (targetSpeed != speed) {
            targetSpeed = speed;
            needsUpdate = true;
        }
    }

    @Override
    public void buildConfiguration(Table table) {
        // 滑块范围调整为0到256，步长为1，长度适中
        table.slider(0, 256, 1f, targetSpeed, this::configure).width(200f).row();
    }

    /**
     * 配置滑块值，自动吸附到32的倍速
     */
    public void configure(float value) {
        // 自动吸附到32的倍速
        float snappedValue = Math.round(value / 32f) * 32f;
        setTargetSpeed(snappedValue);
    }

    @Override
    public Float config() {
        return targetSpeed;
    }

    @Override
    public void display(Table table) {
        // 创建可更新的应力显示标签
        var stressLabel = table.add("[accent]应力: [white]∞ us").width(160).get();
        table.row();

        // 创建可更新的转速显示标签
        var speedLabel = table.add("[accent]转速: [white]" + (int)rotationSpeed + " rpm").width(160).get();
        table.row();

        // 添加更新任务，每帧更新显示值
        Time.runTask(0f, () -> {
            Timer.schedule(() -> {
                if (this.isAdded() && stressLabel != null && speedLabel != null) {
                    stressLabel.setText("[accent]应力: [white]∞ us");
                    speedLabel.setText("[accent]转速: [white]" + (int)rotationSpeed + " rpm");
                }
            }, 0, 1/30f);
        });
    }
}
