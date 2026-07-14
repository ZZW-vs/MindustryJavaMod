package zzw.content.units.effects;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.graphics.Layer;

/**
 * PU132 充能特效 (简化移植版)
 * - devourerCharge: 41tick, 3层光环叠加
 * - oppressionCharge: 5*60tick, 三阶段粒子渐入
 * 参考: PU132 main/src/unity/content/effects/ChargeFx.java
 */
public class ChargeEffect {

    // PU132 颜色常量
    private static final Color SCAR_COLOR = Color.valueOf("f53036");
    private static final Color END_COLOR = Color.valueOf("ff786e");
    /**
     * 斜坡函数 (移植自 PU132 MathU.slope)
     * bias 处达到峰值 1, 0 和 1 处为 0, 形成非对称三角波
     * 参考: PU132 main/src/unity/util/MathU.java L24-26
     */
    private static float slope(float fin, float bias) {
        return (fin < bias ? (fin / bias) : 1f - (fin - bias) / (1f - bias));
    }

    /**
     * Devourer 充能特效 (41tick, 3层光环)
     * PU132 ChargeFx.devourerChargeEffect
     */
    public static final Effect devourerCharge = new Effect(41f, 80f, e -> {
        // ★ 设置高渲染层级, 确保充能特效显示在单位上方
        Draw.z(Layer.flyingUnit + 1f);
        Color[] colors = {SCAR_COLOR, END_COLOR, Color.white};

        for (int i = 0; i < colors.length; i++) {
            Draw.color(colors[i]);
            float scl = (colors.length - (i / 1.25f)) * (17f / colors.length);
            float width = (35f / (1f + (i / Mathf.pi))) * e.fin();
            float spikeIn = e.fslope() * scl * 1.5f;

            // 简化版闪光圆环: 圆 + 尖刺
            float radius = scl * e.fin() * 8f;
            if (radius > 0.5f) {
                Lines.stroke(width);
                Lines.circle(e.x, e.y, radius);

                // 尖刺
                int spikes = 9;
                float rot = e.id * 241 + arc.util.Time.time + i * 3f;
                for (int s = 0; s < spikes; s++) {
                    float angle = rot + (360f / spikes) * s;
                    float cos = Mathf.cosDeg(angle);
                    float sin = Mathf.sinDeg(angle);
                    float outerR = radius + 12f * spikeIn;
                    Lines.line(e.x + cos * radius, e.y + sin * radius,
                               e.x + cos * outerR, e.y + sin * outerR, false);
                }
            }
        }
        Draw.reset();
    }).followParent(true).rotWithParent(true);

