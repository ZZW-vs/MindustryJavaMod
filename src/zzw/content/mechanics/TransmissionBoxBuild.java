package zzw.content.mechanics;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
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

    private static final float UI_UPDATE_INTERVAL = 1/30f;   // UI更新间隔（30fps）

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
            float maxInputSpeed = 0f;
            float maxInputStress = 0f;
            boolean hasValidSource = false;

            // 检查所有邻居
            for (int i = 0; i < 4; i++) {
                Building other = nearby(i);
                if (other instanceof MechanicalComponentBuild component) {
                    // 如果邻居是动力源或有转速和应力
                    if (component.isSource || (component.rotationSpeed > SPEED_THRESHOLD && component.stress > 0)) {
                        maxInputSpeed = Math.max(maxInputSpeed, component.rotationSpeed);
                        maxInputStress = Math.max(maxInputStress, component.stress);
                        hasValidSource = true;
                    }
                }
            }

            // 更新自身值，应用传动效率
            if (hasValidSource) {
                float oldSpeed = rotationSpeed;
                float oldStress = stress;

                rotationSpeed = maxInputSpeed;
                stress = maxInputStress;

                // 如果值发生变化，通知邻居更新
                if (Math.abs(oldSpeed - rotationSpeed) > SPEED_THRESHOLD || 
                    Math.abs(oldStress - stress) > SPEED_THRESHOLD) {
                    notifyNeighborsNeedUpdate();
                }
            } else {
                // 如果没有有效动力源，清除旋转速度和应力
                if (rotationSpeed > 0 || stress > 0) {
                    rotationSpeed = 0f;
                    stress = 0f;
                    notifyNeighborsNeedUpdate();
                }
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
    public void draw() {
        super.draw();

        // 基本绘制，无动画效果
        if (rotationSpeed > SPEED_THRESHOLD) {
            Draw.color(Color.white);
            Draw.rect(Core.atlas.find("zzw-transmission-rotating"), x, y, rotation);
            Draw.reset();
        }
    }

    @Override
    public void display(Table table) {
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
            }, 0, UI_UPDATE_INTERVAL); // 使用优化的更新间隔
        });
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        notifyNeighborsNeedUpdate();
    }

    @Override
    public void onProximityAdded() {
        super.onProximityAdded();
        notifyNeighborsNeedUpdate();
    }
}
