package zzw.content.blocks.modular;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.Image;

/**
 * 带描边的贴图 (PU132 younggamExperimental 蓝图 UI 所需, 参考源码未给出实现, 此处最小实现)。
 *
 * <p>在零件图标外画一圈深色边框, 供蓝图编辑面板的零件按钮使用。</p>
 */
public class BorderImage extends Image{
    /** 描边宽度 (世界/像素单位) */
    private final float border;

    public BorderImage(TextureRegion region, float border){
        super(region);
        this.border = border;
    }

    @Override
    public void draw(){
        validate();
        Draw.color(Color.black);
        Fill.rect(x + width * 0.5f, y + height * 0.5f, width + border * 2f, height + border * 2f);
        Draw.color();
        super.draw();
    }
}