package zzw.content.blocks;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.pattern.ShootPattern;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.turrets.LaserTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import zzw.content.Z_Items;
import zzw.content.Z_Sounds;
import zzw.content.blocks.soul.SoulItemTurret;
import zzw.content.blocks.soul.SoulTractorBeamTurret;
import zzw.content.blocks.soul.SoulTurretPowerTurret;

import static mindustry.Vars.tilesize;

/**
 * PU_V8 高级炮台注册 (灵魂系统炮台 + 3D模型炮台)
 *
 * 移植自 PU_V8 unity/content/UnityBlocks.java L2462-2797:
 * - lifeStealer (SoulLifeStealerTurret → SoulTractorBeamTurret)
 * - absorberAura (SoulAbsorberTurret → SoulTractorBeamTurret)
 * - heatRay (SoulHeatRayTurret → SoulTractorBeamTurret)
 * - incandescence (SoulHeatRayTurret → SoulTractorBeamTurret)
 * - oracle (SoulTurretBurstPowerTurret → SoulTurretPowerTurret 简化)
 * - recluse (SoulTurretItemTurret → SoulItemTurret)
 * - prism (PrismTurret → PowerTurret 简化, 无3D模型)
 * - supernova (SupernovaTurret → LaserTurret 简化)
 *
 * 简化策略:
 * - PU_V8 自定义 Build 类 (SoulLifeStealerTurretBuild 等) → 用通用 SoulTractorBeamTurret
 * - PU_V8 自定义特效 (UnityFx.oracleCharge 等) → Fx.chargeLancer/Fx.none
 * - PU_V8 不存在的 Sounds (spark/shotgun/laser) → v158 替代音效
 * - PU_V8 3D 模型 (PrismTurret.model) → 简化为 2D 贴图
 * - PU_V8 复杂充能系统 (oracle chargeTime/supernova charge) → 简化为 reloadTime
 * - PU_V8 多目标攻击 (PrismTurret maxShots) → 简化为单目标
 */
public class Z_AdvTurrets {

    // ===== TractorBeam 炮台 (持续激光) =====
    public static SoulTractorBeamTurret lifeStealer, absorberAura, heatRay, incandescence;
    // ===== 爆发炮台 =====
    public static SoulTurretPowerTurret oracle;
    // ===== 物品炮台 =====
    public static SoulItemTurret recluse;
    // ===== 3D 模型炮台 (简化为 2D) =====
    public static PowerTurret prism;
    public static LaserTurret supernova;

