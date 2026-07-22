package zzw.content.exp;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;

/**
 * PU_V8 UnityFx 简化版 (仅保留经验系统所需特效)
 * 参考: PU_V8 main/src/unity/content/UnityFx.java L104-146, L819-828
 */
public class UnityFx {

    public static final Effect
        expPoof = new Effect(60f, e -> {
            Draw.color(Pal.accent, UnityPal.exp, e.fin());
            Angles.randLenVectors(e.id, 9, 1f + 30f * e.finpow(), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 1.7f * e.fout());
                spark(e.x + x, e.y + y, 5f, (5 + 1.5f * Mathf.sin(arc.util.Time.time * 0.12f + e.id * 4f)) * e.fout(), e.finpow() * 90f + e.id * 69f);
            });
        }),

        expShineRegion = new Effect(25f, e -> {
            Draw.color();
            Tmp.c1.set(Pal.accent).lerp(UnityPal.exp, e.fin());
            Draw.mixcol(Tmp.c1, 1f);
            Draw.alpha(1f - e.fin() * e.fin());
            if(e.data instanceof TextureRegion region){
                Draw.rect(region, e.x, e.y, e.rotation);
            }
        }),

        orbDespawn = new Effect(15f, e -> {
            Draw.color(UnityPal.exp);
            Lines.stroke(e.fout() * 1.2f + 0.01f);
            Lines.circle(e.x, e.y, 4f * e.finpow());
        }),

        expLaser = new Effect(15f, e -> {
            if(e.data instanceof mindustry.gen.Building b && !b.dead){
                Tmp.v2.set(b);
                Tmp.v1.set(Tmp.v2).sub(e.x, e.y).nor().scl(mindustry.Vars.tilesize / 2f);
                Tmp.v2.sub(Tmp.v1);
                Tmp.v1.add(e.x, e.y);
                Drawf.laser(Core.atlas.find("create-exp-laser"), Core.atlas.find("create-exp-laser-end"), Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y, 0.4f * e.fout());
            }
        }),

        placeShine = new Effect(30f, e -> {
            Draw.color(e.color);
            Lines.stroke(e.fout());
            Lines.square(e.x, e.y, e.rotation / 2f + e.fin() * 3f);
            spark(e.x, e.y, 25f, 15f * e.fout(), e.finpow() * 90f);
        }),

        expAbsorb = new Effect(15f, e -> {
            Lines.stroke(e.fout() * 1.5f);
            Draw.color(UnityPal.exp);
            Lines.circle(e.x, e.y, e.fin() * 2.5f + 1f);
        }),

        expDespawn = new Effect(15f, e -> {
            Draw.color(UnityPal.exp);
            Angles.randLenVectors(e.id, 7, 2f + 5 * e.fin(), (x, y) -> Fill.circle(e.x + x, e.y + y, e.fout()));
        }),

        // ===== Supernova 专用特效 (PU_V8 UnityFx L1130-1183 完整移植) =====
        // 充能开始特效: 数据为 Float r, 随机线段向外发散
        supernovaChargeBegin = new Effect(27f, e -> {
            if(e.data instanceof Float data){
                float r = data;
                Angles.randLenVectors(e.id, (int)(2f * r), 1f + 27f * e.fout(), (x, y) -> {
                    Draw.color(Pal.lancerLaser);
                    Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), (1f + e.fslope() * 6f) * r);
                });
            }
        }),

        // 星辰热浪特效: 双圆环扩散
        supernovaStarHeatwave = new Effect(40f, e -> {
            Draw.color(Pal.lancerLaser);
            Lines.stroke(e.fout());
            Lines.circle(e.x, e.y, 110f * e.fin());
            Lines.circle(e.x, e.y, 120f * e.finpow() * 0.6f);
        }),

        // 充能星辰特效: 数据为 Float r, 渐隐圆环
        supernovaChargeStar = new Effect(30f, e -> {
            if(e.data instanceof Float data){
                float r = data;
                Draw.color(Pal.lancerLaser);
                Draw.alpha(e.fin() * 2f * r);
                Lines.circle(e.x, e.y, 150f * Interp.pow2Out.apply(e.fout()) * Mathf.lerp(0.1f, 1f, r));
            }
        }),

        // 星辰衰减特效: 随机小方块渐隐
        supernovaStarDecay = new Effect(56f, e -> Angles.randLenVectors(e.id, 1, 36f * e.finpow(), (x, y) -> {
            Draw.color(Pal.lancerLaser);
            Fill.rect(e.x + x, e.y + y, 2.2f * e.fout(), 2.2f * e.fout(), 45f);
        })),

        // 充能星辰2特效: 数据为 Float r, 随机小圆点扩散
        supernovaChargeStar2 = new Effect(27f, e -> {
            if(e.data instanceof Float data){
                float r = data;
                Angles.randLenVectors(e.id, (int)(3f * r), e.fout() * ((90f + r * 150f) * (0.3f + Mathf.randomSeed(e.id, 0.7f))), (x, y) -> {
                    Draw.color(Pal.lancerLaser);
                    Fill.circle(e.x + x, e.y + y, 2f * e.fin());
                });
            }
        }),

        // 拉拽特效: 数据为 Vec2 (目标位置), e.rotation 为 size
        // ★ v155.4 简化: 原版用 SVec2 (Long 序列化), 这里改用 Vec2 直接传递
        supernovaPullEffect = new Effect(30f, 500f, e -> {
            if(e.data instanceof Vec2 target){
                float size = e.rotation;
                float x = e.x + Mathf.randomSeedRange(e.id, 4f);
                float y = e.y + Mathf.randomSeedRange(e.id + 1, 4f);
                // 从单位位置向目标位置插值
                float px = Mathf.lerp(x, target.x, e.fin());
                float py = Mathf.lerp(y, target.y, e.fin());
                Draw.color(Pal.lancerLaser);
                Fill.circle(px, py, size * (0.5f + e.fslope() * 0.5f));
            }
        });

    /** 绘制4向三角形尖刺 (PU_V8 UnityDrawf.spark) */
    public static void spark(float x, float y, float w, float h, float r){
        for(int i = 0; i < 4; i++){
            Drawf.tri(x, y, w, h, r + 90 * i);
        }
    }
}
