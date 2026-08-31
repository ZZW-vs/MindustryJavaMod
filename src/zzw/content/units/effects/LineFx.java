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
     * Monolith 灵魂吸收 (32f) —— 灵魂从目标 (data Position) 飘向吸收者 (e.x/y)。
     *
     * <p>轨迹合成 (逐步):</p>
     * <ol>
     *   <li>v1: 垂直于连线的固定侧偏 (种子随机 ±3), 按 pow3Out(fslope)
     *       先甩出后收回 —— 起始 "挣脱感";</li>
     *   <li>v2: 以种子角均匀外扩 (pow4Out);</li>
     *   <li>v3: 起点→终点连线插值 (pow4In, 起步快末段慢) + v2 + v1
     *       合成实际位置, 即 "螺旋收拢" 轨迹。</li>
     * </ol>
     *
     * <p>绘制: 加色混合下画黑→monolithDark 渐变的实心圆,
     * 再叠一层 0.67 透明度的 circle-shadow 阴影圆放大轮廓,
     * 图层位于 flyingUnitLow 之下 (压在单位下)。</p>
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
     * Monolith 灵魂转移 (64f) —— 灵魂沿连线飞行并放大成形。
     *
     * <p>渲染步骤:</p>
     * <ol>
     *   <li>锚点 v1 = 起点→终点 pow2In 插值 (先慢后快);</li>
     *   <li>5 个环绕粒子 (7 参版 randLenVectors, 360°/0 锥), 半径随
     *       pow3Out(fslope) 先散后聚;</li>
     *   <li>monolith 实心圆 ×4.8 + monolithLight 四片旋转三角 (±90° 交错,
     *       末段 45° 顺/逆转), 三角尺寸 pow10Out×foutpowdown 先暴涨后收束。</li>
     * </ol>
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