    /**
     * Oppression 充能特效 (5*60tick, 完整移植 PU132)
     * - 11个菱形粒子辐射 (0-150tick)
     * - 13个尖刺菱形 (145tick+)
     * - 中心菱形 (145tick+)
     * - 35个方块粒子 (scarColor→black 渐变)
     * - 22条短线段 (沿激光方向)
     * - 主线 (最后渐入黑色)
     * 参考: PU132 main/src/unity/content/effects/ChargeFx.java L112-202
     */
    public static final Effect oppressionCharge = new Effect(4f * 60f, 2530f * 2f, e -> {
        // ★ 设置高渲染层级, 确保充能前摇特效显示在单位上方
        Draw.z(Layer.flyingUnit + 1f);
        float off = 140f / e.lifetime;
        float off2 = 70f / e.lifetime;

        // 阶段1: 0-120tick (原150tick, 按4/5比例缩放)
        float fin1 = e.time >= 120f ? 1f : e.time / 120f;
        float fin2 = e.time >= 48f ? 1f : e.time / 48f;
        float time = arc.util.Time.time;

        // ===== 阶段1: 11个菱形粒子辐射 (0-150tick) =====
        Draw.color(SCAR_COLOR);
        for (int i = 0; i < 11; i++) {
            float f = (i / 10f) * off2;
            float cf = Mathf.curve(e.fin(), f, (1f - off2) + f);
            float cfo = 1f - cf;
            if (cf <= 0f || cf >= 1f) continue;

            float rot = e.rotation + (Mathf.random(e.id * 9999L + i) - 0.5f) * 12f;
            float len = Mathf.random(75f, 210f) * Interp.pow2Out.apply(slope(cf, 0.75f));
            float wid = (len / 15f) * cf * 2f * Mathf.random(0.8f, 1.2f);
            float trns = Mathf.random(2530f - len * 2f) + len;
            Tmp.v1.trns(rot, trns * Interp.pow3In.apply(cfo)).add(e.x, e.y);
            UnityDrawf.diamond(Tmp.v1.x + Mathf.range(4f) * cf, Tmp.v1.y + Mathf.range(4f) * cf, wid, len, rot);
        }

        // ===== 阶段2: 13个尖刺菱形 + 中心菱形 (116tick+, 原145tick按4/5比例缩放) =====
        if (e.time > 116f) {
            float fin3 = e.time - 116f >= 112f ? 1f : (e.time - 116f) / 112f;
            float spikef = Mathf.clamp((e.time - 116f) / 16f, 0f, 13f);
            int spikei = Mathf.ceil(spikef);

            for (int i = 0; i < spikei; i++) {
                float spikem = spikef >= 13f || i < spikei - 1 ? 1f : (spikef % 1f);
                float rot = e.rotation + i * (360f / 13f);
                float trns = 30f * fin3;
                float w = 20f * fin3;
                float l = 100f * spikem * fin3;
                Tmp.v1.trns(rot, trns).add(e.x, e.y);
                UnityDrawf.diamond(Tmp.v1.x, Tmp.v1.y, w, l, 0.4f, rot);
            }

            // 中心菱形
            float fin4 = (e.time - 116f) / (e.lifetime - 116f);
            float cw = 17f * Interp.pow2Out.apply(Mathf.curve(fin4, 0f, 0.2f));
            float cl = (160f + Mathf.absin(8f, 6f)) * Interp.pow2.apply(fin4);
            UnityDrawf.diamond(e.x, e.y, cw, cl, e.rotation + 90f);
        }

        // ===== 阶段3: 35个方块粒子 (scarColor→black 渐变) =====
        for (int i = 0; i < 35; i++) {
            float d = Mathf.randomSeed(e.id * 9999L + i, 10f, 30f);
            float timeOffset = Mathf.randomSeed(e.id * 9999L + i + 100, 0f, d);
            int timeSeed = Mathf.floor((time + timeOffset) / d) + i * 31;
            float ff = ((time + timeOffset) % d) / d;
            float fo = 1f - ff;
            float trv = 1f - (ff < 0.75f ? Interp.pow3Out.apply(ff / 0.75f) * 0.75f : Interp.pow2In.apply((ff - 0.75f) / 0.25f) * 0.25f + 0.75f);

            float rot = Mathf.randomSeed(timeSeed, 0f, 360f);
            float trns = (Mathf.randomSeed(timeSeed + 1, 15f, 65f) + Mathf.randomSeed(timeSeed + 2, 15f, 75f) * e.fin()) * trv;
            float trns2 = Mathf.randomSeed(timeSeed + 3, 200f, 900f) * fo * (1f - fin1);
            float rad = (Mathf.randomSeed(timeSeed + 4, 10f, 22f) + 11f * e.fin()) * fin2 * Interp.pow2Out.apply(slope(ff, 0.75f));
            if (trns2 > 0) {
                Tmp.v1.trns(e.rotation + Mathf.range(4f), trns2).add(e.x, e.y);
            } else {
                Tmp.v1.set(e.x, e.y);
            }
            Draw.color(SCAR_COLOR, Color.black, Mathf.curve(ff, 0.35f, 0.75f));
            Tmp.v2.trns(rot, trns).add(Tmp.v1);
            Fill.square(Tmp.v2.x, Tmp.v2.y, rad, 45f);
        }

        // ===== 阶段4: 22条短线段 (沿激光方向) =====
        Draw.color(SCAR_COLOR);
        for (int i = 0; i < 22; i++) {
            float f = (i / 21f) * off;
            float cf = Mathf.curve(e.fin(), f, (1f - off) + f);
            float cfo = 1f - cf;
            float rot = e.rotation + (Mathf.random(e.id * 9999L + i + 200) - 0.5f) * 20f;
            float len = Mathf.random(300f, 800f);
            float trns = Mathf.random(2530f - len) * cfo * cfo;
            if (cf <= 0f || cf >= 1f) continue;
            Tmp.v1.trns(rot, trns).add(e.x, e.y);
            Lines.stroke(3f);
            Lines.lineAngle(Tmp.v1.x, Tmp.v1.y, rot, len * Mathf.slope(cfo * cfo), false);
        }

        // ===== 阶段5: 主线 (最后渐入黑色) =====
        float t = e.time < 3f * 60f ? 0f : Mathf.clamp((e.time - 3f * 60f) / 24f);
        float length = Interp.pow3.apply(Mathf.clamp(e.time / 16f)) * 2530f;
        Draw.color(SCAR_COLOR, Color.black, t);
        Lines.stroke(5f);
        Lines.lineAngle(e.x, e.y, e.rotation, length);

        Draw.reset();
    }).followParent(true).rotWithParent(true);
}
