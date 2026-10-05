package zzw.content.units.effects;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
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

    /** 充能特效共用的随机源 (PU132 Utils.seedr / seedr2 / seedr3) */
    private static final Rand seedr = new Rand();
    private static final Rand seedr2 = new Rand();
    private static final Rand seedr3 = new Rand();
    /**
     * 斜坡函数 (移植自 PU132 MathU.slope)
     * bias 处达到峰值 1, 0 和 1 处为 0, 形成非对称三角波
     * 参考: PU132 main/src/unity/util/MathU.java L24-26
     */
    private static float slope(float fin, float bias) {
        return (fin < bias ? (fin / bias) : 1f - (fin - bias) / (1f - bias));
    }

    /**
     * Tenmeikiri 充能粒子特效 (40tick, 2个辐射线粒子)
     * PU_V8 ChargeFx.tenmeikiriChargeEffect
     * 颜色从 scarColor 渐变到 endColor, 线段随 fout 缩短
     */
    public static final Effect tenmeikiriChargeEffect = new Effect(40f, e -> {
        // v155.4 Angles.randLenVectors 4 参数版本 (无 rotation 参数, 粒子全方向随机)
        // PU_V8 原版 5 参数版本 (seed, amount, length, rotation=90f, cons) 在 v155.4 不存在
        Angles.randLenVectors(e.id, 2, 10f, (x, y) -> {
            float angle = Mathf.angle(x, y);
            Draw.color(SCAR_COLOR, END_COLOR, e.fin());
            Lines.stroke(1.5f);
            Lines.lineAngleCenter(e.x + (x * e.fout()), e.y + (y * e.fout()), angle, e.fslope() * 13f);
        });
    }).followParent(true).rotWithParent(true);

    /**
     * Tenmeikiri 充能起始特效 (158tick, 3层三角形叠加)
     * PU_V8 ChargeFx.tenmeikiriChargeBegin
     * 宽度随时间增长 (clamp(time/80)), 3 层颜色 scarColor→endColor→white
     */
    public static final Effect tenmeikiriChargeBegin = new Effect(158f, e -> {
        Color[] colors = {SCAR_COLOR, END_COLOR, Color.white};
        for(int ii = 0; ii < 3; ii++){
            float s = (3 - ii) / 3f;
            float width = Mathf.clamp(e.time / 80f) * (20f + Mathf.absin(Time.time + (ii * 1.4f), 1.1f, 7f)) * s;
            float length = e.fin() * (100f + Mathf.absin(Time.time + (ii * 1.4f), 1.1f, 11f)) * s;
            Draw.color(colors[ii]);
            for(int i : Mathf.signs){
                float rotation = e.rotation + (i * 90f);
                Drawf.tri(e.x, e.y, width, length * 0.5f, rotation);
            }
            Drawf.tri(e.x, e.y, width, length * 1.25f, e.rotation);
        }
    }).followParent(true).rotWithParent(true);

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
     * Oppression 充能特效 (4*60tick, 完整移植 PU132)
     * - 11个菱形粒子辐射 (0-120tick)
     * - 13个尖刺菱形动画 + 中心菱形 (116tick+)
     * - 35个方块粒子 (scarColor→black 渐变)
     * - 22条短线段 (沿激光方向)
     * - 主线 (最后渐入黑色)
     * - 主线爆发 9组×9菱形 (t>0, 原版 3.75*60 起)
     * - 主线前 30方块粒子 (e.time<3*60)
     * 参考: PU132 main/src/unity/content/effects/ChargeFx.java L111-250
     * 时间轴按项目节奏做 4/5 缩放 (lifetime 5*60→4*60)
     */
    public static final Effect oppressionCharge = new Effect(4f * 60f, 2530f * 2f, e -> {
        // ★ 设置高渲染层级, 确保充能前摇特效显示在单位上方
        Draw.z(Layer.flyingUnit + 1f);

        Rand r = seedr, r2 = seedr2, r3 = seedr3;
        r.setSeed(e.id * 9999L);

        float off = 140f / e.lifetime;
        float off2 = 70f / e.lifetime;

        // 时间按 4/5 比例缩放 (原版 150→120, 60→48, 145→116, 3.75*60→3*60)
        float fin1 = e.time >= 120f ? 1f : e.time / 120f;
        float fin2 = e.time >= 48f ? 1f : e.time / 48f;

        float time = arc.util.Time.time;

        // ===== 阶段1: 11个菱形粒子辐射 (0-120tick) =====
        Draw.color(SCAR_COLOR);
        for (int i = 0; i < 11; i++) {
            float f = (i / 10f) * off2;
            float cf = Mathf.curve(e.fin(), f, (1f - off2) + f);
            float cfo = 1f - cf;
            if (cf <= 0f || cf >= 1f) continue;

            float rot = e.rotation + (r.nextFloat() - r.nextFloat()) * 6f;
            float len = r.random(75f, 210f) * Interp.pow2Out.apply(slope(cf, 0.75f));
            float wid = (len / 15f) * cf * 2f * r.random(0.8f, 1.2f);
            float trns = r.random(2530f - len * 2f) + len;
            Tmp.v1.trns(rot, trns * Interp.pow3In.apply(cfo)).add(e.x, e.y);
            UnityDrawf.diamond(Tmp.v1.x + Mathf.range(4f) * cf, Tmp.v1.y + Mathf.range(4f) * cf, wid, len, rot);
        }

        // ===== 阶段2: 13个尖刺菱形动画 + 中心菱形 (116tick+, 原145tick) =====
        if (e.time > 116f) {
            float fin3 = e.time - 116f >= 112f ? 1f : (e.time - 116f) / 112f;
            r3.setSeed(e.id * 9999L + 781);
            float spikef = Mathf.clamp((e.time - 116f) / 16f, 0f, 13f);
            int spikei = Mathf.ceil(spikef);

            for (int i = 0; i < spikei; i++) {
                float spikem = spikef >= 13f || i < spikei - 1 ? 1f : (spikef % 1f);
                float d = r3.random(25f, 45f);
                float timeOffset = r3.random(d);
                float f = ((time + timeOffset) % d) / d;
                float fo = 1f - f;
                int timeSeed = Mathf.floor((time + timeOffset) / d) + r3.nextInt();
                float offs = 0.33f;
                float lt = f < offs ? Interp.pow2In.apply(f / offs) : 1f - (f - offs) / (1f - offs);

                r2.setSeed(timeSeed);
                float rot = r2.random(360f) + r2.range(5f) * f;
                float trns = (r2.random(8f, 13f) + r2.random(5f, 10f) * e.fin());
                float w = r2.random(17f, 30f) + r2.random(8f) * fin3 * Mathf.curve(fo, 0f, 0.5f);
                float l = r2.random(75f, 180f) * lt * spikem;
                Tmp.v1.trns(rot, trns).add(e.x, e.y);
                UnityDrawf.diamond(Tmp.v1.x, Tmp.v1.y, w, l, 0.4f, rot);
            }

            // 中心菱形
            float fin4 = (e.time - 116f) / (e.lifetime - 116f);
            UnityDrawf.diamond(e.x, e.y,
                17f * Interp.pow2Out.apply(Mathf.curve(fin4, 0f, 0.2f)),
                (160f + Mathf.absin(8f, 6f)) * Interp.pow2.apply(fin4),
                e.rotation + 90f);
        }

        // ===== 阶段3: 35个方块粒子 (scarColor→black 渐变) =====
        for (int i = 0; i < 35; i++) {
            float d = r.random(10f, 30f);
            float timeOffset = r.random(d);
            int timeSeed = Mathf.floor((time + timeOffset) / d) + r.nextInt();
            float f = ((time + timeOffset) % d) / d;
            float fo = 1f - f;
            float trv = 1f - (f < 0.75f ? Interp.pow3Out.apply(f / 0.75f) * 0.75f : Interp.pow2In.apply((f - 0.75f) / 0.25f) * 0.25f + 0.75f);

            r2.setSeed(timeSeed);
            float rot = r2.random(360f);
            float trns = (r2.random(15f, 65f) + r2.random(15f, 75f) * e.fin()) * trv;
            float trns2 = r2.random(200f, 900f) * fo * (1f - fin1);
            float rad = (r2.random(10f, 22f) + 11f * e.fin()) * fin2 * Interp.pow2Out.apply(slope(f, 0.75f));
            if (trns2 > 0) {
                Tmp.v1.trns(e.rotation + r2.range(4f), trns2).add(e.x, e.y);
            } else {
                Tmp.v1.set(e.x, e.y);
            }
            Draw.color(SCAR_COLOR, Color.black, Mathf.curve(f, 0.35f, 0.75f));
            Tmp.v2.trns(rot, trns).add(Tmp.v1);
            Fill.square(Tmp.v2.x, Tmp.v2.y, rad, 45f);
        }

        // ===== 阶段4: 22条短线段 (沿激光方向) =====
        Draw.color(SCAR_COLOR);
        for (int i = 0; i < 22; i++) {
            float f = (i / 21f) * off;
            float cf = Mathf.curve(e.fin(), f, (1f - off) + f);
            float cfo = 1f - cf;
            if (cf <= 0f || cf >= 1f) continue;
            float rot = e.rotation + (r.nextFloat() - r.nextFloat()) * 20f;
            float len = r.random(300f, 800f);
            float trns = r.random(2530f - len) * cfo * cfo;
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

        // ===== 阶段6: 主线爆发 9组×9菱形 (t>0 时沿主线爆裂) =====
        if (t > 0f) {
            r3.setSeed(e.id * 9999L + 613);
            float dr = 3f * 60f;
            float partf = Mathf.clamp((e.time - dr) / (e.lifetime - dr)) * 9f;
            int parti = Mathf.ceil(partf);

            for (int j = 0; j < parti; j++) {
                float partm = partf >= 9f || j < parti - 1 ? 1f : (partf % 1f);

                for (int i = 0; i < 9; i++) {
                    float d = r3.random(7f, 11f);
                    float timeOffset = r3.random(d);
                    int timeSeed = Mathf.floor((time + timeOffset) / d) + r3.nextInt();
                    float f = ((time + timeOffset) % d) / d;

                    r2.setSeed(timeSeed);
                    float l = r2.random(100f, 200f) * Interp.pow2Out.apply(Mathf.curve(f, 0f, 0.5f)) * partm;
                    float w = r2.random(9f, 19f) * slope(f, 0.8f) * partm * t;

                    float trns = r2.random(2530f - l * 2f) + l + r2.range(3f) * f;
                    float of = (r2.nextFloat() - r2.nextFloat()) * 35f * Interp.pow3Out.apply(1f - f) * (0.5f + t * 0.5f);
                    Tmp.v1.trns(e.rotation, trns, of).add(e.x, e.y);
                    Draw.color(SCAR_COLOR, Color.black, Mathf.curve(f, 0.2f, 0.75f));
                    UnityDrawf.diamond(Tmp.v1.x, Tmp.v1.y, w, l, e.rotation);
                }
            }
        }

        // ===== 阶段7: 主线前 30方块粒子 (e.time<3*60) =====
        if (e.time < 3f * 60f) {
            float t2 = Mathf.clamp((3f * 60f - e.time) / 24f);

            r3.setSeed(e.id * 9999L + 613);
            Draw.color(SCAR_COLOR);
            for (int i = 0; i < 30; i++) {
                float d = r3.random(18f, 24f);
                float timeOffset = r3.random(d);
                int timeSeed = Mathf.floor((time + timeOffset) / d) + r3.nextInt();
                float f = ((time + timeOffset) % d) / d;

                r2.setSeed(timeSeed);
                float trns = r2.random(length) + r2.range(2f) * f;
                float of = (r2.nextFloat() - r2.nextFloat()) * 65f * Interp.pow3In.apply(f) * (0.5f + t2 * 0.5f);
                float scl = r2.random(3f, 8f) * t2 * slope(f, 0.25f);
                Tmp.v1.trns(e.rotation, trns, of).add(e.x, e.y);
                Fill.square(Tmp.v1.x, Tmp.v1.y, scl, 45f);
            }
        }

        Draw.reset();
    }).followParent(true).rotWithParent(true);
}
