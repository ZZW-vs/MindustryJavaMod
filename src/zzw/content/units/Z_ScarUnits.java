package zzw.content.units;

import arc.graphics.Color;
import arc.struct.ObjectSet;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.bullet.RailBulletType;
import mindustry.entities.pattern.ShootPattern;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import zzw.content.units.weapons.BlankWeapon;
import zzw.content.Z_Bullets;
import zzw.content.graphics.UnityPal;
import zzw.content.type.UnityUnitType;
import zzw.content.units.abilities.DirectionShieldAbility;
import zzw.content.units.ai.DistanceGroundAI;
import zzw.content.units.bullets.SaberContinuousLaserBulletType;
import zzw.content.units.effects.ScarFx;

/**
 * Scar 系列单位注册类 (PU132 unity.content.UnityUnitTypes scar region 完整移植)
 *
 * <p>6 个腿部单位 (LegsUnit 实体) + 3 个飞行单位 (UnitEntity 实体),
 * 数值/武器/能力逐字段对照 PU132 原版 (UnityUnitTypes.java scar region)。</p>
 *
 * <p>包含单位：</p>
 * <ul>
 *   <li>hovos (T1) - 基础腿部单位, 磁轨炮</li>
 *   <li>ryzer (T2) - 进阶腿部单位, 磁轨炮 + 导弹发射器</li>
 *   <li>zena (T3) - 高级腿部单位, 双磁轨炮 + 导弹发射器</li>
 *   <li>sundown (T4) - 精英腿部单位, 大型导弹 + 磁轨炮 + 方向护盾</li>
 *   <li>rex (T5) - 重型悬浮腿部单位, 重型磁轨炮 + 破片弹 + 双导弹 + 方向护盾</li>
 *   <li>excelsus (T6) - 顶级悬浮腿部单位, 双导弹 + 双连续激光 + 方向护盾</li>
 *   <li>whirlwind (T1) - 基础飞行单位, saber 激光 + 导弹</li>
 *   <li>jetstream (T2) - 进阶飞行单位, swipe saber 激光 + 导弹</li>
 *   <li>vortex (T3) - 高级飞行单位, swipe saber 激光</li>
 * </ul>
 *
 * <p>★ v158 适配 (对齐 PU132 时遇到的所有 API 差异):</p>
 * <ul>
 *   <li>defaultController → aiController (v7+ 字段改名, 语义相同)</li>
 *   <li>Weapon 的 shots/shotDelay → shoot = ShootPattern 体系 (v152+ 重构)</li>
 *   <li>RailBulletType 的 updateEffect/updateEffectSeg → pointEffect/pointEffectSpace
 *       (v7+ 磁轨炮改为瞬时射线机制, 分段特效由原生 pointEffect 承担,
 *       命中特效 pierceEffect 字段名保持一致)</li>
 *   <li>v6 字段 legTrns / visualElevation / kinematicScl 在 v158.1 中已移除,
 *       相关赋值以注释保留原值</li>
 *   <li>Sounds.missile / Sounds.artillery → Sounds.shootMissile / shootArtillery</li>
 * </ul>
 *
 * @author PU132 原作, 移植: zzw
 */
public class Z_ScarUnits {

    // 腿部单位 (6个)
    public static UnitType hovos, ryzer, zena, sundown, rex, excelsus;
    // 飞行单位 (3个)
    public static UnitType whirlwind, jetstream, vortex;

    /** 射击疲劳状态 (PU132 UnityStatusEffects.reloadFatigue): 连续射击时装填速度降至 75% */
    public static StatusEffect reloadFatigue;

