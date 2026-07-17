package zzw.content.blocks;

import arc.graphics.Color;
import arc.math.Mathf;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.FlakBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.entities.pattern.ShootAlternate;
import mindustry.entities.pattern.ShootSpread;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.LaserTurret;
import mindustry.world.consumers.ConsumeLiquidFilter;
import zzw.content.Z_Bullets.ArcBulletType;
import zzw.content.Z_Bullets.BeamBulletType;
import zzw.content.Z_Bullets.DecayBasicBulletType;
import zzw.content.Z_Bullets.EphemeronBulletType;
import zzw.content.Z_Bullets.EphemeronPairBulletType;
import zzw.content.Z_Bullets.GravitonLaserBulletType;
import zzw.content.Z_Bullets.RoundLaserBulletType;
import zzw.content.Z_Bullets.ShieldBulletType;
import zzw.content.Z_Bullets.SmokeBulletType;
import zzw.content.Z_Bullets.SparkingContinuousLaserBulletType;
import zzw.content.Z_Bullets.SingularityBulletType;
import zzw.content.Z_Bullets.TriangleBulletType;
import zzw.content.Z_Bullets.VelocityLaserBoltBulletType;
import zzw.content.Z_Bullets.AcceleratingLaserBulletType;
import zzw.content.Z_Items;
import zzw.content.Z_Sounds;
import zzw.content.units.bullets.ChangeTeamLaserBulletType;
import zzw.content.blocks.turrets.AbsorberTurret;
import zzw.content.blocks.turrets.BarrelsItemTurret;
import zzw.content.blocks.turrets.BigLaserTurret;
import zzw.content.blocks.turrets.BlockOverdriveTurret;
import zzw.content.blocks.turrets.OrbTurret;
import zzw.content.blocks.turrets.RampupPowerTurret;
import zzw.content.blocks.turrets.ShieldTurret;

import static mindustry.Vars.tilesize;

/**
 * PU_V8 炮台注册 (非经验系统炮台)
 * 参考: PU132源代码 main/src/unity/content/UnityBlocks.java
 *
 * 简化策略 (v158 兼容):
 * - PU132 自定义特效 (ShootFx/HitFx/UnityFx) → v158 原生特效
 * - PU132 自定义声音 (UnitySounds) → v158 原生声音
 * - PU132 自定义物品 (UnityItems) → Z_Items (已移植)
 * - PU132 自定义颜色 (UnityPal) → v158 Pal
 * - 复杂子弹类型 (DecayBasicBulletType 等) → BasicBulletType 简化
 * - "unity-electric-shell" 贴图 → electric-shell.png (已有)
 */
public class Z_Turrets {

    // ===== flight faction (光子科技) =====
    // electron: T1 电力炮 (BasicBulletType 电球)
    public static PowerTurret electron;
    // proton: T2 质子炮 (ArtilleryBulletType 电球+闪电)
    public static PowerTurret proton;
    // neutron: T3 中子炮 (FlakBulletType 电球)
    public static PowerTurret neutron;
    // gluon: T4 胶子炮 (BasicBulletType 能量球)
    public static PowerTurret gluon;
    // photon: 激光炮 (LaserTurret)
    public static LaserTurret photon;
    // graviton: 重力子激光炮 (LaserTurret)
    public static LaserTurret graviton;

    // ===== 28个新增炮台 (PU_V8 移植) =====
    // 简单 (7): apparition, electrobomb, celsius, kelvin, current, muon, higgsBoson
    public static ItemTurret apparition, electrobomb;
    public static PowerTurret celsius, kelvin, current, muon, higgsBoson;
    // 中等 (21): caster, storm, eclipse, wBoson, singularity, orb, plasma, shockwire, shielder,
    //           zBoson, ephemeron, ghost, banshee, fallout, catastrophe, calamity, extinction,
    //           buffTurret, upgradeTurret, absorber, orbTurret
    public static PowerTurret caster, storm, wBoson, singularity, orb, plasma, ephemeron;
    public static LaserTurret eclipse, shockwire, fallout;
    public static BigLaserTurret catastrophe, calamity, extinction;
    public static RampupPowerTurret zBoson;
    public static ShieldTurret shielder;
    public static BarrelsItemTurret ghost, banshee;
    public static BlockOverdriveTurret buffTurret, upgradeTurret;
    public static AbsorberTurret absorber;
    public static OrbTurret orbTurret;
    // 西诺腐蚀者 (LaserTurret + ChangeTeamLaserBulletType)
    public static LaserTurret xenoCorruptor;

