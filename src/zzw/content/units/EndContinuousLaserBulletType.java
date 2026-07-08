package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Damage;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;
import mindustry.entities.bullet.BasicBulletType;

public class EndContinuousLaserBulletType extends BasicBulletType {
    public float length = 220f;
    public float shake = 1f;
    public float fadeTime = 16f;
    public float lightStroke = 40f;
    public float spaceMag = 35f;
    public Color[] colors = {Color.valueOf("ec745855"), Color.valueOf("ec7458aa"), Color.valueOf("ff9c5a"), Color.white};
    public float[] tscales = {1f, 0.7f, 0.5f, 0.2f};
    public float[] strokes = {2f, 1.5f, 1f, 0.3f};
    public float[] lenscales = {1f, 1.12f, 1.15f, 1.17f};
    public float width = 9f, oscScl = 0.8f, oscMag = 1.5f;
    public boolean largeHit = true;

    public float lightningChance = 0f;
    public float lightningDamage = 0f;
    public int lightningLength = 0;
    public int lightningLengthRand = 0;

    public EndContinuousLaserBulletType(float damage) {
        this.damage = damage;
        this.speed = 0f;
        this.hitEffect = mindustry.content.Fx.hitBeam;
        this.despawnEffect = mindustry.content.Fx.none;
        this.hitSize = 4;
        this.drawSize = 420f;
        this.lifetime = 16f;
        this.hitColor = colors[2];
        this.lightColor = Color.orange;
        this.impact = true;
        this.pierce = true;
        this.hittable = false;
        this.absorbable = false;
    }

    @Override
    public float continuousDamage() {
        return damage / 5f * 60f;
    }

    @Override
    public float range() {
        return Math.max(length, maxRange);
    }

    @Override
    public void init() {
        super.init();
        drawSize = Math.max(drawSize, length * 2f);
    }

    @Override
    public void update(Bullet b) {
        if (b.timer(1, 5f)) {
            b.fdata = length;

            Vec2 v = Tmp.v1.trns(b.rotation(), length).add(b);
            float w = largeHit ? 15f : 3f;

            float laserX1 = b.x, laserY1 = b.y, laserX2 = v.x, laserY2 = v.y;
            float searchRange = length + w;
            float cx = (laserX1 + laserX2) * 0.5f, cy = (laserY1 + laserY2) * 0.5f;

            mindustry.util.Tmp.rect.setCentered(cx, cy, searchRange * 2f, searchRange * 2f);
            Units.nearbyEnemies(b.team, mindustry.util.Tmp.rect, unit -> {
                if (!unit.hittable() || !unit.checkTarget(collidesAir, collidesGround)) return;
                if (arc.math.geom.Intersector.distanceSegmentPoint(laserX1, laserY1, laserX2, laserY2, unit.x, unit.y) > w) return;
                unit.damage(damage);
                if (b.owner instanceof Healthc h) {
                    h.heal(damage * 0.1f);
                }
            });

            Vars.indexer.eachBlock(null, cx, cy, searchRange,
                    build -> build.team != b.team && build.health > 0,
                    build -> {
                        if (arc.math.geom.Intersector.distanceSegmentPoint(laserX1, laserY1, laserX2, laserY2, build.x, build.y) > w) return;
                        build.damage(damage);
                    });
        }
    }

    @Override
    public void draw(Bullet b) {
        float realLen = b.fdata > 0 ? b.fdata : length;
        float width = this.width;

        Draw.color(colors[0]);
        Draw.alpha(0.5f);

        for (int i = 0; i < strokes.length; i++) {
            Draw.color(colors[i]);
            Draw.alpha(0.35f + i * 0.15f);
            Lines.stroke(width * strokes[i]);
            Lines.lineAngle(b.x, b.y, b.rotation(), realLen * lenscales[i]);
        }

        Draw.color(colors[2]);
        Lines.stroke(width * 0.5f);
        Lines.lineAngle(b.x, b.y, b.rotation(), realLen);

        Draw.reset();
    }
}