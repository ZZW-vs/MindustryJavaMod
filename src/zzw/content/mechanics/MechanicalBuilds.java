package zzw.content.mechanics;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;

/**
 * 机械组件实体类
 * 参考 Create 模组: 子类只关心行为, 网络逻辑由基类处理
 *
 * 关键 API 变更:
 * - speed → 基类字段 (理论转速)
 * - getSpeed() → 实际转速 (过载时返回 0)
 * - baseImpact → 此方块在 1 RPM 下的应力影响
 * - baseCapacity → 此源在 1 RPM 下的容量
 */
public class MechanicalBuilds {
    // private static final float STRESS_THRESHOLD = 0.5f;
    private static final int SNAP_INTERVAL = 32;
    // private static final Color STRESS_COLOR = new Color(1f, 0.7f, 0.4f, 1f);
    // private static final Color OVERLOAD_COLOR = new Color(1f, 0.3f, 0.2f, 1f);

    // ===================== 齿轮 =====================
    public static class CogwheelBuild extends MechanicalComponentBuild {
        private float rotation = 0f;

        public CogwheelBuild() {
            baseImpact = 1f;  // 齿轮的应力影响: 1 × |speed|
        }

        @Override
        public void update() {
            super.update();
            // 用 getSpeed() 响应过载: 过载时齿轮停止旋转
            float actualSpeed = getSpeed();
            if (Math.abs(actualSpeed) > SPEED_THRESHOLD) {
                rotation += actualSpeed * arc.util.Time.delta;
            }
        }

        @Override
        public void draw() {
            Draw.rect(block.region, x, y, rotation);

        //     if (stress > STRESS_THRESHOLD) {
        //         // 过载时显示红色, 正常时显示橙色
        //         Draw.color(overStressed ? OVERLOAD_COLOR : STRESS_COLOR);
        //         Lines.stroke(2f);
        //         float lineLen = 6f + Math.min(Math.abs(getSpeed()), 30f);
        //         for (int i = 0; i < 4; i++) {
        //             Lines.lineAngle(x, y, rotation + i * 90f, lineLen);
        //         }
        //         Fill.circle(x, y, 3f);
        //         Draw.color();
        //     }

        //     if (block.teamRegion.found()) {
        //         Draw.color(team.color);
        //         Draw.rect(block.teamRegion, x, y, rotation);
        //         Draw.color();
        //     }
        }

        @Override
        public void display(Table table) {
            super.display(table);
            addStatusDisplay(table, this);
        }
    }

    // ===================== 应力源 =====================
    public static class StressSourceBuild extends MechanicalComponentBuild {
        private float targetSpeed = 0f;

        public StressSourceBuild() {
            baseCapacity = 100f;  // 源的容量: 100 × |speed|
            baseImpact = 0f;      // 源本身不产生应力负载
        }

        @Override
        public void update() {
            // ★ 先设置自己的值, 再调用 super.update() 触发传播
            isSource = true;
            speed = targetSpeed;
            super.update();
        }

        public void setTargetSpeed(float speed) {
            targetSpeed = speed;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.slider(0, 256, 1f, targetSpeed, this::configure).width(200f).row();
        }

        public void configure(float value) {
            float snapped = Mathf.round(value / SNAP_INTERVAL) * SNAP_INTERVAL;
            if (Math.abs(snapped - targetSpeed) > 0.1f) {
                targetSpeed = snapped;
                speed = snapped;
                // ★ 玩家改了滑块, 请求全网络刷新
                requestNetworkRefresh();
            }
        }

        @Override
        public Float config() {
            return targetSpeed;
        }

        @Override
        public void display(Table table) {
            Table info = new Table();
            info.margin(4);
            info.add("[accent]容量: [white]" + (int) capacity + " su").width(160).left().row();
            info.add("[accent]应力: [white]" + (int) stress + " su").width(160).left().row();
            info.add("[accent]转速: [white]" + (int) getSpeed() + " rpm").width(160).left().row();
            if (overStressed) {
                info.add("[red]⚠ 过载! [white]请减少负载或增加容量").width(200).left().row();
            }
            table.add(info).growX().row();
        }
    }

    // ===================== 传动箱 =====================
    public static class TransmissionBoxBuild extends MechanicalComponentBuild {
        public TransmissionBoxBuild() {
            baseImpact = 0.5f;  // 传动箱的应力影响比齿轮小
        }

        @Override
        public void display(Table table) {
            super.display(table);
            addStatusDisplay(table, this);
        }
    }

    // ===================== 辅助: 状态显示 =====================
    private static void addStatusDisplay(Table table, MechanicalComponentBuild b) {
        Table info = new Table();
        info.margin(2);

        info.add("[accent]容量: [white]" + (int) b.capacity + " su").width(160).left().row();
        info.add("[accent]应力: [white]" + (int) b.stress + " su").width(160).left().row();
        info.add("[accent]转速: [white]" + (int) b.getSpeed() + " rpm").width(160).left().row();
        if (b.overStressed) {
            info.add("[red]⚠ 过载").width(160).left().row();
        }

        table.add(info).growX().row();
    }
}
