package zzw.content.blocks.soul;

import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.entities.Predict;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Hitboxc;
import mindustry.gen.Teamc;
import mindustry.graphics.Trail;

/**
 * 弹跳子弹类型 (PU_V8 RicochetBulletType 移植版)
 *
 * - 子弹击中目标后自动转向附近敌方目标 (最多 pierceCap 次)
 * - 子弹有尾迹效果
 *
 * 注意: 原版 @Deprecated 但仍使用, 因为 PU_V8 自身无法修复, 这里移植保留功能
 * 参考: PU_V8 unity/entities/bullet/monolith/energy/RicochetBulletType.java
 */
public class RicochetBulletType extends BasicBulletType {
    public int trailLength = 6;

    public RicochetBulletType(float speed, float damage) {
        this(speed, damage, "bullet");
    }

    public RicochetBulletType(float speed, float damage, String spriteName) {
        super(speed, damage, spriteName);
        pierce = true;
        pierceBuilding = true;
        pierceCap = 3;
        trailChance = 1f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = new RicochetBulletData();
    }

    @Override
    public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
        ricochet(b, (mindustry.gen.Posc) other);
    }

    @Override
    public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
        super.hitTile(b, build, x, y, initialHealth, direct);
        if (direct) {
            ricochet(b, build);
        }
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        if (b.data instanceof RicochetBulletData data) {
            if (data.trail != null) {
                data.trail.update(b.x, b.y);
            }
        }
    }

    @Override
    public void draw(Bullet b) {
        if (b.data instanceof RicochetBulletData data) {
            if (data.trail != null) {
                data.trail.draw(backColor, width * 0.18f);
            }
        }
        super.draw(b);
    }

    public void ricochet(Bullet b, mindustry.gen.Posc entity) {
        if (!(b.data instanceof RicochetBulletData data)) return;

        if (data.hit == entity.id()) return;
        data.hit = entity.id();
        b.collided.clear();

        if (data.ricochet < pierceCap) {
            data.ricochet++;
            data.findEnemy(b, range);  // v158 BulletType 无 range() 方法, 直接读 range 字段
            if (data.target != null) {
                if (data.target instanceof Hitboxc v) {
                    Vec2 out = Predict.intercept(b.x, b.y, v.x(), v.y(), v.deltaX(), v.deltaY(), b.vel.len());
                    float rot = out.sub(b.x, b.y).angle();
                    b.vel.setAngle(rot);
                } else {
                    b.vel.setAngle(b.angleTo(data.target));
                }
            } else {
                despawned(b);
            }
        }
    }

    public class RicochetBulletData {
        protected int ricochet;
        protected Teamc target;
        protected int hit;
        protected Trail trail = new Trail(trailLength);

        protected RicochetBulletData() {}

        protected void findEnemy(Bullet b, float range) {
            target = Units.closestTarget(b.team, b.x, b.y, range * b.fout(),
                u -> u.isValid() && u.id != hit && ((u.isFlying() && collidesAir) || (u.isGrounded() && collidesGround)),
                t -> t.isValid() && t.id != hit && collidesGround
            );
        }
    }
}
