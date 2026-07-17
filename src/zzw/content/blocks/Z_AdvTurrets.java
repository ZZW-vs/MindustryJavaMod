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
import mindustry.world.blocks.defense.turrets.Turret;
import zzw.content.Z_Items;
import zzw.content.Z_Sounds;
import zzw.content.blocks.soul.SoulAbsorberTurret;
import zzw.content.blocks.soul.SoulBurstPowerTurret;
import zzw.content.blocks.soul.SoulHeatRayTurret;
import zzw.content.blocks.soul.SoulItemTurret;
import zzw.content.blocks.soul.SoulLifeStealerTurret;
import zzw.content.blocks.soul.SoulTractorBeamTurret;
import zzw.content.blocks.soul.SoulTurretPowerTurret;
import zzw.content.blocks.soul.SupernovaTurret;
import zzw.content.blocks.turrets.EndLaserTurret;
import zzw.content.blocks.turrets.EndGameTurret;
import zzw.content.blocks.turrets.ObjPowerTurret;
import zzw.content.blocks.turrets.PrismTurret;
import zzw.content.blocks.turrets.WavefrontTurret;
import zzw.content.units.bullets.EndCutterLaserBulletType;
import zzw.content.units.bullets.PointBlastLaserBulletType;
import zzw.content.units.bullets.WavefrontLaserBulletType;
import zzw.content.units.effects.ChargeEffect;
import zzw.util.ZObjs;

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
 * - prism (PrismTurret → PrismTurret + WavefrontObject 伪3D)
 * - supernova (SupernovaTurret → LaserTurret 简化)
 *
 * 简化策略:
 * - PU_V8 自定义 Build 类 (SoulLifeStealerTurretBuild 等) → 用通用 SoulTractorBeamTurret
 * - PU_V8 自定义特效 (UnityFx.oracleCharge 等) → Fx.chargeLancer/Fx.none
 * - PU_V8 不存在的 Sounds (spark/shotgun/laser) → v158 替代音效
 * - PU_V8 3D 模型 (PrismTurret.model) → WavefrontObject 伪3D (prism.obj)
 * - PU_V8 复杂充能系统 (oracle chargeTime/supernova charge) → 简化为 reloadTime
 * - PU_V8 多目标攻击 (PrismTurret maxShots) → 保留多目标
 */
public class Z_AdvTurrets {

    // ===== TractorBeam 炮台 (持续激光) =====
    public static SoulLifeStealerTurret lifeStealer;
    public static SoulAbsorberTurret absorberAura;
    public static SoulHeatRayTurret heatRay, incandescence;
    // ===== 爆发炮台 =====
    public static SoulBurstPowerTurret oracle;
    // ===== 物品炮台 =====
    public static SoulItemTurret recluse;
    // ===== 3D 模型炮台 (伪 3D, WavefrontObject) =====
    public static PrismTurret prism;
    public static SupernovaTurret supernova;
    // ===== End 阵营非 3D 炮台 =====
    public static EndLaserTurret tenmeikiri;
    public static EndGameTurret endGame;
    // ===== 3D 模型炮台 (伪 3D, WavefrontObject) =====
    public static ObjPowerTurret cube;
    public static WavefrontTurret wavefront;

