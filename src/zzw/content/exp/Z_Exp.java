package zzw.content.exp;

import arc.graphics.Color;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;
import zzw.content.Z_Items;
import zzw.content.blocks.exp.KoruhReactor;
import zzw.content.units.bullets.ExpLaserBulletType;
import zzw.content.units.bullets.GeyserBulletType;
import zzw.content.units.bullets.GeyserLaserBulletType;

import static mindustry.Vars.tilesize;
import static zzw.content.exp.EField.*;

/**
 * PU_V8 经验系统方块注册
 * 参考: PU_V8 main/src/unity/content/UnityBlocks.java L1735-2062
 * 
 * 主要功能:
 * 1. 经验存储运输: 完整的经验存储、运输、处理系统
 * 2. 经验炮台: 基于经验等级强化的特殊炮台
 * 3. 经验字段系统: 支持线性、比例、上限等多种经验增长方式
 * 4. 经验管理: 提供经验的存储、分配、消耗机制
 * 
 * 系统组件:
 * - 存储设备: ExpTank（经验罐）、ExpChest（经验箱）、ExpRouter（经验路由器）
 * - 运输设备: ExpTower（经验塔）、ExpHub（经验枢纽）、ExpNode（经验节点）
 * - 处理设备: ExpFountain（经验喷泉）、ExpVoid（经验虚空）
 * - 特殊设备: BufferTower（缓冲塔）、ExpUnloader（经验卸载器）
 * 
 * 经验炮台类型:
 * - 电力炮台: LaserTurret, ChargeLaserTurret, FractalLaserTurret, BTLaserTurret
 * - 物品炮台: InfernoTurret
 * - 液体炮台: FrostLaserTurret, KelvinLaserTurret
 * - 特殊炮台: SwarmLaserTurret（群体激光）
 * 
 * 技术特点:
 * - 使用EField系统管理经验增长
 * - 支持多种经验增长模式（线性、比例、上限）
 * - 集成经验等级系统
 * - 支持经验运输网络
 * - 兼容PU_V8的经验机制
 * 
 * 经验字段类型:
 * - ELinear: 线性增长（基础值 + 等级 * 增长率）
 * - ERational: 比例增长（基础值 * 等数 ^ 系数）
 * - ELinearCap: 线性增长带上限
 * - ECeil: 向上取整增长
 */
public class Z_Exp {
    // 经验存储运输
    public static Block expTank, expChest, expRouter, expTower, expTowerDiagonal, bufferTower,
            expHub, expUnloader, expNode, expNodeLarge, expFountain, expVoid;
    // 经验焚化炉: 焚化物品/液体产经验球
    public static ExpIncinerator expIncinerator;
    // 经验反应冲击堆发电机 (消耗铀+经验产电)
    public static KoruhReactor uraniumReactor;

    // 经验炮台 (电力)
    public static ExpPowerTurret laserTurret, chargeLaserTurret, fractalLaserTurret, btLaserTurret;
    // 经验炮台 (物品)
    public static ExpItemTurret infernoTurret;
    // 经验炮台 (液体)
    public static ExpLiquidTurret frostLaserTurret;
    // 经验炮台 (BurstCharge 电力, PU_V8 BurstChargePowerTurret 简化为 ExpPowerTurret)
    public static ExpPowerTurret swarmLaserTurret;
    // 经验系力场投影仪 (ClassicProjector)
    public static ClassicProjector shieldGenerator, deflectGenerator;
    // 经验炮台 (OmniLiquid 液体)
    public static OmniLiquidTurret kelvinLaserTurret;

