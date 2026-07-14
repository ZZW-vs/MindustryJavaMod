package zzw.content.units.effects;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import mindustry.entities.Effect;

/**
 * PU132 HitFx 移植版
 * - endHitRedBig: 红色大爆炸命中特效 (15tick, 7条线段)
 * 参考: PU132 main/src/unity/content/effects/HitFx.java L319-326
 */
public class HitEffect {

    // PU132 颜色常量
    private static final Color SCAR_COLOR = Color.valueOf("f53036");
    private static final Color END_COLOR = Color.valueOf("ff786e");

    /**
     * 红色大爆炸命中特效 (15tick)
     * PU132 HitFx.endHitRedBig
     * 7条线段从中心向外扩散, endColor → scarColor 渐变
     */
    public static final Effect endHitRedBig = new Effect(15f, e -> {
        // PU132: color(UnityPal.endColor, UnityPal.scarColor, e.fin())
        Color c = TmpColor.c1.set(END_COLOR).lerp(SCAR_COLOR, e.fin());
        Draw.color(c);
        Angles.randLenVectors(e.id, 7, e.fin(Interp.pow3Out) * 45f, e.rotation, 45f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.stroke(e.fout() * 2f);
            Lines.lineAngle(e.x + x, e.y + y, ang, e.fout(Interp.pow3In) * 24f);
        });
        Draw.color();
    });

    /** 临时颜色对象 (避免每次 new) */
    private static class TmpColor {
        static final Color c1 = new Color();
    }
}
