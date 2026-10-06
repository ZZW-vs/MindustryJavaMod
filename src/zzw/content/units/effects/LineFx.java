package zzw.content.units.effects;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import zzw.content.graphics.UnityPal;

import static arc.graphics.g2d.Draw.alpha;
import static arc.graphics.g2d.Draw.blend;
import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.line;
import static arc.graphics.g2d.Lines.stroke;
import static arc.math.Angles.randLenVectors;

/**
 * 位置连线特效 (PU132 unity.content.effects.LineFx 移植)。
 *
 * <p>所有特效的 data 均携带 1 个 {@link Position} (终点),
 * 用于 "从 A 拉到 B" 的防御 / 灵魂吸收 / 灵魂转移类效果。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class LineFx{
    public static final Effect

    /**
     * 终点防御连线 (17f, 裁剪 600): 双层 (scarColor + 白) 渐细连线,
     * 两端各带一个同宽圆点, 线宽按 (2-i)×2.2×fout 收缩。
     */
    endPointDefence = new Effect(17f, 300f * 2f, e -> {
        if(!(e.data instanceof Position data)) return;

        for(int i = 0; i < 2; i++){
            float width = (2 - i) * 2.2f * e.fout();
            color(i == 0 ? UnityPal.scarColor : Color.white);
            stroke(width);
            line(e.x, e.y, data.getX(), data.getY(), false);
            Fill.circle(e.x, e.y, width);
            Fill.circle(data.getX(), data.getY(), width);
        }
    }),

    /**
     * 灵魂吸收连线 (32f) —— LineFx.monolithSoulAbsorb。
     *
     * <p>data 携带吸收终点 {@link Position}。灵魂单位在"收集地块 / 接近容器"时
     * 反复播放：一个光球沿随机曲线路径被拉向终点 (additive 蓝黑渐变)。</p>
     */
    monolithSoulAbsorb = new Effect(32f, e -> {
        if(!(e.data instanceof Position data)) return;

        Tmp.v1
            .trns(Angles.angle(e.x, e.y, data.getX(), data.getY()) - 90f, Mathf.randomSeedRange(e.id, 3f))
            .scl(Interp.pow3Out.apply(e.fslope()));
        Tmp.v2.trns(Mathf.randomSeed(e.id + 1, 360f), e.fin(Interp.pow4Out));
        Tmp.v3.set(data).sub(e.x, e.y).scl(e.fin(Interp.pow4In))
            .add(Tmp.v2).add(Tmp.v1).add(e.x, e.y);

        float fin = 0.3f + e.fin() * 1.4f;

        blend(Blending.additive);
        color(Color.black, UnityPal.monolithDark, e.fin());

        alpha(1f);
        Fill.circle(Tmp.v3.x, Tmp.v3.y, fin);

        alpha(0.67f);
        Draw.rect("circle-shadow", Tmp.v3.x, Tmp.v3.y, fin + 6f, fin + 6f);

        blend();
    }).layer(Layer.flyingUnitLow),

    /**
     * 灵魂转移连线 (64f) —— LineFx.monolithSoulTransfer。
     *
     * <p>data 携带容器终点 {@link Position}。灵魂单位 join 容器成功时，
     * 一串光尘沿直线飞向容器，末端爆出一枚发光星芒。</p>
     */
    monolithSoulTransfer = new Effect(64f, e -> {
        if(!(e.data instanceof Position data)) return;

        Tmp.v1.set(data).sub(e.x, e.y).scl(e.fin(Interp.pow2In)).add(e.x, e.y);

        color(UnityPal.monolithDark, UnityPal.monolith, e.fslope());
        randLenVectors(e.id, 5, Interp.pow3Out.apply(e.fslope()) * 8f, 360f, 0f, 8f, (x, y) ->
            Fill.circle(Tmp.v1.x + x, Tmp.v1.y + y, 0.5f + e.fslope() * 2.7f)
        );

        float size = e.fin(Interp.pow10Out) * e.foutpowdown();

        color(UnityPal.monolith);
        Fill.circle(Tmp.v1.x, Tmp.v1.y, size * 4.8f);

        color(UnityPal.monolithLight);
        for(int i = 0; i < 4; i++){
            Drawf.tri(Tmp.v1.x, Tmp.v1.y, size * 6.4f, size * 27f, e.rotation + 90f * i + e.finpow() * 45f * Mathf.sign(e.id % 2 == 0));
        }
    });
}
