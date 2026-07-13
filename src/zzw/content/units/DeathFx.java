package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.graphics.Layer;

/**
 * 死亡特效集合 (移植自 PU132 DeathFx, 简化版)
 * - 用 v158 标准 Effect API 替代自定义 GLSL
 * - 黑红色爆炸粒子
 */
public final class DeathFx {
    public static final Effect

    monolithSoulDeath = new Effect(64f, e -> {
        Color c1 = Color.valueOf("8a4dff");
        Color c2 = Color.valueOf("4a1d8a");
        Draw.color(c1, c2, e.fin());
        float radius = 0.5f + e.fout() * 2.5f;
        for (int i = 0; i < 27; i++) {
            Tmp.v1.trns(e.id * 1.0f + i * 24f, e.finpow() * 56f);
            Fill.circle(e.x + Tmp.v1.x, e.y + Tmp.v1.y, radius);
        }

        e.scaled(48f, i -> {
            Draw.color(Color.valueOf("b9a0ff"));
            Draw.alpha(i.fout());
            Fill.circle(e.x, e.y, 2f);
            Draw.color();
        });
    }),

    monolithSoulCrack = new Effect(20f, e -> {
        // 简化: 不做 wreckRegions 散布, 只画一个暗色圆环
        Draw.color(Color.valueOf("4a1d8a"));
        Draw.alpha(e.foutpowdown());
        Fill.circle(e.x, e.y, e.finpow() * 24f);
        Draw.color();
    }).layer(Layer.flyingUnit),

    monolithSoulJoin = new Effect(72f, e -> {
        Draw.color(Color.valueOf("8a4dff"));
        Draw.alpha(e.foutpowdown());
        float t = e.foutpowdown();
        // 画一个旋转的多边形
        for (int i = 0; i < 6; i++) {
            float angle = i * 60f + e.rotation;
            Tmp.v1.trns(angle, 25f * t);
            Fill.circle(e.x + Tmp.v1.x, e.y + Tmp.v1.y, 2f);
        }
        Draw.color();
    }).layer(Layer.flyingUnit);
}
