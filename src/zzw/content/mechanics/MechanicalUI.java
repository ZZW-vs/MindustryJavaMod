package zzw.content.mechanics;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.scene.ui.Label;
import arc.util.pooling.Pools;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.scene.event.HandCursorListener;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WidgetGroup;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.ui.Styles;

/**
 * 机械动力系统UI组件基类
 * 提供统一的UI样式和交互体验
 */
public class MechanicalUI {
    // 颜色方案
    public static final Color 
        BG_COLOR = new Color(0.1f, 0.1f, 0.1f, 0.8f),
        BORDER_COLOR = new Color(0.3f, 0.3f, 0.3f, 0.9f),
        NORMAL_COLOR = Color.lightGray,
        STRESS_COLOR = new Color(1f, 0.7f, 0.4f, 1f),
        SPEED_COLOR = new Color(0.4f, 0.7f, 1f, 1f),
        EFFICIENCY_COLOR = new Color(0.4f, 1f, 0.7f, 1f);

    // 常量
    public static final float UPDATE_INTERVAL = 1/30f;
    private static final float ANIMATION_SPEED = 2f;

    // 创建圆形指示器
    public static class CircularIndicator extends WidgetGroup {
        private float value = 0f;
        private float targetValue = 0f;
        private final float radius;
        private final Color color;
        private final String label;
        private final String unit;

        public CircularIndicator(float radius, Color color, String label, String unit) {
            this.radius = radius;
            this.color = color;
            this.label = label;
            this.unit = unit;
            setSize(radius * 2.5f, radius * 2.5f);
        }

        @Override
        public void draw() {
            // 平滑过渡
            if (Math.abs(value - targetValue) > 0.01f) {
                value += (targetValue - value) * ANIMATION_SPEED * Time.delta;
            }

            // 绘制背景圆
            Draw.color(BG_COLOR);
            Fill.circle(x + width/2, y + height/2, radius);

            // 绘制边框
            Draw.color(BORDER_COLOR);
            Lines.stroke(2f);
            Lines.circle(x + width/2, y + height/2, radius);

            // 绘制进度弧
            if (value > 0) {
                Draw.color(color);
                Lines.stroke(4f);
                Lines.arc(x + width/2, y + height/2, radius - 5, 0, value * 360);
            }

            // 绘制文本
            Draw.color(Color.white);
            String text = String.format("%s: %.1f%s", label, value * 100, unit);
            Font font = Core.scene.getStyle(Label.LabelStyle.class).font;
            GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
            layout.setText(font, text);
            float textWidth = layout.width;
            float textHeight = layout.height;
            font.draw(text, x + width/2 - textWidth/2, y + height/2 + textHeight/2);
            Pools.free(layout);
        }

        public void setValue(float value) {
            this.targetValue = value;
            if (this.value == 0) this.value = value;
        }
    }

    // 创建旋转指示器
    public static class RotationIndicator extends WidgetGroup {
        private float rotation = 0f;
        private float speed = 0f;
        private float targetSpeed = 0f;
        private final float radius;
        private final Color color;
        private final String label;

        public RotationIndicator(float radius, Color color, String label) {
            this.radius = radius;
            this.color = color;
            this.label = label;
            setSize(radius * 2.5f, radius * 2.5f);
        }

        @Override
        public void draw() {
            // 平滑过渡速度
            if (Math.abs(speed - targetSpeed) > 0.01f) {
                speed += (targetSpeed - speed) * ANIMATION_SPEED * Time.delta;
            }

            // 更新旋转角度
            if (speed > 0) {
                rotation += speed * Time.delta;
            }

            // 绘制背景圆
            Draw.color(BG_COLOR);
            Fill.circle(x + width/2, y + height/2, radius);

            // 绘制边框
            Draw.color(BORDER_COLOR);
            Lines.stroke(2f);
            Lines.circle(x + width/2, y + height/2, radius);

            // 绘制旋转指示器
            if (speed > 0) {
                Draw.color(color);
                Lines.stroke(3f);

                // 绘制旋转线
                for (int i = 0; i < 4; i++) {
                    float angle = rotation + i * 90;
                    Tmp.v1.set(0, 1).rotate(angle).scl(radius - 5);
                    Lines.line(
                        x + width/2 + Tmp.v1.x * 0.3f, 
                        y + height/2 + Tmp.v1.y * 0.3f,
                        x + width/2 + Tmp.v1.x,
                        y + height/2 + Tmp.v1.y
                    );
                }

                // 绘制中心点
                Fill.circle(x + width/2, y + height/2, 4f);
            }

            // 绘制文本
            Draw.color(Color.white);
            String text = String.format("%s: %.1f rpm", label, speed);
            Font font = Core.scene.getStyle(Label.LabelStyle.class).font;
            GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
            layout.setText(font, text);
            float textWidth = layout.width;
            float textHeight = layout.height;
            font.draw(text, x + width/2 - textWidth/2, y + height/2 + radius + 15 + textHeight/2);
            Pools.free(layout);
        }

        public void setSpeed(float speed) {
            this.targetSpeed = speed;
            if (this.speed == 0) this.speed = speed;
        }
    }

    // 创建信息面板
    public static Table createInfoPanel(String title) {
        Table panel = new Table();

        // 背景和边框
        panel.background(Styles.black6);
        panel.margin(10);
        panel.defaults().pad(5);

        // 标题
        Label titleLabel = new Label(title);
        titleLabel.setFontScale(1.2f);
        titleLabel.setColor(Color.white);
        panel.add(titleLabel).colspan(2).center().row();

        // 分隔线
        Image separator = new Image();
        separator.setDrawable(new TextureRegionDrawable(Core.atlas.find("white")));
        separator.setColor(BORDER_COLOR);
        separator.setHeight(2f);
        panel.add(separator).growX().colspan(2).pad(5).row();

        return panel;
    }

    // 创建带工具提示的标签
    public static Label createTooltipLabel(String text, String tooltip) {
        Label label = new Label(text);
        label.addListener(new HandCursorListener());

        label.addListener(new arc.scene.ui.Tooltip(t -> {
            t.background(Styles.black6);
            t.labelWrap(tooltip).growX().pad(5);
        }));

        return label;
    }
}
