package zzw.content.mechanics.torque.ui;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.scene.Element;
import arc.scene.style.Drawable;
import arc.util.Align;
import arc.util.pooling.Pools;
import mindustry.graphics.Pal;
import mindustry.gen.Tex;
import mindustry.type.Item;
import mindustry.ui.Fonts;
import zzw.content.mechanics.torque.Utils;
import zzw.content.mechanics.torque.meta.CrucibleRecipes;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleItem;

/**
 * 单一物品熔点显示元素 (PU_V8 unity.ui.CrucibleMeltStatElement 移植)
 * <p>
 * 显示物品图标 + "[熔点] xx°C" 文本 + 表示该温度的颜色条。
 */
public class CrucibleMeltStatElement extends Element{
    Item item;

    public CrucibleMeltStatElement(Item item){
        this.item = item;
    }

    @Override
    public void draw(){
        Font font = Fonts.def;
        GlyphLayout lay = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        CrucibleItem melt = CrucibleRecipes.items.get(item);
        Drawable top = Tex.barTop;

        Draw.color(item.color);
        top.draw(x, y, Math.max(width * 0.1f, 32f), height);

        Draw.color(Pal.darkerGray);
        Draw.rect(item.fullIcon, x + 16f, y + 14f, 24f, 24f);
        Draw.color();
        Draw.rect(item.fullIcon, x + 16f, y + 18f, 24f, 24f);

        String text = Core.bundle.format("stat.unity-itemmeltpoint", melt == null ? 0f : melt.meltingpoint - CrucibleRecipes.celsiusZero);
        text(text, x + 50f, y + 16f, lay, font, Align.left);
        float xpos = x + lay.width + 50f + 8f;

        Color col = Utils.tempColor(melt == null ? 0f : melt.meltingpoint);
        Draw.color(col);
        top.draw(xpos, y, 32f, height);
        Draw.color();

        Pools.free(lay);
    }

    public void text(String text, float x, float y, GlyphLayout lay, Font font, int align){
        lay.setText(font, text);
        font.setColor(Color.white);
        if(align == Align.center){
            font.draw(text, x - lay.width / 2f, y + lay.height / 2f);
        }else{
            font.draw(text, x, y + lay.height / 2f);
        }
    }

    @Override
    public float getMinHeight(){
        return 32f;
    }
}
