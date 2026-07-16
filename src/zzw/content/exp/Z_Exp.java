package zzw.content.exp;

import mindustry.content.Items;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;
import zzw.content.Z_Items;

import static mindustry.Vars.tilesize;
import static zzw.content.exp.EField.*;

/**
 * PU_V8 经验系统方块注册
 * 参考: PU_V8 main/src/unity/content/UnityBlocks.java L1735-2062
 */
public class Z_Exp {
    // 经验存储运输
    public static Block expTank, expChest, expRouter, expTower, expTowerDiagonal, bufferTower,
            expHub, expNode, expNodeLarge, expFountain, expVoid;

    // 经验炮台 (电力)
    public static ExpPowerTurret laserTurret, chargeLaserTurret, fractalLaserTurret, btLaserTurret;
    // 经验炮台 (物品)
    public static ExpItemTurret infernoTurret;
    // 经验炮台 (液体)
    public static ExpLiquidTurret frostLaserTurret;

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
            shootType = new LaserBulletType(20f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, arc.graphics.Color.white};
                hitEffect = mindustry.content.Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                collidesAir = false;
                length = 140f;
                ammoMultiplier = 1f;
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

            shootType = new LaserBulletType(35f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, arc.graphics.Color.white};
                hitEffect = mindustry.content.Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                length = 140f;
                ammoMultiplier = 1f;
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
            ammo(mindustry.content.Liquids.cryofluid, new LaserBulletType(20f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, arc.graphics.Color.white};
                hitEffect = mindustry.content.Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                length = 160f;
                ammoMultiplier = 1f;
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

            shootType = new LaserBulletType(90f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, arc.graphics.Color.white};
                hitEffect = mindustry.content.Fx.hitLaserBlast;
                hitSize = 6;
                lifetime = 20f;
                drawSize = 400f;
                length = 160f;
                ammoMultiplier = 1f;
                pierceCap = 4;
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
            size = 3;
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

            shootType = new LaserBulletType(150f){{
                colors = new arc.graphics.Color[]{mindustry.graphics.Pal.lancerLaser.cpy().a(0.4f), mindustry.graphics.Pal.lancerLaser, UnityPal.exp};
                hitEffect = mindustry.content.Fx.hitLaserBlast;
                hitSize = 8;
                lifetime = 22f;
                drawSize = 500f;
                length = 180f;
                ammoMultiplier = 1f;
                pierceCap = 6;
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
            ammo(mindustry.content.Items.coal, new mindustry.entities.bullet.BulletType(3.35f, 17f){{
                ammoMultiplier = 3f;
                hitSize = 7f;
                lifetime = 18f;
                pierce = true;
                collidesAir = false;
                statusDuration = 60f * 4;
                shootEffect = mindustry.content.Fx.shootSmallFlame;
                hitEffect = mindustry.content.Fx.hitFlameSmall;
                despawnEffect = mindustry.content.Fx.none;
                status = mindustry.content.StatusEffects.burning;
                hittable = false;
            }});
            requirements(Category.turret, ItemStack.with(Items.copper, 60, Items.graphite, 50));
            size = 2;
            health = 1200;

            reload = 20f;
            range = 100f;
            targetAir = false;
            targetGround = true;
            inaccuracy = 10f;

            shootSound = V7Sounds.laser;
            heatColor = mindustry.graphics.Pal.redderDust;

            maxLevel = 20;
            expFields = new EField[]{
                new LinearReloadTime(v -> reload = v, 20f, -0.5f),
                new ELinear(v -> range = v, 100f, 1f, mindustry.world.meta.Stat.shootRange, v -> arc.util.Strings.autoFixed(v / tilesize, 2) + " blocks")
            };
        }};
        //endregion
    }
}
