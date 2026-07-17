package zzw.content.units.bullets;

import mindustry.content.StatusEffects;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Entityc;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Statusc;
import mindustry.gen.Teamc;
import mindustry.type.StatusEffect;

/**
 * 改变队伍激光子弹 (PU_V8 ChangeTeamLaserBulletType 移植版)
 * - 持续激光, 当目标生命值低于阈值时改变其队伍
 * - convertUnits: 转化敌方单位
 * - convertBlocks: 转化敌方建筑
 * - 改变队伍后对发射者造成伤害 (ownerDamageRatio)
 * 参考: PU_V8 main/src/unity/entities/bullet/laser/ChangeTeamLaserBulletType.java
 */
public class ChangeTeamLaserBulletType extends ContinuousLaserBulletType {
    /** 目标生命值百分比阈值, 低于此值才改变队伍 */
    public float minimumHealthPercent = 0.125f;
    /** 目标生命值绝对值阈值, 低于此值也改变队伍 */
    public float minimumHealthOverride = 90f;
    /** 改变队伍后对发射者造成的伤害比例 */
    public float ownerDamageRatio = 0.5f;
    /** 是否转化单位 */
    public boolean convertUnits = true;
    /** 是否转化建筑 */
    public boolean convertBlocks = true;
    /** 转化后施加的状态效果 */
    public StatusEffect conversionStatusEffect = StatusEffects.none;

    public ChangeTeamLaserBulletType(float damage) {
        super(damage);
    }

    @Override
    public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
        super.hitEntity(b, other, initialHealth);
        if (!(other instanceof Teamc t && other instanceof Healthc h)) return;
        if (convertUnits && (h.healthf() <= minimumHealthPercent || h.health() < minimumHealthOverride)) {
            t.team(b.team);
            damageOwner(b, initialHealth * ownerDamageRatio);
            if (other instanceof Statusc s) {
                s.apply(conversionStatusEffect);
            }
        }
    }

    @Override
    public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
        super.hitTile(b, build, x, y, initialHealth, direct);
        if (convertBlocks && (build.healthf() <= minimumHealthPercent || build.health < minimumHealthOverride)) {
            build.team(b.team);
            damageOwner(b, initialHealth * ownerDamageRatio);
        }
    }

    /** 改变队伍后对发射者造成伤害 */
    void damageOwner(Bullet b, float damage) {
        if (damage == 0) return;
        if (b.owner instanceof Healthc h) {
            if (damage < 0) {
                h.heal(Math.abs(damage));
                return;
            }
            if (h.health() - damage > 1f || h.health() < h.maxHealth() / 2f) {
                h.damage(damage);
            } else {
                h.health(1f);
            }
        }
    }
}