    public static void load() {
        //region 经验存储运输
        expTank = new ExpTank("exp-tank"){{
            requirements(Category.effect, ItemStack.with(Items.copper, 100, Z_Items.denseAlloy, 100, Items.graphite, 30));
            expCapacity = 800;
            health = 300;
            size = 2;
        }};

        expChest = new ExpTank("exp-chest"){{
            requirements(Category.effect, ItemStack.with(Items.copper, 400, Z_Items.steel, 250, Items.phaseFabric, 120));
            expCapacity = 3600;
            health = 1200;
            size = 4;
        }};

        expRouter = new ExpRouter("exp-router"){{
            requirements(Category.effect, ItemStack.with(Z_Items.stone, 5));
        }};

        expTower = new ExpTower("exp-tower"){{
            requirements(Category.effect, ItemStack.with(Z_Items.denseAlloy, 10, Items.silicon, 5));
            expCapacity = 100;
        }};

        expTowerDiagonal = new DiagonalTower("diagonal-tower"){{
            requirements(Category.effect, ItemStack.with(Z_Items.steel, 10, Items.silicon, 5));
            range = 7;
            expCapacity = 150;
        }};

        bufferTower = new ExpTower("buffer-tower"){{
            requirements(Category.effect, ItemStack.with(Items.thorium, 5, Items.graphite, 10));
            reloadTime = 20f;
            expCapacity = 180;
            buffer = true;
            health = 300;
        }};

        expHub = new ExpHub("exp-output"){{
            requirements(Category.effect, ItemStack.with(Z_Items.stone, 30, Items.copper, 15));
            expCapacity = 100;
        }};

        // 经验卸载器: 从相邻经验储罐/塔抽取经验, 沿朝向发射经验球到传送带
        expUnloader = new ExpUnloader("exp-unloader"){{
            requirements(Category.effect, ItemStack.with(Z_Items.denseAlloy, 30, Items.copper, 20, Items.silicon, 10));
            expCapacity = 100;
            reloadTime = 30f;
        }};

        expNode = new ExpNode("exp-node"){{
            requirements(Category.effect, ItemStack.with(Z_Items.denseAlloy, 30, Items.silicon, 30, Z_Items.steel, 8));
            expCapacity = 200;
            consumePower(0.6f);
        }};

        expNodeLarge = new ExpNode("exp-node-large"){{
            requirements(Category.effect, ItemStack.with(Z_Items.denseAlloy, 120, Items.silicon, 120, Z_Items.steel, 24));
            expCapacity = 600;
            range = 10;
            health = 200;
            size = 2;
            consumePower(1.4f);
        }};

        expFountain = new ExpSource("exp-fountain"){{
            requirements(Category.effect, ItemStack.with());
            buildVisibility = mindustry.world.meta.BuildVisibility.sandboxOnly;
        }};

        expVoid = new ExpVoid("exp-void"){{
            requirements(Category.effect, ItemStack.with());
            buildVisibility = mindustry.world.meta.BuildVisibility.sandboxOnly;
        }};

        // 经验焚化炉: 焚化任意物品/液体产经验球 (1x1, 耗电比原版焚化炉多 0.2/tick)
        expIncinerator = new ExpIncinerator("exp-incinerator"){{
            requirements(Category.effect, ItemStack.with(Items.copper, 20, Items.lead, 20, Z_Items.denseAlloy, 10));
            health = 90;
            liquidCapacity = 20f;
            // 原版焚化炉 0.5 + 0.2 = 0.7/tick
            consumePower(0.7f);
            envEnabled |= mindustry.world.meta.Env.space;
        }};

        // 经验反应冲击堆发电机 (PU132 UnityBlocks L1766-1781, KoruhReactor)
        // 消耗铀x2 + 水 + 电20 启动, 产电150; 维持反应持续消耗经验(expUse=2/次)
        // 经验不足时受损伤, 摧毁时大量喷出经验球 (KoruhReactorBuild 内置逻辑)
        uraniumReactor = new KoruhReactor("uranium-reactor"){{
            requirements(Category.power, ItemStack.with(Items.plastanium, 80, Items.surgeAlloy, 100, Items.lead, 150, Z_Items.steel, 200));
            size = 3;
            itemDuration = 200f;
            consumeItem(Z_Items.uranium, 2);
            consumeLiquid(mindustry.content.Liquids.water, 0.7f);
            consumePower(20f);
            itemCapacity = 20;
            powerProduction = 150f;
            health = 1000;
            // PU132 plasma1/plasma2 (v132 ImpactReactor 字段) → v155.4 移入 DrawPlasma drawer
            drawer = new mindustry.world.draw.DrawMulti(
                new mindustry.world.draw.DrawRegion("-bottom"),
                new mindustry.world.draw.DrawPlasma(){{
                    plasma1 = Color.valueOf("a5e1a2");
                    plasma2 = Color.valueOf("869B84");
                }},
                new mindustry.world.draw.DrawDefault()
            );
        }};
        //endregion

        //region 经验炮台
        laserTurret = new ExpPowerTurret("laser-turret"){{
            requirements(Category.turret, ItemStack.with(Items.copper, 90, Items.silicon, 40, Items.titanium, 15));
            size = 2;
            health = 600;

            reload = 35f;
            coolantMultiplier = 2f;
            range = 140f;
            targetAir = false;
            shootSound = V7Sounds.laser;

            powerUse = 7f;
            shootType = new ExpLaserBulletType(140f, 20f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, arc.graphics.Color.white};
                hitEffect = mindustry.content.Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                collidesAir = false;
                ammoMultiplier = 1f;
                lengthInc = 2f;
                damageInc = 7f;
            }};

            maxLevel = 10;
            expFields = new EField[]{
                new LinearReloadTime(v -> reload = v, 45f, -2f),
                new ELinear(v -> range = v, 120f, 2f, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " blocks"),
                new EBool(v -> targetAir = v, false, 5, mindustry.world.meta.Stat.targetsAir)
            };
        }};

        chargeLaserTurret = new ExpPowerTurret("charge-laser-turret"){{
            requirements(Category.turret, ItemStack.with(Z_Items.denseAlloy, 60, Items.graphite, 15));
            size = 2;
            health = 1400;

            reload = 60f;
            coolantMultiplier = 2f;
            range = 140f;

            shoot.firstShotDelay = 50f;
            recoil = 2f;
            targetAir = true;
            shake = 2f;

            powerUse = 7f;

            shootEffect = mindustry.content.Fx.lancerLaserShoot;
            smokeEffect = mindustry.content.Fx.none;
            heatColor = mindustry.graphics.Pal.redderDust;
            shootSound = V7Sounds.laser;

            shootType = new ExpLaserBulletType(140f, 35f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, arc.graphics.Color.white};
                hitEffect = mindustry.content.Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                ammoMultiplier = 1f;
                lengthInc = 1.3f;
                damageInc = 5f;
            }};

            maxLevel = 30;
            expFields = new EField[]{
                new LinearReloadTime(v -> reload = v, 60f, -1f),
                new ELinear(v -> range = v, 140f, 1.3f, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " blocks")
            };
            pregrade = laserTurret;
            effectColors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser};
        }};

        frostLaserTurret = new ExpLiquidTurret("frost-laser-turret"){{
            ammo(mindustry.content.Liquids.cryofluid, new ExpLaserBulletType(160f, 20f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, arc.graphics.Color.white};
                hitEffect = mindustry.content.Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                ammoMultiplier = 1f;
                damageInc = 2.5f;
            }});
            requirements(Category.turret, ItemStack.with(Z_Items.denseAlloy, 60, Items.metaglass, 15));
            size = 2;
            health = 1000;

            range = 160f;
            reload = 80f;
            targetAir = true;
            liquidCapacity = 10f;
            shootSound = V7Sounds.laser;
            extinguish = false;

            maxLevel = 30;

            consumePowerCond(1f, Turret.TurretBuild::isActive);
            pregrade = laserTurret;
        }};

        fractalLaserTurret = new ExpPowerTurret("fractal-laser-turret"){{
            requirements(Category.turret, ItemStack.with(Z_Items.steel, 50, Items.graphite, 90, Items.thorium, 95));
            size = 3;
            health = 2000;

            reload = 60f;
            coolantMultiplier = 2f;
            range = 140f;

            shoot.firstShotDelay = 80f;
            recoil = 4f;

            targetAir = true;
            shake = 5f;
            powerUse = 13f;

            shootEffect = mindustry.content.Fx.lancerLaserShoot;
            smokeEffect = mindustry.content.Fx.none;
            shootSound = V7Sounds.laser;

            heatColor = mindustry.graphics.Pal.redderDust;
            fromColor = mindustry.graphics.Pal.lancerLaser;
            toColor = mindustry.graphics.Pal.place;

            shootType = new ExpLaserBulletType(160f, 90f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, arc.graphics.Color.white};
                hitEffect = mindustry.content.Fx.hitLaserBlast;
                hitSize = 6;
                lifetime = 20f;
                drawSize = 400f;
                ammoMultiplier = 1f;
                pierceCap = 4;
                lengthInc = 2f;
                damageInc = 6f;
            }};

            maxLevel = 30;
            expFields = new EField[]{
                new LinearReloadTime(v -> reload = v, 60f, -2f),
                new ELinear(v -> range = v, 140f, 0.25f * tilesize, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " blocks")
            };

            pregrade = chargeLaserTurret;
            pregradeLevel = 15;
        }};

        btLaserTurret = new ExpPowerTurret("bt-laser-turret"){{
            requirements(Category.turret, ItemStack.with(Items.surgeAlloy, 80, Z_Items.steel, 120, Z_Items.dirium, 70));
            size = 4;
            health = 2400;

            reload = 90f;
            coolantMultiplier = 2f;
            range = 160f;

            shoot.firstShotDelay = 100f;
            recoil = 4f;
            targetAir = true;
            shake = 6f;
            powerUse = 15f;

            shootEffect = mindustry.content.Fx.lancerLaserShoot;
            smokeEffect = mindustry.content.Fx.none;
            shootSound = V7Sounds.laser;

            heatColor = mindustry.graphics.Pal.redderDust;
            toColor = UnityPal.exp;

            shootType = new ExpLaserBulletType(240f, 150f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, UnityPal.exp};
                hitEffect = mindustry.content.Fx.hitLaserBlast;
                hitSize = 8;
                lifetime = 22f;
                drawSize = 500f;
                width = 28f;
                ammoMultiplier = 1f;
                pierceCap = 6;
                lengthInc = 1f;
                damageInc = 15f;
            }};

            expScale = 30;
            pregrade = chargeLaserTurret;
            maxLevel = 20;
            expFields = new EField[]{
                new LinearReloadTime(v -> reload = v, 90f, -3f),
                new ELinear(v -> range = v, 160f, 1f, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " blocks")
            };
            effectColors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser, UnityPal.exp};
        }};

        infernoTurret = new ExpItemTurret("inferno"){{
            // ★完整移植 PU_V8: 3 种弹药 (scrap/slagShot, coal/coalBlaze, pyratite/pyraBlaze)
            // shootSmallBlaze/shootPyraBlaze: 火焰色粒子向射击方向喷射 (PU_V8 自定义)
            // ★ v158 简化: 用 BulletType 替代 ExpBulletType, 用自定义 Effect 替代 ShootFx
            ammo(
                mindustry.content.Items.scrap, new mindustry.entities.bullet.LiquidBulletType(mindustry.content.Liquids.slag) {{
                    // ★ PU_V8 Bullets.slagShot 等效 (来自 PU特供v132版): damage=4.0f, drag=0.01f
                    damage = 4.0f;
                    drag = 0.01f;
                }},
                mindustry.content.Items.coal, new mindustry.entities.bullet.BulletType(3.35f, 32f) {{
                    ammoMultiplier = 3;
                    hitSize = 7f;
                    lifetime = 24f;
                    pierce = true;
                    collidesAir = false;
                    statusDuration = 60f * 4;
                    // ★ PU_V8 shootSmallBlaze: 火焰色 (lightFlame/darkFlame/gray) 16粒子向射击方向喷射
                    shootEffect = new mindustry.entities.Effect(22f, e -> {
                        arc.graphics.g2d.Draw.color(Pal.lightFlame, Pal.darkFlame, Pal.gray, e.fin());
                        arc.math.Angles.randLenVectors(e.id, 16, e.finpow() * 60f, e.rotation, 18f, (x, y) ->
                            arc.graphics.g2d.Fill.circle(e.x + x, e.y + y, 0.85f + e.fout() * 3.5f));
                    });
                    hitEffect = mindustry.content.Fx.hitFlameSmall;
                    despawnEffect = mindustry.content.Fx.none;
                    status = mindustry.content.StatusEffects.burning;
                    keepVelocity = true;
                    hittable = false;
                }},
                mindustry.content.Items.pyratite, new mindustry.entities.bullet.BulletType(3.35f, 46f) {{
                    ammoMultiplier = 3;
                    hitSize = 7f;
                    lifetime = 24f;
                    pierce = true;
                    collidesAir = false;
                    statusDuration = 60f * 4;
                    // ★ PU_V8 shootPyraBlaze: pyra 火焰色粒子
                    shootEffect = new mindustry.entities.Effect(32f, e -> {
                        arc.graphics.g2d.Draw.color(Pal.lightPyraFlame, Pal.darkPyraFlame, Pal.gray, e.fin());
                        arc.math.Angles.randLenVectors(e.id, 16, e.finpow() * 60f, e.rotation, 18f, (x, y) ->
                            arc.graphics.g2d.Fill.circle(e.x + x, e.y + y, 0.85f + e.fout() * 3.5f));
                    });
                    hitEffect = mindustry.content.Fx.hitFlameSmall;
                    despawnEffect = mindustry.content.Fx.none;
                    status = mindustry.content.StatusEffects.burning;
                    keepVelocity = false;
                    hittable = false;
                }}
            );
            requirements(Category.turret, ItemStack.with(Z_Items.stone, 150, Z_Items.denseAlloy, 65, Items.graphite, 60));
            size = 3;
            health = 1200;

            reload = 6f;  // ★ PU_V8 reloadTime=6f (快速发射)
            range = 80f;  // ★ PU_V8 range=80f
            targetAir = false;
            targetGround = true;
            shootCone = 5f;
            recoil = 0f;
            coolantMultiplier = 2f;
            shootSound = Sounds.shootFlame;  // ★ PU_V8 Sounds.flame → v158 Sounds.shootFlame (火焰喷射)
            heatColor = mindustry.graphics.Pal.redderDust;

            // ★ v158: shoot 默认为 ShootPattern (无 spread 字段), 需初始化为 ShootSpread
            shoot = new mindustry.entities.pattern.ShootSpread(1, 0f);
            maxLevel = 10;  // ★ PU_V8 maxLevel=10
            expFields = new EField[]{
                new EList<>(v -> shoot.shots = v, new Integer[]{1, 1, 2, 2, 2, 3, 3, 4, 4, 5, 5}, mindustry.world.meta.Stat.shots),
                new EList<>(v -> ((mindustry.entities.pattern.ShootSpread)shoot).spread = v, new Float[]{0f, 0f, 5f, 10f, 15f, 7f, 14f, 8f, 10f, 6f, 9f}, null)
            };
        }};

        // ===== swarmLaserTurret (PU_V8 L1890-1935, BurstChargePowerTurret → 简化为 ExpPowerTurret)
        // PU_V8: chargeTime=50, chargeMaxDelay=30, chargeEffects=4, shots=4, burstSpacing=20
        // ★ v158 完整移植: firstShotDelay=50 (chargeTime), shotDelay=20 (burstSpacing), shots=4
        // shootSound: PU_V8 Sounds.plasmaboom → v158 无该音效, 用 Z_Sounds.singularityShoot 替代
        swarmLaserTurret = new ExpPowerTurret("swarm-laser-turret"){{
            requirements(Category.turret, ItemStack.with(Z_Items.steel, 50, Items.silicon, 90, Items.thorium, 95));
            size = 3;
            health = 2400;

            reload = 90f;
            coolantMultiplier = 2.25f;
            powerUse = 15f;
            targetAir = true;
            range = 150f;

            // ★ PU_V8 完整移植: ShootPattern + shotDelay 实现间隔发射 (每发间隔20tick)
            shoot = new mindustry.entities.pattern.ShootPattern();
            shoot.shots = 4;
            shoot.firstShotDelay = 50f;  // 充能时间
            shoot.shotDelay = 20f;  // ★ burstSpacing: 4发依次间隔20tick发射
            inaccuracy = 1f;

            recoil = 2f;
            cooldownTime = 0.03f;  // v158 用 cooldownTime 替代 cooldown
            shake = 2f;
            shootEffect = mindustry.content.Fx.lancerLaserShoot;
            smokeEffect = mindustry.content.Fx.none;
            heatColor = Color.red;
            shootSound = zzw.content.Z_Sounds.singularityShoot;

            // branchLaser 子弹: 激光 + 3 发 frag (branchLaserFrag)
            // PU_V8: ExpLaserBulletType(140, 20) + fragBullet=branchLaserFrag + fragBullets=3
            shootType = new ExpLaserBulletType(150f, 20f){{
                colors = new Color[]{
                        Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.5f).a(0.4f),
                        Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.5f),
                        Color.white
                };
                hitEffect = mindustry.content.Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                collidesAir = false;
                ammoMultiplier = 1f;
                pierceCap = 10;
                lengthInc = 2f;
                damageInc = 6f;
                status = StatusEffects.shocked;
                statusDuration = 3 * 60f;

                // frag: branchLaserFrag (BasicBulletType 简化版)
                fragBullets = 3;
                fragBullet = new BasicBulletType(3.5f, 15f){{
                    width = 4f;
                    height = 4f;
                    lifetime = 30f;
                    shootEffect = mindustry.content.Fx.hitLancer;
                    hitEffect = mindustry.content.Fx.hitLancer;
                    despawnEffect = mindustry.content.Fx.none;
                    pierceCap = 10;
                    pierceBuilding = true;
                    splashDamageRadius = 4f;
                    splashDamage = 4f;
                    status = StatusEffects.burning;  // PU_V8 UnityStatusEffects.plasmaed → v158 burning
                    statusDuration = 180f;
                    trailLength = 6;
                    trailColor = Color.white;
                    weaveScale = 0.6f;
                    weaveMag = 0.5f;
                    homingPower = 0.4f;
                    frontColor = Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.5f);
                    backColor = Pal.sapBullet;
                    hitColor = Pal.sapBullet;
                }};
            }};

            maxLevel = 30;
            expFields = new EField[]{
                    // v158: shots 通过 ShootSpread 设置, 这里 EField 只能修改 inaccuracy/range
                    // shots 字段简化为通过 maxLevel 增加伤害而非数量 (因 ShootSpread 在 init 时已固定)
                    new ELinearCap(v -> inaccuracy = v, 1f, 0.25f, 10, mindustry.world.meta.Stat.inaccuracy, v -> arc.util.Strings.autoFixed(v, 1) + " degrees"),
                    new ELinear(v -> range = v, 150f, 2f, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " blocks")
            };
            pregrade = chargeLaserTurret;
            pregradeLevel = 15;
            effectColors = new Color[]{
                    Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.3f),
                    Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.6f),
                    Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.8f),
                    Pal.sapBullet
            };
        }};

        // ===== kelvinLaserTurret (PU_V8 L1937-1960, OmniLiquidTurret + GeyserLaserBulletType)
        // PU_V8: 基于当前液体类型调整伤害/击退/特效, 在目标点生成 GeyserBulletType 喷泉
        kelvinLaserTurret = new OmniLiquidTurret("kelvin-laser-turret"){{
            requirements(Category.turret, ItemStack.with(Items.phaseFabric, 50, Items.metaglass, 90, Items.thorium, 95));
            size = 3;
            health = 2100;

            range = 180f;
            reload = 120f;
            targetAir = true;
            liquidCapacity = 15f;
            shootAmount = 3f;
            shootSound = Sounds.shootLaser;

            // GeyserLaserBulletType: 激光命中后生成 GeyserBulletType 喷泉
            shootType = new GeyserLaserBulletType(185f, 30f){{
                geyser = new GeyserBulletType(400f, 10f){{
                    radius = 25f;
                }};
                damageInc = 5f;
            }};

            consumePowerCond(2.5f, Turret.TurretBuild::isActive);

            maxLevel = 30;
            pregrade = frostLaserTurret;
            pregradeLevel = 15;
        }};

        // ===== 经验系力场投影仪 (PU132 UnityBlocks L1660-1702, ClassicProjector) =====

        // shield-generator: 经验力墙 (被打获得经验, 等级提升半径与护盾血量)
        shieldGenerator = new ClassicProjector("shield-generator"){{
            requirements(Category.effect, ItemStack.with(Items.silicon, 50, Items.titanium, 35, Z_Items.steel, 15));
            health = 200;
            cooldownNormal = 1f;
            cooldownBrokenBase = 0.3f;
            phaseRadiusBoost = 10f;
            phaseShieldBoost = 200;
            hasItems = hasLiquids = false;

            consumePower(1.5f);

            maxLevel = 15;
            expFields = new EField[]{
                    new ELinear(v -> radius = v, 40f, 0.5f, mindustry.world.meta.Stat.range, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " blocks"),
                    new ELinear(v -> shieldHealth = v, 500f, 25f, mindustry.world.meta.Stat.shieldHealth)
            };
            fromColor = toColor = Pal.lancerLaser;
        }};

        // deflect-generator: 偏折发生器 (经验力墙 + 子弹反弹, shield-generator 5级升级而来)
        deflectGenerator = new ClassicProjector("deflect-generator"){{
            requirements(Category.effect, ItemStack.with(Items.silicon, 50, Items.titanium, 30, Z_Items.steel, 30, Z_Items.dirium, 8));
            health = 800;
            size = 2;
            cooldownNormal = 1.5f;
            cooldownLiquid = 1.2f;
            cooldownBrokenBase = 0.35f;
            phaseRadiusBoost = 40f;

            consumeItem(Items.phaseFabric).boost();
            consumePower(5f);

            fromColor = Pal.lancerLaser;
            toColor = zzw.content.graphics.UnityPal.diriumLight;
            maxLevel = 30;
            expFields = new EField[]{
                    new ELinear(v -> radius = v, 60f, 0.75f, mindustry.world.meta.Stat.range, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " blocks"),
                    new ELinear(v -> shieldHealth = v, 820f, 35f, mindustry.world.meta.Stat.shieldHealth),
                    new ELinear(v -> deflectChance = v, 0f, 0.1f, mindustry.world.meta.Stat.baseDeflectChance, v -> arc.util.Strings.autoFixed(v * 100, 1) + "%")
            };
            pregrade = shieldGenerator;
            pregradeLevel = 5;
            effectColors = new Color[]{Pal.lancerLaser, zzw.content.graphics.UnityPal.lancerDir1, zzw.content.graphics.UnityPal.lancerDir2, zzw.content.graphics.UnityPal.lancerDir3, zzw.content.graphics.UnityPal.diriumLight};
        }};
        //endregion
    }
}
