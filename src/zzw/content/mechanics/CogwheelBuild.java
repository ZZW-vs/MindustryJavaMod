package zzw.content.mechanics;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;

import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.Timer;
import mindustry.gen.Building;

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


    // 常量定义 - 统一使用父类中的常量
    private static final float GEAR_RATIO = 1.0f;          // 齿轮比，决定转速变化
    private static final float STRESS_THRESHOLD = 0.5f;    // 高应力阈值
    private static final Color STRESS_COLOR = new Color(1f, 0.7f, 0.4f, 1f); // 高应力颜色

    @Override
    public void created() {
        super.created();
        // 加载齿轮纹理 - 使用正确的路径
        gearRegion = Core.atlas.find("zzw-cogwheel");
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
            // 直接使用实际转速更新旋转角度
            rotation += rotationSpeed * Time.delta;
        }

        // 更新应力传递
        updateStressTransmission();
    }

    /**
     * 更新应力传递 - 优化版本
     */
    private void updateStressTransmission() {
        // 如果是动力源，直接返回
        if (isSource) return;
        
        // 使用父类的更新逻辑，应用齿轮比
        float oldSpeed = rotationSpeed;
        float oldStress = stress;
        
        // 检查邻居并获取动力
        float sourceSpeed = 0f;
        float sourceStress = 0f;
        boolean hasValidSource = false;
        boolean hasSource = false;
        
        for (int i = 0; i < 4; i++) {
            Building other = nearby(i);
            if (other instanceof MechanicalComponentBuild component) {
                if (component.isSource) {
                    // 应用齿轮比
                    sourceSpeed = component.rotationSpeed / GEAR_RATIO;
                    sourceStress = component.stress * GEAR_RATIO;
                    hasValidSource = true;
                    hasSource = true;
                } else if (component.rotationSpeed > SPEED_THRESHOLD && component.stress > 0 && !hasSource) {
                    sourceSpeed = Math.max(sourceSpeed, component.rotationSpeed / GEAR_RATIO);
                    sourceStress = Math.max(sourceStress, component.stress * GEAR_RATIO);
                    hasValidSource = true;
                }
            }
        }
        
        // 更新自身值
        if (hasValidSource) {
            rotationSpeed = sourceSpeed;
            stress = sourceStress;
        } else {
            rotationSpeed = 0f;
            stress = 0f;
        }
        
        // 检查值变化并通知邻居
        if (Math.abs(oldSpeed - rotationSpeed) > SPEED_THRESHOLD ||
            Math.abs(oldStress - stress) > SPEED_THRESHOLD) {
            notifyNeighborsNeedUpdate();
        }
        
        // 如果有动力源邻居，但自身没有更新，强制更新
        if (hasSource && Math.abs(oldSpeed - rotationSpeed) <= SPEED_THRESHOLD) {
            needsUpdate = true;
        }
    }

    /**
     * 通知邻居需要更新 - 使用父类方法
     */
    protected void notifyNeighborsNeedUpdate() {
        super.notifyNeighborsNeedUpdate();
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
        // 创建信息面板
        Table panel = MechanicalUI.createInfoPanel("齿轮状态");

        // 添加旋转指示器
        MechanicalUI.RotationIndicator rotationIndicator = new MechanicalUI.RotationIndicator(
            30f, MechanicalUI.SPEED_COLOR, "转速"
        );
        rotationIndicator.setSpeed(rotationSpeed);
        panel.add(rotationIndicator).pad(10).row();

        // 添加应力指示器
        MechanicalUI.CircularIndicator stressIndicator = new MechanicalUI.CircularIndicator(
            25f, MechanicalUI.STRESS_COLOR, "应力", "us"
        );
        stressIndicator.setValue(Math.min(stress / 10f, 1.0f));
        panel.add(stressIndicator).pad(10).row();

        // 移除了效率指示器，简化UI

        // 移除了齿轮比信息，简化UI

        // 移除了状态信息，简化UI
 

 




        // 添加到主表格
        table.add(panel);

        // 设置定时更新
        Time.runTask(0f, () -> Timer.schedule(() -> {
            if (this.isAdded()) {
                rotationIndicator.setSpeed(rotationSpeed);
                stressIndicator.setValue(Math.min(stress / 10f, 1.0f));

            }
        }, 0, MechanicalUI.UPDATE_INTERVAL));
    }
}
