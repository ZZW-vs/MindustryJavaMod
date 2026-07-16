package zzw.content.units.weapons;

import arc.func.Cons2;
import arc.func.Cons3;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.audio.SoundLoop;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.type.Weapon;

import static mindustry.Vars.headless;
import static mindustry.Vars.state;

/**
 * 充能武器 (PU_V8 EnergyChargeWeapon 完整移植版)
 * - drawCharge 回调绘制蓄力特效 (charge: 0=未充能, 1=满充能)
 * - chargeCondition 可选: 自定义充能条件 (null 时走原生 update)
 * - ChargeMount: 扩展 WeaponMount, 增加 timer/charge 字段
 * - shoot(): 自定义实现, 支持 charge 参数传递给 bullet (charge 伤害加成)
 * 简化: 移除 UnityDrawf 依赖, 用 vanilla SoundLoop
 *       shoot() 内部使用 v158 shoot.shoot (ShootPattern) API
 * 参考: PU_V8 main/src/unity/type/weapons/EnergyChargeWeapon.java
 */
public class EnergyChargeWeapon extends Weapon {
    /** 充能绘制回调 (unit, mount, charge) -> 绘制充能特效, charge: 0=未充能, 1=满充能 */
    public Cons3<Unit, WeaponMount, Float> drawCharge = (unit, mount, charge) -> {};
    /** 自定义充能条件 (null 时走原生 update) */
    public Cons2<Unit, WeaponMount> chargeCondition;
    public boolean drawTop = true, startUncharged = true, drawRegion = true;

    public EnergyChargeWeapon(String name) {
        super(name);
        mountType = w -> {
            WeaponMount m = new ChargeMount(w);
            m.reload = startUncharged ? reload : 0f;
            return m;
        };
    }

    public EnergyChargeWeapon() {
        this("");
    }

    @Override
    public void drawOutline(Unit unit, WeaponMount mount) {
        if (drawRegion) super.drawOutline(unit, mount);
    }

    @Override
    public void draw(Unit unit, WeaponMount mount) {
        float tmp = mount.reload;
        mount.reload = Mathf.clamp(mount.reload, 0f, reload);
        if (!drawTop) drawCharge.get(unit, mount, 1f - Mathf.clamp(mount.reload / reload));
        if (drawRegion) super.draw(unit, mount);
        mount.reload = tmp;
        if (drawTop) drawCharge.get(unit, mount, 1f - Mathf.clamp(mount.reload / reload));
    }

