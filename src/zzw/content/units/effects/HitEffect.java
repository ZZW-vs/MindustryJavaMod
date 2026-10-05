package zzw.content.units.effects;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.entities.Effect;
import zzw.content.graphics.UnityPal;

/**
 * PU132 HitFx 移植版
 * - endHitRedBig: 红色大爆炸命中特效 (15tick, 7条线段)
 * 参考: PU132 main/src/unity/content/effects/HitFx.java L319-326
 */
public class HitEffect {

    // PU132 颜色常量
    private static final Color SCAR_COLOR = Color.valueOf("f53036");
    private static final Color END_COLOR = Color.valueOf("ff786e");

    /** 命中特效共用的随机源 (PU132 Utils.seedr) */
    private static final Rand hr = new Rand();

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


    /**
     * 死亡激光命中特效 (35tick) — PU132 HitFx.endDeathLaserHit (L328-366)。
     * <p>oppression 主激光命中时按目标体积 (hitSize, 经 data 传入) 播放:
     * 烟尘 (scarColor→darkGray→gray 圆粒) + 前 15tick 的白色火花线。</p>
     */
    public static final Effect endDeathLaserHit = new Effect(35f, e -> {
        if(!(e.data instanceof Float)) return;
        float rad = Math.max(20f, (Float)e.data);
        float maxOffset = 5f / 35f;
        float maxOffset2 = 5f / 15f;
        int smokeAmount = 9 + (int)(rad / 8);
        int sparkAmount = 2 + (int)(rad / 25);

        hr.setSeed(e.id * 9999L);
        for(int i = 0; i < smokeAmount; i++){
            float cf = (i / (smokeAmount - 1f)) * maxOffset;
            float nf = Mathf.curve(e.fin(), cf, (1f + cf) - maxOffset);

            Draw.color(SCAR_COLOR, Color.darkGray, Color.gray, nf);
            float rot = e.rotation + hr.range(4f);
            float f = Interp.pow3In.apply(nf);
            float w = hr.range(rad) * Interp.circleOut.apply(f);
            float l = (rad * hr.random(1.5f, 3.25f) * f);
            Vec2 v = Tmp.v1.trns(rot, l, w).add(e.x, e.y);
            Fill.circle(v.x, v.y, (9f + rad / 7f) * (1f - nf));
        }
        e.scaled(15f, s -> {
            Draw.color(SCAR_COLOR, Color.white, s.fin());
            Lines.stroke(2f);
            for(int i = 0; i < sparkAmount; i++){
                float cf = (i / (sparkAmount - 1f)) * maxOffset2;
                float nf = Mathf.curve(s.fin(), cf, (1f + cf) - maxOffset2);
                float f = Interp.pow2Out.apply(nf);

                float range = Mathf.sign(hr.chance(0.5f)) * hr.random(60f, 93f);
                float rot = e.rotation + range;
                Vec2 v = Tmp.v1.trns(rot, (f * (rad / 2f) * hr.random(0.5f, 1.2f)) + 0.001f);
                Lines.lineAngle(v.x + e.x, v.y + e.y, v.angle(), (7f + rad / 12f) * (1f - f), false);
            }
        });
        Draw.color();
    });

    /**
     * 磁轨命中特效 (25tick) — PU132 HitFx.endHitRail (L368-389)。
     * soul-destroyer 磁轨炮命中: 15tick 扩散射线 + 3~5 个尖刺三角。
     */
    public static final Effect endHitRail = new Effect(25f, e -> {
        e.scaled(15f, s -> {
            Draw.color(END_COLOR, SCAR_COLOR, e.fin());
            Angles.randLenVectors(e.id, 7, s.fin(Interp.pow3Out) * 45f, e.rotation, 47f, (x, y) -> {
                float ang = Mathf.angle(x, y);
                Lines.stroke(s.fout() * 2f);
                Lines.lineAngle(e.x + x, e.y + y, ang, s.fout(Interp.pow3In) * 24f);
            });
        });

        float scl = 0.3f;
        int spikes = Mathf.randomSeed(e.id * 13L, 3, 5);
        Draw.color(SCAR_COLOR);
        for(int i = 0; i < spikes; i++){
            float fin = Mathf.curve(e.fin(), (i / (float)spikes) * scl, (((i + 1f) / spikes) * scl) + (1f - scl));
            float fin2 = Mathf.curve(fin, 0f, 0.3f);
            float fout = 1f - fin;
            float angle = Mathf.randomSeed(e.id * 53L + i * 31L, -25f, 25f) + e.rotation;
            mindustry.graphics.Drawf.tri(e.x, e.y, fout * 20f, fin2 * (80f + Mathf.randomSeed((e.id + i) * 73L, 40f)), angle);
            mindustry.graphics.Drawf.tri(e.x, e.y, fout * 20f, fin2 * 20f, angle + 180f);
        }
        Draw.color();
    });

