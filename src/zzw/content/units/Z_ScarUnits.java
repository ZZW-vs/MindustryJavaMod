package zzw.content.units;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.pattern.ShootSpread;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import zzw.content.graphics.UnityPal;
import zzw.content.units.abilities.DirectionShieldAbility;
import zzw.content.units.ai.DistanceGroundAI;
import zzw.content.units.bullets.SaberContinuousLaserBulletType;
import zzw.content.units.effects.ScarFx;
import zzw.content.units.types.MixedLegUnitType;

/**
 * Scar 系列单位注册类
 * PU132 移植版 - 6个腿部单位 + 3个飞行单位
 * 
 * <p>包含单位：</p>
 * <ul>
 *   <li>hovos (T1) - 基础腿部单位，磁轨炮</li>
 *   <li>ryzer (T2) - 进阶腿部单位，双磁轨炮</li>
 *   <li>zena (T3) - 高级腿部单位，三磁轨炮+护盾</li>
 *   <li>sundown (T4) - 精英腿部单位，激光剑+护盾</li>
 *   <li>rex (T5) - 重型腿部单位，导弹+激光</li>
 *   <li>excelsus (T6) - 顶级腿部单位，多重武器</li>
 *   <li>whirlwind (T1) - 基础飞行单位，机枪</li>
 *   <li>jetstream (T2) - 进阶飞行单位，激光</li>
 *   <li>vortex (T3) - 高级飞行单位，导弹+护盾</li>
 * </ul>
 */
public class Z_ScarUnits {
    
    // 腿部单位 (6个)
    public static UnitType hovos, ryzer, zena, sundown, rex, excelsus;
    // 飞行单位 (3个)
    public static UnitType whirlwind, jetstream, vortex;

    /**
     * 加载入口 (TestMod.loadContent 调用)。
     *
     * <p>内容本体已在静态初始化块中通过构造器注册到 Mindustry 内容系统
     * (MappableContent 构造时自动入册), 此方法仅触发类初始化。</p>
     */
    public static void load(){}
    
    // Reload Fatigue 状态管理
    private static float currentReloadFatigue = 0f;
    private static float maxReloadFatigue = 100f;
    private static float fatigueRecoveryRate = 5f;
    
    /**
     * 更新 Reload Fatigue 状态
     * @param fatigueDelta 疲劳值变化量
     */
    public static void updateReloadFatigue(float fatigueDelta) {
        currentReloadFatigue = Mathf.clamp(currentReloadFatigue + fatigueDelta, 0f, maxReloadFatigue);
    }
    
    /**
     * 获取当前疲劳值
     * @return 当前疲劳值 (0-100)
     */
    public static float getCurrentReloadFatigue() {
        return currentReloadFatigue;
    }
    
    /**
     * 重置疲劳值
     */
    public static void resetReloadFatigue() {
        currentReloadFatigue = 0f;
    }
    
    /**
     * 检查是否可以射击（基于疲劳值）
     * @param unit 检查的单位
     * @return 是否可以射击
     */
    public static boolean canShoot(Unit unit) {
        if (currentReloadFatigue < maxReloadFatigue * 0.8f) {
            return true;
        }
        return false;
    }
    
