package zzw.content.mechanics;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.ui.Bar;

/**
 * 齿轮方块实现
 * 特性：
 * 1. 整个方块旋转动画，转速越大转得越快
 * 2. 机械动力传输
 * 3. 可变齿轮比
 */
public class CogwheelBuild extends MechanicalComponentBuild {
    // 齿轮特有属性
    private float rotation = 0f;              // 当前旋转角度
    private TextureRegion gearRegion;         // 齿轮纹理
    private TextureRegion topRegion;          // 顶部装饰纹理

    // 常量定义
    private static final float SPEED_THRESHOLD = 0.01f;    // 转速阈值
    private static final float GEAR_RATIO = 1.0f;          // 齿轮比，决定转速变化
    private static final float STRESS_THRESHOLD = 0.5f;    // 高应力阈值
    private static final Color STRESS_COLOR = new Color(1f, 0.7f, 0.4f, 1f); // 高应力颜色

    @Override
    public void created() {
        super.created();
        // 加载齿轮纹理 - 使用方块区域而不是UI图标
        gearRegion = Core.atlas.find("zzw-cogwheel");
        // 如果找不到专用齿轮纹理，则使用方块区域
        if (gearRegion == null || !gearRegion.found()) {
            gearRegion = block.region;
        }
        // 尝试加载顶部装饰纹理
        topRegion = Core.atlas.find("zzw-cogwheel-top");
    }

    @Override
    public void update() {
        super.update();

        // 更新旋转角度 - 转速越大，旋转越快
        if (rotationSpeed > SPEED_THRESHOLD) {
            // 直接使用实际转速更新旋转角度
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
        // 整个方块旋转 - 转速越大，旋转越快
        if (rotationSpeed > SPEED_THRESHOLD) {
            // 绘制齿轮纹理（已经包含了基础方块）
            if (gearRegion != null) {
                Draw.rect(gearRegion, x, y, rotation);
            } else {
                // 如果没有齿轮纹理，使用方块区域
                Draw.rect(block.region, x, y, rotation);
            }

            // 绘制顶部装饰
            if (topRegion != null) {
                Draw.rect(topRegion, x, y, rotation);
            }

            // 绘制应力指示器 - 高应力时显示
            if (stress > STRESS_THRESHOLD) {
                Draw.color(STRESS_COLOR);
                Lines.stroke(2f * stress);
                // 根据转速调整线条长度
                float lineLen = 8f + rotationSpeed * 3f;

                // 绘制4个方向的应力指示线
                for (int i = 0; i < 4; i++) {
                    float angle = rotation + i * 90f;
                    Lines.lineAngle(x, y, angle, lineLen);
                }

                // 绘制中心点
                Fill.circle(x, y, 3f + stress * 2f);
                Draw.color();
            }
        } else {
            // 静止状态，不旋转
            // 绘制齿轮纹理（已经包含了基础方块）
            if (gearRegion != null) {
                Draw.rect(gearRegion, x, y);
            } else {
                // 如果没有齿轮纹理，使用方块区域
                Draw.rect(block.region, x, y);
            }

            // 绘制顶部装饰
            if (topRegion != null) {
                Draw.rect(topRegion, x, y);
            }

            // 静止状态下如果有应力，显示静态应力指示器
            if (stress > STRESS_THRESHOLD) {
                Draw.color(STRESS_COLOR);
                Lines.stroke(1.5f * stress);
                float lineLen = 8f;

                // 绘制4个方向的静态应力指示线
                for (int i = 0; i < 4; i++) {
                    float angle = i * 90f;
                    Lines.lineAngle(x, y, angle, lineLen);
                }

                // 绘制中心点
                Fill.circle(x, y, 3f + stress * 2f);
                Draw.color();
            }
        }

        // 绘制团队颜色（如果有）
        if (team != null) {
            Draw.color(team.color);
            Draw.rect(block.teamRegion, x, y, rotation);
            Draw.color();
        }
    }

    @Override
    public void display(Table table) {
        table.row();

        // 转速显示条
        table.add(new Bar(() -> "转速: " + String.format("%.2f", rotationSpeed), 
                         () -> Color.lightGray, 
                         () -> rotationSpeed / 10f)).width(200f).padBottom(4);

        table.row();

        // 应力显示条
        table.add(new Bar(() -> "应力: " + String.format("%.2f", stress), 
                         () -> STRESS_COLOR, 
                         () -> stress)).width(200f);
    }
}