    public static void load() {
        // ===== lifeStealer (PU_V8 L2462-2475) =====
        // SoulLifeStealerTurret: 持续激光+吸血蓄能+范围治疗
        // ★ 完整移植 PU_V8: laserAlpha 回调基于 power.status 和 soulf
        lifeStealer = new SoulLifeStealerTurret("life-stealer") {{
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
            targetGround = true;
            laserColor = Pal.lancerLaser;
            status = mindustry.content.StatusEffects.none;
            shootSound = Sounds.beamParallax;
            requireSoul = false;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.5f;
            // PU_V8 原版: laserAlpha = power.status * (0.7 + soulf * 0.3)
            laserAlpha(b -> b.power.status * (0.7f + b.soulf() * 0.3f));
            // ★ 吸血机制参数 (PU_V8 LifeStealerTurret)
            maxContain = 600f;       // 蓄能阈值: 累积 600 伤害触发范围治疗
            healPercent = 0.05f;     // 治疗 5% maxHealth
            healTrnsEffect = Fx.healBlockFull;
            healEffect = Fx.healBlockFull;
        }};

        // ===== absorberAura (PU_V8 L2505-2521) =====
        // SoulAbsorberTurret: 持续激光+吸收敌方单位能量产电
        // ★ 完整移植 PU_V8: targetBullets=true + laserAlpha 回调
        absorberAura = new SoulAbsorberTurret("absorber-aura") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 75, Z_Items.monolite, 125));
            size = 2;
            health = 720;
            range = 150f;
            // ★ SoulAbsorberTurret 自身 outputsPower=true, 不调用 consumePower
            damage = 80f;
            shootCone = 6f;
            rotateSpeed = 10f;
            force = 0.3f;
            scaledForce = 0f;
            targetAir = true;
            targetGround = true;
            laserColor = Pal.lancerLaser;
            shootSound = Sounds.beamParallax;
            requireSoul = false;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.6f;
            // PU_V8 原版: laserAlpha = power.status * (0.7 + soulf * 0.3)
            laserAlpha(b -> b.power.status * (0.7f + b.soulf() * 0.3f));
            // ★ 灵魂吸收产电参数 (PU_V8 AbsorberTurret)
            powerProduction = 2.5f;   // 基础产电倍率
            resistance = 0.8f;       // 抵抗强度
            damageScale = 18f;        // 伤害缩放
            speedScale = 3.5f;        // 速度缩放
            // PU_V8 原版: targetBullets=true (吸收子弹产电)
            targetBullets = true;
        }};

        // ===== heatRay (PU_V8 L2624-2641) =====
        // SoulHeatRayTurret: 持续热射线, damage=240, 仅对地, 施加 melting 状态
        // ★ 完整移植 PU_V8: laserAlpha 回调基于 power.status 和 soulf
        heatRay = new SoulHeatRayTurret("heat-ray") {{
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
            // ★ PU_V8: 施加 melting 状态 (热射线核心机制)
            status = mindustry.content.StatusEffects.melting;
            statusDuration = 60f;
            requireSoul = false;
            maxSouls = 5;
            efficiencyFrom = 0.8f;
            efficiencyTo = 1.6f;
            // PU_V8 原版: laserAlpha = power.status * (0.7 + soulf * 0.3)
            laserAlpha(b -> b.power.status * (0.7f + b.soulf() * 0.3f));
        }};

        // ===== incandescence (PU_V8 L2712-2732) =====
        // SoulHeatRayTurret: 强化热射线, damage=480, 对空对地, 施加 melting 状态
        incandescence = new SoulHeatRayTurret("incandescence") {{
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
            // ★ PU_V8: 施加 melting 状态
            status = mindustry.content.StatusEffects.melting;
            statusDuration = 60f;
            requireSoul = false;
            maxSouls = 7;
            efficiencyFrom = 0.7f;
            efficiencyTo = 1.67f;
            // PU_V8 原版: laserAlpha = power.status * (0.7 + soulf * 0.3)
            laserAlpha(b -> b.power.status * (0.7f + b.soulf() * 0.3f));
        }};

        // ===== oracle (PU_V8 L2643-2686) =====
        // SoulBurstPowerTurret: 充能+主弹幕(闪电8连发)+副弹幕(激光)
        // ★ 完整移植 PU_V8 BurstPowerTurret.shoot: chargeTime阶段 + 主弹幕shots连发 + 副弹幕subShots连发
        oracle = new SoulBurstPowerTurret("oracle") {{
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
            // ★ PU_V8 BurstPowerTurret 充能参数 (chargeTime>0 触发完整充能流程)
            chargeTime = 30f;
            chargeMaxDelay = 4f;
            chargeEffects = 12;
            chargeEffect = Fx.lancerLaserCharge;        // PU_V8 UnityFx.oracleCharge 简化替代
            chargeBeginEffect = Fx.lancerLaserChargeBegin; // PU_V8 UnityFx.oracleChargeBegin 简化替代
            chargeSound = Sounds.shootArc;        // PU_V8 Sounds.spark 简化替代
            shootType = new LightningBulletType() {{
                lightningLength = 25;
                damage = 192f;
                shootEffect = Fx.lightningShoot;
            }};
            requireSoul = false;
            maxSouls = 7;
            efficiencyFrom = 0.7f;
            efficiencyTo = 1.67f;
            // ★ 副弹幕: 激光 (PU_V8 BurstPowerTurret.subShootType)
            subShootType = new LaserBulletType(288f) {{  // PU_V8 原版 damage=288f
                length = 180f;
                sideAngle = 45f;
                inaccuracy = 8f;
                colors = new Color[]{Pal.lancerLaser.cpy().a(0.4f), Pal.lancerLaser, Color.white};
                hitEffect = Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                ammoMultiplier = 1f;
            }};
            subShots = 3;             // PU_V8 原版 subShots=3
            subBurstSpacing = 1f;     // PU_V8 原版 subBurstSpacing=1
            subShootEffect = Fx.hitLancer;  // PU_V8 原版 Fx.hitLancer
            subShootSound = Sounds.shootLancer;  // PU_V8 原版 Sounds.laser 简化替代
            subShootSoundVolume = 1f;
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

        // ===== prism (PU_V8 L2734-2763, PrismTurret + ModelInstance) =====
        // 3D 棱镜炮台: 伪 3D 渲染 + 多目标攻击 + 颜色渐变
        // ★ v155.4 适配: ModelInstance → WavefrontObject; SoulPowerTurret → SoulTurretPowerTurret
        // ★ shoot() 在目标位置创建子弹 (speed≈0), 用 chainLightning 连接炮台与目标
        // ★ 完整移植 PU_V8: maxSouls=7, efficiencyFrom=0.7, efficiencyTo=1.67 (灵魂影响伤害)
        prism = new PrismTurret("prism") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 1));  // 原版占位需求
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
            shootSound = Sounds.shootScatter;  // v155.4 无 Sounds.shotgun, 用 shootScatter 替代
            shootEffect = Fx.hitLaserBlast;
            object = ZObjs.prism;
            prismOffset = 6f;
            // PU_V8 原版灵魂系统字段 (progression.linear 等效于 SoulTurretPowerTurret.updateSoulDamage)
            requireSoul = false;
            maxSouls = 7;
            efficiencyFrom = 0.7f;
            efficiencyTo = 1.67f;
            shootType = new BulletType(0.0001f, 320f) {{  // 原版 speed=0.0001f 模拟即时命中
                lifetime = 50f;
                pierce = true;
                pierceBuilding = true;
                hitEffect = Fx.hitLancer;
                despawnEffect = Fx.none;
                hittable = false;
            }};
        }};

        // ===== supernova (PU_V8 L2765-2797, SupernovaTurret) =====
        // SupernovaTurret: 大型激光炮台, 充能后持续射击 + 单位吸引 + 闪电特效
        // ★ 完整移植 PU_V8: 充能(charge/phase/starHeat) + attractUnits + 闪电 + 6部件动画
        // ★ 简化替代: UnityFx.supernovaXxx → v158 Fx 等效特效; UnityDrawf.shiningCircle 用项目已有实现
        supernova = new SupernovaTurret("supernova") {{
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
            // PU_V8 原版灵魂系统字段 (progression.linear 等效于 SoulLaserTurret.updateSoulDamage)
            requireSoul = false;
            maxSouls = 12;
            efficiencyFrom = 0.7f;
            efficiencyTo = 1.8f;
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

        // ===== tenmeikiri (PU_V8 L3542-3584, EndLaserTurret + EndCutterLaserBulletType) =====
        // 持续激光炮台, 充能后发射超长激光 (防作弊伤害, 4 色叠加 + 闪电)
        // ★ 完整移植: 防作弊系统 + 持续激光跟踪 + 7 层灯光渲染 + 底座叠加层
        tenmeikiri = new EndLaserTurret("tenmeikiri") {{
            requirements(Category.turret, ItemStack.with(
                Items.phaseFabric, 3000, Items.surgeAlloy, 4000,
                Z_Items.darkAlloy, 1800, Z_Items.terminum, 1200, Z_Items.terminaAlloy, 200));
            health = 23000;
            range = 900f;
            size = 15;
            shootCone = 1.5f;
            reload = 5f * 60f;
            coolantMultiplier = 0.5f;
            recoil = 15f;  // v155.4: recoil 字段控制视觉后坐距离 (PU_V8 recoilAmount)
            consumePower(350f);
            absorbLasers = true;
            shootLength = 8f;
            chargeTime = 158f;
            chargeEffects = 12;
            chargeMaxDelay = 80f;
            // 充能特效 (PU_V8 ChargeFx.tenmeikiriChargeEffect / tenmeikiriChargeBegin)
            chargeEffect = ChargeEffect.tenmeikiriChargeEffect;
            chargeBeginEffect = ChargeEffect.tenmeikiriChargeBegin;
            chargeSound = Z_Sounds.tenmeikiriCharge;
            shootSound = Z_Sounds.tenmeikiriShoot;
            shake = 4f;
            shootType = new EndCutterLaserBulletType(7800f) {{
                maxLength = 1200f;
                lifetime = 3f * 60f;
                width = 30f;
                laserSpeed = 80f;
                status = mindustry.content.StatusEffects.melting;
                antiCheatScl = 5f;  // PU_V8 原版值
                statusDuration = 200f;
                lightningColor = Color.valueOf("f53036");  // scarColor
                lightningDamage = 85f;
                lightningLength = 15;
                // 防作弊参数 (PU132 tenmeikiri 原值)
                ratioDamage = 1f / 60f;
                ratioStart = 30000f;
                overDamage = 350000f;
                bleedDuration = 5f * 60f;
            }};
            // 冷却液体 (可选 booster, 非必需): 加快射速, 不影响攻击
            // consumeCoolant 会添加到 consumes 列表, BaseTurret.init() 自动设 optional=true + booster=true
            // maxTemp=0.25f 匹配 PU_V8 原版过滤条件 (低温不易燃液体)
            consumeCoolant(3.1f).maxTemp = 0.25f;
        }};

        // ===== endGame (PU_V8 L3586-3607, EndGameTurret) =====
        // 终极炮台: 三层旋转环 + 16 眼睛追踪射击 + 范围摧毁 + 反子弹 + 防作弊
        endGame = new EndGameTurret("endgame") {{
            requirements(Category.turret, ItemStack.with(
                Items.phaseFabric, 9500, Items.surgeAlloy, 10500,
                Z_Items.darkAlloy, 2300, Z_Items.lightAlloy, 2300, Z_Items.advanceAlloy, 2300,
                Z_Items.plagueAlloy, 2300, Z_Items.sparkAlloy, 2300, Z_Items.monolithAlloy, 2300,
                Z_Items.superAlloy, 2300, Z_Items.terminum, 1600, Z_Items.terminaAlloy, 800, Z_Items.terminationFragment, 100));
            // PU_V8 L3593-3600: shootCone=360, reloadTime=430, range=820, size=14, coolantMultiplier=0.6
            // shootCone/reload/range/size 已在 EndGameTurret 构造函数中设置
            coolantMultiplier = 0.6f;
            hasItems = true;
            itemCapacity = 10;
            loopSoundVolume = 0.2f;
            loopSound = Z_Sounds.endgameActive;
            shootSound = Z_Sounds.endgameShoot;
            // PU_V8 原版: damage = (float)Double.MAX_VALUE (不是 Float.MAX_VALUE!)
            shootType = new BulletType() {{
                damage = (float)Double.MAX_VALUE;
            }};
            consumeItem(Z_Items.terminum, 2);
            // 冷却液作为可选 booster (非必需, BaseTurret.init 会自动设 optional/booster/update=false)
            coolant = consumeCoolant(0.6f);
            coolant.boost();
        }};

        // ===== cube (PU_V8 L3411-3429, ObjPowerTurret + PointBlastLaserBulletType) =====
        // 3D 立方体炮台: 伪 3D 渲染 + 受击形变 + 旋转动画
        // ★ v155.4 适配: reloadTime → reload; powerUse → consumePower(); UnityObjs.cube → ZObjs.cube
        cube = new ObjPowerTurret("the-cube") {{
            requirements(Category.turret, ItemStack.with(
                Items.copper, 3300, Items.lead, 2900, Items.graphite, 4400,
                Items.silicon, 3800, Items.titanium, 4600,
                Z_Items.xenium, 2300, Items.phaseFabric, 670, Z_Items.advanceAlloy, 1070));
            health = 22500;
            object = ZObjs.cube;
            size = 10;
            range = 320f;
            reload = 240f;
            consumePower(260f);
            coolantMultiplier = 1.1f;
            shootSound = Sounds.shootLancer;  // ★ v155.4 替代 UnitySounds.cubeBlast (无 shootBig)
            shootType = new PointBlastLaserBulletType(580f) {{
                length = 320f;
                lifetime = 17f;
                pierce = true;
                auraDamage = 8000f;
                damageRadius = 120f;
                laserColors = new Color[]{Color.valueOf("a3e3ff")};  // UnityPal.advance
            }};
        }};

        // ===== wavefront (PU_V8 L3431-3444, WavefrontTurret + WavefrontLaser) =====
        // 3D wavefront 炮台: 伪 3D 渲染 (因 arc 无 g3d 包, 用 WavefrontObject 替代 ModelInstance)
        // ★ v155.4 简化: 移除 AnimControl (展开/折叠动画), 保留旋转和间隙动画
        wavefront = new WavefrontTurret("wavefront") {{
            requirements(Category.turret, ItemStack.with(
                Items.copper, 4900, Items.graphite, 6000, Items.silicon, 5000,
                Items.titanium, 6500, Z_Items.xenium, 1500, Z_Items.advanceAlloy, 1500,
                Z_Items.terminum, 700, Z_Items.terminaAlloy, 500));
            health = 50625;
            object = ZObjs.wavefront;
            size = 15;
            range = 420f;
            rotateSpeed = 3f;
            reload = 240f;
            consumePower(260f);
            coolantMultiplier = 0.9f;
            shootSound = Sounds.shootLancer;  // ★ v155.4 替代 UnitySounds.cubeBlast (无 shootBig)
            shootType = new WavefrontLaserBulletType(2400f);
        }};
    }
}
