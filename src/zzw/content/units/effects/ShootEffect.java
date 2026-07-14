package zzw.content.units.effects;

import zzw.content.units.bullets.OppressionLaserBulletType;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.entities.Effect;

import static zzw.content.units.effects.UnityDrawf.diamond;
import static zzw.content.units.effects.UnityDrawf.tri;

/**
 * PU132 ShootFx 移植版
 * - oppressionShoot: 发射特效 (170tick, 纺锤闪光 + 75粒子)
 * 参考: PU132 main/src/unity/content/effects/ShootFx.java L161-214
 */
public class ShootEffect {

    // PU132 颜色常量
    private static final Color SCAR_COLOR = Color.valueOf("f53036");
    private static final Color END_COLOR = Color.valueOf("ff786e");

    private static final Rand rand = new Rand();

    /**
     * Oppression 发射特效 (170tick, 5060 裁剪半径)
     * PU132 ShootFx.oppressionShoot
     * - 0-25tick: 纺锤主体轮廓线 + 末端三角
     * - 全程: 2条横向激光带 + 中心菱形 + 75个粒子
     * .followParent(true).rotWithParent(true)
     */
    public static final Effect oppressionShoot = new Effect(170f, 2530f * 2f, e -> {
        float[] shape = OppressionLaserBulletType.shape;
        float[] q = OppressionLaserBulletType.quad;
        float fin1 = e.time / 25f;

        // 中心菱形 (endColor)
        Draw.color(END_COLOR);
        diamond(e.x, e.y, 17f * e.fout(), 160f + Mathf.absin(8f, 6f) + 90f * e.finpow(), e.rotation + 90f);

        // 阶段1: 纺锤主体轮廓线 (0-25tick)
        if (e.time < 25f) {
            float width = 280f * Interp.pow3Out.apply(fin1);
            Lines.stroke(5f * (1f - fin1));
            for (int i = 0; i < shape.length; i += 4) {
                if (i < shape.length - 4) {
                    for (int j = 0; j < q.length; j += 2) {
                        Vec2 v = Tmp.v1.trns(e.rotation, shape[i + j + 1] * 380f, shape[i + j] * width).add(e.x, e.y);
                        q[j] = v.x;
                        q[j + 1] = v.y;
                    }
                    Lines.line(q[0], q[1], q[6], q[7], false);
                    Lines.line(q[2], q[3], q[4], q[5], false);
                } else {
                    for (int s : Mathf.signs) {
                        Vec2 v = Tmp.v1.trns(e.rotation, 380f, width * s).add(e.x, e.y);
                        tri(v.x, v.y, Lines.getStroke(), 1000f, e.rotation);
                    }
                }
            }
        }

        // 阶段2: 2条横向激光带
        Lines.stroke(11f * e.fout());
        for (int i = 0; i < 2; i++) {
            float fin2 = Mathf.clamp(e.time / 15f) + e.fin() * 0.25f;
            float trns = i == 0 ? 60f : 130f;
            float w = Interp.circleOut.apply((trns / 380f)) * 140f + 250f * fin2;
            Vec2 v = Tmp.v1.trns(e.rotation, trns).add(e.x, e.y);
            Lines.lineAngleCenter(v.x, v.y, e.rotation + 90f, w * 2f, false);
        }

        // 阶段3: 35个粒子 (scarColor → darkGray 渐变, 减少数量保持视觉效果)
        rand.setSeed(e.id * 9999L);
        for (int i = 0; i < 35; i++) {
            float maxOff = 0.2f + rand.random(0.1f);
            float off = rand.nextFloat() * maxOff;
            float fin = Mathf.curve(e.fin(), off, (1f - maxOff) + off);
            float rot = rand.random(360f);
            float trns1 = rand.random(195f) * Interp.pow3Out.apply(fin) + rand.random(20f);
            float trns2 = rand.random(190f, 610f) * Interp.pow2In.apply(fin);
            float scl = Interp.pow3Out.apply(slope(fin, 0.09f)) * rand.random(9f, 14f);

            Vec2 v = Tmp.v1.trns(rot, trns1);
            Vec2 v2 = Tmp.v2.trns(e.rotation, -trns2).add(e.x, e.y);

            // scarColor → darkGray → gray 渐变 (PU132: color(scarColor, darkGray, gray, fin))
            Draw.color(SCAR_COLOR, Color.darkGray, Color.gray, fin);
            Fill.circle(v2.x + v.x, v2.y + v.y, scl);
            Fill.circle(v2.x + v.x / 2f, v2.y + v.y / 2f, scl / 2f);
        }

        Draw.reset();
    }).followParent(true).rotWithParent(true);

    /** Mathf.slope 的简化实现 (PU132 MathU.slope) */
    private static float slope(float fin, float offset) {
        return Mathf.clamp((fin - offset) / (1f - offset), 0f, 1f);
    }
}
