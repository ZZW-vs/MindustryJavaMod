package zzw.content.mechanics;

import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.Timer;
import mindustry.gen.Building;

/**
 * 简化版传动箱实现
 * 专注于实时检测和显示传输应力和转速
 * 特性：
 * 1. 简单直观的应力传递机制
 * 2. 实时显示传输状态
 * 3. 可视化转速和应力流动
 */
public class TransmissionBoxBuild extends MechanicalComponentBuild {
    // 常量定义
    private static final float SPEED_THRESHOLD = 0.01f;    // 转速阈值

    // 视觉效果参数
    private float rotation = 0f;                           // 旋转角度

    @Override
    public void update() {
        super.update();

        // 更新旋转角度
        if (rotationSpeed > SPEED_THRESHOLD) {
            rotation += rotationSpeed * Time.delta;
        }

        // 更新应力传递
        updateStressTransmission();
    }

    /**
     * 更新应力传递
     */
    private void updateStressTransmission() {
        // 如果不是动力源，从邻居获取动力
        if (!isSource) {
            float sourceSpeed = 0f;
            float sourceStress = 0f;
            boolean hasValidSource = false;
            boolean hasSource = false; // 检测是否有动力源邻居

            // 检查所有邻居
            for (int i = 0; i < 4; i++) {
                Building other = nearby(i);
                if (other instanceof MechanicalComponentBuild component) {
                    // 如果邻居是动力源
                    if (component.isSource) {
                        // 直接使用动力源的值，而不是取最大值
                        sourceSpeed = component.rotationSpeed;
                        sourceStress = component.stress;
                        hasValidSource = true;
                        hasSource = true;
                    }
                    // 或者邻居有转速和应力
                    else if (component.rotationSpeed > SPEED_THRESHOLD && component.stress > 0) {
                        // 如果没有动力源，使用非动力源邻居的值
                        if (!hasSource) {
                            sourceSpeed = Math.max(sourceSpeed, component.rotationSpeed);
                            sourceStress = Math.max(sourceStress, component.stress);
                            hasValidSource = true;
                        }
                    }
                }
            }

            // 更新自身值，应用传动效率
            float oldSpeed = rotationSpeed;
            float oldStress = stress;

            if (hasValidSource) {
                rotationSpeed = sourceSpeed;
                stress = sourceStress;
            } else {
                // 如果没有有效动力源，清除旋转速度和应力
                rotationSpeed = 0f;
                stress = 0f;
            }

            // 如果值发生变化，通知邻居更新
            if (Math.abs(oldSpeed - rotationSpeed) > SPEED_THRESHOLD ||
                Math.abs(oldStress - stress) > SPEED_THRESHOLD) {
                notifyNeighborsNeedUpdate();
            }

            // 如果有动力源邻居，但自身没有更新，强制更新
            if (hasSource && Math.abs(oldSpeed - rotationSpeed) <= SPEED_THRESHOLD) {
                needsUpdate = true;
            }
        }
    }

    /**
     * 通知邻居需要更新
     */
    private void notifyNeighborsNeedUpdate() {
        for (int i = 0; i < 4; i++) {
            Building other = nearby(i);
            if (other instanceof MechanicalComponentBuild component) {
                component.needsUpdate = true;
            }
        }
    }


    @Override
    public void display(Table table) {
        super.display(table);

        // 创建可更新的应力显示标签
        var stressLabel = table.add("[accent]应力: [white]" + (int)stress + " us").width(160).get();
        table.row();

        // 创建可更新的转速显示标签
        var speedLabel = table.add("[accent]转速: [white]" + (int)rotationSpeed + " rpm").width(160).get();
        table.row();

        // 添加更新任务，每帧更新显示值
        Time.runTask(0f, () -> {
            // 使用定时器持续更新UI显示
            Timer.schedule(() -> {
                if (this.isAdded() && stressLabel != null && speedLabel != null) {
                    stressLabel.setText("[accent]应力: [white]" + (int)stress + " us");
                    speedLabel.setText("[accent]转速: [white]" + (int)rotationSpeed + " rpm");
                }
            }, 0, 1/30f); // 使用30fps的更新间隔
        });
    }
}