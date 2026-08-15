package zzw.content.graphics;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.graphics.Pal;

/** PU132 特效子集 (工厂移植所需) */
public class UnityFx{
    public static final Effect
    sparkBoi = new Effect(40f, e -> {
        Draw.color(e.color);
        for(int i = 0; i < 5; i++){
            float a = Mathf.randomSeed(e.id + i * 17L, 360f);
            float len = Mathf.randomSeed(e.id + i * 31L, 6f, 14f) * e.fout();
            float x = e.x + Angles.trnsx(a, len);
            float y = e.y + Angles.trnsy(a, len);
            Fill.circle(x, y, 1.5f * e.fout());
        }
    }),

    blockMelt = new Effect(80f, 300f, e -> {
        Draw.color(Color.orange, Color.red, e.fin());
        Fill.circle(e.x, e.y, e.fslope() * 12f);
        Draw.color();
    }),

    longSmoke = new Effect(150f, e -> {
        Draw.color(e.color, e.fout() * 0.7f);
        Fill.circle(e.x, e.y, e.fin() * 8f + 2f);
        Draw.color();
    }),

    expDump = new Effect(75f, 400f, e -> {
        if(!(e.data instanceof arc.math.geom.Position pos)) return;
        float fin = Mathf.curve(e.fin(), 0, Mathf.randomSeed(e.id, 0.25f, 1f));
        if(fin >= 1) return;
        float a = Angles.angle(e.x, e.y, pos.getX(), pos.getY()) - 90;
        float d = Mathf.dst(e.x, e.y, pos.getX(), pos.getY());
        float fslope = fin * (1f - fin) * 4f;
        float sfin = arc.math.Interp.pow2In.apply(fin);
        float spread = d / 4f;
        arc.util.Tmp.v1.trns(a, Mathf.randomSeed(e.id * 2L, -spread, spread) * fslope, d * sfin);
        arc.util.Tmp.v1.add(e.x, e.y);
        Draw.color(UnityPal.exp, Color.white, 0.1f + 0.1f * Mathf.sin(Time.time * 0.03f + e.id * 3f));
        Fill.circle(arc.util.Tmp.v1.x, arc.util.Tmp.v1.y, 1.5f);
    }),

    craft = new Effect(10, e -> {
        Draw.color(Pal.accent, Color.gray, e.fin());
        Lines.stroke(1);
        Angles.randLenVectors(e.id, 2, e.fin() * 4f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 1.5f * e.fout());
        });
        Draw.color();
    }),

    denseCraft = new Effect(10, e -> {
        Draw.color(UnityPal.dense, Color.gray, e.fin());
        Lines.stroke(1);
        Angles.randLenVectors(e.id, 2, e.fin() * 4f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 1.5f * e.fout());
        });
        Draw.color();
    });
}
