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
    });
}