    public static void load() {
        // ===== lifeStealer (PU_V8 L2462-2475) =====
        // SoulLifeStealerTurret: 持续激光, damage=120, 无灵魂需求
        // ★ 修复: 默认 TractorBeamTurret 只攻击空中, 需显式设 targetGround=true 才能攻击地面
        lifeStealer = new SoulTractorBeamTurret("life-stealer") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 50, Z_Items.monolite, 25));
            size = 1;
            health = 320;
            consumePower(1f);
            damage = 120f;
            range = 120f;
            shootCone = 6f;
            rotateSpeed = 10f;
            force = 0.3f;
            scaledForce = 0f;
            targetAir = true;
            targetGround = true;  // ★ 修复: 默认 false 导致只攻击空中单位
            laserColor = Pal.lancerLaser;
            status = mindustry.content.StatusEffects.none;
            shootSound = Sounds.beamParallax;
            requireSoul = false;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.5f;
        }};

        // ===== absorberAura (PU_V8 L2505-2521) =====
        // SoulAbsorberTurret: 持续激光, resistance=0.8f, targetBullets=true (简化为普通激光)
        // ★ 修复: 默认 TractorBeamTurret 只攻击空中, 需显式设 targetGround=true 才能攻击地面
        absorberAura = new SoulTractorBeamTurret("absorber-aura") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 75, Z_Items.monolite, 125));
            size = 2;
            health = 720;
            range = 150f;
            consumePower(1f);
            damage = 80f;
            shootCone = 6f;
            rotateSpeed = 10f;
            force = 0.3f;
            scaledForce = 0f;
            targetAir = true;
            targetGround = true;  // ★ 修复
            laserColor = Pal.lancerLaser;
            shootSound = Sounds.beamParallax;
            requireSoul = false;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.6f;
        }};

        // ===== heatRay (PU_V8 L2624-2641) =====
        // SoulHeatRayTurret: 持续热射线, damage=240, 仅对地
        heatRay = new SoulTractorBeamTurret("heat-ray") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 75, Items.lead, 50, Items.graphite, 25, Items.titanium, 45, Z_Items.monolite, 50));
            size = 2;
            range = 120f;
            consumePower(2f);
            damage = 240f;
            shootCone = 6f;
            rotateSpeed = 10f;
            force = 0f;
            scaledForce = 0f;
            targetGround = true;
            targetAir = false;
            laserColor = Color.valueOf("ff7b54");
            shootSound = Z_Sounds.heatRay;
            requireSoul = false;
            maxSouls = 5;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.6f;
        }};

        // ===== incandescence (PU_V8 L2712-2732) =====
        // SoulHeatRayTurret: 强化热射线, damage=480, 对空对地
        incandescence = new SoulTractorBeamTurret("incandescence") {{
            requirements(Category.turret, ItemStack.with(Z_Items.monolite, 250, Items.phaseFabric, 45, Z_Items.monolithAlloy, 100));
            size = 3;
            health = 1680;
            range = 180f;
            consumePower(4f);
            damage = 480f;
            shootCone = 6f;
            rotateSpeed = 10f;
            force = 0f;
            scaledForce = 0f;
            targetGround = true;
            targetAir = true;
            laserColor = Color.valueOf("ffd9a0");
            laserWidth = 0.54f;
            shootLength = 6f;
            shootSound = Z_Sounds.heatRay;
            requireSoul = false;
            maxSouls = 7;
            efficiencyFrom = 0.7f;
            efficiencyTo = 1.67f;
        }};

        // ===== oracle (PU_V8 L2643-2686) =====
        // SoulTurretBurstPowerTurret: 充能+爆发闪电+激光副炮
        // 简化: 用 PowerTurret + ShootPattern(shots=8, shotDelay=2f) 替代 chargeTime/burst
        oracle = new SoulTurretPowerTurret("oracle") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 175, Items.titanium, 150, Z_Items.monolithAlloy, 75));
            size = 3;
            health = 1440;
            consumePower(3f);
            range = 180f;
            reload = 72f;
            shootCone = 5f;
            shoot = new ShootPattern();
            shoot.shots = 8;
            shoot.shotDelay = 2f;
            shootSound = Sounds.shootArc;  // ★ v158 无 Sounds.spark, 用 shootArc 替代
            recoil = 2.5f;
            rotateSpeed = 8f;
            shake = 3f;
            shootType = new LightningBulletType() {{
                lightningLength = 25;
                damage = 192f;
                shootEffect = Fx.lightningShoot;
            }};
            requireSoul = false;
            maxSouls = 7;
            efficiencyFrom = 0.7f;
            efficiencyTo = 1.67f;
        }};

        // ===== recluse (PU_V8 L2477-2503) =====
        // SoulTurretItemTurret: 物品炮台, 子弹施加 unmoving 状态
        recluse = new SoulItemTurret("recluse") {{
            requirements(Category.turret, ItemStack.with(Items.lead, 15, Z_Items.monolite, 20));
            size = 1;
            health = 200;
            inaccuracy = 4f;
            reload = 20f;
            range = 110f;
            shootCone = 3f;
            ammoUseEffect = Fx.none;
            rotateSpeed = 12f;
            // ammo: stopLead/stopMonolite/stopSilicon (施加 unmoving 状态)
            ammo(Items.lead, new BasicBulletType(3.6f, 72f, "shell") {{
                width = 9f;
                height = 12f;
                ammoMultiplier = 4;
                lifetime = 60f;
                frontColor = Color.white;
                backColor = Pal.lancerLaser;
                status = mindustry.content.StatusEffects.unmoving;
                statusDuration = 5f;
            }}, Z_Items.monolite, new BasicBulletType(4f, 100f, "shell") {{
                width = 9f;
                height = 12f;
                ammoMultiplier = 4;
                lifetime = 60f;
                frontColor = Color.white;
                backColor = Pal.lancerLaser;
                status = mindustry.content.StatusEffects.unmoving;
                statusDuration = 8f;
            }}, Items.silicon, new BasicBulletType(4f, 72f, "shell") {{
                width = 9f;
                height = 12f;
                ammoMultiplier = 4;
                lifetime = 60f;
                frontColor = Color.white;
                backColor = Pal.lancerLaser;
                status = mindustry.content.StatusEffects.unmoving;
                statusDuration = 16f;
                homingPower = 0.08f;
            }});
            requireSoul = false;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.5f;
        }};

        // ===== prism (PU_V8 L2734-2763) =====
        // PrismTurret: 3D 棱镜炮台, 多目标攻击
        // ★ 修复: 原版用 speed=0.0001f 模拟即时命中, 但在 v158 中子弹过快或过短会导致不显示/不命中
        //         改用合理的速度 + 较长的 lifetime 实现可见的激光样子弹
        prism = new PowerTurret("prism") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 1));  // ★ 原版占位需求
            size = 4;
            health = 2800;
            range = 320f;
            reload = 60f;
            rotateSpeed = 20f;
            recoil = 6f;
            shootCone = 30f;
            targetGround = true;
            targetAir = true;
            consumePower(8f);
            shootSound = Sounds.shootScatter;  // ★ v158 无 Sounds.shotgun, 用 shootScatter 替代
            shootEffect = Fx.hitLaserBlast;
            shootType = new BasicBulletType(80f, 320f) {{  // ★ BasicBulletType 才有 width/height/backColor/frontColor
                lifetime = 4f;  // 4 ticks 内飞行 320f 单位 (即时感)
                pierce = true;
                pierceBuilding = true;
                hitEffect = Fx.hitLancer;
                despawnEffect = Fx.hitLancer;
                hittable = false;
                // 视觉: 短促激光样
                width = 6f;
                height = 12f;
                backColor = Pal.lancerLaser;
                frontColor = Color.white;
            }};
        }};

        // ===== supernova (PU_V8 L2765-2797) =====
        // SupernovaTurret: 大型激光炮台, 充能后持续射击
        // 简化: 用 LaserTurret + ContinuousLaserBulletType (supernovaLaser)
        supernova = new LaserTurret("supernova") {{
            requirements(Category.turret, ItemStack.with(Items.surgeAlloy, 500, Items.silicon, 650, Z_Items.archDebris, 350, Z_Items.monolithAlloy, 325));
            size = 7;
            health = 8100;
            consumePower(24f);
            rotateSpeed = 1f;
            recoil = 4f;
            shootCone = 15f;
            range = 250f;
            shootSound = Z_Sounds.supernovaShoot;
            loopSound = Z_Sounds.supernovaActive;
            loopSoundVolume = 1f;
            shootDuration = 480f;
            // ★ supernovaLaser: 持续激光, 3200 伤害, 长度 280, 多色叠加 + 闪电
            shootType = new ContinuousLaserBulletType(3200f) {{
                length = 280f;
                colors = new Color[]{
                    Color.valueOf("4be3ca55"),
                    Color.valueOf("91eedeaa"),
                    Pal.lancerLaser.cpy(),
                    Color.white
                };
                hitEffect = Fx.hitLancer;
                hitSize = 8f;
                lifetime = 16f;
                drawSize = 600f;
                incendAmount = 0;
                incendSpread = 0f;
                incendChance = 0f;
            }};
        }};
    }
}
