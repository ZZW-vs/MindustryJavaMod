package zzw.content.units;

import arc.graphics.Color;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * 分段单位加载类
 *
 * 第一个单位: arcnelidia (模仿 PU132 的 Arcnelidia)
 * - 飞行虫子, 5 段身体
 * - 头部装激光武器
 * - 段身血量分布 (在 SegmentWormEntity 中实现)
 *
 * ★ 重要 ★
 * 头部和段身用不同的 UnitType:
 * - arcnelidia (头部): 用 SegmentWormEntity, 有武器和血量分布
 * - arcnelidia-segment (段身): 用 SegmentUnitEntity, 不开火不分裂
 *
 * 段身 UnitType 设 hidden=true, 不会出现在数据库和 Spawner 选项中
 * 玩家无法在游戏中单独召唤段身
 */
public class Z_Units {

    public static UnitType
        arcnelidia,            // 头部
        arcnelidiaSegment,     // 段身
        toxobyte,              // 头部 (PU132 瘟疫虫, 25 段)
        toxobyteSegment;       // 段身

    public static void load() {
        // ★ 关键: 注册自定义 Entity 到 EntityMapping.idMap, 否则 v154.3 的 UnitType.init() 会失败 ★
        // v154.3 要求每个自定义 Entity class 有唯一 classId, 必须在 idMap 占一个空 slot
        // (模仿 PU132 的 UnityEntityMapping.register)
        ZEntityRegister.register(SegmentWormEntity.class, SegmentWormEntity::new);
        ZEntityRegister.register(SegmentUnitEntity.class, SegmentUnitEntity::new);

        // —— 段身 UnitType (先创建, 头部要引用它) ——
        arcnelidiaSegment = new UnitType("arcnelidia-segment") {{
            health = 800;
            speed = 0f;                 // 段身不需要自己移动 (由头部驱动)
            // ★ hitSize=19.75f (19.25 + 0.5, 用户要求增大 0.5)
            // 碰撞计算: 段间距22.7 > 半径9.875+9.875=19.75, 不重叠 (间隙2.95)
            hitSize = 19.75f;
            armor = 5f;
            flying = true;
            rotateSpeed = 1f;
            faceTarget = false;
            // ★ 关闭 wobble (PU132 原版静止时不晃动)
            wobble = false;

            // 用 SegmentUnitEntity (禁用 AI 和自身移动)
            constructor = SegmentUnitEntity::create;

            // ★ 隐藏段身 (不出现在数据库/Spawner, 玩家无法单独召唤)
            hidden = true;

            // ★ 段身保留物理碰撞 (PU132 WormSegmentUnit 默认 physics=true)
            // 其他单位不能穿过段身, 段身之间通过 collides() 重写过滤
            physics = true;
            hittable = true;

            // 段身不需要武器
            // weapons 留空
        }};

        // —— 头部 Arcnelidia 飞行分段虫子 ——
        arcnelidia = new UnitType("arcnelidia") {{
            // ===== 基础属性 (PU132 原值) =====
            health = 800;
            speed = 4f;
            accel = 0.035f;
            rotateSpeed = 3.2f;
            // ★ hitSize=19.75f (19.25 + 0.5, 用户要求增大 0.5)
            hitSize = 19.75f;
            armor = 5f;
            flying = true;
            // PU132: engineSize=-1f (不显示引擎喷射效果)
            engineSize = -1f;
            range = 210f;
            faceTarget = false;
            // ★ 关闭 wobble (PU132 原版静止时不晃动)
            wobble = false;
            // ★ drag 增大 (用户要求移动阻力稍大, 防止停止后滑行)
            drag = 0.018f;

            // 用自定义 Entity (SegmentWormEntity)
            constructor = SegmentWormEntity::create;
            // ★ 使用 WormAI (待机静止, 不自动朝 spawn 移动)
            // v154.3: defaultController 改名为 aiController
            aiController = zzw.content.units.WormAI::new;

            // ===== 头部武器: 双激光 (PU132 原配置) =====
            // PU132 UnityUnitTypes.java 第3024-3037行原配置
            weapons.add(new Weapon("arcnelidia-laser") {{
                x = 0f;
                reload = 10f;
                rotateSpeed = 50f;
                // shootSound 在后面用反射设置
                mirror = true;
                rotate = true;
                minShootVelocity = 2.1f;
                bullet = new LaserBulletType(200f) {{
                    // PU132 原配置: surge 颜色 (电弧激光, 黄色)
                    colors = new Color[]{
                        Pal.surge.cpy().mul(1f, 1f, 1f, 0.4f),
                        Pal.surge,
                        Color.white
                    };
                    drawSize = 400f;
                    collidesAir = false;
                    length = 190f;
                    // ★ 加大激光宽度: 20f (v154.3 默认 15f, 太细看起来像白线)
                    width = 20f;
                    // ★ 加长激光持续时间: 24f (v154.3 默认 16f, 太短看起来断断续续)
                    lifetime = 24f;
                    // lengthFalloff 控制每层颜色宽度递减, 0.5 = 每层减半
                    // 保持默认 0.5f
                }};
            }});
        }};

        // ★ 注册 arcnelidia 段身配置到 configs Map ★
        // ★ key 用 type.name (v154.3 mod 单位的 name 带 mod 前缀, 如 "create-arcnelidia")
        // PU132 原版 segmentLength=9, segmentOffset=23f
        // 段间距 22.7f (PU132 23f - 0.3f, 用户要求稍小一点)
        // ★ wobble=true (arcnelidia 轻微晃动, toxobyte=false 完全静止)
        SegmentWormEntity.configs.put(arcnelidia.name,
            new SegmentWormEntity.SegmentConfig(arcnelidiaSegment, 9, 22.7f, 0f, 0, true));

        // 用反射设置 shootSound 和 visualElevation, 避开编译期字段差异 (v150 vs v154)
        try {
            Class<?> soundsClass = Class.forName("mindustry.gen.Sounds");
            java.lang.reflect.Field f = soundsClass.getField("shootLaser");
            Object snd = f.get(null);
            arc.audio.Sound sound = (arc.audio.Sound) snd;
            arcnelidia.weapons.first().shootSound = sound;
        } catch (Throwable t) {
            try { arc.util.Log.err("set shootSound failed", t); } catch (Throwable ignored) {}
        }
        // PU132: visualElevation=0.8f (v150 没有这个字段, 用反射设置)
        try {
            java.lang.reflect.Field ve = arcnelidia.getClass().getSuperclass().getField("visualElevation");
            ve.setFloat(arcnelidia, 0.8f);
        } catch (Throwable t) {
            try { arc.util.Log.err("set visualElevation failed", t); } catch (Throwable ignored) {}
        }

        // ═══════════════════════════════════════════════════════════
        //  Toxobyte (PU132 瘟疫虫)
        //  - 25 段 (segmentLength=25)
        //  - segmentOffset=16.25f, hitSize=15.75f (段间距 ≈ 半径, 紧凑)
        //  - angleLimit=25f (PU132 原值)
        //  - splittable=true, regenTime=15*60f (分裂/再生, 高难度, 暂不移植)
        //  - circleTarget=true, omniMovement=false (环绕目标)
        //  - 武器先不加 (用户要求: 先保证显示和动态)
        // ═══════════════════════════════════════════════════════════

        // —— 段身 UnitType ——
        toxobyteSegment = new UnitType("toxobyte-segment") {{
            health = 200;
            speed = 0f;
            // ★ hitSize=14.2f (用户指定)
            hitSize = 14.2f;
            flying = true;
            rotateSpeed = 1f;
            faceTarget = false;
            constructor = SegmentUnitEntity::create;
            hidden = true;
            physics = true;
            hittable = true;
            // ★ 关闭 wobble (飞行单位默认会小幅晃动, PU132 原版是静止的)
            wobble = false;

            // ===== 段身武器: ArtilleryBullet (PU132 第3269-3281行) =====
            // 瘟疫炮弹: splashDamage=25, splashDamageRadius=25, 瘟疫色
            weapons.add(new Weapon("toxobyte-segment-launcher") {{
                rotate = true;
                mirror = false;
                reload = 60f;
                shootCone = 90f;
                // 154.3 武器需要 recoil/rotateSpeed
                rotateSpeed = 50f;
                bullet = new mindustry.entities.bullet.ArtilleryBulletType(5f, 7f) {{
                    collidesTiles = true;
                    collidesAir = true;
                    collidesGround = true;
                    width = 11f;
                    height = 11f;
                    splashDamage = 25f;
                    splashDamageRadius = 25f;
                    // PU132 UnityPal.plagueDark = #54de3b, plague = #a3f080
                    trailColor = hitColor = lightColor = backColor = Color.valueOf("54de3b");
                    frontColor = Color.valueOf("a3f080");
                }};
            }});
        }};

        // —— 头部 Toxobyte ——
        toxobyte = new UnitType("toxobyte") {{
            // ===== 基础属性 (PU132 UnityUnitTypes.java 第3231-3248行) =====
            health = 200f;
            speed = 3f;
            accel = 0.035f;
            rotateSpeed = 3f;       // PU132 未设, 用默认
            // ★ hitSize=14.75f (用户指定)
            hitSize = 14.75f;
            flying = true;
            engineSize = -1f;
            range = 130f;           // PU132 武器 length=130
            faceTarget = false;
            // ★ 关闭 wobble (PU132 原版静止时不晃动)
            wobble = false;
            // ★ drag 增大 (用户要求移动阻力稍大, 防止停止后滑行)
            drag = 0.025f;
            // ★ PU132: circleTarget=true, omniMovement=false
            // 154.3 FlyingAI 内置 circleAttack, 设 circleTarget=true 即自动环绕
            circleTarget = true;
            omniMovement = false;
            constructor = SegmentWormEntity::create;
            // ★ 使用 WormAI (待机静止, 不自动朝 spawn 移动)
            // v154.3: defaultController 改名为 aiController
            aiController = zzw.content.units.WormAI::new;

            // ===== 头部武器: 12 发散 SapBullet (PU132 第3250-3267行) =====
            // 瘟疫激光: 12 发同时散射, SapBullet 自动回血
            // ★ v154.3: shots/shotDelay 在 shoot (ShootPattern) 字段里, 不在 Weapon 上
            weapons.add(new Weapon("toxobyte-sap") {{
                x = 0f;
                rotate = false;
                mirror = false;
                reload = 70f;
                shootCone = 90f;
                inaccuracy = 35f;
                xRand = 2f;
                // v154.3 ShootPattern: shots=12, shotDelay=0.5f
                shoot.shots = 12;
                shoot.shotDelay = 0.5f;
                bullet = new mindustry.entities.bullet.SapBulletType() {{
                    // PU132 UnityPal.plague = #a3f080 (浅黄绿)
                    color = Color.valueOf("a3f080");
                    damage = 20f;
                    length = 130f;
                    width = 1f;
                    status = mindustry.content.StatusEffects.none;
                }};
            }});
        }};

        // ★ 注册 toxobyte 段身配置 ★
        // ★ key 用 type.name (v154.3 mod 单位的 name 带 mod 前缀, 如 "create-toxobyte")
        // PU132: segmentLength=25, segmentOffset=16.25f
        // PU132: regenTime=15*60f (15秒长一节), maxSegments 默认上限 25
        SegmentWormEntity.configs.put(toxobyte.name,
            new SegmentWormEntity.SegmentConfig(toxobyteSegment, 25, 16.25f, 15f * 60f, 25));

        System.out.println("[Z_Units] load done, configs=" + SegmentWormEntity.configs.keySet());
    }
}
