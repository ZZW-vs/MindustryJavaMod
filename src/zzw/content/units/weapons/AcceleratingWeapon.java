package zzw.content.units.weapons;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.Weapon;

/**
 * 加速射击武器 (PU_V8 AcceleratingWeapon 移植版)
 * - 连续射击时 reload 越来越快 (accel 累积)
 * - 停止射击后 accel 逐渐衰减
 * - alternate 模式下双武器同步 accel
 * 简化: 移除 alternate 模式的复杂同步逻辑, 只保留基础 accel
 * 参考: PU_V8 main/src/unity/type/weapons/AcceleratingWeapon.java
 */
public class AcceleratingWeapon extends Weapon {
    public float accelCooldownTime = 120f;
    public float accelCooldownWaitTime = 60f;
    public float accelPerShot = 1f;
    public float minReload = 5f;

    public AcceleratingWeapon(String name) {
        super(name);
        mountType = AcceleratingMount::new;
    }

    public AcceleratingWeapon() {
        this("");
    }

    @Override
    public void update(Unit unit, WeaponMount mount) {
        AcceleratingMount aMount = (AcceleratingMount) mount;
        // accel 越大, reload 越快
        float r = ((aMount.accel / reload) * unit.reloadMultiplier * Time.delta) * (reload - minReload);
        if (!alternate || otherSide == -1) {
            mount.reload -= r;
        } else {
            WeaponMount other = unit.mounts[otherSide];
            other.reload -= r / 2f;
            mount.reload -= r / 2f;
            if (other instanceof AcceleratingMount aM) {
                float accel = unit.isShooting() && unit.canShoot() ? Math.max(aM.accel, aMount.accel) : Math.min(aM.accel, aMount.accel);
                float wTime = unit.isShooting() && unit.canShoot() ? Math.max(aM.waitTime, aMount.waitTime) : Math.min(aM.waitTime, aMount.waitTime);
                aM.accel = accel;
                aM.waitTime = wTime;
                aMount.accel = accel;
                aMount.waitTime = wTime;
            }
        }
        if (aMount.waitTime <= 0f) {
            aMount.accel = Math.max(0f, aMount.accel - (minReload / accelCooldownTime) * Time.delta);
        } else {
            aMount.waitTime -= Time.delta;
        }
        super.update(unit, mount);
    }

    @Override
    protected void shoot(Unit unit, WeaponMount mount, float shootX, float shootY, float rotation) {
        // v158 shoot 签名是 (Unit, WeaponMount, float shootX, float shootY, float rotation)
        AcceleratingMount aMount = (AcceleratingMount) mount;
        aMount.accel = Mathf.clamp(aMount.accel + accelPerShot, 0f, minReload);
        aMount.waitTime = accelCooldownWaitTime;
        super.shoot(unit, mount, shootX, shootY, rotation);
    }

    public static class AcceleratingMount extends WeaponMount {
        float accel = 0f;
        float waitTime = 0f;

        AcceleratingMount(Weapon weapon) {
            super(weapon);
        }
    }
}
