package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;

/**
 * 切割激光 (移植自 PU132 EndCutterLaserBulletType, 简化版)
 * - 持续加速推进激光
 * - 命中后给低血量单位直接秒杀
 * - 简化: 不做"切两半"特效, 只保留穿透+致死
 * - maxLength=1000, lifetime=60f
 */
public class EndCutterLaserBulletType extends AntiCheatBulletTypeBase {
    public float maxLength = 1000f;
    public float laserSpeed = 15f;
    public float accel = 25f;
    public float width = 12f;
    public float fadeTime = 60f;
    public float fadeInTime = 8f;
    public Color[] colors = {
        new Color(0xf5303690), new Color(0xf53036ff), new Color(0xff786eff), Color.white
    };

    public EndCutterLaserBulletType(float damage) {
        super(0.005f, damage);
        despawnEffect = Fx.none;
        collides = false;
        pierce = true;
        hittable = false;
        absorbable = false;
        lifetime = 60f;
    }

    @Override
    public void init() {
        super.init();
        drawSize = maxLength * 2f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.fdata(0f);
        b.data(new CutterData());
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        // 加速推进
        if (b.data() instanceof CutterData data) {
            data.restartTime += Time.delta;
            if (data.restartTime >= 5f) {
                data.velocity = Mathf.clamp((data.velocityTime / accel) + data.velocity, 0f, laserSpeed);
                b.fdata(Mathf.clamp(b.fdata() + (data.velocity * Time.delta), 0f, maxLength));
                data.velocityTime += Time.delta;
            }
        }
        // 命中检测
        if (b.timer(1, 5f)) {
            Tmp.v1.trns(b.rotation(), b.fdata()).add(b);
            Units.nearbyEnemies(b.team, b.x(), b.y(), b.fdata() + 50f, unit -> {
                if (!unit.hittable() || !unit.checkTarget(collidesAir, collidesGround)) return;
                if (arc.math.geom.Intersector.distanceSegmentPoint(b.x(), b.y(), Tmp.v1.x, Tmp.v1.y, unit.x, unit.y) > width * 2f) return;
                hitUnitAntiCheat(b, unit);
                // 切割效果: 大单位低血量直接秒杀
                if (unit.hitSize() >= 30f || unit.health() <= damage * 2f) {
                    unit.damagePierce(unit.health() * 2f);
                    Fx.hitLancer.at(unit.x(), unit.y(), b.rotation(), Color.white);
                }
            });
            Vars.indexer.allBuildings(b.x(), b.y(), b.fdata() + 50f, build -> {
                if (build.team == b.team || build.health <= 0f) return;
                if (arc.math.geom.Intersector.distanceSegmentPoint(b.x(), b.y(), Tmp.v1.x, Tmp.v1.y, build.x, build.y) > width * 2f) return;
                hitBuildingAntiCheat(b, build);
            });
        }
    }

    @Override
    public void draw(Bullet b) {
        float fade = Mathf.clamp(b.time > b.lifetime - fadeTime ? 1f - (b.time - (lifetime - fadeTime)) / fadeTime : 1f) * Mathf.clamp(b.time / fadeInTime);
        float tipHeight = width / 2f;
        Lines.lineAngle(b.x(), b.y(), b.rotation(), b.fdata());
        for (int i = 0; i < colors.length; i++) {
            float f = (colors.length - i) / (float) colors.length;
            float w = f * (width + Mathf.absin(Time.time + (i * 1.4f), 1.1f, width / 4)) * fade;
            Tmp.v2.trns(b.rotation(), b.fdata() - tipHeight).add(b);
            Tmp.v1.trns(b.rotation(), width * 2f).add(Tmp.v2);
            Draw.color(colors[i]);
            Fill.circle(b.x(), b.y(), w / 2f);
            Lines.stroke(w);
            Lines.line(b.x(), b.y(), Tmp.v2.x, Tmp.v2.y, false);
            for (int s : Mathf.signs) {
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

    static class CutterData {
        float velocity = 0f;
        float velocityTime = 0f;
        float restartTime = 0f;
    }
}
