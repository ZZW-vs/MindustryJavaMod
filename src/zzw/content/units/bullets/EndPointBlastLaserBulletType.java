package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.struct.IntSet;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;

/**
 * PU132 EndPointBlastLaserBulletType 移植版 (简化)
 * - 激光直线碰撞检测, 找到第一个阻挡物后停止
 * - 对阻挡点附近半径 damageRadius 范围内的敌人造成 auraDamage 范围伤害
 * - 多层颜色叠加渲染 (laserColors)
 * - 防作弊伤害 (继承 AntiCheatBulletTypeBase)
 * 参考: PU132 main/src/unity/entities/bullet/anticheat/EndPointBlastLaserBulletType.java
 */
public class EndPointBlastLaserBulletType extends AntiCheatBulletTypeBase {
    /** 范围伤害半径 (阻挡点附近) */
    public float damageRadius = 20f;
    /** 范围伤害值 */
    public float auraDamage = 10f;
    /** 激光长度 */
    public float length = 100f;
    /** 激光宽度 */
    public float width = 12f;
    /** 每层颜色宽度递减 */
    public float widthReduction = 2f;
    /** 多层颜色渲染 */
    public Color[] laserColors = {Color.white};

    /** 去重集合 (每次 init 清空) */
    private static final IntSet collided = new IntSet();
    /** 实际激光终点 (单元素数组, 供 lambda 修改) */
    private static final float[] segLenHolder = new float[1];
    private static final float[] endXHolder = new float[1];
    private static final float[] endYHolder = new float[1];

    public EndPointBlastLaserBulletType(float damage) {
        speed = 0f;
        this.damage = damage;
        hitEffect = mindustry.content.Fx.hitLancer;
        despawnEffect = mindustry.content.Fx.none;
        shootEffect = mindustry.content.Fx.none;
        smokeEffect = mindustry.content.Fx.none;
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
        range = length;  // 设置 BulletType.range 字段
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        // ★ PU132 L71-107: 激光直线碰撞检测, 找到第一个阻挡物
        segLenHolder[0] = length;  // 默认全长
        Tmp.v1.trns(b.rotation(), length).add(b.x(), b.y());
        endXHolder[0] = Tmp.v1.x;
        endYHolder[0] = Tmp.v1.y;
        collided.clear();

        float cx = (b.x() + endXHolder[0]) * 0.5f;
        float cy = (b.y() + endYHolder[0]) * 0.5f;
        float radius = length * 0.5f + 8f;

        // 检测单位碰撞 (找最近的, 同时对所有路径上的单位造成直接伤害)
        Units.nearbyEnemies(b.team, cx, cy, radius, u -> {
            if (!u.hittable()) return;
            if (!u.checkTarget(collidesAir, collidesGround)) return;
            float r = u.hitSize / 2f;
            if (Intersector.distanceSegmentPoint(b.x(), b.y(), endXHolder[0], endYHolder[0], u.x(), u.y()) <= r) {
                if (collided.add(u.id())) {
                    hitUnitAntiCheat(b, u);
                }
                // 更新激光终点到最近的单位
                float dst = b.dst(u);
                if (dst < segLenHolder[0]) {
                    segLenHolder[0] = dst;
                    endXHolder[0] = u.x();
                    endYHolder[0] = u.y();
                }
            }
        });
        // 检测建筑碰撞 (找最近的, 同时对所有路径上的建筑造成直接伤害)
        Vars.indexer.allBuildings(cx, cy, radius, build -> {
            if (build.team == b.team) return;
            float r = build.block.size * Vars.tilesize / 2f;
            if (Intersector.distanceSegmentPoint(b.x(), b.y(), endXHolder[0], endYHolder[0], build.x(), build.y()) <= r) {
                if (collided.add(build.id())) {
                    hitBuildingAntiCheat(b, build);
                }
                // 更新激光终点到最近的建筑
                float dst = b.dst(build);
                if (dst < segLenHolder[0]) {
                    segLenHolder[0] = dst;
                    endXHolder[0] = build.x();
                    endYHolder[0] = build.y();
                }
            }
        });

        // 设置实际激光长度
        float actualLen = Math.min(length, segLenHolder[0]);
        b.fdata(actualLen);

        // ★ PU132 L93-106: 对阻挡点附近范围造成 auraDamage 伤害
        if (actualLen < length) {
            Tmp.v1.set(endXHolder[0], endYHolder[0]);
            // 对阻挡点附近半径 damageRadius 内的建筑造成范围伤害
            Vars.indexer.allBuildings(Tmp.v1.x, Tmp.v1.y, damageRadius, build -> {
                if (build.team != b.team && build.within(Tmp.v1.x, Tmp.v1.y, damageRadius)) {
                    hitBuildingAntiCheat(b, build, auraDamage * (b.damage / damage));
                }
            });
            // 对阻挡点附近半径 damageRadius 内的单位造成范围伤害
            Units.nearby(Tmp.v1.x - damageRadius, Tmp.v1.y - damageRadius, damageRadius * 2f, damageRadius * 2f, u -> {
                if (u.team != b.team && u.within(Tmp.v1.x, Tmp.v1.y, damageRadius)) {
                    hitUnitAntiCheat(b, u, auraDamage * (b.damage / damage));
                }
            });
            // 命中特效
            hitEffect.at(Tmp.v1.x, Tmp.v1.y, b.rotation());
        }
    }

    @Override
    public void draw(Bullet b) {
        // ★ v158 bloom 适配: 重置混合模式为 alpha 混合
        Draw.blend();
        float realLength = b.fdata();
        float f = Mathf.curve(b.fin(), 0f, 0.2f);
        float baseLen = realLength * f;

        for (int i = 0; i < laserColors.length; i++) {
            float wReduced = i * widthReduction;
            Draw.color(laserColors[i]);
            Fill.circle(b.x(), b.y(), ((width - wReduced) / 2f) * b.fout());
            Lines.stroke((width - wReduced) * b.fout());
            if (baseLen > 0) {  // ★ 避免长度为 0 产生 NaN
                Lines.lineAngle(b.x(), b.y(), b.rotation(), baseLen, false);
                Tmp.v1.trns(b.rotation(), baseLen).add(b.x(), b.y());
                Drawf.tri(Tmp.v1.x, Tmp.v1.y, Lines.getStroke() * 1.22f, width * 2f, b.rotation());
            }
            Draw.reset();
        }
        // 光照效果 (v158: Drawf.light 只支持单点)
        if (baseLen > 0) {
            Drawf.light(b.x(), b.y(), width * 1.4f * b.fout(), laserColors[0], 0.5f);
        }
    }

    @Override
    public void drawLight(Bullet b) {
        // 空实现, 光照在 draw() 中处理
    }
}
