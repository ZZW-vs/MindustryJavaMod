package zzw.content.blocks;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.pattern.ShootPattern;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import zzw.content.Z_Items;
import zzw.content.Z_Sounds;
import zzw.content.blocks.soul.ISoulTurret;
import zzw.content.blocks.soul.RicochetBulletType;
import zzw.content.blocks.soul.SoulTurretPowerTurret;

import static mindustry.Vars.tilesize;

/**
 * fmonolith 阵营灵魂炮台注册 (PU_V8 移植版, v158 简化)
 *
 * 简化策略:
 * - 跳过 @Merge 注解系统, SoulTurretPowerTurret 直接继承 PowerTurret
 * - 简化 SoulComp: 实现 ISoulTurret 接口, 提供 souls/maxSouls/joinSoul/unjoinSoul 方法
 * - 简化 SoulInfuser: 不依赖 FloorExtractor, 直接消耗物品+电力产生灵魂, 扫描附近炮台注入
 * - 跳过灵魂实体 (Soul/MonolithSoul) 的可控机制 (玩家无法控制灵魂单位)
 * - 简化 progression: 直接在 build 内根据 soulf() 更新 shootType.damage
 *
 * 参考: PU_V8 unity/content/UnityBlocks.java L2412-2710 (ricochet/diviner/mage/blackout/shellshock/purge)
 *       PU_V8 unity/entities/merge/SoulComp.java
 *       PU_V8 unity/world/blocks/production/SoulInfuser.java
 */
public class Z_SoulTurrets {

    // ===== 6 个简单 SoulTurretPowerTurret 炮台 =====
    public static SoulTurretPowerTurret ricochet, diviner, mage, blackout, shellshock, purge;

