package zzw.content.units;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.gen.Sounds;
import mindustry.graphics.Drawf;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import zzw.content.Z_Sounds;
import zzw.content.graphics.UnityPal;
import zzw.content.units.abilities.TentacleAbility;
import zzw.content.units.bullets.ContinuousSingularityLaserBulletType;
import zzw.content.units.bullets.EndBasicBulletType;
import zzw.content.units.bullets.EndContinuousLaserBulletType;
import zzw.content.units.bullets.EndCutterLaserBulletType;
import zzw.content.units.bullets.VoidFractureBulletType;
import zzw.content.units.effects.HitEffect;
import zzw.content.units.effects.ShootEffect;
import zzw.content.units.effects.UnityDrawf;
import zzw.content.units.entities.ApocalypseUnit;
import zzw.content.units.entities.ThalassophobiaUnit;
import zzw.content.units.types.ApocalypseUnitType;
import zzw.content.units.types.ThalassophobiaUnitType;
import zzw.content.units.type.decal.FlagellaDecorationType;
import zzw.content.units.weapons.EnergyChargeWeapon;

/**
 * End 最终单位注册类 (PU132 UnityUnitTypes end region 最后两个单位的完整移植)
 *
 * <p>End 阵营共 10 个单位, 前 8 个 (enigma/voidVessel/chronos/opticaecus/
 * devourer/oppression/ravager/desolation) 已在 Z_Units.java 移植,
 * 本类收尾最后两个最终 BOSS:</p>
 *
 * <ul>
 *   <li><b>apocalypse 天启</b>: 飞行隐身 BOSS (hitSize 205, 1725000 血),
 *       8 小炮台 + 3 持续激光 + 4 导弹巢 + quetzalcoatl 巨型切割激光主炮
 *       + 4 条独立物理触手 (2 激光 + 2 接触伤害), 血量过半且未交战时整体隐身。</li>
 *   <li><b>thalassophobia 深海恐惧</b>: 超大型海洋 BOSS (hitSize 242.5, 2750000 血),
 *       6 虚空碎裂炮 + 6 导弹巢 + 奇异点连续激光主炮 (自带引力场),
 *       身后 15 节鞭毛尾巴 (FlagellaDecoration 摆动动画),
 *       自定义软阴影。</li>
 * </ul>
 *
 * <p>★ v158 适配 (对照 PU132 原版时的 API 差异):</p>
 * <ul>
 *   <li>InvisibleUnitType → ApocalypseUnitType (本模组自建隐身绘制类型)</li>
 *   <li>Weapon shots/shotDelay → shoot.shots/shoot.shotDelay (v152+ ShootPattern 体系)</li>
 *   <li>Tentaclec/TentacleType → TentacleAbility (能力驱动, 挂在 UnitType.abilities 上)</li>
 *   <li>WaterMovec/Decorationc → DecorationUnitEntity (装饰系统, WaterMovec 仅为标记接口)</li>
 *   <li>antiCheatType = AntiCheatVariables(...) → 实体内置防作弊 (参数记录在注释,
 *       阈值与 PU132 EndComp 默认一致)</li>
 *   <li>immuneAll → immunities.addAll(所有状态效果)</li>
 * </ul>
 *
 * @author PU132 原作, 移植: zzw
 */
public class Z_EndUnits {

    public static UnitType apocalypse, thalassophobia;

    /** endLaserSmall 弹配置 (PU132 UnityBullets.endLaserSmall L1277-1291, 触手共用) */
    private static EndContinuousLaserBulletType endLaserSmall(){
        return new EndContinuousLaserBulletType(85f) {{
            lifetime = 2f * 60f;
            length = 230f;
            // PU132: strokes 每项 × 0.4 (细激光)
            strokes = new float[]{0.8f, 0.6f, 0.4f, 0.12f};
            overDamage = 800000f;
            ratioDamage = 1f / 40f;
            ratioStart = 1000000f;
            colors = new Color[]{UnityPal.scarColorAlpha, UnityPal.scarColor, UnityPal.endColor, Color.white};
            modules = new zzw.content.units.anticheat.AntiCheatBulletModule[]{
                new zzw.content.units.anticheat.ArmorDamageModule(0.1f, 30f, 30f, 0.4f)
            };
            hitEffect = HitEffect.endHitRedSmall;
        }};
    }