    /**
     * lightHitLarge (15f) — PU132 HitFx.lightHitLarge。
     * 17 条射线 + 扩散圆环 (w-boson 主弹消失特效)。
     */
    public static final Effect lightHitLarge = new Effect(15f, e -> {
        Draw.color(mindustry.graphics.Pal.lancerLaser, UnityPal.lightEffect, e.fin());
        Lines.stroke(0.5f + e.fout());

        Angles.randLenVectors(e.id, 17, e.finpow() * 50f, (x, y) -> {
            float a = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, a, e.fout() * 8f);
        });

        Lines.stroke(0.5f + e.fout() * 1.2f);
        Lines.circle(e.x, e.y, e.finpow() * 30f);
    });

    /**
     * electron 命中特效 (12f) — PU132 HitFx.electronHit。
     * 扩散圆环 + 7 个旋转小三角。
     */
    public static final Effect electronHit = new Effect(12f, e -> {
        Draw.color(mindustry.graphics.Pal.lancerLaser, UnityPal.lightEffect, e.fin());
        Lines.stroke(e.fout() * 3f);
        Lines.circle(e.x, e.y, e.fin() * 90f);

        Angles.randLenVectors(e.id, 7, e.finpow() * 45f, (x, y) -> {
            float a = Mathf.angle(x, y);
            Fill.poly(e.x + x, e.y + y, 3, e.fout() * 4f, e.fin() * 120f + e.rotation + a);
        });
    });

    /**
     * proton 命中特效 (20f) — PU132 HitFx.protonHit。
     * 更大的扩散圆环 + 12 个旋转小三角 (透明渐隐)。
     */
    public static final Effect protonHit = new Effect(20f, e -> {
        Draw.color(mindustry.graphics.Pal.lancerLaser, Color.valueOf("4787ff00"), e.fin());
        Lines.stroke(e.fout() * 4f);
        Lines.circle(e.x, e.y, e.fin() * 150f);

        Angles.randLenVectors(e.id, 12, e.finpow() * 64f, (x, y) -> {
            float a = Mathf.angle(x, y);
            Fill.poly(e.x + x, e.y + y, 3, e.fout() * 6f, e.fin() * 135f + e.rotation + a);
        });
    });

    /**
     * neutron 命中特效 (28f) — PU132 HitFx.neutronHit。
     * 7 个旋转小三角 (无圆环)。
     */
    public static final Effect neutronHit = new Effect(28f, e -> {
        Draw.color(mindustry.graphics.Pal.lancerLaser, UnityPal.lightEffect, e.fin());

        Angles.randLenVectors(e.id, 7, e.finpow() * 50f, (x, y) -> {
            float a = Mathf.angle(x, y);
            Fill.poly(e.x + x, e.y + y, 3, e.fout() * 5f, e.fin() * 120f + e.rotation + a);
        });
    });

    /**
     * w-boson 衰变弹命中特效 (13f) — PU132 HitFx.wBosonDecayHitEffect。
     * 17 条短射线。
     */
    public static final Effect wBosonDecayHit = new Effect(13f, e -> {
        Draw.color(mindustry.graphics.Pal.lancerLaser, UnityPal.lightEffect, e.fin());
        Lines.stroke(0.5f + e.fout());

        Angles.randLenVectors(e.id, 17, e.finpow() * 20f, (x, y) -> {
            float a = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, a, e.fout() * 8f);
        });
    });

    /**
     * orb 命中特效 (12f) — PU132 HitFx.orbHit。
     * 8 条径向短线 (surge 色)。
     */
    public static final Effect orbHit = new Effect(12f, e -> {
        Draw.color(mindustry.graphics.Pal.surge);
        Lines.stroke(e.fout() * 1.5f);
        Angles.randLenVectors(e.id, 8, e.finpow() * 17f, e.rotation, 360f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 4f + 1f);
        });
    });

    /**
     * plasma 三角弹命中特效 (30f) — PU132 HitFx.plasmaTriangleHit。
     * surge 色扩散冲击环。
     */
    public static final Effect plasmaTriangleHit = new Effect(30f, e -> {
        Draw.color(mindustry.graphics.Pal.surge);
        Lines.stroke(e.fout() * 2.8f);
        Lines.circle(e.x, e.y, e.fin() * 60f);
    });

    /**
     * blue-eclipse 命中特效 (15f) — PU132 HitFx.eclipseHit。
     * 浅蓝→蓝 4 方块散开 + 白/激光色 7 射线。
     */
    public static final Effect eclipseHit = new Effect(15f, e -> {
        Draw.color(Color.valueOf("c2ebff"), Color.valueOf("68c0ff"), e.fin());
        Angles.randLenVectors(e.id, 4, e.finpow() * 28f, (x, y) ->
            Fill.poly(e.x + x, e.y + y, 4, 3f + e.fout() * 9f, 0f));

        Draw.color(Color.white, mindustry.graphics.Pal.lancerLaser, e.fin());
        Lines.stroke(1.5f * e.fout());
        Angles.randLenVectors(e.id * 2L, 7, e.finpow() * 42f, (x, y) -> {
            float a = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, a, e.fout() * 8f + 1.5f);
        });
    });

    /**
     * advance 派系燃烧命中特效 (15f) — PU132 HitFx.hitAdvanceFlame。
     * advance 双色 2 个六边形 (旋转)。
     */
    public static final Effect hitAdvanceFlame = new Effect(15f, e -> {
        Draw.color(UnityPal.advance, UnityPal.advanceDark, e.fin());
        Angles.randLenVectors(e.id, 2, e.finpow() * 17f, e.rotation, 60f, (x, y) ->
            Fill.poly(e.x + x, e.y + y, 6, 3f + e.fout() * 3f, e.rotation));
    });

    /**
     * branch 激光破片命中特效 (8f) — PU132 HitFx.branchFragHit。
     * 双层白/激光色扩散环。
     */
    public static final Effect branchFragHit = new Effect(8f, e -> {
        Draw.color(Color.white, mindustry.graphics.Pal.lancerLaser, e.fin());
        Lines.stroke(0.5f + e.fout());
        Lines.circle(e.x, e.y, e.fin() * 5f);
        Lines.stroke(e.fout());
        Lines.circle(e.x, e.y, e.fin() * 6f);
    });

    /**
     * 红色小爆炸命中特效 (15f) — PU132 HitFx.endHitRedSmall (L287-301)。
     * 半径扩散环 + 7 条红线段。endLaserSmall / thalassophobia 导弹命中用。
     */
    public static final Effect endHitRedSmall = new Effect(15f, e -> {
        e.scaled(e.lifetime / 2f, s -> {
            Draw.color(SCAR_COLOR, END_COLOR, s.fin());
            Lines.stroke(2f * s.fout());
            Lines.circle(e.x, e.y, 10f * s.fin());
        });

        Draw.color(END_COLOR, SCAR_COLOR, e.fin());

        Angles.randLenVectors(e.id, 7, e.fin(Interp.pow3Out) * 20f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.stroke(e.fout());
            Lines.lineAngle(e.x + x, e.y + y, ang, e.fout(Interp.pow5In) * 12f);
        });
    });

    /**
     * 虚空小命中特效 (20f) — PU132 HitFx.voidHit (L259-265)。
     * 纯黑色粒子团 (void-fracture 弹命中)。
     */
    public static final Effect voidHit = new Effect(20f, e -> {
        Draw.color(Color.black);
        Angles.randLenVectors(e.id, 7, e.finpow() * 15f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 5f);
            Fill.circle(e.x + x / 2f, e.y + y / 2f, e.fout() * 3f);
        });
    });

    /**
     * 虚空大命中特效 (30f) — PU132 HitFx.voidHitBig (L267-285)。
     * 3 层黑色双向三角 + 45° 锥形黑色粒子飞散。
     */
    public static final Effect voidHitBig = new Effect(30f, e -> {
        Draw.color(Color.black);
        e.scaled(e.lifetime / 2, s -> {
            for(int i = 0; i < 3; i++){
                float f = Mathf.lerp(10, 5, i / 2f);
                Draw.alpha(Mathf.lerp(0.45f, 1, (i / 2f) * (i / 2f)));

                mindustry.graphics.Drawf.tri(e.x, e.y,  f * 1.22f * s.fout(Interp.pow5Out), 1 + 7 * f * s.fin(Interp.pow5Out), e.rotation);
                mindustry.graphics.Drawf.tri(e.x, e.y,  f * 1.22f * s.fout(Interp.pow5Out), 3 * f * s.fout(Interp.pow5Out), e.rotation - 180f);
            }
        });

        if(e.fin() > 0.45f){
            float l2 = Mathf.curve(e.fin(), 0.45f, 1);
            Angles.randLenVectors(e.id, 20, 35 * Interp.pow2Out.apply(l2), e.rotation, 45, (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 4 * Interp.pow2Out.apply(1 - l2));
            });
        }
    });

    /** 临时颜色对象 (避免每次 new) */
    private static class TmpColor {
        static final Color c1 = new Color();
    }
}
