package zzw.content.blocks;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.FlakBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.LaserTurret;
import zzw.content.Z_Items;

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
            shootSound = Sounds.shoot;
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
            shootSound = Sounds.shootLancer;
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
            requirements(Category.turret, ItemStack.with(Items.lead, 110, Items.silicon, 75, Z_Items.luminum, 165, Items.titanium, 135));
            size = 3;
            health = 2540;
            reload = 60f;
            coolantMultiplier = 2f;
            range = 160f;
            consumePower(6.6f);
            heatColor = Pal.turretHeat;
            shootSound = Sounds.shootLancer;
            shootType = new LaserBulletType(20f) {{
                colors = new Color[]{Pal.lancerLaser.cpy().a(0.4f), Pal.lancerLaser, Color.white};
                hitEffect = Fx.hitLancer;
                hitSize = 4;
                lifetime = 16f;
                drawSize = 400f;
                length = 160f;
                ammoMultiplier = 1f;
            }};
        }};

        // ===== graviton (重力子激光炮, PU132 L611-631) =====
        // PU132: LaserTurret + 更强激光
        graviton = new LaserTurret("graviton") {{
            requirements(Category.turret, ItemStack.with(Items.silicon, 300, Z_Items.luminum, 430, Items.titanium, 190, Items.thorium, 110, Z_Items.lightAlloy, 15));
            size = 4;
            health = 5000;
            reload = 90f;
            coolantMultiplier = 3f;
            range = 200f;
            consumePower(1.9f);
            heatColor = Pal.turretHeat;
            shootSound = Sounds.shootLancer;
            shootType = new LaserBulletType(35f) {{
                colors = new Color[]{Pal.lancerLaser.cpy().a(0.4f), Pal.lancerLaser, Color.white};
                hitEffect = Fx.hitLancer;
                hitSize = 6;
                lifetime = 20f;
                drawSize = 500f;
                length = 200f;
                ammoMultiplier = 1f;
            }};
        }};
    }
}
