package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.math.geom.Geometry;
import arc.math.geom.Intersector;
import arc.math.geom.Rect;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.Vars;

/**
 * PU132 PointBlastLaserBulletType 移植版 (简化) - v158 适配
 *
 * 机制:
 * - 直线激光碰撞检测 (建筑+单位)
 * - 命中点产生范围伤害 (damageRadius)
 * - 多色激光渲染
 *
 * v158 适配:
 * - 使用 Vars.indexer.eachBlock + Geometry.raycastRect 替代 Utils.collideLineRawEnemy
 * - 使用 Units.nearby 替代 Utils.trueEachBlock
 * - Drawf.light 无 Team 参数
 * - 用简化特效替代 SpecialFx.pointBlastLaserEffect
 *
 * 参考: PU_V8 main/src/unity/entities/bullet/laser/PointBlastLaserBulletType.java
 */
public class PointBlastLaserBulletType extends BulletType {
    public float damageRadius = 20f;
    public float auraDamage = 10f;
    public float length = 100f;
    public float width = 12f;
    public float widthReduction = 2f;
    public float auraWidthReduction = 3f;
    public boolean overrideDamage = false;
    public Color[] laserColors = {Color.white};

    private static boolean available = false;
    private static final Seq<Building> hitBuildings = new Seq<>();
    private static final Seq<Unit> hitUnits = new Seq<>();

    public PointBlastLaserBulletType(float damage) {
        speed = 0f;
        this.damage = damage;
        hitEffect = Fx.hitLancer;
        despawnEffect = Fx.none;
        shootEffect = Fx.none;
        smokeEffect = Fx.none;
        hitSize = 4f;
        lifetime = 16f;
        keepVelocity = false;
        collides = false;
        pierce = true;
        hittable = false;
        absorbable = false;
    }

    public float estimateDPS() {
        return (damage * (lifetime / 2f) / 5f * 3f) + auraDamage;
    }

    @Override
    protected float calculateRange() {
        return length;
    }

    @Override
    public void init() {
        super.init();
        drawSize = Math.max(drawSize, length + damageRadius * 2f);
    }

    public void handleUnit(Bullet b, Unit unit, float initialHealth) {
    }

    public void handleBuilding(Bullet b, Building build, float initialHealth) {
        build.damage(auraDamage);
    }

    @Override
    public void init(Bullet b) {
        super.init(b);

        b.fdata = length;
        Tmp.v1.trns(b.rotation(), length);
        Tmp.v1.add(b);
        available = false;

        // 检测建筑碰撞 (替代 Utils.collideLineRawEnemy)
        Vars.indexer.eachBlock(null, b.x, b.y, length,
            build -> build.team != b.team && build.health > 0,
            build -> {
                Rect rect = Tmp.r2.setCentered(build.x, build.y, build.block.size * Vars.tilesize);
                Vec2 hit = Geometry.raycastRect(b.x, b.y, Tmp.v1.x, Tmp.v1.y, rect);
                if (hit != null && !available) {
                    build.damage(b.damage);
                    available = true;
                    b.fdata = b.dst(build);
                    Tmp.v2.set(hit);
                }
            });

        // 检测单位碰撞
        if (!available) {
            Rect searchRect = Tmp.r1.set(b.x, b.y, 0, 0).merge(Tmp.v1.x, Tmp.v1.y).grow(8f);
            Units.nearbyEnemies(b.team, searchRect, unit -> {
                if (available) return;
                Vec2 nearest = Intersector.nearestSegmentPoint(b.x, b.y, Tmp.v1.x, Tmp.v1.y, unit.x, unit.y, Tmp.v3);
                if (unit.within(nearest.x, nearest.y, unit.hitSize / 2f + 2f)) {
                    available = true;
                    Tmp.v2.set(nearest);
                    unit.damage(b.damage);
                    unit.apply(status, statusDuration);
                }
            });
        }

        if (available) {
            b.fdata = b.dst(Tmp.v2);
            // 范围伤害 (替代 Utils.trueEachBlock)
            Vars.indexer.eachBlock(null, Tmp.v2.x, Tmp.v2.y, damageRadius,
                build -> build.team != b.team,
                build -> handleBuilding(b, build, build.health));

            Units.nearby(Tmp.v2.x - damageRadius, Tmp.v2.y - damageRadius, damageRadius * 2f, damageRadius * 2f, unit -> {
                if (unit.team != b.team && unit.within(Tmp.v2.x, Tmp.v2.y, damageRadius)) {
                    float ratio = b.damage / damage;
                    float health = unit.health;
                    if (!overrideDamage) unit.damage(auraDamage * ratio);
                    handleUnit(b, unit, health);
                    unit.apply(status, statusDuration);
                }
            });

            // 简化点爆炸特效 (替代 SpecialFx.pointBlastLaserEffect)
            Effect.shake(damageRadius / 8f, 8f, Tmp.v2.x, Tmp.v2.y);
            hitEffect.at(Tmp.v2.x, Tmp.v2.y, b.rotation());
        }
    }

    @Override
    public void draw(Bullet b) {
        float realLength = b.fdata;
        float f = Mathf.curve(b.fin(), 0f, 0.2f);
        float baseLen = realLength * f;

        for (int i = 0; i < laserColors.length; i++) {
            float wReduced = i * widthReduction;
            Draw.color(laserColors[i]);
            Fill.circle(b.x, b.y, ((width - wReduced) / 2f) * b.fout());
            Lines.stroke((width - wReduced) * b.fout());
            Lines.lineAngle(b.x, b.y, b.rotation(), baseLen, false);
            Tmp.v1.trns(b.rotation(), baseLen).add(b);
            Drawf.tri(Tmp.v1.x, Tmp.v1.y, Lines.getStroke() * 1.22f, width * 2f, b.rotation());
            Draw.reset();
            Tmp.v1.trns(b.rotation(), baseLen + (width / 1.5f)).add(b);
        }
        // v158 Drawf.light 无 Team 参数
        Drawf.light(b.x, b.y, Tmp.v1.x, Tmp.v1.y, width * 1.4f * b.fout(), laserColors[0], 0.5f);
    }

    @Override
    public void drawLight(Bullet b) {
    }
}