    static {
        // ===== 腿部单位注册 =====
        
        // Hovos (T1) - 基础腿部单位，磁轨炮
        hovos = new MixedLegUnitType("hovos") {{
            health = 8000f;
            speed = 2.2f;
            armor = 2f;
            hitSize = 14f;
            rotateSpeed = 2f;
            drag = 0.02f;
            // acceleration = 0.15f; // v158.1 中可能不存在
            range = 240f;
            weapons.add(new Weapon("create-hovos-railgun") {{
                top = false;
                x = 0f;
                y = 8f;
                shootY = 2f;
                reload = 60f;
                mirror = true;
                shootCone = 20f;
                bullet = new BasicBulletType(12f, 8f) {{
                    width = 4f;
                    height = 12f;
                    lifetime = 20f;
                    speed = 16f;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                }};
            }});
            
            // AI 控制器
            aiController = DistanceGroundAI::new;
            
            // 腿部配置
            legCount = 4;
            legLength = 12f;
            legMoveSpace = 0.3f;
            
            // 贴图
            legBaseRegion = Core.atlas.find("create-hovos-backleg");
            legRegion = Core.atlas.find("create-hovos-leg");
            footRegion = Core.atlas.find("create-hovos-foot");
            
            constructor = mindustry.gen.UnitEntity::create;
        }};
        
        // Ryzer (T2) - 进阶腿部单位，双磁轨炮
        ryzer = new MixedLegUnitType("ryzer") {{
            health = 15000f;
            speed = 2.0f;
            armor = 4f;
            hitSize = 16f;
            rotateSpeed = 2.2f;
            drag = 0.025f;
            // acceleration = 0.13f; // v158.1 中可能不存在
            range = 280f;
            weapons.add(new Weapon("create-ryzer-railgun-left") {{
                top = false;
                x = -8f;
                y = 10f;
                shootY = 3f;
                reload = 45f;
                shootCone = 25f;
                bullet = new BasicBulletType(14f, 10f) {{
                    width = 5f;
                    height = 14f;
                    lifetime = 22f;
                    speed = 18f;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                }};
            }});
            weapons.add(new Weapon("create-ryzer-railgun-right") {{
                top = false;
                x = 8f;
                y = 10f;
                shootY = 3f;
                reload = 45f;
                shootCone = 25f;
                bullet = new BasicBulletType(14f, 10f) {{
                    width = 5f;
                    height = 14f;
                    lifetime = 22f;
                    speed = 18f;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                }};
            }});

            // AI 控制器
            aiController = DistanceGroundAI::new;
            
            // 腿部配置
            legCount = 6;
            legLength = 14f;
            legMoveSpace = 0.35f;
            
            // 贴图
            legBaseRegion = Core.atlas.find("create-ryzer-backleg");
            legRegion = Core.atlas.find("create-ryzer-leg");
            footRegion = Core.atlas.find("create-ryzer-foot");
            
            constructor = mindustry.gen.UnitEntity::create;
        }};
        
        // Zena (T3) - 高级腿部单位，三磁轨炮+护盾
        zena = new MixedLegUnitType("zena") {{
            health = 25000f;
            speed = 1.8f;
            armor = 6f;
            hitSize = 18f;
            rotateSpeed = 2.5f;
            drag = 0.03f;
            // acceleration = 0.12f; // v158.1 中可能不存在
            range = 320f;
            weapons.add(new Weapon("create-zena-railgun-center") {{
                top = false;
                x = 0f;
                y = 12f;
                shootY = 4f;
                reload = 30f;
                shootCone = 30f;
                bullet = new BasicBulletType(16f, 12f) {{
                    width = 6f;
                    height = 16f;
                    lifetime = 25f;
                    speed = 20f;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                }};
            }});
            weapons.add(new Weapon("create-zena-railgun-left") {{
                top = false;
                x = -10f;
                y = 14f;
                shootY = 4f;
                reload = 40f;
                shootCone = 25f;
                bullet = new BasicBulletType(14f, 10f) {{
                    width = 5f;
                    height = 14f;
                    lifetime = 22f;
                    speed = 18f;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                }};
            }});
            weapons.add(new Weapon("create-zena-railgun-right") {{
                top = false;
                x = 10f;
                y = 14f;
                shootY = 4f;
                reload = 40f;
                shootCone = 25f;
                bullet = new BasicBulletType(14f, 10f) {{
                    width = 5f;
                    height = 14f;
                    lifetime = 22f;
                    speed = 18f;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                }};
            }});
            
            // 护盾能力
            abilities.add(new DirectionShieldAbility(4, 2.0f, 12f, 800f, 8f, 2f, 16f));
            
            // AI 控制器
            aiController = DistanceGroundAI::new;
            
            // 腿部配置
            legCount = 8;
            legLength = 16f;
            legMoveSpace = 0.4f;
            
            // 贴图
            legBaseRegion = Core.atlas.find("create-zena-backleg");
            legRegion = Core.atlas.find("create-zena-leg");
            footRegion = Core.atlas.find("create-zena-foot");
            
            constructor = mindustry.gen.UnitEntity::create;
        }};
        
        // Sundown (T4) - 精英腿部单位，激光剑+护盾
        sundown = new MixedLegUnitType("sundown") {{
            health = 40000f;
            speed = 1.6f;
            armor = 8f;
            hitSize = 20f;
            rotateSpeed = 3.0f;
            drag = 0.04f;
            // acceleration = 0.10f; // v158.1 中可能不存在
            range = 200f;
            weapons.add(new Weapon("create-sundown-saber") {{
                top = false;
                x = 0f;
                y = 15f;
                shootY = 5f;
                reload = 20f;
                shootCone = 360f;
                bullet = new SaberContinuousLaserBulletType(25f) {{
                    length = 80f;
                    width = 4f;
                    damage = 8f;
                    colors = new Color[]{UnityPal.scarColor, Color.white};
                    strokes = new float[]{1.2f, 0.8f};
                    lightColor = UnityPal.scarColor;
                    lightStroke = 60f;
                    largeHit = true;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                    // 启用 swipe 模式
                    swipe = true;
                    swipeTime = 40f;
                    swipeDamageMultiplier = 1.5f;
                }};
            }});
            
            // 护盾能力
            abilities.add(new DirectionShieldAbility(6, 2.5f, 15f, 1200f, 10f, 3f, 20f));
            
            // AI 控制器
            aiController = DistanceGroundAI::new;
            
            // 腿部配置
            legCount = 10;
            legLength = 18f;
            legMoveSpace = 0.45f;
            
            // 贴图
            legBaseRegion = Core.atlas.find("create-sundown-backleg");
            legRegion = Core.atlas.find("create-sundown-leg");
            footRegion = Core.atlas.find("create-sundown-foot");
            
            constructor = mindustry.gen.UnitEntity::create;
        }};
        
        // Rex (T5) - 重型腿部单位，导弹+激光
        rex = new MixedLegUnitType("rex") {{
            health = 60000f;
            speed = 1.4f;
            armor = 10f;
            hitSize = 22f;
            rotateSpeed = 2.8f;
            drag = 0.05f;
            // acceleration = 0.08f; // v158.1 中可能不存在
            range = 400f;
            weapons.add(new Weapon("create-rex-missile") {{
                top = false;
                x = -15f;
                y = 18f;
                shootY = 6f;
                reload = 80f;
                shootCone = 45f;
                bullet = new MissileBulletType() {{
                    speed = 4f;
                    lifetime = 80f;
                    damage = 120f;
                    splashDamage = 80f;
                    splashDamageRadius = 40f;
                    homingRange = 120f;
                    homingPower = 0.08f;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                    hitEffect = Fx.dynamicExplosion;
                    smokeEffect = Fx.shootBig;
                    trailEffect = ScarFx.scarRailTrail;
                }};
            }});
            weapons.add(new Weapon("create-rex-laser") {{
                top = false;
                x = 15f;
                y = 18f;
                shootY = 6f;
                reload = 40f;
                shootCone = 30f;
                bullet = new LaserBulletType(15f) {{
                    length = 60f;
                    width = 3f;
                    damage = 12f;
                    colors = new Color[]{UnityPal.scarColor, Color.white};
                    lightColor = UnityPal.scarColor;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                }};
            }});
            
            // AI 控制器
            aiController = DistanceGroundAI::new;
            
            // 腿部配置
            legCount = 12;
            legLength = 20f;
            legMoveSpace = 0.5f;
            
            // 贴图
            legBaseRegion = Core.atlas.find("create-rex-backleg");
            legRegion = Core.atlas.find("create-rex-leg");
            footRegion = Core.atlas.find("create-rex-foot");
            
            constructor = mindustry.gen.UnitEntity::create;
        }};
        
        // Excelsus (T6) - 顶级腿部单位，多重武器
        excelsus = new MixedLegUnitType("excelsus") {{
            health = 100000f;
            speed = 1.2f;
            armor = 15f;
            hitSize = 24f;
            rotateSpeed = 3.5f;
            drag = 0.06f;
            // acceleration = 0.06f; // v158.1 中可能不存在
            range = 450f;
            weapons.add(new Weapon("create-excelsus-railgun") {{
                top = false;
                x = -20f;
                y = 20f;
                shootY = 8f;
                reload = 25f;
                shootCone = 35f;
                bullet = new BasicBulletType(18f, 15f) {{
                    width = 7f;
                    height = 18f;
                    lifetime = 28f;
                    speed = 22f;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                }};
            }});
            weapons.add(new Weapon("create-excelsus-saber") {{
                top = false;
                x = 0f;
                y = 22f;
                shootY = 10f;
                reload = 15f;
                shootCone = 360f;
                bullet = new SaberContinuousLaserBulletType(35f) {{
                    length = 100f;
                    width = 5f;
                    damage = 12f;
                    colors = new Color[]{UnityPal.scarColor, Color.white};
                    strokes = new float[]{1.5f, 1.0f};
                    lightColor = UnityPal.scarColor;
                    lightStroke = 80f;
                    largeHit = true;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                    swipe = true;
                    swipeTime = 50f;
                    swipeDamageMultiplier = 2.0f;
                }};
            }});
            weapons.add(new Weapon("create-excelsus-missile") {{
                top = false;
                x = 20f;
                y = 20f;
                shootY = 8f;
                reload = 60f;
                shootCone = 40f;
                bullet = new MissileBulletType() {{
                    speed = 5f;
                    lifetime = 60f;
                    damage = 150f;
                    splashDamage = 100f;
                    splashDamageRadius = 50f;
                    homingRange = 150f;
                    homingPower = 0.1f;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                    hitEffect = Fx.dynamicExplosion;
                    smokeEffect = Fx.shootBig;
                    trailEffect = ScarFx.scarRailTrail;
                }};
            }});
            
            // 护盾能力
            abilities.add(new DirectionShieldAbility(8, 3.0f, 18f, 2000f, 15f, 5f, 25f));
            
            // AI 控制器
            aiController = DistanceGroundAI::new;
            
            // 腿部配置
            legCount = 14;
            legLength = 22f;
            legMoveSpace = 0.6f;
            
            // 贴图
            legBaseRegion = Core.atlas.find("create-excelsus-backleg");
            legRegion = Core.atlas.find("create-excelsus-leg");
            footRegion = Core.atlas.find("create-excelsus-foot");
            
            constructor = mindustry.gen.UnitEntity::create;
        }};
        
        // ===== 飞行单位注册 =====
        
        // Whirlwind (T1) - 基础飞行单位，机枪
        whirlwind = new UnitType("whirlwind") {{
            health = 5000f;
            speed = 3.5f;
            armor = 3f;
            hitSize = 12f;
            rotateSpeed = 5f;
            drag = 0.01f;
            // acceleration = 0.2f; // v158.1 中可能不存在
            range = 200f;
            weapons.add(new Weapon("create-whirlwind-gun") {{
                top = false;
                x = 0f;
                y = 6f;
                shootY = 2f;
                reload = 20f;
                // v158.1: shots/spread 移入 ShootPattern 体系
                shoot = new ShootSpread(3, 8f);
                shootCone = 25f;
                bullet = new BasicBulletType(8f, 6f) {{
                    width = 3f;
                    height = 8f;
                    lifetime = 30f;
                    speed = 12f;
                    shootEffect = ScarFx.coloredHitSmall;
                    hitEffect = ScarFx.coloredHitSmall;
                    smokeEffect = Fx.shootSmall;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                }};
            }});
            
            constructor = mindustry.gen.UnitEntity::create;
        }};
        
        // Jetstream (T2) - 进阶飞行单位，激光
        jetstream = new UnitType("jetstream") {{
            health = 8000f;
            speed = 4.0f;
            armor = 5f;
            hitSize = 14f;
            rotateSpeed = 6f;
            drag = 0.008f;
            // acceleration = 0.25f; // v158.1 中可能不存在
            range = 300f;
            weapons.add(new Weapon("create-jetstream-laser") {{
                top = false;
                x = 0f;
                y = 8f;
                shootY = 3f;
                reload = 30f;
                shootCone = 20f;
                bullet = new LaserBulletType(12f) {{
                    length = 50f;
                    width = 4f;
                    damage = 10f;
                    colors = new Color[]{UnityPal.scarColor, Color.white};
                    lightColor = UnityPal.scarColor;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                }};
            }});
            
            constructor = mindustry.gen.UnitEntity::create;
        }};
        
        // Vortex (T3) - 高级飞行单位，导弹+护盾
        vortex = new UnitType("vortex") {{
            health = 15000f;
            speed = 4.5f;
            armor = 8f;
            hitSize = 16f;
            rotateSpeed = 7f;
            drag = 0.006f;
            // acceleration = 0.3f; // v158.1 中可能不存在
            range = 350f;
            weapons.add(new Weapon("create-vortex-missile") {{
                top = false;
                x = -10f;
                y = 10f;
                shootY = 4f;
                reload = 50f;
                shootCone = 35f;
                bullet = new MissileBulletType() {{
                    speed = 6f;
                    lifetime = 50f;
                    damage = 80f;
                    splashDamage = 60f;
                    splashDamageRadius = 35f;
                    homingRange = 100f;
                    homingPower = 0.12f;
                    frontColor = UnityPal.scarColor;
                    backColor = Color.white;
                    hitEffect = Fx.dynamicExplosion;
                    smokeEffect = Fx.shootBig;
                    trailEffect = ScarFx.scarRailTrail;
                }};
            }});
            weapons.add(new Weapon("create-vortex-laser") {{
                top = false;
                x = 10f;
                y = 10f;
                shootY = 4f;
                reload = 25f;
                shootCone = 25f;
                bullet = new LaserBulletType(15f) {{
                    length = 60f;
                    width = 5f;
                    damage = 12f;
                    colors = new Color[]{UnityPal.scarColor, Color.white};
                    lightColor = UnityPal.scarColor;
                    shootEffect = ScarFx.scarRailShoot;
                    hitEffect = ScarFx.scarRailHit;
                    smokeEffect = Fx.shootSmall;
                }};
            }});
            
            // 护盾能力
            abilities.add(new DirectionShieldAbility(4, 2.0f, 12f, 600f, 8f, 2f, 18f));
            
            constructor = mindustry.gen.UnitEntity::create;
        }};
        
        // 静态初始化时会自动注册到内容系统
    }
}
