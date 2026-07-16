package zzw.content.units.weapons;

import mindustry.entities.units.WeaponMount;
import mindustry.gen.Bullet;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.entities.bullet.BulletType;

/**
 * 点防多管武器 (移植自 PU_V8 unity.type.weapons.PointDefenceMultiBarrelWeapon)
 * - 继承 MultiBarrelWeapon (多管)
 * - 覆写 findTarget: 查找最近的敌方子弹 (反导防御)
 * - 覆写 checkTarget: 处理子弹目标
 */
public class PointDefenceMultiBarrelWeapon extends MultiBarrelWeapon {
    static WeaponMount tmp;

    public PointDefenceMultiBarrelWeapon(String name) {
        super(name);
    }

    @Override
    public void update(Unit unit, WeaponMount mount) {
        tmp = mount;
        super.update(unit, mount);
    }

    @Override
    protected Teamc findTarget(Unit unit, float x, float y, float range, boolean air, boolean ground) {
        // 查找最近的敌方子弹 (hittable 且速度足够慢)
        return nearestBullet(x, y, range, b -> b.team != unit.team && b.type.hittable && b.vel.len2() < 5f * 5f);
    }

    @Override
    protected boolean checkTarget(Unit unit, Teamc target, float x, float y, float range) {
        boolean bullet = (target instanceof Bullet b && (b.hitSize <= 0f || b.type == null));
        if (bullet) tmp.retarget = 5f;
        return super.checkTarget(unit, target, x, y, range) || bullet;
    }

    /** 查找最近的符合条件的子弹 */
    private static Bullet nearestBullet(float x, float y, float range, arc.func.Boolf<Bullet> predicate) {
        Bullet closest = null;
        float closestDist = range * range;
        for (Bullet b : mindustry.gen.Groups.bullet) {
            if (!predicate.get(b)) continue;
            float dist = b.dst2(x, y);
            if (dist < closestDist) {
                closestDist = dist;
                closest = b;
            }
        }
        return closest;
    }
}
