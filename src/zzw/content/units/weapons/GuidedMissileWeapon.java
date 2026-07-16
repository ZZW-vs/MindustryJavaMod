package zzw.content.units.weapons;

import mindustry.entities.units.WeaponMount;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;

/**
 * 制导导弹武器 (移植自 PU132 blue-side-silo 的 LimitedAngleWeapon + bullet() 覆写)
 * - 继承 LimitedAngleWeapon (限制角度)
 * - 覆写 handleBullet: 把 WeaponMount 存到 b.data, 供 GuidedMissileBulletType 使用
 * v158 用 handleBullet 替代 PU132 的 bullet() 覆写 (签名不同)
 */
public class GuidedMissileWeapon extends LimitedAngleWeapon {

    public GuidedMissileWeapon(String name) {
        super(name);
    }

    public GuidedMissileWeapon() {
        this("");
    }

    @Override
    protected void handleBullet(Unit unit, WeaponMount mount, Bullet b) {
        super.handleBullet(unit, mount, b);
        b.data = mount;
    }
}
