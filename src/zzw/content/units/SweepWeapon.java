package zzw.content.units;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Predict;
import mindustry.entities.Sized;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.type.Weapon;

/**
 * PU132 SweepWeapon 扫射武器移植版 (适配 v150.1 Weapon API)
 * - 每次射击时翻转 sweep 方向
 * - 武器在瞄准方向左右 sweepAngle 范围内扫射
 * - sweepTime 控制扫射速度
 * 参考: PU132 main/src/unity/type/weapons/SweepWeapon.java
 */
public class SweepWeapon extends Weapon {
    public float sweepTime = 120f;
    public float sweepAngle = 60f;

    public SweepWeapon(String name) {
        super(name);
        rotate = true;
        continuous = true;
        mountType = SweepWeaponMount::new;
    }

    @Override
    public void update(Unit unit, WeaponMount mount) {
        SweepWeaponMount m = (SweepWeaponMount) mount;
        boolean can = unit.canShoot();
        boolean sweep = m.sweep2 != m.sweep;
        float lastReload = mount.reload;
        mount.reload = Math.max(mount.reload - Time.delta * unit.reloadMultiplier, 0);
        mount.recoil = Mathf.approachDelta(mount.recoil, 0, (Math.abs(recoil) * unit.reloadMultiplier) / recoilTime);

        // 扫射角度逼近 (PU132 核心逻辑)
        if (sweep) {
            float direction = sweepAngle * Mathf.sign(m.sweep);
            m.angle = Mathf.approachDelta(m.angle, direction, (sweepAngle * 2f) / sweepTime);
            if (Mathf.equal(m.angle, direction)) {
                m.sweep2 = m.sweep;
            }
        }

        float mountX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
              mountY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);

        // 自动索敌
        if (!controllable && autoTarget) {
            if ((mount.retarget -= Time.delta) <= 0f) {
                mount.target = findTarget(unit, mountX, mountY, bullet.range, bullet.collidesAir, bullet.collidesGround);
                mount.retarget = mount.target == null ? targetInterval : targetSwitchInterval;
            }
            if (mount.target != null && checkTarget(unit, mount.target, mountX, mountY, bullet.range)) {
                mount.target = null;
            }
            boolean shoot = false;
            if (mount.target != null) {
                shoot = mount.target.within(mountX, mountY, bullet.range + Math.abs(shootY) + (mount.target instanceof Sized s ? s.hitSize() / 2f : 0f)) && can;
                if (predictTarget && mount.target instanceof Hitboxc h) {
                    Vec2 to = Predict.intercept(unit, h, bullet.speed);
                    mount.aimX = to.x;
                    mount.aimY = to.y;
                } else {
                    mount.aimX = mount.target.x();
                    mount.aimY = mount.target.y();
                }
            }
            mount.shoot = mount.rotate = shoot;
        }

        // 旋转 (PU132: 在旋转目标角度上加上 m.angle 实现扫射偏移)
        if (rotate && (mount.rotate || mount.shoot) && can) {
            float axisX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                  axisY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);
            mount.targetRotation = Angles.angle(axisX, axisY, mount.aimX, mount.aimY) - unit.rotation;
            mount.rotation = Angles.moveToward(mount.rotation, mount.targetRotation + m.angle, rotateSpeed * Time.delta);
        } else if (!rotate) {
            mount.rotation = 0;
            mount.targetRotation = unit.angleTo(mount.aimX, mount.aimY);
        }
        // PU132: 扫射进行中但未在射击状态时, 仍继续旋转
        if (sweep && !(mount.rotate || mount.shoot)) {
            mount.rotation = Angles.moveToward(mount.rotation, mount.targetRotation + m.angle, rotateSpeed * Time.delta);
        }

        float weaponRotation = unit.rotation - 90 + (rotate ? mount.rotation : 0),
              bulletX = mountX + Angles.trnsx(weaponRotation, this.shootX, this.shootY),
              bulletY = mountY + Angles.trnsy(weaponRotation, this.shootX, this.shootY),
              shootAngle = rotate ? weaponRotation + 90 : Angles.angle(bulletX, bulletY, mount.aimX, mount.aimY) + (unit.rotation - unit.angleTo(mount.aimX, mount.aimY));

        // 连续武器状态更新
        if (continuous && mount.bullet != null) {
            if (!mount.bullet.isAdded() || mount.bullet.time >= mount.bullet.lifetime || mount.bullet.type != bullet) {
                mount.bullet = null;
            } else {
                mount.bullet.rotation(weaponRotation + 90);
                mount.bullet.set(bulletX, bulletY);
                mount.reload = reload;
                mount.recoil = recoil;
                unit.vel.add(Tmp.v1.trns(unit.rotation + 180f, mount.bullet.type.recoil));
                if (shootSound != mindustry.gen.Sounds.none && !Vars.headless) {
                    if (mount.sound == null) mount.sound = new mindustry.audio.SoundLoop(shootSound, 1f);
                    mount.sound.update(bulletX, bulletY, true);
                }
            }
        } else {
            mount.heat = Math.max(mount.heat - Time.delta * unit.reloadMultiplier / cooldownTime, 0);
            if (mount.sound != null) {
                mount.sound.update(bulletX, bulletY, false);
            }
        }

        // 交替武器翻转
        boolean wasFlipped = mount.side;
        if (otherSide != -1 && alternate && mount.side == flipSprite && mount.reload <= reload / 2f && lastReload > reload / 2f) {
            unit.mounts[otherSide].side = !unit.mounts[otherSide].side;
            mount.side = !mount.side;
        }

        // 射击判定 (PU132: 检查 mount.rotation - m.angle 而非 mount.rotation)
        if (mount.shoot && can &&
            (!useAmmo || unit.ammo > 0 || !Vars.state.rules.unitAmmo || unit.team.rules().infiniteAmmo) &&
            (!alternate || wasFlipped == flipSprite) &&
            unit.vel.len() >= minShootVelocity &&
            mount.reload <= 0.0001f &&
            Angles.within(rotate ? (mount.rotation - m.angle) : unit.rotation, mount.targetRotation, shootCone)) {
            shoot(unit, mount, bulletX, bulletY, shootAngle);
            mount.reload = reload;
            if (useAmmo) {
                unit.ammo--;
                if (unit.ammo < 0) unit.ammo = 0;
            }
        }
    }

    /** PU132: 每次射击时翻转 sweep 方向 */
    @Override
    protected void shoot(Unit unit, WeaponMount mount, float shootX, float shootY, float rotation) {
        SweepWeaponMount m = (SweepWeaponMount) mount;
        m.sweep = !m.sweep;
        super.shoot(unit, mount, shootX, shootY, rotation);
    }

    /** 扫射武器专用 Mount */
    public static class SweepWeaponMount extends WeaponMount {
        public float angle;
        public boolean sweep, sweep2;

        public SweepWeaponMount(Weapon weapon) {
            super(weapon);
            SweepWeapon w = (SweepWeapon) weapon;
            angle = w.sweepAngle * Mathf.sign(weapon.flipSprite);
            sweep = sweep2 = weapon.flipSprite;
        }
    }
}
