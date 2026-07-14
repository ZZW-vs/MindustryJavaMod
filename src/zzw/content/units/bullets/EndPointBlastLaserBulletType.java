package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.graphics.Drawf;

/**
 * 端点爆破激光 (移植自 PU132 EndPointBlastLaserBulletType, 简化版)
 * - 短距离激光, 命中后在命中点产生范围伤害
 * - width=12, length=100, damageRadius=20
 *
 * 简化:
 * - 用 v158 标准 Groups.unit.intersect + Geometry.raycastRect 精确碰撞
 * - 命中后由 hitUnitAntiCheat 触发防作弊伤害
 * - hitBullet 范围伤害
 */
public class EndPointBlastLaserBulletType extends AntiCheatBulletTypeBase {
    public float damageRadius = 20f;
    public float auraDamage = 10f;
    public float length = 100f;
    public float width = 12f;
    public float widthReduction = 2f;
    public Color[] laserColors = {Color.white};

    public EndPointBlastLaserBulletType(float damage) {
        super(0f, damage);
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

    @Override
    public void init() {
        super.init();
        drawSize = Math.max(drawSize, length + damageRadius * 2f);
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.fdata(length);
    }

    @Override
    public void update(Bullet b) {
        if (b.timer(1, 3f)) {
            b.fdata(length);
            Vec2 v = Tmp.v1.trns(b.rotation(), length).add(b);
            Rect rect = Tmp.r1;
            rect.set(b.x, b.y, 0, 0).merge(v.x, v.y).grow(width * 2f + 50f);

            int[] hitCount = {0};

            Groups.unit.intersect(rect.x, rect.y, rect.width, rect.height, unit -> {
                if (unit.team == b.team) return;
                if (!unit.checkTarget(collidesAir, collidesGround)) return;
                unit.hitbox(Tmp.r2);
                Tmp.r2.grow(width * 2f);
                Vec2 hv = arc.math.geom.Geometry.raycastRect(b.x, b.y, v.x, v.y, Tmp.r2);
                if (hv != null) {
                    hitUnitAntiCheat(b, unit);
                    if (hitCount[0] < 4) {
                        hit(b, hv.x, hv.y);
                        // 范围伤害: 在命中点产生 aura
                        Vars.indexer.eachBlock(null, hv.x - damageRadius, hv.y - damageRadius,
                            damageRadius * 2f, build -> build.team != b.team && build.health > 0,
                            build -> {
                                if (build.within(hv.x, hv.y, damageRadius)) {
                                    hitBuildingAntiCheat(b, build, auraDamage * (b.damage / Math.max(1f, b.type.damage)));
                                }
                            });
                        Units.nearbyEnemies(b.team, hv.x - damageRadius, hv.y - damageRadius, damageRadius * 2f, u2 -> {
                            if (u2.team != b.team && u2.within(hv.x, hv.y, damageRadius)) {
                                hitUnitAntiCheat(b, u2, auraDamage * (b.damage / Math.max(1f, b.type.damage)));
                            }
                        });
                    }
                    hitCount[0]++;
                }
            });
        }
    }

    @Override
    public void draw(Bullet b) {
        float realLength = b.fdata();
        float f = Mathf.curve(b.fout(), 0f, 0.2f);
        float baseLen = realLength * f;

        for (int i = 0; i < laserColors.length; i++) {
            float wReduced = i * widthReduction;
            Draw.color(laserColors[i]);
            Fill.circle(b.x(), b.y(), ((width - wReduced) / 2f) * b.fout());
            Lines.stroke((width - wReduced) * b.fout());
            Lines.lineAngle(b.x(), b.y(), b.rotation(), baseLen, false);
            Tmp.v1.trns(b.rotation(), baseLen).add(b);
            Drawf.tri(Tmp.v1.x, Tmp.v1.y, Lines.getStroke() * 1.22f, width * 2f, b.rotation());
            Draw.reset();
            Tmp.v1.trns(b.rotation(), baseLen + (width / 1.5f)).add(b);
        }
        Drawf.light(b.x(), b.y(), Tmp.v1.x, Tmp.v1.y, width * 1.4f * b.fout(), laserColors[0], 0.5f);
    }

    @Override
    public void drawLight(Bullet b) {
    }
}
