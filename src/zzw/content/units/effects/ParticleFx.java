package zzw.content.units.effects;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.math.Interp;
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
