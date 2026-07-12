package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.math.geom.Intersector;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

/**
 * PU132 EndSweepLaser 扫射激光移植版
 * - 4色叠加渲染 + 振荡宽度 (oscScl/oscMag)
 * - 每隔 distance 距离触发 hitBullet 生成虚空区域
 * - 持续型激光 (lifetime 内每 5 tick 检测碰撞)
 * - 防作弊伤害 (继承 AntiCheatBulletTypeBase)
 * - 黑圆优先在敌方密集处生成 (DensityCalculator)
 * 参考: PU132 main/src/unity/entities/bullet/anticheat/EndSweepLaser.java
 * 简化: 用 v154.3 的 Units.nearbyEnemies + Intersector 替代 Utils.collideLineRawEnemy
 */
public class EndSweepLaser extends AntiCheatBulletTypeBase {
    public float length = 300f;
    public float collisionWidth = 3f;
    public float widthLoss = 0.7f;
    public float width = 9f, oscScl = 0.8f, oscMag = 1.5f;
    /** 每隔 distance 距离触发一次 hitBullet */
    public float distance = 250f;
    public Color[] colors = {
        Color.valueOf("f5303690"),
        Color.valueOf("f53036"),
        Color.valueOf("ff786e"),
        Color.black
    };
    /** 命中时生成的子弹类型 (用于生成虚空区域 oppressionArea) */
    public BulletType hitBullet;
    /** 上次生成黑圆的距离 */
    protected float lastSpawnDist = 0f;

    public EndSweepLaser(float damage) {
        super(0f, damage);
        despawnEffect = mindustry.content.Fx.none;
        hittable = false;
        collides = false;
        absorbable = false;
        keepVelocity = false;
        impact = true;
        pierceShields = true;
        countsAsSkill = true;
    }

    @Override
    public float estimateDPS() {
        return damage * (lifetime / 2f) / 5f * 3f;
    }

    @Override
    public float continuousDamage() {
        return damage / 5f * 60f;
    }

    @Override
    public void init() {
        super.init();
        despawnHit = false;
        drawSize = length * 2f + 20f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        lastSpawnDist = 0f;
    }

    @Override
    public void update(Bullet b) {
        if (!checkSkillLimit(b)) return;
        if (b.timer(1, 5f)) {
            float len = length;
            Tmp.v1.trns(b.rotation(), len).add(b);
            float endX = Tmp.v1.x, endY = Tmp.v1.y;

            float currentDist = b.dst(endX, endY);

            if (hitBullet != null && currentDist - lastSpawnDist > distance) {
                Vec2 densePos = DensityCalculator.findBestPosition(b.x, b.y, b.rotation(), len, b.team, 1);
                if (densePos != null) {
                    hitBullet.create(b.owner, b.team, densePos.x, densePos.y, b.rotation());
                    lastSpawnDist = b.dst(densePos.x, densePos.y);
                } else {
                    hitBullet.create(b.owner, b.team, endX, endY, b.rotation());
                    lastSpawnDist = currentDist;
                }
            }

            // 检测敌方单位 (替代 PU132 Utils.collideLineRawEnemy)
            Units.nearbyEnemies(b.team, b.x, b.y, len + 50f, unit -> {
                if (!unit.hittable()) return;
                if (!unit.checkTarget(collidesAir, collidesGround)) return;
                if (Intersector.distanceSegmentPoint(b.x, b.y, endX, endY, unit.x, unit.y) > collisionWidth + unit.hitSize / 2f) return;
                hitUnitAntiCheat(b, unit);
            });

            // 检测建筑
            Vars.indexer.eachBlock(null, b.x, b.y, len + 50f,
                    build -> build.team != b.team && build.health > 0,
                    build -> {
                        if (Intersector.distanceSegmentPoint(b.x, b.y, endX, endY, build.x, build.y) > collisionWidth + build.hitSize() / 2f) return;
                        hitBuildingAntiCheat(b, build);
                    });

            b.fdata = len;
        }
    }

    @Override
    public void drawLight(Bullet b) {
        // 光源在 draw() 中处理
    }

    @Override
    public void draw(Bullet b) {
        Vec2 v = Tmp.v1.trns(b.rotation(), b.fdata > 0 ? b.fdata : length).add(b);
        // 渐入渐出 (前 16 tick 渐入, 后 16 tick 渐出)
        float fin = Mathf.clamp(b.time / 16f) * Mathf.clamp(b.time > b.lifetime - 16f ? 1f - (b.time - (b.lifetime - 16f)) / 16f : 1f);

        float w = (width + Mathf.absin(oscScl, oscMag)) * fin;
        for (Color c : colors) {
            Draw.color(c);
            Lines.stroke(w);
            Lines.line(b.x, b.y, v.x, v.y, false);
            Drawf.tri(b.x, b.y, w * 1.22f, (width * 2f), b.rotation() + 180f);
            Drawf.tri(v.x, v.y, w * 1.22f, (width * 3f) + (w * 2f), b.rotation());
            w *= widthLoss;
        }
        Draw.reset();
    }
}
