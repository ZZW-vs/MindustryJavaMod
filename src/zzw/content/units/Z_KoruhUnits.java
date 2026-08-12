package zzw.content.units;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.gen.Sounds;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.abilities.MoveLightningAbility;
import mindustry.graphics.Pal;
import mindustry.type.Weapon;
import zzw.content.type.UnityUnitType;
import zzw.content.units.abilities.LightningBurstAbility;
import zzw.content.units.abilities.ShootArmorAbility;

/**
 * Z_KoruhUnits - PU132 koruh 阵营单位注册 (buffer/omega/cache)
 *
 * 完整移植自 PU132 UnityUnitTypes.java L3351-3487
 *
 * v155.4 适配:
 * - Sounds 在 mindustry.gen 包 (非 mindustry.content)
 * - Sounds.spark -> Sounds.shootArc
 * - Sounds.shootBig -> Sounds.shootArtillery
 * - Sounds.missile -> Sounds.shootMissile
 * - landShake 字段移除 (v155.4 不存在)
 * - shots/shotDelay 移至 ShootPattern: shoot.shots / shoot.shotDelay
 * - spacing 字段移除 (v155.4 不存在, 用 ShootPattern 子类替代)
 */
public class Z_KoruhUnits{
    public static UnityUnitType buffer, omega, cache;

    public static void load(){
        // ===== buffer (PU132 L3351-3388) =====
        buffer = new UnityUnitType("buffer"){{
            mineTier = 1;
            speed = 0.75f;
            boostMultiplier = 1.5f;
            itemCapacity = 15;
            health = 150;
            buildSpeed = 0.9f;
            engineColor = Color.valueOf("d3ddff");
            canBoost = true;

            weapons.add(new Weapon(name + "-shotgun"){{
                top = false;
                shake = 2f;
                x = 3f;
                y = 0.5f;
                shootX = 0f;
                shootY = 3.5f;
                reload = 55f;
                shoot.shotDelay = 3f;  // v155.4: shotDelay -> shoot.shotDelay
                alternate = true;
                shoot.shots = 2;  // v155.4: shots -> shoot.shots
                inaccuracy = 0f;
                ejectEffect = Fx.none;
                shootSound = Sounds.shootArc;  // v155.4: spark -> shootArc
                bullet = new LightningBulletType(){{
                    damage = 12;
                    shootEffect = Fx.hitLancer;
                    smokeEffect = Fx.none;
                    despawnEffect = Fx.none;
                    hitEffect = Fx.hitLancer;
                    keepVelocity = false;
                }};
            }});

            abilities.add(new LightningBurstAbility(120f, 8, 8, 17f, 14, Pal.lancerLaser));
        }};

        // ===== omega (PU132 L3390-3443) =====
        omega = new UnityUnitType("omega"){{
            mineTier = 2;
            mineSpeed = 1.5f;
            itemCapacity = 80;
            speed = 0.4f;
            accel = 0.36f;
            canBoost = true;
            boostMultiplier = 0.6f;
            engineColor = Color.valueOf("feb380");
            health = 350f;
            buildSpeed = 1.5f;
            rotateSpeed = 3f;

            weapons.add(new Weapon(name + "-cannon"){{
                top = false;
                x = 4f;
                y = 0f;
                shootX = 1f;
                shootY = 3f;
                recoil = 4f;
                reload = 38f;
                shoot.shots = 4;  // v155.4: shots -> shoot.shots
                // spacing = 8f;  // v155.4: spacing 移除
                inaccuracy = 8f;
                alternate = true;
                ejectEffect = Fx.none;
                shake = 3f;
                shootSound = Sounds.shootArtillery;  // v155.4: shootBig -> shootArtillery
                bullet = new MissileBulletType(2.7f, 12f){{
                    width = height = 8f;
                    shrinkX = shrinkY = 0f;
                    drag = -0.003f;
                    homingRange = 60f;
                    keepVelocity = false;
                    splashDamageRadius = 25f;
                    splashDamage = 10f;
                    lifetime = 120f;
                    trailColor = Color.gray;
                    backColor = Pal.bulletYellowBack;
                    frontColor = Pal.bulletYellow;
                    hitEffect = Fx.blastExplosion;
                    despawnEffect = Fx.blastExplosion;
                    weaveScale = 8f;
                    weaveMag = 2f;
                    status = StatusEffects.blasted;
                    statusDuration = 60f;
                }};
            }});

            String armorRegion = name + "-armor";
            abilities.add(new ShootArmorAbility(50f, 0.06f, 2f, 0.5f, armorRegion));
        }};

        // ===== cache (PU132 L3445-3487) =====
        cache = new UnityUnitType("cache"){{
            mineTier = -1;
            speed = 7f;
            drag = 0.001f;
            health = 560;
            engineColor = Color.valueOf("d3ddff");
            flying = true;
            armor = 6f;
            accel = 0.02f;

            weapons.add(new Weapon(){{
                top = false;
                shootY = 1.5f;
                reload = 70f;
                shoot.shots = 4;  // v155.4: shots -> shoot.shots
                inaccuracy = 2f;
                alternate = true;
                ejectEffect = Fx.none;
                velocityRnd = 0.2f;
                // spacing = 1f;  // v155.4: spacing 移除
                shootSound = Sounds.shootMissile;  // v155.4: missile -> shootMissile
                bullet = new MissileBulletType(5f, 21f){{
                    width = 8f;
                    height = 8f;
                    shrinkY = 0f;
                    drag = -0.003f;
                    keepVelocity = false;
                    splashDamageRadius = 20f;
                    splashDamage = 1f;
                    lifetime = 60;
                    trailColor = Color.valueOf("b6c6fd");
                    hitEffect = Fx.blastExplosion;
                    despawnEffect = Fx.blastExplosion;
                    backColor = Pal.bulletYellowBack;
                    frontColor = Pal.bulletYellow;
                    weaveScale = 8f;
                    weaveMag = 2f;
                }};
            }});

            String shieldSprite = name + "-shield";
            abilities.add(new MoveLightningAbility(10f, 14, 0.15f, 4f, 3.6f, 6f, Pal.lancerLaser, shieldSprite));
        }};
    }
}
