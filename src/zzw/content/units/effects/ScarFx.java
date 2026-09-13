package zzw.content.units.effects;

import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import zzw.content.graphics.UnityPal;

/**
 * PU132 Scar 系列特效移植版
 * 包含 scarRailShoot, scarRailHit, scarRailTrail, scarHitSmall, coloredHitSmall, falseLightning
 * 参考: PU132 ShootFx.java, HitFx.java, UnityFx.java
 *
 * <p>★ v158 适配: PU132 用静态导入的 stroke/circle/line/lineAngle 在 arc v158.1
 * 中归属 {@link Lines} / {@link Fill}, randLenVectors 归属 {@link Angles}, 已逐一替换。</p>
 */
public class ScarFx {

    /**
     * Scar 磁轨炮射击特效 (24tick)
     * PU132 ShootFx.scarRailShoot
     * - 0-10tick: 白色到浅灰色渐变的圆形脉冲
     * - 全程: 两侧红色三角 + 白色内三角
     */
    public static final Effect scarRailShoot = new Effect(24f, e -> {
        e.scaled(10f, b -> {
            Draw.color(Color.white, Color.lightGray, b.fin());
            Lines.stroke(b.fout() * 3f + 0.2f);
            Lines.circle(b.x, b.y, b.fin() * 50f);
        });
        for(int i = 0; i < 2; i++){
            int sign = Mathf.signs[i];
            Draw.color(UnityPal.scarColor);
            Drawf.tri(e.x, e.y, 13 * e.fout(), 85f, e.rotation + 90f * sign);
            Draw.color(Color.white);
            Drawf.tri(e.x, e.y, Math.max(13 * e.fout() - 4f, 0f), 81f, e.rotation + 90f * sign);
        }
    });

    /**
     * Scar 磁轨炮命中特效 (18tick)
     * PU132 HitFx.scarRailHit
     * - 两侧红色三角 + 白色内三角
     */
    public static final Effect scarRailHit = new Effect(18f, e -> {
        for(int i = 0; i < 2; i++){
            int sign = Mathf.signs[i];
            Draw.color(UnityPal.scarColor);
            Drawf.tri(e.x, e.y, 10f * e.fout(), 60f, e.rotation + 90f + 90f * sign);
            Draw.color(Color.white);
            Drawf.tri(e.x, e.y, Math.max(10 * e.fout() - 4f, 0f), 56f, e.rotation + 90f + 90f * sign);
        }
    });

    /**
     * Scar 磁轨炮拖尾特效 (16tick)
     * PU132 UnityFx.scarRailTrail
     * - 两侧红色三角 + 白色内三角
     */
    public static final Effect scarRailTrail = new Effect(16f, e -> {
        for(int i = 0; i < 2; i++){
            int sign = Mathf.signs[i];
            Draw.color(UnityPal.scarColor);
            Drawf.tri(e.x, e.y, 10f * e.fout(), 24f, e.rotation + 90f + 90f * sign);
            Draw.color(Color.white);
            Drawf.tri(e.x, e.y, Math.max(10f * e.fout() - 4f, 0f), 20f, e.rotation + 90f + 90f * sign);
        }
    });

    /**
     * Scar 小型命中特效 (14tick)
     * PU132 HitFx.scarHitSmall
     * - 0-7tick: 白色到scarColor渐变的圆形脉冲
     * - 全程: 5条射线从中心向外发射
     */
    public static final Effect scarHitSmall = new Effect(14f, e -> {
        Draw.color(Color.white, UnityPal.scarColor, e.fin());
        e.scaled(7f, s -> {
            Lines.stroke(0.5f + s.fout());
            Lines.circle(e.x, e.y, s.fin() * 5f);
        });
        Lines.stroke(0.5f + e.fout());
        Angles.randLenVectors(e.id, 5, e.fin() * 15f, (x, y) -> Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 3f + 1f));
    });

    /**
     * 彩色小型命中特效 (14tick)
     * PU132 HitFx.coloredHitSmall
     * - 0-7tick: 白色到指定颜色渐变的圆形脉冲
     * - 全程: 5条射线从中心向外发射
     */
    public static final Effect coloredHitSmall = new Effect(14f, e -> {
        Draw.color(Color.white, e.color, e.fin());
        e.scaled(7f, s -> {
            Lines.stroke(0.5f + s.fout());
            Lines.circle(e.x, e.y, s.fin() * 5f);
        });
        Lines.stroke(0.5f + e.fout());
        Angles.randLenVectors(e.id, 5, e.fin() * 15f, (x, y) -> Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 3f + 1f));
    });

    /**
     * 彩色大型命中特效 (21tick)
     * PU132 HitFx.coloredHitLarge
     * - 0-8tick: 白色到指定颜色渐变的圆形脉冲 (更大)
     * - 全程: 6条射线朝弹道反方向 45° 扇形发射
     */
    public static final Effect coloredHitLarge = new Effect(21f, e -> {
        Draw.color(Color.white, e.color, e.fin());
        e.scaled(8f, s -> {
            Lines.stroke(0.5f + s.fout());
            Lines.circle(e.x, e.y, s.fin() * 11f);
        });
        Lines.stroke(0.5f + e.fout());
        Angles.randLenVectors(e.id, 6, e.fin() * 35f, e.rotation + 180f, 45f, (x, y) -> Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 7f + 1f));
    });

    /**
     * 假闪电特效 (10tick, 500裁剪半径)
     * PU132 UnityFx.falseLightning
     * - 基于长度生成分段闪电效果
     * - 每段都有随机偏移，模拟真实闪电
     */
    public static final Effect falseLightning = new Effect(10f, 500f, e -> {
        if(!(e.data instanceof Float length)) return;
        int lenInt = Mathf.round(length / 8f);
        Lines.stroke(3f * e.fout());
        Draw.color(e.color, Color.white, e.fin());

        for(int i = 0; i < lenInt; i++){
            float offsetXA = i == 0 ? 0 : Mathf.randomSeed(e.id + i * 6413L, -4.5f, 4.5f);
            float offsetYA = length / lenInt * i;
            int j = i + 1;
            float offsetXB = j == lenInt ? 0 : Mathf.randomSeed(e.id + j * 6413L, -4.5f, 4.5f);
            float offsetYB = length / lenInt * j;

            Tmp.v1.trns(e.rotation, offsetYA, offsetXA);
            Tmp.v1.add(e.x, e.y);
            Tmp.v2.trns(e.rotation, offsetYB, offsetXB);
            Tmp.v2.add(e.x, e.y);
            Lines.line(Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y, false);
            Fill.circle(Tmp.v1.x, Tmp.v1.y, Lines.getStroke() / 2f);
        }
    });
}
