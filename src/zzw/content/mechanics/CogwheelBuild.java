package zzw.content.mechanics;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.gen.Building;

/**
 * 齿轮方块实现
 * 特性：
 * 1. 流畅的旋转动画
 * 2. 动态应力表现
 * 3. 可变齿轮比
 * 4. 视觉反馈效果
 */
public class CogwheelBuild extends MechanicalComponentBuild {
    // 齿轮特有属性
    private float rotation = 0f;              // 当前旋转角度
    private float visualRotation = 0f;        // 视觉旋转角度（用于平滑动画）
    private float lastRotationSpeed = 0f;     // 上一帧的旋转速度
    private float stressPulse = 0f;           // 应力脉冲效果
    private float speedTransition = 0f;       // 速度过渡值
    private TextureRegion gearRegion;         // 齿轮纹理
    private TextureRegion glowRegion;         // 发光效果纹理

    // 常量定义
    private static final float SPEED_THRESHOLD = 0.01f;    // 转速阈值
    private static final float ROTATION_SPEED_FACTOR = 0.3f; // 旋转速度系数（调整动画速度）
    private static final float GEAR_RATIO = 1.0f;          // 齿轮比，决定转速变化
    private static final float SMOOTH_FACTOR = 0.15f;      // 平滑系数
    private static final float MAX_GLOW_SIZE = 1.8f;       // 最大发光大小
    private static final float MIN_GLOW_SIZE = 1.2f;       // 最小发光大小
    private static final float STRESS_THRESHOLD = 0.5f;     // 高应力阈值
    private static final Color GEAR_COLOR = new Color(0.9f, 0.9f, 1f, 1f); // 齿轮基础颜色
    private static final Color STRESS_COLOR = new Color(1f, 0.7f, 0.4f, 1f); // 高应力颜色

    @Override
    public void created() {
        super.created();
        // 加载齿轮纹理
        gearRegion = block.uiIcon;

        // 尝试加载发光效果纹理
        glowRegion = Core.atlas.find("zzw-cogwheel-glow");
    }

    @Override
    public void update() {
        super.update();

        // 更新旋转角度
        if (rotationSpeed > SPEED_THRESHOLD) {
            rotation += rotationSpeed * Time.delta * ROTATION_SPEED_FACTOR;
        }

        // 平滑视觉旋转
        visualRotation = Mathf.lerpDelta(visualRotation, rotation, SMOOTH_FACTOR);

        // 更新速度过渡效果
        speedTransition = Mathf.lerpDelta(speedTransition, rotationSpeed, SMOOTH_FACTOR * 2);

        // 更新应力脉冲效果
        if (stress > STRESS_THRESHOLD) {
            stressPulse = Mathf.sin(Time.time * 2f + id * 2f) * 0.1f + 1f;
        } else {
            stressPulse = Mathf.lerpDelta(stressPulse, 1f, SMOOTH_FACTOR * 2);
        }

        // 检测速度变化
        if (Math.abs(lastRotationSpeed - rotationSpeed) > SPEED_THRESHOLD) {
            lastRotationSpeed = rotationSpeed;
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
                        // 应用齿轮比
                        float inputSpeed = component.rotationSpeed / GEAR_RATIO;
                        float inputStress = component.stress * GEAR_RATIO;

                        // 直接使用动力源的值，而不是取最大值
                        sourceSpeed = inputSpeed;
                        sourceStress = inputStress;
                        hasValidSource = true;
                        hasSource = true;
                    }
                    // 或者邻居有转速和应力
                    else if (component.rotationSpeed > SPEED_THRESHOLD && component.stress > 0) {
                        // 应用齿轮比
                        float inputSpeed = component.rotationSpeed / GEAR_RATIO;
                        float inputStress = component.stress * GEAR_RATIO;

                        // 如果没有动力源，使用非动力源邻居的值
                        if (!hasSource) {
                            sourceSpeed = Math.max(sourceSpeed, inputSpeed);
                            sourceStress = Math.max(sourceStress, inputStress);
                            hasValidSource = true;
                        }
                    }
                }
            }

            // 更新自身值
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
    public void draw() {
        super.draw();

        // 绘制旋转的齿轮
        if (gearRegion != null) {
            // 基础透明度，玩家放置的齿轮设为半透明
            float baseAlpha = isPlayerPlaced() ? 0.7f : 1.0f;

            // 根据应力调整颜色
            Color gearColor = GEAR_COLOR.cpy();
            if (stress > STRESS_THRESHOLD) {
                gearColor.lerp(STRESS_COLOR, Mathf.clamp(stress - STRESS_THRESHOLD));
            }

            // 如果齿轮在旋转
            if (speedTransition > SPEED_THRESHOLD) {
                // 绘制发光效果
                if (glowRegion != null && stress > 0.3f) {
                    Draw.color(gearColor, baseAlpha * 0.5f * stress);
                    float glowSize = Mathf.lerp(MIN_GLOW_SIZE, MAX_GLOW_SIZE, stress) * stressPulse;
                    float blockSize = block.size * 8f;
                    Draw.rect(glowRegion, x, y, glowSize * blockSize, glowSize * blockSize, visualRotation);
                }

                // 绘制主齿轮
                Draw.color(gearColor, baseAlpha);
                float gearSize = Mathf.lerp(32f, 36f, speedTransition / 5f) * stressPulse;
                Draw.rect(gearRegion, x, y, gearSize, gearSize, visualRotation);

                // 绘制应力指示线
                if (stress > STRESS_THRESHOLD) {
                    Draw.color(STRESS_COLOR, baseAlpha * 0.7f);
                    Lines.stroke(1.5f * stress);
                    for (int i = 0; i < 4; i++) {
                        float angle = visualRotation + i * 90f;
                        float lineLen = 10f + stress * 5f;
                        float blockSize = block.size * 8f;
                        Lines.lineAngle(
                            x + Angles.trnsx(angle, blockSize/2),
                            y + Angles.trnsy(angle, blockSize/2),
                            angle, lineLen
                        );
                    }
                }
            }
            // 静止状态
            else {
                // 静止时使用非常透明的静态贴图
                Draw.color(gearColor, baseAlpha * 0.2f);
                Draw.rect(gearRegion, x, y, 32f, 32f);
            }

            // 绘制中心点
            Draw.color(Color.white, baseAlpha);
            Fill.circle(x, y, 3f);

            // 重置颜色和透明度
            Draw.color();
        }
    }

    /**
     * 判断是否为玩家放置的齿轮
     */
    private boolean isPlayerPlaced() {
        // 默认返回true，将所有齿轮视为玩家放置的
        return true;
    }

    @Override
    public void display(Table table) {
        // 不调用 super.display(table) 以避免重复显示
        // UI显示已移除
    }
}