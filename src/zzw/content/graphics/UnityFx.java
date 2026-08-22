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
    }),

    // ===== PU132 工厂特效补充 (UnityFx 原版移植) =====

    /** 迪尔坩埚合成特效 (PU132 diriumCraft) */
    diriumCraft = new Effect(10, e -> {
        Draw.color(Color.white, UnityPal.dirium, e.fin());
        Lines.stroke(1);
        Lines.spikes(e.x, e.y, e.fin() * 4, 1.5f, 6);
        Draw.color();
    }),

    /** 固化器合成特效 (PU132 rockFx) */
    rockFx = new Effect(10f, e -> {
        Draw.color(Color.orange, Color.gray, e.fin());
        Lines.stroke(1f);
        Lines.spikes(e.x, e.y, e.fin() * 4f, 1.5f, 6);
    }),

    /** 通用合成特效 (PU132 craftFx, 煤提取器等) */
    craftFx = new Effect(10f, e -> {
        Draw.color(Pal.accent, Color.gray, e.fin());
        Lines.stroke(1f);
        Lines.spikes(e.x, e.y, e.fin() * 4f, 1.5f, 6);
    }),

    /** 暗合金锻造厂工作特效 (PU132 craftingEffect): 螺旋飞入的橙色方块 */
    craftingEffect = new Effect(67f, 35f, e -> {
        float value = Mathf.randomSeed(e.id);

        arc.util.Tmp.v1.trns(value * 360f + ((value + 4f) * e.fin() * 80f), (Mathf.randomSeed(e.id * 126L) + 1f) * 34f * (1f - e.finpow()));

        Draw.color(UnityPal.laserOrange);
        Fill.square(e.x + arc.util.Tmp.v1.x, e.y + arc.util.Tmp.v1.y, e.fslope() * 3f, 45f);
        Draw.color();
    }),

    /** 火花合金锻造厂合成特效 (PU132 imberCircleSparkCraftingEffect): 扩散电涌圈 */
    imberCircleSparkCraftingEffect = new Effect(30f, e -> {
        Draw.color(Pal.surge);
        Lines.stroke(e.fslope());
        Lines.circle(e.x, e.y, e.fin() * 20f);
    }),

    /** 火花合金锻造厂工作特效 (PU132 imberSparkCraftingEffect): 向心火花 */
    imberSparkCraftingEffect = new Effect(70f, e -> {
        Draw.color(UnityPal.imberColor, Color.valueOf("ffc266"), e.finpow());
        Draw.alpha(e.finpow());
        Angles.randLenVectors(e.id, 3, (1f - e.finpow()) * 24f, e.rotation, 360f, (x, y) -> {
            mindustry.graphics.Drawf.tri(e.x + x, e.y + y, e.fout() * 8f, e.fout() * 10f, e.rotation);
            mindustry.graphics.Drawf.tri(e.x + x, e.y + y, e.fout() * 4f, e.fout() * 6f, e.rotation);
        });
        Draw.color();
    }),

    /** 终焉锻造厂吸收脉冲特效 (PU132 forgeAbsorbPulseEffect) */
    forgeAbsorbPulseEffect = new Effect(124f, e -> {
        float rad = 110f * e.fout(arc.math.Interp.pow5In);
        int sides = Lines.circleVertices(rad);

        Draw.z(mindustry.graphics.Layer.effect + 1f);
        Draw.blend(arc.graphics.Blending.additive);
        arc.util.Tmp.c1.set(UnityPal.endColor);
        arc.util.Tmp.c1.a = e.fin(arc.math.Interp.pow5Out);
        Fill.light(e.x, e.y, sides, rad, Color.clear, arc.util.Tmp.c1);

        arc.util.Tmp.c1.a = e.fin(arc.math.Interp.pow10Out) * e.fout(arc.math.Interp.pow10Out);
        Fill.light(e.x, e.y, 27, 40f, arc.util.Tmp.c1, Color.clear);
        Draw.blend();
    }),

    /** 终焉锻造厂吸收线特效 (PU132 forgeAbsorbEffect) */
    forgeAbsorbEffect = new Effect(124f, e -> {
        float angle = e.rotation;
        float slope = (0.5f - Math.abs(e.finpow() - 0.5f)) * 2f;
        arc.util.Tmp.v1.trns(angle, (1 - e.finpow()) * 110f);
        Draw.color(UnityPal.endColor);
        Lines.stroke(1.5f);
        Lines.lineAngleCenter(e.x + arc.util.Tmp.v1.x, e.y + arc.util.Tmp.v1.y, angle, slope * 8f);
    }),

    /** 终焉锻造厂火焰特效 (PU132 forgeFlameEffect): 四向喷射的火焰三角 */
    forgeFlameEffect = new Effect(84f, e -> {
        float fin = e.fin(arc.math.Interp.pow5Out);
        float alpha = 1f - Mathf.curve(fin, 0.5f, 1f);
        for(int i = 0; i < 4; i++){
            float a = 90f * i;
            for(int j = 0; j < 2; j++){
                float side = Mathf.signs[j];
                float fa = a - 45f * side;

                arc.util.Tmp.v1.trns(a, (31f / 4f) * side, 76f / 4f);

                float s = (float)Math.sqrt(72f) / 2f;
                Draw.color(UnityPal.endColor);
                Draw.alpha(alpha);
                arc.util.Tmp.v2.trns(fa, s, 0f);
                arc.util.Tmp.v3.trns(fa, -s, 0f);
                arc.util.Tmp.v4.trns(fa, 0f, fin * 20f);
                Fill.circle(e.x + arc.util.Tmp.v1.x, e.y + arc.util.Tmp.v1.y, s);
                Fill.tri(e.x + arc.util.Tmp.v1.x + arc.util.Tmp.v2.x, e.y + arc.util.Tmp.v1.y + arc.util.Tmp.v2.y,
                    e.x + arc.util.Tmp.v1.x + arc.util.Tmp.v3.x, e.y + arc.util.Tmp.v1.y + arc.util.Tmp.v3.y,
                    e.x + arc.util.Tmp.v1.x + arc.util.Tmp.v4.x, e.y + arc.util.Tmp.v1.y + arc.util.Tmp.v4.y
                );

                s = (float)Math.sqrt(18f) / 2f;
                Draw.color(Color.white);
                Draw.alpha(alpha);
                arc.util.Tmp.v2.trns(fa, s, 0f);
                arc.util.Tmp.v3.trns(fa, -s, 0f);
                arc.util.Tmp.v4.trns(fa, 0f, fin * 16f);
                Fill.circle(e.x + arc.util.Tmp.v1.x, e.y + arc.util.Tmp.v1.y, s);
                Fill.tri(e.x + arc.util.Tmp.v1.x + arc.util.Tmp.v2.x, e.y + arc.util.Tmp.v1.y + arc.util.Tmp.v2.y,
                    e.x + arc.util.Tmp.v1.x + arc.util.Tmp.v3.x, e.y + arc.util.Tmp.v1.y + arc.util.Tmp.v3.y,
                    e.x + arc.util.Tmp.v1.x + arc.util.Tmp.v4.x, e.y + arc.util.Tmp.v1.y + arc.util.Tmp.v4.y
                );
            }
        }
    }).layer(mindustry.graphics.Layer.blockOver);
}