    public static void load(){
        // ★ 自定义实体注册 (v155.4+ 要求, 取唯一 classId)
        ZEntityRegister.register(ApocalypseUnit.class, ApocalypseUnit::create);
        ZEntityRegister.register(ThalassophobiaUnit.class, ThalassophobiaUnit::create);

        apocalypse();
        thalassophobia();
    }

    //region apocalypse 天启

    /**
     * apocalypse (PU132 UnityUnitTypes.java L4295-4464)。
     *
     * <p>防作弊参数 (PU132 antiCheatType = AntiCheatVariables):
     * damageThreshold=health/600, maxDamageThreshold=health/200,
     * resistThreshold=health/600, maxResistThreshold=health/100,
     * curvePower=0.6, resistDuration=7×60, resistTime=8×60,
     * invincibilityDuration=35, invincibilityArray=4。
     * 实体侧 ApocalypseUnit 使用独立机制 (30 tick 免伤 + 5 槽优先级无敌帧 + 免疫累积)。</p>
     */
    private static void apocalypse(){
        apocalypse = new ApocalypseUnitType("apocalypse"){{
            health = 1725000f;
            speed = 0.75f;
            accel = 0.06f;
            drag = 0.06f;
            armor = 17f;
            hitSize = 205f;
            rotateSpeed = 0.3f;
            // v158.1 已移除 visualElevation (PU132: 3f)
            engineOffset = 116.5f;
            engineSize = 14f;
            flying = true;
            lowAltitude = true;
            outlineColor = UnityPal.darkerOutline;

            // PU132: antiCheatType = (h/600, h/200, h/600, h/100, 0.6, 7m, 8m, 35, 4)
            antiCheatType = new zzw.content.units.anticheat.EndCheatVars(
                health / 600f, health / 200f, health / 600f, health / 100f, 0.6f, 7f * 60f, 8f * 60f, 35f, 4);

            // PU132: rotateShooting = false (v158 无此字段, 默认行为一致)
            constructor = ApocalypseUnit::create;

            // PU132: immuneAll = true → 免疫所有状态效果
            for(StatusEffect st : mindustry.Vars.content.statusEffects()){
                immunities.add(st);
            }

            // ===== 武器模板 1: 小炮台 ×8 (PU132 UnityWeaponTemplates.apocalypseSmall) =====
            // 贴图复用 ravager-small-turret, 8 个位置 stagger 装填 (reload += 0/2/4/.../12)
            //region apocalypse weapons

            Weapon small = new Weapon("create-ravager-small-turret"){{
                reload = 2f * 60f;
                shootY = 6.5f;
                // PU132: shots=3, spacing=15 (v155.4 无 spacing 字段, 平行弹幕以默认扇形近似)
                shoot.shots = 3;
                // spacing = 15f;  // v155.4: spacing 移除
                shootCone = 10f;
                shadow = 15f;
                mirror = true;
                alternate = true;
                rotate = true;

                bullet = new EndBasicBulletType(6f, 140f){{
                    lifetime = 70f;
                    width = 15f;
                    height = 19f;
                    shrinkY = 0f;
                    backColor = hitColor = lightColor = UnityPal.scarColor;
                    frontColor = UnityPal.endColor;
                    ratioStart = 90000f;
                    ratioDamage = 1f / 200f;
                    overDamage = 900000f;
                }};
            }};

            Weapon laser = new Weapon("create-ravager-artillery"){{
                reload = 5f * 60f;
                rotateSpeed = 2f;
                shootY = 6.5f;
                shootCone = 10f;
                shadow = 24f;
                shootSound = Z_Sounds.continuousLaserB;
                continuous = true;
                rotate = true;
                mirror = true;
                alternate = false;

                bullet = endLaserSmall();
            }};

            Weapon launcher = new Weapon("create-doeg-launcher"){{
                reload = 3.5f * 60f;
                rotateSpeed = 6f;
                shootY = 6.5f;
                // PU132: shots=12, shotDelay=6
                shoot.shots = 12;
                shoot.shotDelay = 6f;
                inaccuracy = 20f;
                shootCone = 10f;
                shadow = 24f;
                mirror = true;
                alternate = true;
                rotate = true;

                bullet = new EndBasicBulletType(6f, 220f, "missile"){{
                    lifetime = 80f;
                    width = 15f;
                    height = 17f;
                    shrinkY = 0f;
                    drag = -0.013f;
                    splashDamageRadius = 40f;
                    splashDamage = 210f;
                    backColor = trailColor = hitColor = lightColor = UnityPal.scarColor;
                    frontColor = UnityPal.endColor;
                    trailChance = 0.2f;
                    homingPower = 0.08f;
                    weaveScale = 6f;
                    weaveMag = 1.2f;
                    hitEffect = Fx.blastExplosion;
                    despawnEffect = Fx.blastExplosion;

                    ratioStart = 56000f;
                    ratioDamage = 1f / 150f;
                    overDamage = 850000f;
                }};
            }};

            // PU132 CloneableSetWeapon.set(...) 逐个展开 (x 全为正值, mirror=true 自动补左侧)
            // 小炮台 ×8 (reload stagger 0/2/4/6/8/0/10/12)
            weapons.addAll(Seq.with(
                copy(small, w -> { w.x = 74.75f; w.y = 47.25f; w.reload += 0; }),
                copy(small, w -> { w.x = 80f; w.y = 24.75f; w.reload += 2; }),
                copy(small, w -> { w.x = 108.5f; w.y = -76.5f; w.reload += 4; }),
                copy(small, w -> { w.x = 70.25f; w.y = -31.25f; w.reload += 6; }),
                copy(small, w -> { w.x = 56f; w.y = -74.5f; w.reload += 8; }),
                copy(small, w -> { w.x = 51f; w.y = 0f; w.reload += 0; }),
                copy(small, w -> { w.x = 65.5f; w.y = -80.25f; w.reload += 10; }),
                copy(small, w -> { w.x = 36.25f; w.y = -63.5f; w.reload += 12; }),
                // 持续激光 ×3 (reload stagger 0/2/4)
                copy(laser, w -> { w.x = 44.25f; w.y = 37.25f; w.reload += 0; }),
                copy(laser, w -> { w.x = 51f; w.y = -22.75f; w.reload += 2; }),
                copy(laser, w -> { w.x = 62.25f; w.y = -51.75f; w.reload += 4; }),
                // 导弹巢 ×4 (reload stagger 0/2/4/6)
                copy(launcher, w -> { w.x = 87f; w.y = 0f; w.reload += 0; }),
                copy(launcher, w -> { w.x = 97f; w.y = -23.25f; w.reload += 2; }),
                copy(launcher, w -> { w.x = 97.5f; w.y = -50.5f; w.reload += 4; }),
                copy(launcher, w -> { w.x = 87f; w.y = -74.75f; w.reload += 6; })
            ));

            // ===== 主炮: quetzalcoatl 切割激光 (PU132 L4382-4412) =====
            weapons.add(new Weapon("create-quetzalcoatl"){{
                x = y = 0f;
                shootY = -8.25f;
                mirror = false;
                rotate = true;
                continuous = true;
                rotateSpeed = 0.2f;
                shadow = 63f;
                shootCone = 1f;
                reload = 6f * 60f;
                shootSound = Z_Sounds.continuousLaserB;
                bullet = new EndCutterLaserBulletType(3100f){{
                    maxLength = 1200f;
                    lifetime = 3f * 60f;
                    width = 17f;
                    antiCheatScl = 5f;
                    laserSpeed = 70f;
                    buildingDamageMultiplier = 0.4f;
                    lightningColor = UnityPal.scarColor;
                    lightningDamage = 55f;
                    lightningLength = 13;

                    bleedDuration = 5f * 60f;
                    overDamage = 500000f;
                    ratioDamage = 1f / 100f;
                    ratioStart = 7000f;

                    hitEffect = HitEffect.endHitRedBig;
                }};
            }});

            //endregion

            // ===== 触手 ×4 (PU132 tentacles.add, L4415-4512) =====
            // PU132 TentacleType 默认: mirror=true(自动补左侧), top=false, drag=0.06,
            //   swayScl=110, swayMag=0.6, angleLimit=65, firstSegmentAngleLimit=35
            // 触手 1/2: apocalypse-tentacle (持续激光, 15/10 节 × 37.25)
            // 触手 3/4: apocalypse-small-tentacle (接触伤害 430, 20/23 节 × 28, 无子弹)
            abilities.add(new TentacleAbility("create-apocalypse-tentacle"){{
                x = 101.75f;
                y = -72.5f;
                rotationOffset = 30f;

                rotationSpeed = 3f;
                accel = 0.2f;
                speed = 4f * 2f;
                // 模组默认 swayMag=0.08, PU132 TentacleType 默认 0.6
                swayMag = 0.6f;
                top = false;

                segments = 15;
                segmentLength = 37.25f;

                bullet = endLaserSmall();
                automatic = false;
                continuous = true;
                reload = 4f * 60f;
                // PU132 range() 公式: 节数×节长 - 5 + bullet.range×0.75
                range = 15 * 37.25f - 5f;
            }}, new TentacleAbility("create-apocalypse-tentacle"){{
                x = 56.5f;
                y = -71.75f;
                rotationOffset = 10f;

                rotationSpeed = 3f;
                accel = 0.2f;
                speed = 4f * 2f;
                swayMag = 0.6f;
                swayOffset = 90f;
                top = false;

                segments = 10;
                segmentLength = 37.25f;

                bullet = endLaserSmall();
                automatic = false;
                continuous = true;
                reload = 4f * 60f;
                range = 10 * 37.25f - 5f;
            }}, new TentacleAbility("create-apocalypse-small-tentacle"){{
                x = 104.25f;
                y = -49f;
                rotationOffset = 35f;

                rotationSpeed = 3f;
                accel = 0.15f;
                speed = 5f * 2f;
                swayOffset = 120f;
                swayMag = 0.2f;
                swayScl = 120f;
                top = false;

                segments = 20;
                segmentLength = 28f;

                bullet = null;
                automatic = true;
                tentacleDamage = 430f;
                range = 20 * 28f - 5f;
            }}, new TentacleAbility("create-apocalypse-small-tentacle"){{
                x = 69.75f;
                y = -74.25f;
                rotationOffset = 20f;

                rotationSpeed = 3f;
                accel = 0.15f;
                speed = 5f * 2f;
                swayOffset = 70f;
                swayMag = 0.2f;
                swayScl = 120f;
                top = false;

                segments = 23;
                segmentLength = 28f;

                bullet = null;
                automatic = true;
                tentacleDamage = 430f;
                range = 23 * 28f - 5f;
            }});
        }};
    }

