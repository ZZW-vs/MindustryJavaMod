package zzw.content.mechanics;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;

public class MechanicalBuilds {
    private static final float STRESS_THRESHOLD = 0.5f;
    private static final int SNAP_INTERVAL = 32;
    private static final float INFINITY_STRESS = 10000f;

    // ===================== 齿轮 =====================
    public static class CogwheelBuild extends MechanicalComponentBuild {
        private float rotation = 0f;

        @Override
        public void update() {
            super.update();
            if (rotationSpeed > SPEED_THRESHOLD) {
                rotation += rotationSpeed * arc.util.Time.delta;
            }
        }

        @Override
        public void draw() {
            Draw.rect(block.region, x, y, rotation);

            if (stress > STRESS_THRESHOLD) {
                Draw.color(new Color(1f, 0.7f, 0.4f, 1f));
                Lines.stroke(2f * Math.min(stress, 5f));
                float lineLen = 6f + Math.min(rotationSpeed, 30f);
                for (int i = 0; i < 4; i++) {
                    Lines.lineAngle(x, y, rotation + i * 90f, lineLen);
                }
                Fill.circle(x, y, 3f);
                Draw.color();
            }

            if (team != null) {
                Draw.color(team.color);
                Draw.rect(block.teamRegion, x, y, rotation);
                Draw.color();
            }
        }

        @Override
        public void display(Table table) {
            super.display(table);
            addStatusDisplay(table, stress, rotationSpeed);
        }
    }

    // ===================== 应力源 =====================
    public static class StressSourceBuild extends MechanicalComponentBuild {
        private float targetSpeed = 0f;

        @Override
        public void update() {
            super.update();
            isSource = true;
            stress = INFINITY_STRESS;
            rotationSpeed = targetSpeed;
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
                setTargetSpeed(snapped);
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
            info.add("[accent]应力: [white]∞ us").width(160).left().row();
            info.add("[accent]转速: [white]" + (int) rotationSpeed + " rpm").width(160).left().row();
            table.add(info).growX().row();
        }
    }

    // ===================== 传动箱 =====================
    public static class TransmissionBoxBuild extends MechanicalComponentBuild {
        @Override
        public void display(Table table) {
            super.display(table);
            addStatusDisplay(table, stress, rotationSpeed);
        }
    }

    // ===================== 辅助：状态显示 =====================
    private static void addStatusDisplay(Table table, float stress, float rotationSpeed) {
        Table info = new Table();
        info.margin(2);

        String stressText = stress >= INFINITY_STRESS ? "∞ us" : (int) stress + " us";
        info.add("[accent]应力: [white]" + stressText).width(160).left().row();
        info.add("[accent]转速: [white]" + (int) rotationSpeed + " rpm").width(160).left().row();

        table.add(info).growX().row();
    }
}