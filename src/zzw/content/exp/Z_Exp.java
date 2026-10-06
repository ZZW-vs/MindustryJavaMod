package zzw.content.exp;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.entities.Damage;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.effect.MultiEffect;
import mindustry.gen.Bullet;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;
import zzw.content.Z_Sounds;
import zzw.content.Z_Items;
import zzw.content.Z_StatusEffects;
import zzw.content.blocks.exp.KoruhReactor;
import zzw.content.units.bullets.DistFieldBulletType;
import zzw.content.units.bullets.ExpLaserBulletType;
import zzw.content.units.bullets.ExpLaserFieldBulletType;
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

    /**
     * 全量加载: 经验存储运输 + 力场投影仪 (非炮台部分)。
     * <p>
     * ★ 经验炮台已拆分到 loadTurrets(), 由 TestMod 在 turret 类别的 koruh 派系位置调用,
     * 确保 PU 移植炮台在物品栏按 PU132 原版派系顺序成组排列。
     */
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
            // 基础电力输出 120000/秒 (面板), 消耗电力 1800/秒, 启动时间 18 秒
            // 维持反应持续消耗经验(expUse=2/次), 经验不足时受损伤, 摧毁时大量喷出经验球
            uraniumReactor = new KoruhReactor("uranium-reactor"){{
                requirements(Category.power, ItemStack.with(Items.plastanium, 80, Items.surgeAlloy, 100, Items.lead, 150, Z_Items.steel, 200));
                size = 3;
                itemDuration = 200f;
                consumeItem(Z_Items.uranium, 2);
                consumeLiquid(mindustry.content.Liquids.water, 0.7f);
                // 消耗 1800 电力/秒 = 30/tick
                consumePower(30f);
                itemCapacity = 20;
                // 面板显示 120000 电力/秒 = 内部 2000/tick
                powerProduction = 2000f;
                // 启动 (升温) 时间 18 秒: warmupSpeed = -ln(0.001)/(18*60) ≈ 0.0064
                warmupSpeed = 0.0064f;
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

        //region 经验系力场投影仪 (炮台部分已拆至 loadTurrets, 由 TestMod 在 koruh 派系位置调用)

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
                    new ELinear(v -> radius = v, 40f, 0.5f, mindustry.world.meta.Stat.range, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " " + mindustry.world.meta.StatUnit.blocks.localized()),
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
                    new ELinear(v -> radius = v, 60f, 0.75f, mindustry.world.meta.Stat.range, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " " + mindustry.world.meta.StatUnit.blocks.localized()),
                    new ELinear(v -> shieldHealth = v, 820f, 35f, mindustry.world.meta.Stat.shieldHealth),
                    new ELinear(v -> deflectChance = v, 0f, 0.1f, mindustry.world.meta.Stat.baseDeflectChance, v -> arc.util.Strings.autoFixed(v * 100, 1) + "%")
            };
            pregrade = shieldGenerator;
            pregradeLevel = 5;
            effectColors = new Color[]{Pal.lancerLaser, zzw.content.graphics.UnityPal.lancerDir1, zzw.content.graphics.UnityPal.lancerDir2, zzw.content.graphics.UnityPal.lancerDir3, zzw.content.graphics.UnityPal.diriumLight};
        }};
        //endregion
    }

    /**
     * 经验炮台 8 个 (koruh 派系, PU132 UnityBlocks 注册顺序):
     * laser-turret, charge-laser-turret, frost-laser-turret, fractal-laser-turret,
     * swarm-laser-turret, kelvin-laser-turret, bt-laser-turret, inferno
     * <p>
     * ★ 物品栏顺序 = 注册顺序, 由 TestMod 在 imber 之后 monolith 之前调用。
     */
    public static void loadTurrets() {
        // ===== PU132 UnityBullets.shardLaserFrag (charge-laser-turret 的激光碎片) =====
        // 白色短线段碎片, 覆写 draw 手动绘制, 不依赖贴图
        BasicBulletType shardLaserFrag = new BasicBulletType(2f, 10f){
            {
                lifetime = 20f;
                pierceCap = 10;
                pierceBuilding = true;
                backColor = Color.white.cpy().lerp(Pal.lancerLaser, 0.1f);
                frontColor = Color.white;
                hitEffect = mindustry.content.Fx.none;
                despawnEffect = mindustry.content.Fx.none;
                smokeEffect = mindustry.content.Fx.hitLaser;
                hittable = false;
                reflectable = false;
                lightColor = Color.white;
                lightOpacity = 0.6f;
            }

            @Override
            public void draw(Bullet b){
                Draw.color(Pal.lancerLaser);
                Lines.stroke(2f * b.fout(0.7f) + 0.01f);
                Lines.lineAngleCenter(b.x, b.y, b.rotation(), 8f);
                Lines.stroke(1.3f * b.fout(0.7f) + 0.01f);
                Draw.color(frontColor);
                Lines.lineAngleCenter(b.x, b.y, b.rotation(), 5f);
                Draw.reset();
            }
        };

        // ===== PU132 UnityBullets.branchLaserFrag (swarm-laser-turret 的分裂碎片) =====
        BasicBulletType branchLaserFrag = new BasicBulletType(3.5f, 15f){{
            trailWidth = 2f;
            weaveScale = 0.6f;
            weaveMag = 0.5f;
            homingPower = 0.4f;
            lifetime = 30f;
            shootEffect = mindustry.content.Fx.hitLancer;
            hitEffect = mindustry.content.Fx.hitLancer;
            despawnEffect = mindustry.content.Fx.hitLancer;
            pierceCap = 10;
            pierceBuilding = true;
            splashDamageRadius = 4f;
            splashDamage = 4f;
            status = StatusEffects.burning;  // PU132 UnityStatusEffects.plasmaed → v160 burning 近似
            statusDuration = 180f;
            trailLength = 6;
            trailColor = Color.white;
            frontColor = Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.5f);
            backColor = Pal.sapBullet;
            hitColor = Pal.sapBullet;
        }};

        // ===== PU132 UnityBullets.distField / smallDistField (fractal-laser-turret 的扭曲力场) =====
        DistFieldBulletType distFieldBullet = new DistFieldBulletType(0f, -1f){{
            centerColor = Pal.lancerLaser.cpy().a(0f);
            edgeColor = Pal.place;
            distSplashFx = UnityFx.distSplashFx;
            distStart = UnityFx.distStart;
            distStatus = Z_StatusEffects.distort;

            collidesTiles = false;
            collides = false;
            collidesAir = false;
            keepVelocity = false;

            lifetime = 6f * 60f;
            radius = 3f * 8f;
            radiusInc = 0.1f * 8f;
            bulletSlow = 0.1f;
            bulletSlowInc = 0.025f;
            damageLimit = 100f;
            distDamage = 0.1f;
        }};

        DistFieldBulletType smallDistFieldBullet = new DistFieldBulletType(0f, -1f){{
            centerColor = Pal.lancerLaser.cpy().a(0f);
            edgeColor = Pal.place;
            distSplashFx = UnityFx.distSplashFx;
            distStart = UnityFx.distStart;
            distStatus = Z_StatusEffects.distort;

            collidesTiles = false;
            collides = false;
            collidesAir = false;
            keepVelocity = false;

            lifetime = 2.5f * 60f;
            radius = 1.5f * 8f;
            radiusInc = 0.05f * 8f;
            bulletSlow = 0.05f;
            bulletSlowInc = 0.015f;
            damageLimit = 50f;
            distDamage = 0.05f;
        }};

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
            shootType = new ExpLaserBulletType(140f, 32f){{
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
                new ELinear(v -> range = v, 120f, 2f, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " " + mindustry.world.meta.StatUnit.blocks.localized()),
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

            shoot.firstShotDelay = 50f;   // PU132 chargeTime
            moveWhileCharging = false;
            recoil = 2f;
            cooldownTime = 0.03f;         // PU132 cooldown
            targetAir = true;
            shake = 2f;

            powerUse = 7f;

            shootEffect = UnityFx.laserChargeShoot;   // PU132 ShootFx.laserChargeShoot
            smokeEffect = mindustry.content.Fx.none;
            chargeSound = Sounds.chargeLancer;        // PU132 Sounds.laser (v160 无同名音, 用 chargeLancer 近似)
            heatColor = mindustry.graphics.Pal.redderDust;
            shootSound = V7Sounds.laser;

            // PU132 UnityBullets.shardLaser: 激光 + 1 发激光碎片
            shootType = new ExpLaserBulletType(150f, 40f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, arc.graphics.Color.white};
                hitEffect = mindustry.content.Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                ammoMultiplier = 1f;
                status = StatusEffects.shocked;
                statusDuration = 180f;
                damageInc = 5f;
                fromColor = Pal.lancerLaser;
                toColor = Pal.sapBullet;
                fragBullet = shardLaserFrag;
                // v160 蓄力由 TurretBuild.shoot() 驱动: 开始蓄力时播放一次 chargeEffect
                chargeEffect = new MultiEffect(UnityFx.laserCharge, UnityFx.laserChargeBegin);
            }};

            maxLevel = 30;
            expFields = new EField[]{
                new LinearReloadTime(v -> reload = v, 60f, -1f),
                new ELinear(v -> range = v, 140f, 1.3f, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " " + mindustry.world.meta.StatUnit.blocks.localized())
            };
            pregrade = laserTurret;
            effectColors = new arc.graphics.Color[]{Pal.lancerLaser, zzw.content.exp.UnityPal.lancerSap1, zzw.content.exp.UnityPal.lancerSap2, zzw.content.exp.UnityPal.lancerSap3, zzw.content.exp.UnityPal.lancerSap4, zzw.content.exp.UnityPal.lancerSap5, Pal.sapBullet};
        }};

        frostLaserTurret = new ExpLiquidTurret("frost-laser-turret"){{
            // PU132 UnityBullets.frostLaser: 命中后生成冻结圈 (freezePos)
            ammo(mindustry.content.Liquids.cryofluid, new ExpLaserBulletType(170f, 130){
                {
                    colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, arc.graphics.Color.white};
                    hitEffect = mindustry.content.Fx.hitLancer;
                    hitSize = 4;
                    lifetime = 16f;
                    drawSize = 400f;
                    ammoMultiplier = 1f;
                    status = StatusEffects.freezing;
                    statusDuration = 180f;
                    shootEffect = UnityFx.shootFlake;
                    damageInc = 2.5f;
                    fromColor = Liquids.cryofluid.color;
                    toColor = Color.cyan;
                    // ★ PU132 原版还原: 细三层描线激光 + 命中点扩散光圈 (blip),
                    //   且激光束止于首个命中目标 (不再永远画到最大长度 → 不再"只打到最远处")
                    width = 1f;
                    puLaser = true;
                    blip = true;
                }

                @Override
                public void onHit(Bullet b, float x, float y){
                    int lvl = getLevel(b);
                    float rad = 3.5f;
                    // ★ PU132 攻击方式补齐: frostLaser.expGain = 2
                    //   原版 ExpLaserBulletType.init 命中后调用 handleExp(b, x, y, expGain) 给炮台加经验,
                    //   这是该炮台唯一的升级途径 (它没有碎片弹), 此前移植漏掉了 → 炮台永远无法升级.
                    if(b.owner instanceof ExpTurret.ExpTurretBuild exp) exp.handleExp(2);
                    // PU132 freezePos: 冻结爆裂特效 + 冻结音效 + 范围冻结/瘫痪
                    UnityFx.freezeEffect.at(x, y, lvl / rad + 10f, getColor(b));
                    Z_Sounds.laserFreeze.at(x, y);

                    Damage.status(b.team, x, y, 10f + lvl / rad, status, 60f + lvl * 6f, true, true);
                    Damage.status(b.team, x, y, 10f + lvl / rad, Z_StatusEffects.disabled, 2f * lvl, true, true);
                }
            });
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

            reload = distFieldBullet.lifetime / 3f;   // PU132: UnityBullets.distField.lifetime / 3f
            coolantMultiplier = 2f;
            range = 140f;

            shoot.firstShotDelay = 80f;   // PU132 chargeTime
            moveWhileCharging = false;
            recoil = 4f;
            cooldownTime = 0.03f;         // PU132 cooldown

            targetAir = true;
            shake = 5f;
            powerUse = 13f;

            shootEffect = UnityFx.laserFractalShoot;  // PU132 ShootFx.laserFractalShoot
            smokeEffect = mindustry.content.Fx.none;
            chargeSound = Sounds.chargeLancer;        // PU132 Sounds.laser (v160 无同名音, 用 chargeLancer 近似)
            shootSound = V7Sounds.laser;

            heatColor = mindustry.graphics.Pal.redderDust;
            fromColor = zzw.content.exp.UnityPal.lancerSap3;
            toColor = Pal.place;

            // PU132 UnityBullets.fractalLaser: 裂缝激光 + 命中生成扭曲力场
            shootType = new ExpLaserFieldBulletType(170f, 164f){{
                colors = new arc.graphics.Color[]{Pal.lancerLaser.cpy().lerp(Pal.place, 0.5f).a(0.4f), Pal.lancerLaser.cpy().lerp(Pal.place, 0.5f), arc.graphics.Color.white};
                hitEffect = mindustry.content.Fx.hitLaserBlast;
                hitSize = 6;
                lifetime = 20f;
                drawSize = 400f;
                ammoMultiplier = 1f;
                width = 2f;
                lengthInc = 2f;
                damageInc = 6f;
                fields = 2;
                fieldInc = 0.15f;
                maxRange = 150f + 2f * 30f;   // PU132: 计入射程增长
                fromColor = Pal.lancerLaser.cpy().lerp(Pal.place, 0.5f);
                toColor = Pal.place;
                distField = distFieldBullet;
                smallDistField = smallDistFieldBullet;
                // v160 蓄力由 TurretBuild.shoot() 驱动: 开始蓄力时播放一次 chargeEffect
                chargeEffect = new MultiEffect(UnityFx.laserFractalCharge, UnityFx.laserFractalChargeBegin);
            }};

            maxLevel = 30;
            expFields = new EField[]{
                new LinearReloadTime(v -> reload = v, distFieldBullet.lifetime / 3f, -2f),
                new ELinear(v -> range = v, 140f, 0.25f * tilesize, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " " + mindustry.world.meta.StatUnit.blocks.localized())
            };

            pregrade = chargeLaserTurret;
            pregradeLevel = 15;
            effectColors = new arc.graphics.Color[]{fromColor, Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.75f), Pal.sapBullet};
        }};

        // ===== swarmLaserTurret (PU132 UnityBlocks L1915-1960, BurstChargePowerTurret)
        // 连发蓄力: 一次装填按 burstSpacing 依次蓄力发射 shots 发, 每发独立播放蓄力特效
        // shootSound: PU132 Sounds.plasmaboom → v160 无该音效, 用 Z_Sounds.singularityShoot 替代
        swarmLaserTurret = new BurstChargePowerTurret("swarm-laser-turret"){{
            requirements(Category.turret, ItemStack.with(Z_Items.steel, 50, Items.silicon, 90, Items.thorium, 95));
            size = 3;
            health = 2400;

            reload = 90f;
            coolantMultiplier = 2.25f;
            powerUse = 15f;
            targetAir = true;
            range = 150f;

            chargeTime = 50f;
            chargeMaxDelay = 30f;
            chargeEffects = 4;
            recoil = 2f;
            cooldownTime = 0.03f;  // v158/v160 用 cooldownTime 替代 PU132 cooldown
            shake = 2f;
            shootEffect = UnityFx.laserChargeShootShort;  // PU132 ShootFx.laserChargeShootShort
            smokeEffect = mindustry.content.Fx.none;
            chargeEffect = UnityFx.laserChargeShort;      // PU132 UnityFx.laserChargeShort
            chargeBeginEffect = UnityFx.laserChargeBegin; // PU132 UnityFx.laserChargeBegin
            heatColor = Color.red;
            fromColor = zzw.content.exp.UnityPal.lancerSap3;
            shootSound = zzw.content.Z_Sounds.singularityShoot;

            // PU132 UnityBullets.branchLaser: 激光 + 3 发分裂碎片
            shootType = new ExpLaserBulletType(140f, 76f){{
                colors = new Color[]{
                        Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.5f).a(0.4f),
                        Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.5f),
                        Color.white
                };
                hitEffect = mindustry.content.Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                ammoMultiplier = 1f;
                status = StatusEffects.shocked;
                statusDuration = 180f;
                fragBullets = 3;
                fragBullet = branchLaserFrag;
                maxRange = 150f + 2f * 30f;   // PU132: 计入射程增长
                damageInc = 6f;
                lengthInc = 2f;
                fromColor = Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.5f);
                toColor = Pal.sapBullet;
            }};

            shootLength = size * tilesize / 2.7f;
            shots = 4;
            burstSpacing = 20f;
            inaccuracy = 1f;
            spread = 0f;
            xRand = 6f;

            maxLevel = 30;
            expFields = new EField[]{
                    new ELinearCap(v -> shots = (int)v, 2, 0.35f, 15, mindustry.world.meta.Stat.shots),
                    new ELinearCap(v -> inaccuracy = v, 1f, 0.25f, 10, mindustry.world.meta.Stat.inaccuracy, v -> arc.util.Strings.autoFixed(v, 1) + " degrees"),
                    new ELinear(v -> burstSpacing = v, 20f, -0.5f, null),
                    new ELinear(v -> range = v, 150f, 2f, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " " + mindustry.world.meta.StatUnit.blocks.localized())
            };
            pregrade = chargeLaserTurret;
            pregradeLevel = 15;
            effectColors = new Color[]{
                    zzw.content.exp.UnityPal.lancerSap3,
                    zzw.content.exp.UnityPal.lancerSap4,
                    zzw.content.exp.UnityPal.lancerSap5,
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

        btLaserTurret = new ExpPowerTurret("bt-laser-turret"){{
            requirements(Category.turret, ItemStack.with(Items.surgeAlloy, 80, Z_Items.steel, 120, Z_Items.dirium, 70));
            size = 4;
            health = 2400;

            reload = 90f;
            coolantMultiplier = 2f;
            range = 160f;

            shoot.firstShotDelay = 60f;  // 充能时间 (与 btLaserCharge 的 60f 对齐, 特效结束即开火)
            // ★ 开火蓄力特效 (原版 Lancer 同款做法):
            //   chargeEffect 挂在 shootType 上, Turret.shoot() 在 firstShotDelay > 0 时自动播放
            //   moveWhileCharging = false 让炮台在充能期间锁定朝向, 避免充能特效与炮管错位
            moveWhileCharging = false;
            chargeSound = mindustry.gen.Sounds.chargeLancer;
            recoil = 4f;
            targetAir = true;
            shake = 6f;
            powerUse = 15f;

            shootEffect = mindustry.content.Fx.lancerLaserShoot;
            smokeEffect = mindustry.content.Fx.none;
            shootSound = V7Sounds.laser;

            heatColor = mindustry.graphics.Pal.redderDust;
            toColor = UnityPal.exp;

            // ★ 用户调整: 伤害 150 → 560 (注意 ExpLaserBulletType 参数为 (length, damage),
            //   长度保持原设定的 240f 不变, 仅提升第二参数 damage)
            shootType = new ExpLaserBulletType(240f, 560f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, UnityPal.exp};
                // ★ 充能特效: 自定义略放大版 Lancer 光环, 颜色与激光一致 (Pal.lancerLaser)
                chargeEffect = zzw.content.units.effects.ChargeFx.btLaserCharge;
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
                new ELinear(v -> range = v, 160f, 1f, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " " + mindustry.world.meta.StatUnit.blocks.localized())
            };
            effectColors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser, UnityPal.exp};
        }};

        infernoTurret = new ExpItemTurret("inferno"){{
            // ★完整移植 PU_V8: 3 种弹药 (scrap/slagShot, coal/coalBlaze, pyratite/pyraBlaze)
            // shootSmallBlaze/shootPyraBlaze: 火焰色粒子向射击方向喷射 (PU_V8 自定义 ShootFx)
            // coal/pyratite 使用 ExpBulletType (命中给炮台加经验), 与 PU 原版 UnityBullets.coalBlaze/pyraBlaze 一致
            ammo(
                mindustry.content.Items.scrap, new SlagFanBulletType(mindustry.content.Liquids.slag) {{
                    // ★ PU_V8 Bullets.slagShot 等效 (来自 PU特供v132版): damage=4.0f, drag=0.01f
                    damage = 4.0f;
                    drag = 0.01f;
                    // ★ 用户需求: 废料弹一次发射 3 发扇形分叉 (左右各偏 12°)
                    fanSpread = 12f;
                }},
                mindustry.content.Items.coal, new ExpBulletType(3.35f, 32f) {{
                    ammoMultiplier = 3;
                    hitSize = 7f;
                    lifetime = 24f;
                    pierce = true;
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
                    // ★ PU_V8 coalBlaze: 命中 50% 概率给炮台 1 点经验
                    expOnHit = true;
                    expChance = 0.5f;
                }},
                mindustry.content.Items.pyratite, new ExpBulletType(3.35f, 46f) {{
                    ammoMultiplier = 3;
                    hitSize = 7f;
                    lifetime = 24f;
                    pierce = true;
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
                    // ★ PU_V8 pyraBlaze: 命中 60% 概率给炮台 1 点经验
                    expOnHit = true;
                    expChance = 0.6f;
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
    }

    /**
     * 废料专属分叉液弹 (PU 原版 inferno 废料弹视觉)。
     * <p>
     * 原版 inferno 使用废料作弹药时, 一次会喷出 3 发呈扇形分叉的渣液弹;
     * v158 的 {@link mindustry.entities.bullet.LiquidBulletType} 每次只发射 1 发,
     * 因此这里在子弹初始化时额外朝左右各偏 {@link #fanSpread} 度补射两发副弹。
     * <p>
     * {@link #spawning} 为重入标记: 生成副弹时置为 true, 让副弹的 init() 直接返回,
     * 避免副弹再次分叉造成无限递归。
     */
    static class SlagFanBulletType extends mindustry.entities.bullet.LiquidBulletType {
        /** 扇形半角 (度): 主弹左右各偏该角度生成一发副弹 */
        public float fanSpread = 12f;
        /** 重入标记: 生成副弹期间为 true, 阻止副弹继续分叉 */
        boolean spawning = false;

        public SlagFanBulletType(mindustry.type.Liquid liquid) {
            super(liquid);
        }

        @Override
        public void init(mindustry.gen.Bullet b) {
            super.init(b);
            // 副弹不再分叉
            if (spawning) return;

            spawning = true;
            // Mathf.signs = {-1, 1}: 左右各生成一发副弹
            for (int s : arc.math.Mathf.signs) {
                create(b, b.x, b.y, b.rotation() + fanSpread * s);
            }
            spawning = false;
        }
    }
}
