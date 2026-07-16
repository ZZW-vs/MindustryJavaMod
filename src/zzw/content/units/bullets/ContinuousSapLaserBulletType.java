package zzw.content.units.bullets;

import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;

/**
 * 持续吸血激光 (移植自 PU_V8 UnityBullets.continuousSapLaser)
 * 命中时 owner 回复伤害量 20% 的生命
 */
public class ContinuousSapLaserBulletType extends ContinuousLaserBulletType {

    public ContinuousSapLaserBulletType(float damage) {
        super(damage);
    }

    @Override
    public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
        super.hitTile(b, build, x, y, initialHealth, direct);
        if (b.owner instanceof Healthc owner) {
            owner.heal(Math.max(initialHealth - build.health(), 0f) * 0.2f);
        }
    }

    @Override
    public void hitEntity(Bullet b, Hitboxc entity, float health) {
        super.hitEntity(b, entity, health);
        if (entity instanceof Healthc h && b.owner instanceof Healthc owner) {
            owner.heal(Math.max(health - h.health(), 0f) * 0.2f);
        }
    }
}
