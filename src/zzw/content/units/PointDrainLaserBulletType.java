package zzw.content.units;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.graphics.*;

public class PointDrainLaserBulletType extends BulletType {
    public float drainPercent = 0.1f;
    public float maxLength = 140f;
    public float width = 6f;
    public float area = 9f;
    public float fadeTime = 16f;
    public Color backColor = Color.valueOf("54de3b");
    public Color frontColor = Color.valueOf("a3f080");

    private static final Vec2 tmpVec = new Vec2();
    private static final Rect tmpRect = new Rect();

    public PointDrainLaserBulletType(float damage) {
        super(0.001f, damage);
        despawnEffect = Fx.none;
        hitSize = 4f;
        drawSize = 420f;
        keepVelocity = false;
        collides = false;
        pierce = true;
        hittable = false;
        absorbable = false;
        collidesGround = true;
        collidesTiles = true;
        collidesAir = false;
        lifetime = 10f * 60f;
    }

    @Override
    public void init() {
        super.init();
        drawSize = maxLength * 2f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = new DrainLaserData();
        ((DrainLaserData) b.data).pos.set(b);
    }

    @Override
    public void update(Bullet b) {
        DrainLaserData dld = (DrainLaserData) b.data;
        if (dld == null) return;

        final Healthc hOwner = b.owner instanceof Healthc ? (Healthc) b.owner : null;

        if (b.owner instanceof Unit) {
            Unit e = (Unit) b.owner;
            dld.pos.trns(b.rotation(), e.dst(e.aimX, e.aimY)).limit(maxLength);
        } else {
            dld.pos.trns(b.rotation(), maxLength);
        }

        float length = Damage.findLaserLength(b, dld.pos.len());
        dld.pos.setLength(length).add(b);

        dld.trail.update(dld.pos.x, dld.pos.y);

        if (b.timer(1, 5f)) {
            if (hOwner != null) {
                // ★ 沿激光线段扫描所有命中目标, 每个目标都回血一次 (参考 PU132 collideLineRawEnemy)
                //   v154.3 没有 collideLineRawEnemy, 手动用 Units.nearbyEnemies + 矩形范围 + 距离检查实现
                //   rect 沿激光线段扩展, 覆盖所有可能命中的目标
                final float laserX1 = b.x, laserY1 = b.y, laserX2 = dld.pos.x, laserY2 = dld.pos.y;
                float cx = (laserX1 + laserX2) * 0.5f, cy = (laserY1 + laserY2) * 0.5f;
                float searchRange = Math.max(dld.pos.len(), area * 2f) + area;
                tmpRect.setCentered(cx, cy, searchRange * 2f, searchRange * 2f);

                Units.nearbyEnemies(b.team, tmpRect, unit -> {
                    if (!unit.hittable() || !unit.checkTarget(collidesAir, collidesGround)) return;
                    // 距离激光线段 < area
                    if (arc.math.geom.Intersector.distanceSegmentPoint(laserX1, laserY1, laserX2, laserY2, unit.x, unit.y) > area) return;
                    unit.damage(damage);
                    hOwner.heal(damage * drainPercent);
                });

                Vars.indexer.eachBlock(null, cx, cy, searchRange,
                    build -> build.team != b.team && build.health > 0,
                    build -> {
                        if (arc.math.geom.Intersector.distanceSegmentPoint(laserX1, laserY1, laserX2, laserY2, build.x, build.y) > area) return;
                        build.damage(damage * buildingDamageMultiplier);
                        hOwner.heal(damage * drainPercent);
                    });
            } else {
                final float laserX1 = b.x, laserY1 = b.y, laserX2 = dld.pos.x, laserY2 = dld.pos.y;
                float cx = (laserX1 + laserX2) * 0.5f, cy = (laserY1 + laserY2) * 0.5f;
                float searchRange = Math.max(dld.pos.len(), area * 2f) + area;
                tmpRect.setCentered(cx, cy, searchRange * 2f, searchRange * 2f);

                Units.nearbyEnemies(b.team, tmpRect, unit -> {
                    if (!unit.hittable() || !unit.checkTarget(collidesAir, collidesGround)) return;
                    if (arc.math.geom.Intersector.distanceSegmentPoint(laserX1, laserY1, laserX2, laserY2, unit.x, unit.y) > area) return;
                    unit.damage(damage);
                });

                Vars.indexer.eachBlock(null, cx, cy, searchRange,
                    build -> build.team != b.team && build.health > 0,
                    build -> {
                        if (arc.math.geom.Intersector.distanceSegmentPoint(laserX1, laserY1, laserX2, laserY2, build.x, build.y) > area) return;
                        build.damage(damage * buildingDamageMultiplier);
                    });
            }

            if (knockback != 0) {
                Units.nearbyEnemies(b.team, tmpRect.setCentered(dld.pos.x, dld.pos.y, area * 2f),
                    unit -> {
                        if (unit.hittable() && unit.checkTarget(collidesAir, collidesGround)) {
                            tmpVec.trns(b.rotation(), knockback * 80f);
                            unit.impulse(tmpVec);
                        }
                    });
            }
        }
    }

    @Override
    public void draw(Bullet b) {
        DrainLaserData dld = (DrainLaserData) b.data;
        if (dld == null) return;

        float fade = Mathf.clamp(b.time > b.lifetime - fadeTime ? 1f - (b.time - (b.lifetime - fadeTime)) / fadeTime : 1f)
            * Mathf.clamp(b.time / fadeTime);

        Draw.color(backColor);
        dld.trail.draw(backColor, fade * area / 2f);

        for (int i = 0; i < 2; i++) {
            float size = Math.max((width * fade) - (i * width / 2f), 0f);
            Draw.color(i == 0 ? backColor : frontColor);
            Fill.circle(b.x, b.y, size / 2f);
            Lines.stroke(size);
            Lines.line(b.x, b.y, dld.pos.x, dld.pos.y, false);
            Fill.circle(dld.pos.x, dld.pos.y, Math.max((area * fade) - (i * area / 2f), 0f));
        }

        Drawf.light(b.x, b.y, dld.pos.x, dld.pos.y, fade * width * 2f, backColor, 0.5f);
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b) {}

    public float range() {
        return maxRange;
    }

    private static class DrainLaserData {
        Trail trail = new Trail(6);
        Vec2 pos = new Vec2();
    }
}