    @Override
    public void update(Unit unit, WeaponMount mount) {
        if (chargeCondition == null) {
            super.update(unit, mount);
        } else {
            // 自定义充能逻辑 (复刻 PU_V8 EnergyChargeWeapon.update)
            boolean can = unit.canShoot();
            chargeCondition.get(unit, mount);

            float
                    weaponRotation = unit.rotation - 90 + (rotate ? mount.rotation : baseRotation),
                    mountX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                    mountY = unit.y + Angles.trnsy(unit.rotation - 90, x, y),
                    bulletX = mountX + Angles.trnsx(weaponRotation, this.shootX, this.shootY),
                    bulletY = mountY + Angles.trnsy(weaponRotation, this.shootX, this.shootY),
                    shootAngle = rotate ? weaponRotation + 90 : Angles.angle(bulletX, bulletY, mount.aimX, mount.aimY) + (unit.rotation - unit.angleTo(mount.aimX, mount.aimY));

            // update continuous state
            if (continuous && mount.bullet != null) {
                if (!mount.bullet.isAdded() || mount.bullet.time >= mount.bullet.lifetime || mount.bullet.type != bullet) {
                    mount.bullet = null;
                } else {
                    mount.bullet.rotation(weaponRotation + 90);
                    mount.bullet.set(bulletX, bulletY);
                    mount.reload = reload;
                    unit.vel.add(Tmp.v1.trns(unit.rotation + 180f, mount.bullet.type.recoil));
                    if (shootSound != mindustry.gen.Sounds.none && !headless) {
                        if (mount.sound == null) mount.sound = new SoundLoop(shootSound, 1f);
                        mount.sound.update(bulletX, bulletY, true);
                    }
                }
            } else {
                mount.heat = Math.max(mount.heat - Time.delta * unit.reloadMultiplier / mount.weapon.cooldownTime, 0);

                if (mount.sound != null) {
                    mount.sound.update(bulletX, bulletY, false);
                }
            }

            // flip weapon shoot side for alternating weapons
            if (otherSide != -1 && alternate && mount.side == flipSprite &&
                    mount.reload + Time.delta * unit.reloadMultiplier > reload / 2f && mount.reload <= reload / 2f) {
                unit.mounts[otherSide].side = !unit.mounts[otherSide].side;
                mount.side = !mount.side;
            }

            // rotate if applicable
            if (rotate && (mount.rotate || mount.shoot) && can) {
                float axisX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                        axisY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);

                mount.targetRotation = Angles.angle(axisX, axisY, mount.aimX, mount.aimY) - unit.rotation;
                mount.rotation = Angles.moveToward(mount.rotation, mount.targetRotation, rotateSpeed * Time.delta);
            } else if (!rotate) {
                mount.rotation = baseRotation;
                mount.targetRotation = unit.angleTo(mount.aimX, mount.aimY);
            }

            if (mount.shoot && can &&
                    (!useAmmo || unit.ammo > 0 || !state.rules.unitAmmo || unit.team.rules().infiniteAmmo) &&
                    (!alternate || mount.side == flipSprite) &&
                    unit.vel.len() >= mount.weapon.minShootVelocity &&
                    mount.reload <= 0.0001f &&
                    Angles.within(rotate ? mount.rotation : unit.rotation + baseRotation, mount.targetRotation, mount.weapon.shootCone)) {
                shoot(unit, mount, bulletX, bulletY, shootAngle);

                mount.reload = reload;

                if (useAmmo) {
                    unit.ammo--;
                    if (unit.ammo < 0) unit.ammo = 0;
                }
            }
        }
    }

    @Override
    protected void shoot(Unit unit, WeaponMount mount, float shootX, float shootY, float rotation) {
        if (chargeCondition == null) {
            super.shoot(unit, mount, shootX, shootY, rotation);
        } else {
            // 自定义射击逻辑 (复刻 PU_V8 EnergyChargeWeapon.shoot)
            boolean delay = shoot.firstShotDelay + shoot.shotDelay > 0f;

            (delay ? chargeSound : continuous ? mindustry.gen.Sounds.none : shootSound)
                    .at(shootX, shootY, Mathf.random(soundPitchMin, soundPitchMax));

            BulletType ammo = bullet;
            float lifeScl = ammo.scaleLife ? Mathf.clamp(Mathf.dst(shootX, shootY, mount.aimX, mount.aimY) / ammo.range) : 1f;
            float charge = Math.max(0f, -mount.reload);

            shoot.shoot(mount.barrelCounter, (xOffset, yOffset, angle, shotDelay, mover) -> {
                float ang = rotation + angle + Mathf.range(inaccuracy);
                if (shotDelay > 0f) {
                    Time.run(shotDelay, () -> {
                        if (!unit.isAdded()) return;
                        mount.bullet = bulletC(unit, shootX, shootY, ang, lifeScl, charge);
                        if (!continuous) {
                            shootSound.at(shootX, shootY, Mathf.random(soundPitchMin, soundPitchMax));
                        }
                    });
                } else {
                    mount.bullet = bulletC(unit, shootX, shootY, ang, lifeScl, charge);
                }
            }, () -> mount.barrelCounter++);

            if (delay) {
                Time.run(shoot.firstShotDelay, () -> {
                    if (!unit.isAdded()) return;

                    unit.vel.add(Tmp.v1.trns(rotation + 180f, ammo.recoil));
                    Effect.shake(shake, shake, shootX, shootY);
                    mount.heat = 1f;
                    if (!continuous) {
                        shootSound.at(shootX, shootY, Mathf.random(soundPitchMin, soundPitchMax));
                    }
                });
            } else {
                unit.vel.add(Tmp.v1.trns(rotation + 180f, ammo.recoil));
                Effect.shake(shake, shake, shootX, shootY);
                mount.heat = 1f;
            }

            ejectEffect.at(shootX, shootY, rotation);
            ammo.shootEffect.at(shootX, shootY, rotation, ammo.keepVelocity ? unit : null);
            ammo.smokeEffect.at(shootX, shootY, rotation, ammo.keepVelocity ? unit : null);
            unit.apply(shootStatus, shootStatusDuration);
        }
    }

    Bullet bulletC(Unit unit, float shootX, float shootY, float angle, float lifescl, float charge) {
        float xr = Mathf.range(xRand);

        return bullet.create(unit, unit.team,
                shootX + Angles.trnsx(angle, 0, xr),
                shootY + Angles.trnsy(angle, 0, xr),
                angle, bullet.damage + charge, (1f - velocityRnd) + Mathf.random(velocityRnd), lifescl, null);
    }

    public static class ChargeMount extends WeaponMount {
        public float timer = 0f, charge = 0f;

        public ChargeMount(Weapon weapon) {
            super(weapon);
        }
    }
}
