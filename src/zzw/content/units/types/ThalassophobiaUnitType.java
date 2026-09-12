package zzw.content.units.types;

import arc.Core;
import arc.graphics.g2d.Draw;
import mindustry.gen.Unit;
import zzw.content.type.UnityUnitType;

/**
 * 深海恐惧单位类型 (移植自 PU132 UnityUnitTypes thalassophobia 匿名类覆写)
 *
 * <p>thalassophobia 体型巨大 (hitSize 242.5), 原版不用默认圆形阴影,
 * 而是加载专用的 thalassophobia-soft-shadow 贴图绘制半透明软阴影
 * (尺寸 = 身体贴图 × 1.6 倍, 完全不透明黑色底)。</p>
 */
public class ThalassophobiaUnitType extends UnityUnitType{

    public ThalassophobiaUnitType(String name){
        super(name);
    }

    @Override
    public void load(){
        super.load();
        // 专用软阴影贴图 (assets/sprites/units/end/thalassophobia-soft-shadow.png)
        softShadowRegion = Core.atlas.find(name + "-soft-shadow");
    }

    /**
     * 软阴影绘制 (PU132 drawSoftShadow 覆写)。
     *
     * <p>与原版的差异: 阴影 = 身体贴图尺寸 × 1.6 倍, 不透明度 1 (纯黑),
     * 跟随单位旋转 (rotation - 90)。</p>
     */
    @Override
    public void drawSoftShadow(Unit unit){
        Draw.color(0, 0, 0, 1f);
        float rad = 1.6f;
        float size = Math.max(region.width, region.height) * Draw.scl;
        Draw.rect(softShadowRegion, unit, size * rad * Draw.xscl, size * rad * Draw.yscl, unit.rotation - 90f);
        Draw.color();
    }
}