    /**
     * 加载入口 (TestMod.loadContent 调用)。
     *
     * <p>先注册状态效果, 再按 T1→T6 顺序创建 9 个单位
     * (UnitType 构造时自动注册到内容系统)。</p>
     */
    public static void load(){
        // PU132 UnityStatusEffects.reloadFatigue: saber 持续激光武器射击时施加,
        // 降低装填速度模拟"过热", 配合 Weapon.shootStatus 使用
        reloadFatigue = new StatusEffect("reload-fatigue"){{
            reloadMultiplier = 0.75f;
        }};

        //region scar 腿部单位

        // Hovos (T1) - 基础腿部单位, 单管磁轨炮
        hovos = new UnityUnitType("hovos"){{
            aiController = DistanceGroundAI::new;
            speed = 0.8f;
            health = 340;
            hitSize = 7.75f * 1.7f;
            range = 350f;
            allowLegStep = true;
            legMoveSpace = 0.7f;
            // legTrns = 0.4f; // v158.1 已移除该字段
            legLength = 30f;
            legExtension = -4.3f;

            weapons.add(new Weapon("create-small-scar-railgun"){{
                reload = 60f * 2;
                x = 0f;
                y = -2f;
                shootY = 9f;
                mirror = false;
                rotate = true;
                shake = 2.3f;
                rotateSpeed = 2f;

                bullet = new RailBulletType(){{
                    damage = 500f;
                    length = 59f * 6f;
                    // PU132: updateEffectSeg = 59f → v158.1 分段特效字段为 pointEffectSpace
                    pointEffectSpace = 59f;
                    // PU132: updateEffect = UnityFx.scarRailTrail → pointEffect
                    pointEffect = ScarFx.scarRailTrail;
                    pierceEffect = ScarFx.scarRailHit;
                    hitEffect = Fx.massiveExplosion;
                    pierceDamageFactor = 0.3f;
                }};
            }});

            constructor = mindustry.gen.LegsUnit::create;
        }};

        // Ryzer (T2) - 进阶腿部单位, 磁轨炮 + 导弹发射器
        ryzer = new UnityUnitType("ryzer"){{
            aiController = DistanceGroundAI::new;
            speed = 0.7f;
            health = 640;
            hitSize = 9.5f * 1.7f;
            range = 350f;
            allowLegStep = true;
            legMoveSpace = 0.73f;
            legCount = 6;
            // legTrns = 0.4f; // v158.1 已移除该字段
            legLength = 32f;
            legExtension = -4.3f;

            weapons.add(new BlankWeapon() {{
                reload = 2.5f * 60f;
                x = 0f;
                y = 7.5f;
                shootY = 2f;
                mirror = false;
                shake = 2.3f;

                bullet = new RailBulletType(){{
                    damage = 700f;
                    length = 59f * 7f;
                    pointEffectSpace = 59f;
                    pointEffect = ScarFx.scarRailTrail;
                    pierceEffect = ScarFx.scarRailHit;
                    hitEffect = Fx.massiveExplosion;
                    pierceDamageFactor = 0.3f;
                }};
            }}, new Weapon("create-scar-missile-launcher"){{
                reload = 50f;
                x = 6.25f;
                // PU132: shots = 5, shotDelay = 3f → ShootPattern 体系
                shoot = new ShootPattern(){{ shots = 5; shotDelay = 3f; }};
                inaccuracy = 4f;
                rotate = true;

                bullet = new MissileBulletType(5f, 1f){{
                    speed = 5f;
                    width = 7f;
                    height = 12f;
                    shrinkY = 0f;
                    backColor = trailColor = UnityPal.scarColor;
                    frontColor = UnityPal.endColor;
                    splashDamage = 25f;
                    splashDamageRadius = 20f;
                    weaveMag = 3f;
                    weaveScale = 4f;
                }};
            }});

            constructor = mindustry.gen.LegsUnit::create;
        }};

        // Zena (T3) - 高级腿部单位, 中央重磁轨 + 侧磁轨 + 导弹发射器
        zena = new UnityUnitType("zena"){{
            aiController = DistanceGroundAI::new;
            speed = 0.7f;
            health = 1220;
            hitSize = 17.85f;
            range = 350f;
            allowLegStep = true;
            legMoveSpace = 0.73f;
            legCount = 6;
            // legTrns = 0.4f; // v158.1 已移除该字段
            legLength = 40f;
            legExtension = -9.3f;

            weapons.add(
                new BlankWeapon() {{
                    x = 0f;
                    y = 12f;
                    shootY = 0f;
                    mirror = false;
                    rotate = false;
                    shake = 2.3f;
                    reload = 2.75f * 60f;

                    bullet = new RailBulletType(){{
                        damage = 780f;
                        length = 60f * 7f;
                        pointEffectSpace = 60f;
                        pointEffect = ScarFx.scarRailTrail;
                        pierceEffect = ScarFx.scarRailHit;
                        hitEffect = Fx.massiveExplosion;
                        pierceDamageFactor = 0.2f;
                    }};
                }}, new BlankWeapon() {{
                    x = 10.25f;
                    y = 2f;
                    rotate = false;
                    shake = 1.1f;
                    reload = 2.25f * 70f;

                    bullet = new RailBulletType(){{
                        damage = 230f;
                        length = 40f * 7f;
                        pointEffectSpace = 40f;
                        pointEffect = ScarFx.scarRailTrail;
                        pierceEffect = ScarFx.scarRailHit;
                        hitEffect = Fx.massiveExplosion;
                        pierceDamageFactor = 0.5f;
                    }};
                }}, new Weapon("create-scar-missile-launcher"){{
                    x = 12.25f;
                    y = -5f;
                    rotate = true;
                    shoot = new ShootPattern(){{ shots = 5; shotDelay = 3f; }};
                    inaccuracy = 4f;
                    reload = 50f;

                    bullet = new MissileBulletType(5f, 0f){{
                        width = 7f;
                        height = 12f;
                        shrinkY = 0f;
                        backColor = trailColor = UnityPal.scarColor;
                        frontColor = UnityPal.endColor;
                        splashDamage = 30f;
                        splashDamageRadius = 20f;
                        weaveMag = 3f;
                        weaveScale = 4f;
                    }};
                }}
            );

            constructor = mindustry.gen.LegsUnit::create;
        }};

        // Sundown (T4) - 精英腿部单位, 大型导弹巢 + 重磁轨炮 + 方向护盾
        sundown = new UnityUnitType("sundown"){{
            aiController = DistanceGroundAI::new;
            speed = 0.6f;
            health = 9400;
            hitSize = 36f;
            range = 360f;
            allowLegStep = true;
            legMoveSpace = 0.53f;
            rotateSpeed = 2.5f;
            armor = 4f;
            legCount = 4;
            // legTrns = 0.4f; // v158.1 已移除该字段
            legLength = 44f;
            legExtension = -9.3f;
            legSplashDamage = 20f;
            legSplashRange = 30f;

            groundLayer = Layer.legUnit;
            // visualElevation = 0.65f; // v158.1 已移除该字段

            weapons.add(new Weapon("create-scar-large-launcher"){{
                x = 13.5f;
                y = -6.5f;
                shootY = 5f;
                shadow = 8f;
                rotateSpeed = 5f;
                rotate = true;
                reload = 80f;
                shake = 1f;
                shoot = new ShootPattern(){{ shots = 12; }};
                inaccuracy = 19f;
                velocityRnd = 0.2f;
                xRand = 1.2f;
                shootSound = Sounds.shootMissile;

                bullet = Z_Bullets.scarMissile;
            }}, new Weapon("create-scar-railgun"){{
                x = 7f;
                y = -9.25f;
                shootY = 10.75f;
                rotateSpeed = 2f;
                rotate = true;
                shadow = 12f;
                reload = 60f * 2.7f;
                shootSound = Sounds.shootArtillery;

                bullet = new RailBulletType(){{
                    damage = 880f;
                    length = 61f * 7f;
                    pointEffectSpace = 61f;
                    pointEffect = ScarFx.scarRailTrail;
                    pierceEffect = ScarFx.scarRailHit;
                    hitEffect = Fx.massiveExplosion;
                    pierceDamageFactor = 0.2f;
                }};
            }});

            DirectionShieldAbility shield = new DirectionShieldAbility(4, 0.1f, 20f, 1600f, 2.3f, 1.3f, 32.2f);
            shield.healthBarColor = UnityPal.endColor;

            abilities.add(shield);

            constructor = mindustry.gen.LegsUnit::create;
        }};

        // Rex (T5) - 重型悬浮腿部单位, 重型磁轨炮 + 破片机炮 + 双导弹巢 + 方向护盾
        rex = new UnityUnitType("rex"){{
            aiController = DistanceGroundAI::new;
            speed = 0.55f;
            health = 23000;
            hitSize = 47.5f;
            range = 390f;
            allowLegStep = true;
            rotateSpeed = 2f;
            armor = 12f;

            hovering = true;
            groundLayer = Layer.legUnit + 0.01f;
            // visualElevation = 0.95f; // v158.1 已移除该字段

            legCount = 4;
            // legTrns = 1f; // v158.1 已移除该字段
            legLength = 56f;
            legExtension = -9.5f;
            legSplashDamage = 90f;
            legSplashRange = 65f;
            legSpeed = 0.08f;
            legMoveSpace = 0.57f;
            legPairOffset = 0.8f;

            weapons.add(new Weapon("create-rex-railgun"){{
                x = 31.25f;
                y = -12.25f;
                shootY = 23.25f;
                rotate = false;
                top = false;
                reload = 60f * 4.5f;
                recoil = 4f;
                shootSound = Sounds.shootArtillery;

                bullet = new RailBulletType(){{
                    damage = 3300f;
                    buildingDamageMultiplier = 0.5f;
                    length = 61f * 8f;
                    pointEffectSpace = 61f;
                    pointEffect = ScarFx.scarRailTrail;
                    pierceEffect = ScarFx.scarRailHit;
                    hitEffect = Fx.massiveExplosion;
                    pierceDamageFactor = 0.35f;

                    // PU132 在此处手动重写 init() 补分段特效;
                    // v158.1 原生 RailBulletType.init 已内置 fdata 记录 + pointEffect
                    // 分段释放, 无需再覆盖
                }};
            }}, new Weapon("create-scar-large-launcher"){{
                x = 12.25f;
                y = 13f;
                shootY = 5f;
                xRand = 2.2f;
                shadow = 8f;
                rotateSpeed = 5f;
                rotate = true;
                reload = 4f;
                inaccuracy = 5f;

                bullet = new BasicBulletType(6f, 12f){{
                    lifetime = 35f;
                    width = 7f;
                    height = 12f;
                    pierce = true;
                    pierceBuilding = true;
                    pierceCap = 2;
                }};
            }}, new Weapon("create-scar-large-launcher"){{
                x = 15.75f;
                y = -17.5f;
                shootY = 5f;
                shadow = 8f;
                rotateSpeed = 5f;
                rotate = true;
                reload = 85f;
                shake = 1f;
                shoot = new ShootPattern(){{ shots = 9; }};
                inaccuracy = 19f;
                velocityRnd = 0.2f;
                xRand = 1.2f;
                shootSound = Sounds.shootMissile;

                bullet = Z_Bullets.scarMissile;
            }}, new Weapon("create-scar-large-launcher"){{
                x = 9.25f;
                y = -13.75f;
                shootY = 5f;
                shadow = 8f;
                rotateSpeed = 5f;
                rotate = true;
                reload = 90f;
                shake = 1f;
                shoot = new ShootPattern(){{ shots = 9; }};
                inaccuracy = 19f;
                velocityRnd = 0.2f;
                xRand = 1.2f;
                shootSound = Sounds.shootMissile;

                bullet = Z_Bullets.scarMissile;
            }});

            DirectionShieldAbility shield = new DirectionShieldAbility(3, 0.06f, 45f, 3100f, 3.3f, 0.9f, 49f);
            shield.healthBarColor = UnityPal.endColor;

            abilities.add(shield);

            constructor = mindustry.gen.LegsUnit::create;
        }};

        // Excelsus (T6) - 顶级悬浮腿部单位, 双导弹巢 + 双连续激光 + 方向护盾
        excelsus = new UnityUnitType("excelsus"){{
            aiController = DistanceGroundAI::new;
            speed = 0.6f;
            health = 38000;
            hitSize = 66.5f;
            range = 370f;
            allowLegStep = true;
            rotateSpeed = 1.4f;
            armor = 18f;
            // customBackLegs = true; // TODO: PU132 的 boolean customBackLegs 后腿翻转渲染
            //   依赖 drawLegs 覆盖 + leg-back/leg-base-back/foot-back 三张贴图,
            //   项目 UnityUnitType 的 customBackLegs 字段目前是 TextureRegion 语义,
            //   待单独移植渲染逻辑后启用 (贴图 excelsus-leg-back 系列已就位)

            hovering = true;
            groundLayer = Layer.legUnit + 0.03f;
            // visualElevation = 1.1f; // v158.1 已移除该字段

            legCount = 6;
            // legTrns = 1f; // v158.1 已移除该字段
            legLength = 62f;
            legExtension = -9.5f;
            legSplashDamage = 120f;
            legSplashRange = 85f;
            legSpeed = 0.06f;
            legMoveSpace = 0.57f;
            legPairOffset = 0.8f;
            // kinematicScl = 0.7f; // PU132 UnityUnitType 自定义字段, 项目简化版未收录

            immunities = ObjectSet.with(StatusEffects.burning);

            weapons.add(new Weapon("create-scar-large-launcher"){{
                x = 8.25f;
                y = -18.5f;
                shootY = 5f;
                shadow = 8f;
                rotateSpeed = 5f;
                rotate = true;
                reload = 80f;
                shake = 1f;
                shoot = new ShootPattern(){{ shots = 12; }};
                inaccuracy = 19f;
                velocityRnd = 0.2f;
                xRand = 1.2f;
                shootSound = Sounds.shootMissile;

                bullet = Z_Bullets.scarMissile;
            }}, new Weapon("create-scar-large-launcher"){{
                x = 13.75f;
                y = -24.5f;
                shootY = 5f;
                shadow = 8f;
                rotateSpeed = 5f;
                rotate = true;
                reload = 75f;
                shake = 1f;
                shoot = new ShootPattern(){{ shots = 12; }};
                inaccuracy = 19f;
                velocityRnd = 0.2f;
                xRand = 1.2f;
                shootSound = Sounds.shootMissile;

                bullet = Z_Bullets.scarMissile;
            }}, new Weapon("create-scar-small-laser-weapon"){{
                x = 18.25f;
                y = 11.75f;
                shootY = 4f;
                rotateSpeed = 5f;
                rotate = true;
                reload = 3f * 60f;
                shake = 1.2f;
                continuous = true;
                alternate = false;
                shootSound = Sounds.none;

                bullet = new ContinuousLaserBulletType(40f){{
                    length = 180f;
                    lifetime = 10f * 60f;
                    shake = 1.2f;
                    incendChance = 0f;
                    largeHit = false;
                    colors = new Color[]{UnityPal.scarColorAlpha, UnityPal.endColor, Color.white};
                    width = 4f;
                    hitColor = UnityPal.scarColor;
                    lightColor = UnityPal.scarColorAlpha;
                    hitEffect = ScarFx.scarHitSmall;
                }};
            }}, new Weapon("create-excelsus-laser-weapon"){{
                x = 29.75f;
                y = -20.5f;
                shootY = 7f;
                shadow = 19f;
                rotateSpeed = 1.5f;
                rotate = true;
                reload = 7f * 60f;
                shake = 2f;
                continuous = true;
                alternate = false;
                shootSound = Sounds.none;

                bullet = new ContinuousLaserBulletType(210f){{
                    length = 360f;
                    lifetime = 3f * 60f;
                    shake = 3f;
                    colors = new Color[]{UnityPal.scarColorAlpha, UnityPal.endColor, Color.white};
                    width = 8f;
                    hitColor = UnityPal.scarColor;
                    lightColor = UnityPal.scarColorAlpha;
                    hitEffect = ScarFx.scarHitSmall;
                }};
            }});

            DirectionShieldAbility shield = new DirectionShieldAbility(6, 0.04f, 29f, 3400f, 4.2f, 0.9f, 54f);
            shield.healthBarColor = UnityPal.endColor;

            abilities.add(shield);

            constructor = mindustry.gen.LegsUnit::create;
        }};

        //endregion
        //region scar 飞行单位

        // Whirlwind (T1) - 基础飞行单位, saber 持续激光 + 导弹
        whirlwind = new UnityUnitType("whirlwind"){{
            health = 280;
            rotateSpeed = 4.5f;
            faceTarget = false;
            flying = true;
            speed = 8f;
            drag = 0.019f;
            accel = 0.028f;
            hitSize = 8f;
            armor = 1f;  // ★ 按用户设定 (原缺失)
            engineOffset = 8f;

            weapons.add(new BlankWeapon() {{
                mirror = false;
                x = 0f;
                y = 4f;
                minShootVelocity = 5f;
                continuous = true;
                // 射击时施加装填疲劳 (PU132 UnityStatusEffects.reloadFatigue)
                shootStatus = reloadFatigue;
                shootCone = 20f;

                bullet = new SaberContinuousLaserBulletType(21f){{
                    lightStroke = 40f;
                    largeHit = false;
                    lifetime = 10 * 60f;
                    length = 160f;
                    width = 5f;
                    incendChance = 0f;
                    hitEffect = ScarFx.coloredHitSmall;
                    lightColor = hitColor = UnityPal.scarColorAlpha;
                    colors = new Color[]{UnityPal.scarColorAlpha, UnityPal.endColor, Color.white};
                    strokes = new float[]{1.5f, 1f, 0.3f};
                }};

                shootStatusDuration = bullet.lifetime;
                reload = 2 * 60f;
            }}, new BlankWeapon() {{
                rotate = true;
                x = 4.2f;
                reload = 50f;
                inaccuracy = 1.1f;
                shoot = new ShootPattern(){{ shots = 5; shotDelay = 3f; }};

                bullet = new MissileBulletType(5f, 1f){{
                    height = 10f;
                    shrinkY = 0f;
                    backColor = trailColor = UnityPal.scarColor;
                    frontColor = UnityPal.endColor;
                    splashDamage = 25f;
                    splashDamageRadius = 20f;
                    weaveMag = 3f;
                    weaveScale = 4f;
                }};
            }});

            constructor = mindustry.gen.UnitEntity::create;
        }};

        // Jetstream (T2) - 进阶飞行单位, swipe saber 激光 + 导弹
        jetstream = new UnityUnitType("jetstream"){{
            health = 670;
            rotateSpeed = 12.5f;
            flying = true;
            speed = 9.2f;
            drag = 0.019f;
            accel = 0.028f;
            hitSize = 11f;
            armor = 2f;  // ★ 按用户设定 (原缺失)
            engineOffset = 11f;

            weapons.add(new BlankWeapon() {{
                mirror = false;
                x = 0f;
                y = 7f;
                continuous = true;
                shootStatus = reloadFatigue;
                shootCone = 15f;

                bullet = new SaberContinuousLaserBulletType(35f){{
                    swipe = true;
                    lightStroke = 40f;
                    largeHit = false;
                    lifetime = 15f * 60f;
                    length = 150f;
                    width = 5f;
                    incendChance = 0f;
                    hitEffect = ScarFx.coloredHitSmall;
                    lightColor = hitColor = UnityPal.scarColorAlpha;
                    colors = new Color[]{UnityPal.scarColorAlpha, UnityPal.endColor, Color.white};
                    strokes = new float[]{1.5f, 1f, 0.3f};
                    lenscales = new float[]{0.85f, 0.97f, 1f, 1.02f};
                }};

                reload = 60f * 3.2f;
                shootStatusDuration = bullet.lifetime;
            }}, new Weapon("create-small-scar-weapon"){{
                rotate = true;
                x = 7.25f;
                y = -3.5f;
                reload = 50f;
                inaccuracy = 1.1f;
                shoot = new ShootPattern(){{ shots = 6; shotDelay = 4f; }};

                bullet = new MissileBulletType(5f, 1f){{
                    width = 7f;
                    height = 12f;
                    shrinkY = 0f;
                    backColor = trailColor = UnityPal.scarColor;
                    frontColor = UnityPal.endColor;
                    splashDamage = 40f;
                    splashDamageRadius = 20f;
                    weaveMag = 3f;
                    weaveScale = 4f;
                }};
            }});

            constructor = mindustry.gen.UnitEntity::create;
        }};

        // Vortex (T3) - 高级飞行单位, swipe saber 激光
        vortex = new UnityUnitType("vortex"){{
            health = 1200;
            rotateSpeed = 12.5f;
            flying = true;
            speed = 9.1f;
            drag = 0.019f;
            accel = 0.028f;
            hitSize = 11f;
            armor = 3f;  // ★ 按用户设定 (原缺失)
            engineOffset = 14f;

            weapons.add(new BlankWeapon() {{
                mirror = false;
                x = 0f;
                continuous = true;

                bullet = new SaberContinuousLaserBulletType(60f){{
                    swipe = true;
                    swipeDamageMultiplier = 1.2f;
                    largeHit = false;
                    lifetime = 5f * 60f;
                    length = 190f;
                    width = 5f;
                    incendChance = 0f;
                    hitEffect = ScarFx.coloredHitSmall;
                    lightColor = hitColor = UnityPal.scarColorAlpha;
                    colors = new Color[]{UnityPal.scarColorAlpha, UnityPal.endColor, Color.white};
                    strokes = new float[]{1.5f, 1f, 0.3f};
                }};

                reload = 2f * 60f;
            }});

            constructor = mindustry.gen.UnitEntity::create;
        }};

        //endregion
    }
}
