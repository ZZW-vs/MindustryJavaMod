package zzw.content.mechanics.torque.ui;

import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.scene.Element;
import arc.util.pooling.Pools;
import mindustry.ui.Fonts;

/**
 * 堆叠条形图 (PU132 unity.ui.StackedBarChart 移植)
 * <p>
 * 垂直堆叠多个条形, 每个条形显示名称、填充比例和颜色。
 * 用于坩埚对话框中显示各种熔融物的含量。
 */
public class StackedBarChart extends Element{
    private final Prov<BarStat[]> barStats;
    private final float prefHeight;

    public StackedBarChart(float prefHeight, Prov<BarStat[]> barStats){
        this.prefHeight = prefHeight;
        this.barStats = barStats;
    }

    @Override
    public void draw(){
        Font font = Fonts.outline;
        GlyphLayout lay = Pools.obtain(GlyphLayout.class, GlyphLayout::new);

        float totalWeight = 0f;
        BarStat[] data = barStats.get();

        for(BarStat i : data) totalWeight += i.weight;

        float yPos = y;

        for(BarStat i : data){
            float ah = height * i.weight / totalWeight;
            float aw = width * i.filled;

            String text = i.name;

            Draw.color(i.dark);
            Fill.rect(x + width * 0.5f, yPos + ah * 0.5f, width, ah);

            Draw.color(i.color);
            Fill.rect(x + aw * 0.5f, yPos + ah * 0.5f, aw, ah);

            lay.setText(font, text);
            font.setColor(Color.white);
            font.draw(text, x + width / 2f - lay.width / 2f, yPos + ah / 2f + lay.height / 2f + 1f);

            yPos += ah;
        }

        Pools.free(lay);
    }

    @Override
    public float getPrefHeight(){
        return prefHeight;
    }

    @Override
    public float getPrefWidth(){
        return 180f;
    }

    public static class BarStat{
        final Color color, dark;
        final String name;
        final float weight, filled;

        public BarStat(String name, float weight, float filled, Color color){
            this.name = name;
            this.weight = weight;
            this.filled = filled;
            this.color = color;

            dark = color.cpy().mul(0.5f);
        }
    }
}
