package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

/**
 * 加速激光 (PU_V8 AcceleratingLaserBulletType 移植版)
 * - 长度从 0 逐渐增长到 maxLength, 碰到建筑/单位后停止
 * - pierceCap 控制可穿透次数
 * - 自定义绘制: 多色叠加 + 三角形收口
 * 简化: 用 v158 Units.nearbyEnemies + indexer.eachBlock 替代 Utils.collideLineRawEnemyRatio
 *       移除复杂的 pierceScore/pierceOffset 逻辑, 用简单的 hitLength 检测
 * 参考: PU_V8 main/src/unity/entities/bullet/laser/AcceleratingLaserBulletType.java
 */
public class AcceleratingLaserBulletType extends BulletType {
    public float maxLength = 1000f;
    public float maxRange = 0f;
    public float laserSpeed = 15f;
    public float accel = 25f;
    public float width = 12f, collisionWidth = 8f;
    public float fadeTime = 60f;
    public float fadeInTime = 8f;
    public float oscOffset = 1.4f, oscScl = 1.1f;
    public float pierceAmount = 4f;
    public boolean fastUpdateLength = true;
    public Color[] colors = {Color.valueOf("ec745855"), Color.valueOf("ec7458aa"), Color.valueOf("ff9c5a"), Color.white};

    public AcceleratingLaserBulletType(float damage) {
        super(0f, damage);
        despawnEffect = mindustry.content.Fx.none;
        collides = false;
        pierce = true;
        impact = true;
        keepVelocity = false;
        hittable = false;
        absorbable = false;
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
        range = maxRange > 0 ? maxRange : maxLength / 1.5f;
        drawSize = maxLength * 2f;
        despawnHit = false;
    }

    @Override
    public void draw(Bullet b) {
        float fadeIn = fadeInTime <= 0f ? 1f : Mathf.clamp(b.time / fadeInTime);
        float fade = Mathf.clamp(b.time > b.lifetime - fadeTime ? 1f - (b.time - (lifetime - fadeTime)) / fadeTime : 1f) * fadeIn;
        float tipHeight = width / 2f;

        Lines.lineAngle(b.x, b.y, b.rotation(), b.fdata);
        for (int i = 0; i < colors.length; i++) {
            float f = ((float)(colors.length - i) / colors.length);
            float w = f * (width + Mathf.absin(Time.time + (i * oscOffset), oscScl, width / 4)) * fade;

            Tmp.v2.trns(b.rotation(), b.fdata - tipHeight).add(b);
            Tmp.v1.trns(b.rotation(), width * 2f).add(Tmp.v2);
            Draw.color(colors[i]);
            Fill.circle(b.x, b.y, w / 2f);
            Lines.stroke(w);
            Lines.line(b.x, b.y, Tmp.v2.x, Tmp.v2.y, false);
            for (int s : Mathf.signs) {
                Tmp.v3.trns(b.rotation(), w * -0.7f, w * s);
                Fill.tri(Tmp.v2.x, Tmp.v2.y, Tmp.v1.x, Tmp.v1.y, Tmp.v2.x + Tmp.v3.x, Tmp.v2.y + Tmp.v3.y);
            }
        }
        Tmp.v2.trns(b.rotation(), b.fdata + tipHeight).add(b);
        Drawf.light(b.x, b.y, Tmp.v2.x, Tmp.v2.y, width * 2f, colors[0], 0.5f);
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b) {
        // 不绘制光源 (在 draw 中处理)
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = new LaserData();
        b.fdata = 0f;
    }

    @Override
    public void update(Bullet b) {
        boolean timer = b.timer(0, 5f);

        if (b.data instanceof LaserData) {
            LaserData vec = (LaserData) b.data;
            if (vec.restartTime >= 5f) {
                if (accel > 0.01f) {
                    vec.velocity = Mathf.clamp((vec.velocityTime / accel) + vec.velocity, 0f, laserSpeed);
                    b.fdata = Mathf.clamp(b.fdata + (vec.velocity * Time.delta), 0f, maxLength);
                    vec.velocityTime += Time.delta;
                } else if (timer) {
                    b.fdata = maxLength;
                }
            } else {
                vec.restartTime += Time.delta;
            }
        }

        // 每 5 tick 进行一次碰撞检测
        if (timer) {
            Tmp.v1.trns(b.rotation(), b.fdata).add(b);
            checkLaserCollision(b, b.x, b.y, Tmp.v1.x, Tmp.v1.y);
        }
    }

    /** 简化版激光碰撞检测 */
    private void checkLaserCollision(Bullet b, float x1, float y1, float x2, float y2) {
        float fn = 1f; // 简化: 不使用 PU_V8 fn 复杂公式
        float dmg = damage * fn;

        // 检测建筑
        mindustry.Vars.indexer.eachBlock(null, x1, y1, b.fdata + 50f,
                build -> build.team != b.team,
                build -> {
                    if (build.block.absorbLasers) {
                        // 撞到吸收激光的建筑, 截断激光长度
                        b.fdata = Math.min(b.fdata, b.dst(build) - build.block.size * Vars.tilesize / 2f);
                    }
                    build.damage(dmg * buildingDamageMultiplier);
                });

        // 检测单位
        mindustry.entities.Units.nearbyEnemies(b.team, x1, y1, b.fdata + 50f, unit -> {
            if (!unit.hittable()) return;
            if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, unit.x, unit.y) > collisionWidth + unit.hitSize / 2f)
                return;
            unit.damage(dmg);
            Tmp.v3.set(unit).sub(b).nor().scl(knockback * 80f);
            if (impact) Tmp.v3.setAngle(b.rotation() + (knockback < 0 ? 180f : 0f));
            unit.impulse(Tmp.v3);
            unit.apply(status, statusDuration);
        });
    }

    public static class LaserData {
        public float lastLength, lightningTime, velocity, velocityTime, targetSize, pierceOffset, pierceOffsetSmooth, pierceScore, restartTime = 5f;
        public arc.math.geom.Position target;
    }
}