    public static void load() {
        // ===== electron (T1 电力炮, PU132 L632-662) =====
        // PU132: BasicBulletType(9f, 34f) + electric-shell 贴图 + lancerLaser 颜色
        // 简化: 移除 blueTriangleTrail 特效 (PU132 自定义)
        electron = new PowerTurret("electron") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 110, Items.silicon, 75, Z_Items.luminum, 165, Items.titanium, 125));
            size = 3;
            health = 2540;
            reload = 60f;
            coolantMultiplier = 2f;
            range = 170f;
            consumePower(6.6f);
            heatColor = Pal.turretHeat;
            shootEffect = Fx.lancerLaserShoot;
            shootSound = Sounds.shootArc;  // ★ v158 无 Sounds.pew, 用 shootArc (电弧炮音效) 替代
            shootType = new BasicBulletType(9f, 34f) {{
                lifetime = 22f;
                width = 12f;
                height = 19f;
                shrinkX = 0f;
                shrinkY = 0f;
                backColor = lightColor = hitColor = Pal.lancerLaser;
                frontColor = Color.white;
                hitEffect = Fx.hitLancer;
            }};
        }};

        // ===== proton (T2 质子炮, PU132 L664-705) =====
        // PU132: ArtilleryBulletType(8f, 44f) + electric-shell + 闪电扩散
        // 简化: 保留 lightning 字段 (v158 支持)
        proton = new PowerTurret("proton") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 110, Items.silicon, 75, Z_Items.luminum, 165, Items.titanium, 135));
            size = 4;
            health = 2540;
            reload = 60f;
            range = 245f;
            shootCone = 20f;
            heatColor = Pal.turretHeat;
            rotateSpeed = 1.5f;
            recoil = 4f;
            consumePower(4.9f);
            targetAir = false;
            shootEffect = Fx.lancerLaserShoot;
            shootType = new ArtilleryBulletType(8f, 44f) {{
                lifetime = 35f;
                width = 18f;
                splashDamage = 23f;
                splashDamageRadius = 45f;
                height = 27f;
                shrinkX = 0f;
                shrinkY = 0f;
                hitSize = 15f;
                hitEffect = Fx.hitLancer;
                hittable = false;
                collides = false;
                backColor = lightColor = hitColor = lightningColor = Pal.lancerLaser;
                frontColor = Color.white;
                lightning = 3;
                lightningDamage = 18f;
                lightningLength = 10;
                lightningLengthRand = 6;
            }};
        }};

        // ===== neutron (T3 中子炮, PU132 L707-746) =====
        // PU132: FlakBulletType(8.7f, 7f) + electric-shell
        neutron = new PowerTurret("neutron") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 110, Items.silicon, 75, Z_Items.luminum, 165, Items.titanium, 135));
            size = 4;
            health = 2520;
            reload = 10f;
            range = 235f;
            shootCone = 20f;
            heatColor = Pal.turretHeat;
            rotateSpeed = 3.9f;
            recoil = 4f;
            consumePower(4.9f);
            inaccuracy = 3.4f;
            shootEffect = Fx.lancerLaserShoot;
            shootType = new FlakBulletType(8.7f, 7f) {{
                lifetime = 30f;
                width = 8f;
                height = 14f;
                splashDamage = 28f;
                splashDamageRadius = 34f;
                shrinkX = 0f;
                shrinkY = 0f;
                hitSize = 7f;
                hitEffect = Fx.hitLancer;
                collides = true;
                collidesGround = true;
                hittable = false;
                backColor = lightColor = hitColor = Pal.lancerLaser;
                frontColor = Color.white;
            }};
        }};

        // ===== gluon (T4 胶子炮, PU132 L748-763) =====
        // PU132: UnityBullets.gluonEnergyBall (复杂自定义子弹)
        // 简化: 用 BasicBulletType 替代, 移除自定义特效
        gluon = new PowerTurret("gluon") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 300, Z_Items.luminum, 430, Items.titanium, 190, Items.thorium, 110, Z_Items.lightAlloy, 15));
            size = 4;
            health = 5000;
            reload = 90f;
            coolantMultiplier = 3f;
            shootCone = 30f;
            range = 200f;
            heatColor = Pal.turretHeat;
            rotateSpeed = 4.3f;
            recoil = 2f;
            consumePower(1.9f);
            shootSound = Z_Sounds.gluonShoot;  // ★ 原版 UnitySounds.gluonShoot (light/gluon-shoot.ogg)
            shootType = new BasicBulletType(8f, 60f) {{
                lifetime = 60f;
                width = 16f;
                height = 16f;
                splashDamage = 40f;
                splashDamageRadius = 50f;
                shrinkX = 0f;
                shrinkY = 0f;
                backColor = lightColor = hitColor = Pal.lancerLaser;
                frontColor = Color.white;
                hitEffect = Fx.hitLancer;
                despawnEffect = Fx.explosion;
            }};
        }};

        // ===== photon (激光炮, PU132 L590-610) =====
        // PU132: LaserTurret + LaserBulletType
        photon = new LaserTurret("photon") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 50, Items.silicon, 35, Z_Items.luminum, 65, Items.titanium, 65));
            size = 2;
            health = 1280;
            reload = 100f;
            shootCone = 30f;
            range = 120f;
            consumePower(4.5f);
            heatColor = Pal.turretHeat;
            loopSound = Sounds.beamLustre;  // ★ 原版 Sounds.respawning, v158 无此音效用 beamLustre 替代
            shootType = new ContinuousLaserBulletType(16f) {{
                incendChance = -1f;
                length = 130f;
                width = 4f;
                colors = new Color[]{Pal.lancerLaser.cpy().a(3.75f), Pal.lancerLaser, Color.white};
                lightColor = hitColor = Pal.lancerLaser;
            }};
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability < 0.1f, 0.2f)).boost().update(false);
        }};

        // ===== graviton (重力子激光炮, PU132 L611-631) =====
        // ★完整移植 PU132: GravitonLaserBulletType - 牵引激光 (吸引敌方单位)
        graviton = new LaserTurret("graviton") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 110, Items.graphite, 90, Items.silicon, 70, Z_Items.luminum, 180, Items.titanium, 135));
            size = 3;
            health = 2780;
            reload = 150f;
            recoil = 2f;  // ★ 原版 recoilAmount=2f
            shootCone = 30f;
            range = 230f;
            consumePower(5.75f);
            heatColor = Pal.turretHeat;
            loopSound = Z_Sounds.xenoBeam;  // ★ 原版 UnitySounds.xenoBeam (advance/xeno-beam.ogg)
            shootType = new GravitonLaserBulletType(0.8f) {{
                length = 260f;
                knockback = -5f;  // ★ 重力吸引: 负值拉向炮台
                incendChance = -1f;
                // ★ 提高颜色可见度, 原版过透明看不清
                colors = new Color[]{Color.valueOf("3a3a4c").cpy().a(0.55f), Pal.lancerLaser.cpy().a(0.8f)};
                strokes = new float[]{2.4f, 1.8f};
                width = 9f;
            }};
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability < 0.1f, 0.25f)).boost().update(false);
        }};

        // ===========================================================================
        // 简单炮台 (7): apparition, electrobomb, celsius, kelvin, current, muon, higgsBoson
        // ===========================================================================

        // ===== apparition (PU_V8 L384-409, ItemTurret 多发交替) =====
        // PU_V8: ammo(graphite, standardDenseLarge) 等, v158 无 standardDenseBig, 用 BasicBulletType 自建
        apparition = new ItemTurret("apparition") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 350, Items.graphite, 380, Items.silicon, 360, Items.plastanium, 200, Items.thorium, 220, Z_Items.umbrium, 370, Items.surgeAlloy, 290));
            size = 5;
            health = 3975;
            range = 235f;
            reload = 6f;
            coolantMultiplier = 0.5f;
            inaccuracy = 3f;
            // ★ PU_V8: spread=12, shots=2, alternate=true → v158: ShootAlternate(12f) 自带 shots=2 alternate
            shoot = new ShootAlternate(12f);
            shootSound = Sounds.shootSpectre;  // ★ v158 无 Sounds.shootBig, 用 shootSpectre (大型炮弹) 替代
            recoil = 3f;
            rotateSpeed = 4.5f;
            // ★ 子弹颜色按 PU_V8/v7 标准区分不同弹药类型 (不再统一白色)
            ammo(Items.graphite, new BasicBulletType(3.63f, 98f) {{  // ★ speed×1.1, damage×1.4 (原版 standardDenseLarge×1.4)
                lifetime = 35f; width = 18f; height = 21f;
                splashDamage = 30f; splashDamageRadius = 25f;
                backColor = Pal.darkerGray;
                trailColor = hitColor = Pal.darkerMetal;
                frontColor = Color.white;
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }}, Items.silicon, new BasicBulletType(3.63f, 86f) {{  // ★ speed×1.1, damage×1.23 (原版 standardHomingLarge×1.23)
                lifetime = 35f; width = 17f; height = 20f;
                homingPower = 0.09f; reloadMultiplier = 1.3f;
                backColor = Pal.darkerGray;
                trailColor = hitColor = Pal.darkerMetal;
                frontColor = Color.white;
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }}, Items.pyratite, new BasicBulletType(3.63f, 98f) {{  // ★ speed×1.1, damage×1.4 (原版 standardIncendiaryLarge×1.4)
                lifetime = 35f; width = 18f; height = 21f;
                splashDamage = 30f; splashDamageRadius = 25f;
                makeFire = true; status = mindustry.content.StatusEffects.burning;
                backColor = trailColor = hitColor = Color.valueOf("ffaa5f");
                frontColor = Color.valueOf("ffd49a");
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }}, Items.thorium, new BasicBulletType(3.63f, 161f) {{  // ★ speed×1.1, damage×1.4 (原版 standardThoriumLarge×1.4)
                lifetime = 35f; width = 18f; height = 21f;
                pierceCap = 2; pierce = true;
                backColor = Pal.darkerGray;
                trailColor = hitColor = Color.valueOf("f4ba6e");
                frontColor = Color.white;
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }});
        }};

        // ===== electrobomb (PU_V8 L1241-1266, ItemTurret + surgeBomb) =====
        // PU_V8: ammo(sparkAlloy, surgeBomb) + powerCond
        electrobomb = new ItemTurret("electrobomb") {{
            requirements(Category.turret, ItemStack.with(Items.titanium, 360, Items.thorium, 630, Items.silicon, 240, Z_Items.sparkAlloy, 420));
            health = 3650;
            size = 5;
            range = 400f;
            minRange = 60f;
            reload = 320f;
            coolantMultiplier = 2f;
            shootCone = 20f;
            inaccuracy = 0f;
            targetAir = false;
            shootEffect = Fx.none;
            smokeEffect = Fx.none;
            consumePowerCond(10f, b -> ((mindustry.world.blocks.defense.turrets.Turret.TurretBuild)b).isActive());
            shootSound = Sounds.shootLaser;  // ★ 原版 Sounds.laser, v158 无 laser 用 shootLaser 替代
            ammo(Z_Items.sparkAlloy, new BasicBulletType(7f, 100f, "large-bomb") {{  // ★ sprite=large-bomb
                width = height = 30f;
                backColor = Pal.surge;
                frontColor = Color.white;
                mixColorTo = Color.white;  // ★ 原版 mixColorTo
                hitSound = Sounds.explosionTitan;  // ★ v158 无 Sounds.plasmaboom, 用 explosionTitan (钛爆炸) 替代
                despawnShake = 4f;
                collidesAir = false;
                lifetime = 70f;
                despawnEffect = Fx.massiveExplosion;
                hitEffect = Fx.massiveExplosion;
                keepVelocity = false;
                collides = false;
                splashDamage = 680f;
                splashDamageRadius = 120f;
                lightning = 10;
                lightningDamage = 136f;
                lightningLength = 20;
                spin = 2f;  // ★ 原版 spin (旋转动画)
                shrinkX = 0.7f; shrinkY = 0.7f;  // ★ 原版 shrinkX/Y
                // ★ frag 子弹 (8 个 plasmaFragTriangle)
                fragBullets = 8;
                fragLifeMin = 0.8f; fragLifeMax = 1.1f;
                // ★ v158 BulletType 无 scaleVelocity 字段, 省略 (效果: fragBullet 继承母弹速度)
                fragBullet = new TriangleBulletType(11, 10, 4.5f, 90f) {{
                    lifetime = 160f;
                    trailWidth = 3f; trailLength = 8;
                    drag = 0.05f;
                    collides = false;
                    castsLightning = true;
                }};
            }});
        }};

        // ===== celsius (PU_V8 L3211-3230, PowerTurret + SmokeBulletType) =====
        celsius = new PowerTurret("celsius") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 20, Z_Items.xenium, 15, Items.titanium, 30, Z_Items.advanceAlloy, 25));
            health = 780;
            size = 1;
            reload = 3f;
            range = 47f;
            shootCone = 50f;
            heatColor = Color.valueOf("ccffff");
            inaccuracy = 9.2f;
            rotateSpeed = 7.5f;
            shoot = new ShootSpread(2, 0f);
            shoot.shots = 2;
            recoil = 1f;
            consumePower(13.9f);
            targetAir = true;
            shootSound = Sounds.shootFlame;
            shootType = new SmokeBulletType(4.7f, 32f) {{
                drag = 0.034f;
                lifetime = 18f;
                hitSize = 4f;
                shootEffect = Fx.none;
                smokeEffect = Fx.none;
                hitEffect = Fx.hitLiquid;
                despawnEffect = Fx.none;
                collides = true;
                collidesTiles = true;
                collidesAir = true;
                pierce = true;
                statusDuration = 770f;
                status = mindustry.content.StatusEffects.burning;  // ★ 原版 blueBurn, 用 burning 替代 (v158 无自定义 blueBurn)
            }};
        }};

        // ===== kelvin (PU_V8 L3232-3252, PowerTurret + SmokeBulletType) =====
        kelvin = new PowerTurret("kelvin") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 80, Z_Items.xenium, 35, Items.titanium, 90, Z_Items.advanceAlloy, 50));
            health = 2680;
            size = 2;
            reload = 3f;
            range = 100f;
            shootCone = 50f;
            heatColor = Color.valueOf("ccffff");
            inaccuracy = 9.2f;
            rotateSpeed = 6.5f;
            shoot = new ShootSpread(2, 6f);
            recoil = 1f;
            consumePower(13.9f);
            targetAir = true;
            shootSound = Sounds.shootFlame;
            shootType = new SmokeBulletType(4.7f, 16f) {{
                drag = 0.016f;
                lifetime = 32f;
                hitSize = 4f;
                shootEffect = Fx.none;
                smokeEffect = Fx.none;
                hitEffect = Fx.hitLiquid;
                despawnEffect = Fx.none;
                collides = true;
                collidesTiles = true;
                collidesAir = true;
                pierce = true;
                statusDuration = 770f;
                status = mindustry.content.StatusEffects.burning;  // ★ 原版 blueBurn, 用 burning 替代
            }};
        }};

        // ===== current (PU_V8 L1204-1222, PowerTurret + LaserBulletType) =====
        // PU_V8: currentStroke = LaserBulletType(450) with lightning
        current = new PowerTurret("current") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 280, Items.lead, 295, Items.silicon, 260, Z_Items.sparkAlloy, 65));
            size = 3;
            health = 2400;
            range = 220f;
            reload = 120f;
            coolantMultiplier = 2f;
            shootCone = 0.01f;
            inaccuracy = 0f;
            // v158 无 chargeTime/chargeEffects/chargeMaxDelay 字段, 用 shoot.firstShotDelay 替代充能时间
            shoot.firstShotDelay = 60f;
            consumePower(6.8f);
            shootType = new LaserBulletType(450f) {{
                lifetime = 65f;
                width = 20f;
                length = 430f;
                lightningSpacing = 35f;
                lightningLength = 5;
                lightningDelay = 1.1f;
                lightningLengthRand = 15;
                lightningDamage = 50f;
                lightningAngleRand = 40f;
                largeHit = true;
                lightColor = lightningColor = Pal.surge;
                sideAngle = 15f;
                sideWidth = 0f;
                sideLength = 0f;
                colors = new Color[]{Pal.surge.cpy(), Pal.surge, Color.white};
                // v158 充能特效在 BulletType.chargeEffect 上
                chargeEffect = Fx.lightningShoot;
            }};
            shootSound = Sounds.shootMeltdown;  // ★ 原版 Sounds.laserbig, v158 用 shootMeltdown (大型激光射击) 替代
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability <= 0.1f, 0.52f)).boost();
        }};

        // ===== muon (PU_V8 L951-987, PowerTurret + RoundLaserBulletType) =====
        muon = new PowerTurret("muon") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 290, Z_Items.luminum, 430, Items.titanium, 190, Items.thorium, 120, Z_Items.lightAlloy, 25));
            size = 8;
            health = 9800;
            range = 310f;
            shoot = new ShootSpread(9, 12f);
            shoot.shots = 9;
            reload = 90f;
            coolantMultiplier = 1.9f;
            shootCone = 80f;
            consumePower(18f);
            shake = 5f;
            recoil = 8f;
            shootY = size * tilesize / 2f - 8f;
            shootSound = Z_Sounds.muonShoot;  // ★ 原版 UnitySounds.muonShoot (light/muon-shoot.ogg)
            rotateSpeed = 1.9f;
            heatColor = Pal.turretHeat;
            shootType = new RoundLaserBulletType(200f) {{
                length = 330f;
                width = 3.8f;
                hitSize = 13f;
                hitEffect = Fx.hitLancer;
                despawnEffect = Fx.none;
                drawSize = 460f;
                shootEffect = Fx.lightningShoot;
                smokeEffect = Fx.none;
            }};
        }};

        // ===== higgsBoson (PU_V8 L891-924, PowerTurret + RoundLaserBulletType) =====
        higgsBoson = new PowerTurret("higgs-boson") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 290, Z_Items.luminum, 430, Items.titanium, 190, Items.thorium, 120, Z_Items.lightAlloy, 20));
            size = 6;
            health = 6000;
            reload = 13f;
            shoot = new ShootAlternate(2);
            shoot.shots = 2;
            ((ShootAlternate)shoot).spread = 17.25f;
            range = 260f;
            shootCone = 20f;
            heatColor = Pal.turretHeat;
            coolantMultiplier = 3.4f;
            rotateSpeed = 2.2f;
            recoil = 1.5f;
            consumePower(10.4f);
            shootSound = Z_Sounds.higgsBosonShoot;  // ★ 原版 UnitySounds.higgsBosonShoot (light/higgs-boson-shoot.ogg)
            shootType = new RoundLaserBulletType(85f) {{
                length = 270f;
                width = 5.8f;
                hitSize = 13f;
                drawSize = 460f;
                shootEffect = Fx.none;
                smokeEffect = Fx.none;
            }};
        }};

        // ===========================================================================
        // 中等炮台 (21)
        // ===========================================================================

        // ===== caster (PU_V8 L3254-3284, PowerTurret + ArcBulletType) =====
        caster = new PowerTurret("arc-caster") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 20, Z_Items.xenium, 15, Items.titanium, 30, Z_Items.advanceAlloy, 25));
            size = 3;
            health = 4600;
            range = 190f;
            reload = 120f;
            shootCone = 30f;
            inaccuracy = 9.2f;
            rotateSpeed = 5.5f;
            recoil = 1f;
            consumePower(9.4f);
            heatColor = Pal.turretHeat;
            shootSound = Sounds.shootFlame;
            shootEffect = Fx.none;
            // v158 无 chargeTime/chargeMaxDelay/chargeEffects/chargeEffect 字段, 用 shoot.firstShotDelay 替代
            shoot.firstShotDelay = 51f;
            shootType = new ArcBulletType(4.6f, 8f) {{
                lifetime = 43f;
                hitSize = 21f;
                lightningChance1 = 0.5f;
                lightningDamage1 = 29f;
                lightningChance2 = 0.2f;
                lightningDamage2 = 14f;
                length1 = 11;
                lengthRand1 = 7;
            }};
        }};

        // ===== storm (PU_V8 L3286-3318, PowerTurret + ArcBulletType) =====
        storm = new PowerTurret("arc-storm") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 80, Z_Items.xenium, 35, Items.titanium, 90, Z_Items.advanceAlloy, 50));
            size = 4;
            health = 7600;
            range = 210f;
            reload = 180f;
            shoot = new ShootSpread(5, 0f);
            shoot.shots = 5;
            shootCone = 30f;
            inaccuracy = 11.2f;
            rotateSpeed = 5.5f;
            recoil = 2f;
            consumePower(33.4f);
            heatColor = Pal.turretHeat;
            shootSound = Sounds.shootFlame;
            shootEffect = Fx.none;
            // v158 无 chargeTime/chargeMaxDelay/chargeEffects/chargeEffect 字段, 用 shoot.firstShotDelay 替代充能时间
            shoot.firstShotDelay = 51f;
            chargeSound = Sounds.shootLancer;
            shootType = new ArcBulletType(4.6f, 8.6f) {{
                lifetime = 53f;
                hitSize = 28f;
                radius = 13f;
                lightningChance1 = 0.7f;
                lightningDamage1 = 31f;
                lightningChance2 = 0.3f;
                lightningDamage2 = 17f;
                length1 = 13;
                lengthRand1 = 9;
            }};
        }};

        // ===== eclipse (PU_V8 L3320-3367, LaserTurret + AcceleratingLaserBulletType) =====
        eclipse = new LaserTurret("blue-eclipse") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 620, Items.titanium, 520, Items.surgeAlloy, 720, Items.silicon, 760, Items.phaseFabric, 120, Z_Items.xenium, 620, Z_Items.advanceAlloy, 680));
            size = 7;
            health = 9000;
            range = 340f;
            reload = 280f;
            coolantMultiplier = 2.4f;
            shootCone = 40f;
            consumePower(19f);
            shake = 3f;
            shootEffect = Fx.shootBigSmoke2;
            recoil = 8f;
            shootSound = Sounds.shootLancer;  // ★ v158 无 Sounds.laser, 用 shootLancer (激光炮射击) 替代
            loopSound = Z_Sounds.eclipseBeam;  // ★ 原版 UnitySounds.eclipseBeam (advance/eclipse-beam.ogg)
            loopSoundVolume = 2.5f;
            heatColor = Pal.lancerLaser;
            rotateSpeed = 1.9f;
            shootDuration = 320f;
            firingMoveFract = 0.12f;
            shootY = size * tilesize / 2f - recoil;
            shootType = new AcceleratingLaserBulletType(390f) {{
                colors = new Color[]{Color.valueOf("59a7ff55"), Color.valueOf("59a7ffaa"), Color.valueOf("a3e3ff"), Color.white};
                width = 29.2f;
                collisionWidth = 12f;
                knockback = 2.2f;
                lifetime = 18f;
                accel = 0f;
                fadeInTime = 0f;
                fadeTime = 18f;
                maxLength = 490f;
                shootEffect = Fx.none;
                smokeEffect = Fx.none;
                hitEffect = Fx.hitLancer;
            }};
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.4f && liquid.flammability < 0.1f, 2.1f)).boost().update(false);
        }};

        // ===== wBoson (PU_V8 L765-838, PowerTurret + DecayBasicBulletType) =====
        wBoson = new PowerTurret("w-boson") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 300, Z_Items.luminum, 430, Items.titanium, 190, Items.thorium, 110, Z_Items.lightAlloy, 15));
            health = 4000;
            size = 5;
            reload = 90f;
            range = 250f;
            rotateSpeed = 2.5f;
            shootCone = 20f;
            heatColor = Pal.turretHeat;
            // v158 无 chargeTime/chargeEffect/chargeBeginEffect 字段, 用 shoot.firstShotDelay 替代充能时间
            shoot.firstShotDelay = 38f;
            chargeSound = Sounds.shootLancer;
            consumePower(8.6f);
            shootType = new DecayBasicBulletType(8.5f, 24f) {{
                drag = 0.026f;
                lifetime = 48f;
                hittable = absorbable = collides = false;
                backColor = trailColor = hitColor = lightColor = Pal.lancerLaser;
                shootEffect = smokeEffect = Fx.none;
                hitEffect = Fx.hitLancer;
                despawnEffect = Fx.hitLancer;
                frontColor = Color.white;
                height = 13f;
                width = 12f;
                decayBullet = new BasicBulletType(4.8f, 24f) {{
                    drag = 0.04f;
                    lifetime = 18f;
                    pierce = true;
                    pierceCap = 3;
                    height = 9f;
                    width = 8f;
                    backColor = trailColor = hitColor = lightColor = Pal.lancerLaser;
                    hitEffect = Fx.hitLancer;
                    despawnEffect = Fx.hitLancer;
                    frontColor = Color.white;
                    hittable = false;
                }};
                fragBullet = decayBullet;
                fragBullets = 12;
                fragVelocityMin = 0.75f;
                fragVelocityMax = 1.25f;
                fragLifeMin = 1.2f;
                fragLifeMax = 1.3f;
            }};
        }};

        // ===== singularity (PU_V8 L926-949, PowerTurret + singularityEnergyBall -> 黑洞子弹) =====
        // ★完整移植 PU_V8: 发射能量球, 接近敌人时变成黑洞, 吸引并伤害范围内敌方单位/建筑
        singularity = new PowerTurret("singularity") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 290, Z_Items.luminum, 430, Items.titanium, 190, Items.thorium, 120, Z_Items.lightAlloy, 20));
            size = 7;
            health = 9800;
            reload = 220f;
            coolantMultiplier = 1.1f;
            shootCone = 30f;
            range = 310f;
            heatColor = Pal.turretHeat;
            rotateSpeed = 3.3f;
            recoil = 6f;
            consumePower(39.3f);
            shootSound = Z_Sounds.singularityShoot;  // ★ 原版 UnitySounds.singularityShoot (light/singularity-shoot.ogg)
            shootType = new BasicBulletType(6.6f, 7f) {
                {
                    lifetime = 110f;
                    drag = 0.018f;
                    pierce = pierceBuilding = true;
                    hitSize = 9f;
                    despawnEffect = hitEffect = Fx.none;
                    backColor = Pal.lancerLaser;
                    frontColor = Color.white;
                }

                @Override
                public void update(mindustry.gen.Bullet b) {
                    super.update(b);
                    // 接近敌人时变成黑洞
                    if (mindustry.entities.Units.closestTarget(b.team, b.x, b.y, 20f) != null) {
                        // 创建黑洞子弹
                        SingularityBulletType blackHole = new SingularityBulletType(26f);
                        blackHole.create(b, b.x, b.y, 0f);
                        b.remove();
                    }
                }

                @Override
                public void draw(mindustry.gen.Bullet b) {
                    arc.graphics.g2d.Draw.color(Pal.lancerLaser);
                    arc.graphics.g2d.Fill.circle(b.x, b.y, 7f + b.fout() * 1.5f);
                    arc.graphics.g2d.Draw.color(Color.white);
                    arc.graphics.g2d.Fill.circle(b.x, b.y, 5.5f + b.fout());
                }
            };
        }};

        // ===== orb (PU_V8 L1164-1186, PowerTurret + 自定义orb子弹) =====
        // PU_V8: orb 子弹带闪电攻击附近敌人
        orb = new PowerTurret("orb") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 55, Items.lead, 30, Items.graphite, 25, Items.silicon, 35, Z_Items.imberium, 20));
            size = 2;
            health = 480;
            range = 145f;
            reload = 130f;
            coolantMultiplier = 2f;
            shootCone = 0.1f;
            shoot.shots = 1;
            inaccuracy = 12f;
            // v158 无 chargeTime/chargeEffects/chargeMaxDelay/chargeEffect/chargeBeginEffect 字段, 用 shoot.firstShotDelay 替代充能时间
            shoot.firstShotDelay = 65f;
            chargeSound = Sounds.shootLancer;
            consumePower(4.2069f);
            targetAir = false;
            shootType = new BulletType() {
                {
                    lifetime = 240;
                    speed = 1.24f;
                    damage = 23;
                    pierce = true;
                    hittable = false;
                    hitEffect = Fx.hitLancer;
                    trailChance = 0.4f;
                }

                @Override
                public void draw(mindustry.gen.Bullet b) {
                    arc.graphics.g2d.Draw.color(Pal.surge);
                    arc.graphics.g2d.Fill.circle(b.x, b.y, 4);
                    arc.graphics.g2d.Draw.color();
                    arc.graphics.g2d.Fill.circle(b.x, b.y, 2.5f);
                }

                @Override
                public void update(mindustry.gen.Bullet b) {
                    super.update(b);
                    if (b.timer.get(1, 7)) {
                        mindustry.entities.Units.nearbyEnemies(b.team, b.x - 5 * tilesize, b.y - 5 * tilesize, 5 * tilesize * 2, 5 * tilesize * 2,
                            unit -> mindustry.entities.Lightning.create(b.team, Pal.surge, Mathf.random(17, 33), b.x, b.y, b.angleTo(unit), Mathf.random(7, 13)));
                    }
                }

                @Override
                public void drawLight(mindustry.gen.Bullet b) {}
            };
            shootSound = Sounds.shootLancer;
            heatColor = Pal.turretHeat;
            shootEffect = Fx.sparkShoot;
            smokeEffect = Fx.none;
        }};

        // ===== plasma (PU_V8 L1224-1239, PowerTurret + TriangleBulletType) =====
        plasma = new PowerTurret("plasma") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 580, Items.lead, 520, Items.graphite, 410, Items.silicon, 390, Items.surgeAlloy, 180, Z_Items.sparkAlloy, 110));
            size = 4;
            health = 2800;
            range = 200f;
            reload = 360f;
            recoil = 4f;
            coolantMultiplier = 1.2f;
            liquidCapacity = 20f;
            shootCone = 1f;
            inaccuracy = 0f;
            consumePower(8.2f);
            shootType = new TriangleBulletType(13, 10, 4f, 380f) {{
                lifetime = 180f;
                trailWidth = 3.5f;
                trailLength = 14;
                homingPower = 0.06f;
                hitSound = Sounds.explosion;
                hitEffect = Fx.hitLancer;
                despawnEffect = Fx.none;
                castsLightning = true;
                fragBullet = new TriangleBulletType(11, 10, 4.5f, 90f) {{
                    lifetime = 160f;
                    trailWidth = 3f;
                    trailLength = 8;
                    drag = 0.05f;
                    collides = false;
                    castsLightning = true;
                    shootEffect = Fx.sparkShoot;
                    hitEffect = Fx.hitLancer;
                    despawnEffect = Fx.hitLancer;
                }};
                fragBullets = 8;
            }};
            shootSound = Sounds.shootToxopidShotgun;
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability <= 0.1f, 0.52f)).boost();
        }};

        // ===== shockwire (PU_V8 L1188-1202, LaserTurret + BeamBulletType) =====
        shockwire = new LaserTurret("shockwire") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 150, Items.lead, 145, Items.titanium, 160, Items.silicon, 130, Z_Items.imberium, 70));
            size = 2;
            health = 860;
            range = 125f;
            reload = 140f;
            coolantMultiplier = 2f;
            shootCone = 1f;
            inaccuracy = 0f;
            consumePower(6.9420f);
            targetAir = false;
            shootType = new BeamBulletType(120f, 35f) {{
                status = mindustry.content.StatusEffects.shocked;
                statusDuration = 3f * 60f;
                beamWidth = 0.62f;
                hitEffect = Fx.hitLiquid;
                castsLightning = true;
                minLightningDamage = damage / 1.8f;
                maxLightningDamage = damage / 1.2f;
                color = Pal.surge;
            }};
            shootSound = Sounds.shootLancer;
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability <= 0.1f, 0.4f)).boost().update(false);
        }};

        // ===== shielder (PU_V8 L1268-1287, ShieldTurret + ShieldBulletType) =====
        shielder = new ShieldTurret("shielder") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 300, Items.lead, 100, Items.titanium, 160, Items.silicon, 240, Z_Items.sparkAlloy, 90));
            size = 3;
            health = 900;
            range = 260;
            reload = 800;
            coolantMultiplier = 2;
            shootCone = 60;
            inaccuracy = 0;
            consumePower(6.4f);
            targetAir = false;
            shootType = new ShieldBulletType(8f) {{
                drag = 0.03f;
                shootEffect = Fx.none;
                despawnEffect = Fx.none;
                collides = false;
                hitSize = 0;
                hittable = false;
                hitEffect = Fx.hitLiquid;
                maxRadius = 10f;
                shieldHealth = 3000f;
                // v158 充能特效在 BulletType.chargeEffect 上 (原 PU_V8 block.chargeEffect)
                chargeEffect = new mindustry.entities.Effect(38f, e -> {
                    arc.graphics.g2d.Draw.color(Pal.accent);
                    arc.math.Angles.randLenVectors(e.id, 2, 1 + 20 * e.fout(), e.rotation, 120, (x, y) -> arc.graphics.g2d.Lines.lineAngle(e.x + x, e.y + y, arc.math.Mathf.angle(x, y), e.fslope() * 3 + 1));
                });
            }};
            shootSound = Sounds.shoot;
            // v158 无 chargeEffect/chargeBeginEffect 字段, 用 shoot.firstShotDelay + BulletType.chargeEffect 替代
            shoot.firstShotDelay = 38f;
            chargeSound = Sounds.shootLancer;
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability <= 0.1f, 0.4f)).boost().update(false);
        }};

        // ===== zBoson (PU_V8 L840-889, RampupPowerTurret + VelocityLaserBoltBulletType) =====
        zBoson = new RampupPowerTurret("z-boson") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 290, Z_Items.luminum, 430, Items.titanium, 190, Items.thorium, 120, Z_Items.lightAlloy, 15));
            health = 4000;
            size = 5;
            reload = 40f;
            range = 230f;
            shootCone = 20f;
            heatColor = Pal.turretHeat;
            coolantMultiplier = 1.9f;
            rotateSpeed = 2.7f;
            recoil = 2f;
            consumePower(3.6f);
            targetAir = true;
            shootSound = Z_Sounds.zbosonShoot;  // ★ 原版 UnitySounds.zbosonShoot (light/zboson-shoot.ogg)
            shoot = new ShootAlternate(2);
            shoot.shots = 2;
            ((ShootAlternate)shoot).spread = 14f;
            inaccuracy = 2.3f;

            lightning = true;
            lightningThreshold = 12f;
            baseLightningLength = 16;
            lightningLengthDec = 1;
            baseLightningDamage = 18f;
            lightningDamageDec = 1f;

            barBaseY = -10.75f;
            barLength = 20f;

            shootType = new VelocityLaserBoltBulletType(9.5f, 56f) {{
                splashDamage = 8f;
                splashDamageRadius = 16f;
                drag = 0.005f;
                lifetime = 27f;
                hitSize = 8f;
                shootEffect = smokeEffect = Fx.none;
                hitEffect = Fx.hitLancer;
                hittable = false;
            }};
        }};

        // ===== ephemeron (PU_V8 L989-1035, PowerTurret + EphemeronBulletType) =====
        ephemeron = new PowerTurret("ephemeron") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 290, Z_Items.luminum, 430, Items.titanium, 190, Items.thorium, 120, Z_Items.lightAlloy, 25));
            size = 8;
            health = 9800;
            range = 320f;
            reload = 70f;
            coolantMultiplier = 1.9f;
            consumePower(26f);
            shake = 2f;
            recoil = 4f;
            shootSound = Z_Sounds.ephemeronShoot;  // ★ 原版 UnitySounds.ephemeronShoot (light/ephemeron-shoot.ogg)
            rotateSpeed = 1.9f;
            heatColor = Pal.turretHeat;
            // v158 无 chargeTime/chargeBeginEffect 字段, 用 shoot.firstShotDelay 替代充能时间
            shoot.firstShotDelay = 80f;
            chargeSound = Sounds.shootLancer;

            shootType = new EphemeronBulletType(7.7f, 10f) {{
                lifetime = 70f;
                hitSize = 12f;
                pierce = true;
                collidesTiles = false;
                shootEffect = Fx.lightningShoot;
                hitEffect = Fx.hitLancer;
                despawnEffect = smokeEffect = Fx.none;
                // v158 充能特效在 BulletType.chargeEffect 上 (原 PU_V8 block.chargeBeginEffect)
                chargeEffect = Fx.sparkShoot;

                positive = new EphemeronPairBulletType(4f) {{
                    positive = true;
                    frontColor = Pal.lancerLaser;
                    backColor = Color.white;
                }};

                negative = new EphemeronPairBulletType(4f) {{
                    frontColor = Color.white;
                    backColor = Pal.lancerLaser;
                }};
            }};
        }};

        // ===== ghost (PU_V8 L411-428, BarrelsItemTurret) =====
        // ★ PU_V8: spread=21, addBarrel(8f, 18.75f, 6f)
        ghost = new BarrelsItemTurret("ghost") {{
            size = 8;
            health = 9750;
            range = 290f;
            reload = 9f;
            coolantMultiplier = 0.5f;
            inaccuracy = 3f;
            // ★ PU_V8: spread=21, shots=2, alternate=true → ShootAlternate(21f)
            shoot = new ShootAlternate(21f);
            shootSound = Sounds.shootSpectre;
            recoil = 5.5f;
            rotateSpeed = 3.5f;
            addBarrel(8f, 18.75f, 6f);
            // ★ 子弹颜色按 PU_V8/v7 标准区分
            ammo(Items.graphite, new BasicBulletType(3.3f, 120f) {{
                lifetime = 35f; width = 21f; height = 26f;
                splashDamage = 40f; splashDamageRadius = 30f;
                backColor = Pal.darkerGray;
                trailColor = hitColor = Pal.darkerMetal; frontColor = Color.white;
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }}, Items.silicon, new BasicBulletType(3.3f, 98f) {{
                lifetime = 35f; width = 19f; height = 24f;
                homingPower = 0.09f; reloadMultiplier = 1.3f;
                backColor = Pal.darkerGray;
                trailColor = hitColor = Pal.darkerMetal; frontColor = Color.white;
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }}, Items.pyratite, new BasicBulletType(3.3f, 90f) {{
                lifetime = 35f; width = 21f; height = 26f;
                splashDamage = 40f; splashDamageRadius = 35f; makeFire = true;
                status = mindustry.content.StatusEffects.burning;
                backColor = trailColor = hitColor = Color.valueOf("ffaa5f"); frontColor = Color.valueOf("ffd49a");
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }}, Items.thorium, new BasicBulletType(3.3f, 140f) {{
                lifetime = 35f; width = 21f; height = 26f;
                pierceCap = 2; pierce = true;
                backColor = Pal.darkerGray;
                trailColor = hitColor = Color.valueOf("f4ba6e"); frontColor = Color.white;
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }});
            requirements(Category.turret, ItemStack.with(Items.copper, 1150, Items.graphite, 1420, Items.silicon, 960, Items.plastanium, 800, Items.thorium, 1230, Z_Items.darkAlloy, 380));
        }};

        // ===== banshee (PU_V8 L430-449, BarrelsItemTurret + focus) =====
        // ★ PU_V8: spread=37, addBarrel×2, focus=true
        banshee = new BarrelsItemTurret("banshee") {{
            size = 12;
            health = 22000;
            range = 370f;
            reload = 12f;
            coolantMultiplier = 0.5f;
            inaccuracy = 3f;
            // ★ PU_V8: spread=37, shots=2, alternate=true → ShootAlternate(37f)
            shoot = new ShootAlternate(37f);
            shootSound = Sounds.shootSpectre;
            recoil = 5.5f;
            rotateSpeed = 3.5f;
            focus = true;
            addBarrel(23.5f, 36.5f, 9f);
            addBarrel(8.5f, 24.5f, 6f);
            // ★ 子弹颜色按 PU_V8/v7 标准区分
            ammo(Items.graphite, new BasicBulletType(3.3f, 130f) {{
                lifetime = 40f; width = 21f; height = 27f;
                splashDamage = 50f; splashDamageRadius = 35f;
                backColor = Pal.darkerGray;
                trailColor = hitColor = Pal.darkerMetal; frontColor = Color.white;
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }}, Items.silicon, new BasicBulletType(3.3f, 115f) {{
                lifetime = 40f; width = 19f; height = 25f;
                homingPower = 0.09f; reloadMultiplier = 1.3f;
                backColor = Pal.darkerGray;
                trailColor = hitColor = Pal.darkerMetal; frontColor = Color.white;
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }}, Items.pyratite, new BasicBulletType(3.3f, 100f) {{
                lifetime = 40f; width = 21f; height = 27f;
                splashDamage = 50f; splashDamageRadius = 40f; makeFire = true;
                status = mindustry.content.StatusEffects.burning;
                backColor = trailColor = hitColor = Color.valueOf("ffaa5f"); frontColor = Color.valueOf("ffd49a");
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }}, Items.thorium, new BasicBulletType(3.3f, 160f) {{
                lifetime = 40f; width = 21f; height = 27f;
                pierceCap = 2; pierce = true;
                backColor = Pal.darkerGray;
                trailColor = hitColor = Color.valueOf("f4ba6e"); frontColor = Color.white;
                hitEffect = Fx.hitBulletBig; despawnEffect = Fx.hitBulletBig;
            }});
            requirements(Category.turret, ItemStack.with(Items.copper, 2800, Items.graphite, 2980, Items.silicon, 2300, Items.titanium, 1900, Items.phaseFabric, 1760, Items.thorium, 1780, Z_Items.darkAlloy, 1280));
        }};

        // ===== fallout (PU_V8 L451-481, LaserTurret + SparkingContinuousLaserBulletType) =====
        fallout = new LaserTurret("fallout") {{
            size = 5;
            health = 3975;
            range = 215f;
            reload = 110f;
            coolantMultiplier = 0.8f;
            shootCone = 40f;
            shootDuration = 230f;
            consumePower(19f);
            shake = 3f;
            firingMoveFract = 0.2f;
            shootEffect = Fx.shootBigSmoke2;
            recoil = 4f;
            shootSound = Sounds.shootMeltdown;  // ★ 原版 Sounds.laserbig, v158 用 shootMeltdown 替代
            heatColor = Color.valueOf("e04300");
            rotateSpeed = 3.5f;
            loopSound = Sounds.beamPlasma;
            loopSoundVolume = 2.1f;
            requirements(Category.turret, ItemStack.with(Items.copper, 450, Items.lead, 350, Items.graphite, 390, Items.silicon, 360, Items.titanium, 250, Z_Items.umbrium, 370, Items.surgeAlloy, 360));
            shootType = new SparkingContinuousLaserBulletType(95f) {{
                length = 230f;
                fromBlockChance = 0.12f;
                fromBlockDamage = 23f;
                fromLaserAmount = 0;
                incendChance = 0f;
                fromBlockLen = 2;
                fromBlockLenRand = 5;
            }};
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability < 0.1f, 0.58f)).boost().update(false);
        }};

        // ===== catastrophe (PU_V8 L483-505, BigLaserTurret + SparkingContinuousLaserBulletType) =====
        catastrophe = new BigLaserTurret("catastrophe") {{
            size = 8;
            health = 9750;
            range = 300f;
            reload = 190f;
            coolantMultiplier = 0.6f;
            shootCone = 40f;
            shootDuration = 320f;
            consumePower(39f);
            shake = 4f;
            firingMoveFract = 0.16f;
            shootEffect = Fx.shootBigSmoke2;
            recoil = 7f;
            heatColor = Color.white;
            rotateSpeed = 1.9f;
            shootSound = Sounds.shootSpectre;
            loopSound = Sounds.beamPlasma;
            loopSoundVolume = 2.2f;
            requirements(Category.turret, ItemStack.with(Items.copper, 1250, Items.lead, 1320, Items.graphite, 1100, Items.titanium, 1340, Items.surgeAlloy, 1240, Items.silicon, 1350, Items.thorium, 770, Z_Items.darkAlloy, 370));
            shootType = new SparkingContinuousLaserBulletType(240f) {{
                length = 340f;
                incendSpread = 7f;
                incendAmount = 2;
            }};
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.4f && liquid.flammability < 0.1f, 1.3f)).boost().update(false);
        }};

        // ===== calamity (PU_V8 L507-529, BigLaserTurret + SparkingContinuousLaserBulletType) =====
        calamity = new BigLaserTurret("calamity") {{
            size = 12;
            health = 22000;
            range = 420f;
            reload = 320f;
            coolantMultiplier = 0.6f;
            shootCone = 23f;
            shootDuration = 360f;
            consumePower(87f);
            shake = 4f;
            firingMoveFract = 0.09f;
            shootEffect = Fx.shootBigSmoke2;
            recoil = 7f;
            heatColor = Color.white;
            rotateSpeed = 0.97f;
            shootSound = Sounds.shootMeltdown;  // ★ 原版 Sounds.laserbig, v158 用 shootMeltdown 替代
            loopSound = Sounds.beamPlasma;
            loopSoundVolume = 2.6f;
            requirements(Category.turret, ItemStack.with(Items.copper, 2800, Items.lead, 2970, Items.graphite, 2475, Items.titanium, 3100, Items.surgeAlloy, 2790, Items.silicon, 3025, Items.thorium, 1750, Z_Items.darkAlloy, 1250));
            shootType = new SparkingContinuousLaserBulletType(580f) {{
                length = 450f;
                fromBlockChance = 0.5f;
                fromBlockDamage = 34f;
                fromLaserChance = 0.8f;
                fromLaserDamage = 32f;
                fromLaserAmount = 3;
                fromLaserLen = 5;
                fromLaserLenRand = 7;
                incendChance = 0.6f;
                incendSpread = 9f;
                incendAmount = 2;
            }};
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.3f && liquid.flammability < 0.1f, 2.1f)).boost().update(false);
        }};

        // ===== extinction (PU_V8 L531-554, BigLaserTurret + SparkingContinuousLaserBulletType) =====
        extinction = new BigLaserTurret("extinction") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 3800, Items.lead, 4100, Items.graphite, 3200, Items.titanium, 4200, Items.surgeAlloy, 3800, Items.silicon, 4300, Items.thorium, 2400, Z_Items.darkAlloy, 1700, Z_Items.terminum, 900, Z_Items.terminaAlloy, 500));
            size = 14;
            health = 29500;
            range = 520f;
            reload = 380f;
            coolantMultiplier = 0.4f;
            shootCone = 12f;
            shootDuration = 360f;
            consumePower(175f);
            shake = 4f;
            firingMoveFract = 0.09f;
            shootEffect = Fx.shootBigSmoke2;
            recoil = 7f;
            heatColor = Color.white;
            rotateSpeed = 0.82f;
            shootSound = Z_Sounds.extinctionShoot;  // ★ 原版 UnitySounds.extinctionShoot (dark/extinction-shoot.ogg)
            loopSound = Z_Sounds.beamIntenseHighpitchTone;  // ★ 原版 UnitySounds.beamIntenseHighpitchTone (dark/beam-intense-highpitch-tone.ogg)
            loopSoundVolume = 2f;
            shootType = new SparkingContinuousLaserBulletType(770f) {{
                length = 560f;
                fromBlockChance = 0.5f;
                fromBlockDamage = 76f;
                fromLaserChance = 0.8f;
                fromLaserDamage = 46f;
                fromLaserAmount = 4;
                fromLaserLen = 10;
                fromLaserLenRand = 7;
                incendChance = 0.7f;
                incendSpread = 9f;
                incendAmount = 2;
                extinction = true;
            }};
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.27f && liquid.flammability < 0.1f, 2.5f)).boost().update(false);
        }};

        // ===== buffTurret (PU_V8 L2106-2112, BlockOverdriveTurret) =====
        buffTurret = new BlockOverdriveTurret("buff-turret") {{
            requirements(Category.effect, ItemStack.with(Items.thorium, 60, Items.plastanium, 90, Z_Items.stone, 100, Z_Items.denseAlloy, 70));
            health = 200;
            size = 1;
            buffRange = 100f;
            consume(new mindustry.world.consumers.ConsumeItemFilter(i -> i == Z_Items.steel)).boost();
        }};

        // ===== upgradeTurret (PU_V8 L2114-2120, BlockOverdriveTurret) =====
        upgradeTurret = new BlockOverdriveTurret("upgrade-turret") {{
            requirements(Category.effect, ItemStack.with(Items.surgeAlloy, 80, Z_Items.steel, 120, Z_Items.dirium, 70));
            health = 300;
            size = 1;
            buffRange = 100f;
            consume(new mindustry.world.consumers.ConsumeItemFilter(i -> i == Z_Items.dirium)).boost();
        }};

        // ===== absorber (PU_V8 L1352-1366, AbsorberTurret) =====
        absorber = new AbsorberTurret("absorber") {{
            requirements(Category.power, ItemStack.with(Z_Items.imberium, 20, Items.lead, 20));

            consumesPower = false;

            powerProduction = 1.2f;
            range = 50f;

            targetUnits = true;
            status = mindustry.content.StatusEffects.slow;

            rotateSpeed = 1.2f;
            shootCone = 2f;
            damage = 0.6f;
        }};

        // ===== orbTurret (PU_V8 L1289-1319, OrbTurret) =====
        orbTurret = new OrbTurret("orb-turret") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 1));

            size = 2;

            consumePower(0.3f);
            shootType = new BasicBulletType() {
                {
                    damage = 20f;
                    lifetime = 120f;
                    pierce = true;
                    hittable = false;
                    hitEffect = Fx.hitLancer;
                    despawnEffect = Fx.none;
                }

                @Override
                public void draw(mindustry.gen.Bullet b) {
                    arc.graphics.g2d.Draw.color(Pal.accent);
                    arc.graphics.g2d.Fill.circle(b.x, b.y, 3f);
                    arc.graphics.g2d.Draw.color(Color.white);
                    arc.graphics.g2d.Fill.circle(b.x, b.y, 2f);
                }
            };
        }};

        // ===== xenoCorruptor (PU_V8 L3369-3409, LaserTurret + ChangeTeamLaserBulletType) =====
        // PU_V8: 发射连续激光, 将生命值低于阈值的敌方单位转化为己方, 转化后单位会被削弱
        // v158 适配: powerUse→consumePower, reloadTime→reload, UnitySounds.xenoBeam→Z_Sounds.xenoBeam,
        //           UnityPal.advanceDark→暗蓝色, UnityStatusEffects.teamConverted→StatusEffects.none,
        //           consumes.add→consume, baseRegion查找由DrawTurret自动处理
        xenoCorruptor = new LaserTurret("xeno-corruptor") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 640, Items.graphite, 740, Items.titanium, 560, Items.surgeAlloy, 650, Items.silicon, 720, Items.thorium, 400, Z_Items.xenium, 340, Z_Items.advanceAlloy, 640));
            health = 7900;
            size = 7;
            reload = 230f;
            range = 290f;
            coolantMultiplier = 1.4f;
            shootCone = 40f;
            shootDuration = 310f;
            firingMoveFract = 0.16f;
            consumePower(45f);
            shake = 3f;
            recoil = 8f;
            shootSound = Sounds.shootLancer;
            loopSound = Z_Sounds.xenoBeam;
            loopSoundVolume = 2f;
            heatColor = Color.valueOf("3a4a7a");
            rotateSpeed = 2f;
            shootType = new ChangeTeamLaserBulletType(60f) {{
                length = 300f;
                lifetime = 18f;
                shootEffect = Fx.none;
                smokeEffect = Fx.none;
                hitEffect = Fx.hitLancer;
                incendChance = -1f;
                lightColor = Color.valueOf("59a7ff");
                conversionStatusEffect = mindustry.content.StatusEffects.none;
                convertBlocks = false;
                colors = new Color[]{Color.valueOf("59a7ff55"), Color.valueOf("59a7ffaa"), Color.valueOf("a3e3ff"), Color.white};
            }};
            consume(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.4f && liquid.flammability < 0.1f, 2.1f)).boost().update(false);
        }};
    }
}