    /** 克隆武器并应用位置/装填偏移 (PU132 CloneableSetWeapon.set + clnW 等价物) */
    private static Weapon copy(Weapon base, Cons<Weapon> cons){
        Weapon w = base.copy();
        cons.get(w);
        return w;
    }

    //endregion

    //region thalassophobia 深海恐惧

    /**
     * thalassophobia (PU132 UnityUnitTypes.java L4979-5268)。
     *
     * <p>防作弊参数 (PU132 antiCheatType = AntiCheatVariables):
     * damageThreshold=8000, maxDamageThreshold=16000,
     * resistThreshold=health/520, maxResistThreshold=health/120,
     * curvePower=0.6, resistDuration=7×60, resistTime=8×60,
     * invincibilityDuration=35, invincibilityArray=4 (实体侧记录)。</p>
     */
    private static void thalassophobia(){
        thalassophobia = new ThalassophobiaUnitType("thalassophobia"){{
            health = 2750000f;
            hitSize = 242.5f;
            speed = 1.9f;
            accel = 0.2f;
            drag = 0.16f;
            rotateSpeed = 0.3f;
            outlineColor = UnityPal.darkerOutline;

            // PU132: antiCheatType = (8000, 16000, h/520, h/120, 0.6, 7m, 8m, 35, 4)
            antiCheatType = new zzw.content.units.anticheat.EndCheatVars(
                8000f, 16000f, health / 520f, health / 120f, 0.6f, 7f * 60f, 8f * 60f, 35f, 4);

            // PU132: immuneAll = true → 免疫所有状态效果
            for(StatusEffect st : mindustry.Vars.content.statusEffects()){
                immunities.add(st);
            }

            // PU132: WaterMovec + Decorationc → 水中移动装饰实体
            // (实现原版 WaterMovec 接口, UnitType.init 自动设置 naval=true/omniMovement=false/免溺水)
            constructor = ThalassophobiaUnit::create;

            // ===== 鞭毛尾巴 (PU132 decorations.add FlagellaDecorationType) =====
            // 4 段贴图 × 15 节 × 45.75 节长, 挂载点 (0, -172) (身体正后方)
            // swayScl = hitSize / speed (PU132 原版写法, 242.5/1.9 ≈ 127.6, 单位越慢摆得越慢)
            decorations.add(new FlagellaDecorationType("create-thalassophobia-tail", 4, 15, 45.75f){{
                x = 0f;
                y = -172f;
                swayScl = hitSize / speed;
                swayOffset = 67f;
            }});

            //region thalassophobia weapons

            // ===== 武器模板: 虚空碎裂炮 ×6 (PU132 EnergyChargeWeapon "unity-void-fracture-turret") =====
            EnergyChargeWeapon fracture = new EnergyChargeWeapon("create-void-fracture-turret"){{
                mirror = false;
                alternate = true;
                shadow = 47f;
                // PU132: shots=3, shotDelay=6
                shoot.shots = 3;
                shoot.shotDelay = 6f;
                reload = 120f;
                inaccuracy = 20f;
                shootCone = 7f;
                shootY = 0f;
                rotate = true;
                rotateSpeed = 2f;
                velocityRnd = 0.1f;
                shootSound = Z_Sounds.spaceFracture;

                // 蓄力特效: 黑色闪光圆 + 尖刺 (PU132 drawCharge, UnityDrawf.shiningCircle)
                drawCharge = (unit, mount, charge) -> {
                    Weapon w = mount.weapon;
                    float rotation = unit.rotation - 90f,
                    wx = unit.x + Angles.trnsx(rotation, w.x, w.y),
                    wy = unit.y + Angles.trnsy(rotation, w.x, w.y);

                    Draw.color(Color.black);
                    UnityDrawf.shiningCircle(unit.id * 321 + Math.max(0, w.otherSide * 41), Time.time, wx, wy, 3.5f * charge, 6, 60f, 17f, 3f * charge, 70f);
                    Draw.color();
                };

                bullet = new VoidFractureBulletType(40f, 800f){{
                    speed = 5f;
                    delay = 50f;
                    lifetime = 60f;
                    drag = 0.09f;
                    nextLifetime = 13f;
                    ratioDamage = 1f / 170f;
                    ratioStart = 30000f;
                    bleedDuration = 40f;
                    length = 52f;
                    width = 20f;
                    widthTo = 8f;
                    spikesRand = 16f;
                    spikesDamage = 310f;
                    targetingRange = 400f;
                    maxTargets = 20;
                    shootEffect = ShootEffect.voidShoot;

                    hitEffect = HitEffect.voidHitBig;
                    smokeEffect = HitEffect.voidHit;

                    modules = new zzw.content.units.anticheat.AntiCheatBulletModule[]{
                        new zzw.content.units.anticheat.ArmorDamageModule(50f, 50f, 2f),
                        new zzw.content.units.anticheat.ForceFieldDamageModule(2f, 20f, 220f, 7f, 1f / 50f, 2f * 60f)
                    };
                }};
            }};

            // ===== 武器模板: 导弹发射器 ×6 (PU132 Weapon "unity-end-missile-launcher") =====
            Weapon missile = new Weapon("create-end-missile-launcher"){{
                shootY = 7.25f;
                reload = 50f;
                alternate = true;
                mirror = false;
                // PU132: shots=5, shotDelay=3
                shoot.shots = 5;
                shoot.shotDelay = 3f;
                xRand = 5.75f;
                rotate = true;
                rotateSpeed = 4f;
                inaccuracy = 7f;
                shootSound = Sounds.shootMissile;  // v155.4: missile → shootMissile

                bullet = new EndBasicBulletType(4f, 210f, "missile"){{
                    lifetime = 75f;
                    width = height = 12f;
                    shrinkY = 0f;
                    drag = -0.01f;
                    splashDamageRadius = 45f;
                    splashDamage = 220f;
                    homingPower = 0.08f;
                    homingRange = 100f;
                    trailChance = 0.3f;
                    weaveScale = 6f;
                    weaveMag = 1f;

                    overDamage = 950000f;
                    ratioDamage = 1f / 400f;
                    ratioStart = 2000f;

                    hitEffect = HitEffect.endHitRedSmall;
                    despawnEffect = HitEffect.endHitRedSmall;

                    backColor = lightColor = trailColor = UnityPal.scarColor;
                    frontColor = UnityPal.endColor;
                }};
            }};

            // PU132 clnW(w, ...) 展开: 右侧 3 座碎裂炮 + 左侧 3 座 (otherSide 配对给蓄力特效种子)
            weapons.addAll(Seq.with(
                copy(fracture, w -> { w.x = 79.5f; w.y = -34f; w.otherSide = 1; }),
                copy(fracture, w -> { w.x = 90.5f; w.y = -71.5f; w.otherSide = 2; w.flipSprite = true; }),
                copy(fracture, w -> { w.x = 91.25f; w.y = -104f; w.otherSide = 0; w.flipSprite = true; }),

                copy(fracture, w -> { w.x = -79.5f; w.y = -34f; w.otherSide = 4; }),
                copy(fracture, w -> { w.x = -90.5f; w.y = -71.5f; w.otherSide = 5; w.flipSprite = true; }),
                copy(fracture, w -> { w.x = -91.25f; w.y = -104f; w.otherSide = 3; w.flipSprite = true; }),

                // 导弹发射器 ×6 (前 3 + 后 3, otherSide 配对给 alternate 换边)
                copy(missile, w -> { w.x = 73.5f; w.y = 69.5f; w.otherSide = 7; }),
                copy(missile, w -> { w.x = 84f; w.y = 40f; w.otherSide = 8; w.flipSprite = true; }),
                copy(missile, w -> { w.x = 72.5f; w.y = 6.25f; w.otherSide = 6; w.flipSprite = true; }),

                copy(missile, w -> { w.x = -73.5f; w.y = 69.5f; w.otherSide = 10; }),
                copy(missile, w -> { w.x = -84f; w.y = 40f; w.otherSide = 11; w.flipSprite = true; }),
                copy(missile, w -> { w.x = -72.5f; w.y = 6.25f; w.otherSide = 9; w.flipSprite = true; })
            ));

            // ===== 主炮: 奇异点连续激光 (PU132 匿名 EnergyChargeWeapon "", L5136-5253) =====
            // 无炮塔贴图 (name=""), 蓄力时四色三角 + 闪光圆 + 4 段炮管贴图依次飞入
            weapons.add(new EnergyChargeWeapon(""){
                TextureRegion r;
                TextureRegion[] rs;
                final Color[] colors = {UnityPal.scarColor, UnityPal.endColor, Color.white, Color.black};

                {
                    mirror = false;
                    x = 0f;
                    y = 41.25f;
                    reload = 15f * 60f;
                    continuous = true;
                    shootSound = Z_Sounds.thalassophobiaLaser;
                    drawRegion = false;

                    bullet = new ContinuousSingularityLaserBulletType(3500f){{
                        lifetime = 5f * 60f;
                        width = 40f;
                        widthReduction = 6f;
                        collisionWidth = 17f;
                        accel = 15f;
                        laserSpeed = 45f;
                        pierceAmount = 20f;
                        gravityStrength = 20f * 80f;
                        baseTriangleSize = 210f;
                        oscScl = 5f;
                        buildingDamageMultiplier = 0.7f;

                        overDamage = 600000f;
                        overDamagePower = 3f;
                        overDamageScl = 7000f;
                        ratioDamage = 1f / 200f;
                        ratioStart = 20000f;
                        bleedDuration = 600f;

                        hitEffect = HitEffect.endHitRedBig;

                        modules = new zzw.content.units.anticheat.AntiCheatBulletModule[]{
                            new zzw.content.units.anticheat.ArmorDamageModule(0.01f, 3f, 40f, 5f),
                            new zzw.content.units.anticheat.AbilityDamageModule(50f, 300f, 20f, 0.002f, 3f)
                        };
                    }};

                    // 蓄力特效 (PU132 drawCharge L5197-5243):
                    // 4 色 (红/浅红/白/黑) 三角 + 闪光圆叠加, 再画 4 段炮管贴图从远处飞入炮口
                    drawCharge = (unit, mount, charge) -> {
                        float len = 32.25f, rr = unit.rotation;
                        float wx = Angles.trnsx(rr, y) + unit.x,
                        wy = Angles.trnsy(rr, y) + unit.y;

                        float tx = Angles.trnsx(rr, len),
                        ty = Angles.trnsy(rr, len);

                        for(int i = 0; i < colors.length; i++){
                            float w = 1f - ((6f * i) / 50f),
                            rx = Mathf.range(charge),
                            ry = Mathf.range(charge),
                            s = Mathf.absin(40f, 5f);

                            Draw.color(colors[i]);
                            Drawf.tri(wx + rx, wy + ry, (35f + s) * w * charge * 2f * 1.22f, charge * charge * w * (160f + Mathf.absin(Time.time + i * 8f, 15f, 12f)), unit.rotation);
                            UnityDrawf.shiningCircle(unit.id, Time.time, wx + rx, wy + ry, (35f + s) * w * charge, 5, 90f, 0.5f, 25f, 15f * charge, 45f);
                        }

                        Draw.color(UnityPal.scarColor);
                        Draw.blend(Blending.additive);

                        Draw.alpha(charge);
                        Draw.rect(r, wx + tx + Mathf.range(charge * 3f), wy + ty + Mathf.range(charge * 3f), rr - 90f);

                        for(int i = 0; i < rs.length; i++){
                            float f = i / (float)rs.length,
                            t = (i + 1f) / rs.length;
                            float a = Mathf.curve(charge, f, t), inv = Interp.pow2In.apply(1f - a) * 3f;
                            Draw.alpha(Mathf.clamp(a * 2f));
                            Draw.rect(rs[i], wx + (tx * (1f + inv)) + Mathf.range(a * 2f),
                            wy + (ty * (1f + inv)) + Mathf.range(a * 2f),
                            rr - 90f);
                        }

                        Draw.blend();
                        Draw.reset();
                    };
                }

                @Override
                public void load(){
                    super.load();
                    r = Core.atlas.find("create-thalassophobia-cannon");
                    rs = new TextureRegion[4];
                    for(int i = 0; i < 4; i++){
                        rs[i] = Core.atlas.find("create-thalassophobia-cannon-" + i);
                    }
                }
            });

            //endregion
        }};
    }

    //endregion
}