    public static void load() {
        // ===== ricochet (PU_V8 L2412-2435) =====
        // size=1, 弹跳子弹, 不需要灵魂, 0.8~1.5倍效率
        ricochet = new SoulTurretPowerTurret("ricochet") {{
            requirements(Category.turret, ItemStack.with(Z_Items.monolite, 40));
            size = 1;
            health = 200;
            consumePower(1f);
            reload = 60f;
            recoil = 0.03f;
            range = 180f;
            shootCone = 15f;
            ammoUseEffect = Fx.none;
            inaccuracy = 2f;
            rotateSpeed = 12f;
            shootSound = Z_Sounds.energyBolt;
            shootType = new RicochetBulletType(7f, 80f) {{
                width = 9f;
                height = 12f;
                ammoMultiplier = 4;
                lifetime = 30f;
                frontColor = Color.white;
                backColor = trailColor = Pal.lancerLaser;
            }};
            requireSoul = false;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.5f;
        }};

        // ===== diviner (PU_V8 L2437-2460) =====
        // size=1, 短距激光, 不需要灵魂, 0.8~1.5倍效率
        diviner = new SoulTurretPowerTurret("diviner") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 15, Z_Items.monolite, 30));
            size = 1;
            health = 240;
            consumePower(1.5f);
            reload = 30f;
            range = 75f;
            targetGround = true;
            targetAir = false;
            shootSound = Z_Sounds.energyBolt;
            shootType = new LaserBulletType(200f) {{
                length = 85f;
            }};
            requireSoul = false;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.5f;
        }};

        // ===== mage (PU_V8 L2523-2550) =====
        // size=2, 3连发闪电, 不需要灵魂, maxSouls=5, 0.8~1.6倍效率
        mage = new SoulTurretPowerTurret("mage") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 75, Items.silicon, 50, Z_Items.monolite, 25));
            size = 2;
            health = 600;
            consumePower(2.5f);
            range = 120f;
            reload = 48f;
            shootCone = 15f;
            shoot = new ShootPattern();
            shoot.shots = 3;
            shoot.shotDelay = 2f;
            shootSound = Sounds.shootLancer;  // v158 无 Sounds.spark
            recoil = 2.5f;
            rotateSpeed = 10f;
            shootType = new LightningBulletType() {{
                lightningLength = 20;
                damage = 128f;
            }};
            requireSoul = false;
            maxSouls = 5;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.6f;
        }};

        // ===== blackout (PU_V8 L2552-2598) =====
        // size=2, 范围爆炸+定身, 不需要灵魂, maxSouls=5, 0.8~1.6倍效率
        blackout = new SoulTurretPowerTurret("blackout") {{
            requirements(Category.turret, ItemStack.with(Items.graphite, 85, Items.titanium, 25, Z_Items.monolite, 125));
            size = 2;
            health = 720;
            consumePower(3f);
            reload = 140f;
            range = 200f;
            rotateSpeed = 10f;
            recoil = 3f;
            shootSound = Sounds.shootSpectre;  // ★ v158 无 Sounds.shootBig, 用 shootSpectre 替代
            targetGround = true;
            targetAir = false;
            shootType = new BasicBulletType(6f, 180f, "shell") {{
                lifetime = 35f;
                width = 20f;
                height = 20f;
                frontColor = Color.valueOf("4a5866");  // UnityPal.monolith
                backColor = Color.valueOf("2c3640");    // UnityPal.monolithDark
                hitEffect = despawnEffect = Fx.blastExplosion;
                splashDamage = 90f;
                splashDamageRadius = 3.2f * tilesize;
            }

            // ★ 原版 blackout: hitEntity 时在 splashDamageRadius 范围内对所有敌人施加 unmoving(60f) + disarmed(60f)
            @Override
            public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
                super.hitEntity(b, other, initialHealth);
                Units.nearbyEnemies(b.team, b.x, b.y, splashDamageRadius, u -> {
                    if (u.isValid()) {
                        u.apply(StatusEffects.unmoving, 60f);
                        u.apply(StatusEffects.disarmed, 60f);
                    }
                });
            }
            };

            requireSoul = false;
            maxSouls = 5;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.6f;
        }};

        // ===== shellshock (PU_V8 L2600-2622) =====
        // size=2, 弹跳子弹, 不需要灵魂, maxSouls=5, 0.8~1.6倍效率
        shellshock = new SoulTurretPowerTurret("shellshock") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 90, Items.graphite, 100, Z_Items.monolite, 80));
            size = 2;
            health = 720;
            consumePower(2f);
            reload = 75f;
            range = 260f;
            shootCone = 3f;
            ammoUseEffect = Fx.none;
            rotateSpeed = 10f;
            shootType = new RicochetBulletType(8.5f, 168f) {{
                width = 12f;
                height = 16f;
                ammoMultiplier = 4;
                lifetime = 35f;
                pierceCap = 5;
                trailLength = 7;
                frontColor = Color.white;
                backColor = trailColor = Pal.lancerLaser;
            }};
            shootSound = Z_Sounds.energyBolt;
            requireSoul = false;
            maxSouls = 5;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.6f;
        }};

        // ===== purge (PU_V8 L2688-2710) =====
        // size=3, 弹跳子弹, 不需要灵魂, maxSouls=7, 0.7~1.67倍效率
        purge = new SoulTurretPowerTurret("purge") {{
            requirements(Category.turret, ItemStack.with(Items.plastanium, 75, Items.lead, 350, Z_Items.monolite, 200, Z_Items.monolithAlloy, 75));
            size = 3;
            health = 1680;
            consumePower(3f);
            reload = 90f;
            range = 360f;
            shootCone = 3f;
            ammoUseEffect = Fx.none;
            rotateSpeed = 8f;
            shootType = new RicochetBulletType(10f, 528f) {{
                width = 14f;
                height = 18f;
                ammoMultiplier = 4;
                lifetime = 40f;
                pierceCap = 8;
                trailLength = 8;
                frontColor = Color.white;
                backColor = trailColor = Pal.lancerLaser;
            }};
            shootSound = Z_Sounds.energyBolt;
            requireSoul = false;
            maxSouls = 7;
            efficiencyFrom = 0.7f;
            efficiencyTo = 1.67f;
        }};
    }
}
