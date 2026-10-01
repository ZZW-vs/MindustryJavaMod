package zzw.content.mechanics.torque.ui;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.ScissorStack;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.scene.Element;
import arc.scene.style.Drawable;
import arc.struct.OrderedMap;
import arc.util.Strings;
import arc.util.pooling.Pools;
import mindustry.graphics.Pal;
import mindustry.gen.Tex;
import mindustry.ui.Fonts;
import zzw.content.mechanics.torque.graph.CrucibleGraph.CrucibleFluid;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleIngredient;

/**
 * 坩埚内容物显示元素 (PU_V8 unity.ui.CrucibleDisplayElement 移植)
 * <p>
 * 以网格形式绘制每种原料的图标 / 总量 / 熔融比例条: 底色为该原料颜色,
 * 上方按熔融比例裁剪显示高亮条。
 */
public class CrucibleDisplayElement extends Element{
    private static final Rect scissor = new Rect();

    public OrderedMap<CrucibleIngredient, CrucibleFluid> fluids;
    int columns;

    public CrucibleDisplayElement(OrderedMap<CrucibleIngredient, CrucibleFluid> fluids, int columns){
        this.fluids = fluids;
        this.columns = columns;
    }

    @Override
    public void draw(){
        Font font = Fonts.outline;
        GlyphLayout lay = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        Drawable bar = Tex.bar;
        Drawable top = Tex.barTop;
        int i = 0;
        float cwidth = width / columns;

        for(var fluid : fluids){
            if(fluid.value.total() < 0.0001f) continue;

            float xpos = x + (i % columns) * cwidth;
            float ypos = y + (i / columns) * 32f;

            Draw.color(fluid.key.color, 0.2f);
            bar.draw(xpos, ypos, cwidth, 32f);
            Draw.color(fluid.key.color);
            if(ScissorStack.push(scissor.set(xpos, ypos, cwidth * fluid.value.meltedRatio(), 32f))){
                top.draw(xpos, ypos, cwidth, 32f);
                ScissorStack.pop();
            }

            Draw.color(Pal.darkerGray);
            Draw.rect(fluid.key.icon(), xpos + 16f, ypos + 14f, 24f, 24f);
            Draw.color();
            Draw.rect(fluid.key.icon(), xpos + 16f, ypos + 18f, 24f, 24f);

            String text = Strings.fixed(fluid.value.total(), 1);
            lay.setText(font, text);
            font.setColor(Color.white);
            font.draw(text, xpos + cwidth * 0.5f - lay.width / 2f + 8f, ypos + lay.height / 2f + 16f);
            i++;
        }
        Draw.color();
        Pools.free(lay);
    }

    @Override
    public float getMinHeight(){
        float notempty = 0f;
        for(var fluid : fluids){
            notempty += fluid.value.total() > 0 ? 1 : 0;
        }
        return Mathf.ceil(notempty / columns) * 32f;
    }
}
