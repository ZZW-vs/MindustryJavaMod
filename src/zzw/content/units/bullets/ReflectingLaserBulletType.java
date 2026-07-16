package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.struct.IntSet;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Lightning;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Hitboxc;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

/**
 * 反射激光 (PU_V8 ReflectingLaserBulletType 移植版)
 * - 命中后反射到附近敌方 (最多 reflections 次)
 * - 反射时产生闪电
 * 简化: 用 v158 Units.closestTarget + Intersector 替代 Utils.collideLineRawEnemy
 *       移除复杂的 reflected Set (反射只通过角度过滤)
 *       Drawf.light 使用单 team 参数版 (v158 API)
 * 参考: PU_V8 main/src/unity/entities/bullet/laser/ReflectingLaserBulletType.java
 */
public class ReflectingLaserBulletType extends BulletType {
    private static int p = 0;
    private final static Vec2 vec = new Vec2();

    public Color[] colors = {};
    public float length = 500f, reflectLength = 200f;
    public float width = 65f, lengthFalloff = 0.5f;
    public float reflectRange = 80f, reflectLoss = 0.75f;
    public float minimumTargetLength = 70f;
    public int reflections = 5;
    public int reflectLightning = 10;

    public ReflectingLaserBulletType(float damage) {
        super(0f, damage);
        lifetime = 16f;
        impact = true;
        keepVelocity = false;
        collides = false;
        pierce = true;
        hittable = false;
        absorbable = false;
    }

    @Override
    public void init() {
        super.init();
        drawSize = length * 2f;
        despawnHit = false;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);

        b.fdata = b.data == null ? length : reflectLength;
        if (b.data == null) {
            ReflectLaserData data = new ReflectLaserData();
            data.reflected = new IntSet();
            b.data = data;
        }

