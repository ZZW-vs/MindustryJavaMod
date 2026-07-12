package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

/**
 * 连续奇点激光 (移植自 PU132 ContinuousSingularityLaserBulletType, 简化版)
 * - 持续激光, 推力场, 黑洞
 * - 简化: 跳过推力场, 只保留激光 + 吸引效果
 * - maxLength=1000, lifetime=16f
 */
public class ContinuousSingularityLaserBulletType extends AntiCheatBulletTypeBase {
    public float maxLength = 1000f;
    public float laserSpeed = 15f;
    public float accel = 25f;
    public float width = 12f;
    public float widthReduction = 2f;
    public float fadeTime = 60f;
    public float fadeInTime = 8f;
    public Color[] colors = {
        new Color(0xf5303660), new Color(0xf53036ff), new Color(0xff786eff),
        Color.white, Color.black
    };

    public ContinuousSingularityLaserBulletType(float damage) {
        super(0f, damage);
        despawnEffect = Fx.none;
        collides = false;
        pierce = true;
        impact = true;
        keepVelocity = false;
        hittable = false;
        absorbable = false;
        layer = Layer.flyingUnit + 0.5f;
    }

    @Override
    public void init() {
        super.init();
        drawSize = maxLength * 2f;
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        if (b.fdata() < maxLength && b.timer(0, 5f)) {
            // 加速推进
            b.fdata(Mathf.clamp(b.fdata() + laserSpeed * Time.delta, 0f, maxLength));
        }
        // 周期性检测命中
        if (b.timer(1, 5f)) {
            Tmp.v1.trns(b.rotation(), b.fdata()).add(b);
            Units.nearbyEnemies(b.team, b.x(), b.y(), b.fdata() + 50f, unit -> {
                if (!unit.hittable()) return;
                if (arc.math.geom.Intersector.distanceSegmentPoint(b.x(), b.y(), Tmp.v1.x, Tmp.v1.y, unit.x, unit.y) > width * 2f) return;
                hitUnitAntiCheat(b, unit);
                // 吸引: 给单位一个向激光方向的冲量
                if (unit.hitSize() < 30f) {
                    arc.util.Tmp.v2.set(Tmp.v1).sub(unit).nor().scl(5f);
                    unit.impulse(arc.util.Tmp.v2);
                }
            });
            mindustry.Vars.indexer.eachBlock(null, b.x(), b.y(), b.fdata() + 50f,
                build -> build.team != b.team && build.health > 0,
                build -> {
                    if (arc.math.geom.Intersector.distanceSegmentPoint(b.x(), b.y(), Tmp.v1.x, Tmp.v1.y, build.x, build.y) > width * 2f) return;
                    hitBuildingAntiCheat(b, build);
                });
        }
    }

    @Override
    public void draw(Bullet b) {
        float fadeIn = fadeInTime <= 0f ? 1f : Mathf.clamp(b.time / fadeInTime);
        float fade = Mathf.clamp(b.time > b.lifetime - fadeTime ? 1f - (b.time - (lifetime - fadeTime)) / fadeTime : 1f) * fadeIn;
        float tipHeight = width / 2f;

        Lines.lineAngle(b.x(), b.y(), b.rotation(), b.fdata());
        for (int i = 0; i < colors.length; i++) {
            float f = 1f - ((widthReduction * i) / width);
            float w = f * (width + Mathf.absin(Time.time + (i * 1.4f), 1.1f, width / 4)) * fade;

            Tmp.v2.trns(b.rotation(), b.fdata() - tipHeight).add(b);
            Tmp.v1.trns(b.rotation(), width * 2f).add(Tmp.v2);
            Draw.color(colors[i]);
            Fill.circle(b.x(), b.y(), (w / 2f) * 3f);
            Lines.stroke(w);
            Lines.line(b.x(), b.y(), Tmp.v2.x, Tmp.v2.y, false);
            for (int s : Mathf.signs) {
                Drawf.tri(b.x(), b.y(), w, width * 4f + w, b.rotation() + 90f * s);
                Tmp.v3.trns(b.rotation(), w * -0.7f, w * s);
                Fill.tri(Tmp.v2.x, Tmp.v2.y, Tmp.v1.x, Tmp.v1.y, Tmp.v2.x + Tmp.v3.x, Tmp.v2.y + Tmp.v3.y);
            }
        }
        Tmp.v2.trns(b.rotation(), b.fdata() + tipHeight).add(b);
        Drawf.light(b.x(), b.y(), Tmp.v2.x, Tmp.v2.y, width * 2f, colors[0], 0.5f);
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b) {
    }
}
