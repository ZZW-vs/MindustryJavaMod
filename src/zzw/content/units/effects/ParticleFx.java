package zzw.content.units.effects;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.math.Interp;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import mindustry.entities.Effect;
import mindustry.graphics.Layer;
import zzw.content.graphics.UnityPal;

import static arc.graphics.g2d.Draw.alpha;
import static arc.graphics.g2d.Draw.blend;
import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Draw.rect;
import static arc.graphics.g2d.Fill.circle;
import static arc.graphics.g2d.Fill.poly;
import static arc.graphics.g2d.Fill.rect;
import static arc.graphics.g2d.Fill.square;
import static arc.graphics.g2d.Lines.lineAngle;
import static arc.graphics.g2d.Lines.stroke;
import static arc.math.Angles.randLenVectors;

/**
 * 小型粒子特效 (PU132 unity.content.effects.ParticleFx 移植)。
 *
 * <p>本类收录以 "小粒子" 为主的通用特效:
 * Scar 的再生禁止标记、Monolith 火花 / 灵魂粒子、雷电支点线。
 * Monolith 灵魂粒子的 "黑芯 + 阴影圈" 双层画法是 Monolith 系列的标志性视觉。</p>
 *
 * <p>★ v132 → v155 适配要点:</p>
 * <ul>
 *   <li>{@code unity.graphics.UnityPal} → {@link UnityPal};</li>
 *   <li>其余 API (Fill/Lines/Blending/Angles.randLenVectors) 在 v155 无变化。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class ParticleFx{

    /**
     * electron/proton/neutron 弹道拖尾 (50f) — PU132 UnityFx.blueTriangleTrail。
     * 白→激光色收缩三角 (朝向反向)。
     */
    public static final Effect blueTriangleTrail = new Effect(50f, e -> {
        color(Color.white, mindustry.graphics.Pal.lancerLaser, e.fin());
        poly(e.x, e.y, 3, 4f * e.fout(), e.rotation + 180f);
    });

    /**
     * singularity 奇点弹光尾 (55f) — PU132 UnityFx.lightHexagonTrail。
     * 激光色→lightEffect 六边形, 半径随速度 (e.rotation 传速度值)。
     */
    public static final Effect lightHexagonTrail = new Effect(55f, e -> {
        color(mindustry.graphics.Pal.lancerLaser, UnityPal.lightEffect, e.fin());
        poly(e.x, e.y, 6, e.rotation * e.fout(), e.rotation);
    });

    /**
     * w-boson 衰变小拖尾 (24f) — PU132 UnityFx.wBosonEffect。
     * 沿弹道短射线。
     */
    public static final Effect wBosonEffect = new Effect(24f, e -> {
        color(mindustry.graphics.Pal.lancerLaser, UnityPal.lightEffect, e.fin());
        stroke(1.25f);
        lineAngle(e.x, e.y, e.rotation, e.fout() * 4f);
    });

    /** w-boson 衰变长拖尾 (47f) — PU132 UnityFx.wBosonEffectLong。 */
    public static final Effect wBosonEffectLong = new Effect(47f, e -> {
        color(mindustry.graphics.Pal.lancerLaser, UnityPal.lightEffect, e.fin());
        stroke(1.25f);
        lineAngle(e.x, e.y, e.rotation, e.fout() * 7f);
    });

    /**
     * orb 弹道光点拖尾 (43f) — PU132 UnityFx.orbTrail。
     * 随机偏移的 surge 光点 + 微光照。
     */
    public static final Effect orbTrail = new Effect(43f, e -> {
        arc.util.Tmp.v1.trns(Mathf.randomSeed(e.id) * 360f, Mathf.randomSeed(e.id * 341L) * 12f * e.fin());

        mindustry.graphics.Drawf.light(e.x + arc.util.Tmp.v1.x, e.y + arc.util.Tmp.v1.y, 4.7f * e.fout() + 3f, mindustry.graphics.Pal.surge, 0.6f);

        color(mindustry.graphics.Pal.surge);
        circle(e.x + arc.util.Tmp.v1.x, e.y + arc.util.Tmp.v1.y, e.fout() * 2.7f);
    }).layer(Layer.bullet - 0.01f);

    /**
     * orb 充能特效 (38f) — PU132 UnityFx.orbCharge。
     * 双射向扇形短线。
     */
    public static final Effect orbCharge = new Effect(38f, e -> {
        color(mindustry.graphics.Pal.surge);
        randLenVectors(e.id, 2, 1f + 20f * e.fout(), e.rotation, 120f, (x, y) -> lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fslope() * 3f + 1f));
    });

    /**
     * orb 充能起始特效 (71f) — PU132 UnityFx.orbChargeBegin。
     * surge/白双层收缩圆点。
     */
    public static final Effect orbChargeBegin = new Effect(71f, e -> {
        color(mindustry.graphics.Pal.surge);
        circle(e.x, e.y, e.fin() * 3f);
        color();
        circle(e.x, e.y, e.fin() * 2f);
    });

    /**
     * current 充能特效 (32f) — PU132 UnityFx.currentCharge。
     * 远距离拉丝 (420+ 长度) 细线束。
     */
    public static final Effect currentCharge = new Effect(32f, e -> {
        color(mindustry.graphics.Pal.surge, Color.white, e.fin());
        randLenVectors(e.id, 8, 420f + Mathf.random(24f, 28f) * e.fout(), e.rotation, 4f, (x, y) -> {
            stroke(0.3f + e.fout() * 2f);
            lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 14f + 0.5f);
        });
    });

    /**
     * current 充能起始特效 (260f) — PU132 UnityFx.currentChargeBegin。
     * surge/白双层缓慢生长圆点。
     */
    public static final Effect currentChargeBegin = new Effect(260f, e -> {
        color(mindustry.graphics.Pal.surge);
        circle(e.x, e.y, e.fin() * 7f);
        color();
        circle(e.x, e.y, e.fin() * 3f);
    });

    /** plasma 破片出现特效 (12f) — PU132 UnityFx.plasmaFragAppear。白色生长三角。 */
    public static final Effect plasmaFragAppear = new Effect(12f, e -> {
        color(Color.white);
        mindustry.graphics.Drawf.tri(e.x, e.y, e.fin() * 12f, e.fin() * 13f, e.rotation);
    }).layer(Layer.bullet - 0.01f);

    /** plasma 破片消失特效 (12f) — PU132 UnityFx.plasmaFragDisappear。surge→白收缩三角。 */
    public static final Effect plasmaFragDisappear = new Effect(12f, e -> {
        color(mindustry.graphics.Pal.surge, Color.white, e.fin());
        mindustry.graphics.Drawf.tri(e.x, e.y, e.fout() * 10f, e.fout() * 11f, e.rotation);
    }).layer(Layer.bullet - 0.01f);

    /**
     * electrobomb 落点涌浪特效 (40f, 裁剪 100) — PU132 UnityFx.surgeSplash。
     * surge 扩散环 + 四向 (90° 间隔) 双层大三角。
     */
    public static final Effect surgeSplash = new Effect(40f, 100f, e -> {
        color(mindustry.graphics.Pal.surge);
        stroke(e.fout() * 2f);
        arc.graphics.g2d.Lines.circle(e.x, e.y, 4 + e.finpow() * 65);

        color(mindustry.graphics.Pal.surge);
        for(int i = 0; i < 4; i++){
            mindustry.graphics.Drawf.tri(e.x, e.y, 6, 100 * e.fout(), i * 90);
        }

        color();
        for(int i = 0; i < 4; i++){
            mindustry.graphics.Drawf.tri(e.x, e.y, 3, 35 * e.fout(), i * 90);
        }
    });

    /**
     * arc-caster/arc-storm 充能特效 (27f) — PU132 UnityFx.arcCharge。
     * 灰蓝双色 2 个脉动六边形 (±135° 扇形)。
     */
    public static final Effect arcCharge = new Effect(27f, e -> {
        color(Color.valueOf("606571"), Color.valueOf("6c8fc7"), e.fin());
        Angles.randLenVectors(e.id, 2, e.fout() * 40f, e.rotation, 135f, (x, y) -> {
            poly(e.x + x, e.y + y, 6, 1f + Mathf.sin(e.fin() * 3f, 1f, 2f) * 5f, e.rotation);
        });
    });

    /**
     * oracle 充能特效 (30f) — PU132 UnityFx.oracleCharge。
     * 激光色旋转分布的 45° 方块。
     */
    public static final Effect oracleCharge = new Effect(30f, e -> {
        color(mindustry.graphics.Pal.lancerLaser);
        arc.util.Tmp.v1.trns(Mathf.randomSeed(e.id, 360f) + Time.time, (1 - e.finpow()) * 20f);
        square(e.x + arc.util.Tmp.v1.x, e.y + arc.util.Tmp.v1.y, e.fin() * 4.5f, 45f);
    });

    /** oracle 充能起始特效 (40f) — PU132 UnityFx.oracleChargeBegin。激光色生长圆点。 */
    public static final Effect oracleChargeBegin = new Effect(40f, e -> {
        color(mindustry.graphics.Pal.lancerLaser);
        circle(e.x, e.y, e.fin() * 6f);
    });

    /**
     * 蓝色燃烧状态伴随特效 (35f) — PU132 UnityFx.blueBurnEffect。
     * advance 双色 3 个小光尘 (blueBurn StatusEffect 的 effect 字段用)。
     */
    public static final Effect blueBurnEffect = new Effect(35f, e -> {
        color(UnityPal.advance, UnityPal.advanceDark, e.fin());
        Angles.randLenVectors(e.id, 3, 2 + e.fin() * 7, (x, y) ->
            circle(e.x + x, e.y + y, 0.1f + e.fout() * 1.4f));
    });

    /**
     * ricochet 弹道拖尾 (小, 12f) — PU132 UnityFx.ricochetTrailSmall。
     * 4 个 monolith 渐变小方块。
     */
    public static final Effect ricochetTrailSmall = new Effect(12f, e -> randLenVectors(e.id, 4, e.fout() * 3.5f, (x, y) -> {
        float w = 0.3f + e.fout();
        color(UnityPal.monolith, UnityPal.monolithDark, e.fin());
        rect(e.x + x, e.y + y, w, w, 45f);
    }));

    /**
     * shellshock 弹道拖尾 (中, 16f) — PU132 UnityFx.ricochetTrailMedium。
     * 5 个 monolith 渐变中方块。
     */
    public static final Effect ricochetTrailMedium = new Effect(16f, e -> randLenVectors(e.id, 5, e.fout() * 5f, (x, y) -> {
        float w = 0.3f + e.fout() * 1.3f;
        color(UnityPal.monolith, UnityPal.monolithDark, e.fin());
        rect(e.x + x, e.y + y, w, w, 45f);
    }));

    public static Effect

    /**
     * 再生禁止标记 (30f): scarColor 色的 45° 正方形, 尺寸按 pow2In(fslope)
     * 先慢后快地收缩到 0 —— 用于 End 系列禁止目标回血的提示。
     */
    endRegenDisable = new Effect(30f, e -> {
        color(UnityPal.scarColor);
        square(e.x, e.y, 2.5f * Interp.pow2In.apply(e.fslope()), 45f);
    }),

    /**
     * Monolith 火花 (60f): 2 个随机方向的小方块,
     * 颜色 monolith → monolithDark 随进度加深, 边长 1 + fout×4 收缩。
     */
    monolithSpark = new Effect(60f, e -> randLenVectors(e.id, 2, e.rotation, (x, y) -> {
        color(UnityPal.monolith, UnityPal.monolithDark, e.fin());

        float w = 1f + e.fout() * 4f;
        rect(e.x + x, e.y + y, w, w, 45f);
    })),

    /**
     * 雷电支点线 (36f): 3 条随机短线段, 分布半径随 foutpowdown 收缩,
     * 线段朝向指向粒子本身的方位角, 长度 fin×6 渐长。
     * 用作闪电类武器的中继 "支点" 视觉。
     */
    lightningPivot = new Effect(36f, e -> {
        stroke(2f, e.color);
        randLenVectors(e.id, 3, e.foutpowdown() * 32f, (x, y) ->
            lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fin() * 6f)
        );
    });
}
