package zzw.content.units.bullets;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Unit;

/**
 * 慢速磁轨炮子弹 (PU_V8 SlowRailBulletType 移植版)
 * - speed > 0, 多 tick 飞行
 * - pierce=true, pierceBuilding=true (穿透单位和建筑)
 * - 自定义绘制 (双三角箭头)
 * - trailSpacing 沿轨迹生成 trailEffect
 * 简化: 用 vanilla collides=true + pierce 替代 PU_V8 Utils.collideLineRawEnemy 手动碰撞检测
 * 参考: PU_V8 main/src/unity/entities/bullet/physical/SlowRailBulletType.java
 */
public class SlowRailBulletType extends BasicBulletType {
    public float trailSpacing = 5f;
    public float collisionWidth = 3f;
    public float pierceDamageFactor = 0f;

    public SlowRailBulletType(float speed, float damage) {
        super(speed, damage);
        collides = collidesTiles = reflectable = false;
        pierce = pierceBuilding = true;
        // 用 vanilla 默认 trail, 后续可被外部覆盖
        trailEffect = mindustry.content.Fx.smoke;
    }

    @Override
    public void init() {
        super.init();
        drawSize = Math.max(drawSize, (Math.max(height, width) + (speed * lifetime * 0.75f)) * 2f);
        trailColor = backColor;
        trailLength = Math.max((int)(lifetime), 2);
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        RailData data = new RailData();
        data.x = b.x;
        data.y = b.y;
        b.data = data;
    }

    @Override
    public void update(Bullet b) {
        // 用 vanilla 默认 update 处理 trail/homing/weaving
        super.update(b);

        // 自定义碰撞检测: 沿 lastX,lastY -> x,y 检测
        // 简化版: 用 nearbyEnemies + Intersector 替代 Utils.collideLineRawEnemy
        if (b.damage > 0f) {
            float dst = Mathf.dst(b.lastX, b.lastY, b.x, b.y);
            if (dst > 0f) {
                checkCollisions(b);
            }
        }

        // 沿轨迹生成 trailEffect (PU_V8 原版特性)
        if (b.data instanceof RailData) {
            RailData data = (RailData) b.data;
            data.lastLen += Mathf.dst(b.lastX, b.lastY, b.x, b.y);
            while (data.len < data.lastLen) {
                Tmp.v1.trns(b.rotation(), data.len).add(data.x, data.y);
                trailEffect.at(Tmp.v1.x, Tmp.v1.y, b.rotation(), trailColor);
                data.len += trailSpacing;
            }
        }
    }

    /** 简化版碰撞检测: 用 vanilla 的 collision/hitEntity/hitTile 流程 */
    private void checkCollisions(Bullet b) {
        // 由于已经设置 pierce=true + collides=false, vanilla 不会自动检测
        // 这里手动遍历附近敌方单位和建筑
        float segLen = Mathf.dst(b.lastX, b.lastY, b.x, b.y);
        float checkRange = segLen + collisionWidth + 8f;

        // 检测单位
        mindustry.entities.Units.nearbyEnemies(b.team, b.x, b.y, checkRange, unit -> {
            if (!unit.hittable()) return;
            if (!unit.checkTarget(collidesAir, collidesGround)) return;
            if (b.collided.contains(unit.id)) return;
            if (arc.math.geom.Intersector.distanceSegmentPoint(b.lastX, b.lastY, b.x, b.y, unit.x, unit.y) > collisionWidth + unit.hitSize / 2f)
                return;
            float h = unit.health;
            hitEntity(b, unit, unit.health);
            b.collided.add(unit.id);
            float sub = Math.max(unit.health * pierceDamageFactor, 0);
            b.damage -= sub;
        });

        // 检测建筑
        mindustry.Vars.indexer.eachBlock(null, b.x, b.y, checkRange,
                build -> build.team != b.team && build.health > 0 && !b.collided.contains(build.id),
                build -> {
                    if (arc.math.geom.Intersector.distanceSegmentPoint(b.lastX, b.lastY, b.x, b.y, build.x, build.y) > collisionWidth + build.block.size * mindustry.Vars.tilesize / 2f)
                        return;
                    float h = build.health;
                    build.collision(b);
                    hitTile(b, build, build.x, build.y, h, true);
                    b.collided.add(build.id);
                    float sub = Math.max(build.health * pierceDamageFactor, 0);
                    b.damage -= sub;
                });
    }

    @Override
    public void draw(Bullet b) {
        drawTrail(b);
        float height = this.height * ((1f - shrinkY) + shrinkY * b.fout());
        float width = (this.width * ((1f - shrinkX) + shrinkX * b.fout())) / 1.5f;
        Tmp.v1.trns(b.rotation(), height / 2f);
        for (int s : Mathf.signs) {
            Tmp.v2.trns(b.rotation() - 90f, width * s, -height);
            Draw.color(backColor);
            Fill.tri(Tmp.v1.x + b.x, Tmp.v1.y + b.y, -Tmp.v1.x + b.x, -Tmp.v1.y + b.y, Tmp.v2.x + b.x, Tmp.v2.y + b.y);
            Draw.color(frontColor);
            Fill.tri(Tmp.v1.x / 2f + b.x, Tmp.v1.y / 2f + b.y, -Tmp.v1.x / 2f + b.x, -Tmp.v1.y / 2f + b.y, Tmp.v2.x / 2f + b.x, Tmp.v2.y / 2f + b.y);
        }
    }

    static class RailData {
        float x, y, len, lastLen;
    }
}