        if (b.data instanceof ReflectLaserData) {
            ReflectLaserData data = (ReflectLaserData) b.data;
            float len = b.fdata;
            Vec2 pos = vec.trns(b.rotation(), len).add(b);
            p = 0;

            // 简化版碰撞检测: 沿激光线检测敌方单位和建筑
            float[] hitXY = {pos.x, pos.y};
            boolean[] hit = {false};

            // 检测单位
            mindustry.entities.Units.nearbyEnemies(b.team, b.x, b.y, len + 50f, unit -> {
                if (hit[0]) return;
                if (!unit.hittable()) return;
                if (Intersector.distanceSegmentPoint(b.x, b.y, pos.x, pos.y, unit.x, unit.y) > width / 3f + unit.hitSize / 2f)
                    return;
                hit[0] = true;
                hitXY[0] = unit.x;
                hitXY[1] = unit.y;
                hitEntity(b, unit, unit.health);
                hit(b, unit.x, unit.y);
                if (!b.within(unit, minimumTargetLength)) p++;
            });

            // 检测建筑 (如果没有命中单位)
            if (!hit[0]) {
                mindustry.Vars.indexer.eachBlock(null, b.x, b.y, len + 50f,
                        build -> build.team != b.team,
                        build -> {
                            if (hit[0]) return;
                            float size = build.block.size * mindustry.Vars.tilesize / 2f;
                            if (Intersector.distanceSegmentPoint(b.x, b.y, pos.x, pos.y, build.x, build.y) > width / 3f + size)
                                return;
                            hit[0] = true;
                            hitXY[0] = build.x;
                            hitXY[1] = build.y;
                            hit(b, build.x, build.y);
                            if (build.block.absorbLasers || !b.within(build, minimumTargetLength)) p++;
                        });
            }

            // 治疗友军 (沿激光线)
            mindustry.entities.Units.nearby(b.team, b.x, b.y, len + 50f, other -> {
                if (other == b.owner) return;
                if (Intersector.distanceSegmentPoint(b.x, b.y, pos.x, pos.y, other.x, other.y) > width / 3f + other.hitSize / 2f)
                    return;
                other.heal(other.maxHealth * (healPercent / 100f));
            });

            if (hit[0]) {
                data.hitX = hitXY[0];
                data.hitY = hitXY[1];
                data.hit = true;
                Vec2 hitPt = Intersector.nearestSegmentPoint(b.x, b.y, pos.x, pos.y, data.hitX, data.hitY, Tmp.v2);
                float hx = hitPt.x;
                float hy = hitPt.y;
                b.fdata = b.dst(hx, hy);

                if (data.reflect < reflections) {
                    float delay = lifetime * 0.2f;
                    Posc n = mindustry.entities.Units.closestTarget(b.team, hx, hy, reflectLength,
                            unit -> unit.isValid() && valid(hx, hy, b.rotation(), data.lastRot, unit, data.reflected),
                            building -> valid(hx, hy, b.rotation(), data.lastRot, building, data.reflected));

                    float nextAngle = n == null ? b.rotation() + 180f + Mathf.range(reflectRange / 2f, reflectRange) : n.angleTo(hx, hy) + 180f;
                    ReflectLaserData d = new ReflectLaserData();
                    d.reflect = data.reflect + 1;
                    d.lastRot = (b.rotation() + 360f) % 360f;
                    d.reflected = data.reflected;
                    Time.run(delay, () -> {
                        if (b.isAdded() && b.type == this) {
                            hitReflect(b, hx, hy);
                            createAlt(b, hx, hy, nextAngle, reflectLength, d);
                        }
                    });
                }
            }
        }
    }

    boolean valid(float x, float y, float angle, float angle2, Posc pos, IntSet collided) {
        float angleTo = pos.angleTo(x, y);
        return !pos.within(x, y, minimumTargetLength)
                && Angles.within(angleTo, angle, reflectRange)
                && (angle2 <= -1f || !Angles.within(angleTo + 180f, angle2, reflectRange / 2f))
                && (collided.add(pos.id()));
    }

    void hitReflect(Bullet b, float x, float y) {
        for (int i = 0; i < reflectLightning; i++) {
            Lightning.create(b, lightningColor, lightningDamage < 0 ? damage : lightningDamage, x, y,
                    b.rotation() + Mathf.range(lightningCone / 2) + lightningAngle,
                    lightningLength + Mathf.random(lightningLengthRand));
        }
    }

    @Override
    public void draw(Bullet b) {
        if (b.data instanceof ReflectLaserData) {
            ReflectLaserData data = (ReflectLaserData) b.data;
            boolean hit = data.hit && data.reflect < reflections;
            float len = b.fdata;
            float f = Mathf.curve(b.fin(), 0f, 0.2f);
            float cl = len * f;
            float cw = width;
            Vec2 p = Tmp.v1.trns(b.rotation(), cl).add(b);

            Lines.line(b.x, b.y, p.x, p.y);
            for (Color color : colors) {
                Draw.color(color);
                Lines.stroke(cw * b.fout());
                Lines.line(b.x, b.y, p.x, p.y, false);

                if (!hit) {
                    Drawf.tri(p.x, p.y, Lines.getStroke() * 1.22f, cw * 2 + width / 2f, b.rotation());
                } else {
                    Fill.circle(p.x, p.y, cw * b.fout() / 2f);
                }

                Fill.circle(b.x, b.y, cw * b.fout());

                cw *= lengthFalloff;
            }

            Tmp.v2.set(p).sub(b).scl(1.1f).add(b);
            Drawf.light(b.x, b.y, Tmp.v2.x, Tmp.v2.y, width * 1.4f * b.fout(), colors[0], 0.6f);
        }
    }

    @Override
    public void drawLight(Bullet b) {
        // 不绘制光源 (在 draw 中处理)
    }

    void createAlt(Bullet s, float x, float y, float rotation, float length, ReflectLaserData data) {
        Bullet b = Bullet.create();
        b.x = x;
        b.y = y;
        b.type = this;
        b.owner = s.owner;
        b.team = s.team;
        b.time = 0f;
        b.lifetime = lifetime;
        b.initVel(rotation, 0f);
        b.fdata = length;
        b.data = data;
        b.hitSize = hitSize;
        b.damage = s.damage * reflectLoss;
        b.add();
    }

    static class ReflectLaserData {
        int reflect = 0;
        boolean hit = false;
        float hitX, hitY, lastRot = -1f;
        IntSet reflected;
    }
}
