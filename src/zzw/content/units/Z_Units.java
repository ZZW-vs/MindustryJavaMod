package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.util.Time;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

import zzw.content.units.abilities.CustomLegsAbility;
import zzw.content.units.abilities.TimeStopAbility;
import zzw.content.units.anticheat.AntiCheatBulletModule;
import zzw.content.units.anticheat.ArmorDamageModule;
import zzw.content.units.anticheat.AbilityDamageModule;
import zzw.content.units.anticheat.ForceFieldDamageModule;
import zzw.content.units.bullets.DesolationBulletType;
import zzw.content.units.bullets.EndBasicBulletType;
import zzw.content.units.bullets.EndContinuousLaserBulletType;
import zzw.content.units.bullets.EndPointBlastLaserBulletType;
import zzw.content.units.bullets.EndRailBulletType;
import zzw.content.units.bullets.EndSweepLaser;
import zzw.content.units.bullets.OppressionLaserBulletType;
import zzw.content.units.bullets.SlowLightningBulletType;
import zzw.content.units.bullets.TimeStopBulletType;
import zzw.content.units.bullets.VoidAreaBulletType;
import zzw.content.units.bullets.VoidFractureBulletType;
import zzw.content.units.bullets.VoidPelletBulletType;
import zzw.content.units.bullets.VoidPortalBulletType;
import zzw.content.units.effects.ChargeEffect;
import zzw.content.units.effects.HitEffect;
import zzw.content.units.effects.WormDecal;
import zzw.content.units.entities.EndLegsUnit;
import zzw.content.units.entities.EndGroundUnit;
import zzw.content.units.entities.SegmentUnitEntity;
import zzw.content.units.entities.SegmentWormEntity;
import zzw.content.units.entities.SlowLightningEntity;
import zzw.content.units.weapons.EnergyChargeWeapon;
import zzw.content.units.weapons.SweepWeapon;

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
        toxobyteSegment,       // 段身
        catenapede,            // 头部 (PU132 Catenapede, 15 段)
        catenapedeSegment,     // 段身
        devourer,              // 头部 (PU132 Devourer, 60 段)
        devourerSegment,       // 段身
        oppression,            // 头部 (PU132 Oppression, 55 段)
        oppressionSegment,     // 段身
        enigma,                // PU132 谜团 (End 阵营飞行单位)
        voidVessel,            // PU132 虚空容器 (End 阵营飞行单位)
        chronos,               // PU132 克罗诺斯 (End 阵营飞行单位, 时间停止)
        opticaecus,            // PU132 盲视者 (End 阵营飞行单位, 隐身+激光+导弹)
        ravager,               // PU132 掠夺者 (End 阵营地面单位, 8腿+噩梦激光)
        exowalker,             // PU132 exowalker (Plague 阵营地面单位, 8腿+瘟疫导弹+吸血激光)
        toxoswarmer,           // PU132 toxoswarmer (Plague 阵营地面单位, 6腿+火焰导弹)
        desolation;            // PU132 desolation (End 阵营地面单位, 8腿+触手+蓄力主炮)

    public static void load() {
        // ★ 关键: 注册自定义 Entity 到 EntityMapping.idMap, 否则 v154.3 的 UnitType.init() 会失败 ★
        // v154.3 要求每个自定义 Entity class 有唯一 classId, 必须在 idMap 占一个空 slot
        // (模仿 PU132 的 UnityEntityMapping.register)
        ZEntityRegister.register(SegmentWormEntity.class, SegmentWormEntity::new);
        ZEntityRegister.register(SegmentUnitEntity.class, SegmentUnitEntity::new);
        // ★ 注册 EndGroundUnit (End 阵营腿单位防作弊类, extends LegsUnit)
        ZEntityRegister.register(EndGroundUnit.class, EndGroundUnit::new);
        // ★ 注册 SlowLightningEntity (慢闪电 Entity, 实现 Drawc 接口)
        SlowLightningEntity.register();

        // —— 段身 UnitType (先创建, 头部要引用它) ——
        arcnelidiaSegment = new UnitType("arcnelidia-segment") {{
            health = 1600;  // ★ 提高段身血量 (头部800×2), 避免段身太脆
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

            // ★ 段身不计入单位上限 (PU132 WormSegmentUnit.isCounted 返回 false)
            // v154.3 用 useUnitCap=false 而非 isCounted (旧版字段)
            // 这样段身不占核心单位数量上限, 只有头部占上限
            useUnitCap = false;

            // ★ 段身关闭物理碰撞 (physics=false), 避免撞墙时被弹开导致尾部乱甩
            // 段身位置完全由头部 syncToHead 控制, 不应受物理系统影响
            // 其他单位是否能穿过段身由 collides() 决定 (双向)
            physics = false;
            hittable = true;

            // ===== 段身武器: BombBullet (PU132 第3039-3045行, 匿名武器无贴图) =====
            // PU132 原版: 段身武器是匿名的 new Weapon(){{...}}, 没有 name, 不加载炮台贴图
            // 电弧虫段身投弹: splashDamage=25, 爆炸色同电弧
            weapons.add(new Weapon() {{
                x = 0f;
                rotate = true;
                mirror = false;
                reload = 72f;  // 60 * 1.2, 攻击频率减少一点点
                rotateSpeed = 50f;
                shootCone = 180f;
                bullet = new mindustry.entities.bullet.BombBulletType(27f, 250f) {{  // 25 + 250
                    width = 10f;
                    height = 14f;
                    hitEffect = mindustry.content.Fx.flakExplosion;
                    shootEffect = mindustry.content.Fx.none;
                    smokeEffect = mindustry.content.Fx.none;
                    collidesAir = false;
                    collidesGround = true;
                    splashDamage = 250f;  // 25 + 225
                    splashDamageRadius = 25f;
                    status = mindustry.content.StatusEffects.blasted;
                    statusDuration = 60f;
                }};
            }});
        }};

        // —— 头部 Arcnelidia 飞行分段虫子 ——
        arcnelidia = new UnitType("arcnelidia") {{
            // ===== 基础属性 (PU132 原值) =====
            health = 800;  // PU132 原版
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
            // ★ PU132 原版 faceTarget=false: 单位不盯着目标, 而是朝飞行方向
            //   配合 circleTarget=false + moveTo(target, range*0.8f), 单位直线冲过目标再折返
            //   这样整段身体都能发挥作用 (段身投弹/激光)
            //   激光武器有 minShootVelocity=2.1f, 必须移动才会发射
            faceTarget = false;
            // ★ arcnelidia 关闭原版 wobble (振幅 0.05f 太大), 用自定义 wobbleEnabled (振幅 0.02f)
            wobble = false;
            // ★ drag 用飞行单位合理值 (v154.3 默认 0.3f 对飞行单位太大, 速度衰减太快显得僵硬)
            // 原版飞行单位 drag 通常 0.012f ~ 0.18f
            drag = 0.018f;

            // 用自定义 Entity (SegmentWormEntity)
            constructor = SegmentWormEntity::create;
            // ★ 使用 WormAI (待机静止, 不自动朝 spawn 移动)
            // v154.3: defaultController 改名为 aiController
            controller = unit -> new zzw.content.units.ai.WormAI();

            // ===== 头部武器: 双激光 (PU132 原配置) =====
            // PU132 UnityUnitTypes.java 第3024-3037行: 匿名武器, 无炮台贴图
            weapons.add(new Weapon() {{
                x = 0f;
                reload = 10f;
                rotateSpeed = 50f;
                // shootSound 在后面用反射设置
                mirror = true;
                rotate = false;  // ★ 锁定朝向, 不独立旋转
                minShootVelocity = 2.1f;
                bullet = new LaserBulletType(450f) {{  // 200 + 250
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
        // PU132 原版 segmentLength=9, segmentOffset=23f
        // 段间距 22.7f (PU132 23f - 0.3f, 用户要求稍小一点)
        // wobble=true (arcnelidia 轻微晃动)
        // angleLimit=30f (龙的感觉: 更大的弯曲角度)
        // anglePhysicsSmooth=0.5f (更平滑的转向, 段身自然跟随头部)
        // segmentCast=6, jointStrength=0.6f (增大传播范围, 减小关节强度防止脱节)
        SegmentWormEntity.configs.put(arcnelidia.name,
            new SegmentWormEntity.SegmentConfig(arcnelidiaSegment, 9, 22.7f, 0f, 0, true, false, false,
                30f, 6f, 0.1f, 0.6f, 6, 0.5f, false, 0f));
        // 电弧虫: 每秒回10血
        SegmentWormEntity.configs.get(arcnelidia.name).healPerSecond = 10f;

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
        // PU132: visualElevation=0.8f (v154.3 已移除该字段, 静默忽略)
        try {
            java.lang.reflect.Field ve = arcnelidia.getClass().getSuperclass().getField("visualElevation");
            ve.setFloat(arcnelidia, 0.8f);
        } catch (Throwable ignored) {}

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
            health = 400f;  // ★ 提高段身血量 (头部200×2), 避免段身太脆
            speed = 0f;
            // ★ hitSize=14.2f (用户指定)
            hitSize = 14.2f;
            flying = true;
            rotateSpeed = 1f;
            faceTarget = false;
            constructor = SegmentUnitEntity::create;
            hidden = true;
            // ★ 段身不计入单位上限 (PU132 WormSegmentUnit.isCounted 返回 false)
            // v154.3 用 useUnitCap=false 而非 isCounted (旧版字段)
            useUnitCap = false;
            // ★ 段身关闭物理碰撞, 避免撞墙尾部乱甩 (位置由头部控制)
            physics = false;
            hittable = true;
            // ★ 关闭 wobble (飞行单位默认会小幅晃动, PU132 原版是静止的)
            wobble = false;

            // ===== 段身武器: ArtilleryBullet (PU132 第3269-3281行, 匿名武器无贴图) =====
            // PU132 原版: 段身武器是匿名的, 没有 name, 不加载炮台贴图
            // 瘟疫炮弹: splashDamage=25, splashDamageRadius=25, 瘟疫色
            weapons.add(new Weapon() {{
                rotate = true;
                mirror = false;
                reload = 60f;
                shootCone = 90f;
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
            health = 200f;  // PU132 原版
            speed = 3f;
            accel = 0.035f;
            rotateSpeed = 3f;       // PU132 未设, 用默认
            // ★ hitSize=14.75f (用户指定)
            hitSize = 14.75f;
            flying = true;
            engineSize = -1f;
            range = 130f;           // PU132 武器 length=130
            faceTarget = false;
            // ★ 关闭 wobble (PU132 原版 toxobyte 静止时不晃动)
            wobble = false;
            // ★ drag 用飞行单位合理值 (v154.3 默认 0.3f 对飞行单位太大, 速度衰减太快显得僵硬)
            // 原版飞行单位 drag 通常 0.012f ~ 0.18f
            drag = 0.025f;
            // ★ PU132: circleTarget=true, omniMovement=false
            // 154.3 FlyingAI 内置 circleAttack, 设 circleTarget=true 即自动环绕
            circleTarget = true;
            omniMovement = false;
            constructor = SegmentWormEntity::create;
            // ★ 使用 WormAI (待机静止, 不自动朝 spawn 移动)
            // v154.3: defaultController 改名为 aiController
            controller = unit -> new zzw.content.units.ai.WormAI();

            // ===== 头部武器: 12 发散 SapBullet (PU132 第3250-3267行) =====
            // 瘟疫激光: 12 发同时散射, SapBullet 自动回血
            // PU132 原版: 匿名武器, 无炮台贴图
            // ★ v154.3: shots/shotDelay 在 shoot (ShootPattern) 字段里, 不在 Weapon 上
            weapons.add(new Weapon() {{
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
        // PU132: splittable=true (段身有独立血量, 死亡时虫子分裂)
        // ★ chainable=true (用户要求实现链式合并, 两条虫子靠近时合并)
        // ★ angleLimit=30f (龙的感觉: 更大的弯曲角度)
        // ★ segmentDamageScl=3f (用户要求 3x, 段身受击时血量×3倍掉)
        // anglePhysicsSmooth=0.5f (更平滑的转向)
        // segmentCast=8, jointStrength=0.5f (增大传播范围, 减小关节强度防止脱臼)
        // ★ regenTime 改为6秒: 每6秒生长一节
        SegmentWormEntity.configs.put(toxobyte.name,
            new SegmentWormEntity.SegmentConfig(toxobyteSegment, 25, 16.25f, 6f * 60f, 25, false, true, true,
                30f, 3f, 0.1f, 0.5f, 8, 0.5f, false, 0f));
        // toxobyte: 每秒回15血
        SegmentWormEntity.configs.get(toxobyte.name).healPerSecond = 15f;

        // ═══════════════════════════════════════════════════════════
        //  Catenapede (PU132 吸血虫)
        //  - 15 段 (segmentLength=15)
        //  - segmentOffset=31f, hitSize=30f
        //  - angleLimit=25f, lowAltitude=true
        //  - splittable=true, chainable=true (分裂/合并)
        //  - segmentDamageScl=12f (段身更脆, 原版值)
        //  - healthDistribution=0.15f (血量分布)
        // ═══════════════════════════════════════════════════════════

        // —— 段身 UnitType ——
        catenapedeSegment = new UnitType("catenapede-segment") {{
            health = 1500f;  // ★ 提高段身血量 (头部750×2), 避免段身太脆
            speed = 0f;
            hitSize = 28f;
            armor = 5f;
            flying = true;
            rotateSpeed = 1f;
            faceTarget = false;
            constructor = SegmentUnitEntity::create;
            hidden = true;
            useUnitCap = false;
            physics = false;
            hittable = true;
            wobble = false;

            // ===== 段身武器: plagueMissile (PU132 第3329-3345行) =====
            // 双武器交替发射瘟疫导弹
            weapons.add(new Weapon("create-catenapede-segment-missile") {{
                y = -8f;
                x = 14.75f;
                rotate = true;
                reload = 25f;
                bullet = new mindustry.entities.bullet.MissileBulletType(3.8f, 12f) {{
                    width = height = 8f;
                    backColor = hitColor = lightColor = trailColor = Color.valueOf("54de3b");
                    frontColor = Color.valueOf("a3f080");
                    shrinkY = 0f;
                    drag = -0.01f;
                    splashDamage = 30f;
                    splashDamageRadius = 35f;
                    hitEffect = mindustry.content.Fx.blastExplosion;
                    despawnEffect = mindustry.content.Fx.blastExplosion;
                }};
            }});
            weapons.add(new Weapon("create-catenapede-segment-missile-2") {{
                y = -12.5f;
                x = 7.25f;
                rotate = true;
                reload = 15f;
                bullet = new mindustry.entities.bullet.MissileBulletType(3.8f, 12f) {{
                    width = height = 8f;
                    backColor = hitColor = lightColor = trailColor = Color.valueOf("54de3b");
                    frontColor = Color.valueOf("a3f080");
                    shrinkY = 0f;
                    drag = -0.01f;
                    splashDamage = 30f;
                    splashDamageRadius = 35f;
                    hitEffect = mindustry.content.Fx.blastExplosion;
                    despawnEffect = mindustry.content.Fx.blastExplosion;
                }};
            }});
        }};

        // —— 头部 Catenapede ——
        catenapede = new UnitType("catenapede") {{
            // ===== 基础属性 (PU132 UnityUnitTypes.java 第3284-3306行) =====
            health = 750f;  // PU132 原版
            speed = 2.4f;
            accel = 0.06f;
            drag = 0.03f;
            hitSize = 30f;
            armor = 5f;
            flying = true;
            engineSize = -1f;
            range = 160f;
            faceTarget = false;
            wobble = false;
            // ★ PU132: circleTarget=true, omniMovement=false
            circleTarget = true;
            omniMovement = false;
            // ★ PU132: lowAltitude=true (渲染层级更低, 在 Layer.flyingUnitLow)
            lowAltitude = true;
            // ★ PU132: rotateSpeed=2.7f, angleLimit=25f
            rotateSpeed = 2.7f;
            constructor = SegmentWormEntity::create;
            controller = unit -> new zzw.content.units.ai.WormAI();

            // ===== 头部武器: PointDrainLaser (PU132 第3309-3327行) =====
            // 吸血激光: 持续发射, 吸血 0.5%, 最大长度 160, 击退 -34 (拉向自己)
            weapons.add(new Weapon("create-catenapede-drain-laser") {{
                y = -9f;
                x = 14f;
                shootY = 6.75f;
                rotateSpeed = 5f;
                reload = 5f * 60f;
                shootCone = 45f;
                rotate = true;
                continuous = true;
                alternate = false;
                minShootVelocity = 0.01f;
                bullet = new zzw.content.units.bullets.PointDrainLaserBulletType(45f) {{
                    maxLength = 160f;
                    drainPercent = 0.5f;
                    width = 6f;
                    area = 9f;
                    knockback = -34f;
                    backColor = Color.valueOf("54de3b");
                    frontColor = Color.valueOf("a3f080");
                    // ★ 吸血激光持续时长 +3.5秒 (10s → 13.5s)
                    lifetime = 13.5f * 60f;
                }};
            }});
        }};

        // ★ 注册 catenapede 段身配置 ★
        // PU132: segmentLength=2 (初始只生成2段), segmentOffset=31f
        // PU132: regenTime=30*60f (每30秒长一节), maxSegments=15 (最多15段)
        // PU132: splittable=true, chainable=true
        // PU132: segmentDamageScl=12f (段身受击时血量×12倍掉)
        // PU132: healthDistribution=0.15f (血量分布速率)
        // angleLimit=30f (龙的感觉: 更大的弯曲角度)
        // anglePhysicsSmooth=0.5f (更平滑的转向)
        // segmentCast=8, jointStrength=0.5f (增大传播范围, 减小关节强度防止脱节)
        // ★ regenTime 改为20秒: 每20秒生长一节
        SegmentWormEntity.configs.put(catenapede.name,
            new SegmentWormEntity.SegmentConfig(catenapedeSegment, 2, 31f, 20f * 60f, 15, false, true, true,
                30f, 5f, 0.15f, 0.5f, 8, 0.5f, false, 0f));
        // 吸血虫: 每秒回25血
        SegmentWormEntity.configs.get(catenapede.name).healPerSecond = 25f;

        // ===== Devourer (PU132 devourer-of-eldrich-gods) =====
        // End 阵营超级虫子, 60段, 全免疫, 头部激光+段身多种武器

        // ★ Devourer 段身 ★
        devourerSegment = new UnitType("devourer-segment") {{
            health = 2500000f;  // ★ 提高段身血量 (头部1250000×2), 避免段身太脆
            speed = 0f;
            hitSize = 52f;  // 原 40f + 12 (用户要求加大12)
            armor = 16f;  // ★ 提高护甲与头部一致 (原8f)
            flying = true;
            rotateSpeed = 1f;
            faceTarget = false;
            wobble = false;

            constructor = SegmentUnitEntity::create;
            hidden = true;
            useUnitCap = false;
            physics = false;
            hittable = true;

            // 段身武器1: 导弹发射器 (PU132 unity-doeg-launcher, 8连发)
            weapons.add(new Weapon("create-devourer-segment-missile") {{
                x = 19f;
                y = 0f;
                shootY = 8f;
                mirror = true;
                rotate = true;
                reload = 1.2f * 60f;
                inaccuracy = 1.4f;
                // PU132 原版: shots=8, shotDelay=3f, xRand=12f
                shoot.shots = 8;
                shoot.shotDelay = 3f;
                xRand = 12f;

                bullet = new EndBasicBulletType(6f, 100f, "missile") {{
                    width = 9f;
                    height = 11f;
                    shrinkY = 0f;
                    splashDamage = 90f;
                    splashDamageRadius = 45f;
                    homingPower = 0.08f;
                    lifetime = 52f;
                    trailChance = 0.2f;
                    weaveMag = 18f;
                    weaveScale = 1.6f;
                    // PU132 原版: backColor=scarColor(#f53036), frontColor=endColor(#ff786e)
                    backColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("ff786e");
                }};
            }});

            // 段身武器2: 毁灭者 (PU132 unity-doeg-destroyer, 6连发)
            weapons.add(new Weapon("create-devourer-segment-destroyer") {{
                mirror = true;
                ignoreRotation = true;
                rotate = true;
                x = 22f;
                y = -15.75f;
                shootY = 12f;
                reload = 1.5f * 60f;
                inaccuracy = 1.4f;
                // PU132 原版: shots=6, shotDelay=4f
                shoot.shots = 6;
                shoot.shotDelay = 4f;

                bullet = new EndBasicBulletType(9.2f, 325f) {{
                    hitSize = 8f;
                    shrinkY = 0f;
                    width = 19f;
                    height = 25f;
                    // PU132 原版: backColor=scarColor(#f53036), frontColor=endColor(#ff786e)
                    backColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("ff786e");
                }};
            }});

            // 段身武器3: 小型激光 (PU132 unity-doeg-small-laser)
            weapons.add(new Weapon("create-devourer-segment-small-laser") {{
                mirror = true;
                alternate = false;
                rotate = true;
                x = 17.5f;
                y = 16.5f;
                reload = 2f * 60f;
                continuous = true;

                bullet = new EndContinuousLaserBulletType(85f) {{
                    lifetime = 2f * 60f;
                    length = 230f;
                    strokes = new float[]{2f * 0.4f, 1.5f * 0.4f, 1f * 0.4f, 0.3f * 0.4f};
                    // PU132 原版: scarColorAlpha, scarColor, endColor, white
                    colors = new Color[]{Color.valueOf("f5303690"), Color.valueOf("f53036"), Color.valueOf("ff786e"), Color.white};
                    lightColor = lightningColor = hitColor = Color.valueOf("f53036");
                    width = 9f;
                }};
            }});
        }};

        // ★ Devourer 头部 ★
        devourer = new UnitType("devourer") {{
            health = 1250000f;  // PU132 原版
            flying = true;
            speed = 5f;
            accel = 0.12f;
            drag = 0.1f;
            hitSize = 39f * 1.55f;
            engineSize = -1f;
            lowAltitude = true;
            rotateSpeed = 2.2f;
            armor = 16f;
            range = 480f;
            outlineColor = Color.valueOf("282828");
            faceTarget = false;
            wobble = false;
            circleTarget = true;
            omniMovement = false;
            envEnabled = mindustry.world.meta.Env.terrestrial | mindustry.world.meta.Env.space;
            immunities.addAll(mindustry.Vars.content.getBy(mindustry.ctype.ContentType.status));

            constructor = SegmentWormEntity::create;
            controller = unit -> new zzw.content.units.ai.WormAI();

            // 头部武器1: 主激光 (PU132 UnityBullets.endLaser, 完整移植)
            weapons.add(new Weapon("create-devourer-main-laser") {{
                x = 0f;
                y = 23f;
                mirror = false;
                ignoreRotation = true;
                reload = 15f * 60f;
                continuous = true;
                shake = 4f;
                shoot.firstShotDelay = 41f;
                // ★ 大激光方向固定: rotate=false → 激光方向=unit.rotation+baseRotation (固定)
                //   shootCone=360f 确保任何角度都能发射 (与 oppression 主激光一致)
                rotate = false;
                shootCone = 360f;

                bullet = new EndContinuousLaserBulletType(2650f) {{  // 2400 + 250
                    length = 340f;
                    lifetime = 5f * 60f;
                    // PU132 原版颜色: scarColorAlpha(#f5303690), scarColor(#f53036), endColor(#ff786e), white
                    colors = new Color[]{Color.valueOf("f5303690"), Color.valueOf("f53036"), Color.valueOf("ff786e"), Color.white};
                    lightColor = lightningColor = hitColor = Color.valueOf("f53036");
                    lightningChance = 0.8f;
                    lightningDamage = 80f;
                    lightningLength = 42;
                    lightningLengthRand = 5;
                    width = 15f;
                    chargeEffect = ChargeEffect.devourerCharge;
                    // 防作弊参数 (PU132 devourer 主激光配置)
                    ratioStart = 100000f;
                    ratioDamage = 1f / 60f;
                    overDamage = 650000f;
                    overDamagePower = 2.7f;
                    bleedDuration = 10f * 60f;
                    pierceShields = true;
                    modules = new AntiCheatBulletModule[]{new ArmorDamageModule(0f, 12f, 30f, 20f)};
                }};
            }});

            // 头部武器2: 毁灭者 (PU132 EndBasicBulletType, 6连发)
            weapons.add(new Weapon("create-devourer-destroyer") {{
                x = 19.25f;
                y = -22.75f;
                shootY = 12f;
                mirror = true;
                ignoreRotation = true;
                rotate = true;
                reload = 1.5f * 60f;
                inaccuracy = 1.4f;
                // PU132 原版: shots=6, shotDelay=4f
                shoot.shots = 6;
                shoot.shotDelay = 4f;

                bullet = new EndBasicBulletType(9.2f, 325f) {{
                    hitSize = 8f;
                    shrinkY = 0f;
                    width = 19f;
                    height = 25f;
                    // PU132 原版: backColor=scarColor(#f53036), frontColor=endColor(#ff786e)
                    backColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("ff786e");
                }};
            }});
        }};

        // ★ 注册 devourer 段身配置 ★
        // PU132: segmentLength=60, segmentOffset=(41f*1.55)+7f ≈ 70.55f
        // PU132: splittable=false, chainable=false (不可分裂合并)
        // PU132: 无 regen (初始就60段)
        // PU132 原版: segmentCast=7, anglePhysicsSmooth=0.5f, jointStrength=1f, preventDrifting=true, headOffset=27.75f
        SegmentWormEntity.configs.put(devourer.name,
            new SegmentWormEntity.SegmentConfig(devourerSegment, 60, 70.55f, 0f, 60, false, false, false,
                30f, 6f, 0.1f, 1f, 7, 0.5f, true, 27.75f, 240f));
        // 吞噬者: 每秒回120血
        SegmentWormEntity.configs.get(devourer.name).healPerSecond = 120f;
        // 吞噬者: 受到伤害 × 0.9 (减伤10%)
        SegmentWormEntity.configs.get(devourer.name).damageMultiplier = 0.9f;

        // ★ 初始化分裂/合并音效 (PU132 默认 Sounds.door)
        // ★ v158 可能移除了 Sounds.door 字段, 用反射安全获取
        try {
            arc.audio.Sound doorSound = (arc.audio.Sound) mindustry.gen.Sounds.class.getField("door").get(null);
            SegmentWormEntity.splitSound = doorSound;
            SegmentWormEntity.chainSound = doorSound;
        } catch (Throwable t) {
            // v158 无 Sounds.door, 用自定义音效替代
            SegmentWormEntity.splitSound = zzw.content.Z_Sounds.endBasic;
            SegmentWormEntity.chainSound = zzw.content.Z_Sounds.endBasic;
            arc.util.Log.info("[Z_Units] Sounds.door 不存在, 用 endBasic 替代分裂音效");
        }

        // ═══════════════════════════════════════════════════════════
        //  Oppression (PU132 压迫者)
        //  - 55 段 (segmentLength=55)
        //  - segmentOffset=228f (114f*2f), hitSize=218f (114*2-10)
        //  - angleLimit=35f, barrageRange=490f
        //  - segmentCast=11, anglePhysicsSmooth=0.5f, jointStrength=1f
        //  - preventDrifting=true, lowAltitude=true
        //  - immuneAll=true (免疫所有状态效果)
        //  - 阶段1: 空壳 (基础属性+段身配置, 无武器)
        //  - 后续阶段逐步添加武器和特效
        // ═══════════════════════════════════════════════════════════

        // —— 段身 UnitType ——
        oppressionSegment = new UnitType("oppression-segment") {{
            // PU132: 段身血量由头部 healthDistribution 分配, 这里设基础值
            health = 5000000f;  // ★ 提高段身血量 (头部2500000×2), 避免段身太脆
            speed = 0f;
            // ★ hitSize=180 (用户指定)
            hitSize = 180f;
            armor = 30f;  // ★ 提高护甲与头部一致 (原10f)
            flying = true;
            rotateSpeed = 1f;
            faceTarget = false;
            wobble = false;

            constructor = SegmentUnitEntity::create;
            hidden = true;
            useUnitCap = false;
            physics = false;
            hittable = true;

            // 阶段1: 段身无武器, 后续阶段添加

            // ===== 阶段2: 段身武器组 (PU132 segmentWeapons, 4组每组2个) =====
            // PU132 第4127-4292行: segmentWeapons 是 Seq<Weapon>[]
            // 这里按 PU132 分组添加到段身 weapons

            // —— 段身武器组1: soul-destroyer + destroyer-2 ——
            // soul-destroyer: 轨道穿透激光 (PU132 EndRailBulletType, 完整移植)
            // PU132 第4129-4162行: damage=15000, length=850, pierceDamageFactor=0.001
            // ★ 贴图名称是 oppression-soul-destroyer.png, 不带 create- 前缀
            weapons.add(new Weapon("oppression-soul-destroyer") {{
                mirror = false;
                x = 0f;
                y = 72f;
                shootY = 0f;
                rotate = true;
                rotateSpeed = 1.5f;
                reload = 7.125f * 60f;  // 4.75 * 1.5, 降低攻击频率

                bullet = new EndRailBulletType() {{
                    damage = 15000f;
                    length = 850f;
                    collisionWidth = 4f;
                    // PU132: pierceDamageFactor=0.001 几乎不衰减穿透
                    pierceDamageFactor = 0.001f;
                    // 防作弊参数 (PU132 EndRail 原版配置)
                    ratioStart = 100000f;
                    ratioDamage = 1f / 60f;
                    overDamage = 650000f;
                    overDamagePower = 2.7f;
                    bleedDuration = 10f * 60f;
                    pierceShields = true;
                    modules = new AntiCheatBulletModule[]{new ArmorDamageModule(0f, 12f, 30f, 20f)};
                }};
            }});

            // destroyer-2: 连续激光 (PU132 EndContinuousLaserBulletType, 550伤害)
            // PU132 第4163-4191行: damage=550, length=370, strokes*0.7
            weapons.add(new Weapon("oppression-destroyer-2") {{
                x = 98f;
                y = -26.25f;
                shootY = 22f;
                shootCone = 0.5f;
                alternate = false;
                rotate = true;
                rotateSpeed = 1.75f;
                continuous = true;
                reload = 5.25f * 60f;  // 3.5 * 1.5, 降低攻击频率

                bullet = new EndContinuousLaserBulletType(550f) {{
                    lifetime = 1.5f * 60f;
                    length = 370f;
                    // PU132: strokes[i] *= 0.7f
                    strokes = new float[]{2f * 0.7f, 1.5f * 0.7f, 1f * 0.7f, 0.3f * 0.7f};
                    // PU132 原版颜色: scarColorAlpha, scarColor, endColor, white
                    colors = new Color[]{
                        Color.valueOf("f5303690"),
                        Color.valueOf("f53036"),
                        Color.valueOf("ff786e"),
                        Color.white
                    };
                    lightColor = Color.valueOf("f53036");
                }};
            }});

            // —— 段身武器组2: oppressor + destroyer-3 ——
            // oppressor: 扫射激光 (PU132 SweepWeapon + EndSweepLaser, 完整移植)
            // PU132 第4194-4224行: damage=7000, length=850, width=25, lifetime=130
            weapons.add(new SweepWeapon("oppression-oppressor") {{
                mirror = false;
                x = 0f;
                y = 72f;
                shootY = 21f;
                rotateSpeed = 2f;
                // SweepWeapon 构造函数已设 rotate=true, continuous=true
                // PU132: sweepTime=120, sweepAngle=60
                sweepTime = 120f;
                sweepAngle = 60f;
                reload = 9f * 60f;  // 降低攻击频率 (黑圆生成频率降低)

                bullet = new EndSweepLaser(7000f) {{
                    lifetime = 130f;
                    length = 850f;
                    overDamage = 640000f;
                    overDamagePower = 2.7f;
                    overDamageScl = 4000f;
                    width = 25f;
                    widthLoss = 0.7f;
                    // PU132: collisionWidth = (width / 2f) * widthLoss = 8.75
                    collisionWidth = (width / 2f) * widthLoss;
                    distance = 350f;  // PU132 原版 220, 调大间距避免黑圆太密
                    ratioStart = 14000f;
                    ratioDamage = 1f / 10f;
                    hitEffect = HitEffect.endHitRedBig;
                    pierce = true;
                    pierceCap = 3;
                    // ★ hitBullet: 黑色圆形虚空区域 (PU132 UnityBullets.oppressionArea, L1220-1233)
                    //   EndSweepLaser 命中时每隔 distance 距离生成一次
                    hitBullet = new VoidAreaBulletType(95f) {{
                        lifetime = 5f * 60f;
                        bleedDuration = 30f;
                        ratioDamage = 1f / 200f;
                        ratioStart = 600000f;
                        // PU132 用自定义 weaken 状态, v158 无此状态故省略 (核心伤害机制不受影响)
                        radius = 120f;
                    }};
                }};
            }});

            // destroyer-3: 导弹 (PU132 missileAntiCheat, 13连发)
            weapons.add(new Weapon("oppression-destroyer-3") {{
                x = 98f;
                y = -26.25f;
                shootY = 6f;
                rotate = true;
                rotateSpeed = 4f;
                inaccuracy = 3f;
                xRand = 10.25f;
                reload = 4.5f * 60f;  // 3 * 1.5, 降低攻击频率
                // PU132 原版: shots=13, shotDelay=5f
                shoot.shots = 13;
                shoot.shotDelay = 5f;

                bullet = new EndBasicBulletType(4f, 330f, "missile") {{
                    width = 12f;
                    height = 12f;
                    shrinkY = 0f;
                    drag = -0.013f;
                    lifetime = 90f;
                    splashDamage = 220f;
                    splashDamageRadius = 45f;
                    homingPower = 0.08f;
                    trailChance = 0.2f;
                    weaveMag = 1f;
                    weaveScale = 6f;
                    // PU132 原版颜色: scarColor, endColor
                    backColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("ff786e");
                }};
            }});

            // —— 段身武器组3: void + destroyer-4 ——
            // void: 虚空门户 (PU132 VoidPortalBulletType, 完整移植触手+区域伤害)
            // PU132 第4249-4270行: damage=1300, length=800, bleedDuration=180
            weapons.add(new Weapon("oppression-void") {{
                mirror = false;
                x = 0f;
                y = 72f;
                shootY = 21f;
                rotate = true;
                rotateSpeed = 1.3f;
                reload = 13.5f * 60f;  // 降低攻击频率 (虚空门户菱形)

                bullet = new VoidPortalBulletType(1300f) {{
                    // PU132 原版参数
                    length = 800f;
                    width = 95f;
                    lifetime = 4f * 60f;
                    // 防作弊参数 (PU132 VoidPortal 原版配置)
                    ratioStart = 100000f;
                    ratioDamage = 1f / 60f;
                    overDamage = 650000f;
                    overDamagePower = 2.7f;
                    bleedDuration = 10f * 60f;
                    pierceShields = true;
                    modules = new AntiCheatBulletModule[]{new ArmorDamageModule(0f, 12f, 30f, 20f)};
                }};
            }});

            // destroyer-4: 快闪电 (PU132 SlowLightningBulletType 移植, 高伤害短持续)
            // PU132 第4272-4289行: damage=120, 5发, inaccuracy=15, range=870
            // ★ 快闪电优化: 伤害×6.67, 持续时间大幅缩短, 节点间距增大, 分裂减少
            weapons.add(new Weapon("oppression-destroyer-4") {{
                x = 98f;
                y = -26.25f;
                shootY = 17.5f;
                rotate = true;
                rotateSpeed = 3f;
                inaccuracy = 15f;
                xRand = 6f;
                reload = 6f * 60f;
                shoot.shots = 5;

                bullet = new SlowLightningBulletType(800f);
            }});

            // —— 段身武器组4: 空 (PU132 第4291行 new Seq<Weapon>()) ——
        }};

        // —— 头部 Oppression ——
        oppression = new UnitType("oppression") {{
            // ===== 基础属性 (PU132 UnityUnitTypes.java 第4055-4095行) =====
            health = 2500000f;  // PU132 原版
            flying = true;
            speed = 4.5f;  // PU132 原版
            accel = 0.13f;
            drag = 0.12f;
            // PU132: hitSize=(114*2)-10=218f
            hitSize = 218f;
            // PU132: circleTarget=true, omniMovement=false
            circleTarget = true;
            omniMovement = false;
            // PU132: lowAltitude=true (渲染层级更低)
            lowAltitude = true;
            rotateSpeed = 2.2f;
            engineSize = -1f;
            armor = 30f;
            // PU132: angleLimit=35f
            // PU132: outlineColor=UnityPal.darkerOutline (#2e3142)
            outlineColor = Color.valueOf("2e3142");
            // PU132: envEnabled=terrestrial|space
            envEnabled = mindustry.world.meta.Env.terrestrial | mindustry.world.meta.Env.space;
            // PU132: immuneAll=true (免疫所有状态效果)
            immunities.addAll(mindustry.Vars.content.getBy(mindustry.ctype.ContentType.status));

            constructor = SegmentWormEntity::create;
            controller = unit -> new zzw.content.units.ai.WormAI();
            // range 用最大武器射程, 后续添加武器后更新
            range = 850f;

            // 阶段1: 无武器, 后续阶段逐步添加
            // 阶段2: destroyer-1, destroyer-3
            // 阶段3: destroyer-2, soul-destroyer
            // 阶段4: 主激光, oppressor, void, destroyer-4

            // ===== 阶段4: 头部主激光 (PU132 OppressionLaserBulletType, 完整移植7层渲染) =====
            // PU132 第4097-4108行: damage=9000, length=2150, width=140, lifetime=8*60
            weapons.add(new Weapon() {{
                x = 0f;
                y = 0f;
                shootY = 47.25f;
                mirror = false;
                continuous = true;
                // ★ 大激光方向固定: rotate=false → mount.rotation=baseRotation(固定)
                //   continuous 武器激光方向 = unit.rotation + baseRotation
                //   oppression 是 omniMovement=false, WormAI.attack() 只 moveAt 不改 rotation
                //   → unit.rotation 不变 → 激光方向固定
                //   shootCone=360f 确保任何角度都能发射 (绕过 shootCone 检查)
                rotate = false;
                shootCone = 360f;
                reload = 25f * 60f;
                shoot.firstShotDelay = ChargeEffect.oppressionCharge.lifetime;
                // ★ 蓄力特效跟随单位移动和旋转
                parentizeEffects = true;

                bullet = new OppressionLaserBulletType();
            }});

            // ===== 阶段2: 头部武器 destroyer-1 (PU132 oppressionShell) =====
            // PU132 第4110-4125行: 5连发炮弹, 穿透3个目标
            weapons.add(new Weapon("create-oppression-destroyer-1") {{
                x = 81.75f;
                y = -71.5f;
                shootY = 9.75f;
                rotate = true;
                rotateSpeed = 1.75f;
                reload = 2.3f * 60f;
                inaccuracy = 2f;
                // PU132 原版: shots=5, shotDelay=6f
                shoot.shots = 5;
                shoot.shotDelay = 6f;

                bullet = new EndBasicBulletType(7f, 410f, "shell") {{
                    lifetime = 95f;
                    splashDamage = 125f;
                    splashDamageRadius = 70f;
                    width = 18f;
                    height = 23f;
                    // PU132 原版颜色: scarColor, endColor
                    backColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("ff786e");
                    // PU132: lightning=5, lightningLength=10
                    lightning = 5;
                    lightningLength = 10;
                    lightningLengthRand = 5;
                    // PU132: pierceCap=3, pierce=pierceBuilding=true
                    pierce = true;
                    pierceCap = 3;
                }};
            }});

            // ===== 阶段4: 头部大激光连发 (PU132 L4999-5051 VoidFractureBulletType, shots=3 连发3个) =====
            // PU132 原版: EnergyChargeWeapon with shots=3, shotDelay=6f, reload=120f, drawCharge=黑色发光圆+尖刺
            // 简化版 EnergyChargeWeapon: shoot/update 走 v158 原生 Weapon, 只重写 draw() 补 drawCharge 蓄力视觉
            // PU132 有 6 个此武器分列身体两侧, 这里简化为 1 个 (mirror=false 单边)
            weapons.add(new EnergyChargeWeapon("create-oppression-void-fracture") {{
                x = 85f;
                y = -50f;
                shadow = 47f;
                // ★ PU132 原版: mirror = false (单边武器, 不是双边镜像)
                mirror = false;
                alternate = true;
                rotate = true;
                rotateSpeed = 2f;
                reload = 120f;
                inaccuracy = 20f;
                shootCone = 7f;
                shootY = 0f;
                velocityRnd = 0.1f;
                // PU132 原版: shots=3, shotDelay=6f (3连发)
                shoot.shots = 3;
                shoot.shotDelay = 6f;
                // ★ PU132 原版: shootSound = UnitySounds.spaceFracture
                shootSound = zzw.content.Z_Sounds.spaceFracture;

                // ★ PU132 原版 drawCharge (UnityUnitTypes.java L5014-5023): 黑色发光圆+尖刺
                // 简化版: 用 v158 原版 Fill.circle + Fill.tri 替代 UnityDrawf.shiningCircle
                //   PU132 参数: radius=3.5f*charge, spikes=6, spikeHeight=3f*charge
                drawCharge = (unit, mount, charge) -> {
                    if (charge <= 0.001f) return;
                    mindustry.type.Weapon w = mount.weapon;
                    float rotation = unit.rotation - 90f;
                    float wx = unit.x + Angles.trnsx(rotation, w.x, w.y);
                    float wy = unit.y + Angles.trnsy(rotation, w.x, w.y);
                    float radius = 3.5f * charge;
                    float spikeHeight = 3f * charge;
                    Draw.color(Color.black);
                    // 中心实心圆 (PU132 shiningCircle 第一步)
                    Fill.circle(wx, wy, radius);
                    // 6个旋转尖刺 (PU132 shiningCircle 第二步, 简化为三角形)
                    if (spikeHeight > 0.01f) {
                        int spikes = 6;
                        float baseAngle = Time.time * 3f;
                        for (int i = 0; i < spikes; i++) {
                            float a = baseAngle + i * (360f / spikes);
                            float x1 = wx + Angles.trnsx(a, radius);
                            float y1 = wy + Angles.trnsy(a, radius);
                            float x2 = wx + Angles.trnsx(a, radius + spikeHeight);
                            float y2 = wy + Angles.trnsy(a, radius + spikeHeight);
                            float x3 = wx + Angles.trnsx(a + 8f, radius);
                            float y3 = wy + Angles.trnsy(a + 8f, radius);
                            Fill.tri(x1, y1, x2, y2, x3, y3);
                            float x4 = wx + Angles.trnsx(a - 8f, radius);
                            float y4 = wy + Angles.trnsy(a - 8f, radius);
                            Fill.tri(x1, y1, x2, y2, x4, y4);
                        }
                    }
                    Draw.color();
                };

                bullet = new VoidFractureBulletType(40f, 800f) {{
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
                    // ★ PU132 原版: activeSound, spikesSound
                    activeSound = zzw.content.Z_Sounds.fractureShoot;
                    spikesSound = zzw.content.Z_Sounds.spaceFracture;
                    modules = new AntiCheatBulletModule[]{
                        new ArmorDamageModule(50f, 50f, 2f),
                        new ForceFieldDamageModule(2f, 20f, 220f, 7f, 1f / 50f, 2f * 60f)
                    };
                }};
            }});
        }};

        // ★ 注册 oppression 段身配置 ★
        // PU132: segmentLength=55, segmentOffset=228f (114*2)
        // PU132: splittable=false, chainable=false (不可分裂合并)
        // PU132: 无 regen (初始就55段)
        // PU132: segmentCast=11, anglePhysicsSmooth=0.5f, jointStrength=1f, preventDrifting=true
        // PU132: angleLimit=35f, barrageRange=490f
        // ★ segmentWeaponGroupSize=2: oppression 段身6个武器分3组 (每组2个), 尾部空组
        // PU132 segmentWeapons = {组0(soul-destroyer+destroyer-2), 组1(oppressor+destroyer-3), 组2(void+destroyer-4), 组3(空)}
        SegmentWormEntity.configs.put(oppression.name,
            new SegmentWormEntity.SegmentConfig(oppressionSegment, 55, 228f, 0f, 55, false, false, false,
                35f, 6f, 0.1f, 1f, 11, 0.5f, true, 0f, 490f, 2, true));
        // 压迫者: 每秒回500血
        SegmentWormEntity.configs.get(oppression.name).healPerSecond = 500f;
        // 压迫者: 受到伤害 × 0.7 (减伤30%)
        SegmentWormEntity.configs.get(oppression.name).damageMultiplier = 0.7f;
        // 压迫者: 大招期间速度倍率 0.12 (只剩12%)
        SegmentWormEntity.configs.get(oppression.name).ultSpeedMultiplier = 0.12f;

        // ★ 初始化 oppression 液压装饰 (WormDecal) ★
        // PU132 UnityUnitTypes 第4064-4073行:
        //   lineWidth=11.5, lineColor=scarColor, baseX=41.25, baseY=40.25
        //   endX=81.75, endY=-71.75, baseOffset=19.5, segments=2
        // ★ 用 head type.name 作为 key 存到 wormDecals Map, 段身 draw 时按头部 name 查找
        // 这样 devourer 等其他单位查不到 WormDecal, 不会误画液压杆
        WormDecal decal = new WormDecal("oppression-hydraulics");
        decal.lineWidth = 11.5f;
        decal.lineColor = Color.valueOf("f53036");
        decal.baseX = 41.25f;
        decal.baseY = 40.25f;
        decal.endX = 81.75f;
        decal.endY = -71.75f;
        decal.baseOffset = 19.5f;
        decal.segments = 2;
        // ★ 不调用 decal.load() ★
        // Z_Units.load() 在 loadContent() 阶段调用, 此时 atlas 还没加载, Core.atlas.find() 返回空贴图
        // WormDecal 有延迟加载机制: 第一次 draw 时检查 loaded 标志, 未加载则调用 load()
        SegmentWormEntity.wormDecals.put(oppression.name, decal);

        // 用反射设置 toxobyte 武器音效 (v154.3 编译期可能找不到字段)
        try {
            Class<?> soundsClass = Class.forName("mindustry.gen.Sounds");
            // 头部 Sap 武器音效
            try {
                java.lang.reflect.Field f = soundsClass.getField("shootSap");
                arc.audio.Sound snd = (arc.audio.Sound) f.get(null);
                toxobyte.weapons.first().shootSound = snd;
            } catch (Throwable ignored) {}
            // 段身榴弹音效
            try {
                java.lang.reflect.Field f = soundsClass.getField("shootArtillery");
                arc.audio.Sound snd = (arc.audio.Sound) f.get(null);
                toxobyteSegment.weapons.first().shootSound = snd;
            } catch (Throwable ignored) {}
            // arcnelidia 段身炸弹音效
            try {
                java.lang.reflect.Field f = soundsClass.getField("shootBomb");
                arc.audio.Sound snd = (arc.audio.Sound) f.get(null);
                arcnelidiaSegment.weapons.first().shootSound = snd;
            } catch (Throwable ignored) {}
            // catenapede 头部吸血激光音效 (PU132: Sounds.respawning)
            try {
                java.lang.reflect.Field f = soundsClass.getField("respawning");
                arc.audio.Sound snd = (arc.audio.Sound) f.get(null);
                catenapede.weapons.first().shootSound = snd;
            } catch (Throwable ignored) {}
            // catenapede 段身导弹音效 (PU132: Sounds.missile)
            try {
                java.lang.reflect.Field f = soundsClass.getField("missile");
                arc.audio.Sound snd = (arc.audio.Sound) f.get(null);
                for (mindustry.type.Weapon w : catenapedeSegment.weapons) {
                    w.shootSound = snd;
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            try { arc.util.Log.err("set toxobyte sounds failed", t); } catch (Throwable ignored) {}
        }

        // ═══════════════════════════════════════════════════════════
        //  Enigma (PU132 谜团)
        //  - 飞行单位, 2000 血, 速度 4
        //  - 武器: VoidPelletBulletType (黑色弹丸, 比例伤害)
        //  - 防作弊: 简化版 (无敌帧+单次上限+抗性递增)
        // ═══════════════════════════════════════════════════════════
        enigma = new UnitType("enigma") {{
            health = 2000f;
            speed = 4f;
            drag = 0.4f;
            accel = 0.5f;
            // ★ PU132 原版: boostMultiplier = 0.5f
            boostMultiplier = 0.5f;
            flying = true;
            lowAltitude = true;
            hitSize = 12f;
            engineOffset = 8f;
            engineSize = 1f;
            armor = 4f;
            rotateSpeed = 5f;
            range = 200f;
            outlineColor = Color.valueOf("2e3142");
            constructor = EndLegsUnit::create;
            // ★ 使用 WormAI (继承 FlyingAI, 完全按 PU132 原版自动索敌+攻击)
            controller = unit -> new zzw.content.units.ai.WormAI();

            weapons.add(new Weapon() {{
                x = 4.25f;
                y = -3.75f;
                rotate = true;
                reload = 4f;
                rotateSpeed = 5f;
                bullet = new VoidPelletBulletType(5.5f, 200f) {{
                    ratioDamage = 1f / 60f;
                    ratioStart = damage * 30f;
                }};
            }});
        }};

        // ═══════════════════════════════════════════════════════════
        //  Void Vessel (PU132 虚空容器)
        //  - 飞行单位, 10000 血, 速度 3
        //  - 武器: VoidFractureBulletType (黑色碎裂弹, +ArmorDamageModule)
        //  - 防作弊: 简化版
        // ═══════════════════════════════════════════════════════════
        voidVessel = new UnitType("void-vessel") {{
            health = 10000f;
            speed = 3f;
            accel = 0.1f;
            drag = 0.03f;
            hitSize = 16f;
            engineOffset = 12.5f;
            engineSize = 1.5f;
            flying = true;
            lowAltitude = true;
            armor = 8f;
            rotateSpeed = 4f;
            range = 300f;
            outlineColor = Color.valueOf("2e3142");
            constructor = EndLegsUnit::create;
            // ★ 使用 WormAI (继承 FlyingAI, 完全按 PU132 原版自动索敌+攻击)
            controller = unit -> new zzw.content.units.ai.WormAI();

            // ===== 大激光武器 (和压迫者一样的 OppressionLaserBulletType, 3连发) =====
            // ★ 用户需求:
            //   - 3发为一组, 组内每发间隔3秒, 组间隔12秒
            //   - 激光跟随单位移动, 新激光发射时旧激光脱节
            //   - 不会自动发射, 目标进入小黑色激光攻击范围时才发射
            //   - 玩家操控时按正常方式发射
            // 时间线: t=0(激光1) → t=3s(激光2) → t=6s(激光3) → t=18s(下一组)
            //   - shots=3, shotDelay=3*60=180, reload=18*60=1080
            //   - 组间隔 = 18 - 6 = 12秒 ✓
            //   - bullet.range=300 限制索敌范围 (小激光射程)
            weapons.add(new Weapon() {{
                x = 0f;
                y = 0f;
                shootY = 8f;
                mirror = false;
                continuous = false;
                rotate = false;
                shootCone = 360f;
                reload = 20f * 60f;  // 20秒CD
                shoot.firstShotDelay = ChargeEffect.oppressionCharge.lifetime;  // 4秒充能
                parentizeEffects = true;

                bullet = new OppressionLaserBulletType() {{
                    // ★ 限制大激光索敌范围为小激光射程 (300f)
                    //   AI 只在目标进入小激光范围时才触发大激光
                    range = 300f;
                }};
            }});

            weapons.add(new Weapon("create-end-small-mount") {{
                x = 8.5f;
                y = -4.5f;
                mirror = true;
                rotate = true;
                reload = 30f;
                inaccuracy = 15f;
                rotateSpeed = 5f;
                shootCone = 30f;
                shootSound = zzw.content.Z_Sounds.spaceFracture;
                bullet = new VoidFractureBulletType(32f, 600f) {{
                    ratioDamage = 1f / 50f;
                    ratioStart = damage * 20f;
                    activeSound = zzw.content.Z_Sounds.fractureShoot;
                    spikesSound = zzw.content.Z_Sounds.spaceFracture;
                    modules = new AntiCheatBulletModule[]{
                        new ArmorDamageModule(1f, 2f, 0f)
                    };
                }};
            }});
        }};

        // ═══════════════════════════════════════════════════════════
        //  Chronos (PU132 克罗诺斯)
        //  - 飞行单位, 17000 血, 速度 2
        //  - 能力: TimeStopAbility (时间停止, 15秒持续, 10秒充能)
        //  - 武器: TimeStopBulletType (时间停止子弹)
        //  - 防作弊: 简化版
        // ═══════════════════════════════════════════════════════════
        chronos = new UnitType("chronos") {{
            health = 17000f;
            speed = 2f;
            accel = 0.1f;
            drag = 0.08f;
            hitSize = 36f;
            engineOffset = 19f;
            engineSize = 4f;
            flying = true;
            lowAltitude = true;
            armor = 12f;
            rotateSpeed = 3f;
            range = 510f;
            outlineColor = Color.valueOf("2e3142");
            constructor = EndLegsUnit::create;
            // ★ 使用 WormAI (继承 FlyingAI, 完全按 PU132 原版自动索敌+攻击)
            controller = unit -> new zzw.content.units.ai.WormAI();

            // 时间停止能力 (PU132: duration=15*60, rechargeTime=10*60)
            abilities.add(new TimeStopAbility(15f * 60f, 10f * 60f) {{
                // ★ PU132 原版: timeStopSound = UnitySounds.stopTime
                timeStopSound = zzw.content.Z_Sounds.stopTime;
            }});

            weapons.add(new Weapon("create-end-point-defence") {{
                x = 12f;
                y = -7.5f;
                reload = 12f;
                rotate = true;
                rotateSpeed = 5f;
                bullet = new TimeStopBulletType(6f, 510f);
            }});
        }};

        // ═══════════════════════════════════════════════════════════
        //  Opticaecus (PU132 盲视者, 原译"视界虫"已更正: 是飞行单位非多节虫)
        //  - End 阵营飞行单位, 60000 血, 速度 1.8
        //  - 武器1: 红色激光 (LaserBulletType, 1400 伤害, 长度 390)
        //  - 武器2: 导弹发射器 (MissileBulletType, 10连发, 170 伤害, 追踪+蛇形)
        //  - 防作弊: 简化版 (无敌帧+单次上限+抗性递增)
        //  - ★ PU132 原版有隐身能力 (InvisibleUnitType), v158 简化为普通 UnitType
        //    (隐身机制依赖 Invisiblec 组件, v158 无原生支持)
        // ═══════════════════════════════════════════════════════════
        opticaecus = new UnitType("opticaecus") {{
            health = 60000f;
            speed = 1.8f;
            drag = 0.02f;
            hitSize = 60.5f;
            engineOffset = 38f;
            engineSize = 6f;
            flying = true;
            lowAltitude = true;
            circleTarget = false;
            armor = 12f;
            rotateSpeed = 3f;
            range = 400f;
            outlineColor = Color.valueOf("1a1a2e");
            constructor = EndLegsUnit::create;
            controller = unit -> new zzw.content.units.ai.WormAI();

            // ===== 武器1: 头部红色激光 (PU132 LaserBulletType, 1400 伤害) =====
            // PU132 第3867-3883行: rotate=false, mirror=false, reload=4*60
            weapons.add(new Weapon() {{
                mirror = false;
                rotate = false;
                shootCone = 360f;  // 固定方向, 任意角度都能发射
                x = 0f;
                y = 11.25f;
                shootY = 0f;
                reload = 4f * 60f;
                shootSound = zzw.content.Z_Sounds.devourerMainLaser;

                bullet = new mindustry.entities.bullet.LaserBulletType(1400f) {{
                    colors = new Color[]{Color.valueOf("f5303690"), Color.valueOf("f53036"), Color.valueOf("ff786e"), Color.white};
                    hitColor = Color.valueOf("f53036");
                    width = 30f;
                    length = 390f;
                    largeHit = true;
                    hitEffect = mindustry.content.Fx.hitLancer;
                }};
            }});

            // ===== 武器2: 导弹发射器 (PU132 doeg-launcher, 10连发) =====
            // PU132 第3884-3907行: x=24.75, mirror=true, rotate=true, reload=1.2*60, shots=10
            weapons.add(new Weapon("create-doeg-launcher") {{
                x = 24.75f;
                mirror = true;
                rotate = true;
                rotateSpeed = 5f;
                shootCone = 30f;
                reload = 1.2f * 60f;
                inaccuracy = 20f;
                shoot.shots = 10;
                shoot.shotDelay = 2f;
                shootSound = zzw.content.Z_Sounds.endMissile;

                bullet = new mindustry.entities.bullet.MissileBulletType(6f, 170f) {{
                    lifetime = 55f;
                    frontColor = Color.valueOf("ff786e");
                    backColor = trailColor = lightColor = Color.valueOf("f53036");
                    shrinkY = 0.1f;
                    splashDamage = 320f;
                    splashDamageRadius = 45f;
                    weaveScale = 15f;
                    weaveMag = 2f;
                    width *= 1.6f;
                    height *= 2.1f;
                    hitEffect = mindustry.content.Fx.hitLancer;
                }};
            }});
        }};

        // ═══════════════════════════════════════════════════════════
        //  Ravager (PU132 掠夺者)
        //  - End 阵营地面单位 (8腿), 1650000 血, 速度 0.65
        //  - 武器1: 噩梦激光 (EndPointBlastLaserBulletType, 1210 伤害, 长度 460, 宽度 26.1)
        //         - 直线碰撞 + 阻挡点范围爆炸 (damageRadius=110, auraDamage=9000)
        //  - 武器2,3: 炮弹 (ArtilleryBulletType, 130 伤害, 5连发, 闪电+破片)
        //  - 武器4,5: 小型炮台 (EndBasicBulletType 导弹, 330 伤害, 追踪+蛇形)
        //  - 防作弊: 简化版 (无敌帧+单次上限+抗性递增)
        //  - 8腿地面单位 (legCount=8, legGroupSize=4, legLength=140)
        //  - 免疫所有状态效果
        // ═══════════════════════════════════════════════════════════
        ravager = new UnitType("ravager") {{
            health = 1650000f;
            speed = 0.65f;
            drag = 0.16f;
            armor = 15f;
            hitSize = 138f;
            rotateSpeed = 1.1f;

            immunities.addAll(mindustry.Vars.content.getBy(mindustry.ctype.ContentType.status));

            allowLegStep = true;
            hovering = true;
            shadowElevation = 3f;  // ★ PU132 visualElevation=3f, v158 用 shadowElevation 控制腿的视觉高度
            groundLayer = mindustry.graphics.Layer.legUnit + 6f;
            legCount = 8;
            legGroupSize = 4;
            legPairOffset = 2f;
            legMoveSpace = 0.5f;
            legLength = 140f;
            legExtension = -15f;
            legBaseOffset = 50f;
            legSpeed = 0.15f;
            rippleScale = 7f;

            legSplashRange = 90f;
            legSplashDamage = 1400f;
            outlineColor = Color.valueOf("1a1a2e");
            range = 500f;
            // ★ ravager (End 阵营): 用 EndGroundUnit (extends LegsUnit), 有防作弊系统且能正常显示腿
            constructor = EndGroundUnit::create;
            controller = unit -> new mindustry.ai.types.GroundAI();

            // ===== 武器1: 噩梦激光 (PU132 ravager-nightmare) =====
            // PU132 第4535-4546行: bottomWeapon, x=80.25, reload=6*60, shootSound=ravagerNightmareShoot
            // top=false 等价于 PU132 的 bottomWeapons.add(this), 武器画在 body 下方
            weapons.add(new Weapon("create-ravager-nightmare") {{
                x = 80.25f;
                y = -7.75f;
                shootY = 75f;
                reload = 6f * 60f;
                recoil = 8f;
                alternate = true;
                rotate = false;
                shootCone = 360f;
                top = false;
                shootSound = zzw.content.Z_Sounds.ravagerNightmareShoot;
                bullet = new EndPointBlastLaserBulletType(1210f) {{
                    length = 460f;
                    width = 26.1f;
                    lifetime = 25f;
                    widthReduction = 6f;
                    damageRadius = 110f;
                    auraDamage = 9000f;

                    overDamage = 500000f;
                    ratioDamage = 1f / 30f;
                    ratioStart = 12000f;
                    bleedDuration = 10f * 60f;

                    hitEffect = mindustry.content.Fx.hitLancer;

                    laserColors = new Color[]{Color.valueOf("f5303690"), Color.valueOf("f53036"), Color.valueOf("ff786e"), Color.black};

                    modules = new zzw.content.units.anticheat.AntiCheatBulletModule[]{
                        new zzw.content.units.anticheat.ArmorDamageModule(1f / 15f, 5f, 70f, 8f),
                        new zzw.content.units.anticheat.AbilityDamageModule(50f, 400f, 4f, 1f / 25f, 5f),
                        new zzw.content.units.anticheat.ForceFieldDamageModule(10f, 30f, 230f, 8f, 1f / 40f)
                    };
                }};
            }});

            // ===== 武器2: 炮弹1 (PU132 ravager-artillery) =====
            // PU132 第4547-4559行: x=44.25, y=-31.75, rotate=true, reload=2*50, shots=5
            // 两个炮弹武器共用同 name "ravager-artillery", 共用同一贴图 (PU132 原版设计)
            weapons.add(new Weapon("create-ravager-artillery") {{
                shootY = 11f;
                shoot.shots = 5;
                inaccuracy = 10f;
                shadow = 13.25f * 2f;
                y = -31.75f;
                x = 44.25f;
                rotate = true;
                rotateSpeed = 2f;
                shootCone = 30f;
                velocityRnd = 0.2f;
                reload = 2f * 50f;
                shootSound = zzw.content.Z_Sounds.endBasicLarge;
                bullet = new mindustry.entities.bullet.ArtilleryBulletType(4f, 130f) {{
                    lifetime = 110f;
                    splashDamage = 325f;
                    splashDamageRadius = 140f;
                    width = 21f;
                    height = 21f;
                    backColor = lightColor = trailColor = Color.valueOf("f53036");
                    frontColor = lightningColor = Color.valueOf("ff786e");
                    lightning = 5;
                    lightningLength = 10;
                    lightningLengthRand = 5;
                    hitEffect = mindustry.content.Fx.hitLancer;
                }};
            }});

            // ===== 武器3: 炮弹2 (PU132 ravager-artillery) =====
            // PU132 第4560-4572行: x=51.25, y=-4.25, rotate=true, reload=2.25*50, shots=5
            // 两个炮弹武器共用同 name "ravager-artillery", 共用同一贴图 (PU132 原版设计)
            weapons.add(new Weapon("create-ravager-artillery") {{
                shootY = 11f;
                shoot.shots = 5;
                inaccuracy = 10f;
                shadow = 13.25f * 2f;
                y = -4.25f;
                x = 51.25f;
                rotate = true;
                rotateSpeed = 2f;
                shootCone = 30f;
                velocityRnd = 0.2f;
                reload = 2.25f * 50f;
                shootSound = zzw.content.Z_Sounds.endBasicLarge;
                bullet = new mindustry.entities.bullet.ArtilleryBulletType(4f, 130f) {{
                    lifetime = 110f;
                    splashDamage = 325f;
                    splashDamageRadius = 140f;
                    width = 21f;
                    height = 21f;
                    backColor = lightColor = trailColor = Color.valueOf("f53036");
                    frontColor = lightningColor = Color.valueOf("ff786e");
                    lightning = 5;
                    lightningLength = 10;
                    lightningLengthRand = 5;
                    hitEffect = mindustry.content.Fx.hitLancer;
                }};
            }});

            // ===== 武器4: 小型炮台1 (PU132 ravager-small-turret) =====
            // PU132 第4573-4584行: x=34.5, y=53.75, rotate=true, reload=7
            // 两个小型炮台共用同 name "ravager-small-turret", 共用同一贴图 (PU132 原版设计)
            weapons.add(new Weapon("create-ravager-small-turret") {{
                shootY = 7f;
                inaccuracy = 2f;
                shadow = 9.25f * 2f;
                y = 53.75f;
                x = 34.5f;
                rotate = true;
                rotateSpeed = 5f;
                shootCone = 30f;
                xRand = 2f;
                reload = 7f;
                shootSound = zzw.content.Z_Sounds.endMissile;
                bullet = new EndBasicBulletType(4f, 330f, "missile") {{
                    lifetime = 60f;
                    width = 12f;
                    height = 12f;
                    shrinkY = 0f;
                    drag = -0.013f;
                    splashDamageRadius = 45f;
                    splashDamage = 220f;
                    homingPower = 0.08f;
                    trailChance = 0.2f;
                    weaveScale = 6f;
                    weaveMag = 1f;

                    overDamage = 900000f;
                    ratioDamage = 1f / 150f;
                    ratioStart = 2000f;

                    backColor = lightColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("ff786e");
                    hitEffect = mindustry.content.Fx.hitLancer;
                }};
            }});

            // ===== 武器5: 小型炮台2 (PU132 ravager-small-turret) =====
            // PU132 第4585-4597行: x=50.75, y=24.25, rotate=true, reload=7
            // 两个小型炮台共用同 name "ravager-small-turret", 共用同一贴图 (PU132 原版设计)
            weapons.add(new Weapon("create-ravager-small-turret") {{
                shootY = 7f;
                inaccuracy = 2f;
                shadow = 9.25f * 2f;
                y = 24.25f;
                x = 50.75f;
                rotate = true;
                rotateSpeed = 5f;
                shootCone = 30f;
                xRand = 2f;
                reload = 7f;
                shootSound = zzw.content.Z_Sounds.endMissile;
                bullet = new EndBasicBulletType(4f, 330f, "missile") {{
                    lifetime = 60f;
                    width = 12f;
                    height = 12f;
                    shrinkY = 0f;
                    drag = -0.013f;
                    splashDamageRadius = 45f;
                    splashDamage = 220f;
                    homingPower = 0.08f;
                    trailChance = 0.2f;
                    weaveScale = 6f;
                    weaveMag = 1f;

                    overDamage = 900000f;
                    ratioDamage = 1f / 150f;
                    ratioStart = 2000f;

                    backColor = lightColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("ff786e");
                    hitEffect = mindustry.content.Fx.hitLancer;
                }};
            }});
        }};

        // ═══════════════════════════════════════════════════════════
        //  Exowalker (PU132 exowalker, Plague 阵营地面单位)
        //  - 8腿, 6000 血, 速度 0.7
        //  - 5武器: 4×plagueSmallMount (瘟疫导弹) + 1×drain-laser (吸血激光)
        //  - ★ 简化: PU132 用 TriJointLegsc 自定义腿组件, v158 用原生腿系统 (legCount=8)
        //  - ★ 简化: PU132 ShrapnelBulletType (碎片子弹), v158 用 SapBulletType (吸血子弹) 替代
        // ═══════════════════════════════════════════════════════════
        exowalker = new UnitType("exowalker") {{
            health = 6000f;
            speed = 0.7f;
            drag = 0.1f;
            hitSize = 33f;
            rotateSpeed = 2f;
            armor = 4f;

            // ===== 腿配置 (PU132 原值, v158 原生腿系统) =====
            legCount = 8;
            legGroupSize = 4;
            legLength = 120f;
            legBaseOffset = 9f;
            legMoveSpace = 0.9f;
            legPairOffset = 1.5f;
            // ★ 限制腿伸缩: legMaxLength=1f 不伸缩 (原版默认1.75会伸缩很强)
            // 腿只靠关节弯曲带动, 符合用户要求
            legMaxLength = 1.05f;  // 轻微允许伸缩 (5%), 避免完全僵硬
            legMinLength = 0.85f;  // 最短长度 (避免腿缩太短)
            legSpeed = 0.1f;      // 腿移动速度 (lerp 系数, 原版0.1)

            hovering = true;
            allowLegStep = true;
            shadowElevation = 0.7f;  // ★ PU132 visualElevation=0.7f, v158 用 shadowElevation
            groundLayer = mindustry.graphics.Layer.legUnit + 0.01f;
            outlineColor = Color.valueOf("2e3142");  // PU132 UnityPal.darkerOutline

            constructor = mindustry.gen.LegsUnit::create;
            controller = unit -> new mindustry.ai.types.GroundAI();

            // ===== 武器1-2: small-plague-launcher (前两个, 共用同一 name+贴图) =====
            // PU132 plagueSmallMount 模板: shootY=4.75, reload=1.5*60, shots=4, inaccuracy=15
            // bullet: MissileBulletType (3.8f, 9f), 4连发, 17 范围伤害, 蛇形飞行
            weapons.add(new Weapon("create-small-plague-launcher") {{
                x = 9.5f;
                y = 8f;
                shootY = 4.75f;
                reload = 1.5f * 60f;
                shoot.shots = 4;
                inaccuracy = 15f;
                mirror = false;
                alternate = true;
                rotate = true;
                rotateSpeed = 5f;
                shootCone = 30f;
                shootSound = zzw.content.Z_Sounds.endMissile;  // ★ v158 无 Sounds.missile, 用自定义 endMissile
                bullet = new mindustry.entities.bullet.MissileBulletType(3.8f, 9f) {{
                    width = 8f;
                    height = 8f;
                    lifetime = 45f;
                    backColor = hitColor = lightColor = trailColor = Color.valueOf("54de3b");  // plagueDark
                    frontColor = Color.valueOf("a3f080");  // plague
                    shrinkY = 0f;
                    drag = -0.01f;
                    splashDamage = 17f;
                    splashDamageRadius = 30f;
                    weaveScale = 8f;
                    weaveMag = 2f;
                    hitEffect = mindustry.content.Fx.blastExplosion;
                    despawnEffect = mindustry.content.Fx.blastExplosion;
                }};
            }});

            weapons.add(new Weapon("create-small-plague-launcher") {{
                x = -9.5f;
                y = 8f;
                shootY = 4.75f;
                reload = 1.5f * 60f;
                shoot.shots = 4;
                inaccuracy = 15f;
                mirror = false;
                alternate = true;
                rotate = true;
                rotateSpeed = 5f;
                shootCone = 30f;
                flipSprite = true;
                shootSound = zzw.content.Z_Sounds.endMissile;  // ★ v158 无 Sounds.missile, 用自定义 endMissile
                bullet = new mindustry.entities.bullet.MissileBulletType(3.8f, 9f) {{
                    width = 8f;
                    height = 8f;
                    lifetime = 45f;
                    backColor = hitColor = lightColor = trailColor = Color.valueOf("54de3b");
                    frontColor = Color.valueOf("a3f080");
                    shrinkY = 0f;
                    drag = -0.01f;
                    splashDamage = 17f;
                    splashDamageRadius = 30f;
                    weaveScale = 8f;
                    weaveMag = 2f;
                    hitEffect = mindustry.content.Fx.blastExplosion;
                    despawnEffect = mindustry.content.Fx.blastExplosion;
                }};
            }});

            // ===== 武器3-4: small-plague-launcher-flipped (后两个, 共用 flipped 贴图) =====
            weapons.add(new Weapon("create-small-plague-launcher-flipped") {{
                x = 12.25f;
                y = -12.25f;
                shootY = 4.75f;
                reload = 1.5f * 60f;
                shoot.shots = 4;
                inaccuracy = 15f;
                mirror = false;
                alternate = true;
                rotate = true;
                rotateSpeed = 5f;
                shootCone = 30f;
                flipSprite = true;
                shootSound = zzw.content.Z_Sounds.endMissile;  // ★ v158 无 Sounds.missile, 用自定义 endMissile
                bullet = new mindustry.entities.bullet.MissileBulletType(3.8f, 9f) {{
                    width = 8f;
                    height = 8f;
                    lifetime = 45f;
                    backColor = hitColor = lightColor = trailColor = Color.valueOf("54de3b");
                    frontColor = Color.valueOf("a3f080");
                    shrinkY = 0f;
                    drag = -0.01f;
                    splashDamage = 17f;
                    splashDamageRadius = 30f;
                    weaveScale = 8f;
                    weaveMag = 2f;
                    hitEffect = mindustry.content.Fx.blastExplosion;
                    despawnEffect = mindustry.content.Fx.blastExplosion;
                }};
            }});

            weapons.add(new Weapon("create-small-plague-launcher-flipped") {{
                x = -12.25f;
                y = -12.25f;
                shootY = 4.75f;
                reload = 1.5f * 60f;
                shoot.shots = 4;
                inaccuracy = 15f;
                mirror = false;
                alternate = true;
                rotate = true;
                rotateSpeed = 5f;
                shootCone = 30f;
                flipSprite = true;
                shootSound = zzw.content.Z_Sounds.endMissile;  // ★ v158 无 Sounds.missile, 用自定义 endMissile
                bullet = new mindustry.entities.bullet.MissileBulletType(3.8f, 9f) {{
                    width = 8f;
                    height = 8f;
                    lifetime = 45f;
                    backColor = hitColor = lightColor = trailColor = Color.valueOf("54de3b");
                    frontColor = Color.valueOf("a3f080");
                    shrinkY = 0f;
                    drag = -0.01f;
                    splashDamage = 17f;
                    splashDamageRadius = 30f;
                    weaveScale = 8f;
                    weaveMag = 2f;
                    hitEffect = mindustry.content.Fx.blastExplosion;
                    despawnEffect = mindustry.content.Fx.blastExplosion;
                }};
            }});

            // ===== 武器5: drain-laser (吸血激光) =====
            // PU132: ShrapnelBulletType (碎片子弹), v158 简化为 SapBulletType (吸血子弹)
            // SapBulletType 自动回血, 类似于 PU132 的 drain 效果
            weapons.add(new Weapon("create-drain-laser") {{
                x = 16f;
                y = -2.25f;
                shootY = 6.25f;
                mirror = true;
                rotate = true;
                rotateSpeed = 5f;
                shootCone = 30f;
                shoot.shots = 3;
                shoot.shotDelay = 17.5f;  // ★ v158 用 shotDelay 替代 PU132 burstSpacing
                reload = 1.5f * 60f;
                shootSound = zzw.content.Z_Sounds.devourerMainLaser;  // ★ v158 无 Sounds.laser, 用自定义 devourerMainLaser
                bullet = new mindustry.entities.bullet.SapBulletType() {{
                    sapStrength = 0.4f;  // 吸血强度
                    length = 80f;
                    damage = 43f;
                    color = Color.valueOf("a3f080");  // plague (SapBulletType 用 color 字段)
                    lightColor = Color.valueOf("a3f080");
                    width = 4f;
                    lifetime = 20f;
                    hitEffect = mindustry.content.Fx.sapExplosion;  // ★ v158 无 Fx.sap, 用 sapExplosion
                    despawnEffect = mindustry.content.Fx.none;
                }};
            }});
        }};

        // ═══════════════════════════════════════════════════════════
        //  Toxoswarmer (PU132 toxoswarmer, Plague 阵营地面单位)
        //  - 6腿 (PU132 用 CLegType 5条腿, v158 简化为 legCount=6 偶数对称)
        //  - 7000 血, 速度 1.1
        //  - 1武器: toxo-launcher (8连发追踪导弹, 命中后产生火焰)
        //  - ★ 简化: PU132 ShootingBulletType (追踪+持续射击), v158 用 MissileBulletType + fragBullet 火焰弹
        // ═══════════════════════════════════════════════════════════
        toxoswarmer = new zzw.content.units.types.MixedLegUnitType("toxoswarmer") {{
            health = 7000f;
            speed = 1.1f;
            drag = 0.1f;
            hitSize = 22.25f;
            rotateSpeed = 3f;
            armor = 4f;

            // ===== 腿配置 (原生腿用于碰撞/移动, CustomLegsAbility 用于渲染) =====
            // PU132 原版: 2组 CLegType.createGroup (CLegComp 系统)
            //   小腿组: 3条×2镜像=6条, baseLength=endLength=32, legTrns=0.8
            //   大腿组: 2条×2镜像=4条, baseLength=55, endLength=71, legTrns=0.7
            // v158: 保留原生腿 (legCount=10) 用于碰撞, drawLegs 委托 CustomLegsAbility
            legCount = 10;
            legGroupSize = 2;
            legLength = 95f;  // 原生腿长度 (仅碰撞, 不渲染)
            legBaseOffset = 11.25f;
            legMoveSpace = 0.85f;
            legPairOffset = 1f;
            legMaxLength = 1.1f;
            legMinLength = 0.9f;
            legSpeed = 0.1f;

            hovering = true;
            allowLegStep = true;
            shadowElevation = 0.7f;
            groundLayer = mindustry.graphics.Layer.legUnit + 0.01f;
            outlineColor = Color.valueOf("2e3142");

            constructor = mindustry.gen.LegsUnit::create;
            controller = unit -> new mindustry.ai.types.GroundAI();

            // ===== CustomLegsAbility: PU132 CLegGroup 完整移植 =====
            abilities.add(new zzw.content.units.abilities.CustomLegsAbility() {{
                // 小腿组 (PU132: 3条×2镜像=6条)
                legGroups.add(new CustomLegsAbility.LegGroupType("create-toxoswarmer-base",
                    new CustomLegsAbility.LegType("create-toxoswarmer-leg-small") {{
                        x = 6.25f; y = 10.75f;
                        targetX = 31f; targetY = 53.5f;
                        baseLength = endLength = 32f;
                        legTrns = 0.8f;
                    }},
                    new CustomLegsAbility.LegType("create-toxoswarmer-leg-small") {{
                        x = 12.5f; y = 0f;
                        targetX = 61.75f; targetY = 0f;
                        baseLength = endLength = 32f;
                        legTrns = 0.8f;
                    }},
                    new CustomLegsAbility.LegType("create-toxoswarmer-leg-small") {{
                        x = 6.25f; y = -10.75f;
                        targetX = 31f; targetY = -53.5f;
                        baseLength = endLength = 32f;
                        legTrns = 0.8f;
                        flipped = true;
                    }}
                ) {{
                    baseRotateSpeed = 4f;
                    moveSpacing = 0.8f;
                }});

                // 大腿组 (PU132: 2条×2镜像=4条)
                legGroups.add(new CustomLegsAbility.LegGroupType("create-toxoswarmer-base",
                    new CustomLegsAbility.LegType("create-toxoswarmer-leg-large") {{
                        x = 11.25f; y = 11.25f;
                        targetX = 77.5f; targetY = 77.5f;
                        baseLength = 55f; endLength = 71f;
                        legTrns = 0.7f;
                    }},
                    new CustomLegsAbility.LegType("create-toxoswarmer-leg-large") {{
                        x = 11.25f; y = -11.25f;
                        targetX = 77.5f; targetY = -77.5f;
                        baseLength = 55f; endLength = 71f;
                        legTrns = 0.7f;
                        flipped = true;
                    }}
                ) {{
                    baseRotateSpeed = 1f;
                    moveSpacing = 0.9f;
                }});
            }});

            // ===== 武器: toxo-launcher (8连发追踪导弹+火焰) =====
            // PU132: ShootingBulletType (追踪+持续射击 FlameBulletType)
            // v158 简化: MissileBulletType + fragBullet (火焰弹), 到达后分裂出火焰
            weapons.add(new Weapon("create-toxo-launcher") {{
                x = 17f;
                y = -8.25f;
                reload = 3f * 60f;
                shoot.shots = 8;
                shoot.shotDelay = 2f;
                inaccuracy = 16f;
                rotate = true;
                rotateSpeed = 3f;
                shootCone = 30f;
                shootSound = zzw.content.Z_Sounds.endMissile;  // ★ v158 无 Sounds.missile, 用自定义 endMissile
                bullet = new mindustry.entities.bullet.MissileBulletType(4f, 200f) {{
                    lifetime = 4f * 60f;
                    homingPower = 0.08f;
                    weaveScale = 12f;
                    weaveMag = 2f;
                    width = 9f;
                    height = 9f;
                    trailColor = lightColor = lightningColor = Color.valueOf("54de3b");  // plagueDark
                    backColor = Color.valueOf("54de3b");
                    frontColor = Color.valueOf("a3f080");  // plague
                    shrinkY = 0f;
                    drag = -0.01f;

                    splashDamage = 30f;
                    splashDamageRadius = 35f;

                    // 闪电效果 (PU132: lightning=3, length=3, damage=15)
                    lightning = 3;
                    lightningLength = 3;
                    lightningDamage = 15f;
                    lightningColor = Color.valueOf("54de3b");

                    hitEffect = mindustry.content.Fx.blastExplosion;
                    despawnEffect = mindustry.content.Fx.blastExplosion;

                    // ★ 简化: PU132 ShootingBulletType 到达后 shoot FlameBulletType
                    //   v158 用 fragBullet 模拟: 子弹命中后分裂出 2 个火焰弹
                    fragBullets = 2;
                    fragVelocityMax = 1.2f;
                    fragVelocityMin = 0.5f;
                    fragRandomSpread = 120f;  // ★ v158 用 fragRandomSpread 替代 PU132 fragCone
                    fragBullet = new mindustry.entities.bullet.FireBulletType() {{
                        damage = 15f;
                        lifetime = 20f;
                        pierce = true;
                        collidesAir = true;
                        knockback = 0.001f;
                        splashDamage = 4f;
                        splashDamageRadius = 25f;
                        status = mindustry.content.StatusEffects.burning;
                        statusDuration = 60f * 4f;
                        frontColor = Color.valueOf("a3f080");  // plague
                        lightColor = Color.valueOf("a3f080");
                    }};
                }};
            }});
        }};

        // ═══════════════════════════════════════════════════════════
        //  Desolation (PU132 desolation, End 阵营地面单位)
        //  - 8腿, 307300 血, 速度 0.7, 护甲 35, 全状态免疫
        //  - 主炮: EnergyChargeWeapon (DesolationBulletType, 蓄力+三防作弊模块)
        //  - 副武器: end-mount (3连发, fragBullet VoidFracture), end-mount-2 (2连发, 闪电+穿透)
        //  - 点防: end-point-defence (2座, 多目标防御激光)
        //  - ★ 简化: PU132 4个触手 TentacleType, v158 简化为 4 个独立武器 (无触手动画)
        //  - ★ 简化: PU132 clnW 克隆16个武器, v158 直接列出 (前8后8对称)
        //  - 防作弊: EndLegsUnit (简化版, invincibilityArray=4)
        // ═══════════════════════════════════════════════════════════
        desolation = new zzw.content.units.types.DesolationUnitType("desolation") {{
            health = 307300f;
            speed = 0.7f;
            drag = 0.16f;
            armor = 35f;
            hitSize = 257f;
            rotateSpeed = 0.9f;

            // ===== 腿配置 (模仿 FlameOut DespondencyUnitType + PU132 原值) =====
            // PU132: legTrns=0.3, legLength=672*(1-(0.3*0.85*0.5))=585.6, legExtension=-48, legMoveSpace=0.2, legBaseOffset=61.25
            // Despondency: lockLegBase=true, legForwardScl=0.75, legLengthScl=0.9, baseLegStraightness=1f, legStraightness=0.01f, legStraightLength=4f
            // ★ legBaseOffset 从 PU132 原值 61.25 减小到 25f, 缩小左右腿间距, 让腿在单位贴图下方而非外面
            lockLegBase = true;          // 腿基座锁定单位旋转 (Despondency 关键参数)
            legForwardScl = 0.75f;        // 腿向前移动幅度 (Despondency 原值)
            legLength = 585.6f;           // PU132: 672*(1-(0.3*0.85*0.5))
            legExtension = -48f;          // PU132 原值 (负值=缩短)
            legCount = 8;
            legGroupSize = 2;
            legPairOffset = 1f;
            legMoveSpace = 0.2f;           // PU132 原值
            legBaseOffset = 25f;           // ★ 减小腿根部偏移, 缩小左右间距 (PU132原值61.25太大)
            legLengthScl = 0.9f;           // Despondency 原值
            baseLegStraightness = 1f;      // Despondency 原值
            legStraightness = 0.01f;       // Despondency 原值
            legStraightLength = 4f;        // Despondency 原值
            rippleScale = 12f;

            legSplashRange = 120f;
            legSplashDamage = 1700f;

            aimDst = hitSize / 2f;

            // 全状态免疫
            immunities.addAll(mindustry.Vars.content.getBy(mindustry.ctype.ContentType.status));

            hovering = true;
            allowLegStep = true;
            shadowElevation = 8f;  // PU132 visualElevation=8f
            groundLayer = mindustry.graphics.Layer.flyingUnitLow + 1f;
            outlineColor = Color.valueOf("2e3142");  // PU132 UnityPal.darkerOutline

            // ★ desolation (End 阵营 8 腿单位): 用 EndGroundUnit (extends LegsUnit), 有防作弊系统且能正常显示腿
            constructor = EndGroundUnit::create;
            controller = unit -> new mindustry.ai.types.GroundAI();

            // ===== 身后鞭子触手 (完整移植 PU132 NewTentacle, 4条×mirror=8条) =====
            // PU132 desolation: 4条触手定义 (mirror后8条)
            //   #1 desolation-tentacle: 15段44.5长, EndPointBlastLaserBulletType(250f), 点射
            //   #2 apocalypse-tentacle: 17段37.25长, EndContinuousLaserBulletType(85f), 持续
            //   #3 apocalypse-tentacle: 14段37.25长, 同#2, swayOffset=45
            //   #4 apocalypse-tentacle:  9段37.25长, 同#2, swayOffset=90

            // 触手#1: desolation-tentacle (大鞭子, 爆破激光点射)
            // PU132 原版: laserColors = {scarColorAlpha, scarColor, endColor, black} 外围红→内层黑
            abilities.add(new zzw.content.units.abilities.TentacleAbility("create-desolation-tentacle") {{
                x = 139f;
                y = -13.5f;
                rotationOffset = 40f;
                segments = 15;
                segmentLength = 44.5f;  // PU132 原版
                angleLimit = 30f;       // PU132 原版
                firstSegmentAngleLimit = 17f;  // PU132 原版
                // ★ 降低移动速度+增大drag让动态更慢更稳定丝滑
                rotationSpeed = 1.5f;   // 2.5→1.5 减慢旋转追踪
                speed = 3f;             // 6→3 减慢末端移动
                accel = 0.1f;           // 0.2→0.1 减小加速度
                drag = 0.15f;           // 0.06→0.15 增大阻力让动态更稳定
                swayScl = 120f;
                swayMag = 0.08f;        // 减小摆动 (更硬)
                mirror = true;
                top = true;
                automatic = false;  // PU132 原版
                bullet = new EndPointBlastLaserBulletType(250f) {{
                    // ★ PU132 原版完整参数
                    length = 320f;
                    width = 17f;
                    lifetime = 20f;
                    widthReduction = 3f;
                    auraWidthReduction = 4f;  // EndPointBlastLaserBulletType 有此字段
                    damageRadius = 60f;
                    auraDamage = 1000f;
                    overDamage = 900000f;
                    ratioDamage = 1f / 200f;
                    ratioStart = 11000f;
                    bleedDuration = 10f * 60f;
                    // ★ PU132 原版颜色: 外围红→内层黑 (scarColorAlpha, scarColor, endColor, black)
                    laserColors = new Color[]{
                        Color.valueOf("f5303690"),  // scarColorAlpha (半透明红)
                        Color.valueOf("f53036"),    // scarColor (红)
                        Color.valueOf("ff786e"),    // endColor (浅红)
                        Color.black                 // 黑色核心
                    };
                }};
                reload = 3f * 60f;
                range = 320f;  // PU132: bullet.range()
                shootCone = 4f;  // PU132 原版 (精确瞄准)
                continuous = false;  // 点射模式
            }});

            // 触手#2: apocalypse-tentacle (小鞭子, 连续激光)
            // PU132 endLaserSmall: colors = {scarColorAlpha, scarColor, endColor, white} 红色细激光
            abilities.add(new zzw.content.units.abilities.TentacleAbility("create-apocalypse-tentacle") {{
                x = 122.75f;
                y = -41f;
                rotationOffset = 35f;
                segments = 17;
                segmentLength = 37.25f;  // PU132 原版
                // ★ 减小角度限制 (PU132 默认65, 改为30让鞭子更直, 像多节单位连接)
                angleLimit = 30f;
                firstSegmentAngleLimit = 20f;  // PU132 原版
                // ★ 降低速度+增大drag让动态更慢更稳定
                rotationSpeed = 1.5f;   // 3→1.5
                speed = 4f;             // 8→4
                accel = 0.1f;           // 0.2→0.1
                drag = 0.15f;           // 0.06→0.15
                mirror = true;
                top = true;
                automatic = false;
                bullet = new EndContinuousLaserBulletType(85f) {{
                    // ★ PU132 endLaserSmall 完整配置
                    lifetime = 2f * 60f;
                    length = 230f;
                    // strokes 缩小到 0.4 倍 (细激光)
                    strokes = new float[]{0.8f, 0.6f, 0.4f, 0.12f};
                    overDamage = 800000f;
                    ratioDamage = 1f / 40f;
                    ratioStart = 1000000f;
                    // ★ PU132 原版颜色: 红色细激光 (scarColorAlpha, scarColor, endColor, white)
                    colors = new Color[]{
                        Color.valueOf("f5303690"),  // scarColorAlpha (半透明红)
                        Color.valueOf("f53036"),    // scarColor (红)
                        Color.valueOf("ff786e"),    // endColor (浅红)
                        Color.white                 // 白色核心
                    };
                }};
                reload = 4f * 60f;
                range = 220f;
                shootCone = 15f;  // PU132 默认
                continuous = true;
                bulletDuration = 1.5f * 60f;  // PU132: 90f
            }});

            // 触手#3: apocalypse-tentacle (小鞭子, 连续激光, swayOffset=45)
            abilities.add(new zzw.content.units.abilities.TentacleAbility("create-apocalypse-tentacle") {{
                x = 111.5f;
                y = -57.5f;
                rotationOffset = 30f;
                segments = 14;
                segmentLength = 37.25f;
                // ★ 减小角度限制 (PU132 默认65, 改为30让鞭子更直)
                angleLimit = 30f;
                firstSegmentAngleLimit = 18f;
                swayOffset = 45f;
                // ★ 降低速度+增大drag
                rotationSpeed = 1.5f;
                speed = 4f;
                accel = 0.1f;
                drag = 0.12f;
                mirror = true;
                top = true;
                automatic = false;
                bullet = new EndContinuousLaserBulletType(85f) {{
                    lifetime = 2f * 60f;
                    length = 230f;
                    strokes = new float[]{0.8f, 0.6f, 0.4f, 0.12f};
                    overDamage = 800000f;
                    ratioDamage = 1f / 40f;
                    ratioStart = 1000000f;
                    colors = new Color[]{
                        Color.valueOf("f5303690"),  // scarColorAlpha
                        Color.valueOf("f53036"),    // scarColor
                        Color.valueOf("ff786e"),    // endColor
                        Color.white
                    };
                }};
                reload = 4f * 60f;
                range = 220f;
                shootCone = 15f;
                continuous = true;
                bulletDuration = 1.5f * 60f;
            }});

            // 触手#4: apocalypse-tentacle (小鞭子, 连续激光, swayOffset=90)
            abilities.add(new zzw.content.units.abilities.TentacleAbility("create-apocalypse-tentacle") {{
                x = 95.25f;
                y = -63f;
                rotationOffset = 25f;
                segments = 9;
                segmentLength = 37.25f;
                // ★ 减小角度限制 (PU132 默认65, 改为30让鞭子更直)
                angleLimit = 30f;
                firstSegmentAngleLimit = 16f;
                swayOffset = 90f;
                // ★ 降低速度+增大drag
                rotationSpeed = 1.5f;
                speed = 4f;
                accel = 0.1f;
                drag = 0.12f;
                mirror = true;
                top = true;
                automatic = false;
                bullet = new EndContinuousLaserBulletType(85f) {{
                    lifetime = 2f * 60f;
                    length = 230f;
                    strokes = new float[]{0.8f, 0.6f, 0.4f, 0.12f};
                    overDamage = 800000f;
                    ratioDamage = 1f / 40f;
                    ratioStart = 1000000f;
                    colors = new Color[]{
                        Color.valueOf("f5303690"),  // scarColorAlpha
                        Color.valueOf("f53036"),    // scarColor
                        Color.valueOf("ff786e"),    // endColor
                        Color.white
                    };
                }};
                reload = 4f * 60f;
                range = 220f;
                shootCone = 15f;
                continuous = true;
                bulletDuration = 1.5f * 60f;
            }});

            // ===== 主炮: desolation-main (EnergyChargeWeapon, 蓄力+DesolationBulletType) =====
            // PU132: x=0, y=80, reload=15*60, bullet lifetime=8*60
            // DesolationBulletType: 2500伤害, 比例伤害, 三防作弊模块
            weapons.add(new EnergyChargeWeapon("create-desolation-main") {{
                drawRegion = false;
                mirror = false;
                x = 0f;
                y = 80f;
                shootY = 36.5f;
                reload = 15f * 60f;
                shootCone = 360f;
                rotate = false;
                shootSound = zzw.content.Z_Sounds.ravagerNightmareShoot;

                bullet = new DesolationBulletType(1.75f, 2500f) {{
                    lifetime = 8f * 60f;
                    overDamage = 900000f;
                    overDamagePower = 3f;
                    overDamageScl = 3500f;
                    ratioDamage = 1f / 50f;
                    ratioStart = 200000f;
                    bleedDuration = 10f * 60f;

                    modules = new AntiCheatBulletModule[]{
                        new ArmorDamageModule(1f / 30f, 15f, 30f, 5f),
                        new AbilityDamageModule(50f, 10f * 60f, 10f, 1f / 60f, 15f),
                        new ForceFieldDamageModule(5f, 15f, 200f, 7f, 1f / 40f, 5f * 60f)
                    };
                }};

                // 充能特效 (PU132 drawCharge: 4阶段热图渐入, 红色)
                drawCharge = (unit, mount, charge) -> {
                    float r = unit.rotation - 90f;
                    float wx = arc.math.Angles.trnsx(r, x, y) + unit.x;
                    float wy = arc.math.Angles.trnsy(r, x, y) + unit.y;

                    // ★ PU132 原版: 4阶段渐变绘制 heatRegion 贴图, 配合 Additive 混合
                    // (替代之前的 Fill.circle 圆形特效, 改为贴合单位贴图的 heat 贴图)
                    TextureRegion heatRegion = mindustry.Vars.headless ? null :
                        arc.Core.atlas.find("create-desolation-main-heat");
                    Draw.color(Color.valueOf("f53036"));  // scarColor
                    Draw.blend(arc.graphics.Blending.additive);
                    for (int i = 0; i < 4; i++) {
                        float in = arc.math.Mathf.curve(charge, i / 4f, (i + 1f) / 4f);
                        if (in > 0.0001f) {
                            Draw.alpha(in);
                            if (heatRegion != null && heatRegion.found()) {
                                // PU132 原版: 用 heatRegion 贴图绘制 (贴合单位前方)
                                Draw.rect(heatRegion,
                                    wx + arc.math.Mathf.range(12f - (in * 11.3f)),
                                    wy + arc.math.Mathf.range(12f - (in * 11.3f)),
                                    r);
                            } else {
                                // 无贴图时退化为 Fill.circle
                                Fill.circle(wx + arc.math.Mathf.range(12f - (in * 11.3f)),
                                           wy + arc.math.Mathf.range(12f - (in * 11.3f)),
                                           20f * in);
                            }
                        }
                    }
                    Draw.blend();
                    Draw.color();
                };
            }});

            // ===== 点防1: end-point-defence (右侧) =====
            // PU132 MultiTargetPointDefenceWeapon (自动锁定敌方子弹, 用 beamEffect 光束)
            // v158 简化为普通 Weapon, 单发瞬时命中 (speed=0, lifetime=5)
            // ★ 降低密度: reload=60 (1秒), shots=1 (单发), 原 PU132 reload=15/shots=7 太密集
            weapons.add(new Weapon("create-end-point-defence") {{
                x = 96.75f;
                y = 9f;
                reload = 60f;
                shootCone = 20f;
                rotate = true;
                rotateSpeed = 15f;
                mirror = false;
                alternate = false;
                shootSound = zzw.content.Z_Sounds.endBasicSmall;
                bullet = new EndBasicBulletType(0f, 220f) {{
                    lifetime = 5f;
                    width = 4f;
                    height = 6f;
                    speed = 0f;  // 瞬时命中 (模拟光束)
                    backColor = lightColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("f53036");
                    hitEffect = mindustry.content.Fx.hitLancer;
                    shootEffect = mindustry.content.Fx.pointBeam;
                }};
            }});

            // ===== 点防2: end-point-defence (右侧偏后) =====
            weapons.add(new Weapon("create-end-point-defence") {{
                x = 82f;
                y = 20.5f;
                reload = 60f;
                shootCone = 20f;
                rotate = true;
                rotateSpeed = 15f;
                mirror = false;
                alternate = false;
                shootSound = zzw.content.Z_Sounds.endBasicSmall;
                bullet = new EndBasicBulletType(0f, 220f) {{
                    lifetime = 5f;
                    width = 4f;
                    height = 6f;
                    speed = 0f;
                    backColor = lightColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("f53036");
                    hitEffect = mindustry.content.Fx.hitLancer;
                    shootEffect = mindustry.content.Fx.pointBeam;
                }};
            }});

            // ===== 副武器 w: end-mount (3连发, fragBullet VoidFracture) =====
            // PU132 8个 clnW(w) 武器 (4前4后, 对称), v158 简化为 4 个 (前4个, 后4个对称用 mirror)
            // 实际位置: (62.25, 6.75), (57, -16.25), (52, -39), (46.75, -61.75) + 镜像
            weapons.add(new Weapon("create-end-mount") {{
                x = 62.25f; y = 6.75f;
                mirror = true;
                shootY = 9f;
                reload = 35f;
                inaccuracy = 5f;
                shoot.shots = 3;
                shoot.shotDelay = 5f;
                rotate = true;
                rotateSpeed = 15f;
                alternate = true;
                shootCone = 30f;
                shootSound = zzw.content.Z_Sounds.endBasicLarge;
                bullet = new EndBasicBulletType(5f, 260f) {{
                    lifetime = 70f;
                    width = 19f;
                    height = 27f;
                    backColor = lightColor = Color.valueOf("f53036");  // scarColor
                    frontColor = Color.black;
                    trailChance = 0.4f;
                    hitEffect = mindustry.content.Fx.hitLancer;

                    overDamage = 2200000f;
                    ratioDamage = 1f / 170f;
                    ratioStart = 4000f;

                    // fragBullet: VoidFractureBulletType (简化版, 无贴图依赖)
                    fragBullets = 3;
                    fragVelocityMax = 1.2f;
                    fragVelocityMin = 0.5f;
                    fragRandomSpread = 120f;  // ★ v158 用 fragRandomSpread 替代 PU132 fragCone
                    fragBullet = new VoidFractureBulletType(15f, 100f) {{
                        width = 9.5f;
                        maxTargets = 5;
                        spikesRange = 90f;
                        spikesDamage = 50f;
                        overDamage = 1800000f;
                        ratioDamage = 1f / 50f;
                        ratioStart = 50000f;
                        modules = new AntiCheatBulletModule[]{
                            new ArmorDamageModule(1f, 20f, 2f)
                        };
                    }};
                }};
            }});

            weapons.add(new Weapon("create-end-mount") {{
                x = 57f; y = -16.25f;
                mirror = true;
                flipSprite = true;
                shootY = 9f;
                reload = 35f;
                inaccuracy = 5f;
                shoot.shots = 3;
                shoot.shotDelay = 5f;
                rotate = true;
                rotateSpeed = 15f;
                alternate = true;
                shootCone = 30f;
                shootSound = zzw.content.Z_Sounds.endBasicLarge;
                bullet = new EndBasicBulletType(5f, 260f) {{
                    lifetime = 70f;
                    width = 19f;
                    height = 27f;
                    backColor = lightColor = Color.valueOf("f53036");
                    frontColor = Color.black;
                    trailChance = 0.4f;
                    hitEffect = mindustry.content.Fx.hitLancer;
                    overDamage = 2200000f;
                    ratioDamage = 1f / 170f;
                    ratioStart = 4000f;
                    fragBullets = 3;
                    fragVelocityMax = 1.2f;
                    fragVelocityMin = 0.5f;
                    fragRandomSpread = 120f;  // ★ v158 用 fragRandomSpread 替代 PU132 fragCone
                    fragBullet = new VoidFractureBulletType(15f, 100f) {{
                        width = 9.5f;
                        maxTargets = 5;
                        spikesRange = 90f;
                        spikesDamage = 50f;
                        overDamage = 1800000f;
                        ratioDamage = 1f / 50f;
                        ratioStart = 50000f;
                        modules = new AntiCheatBulletModule[]{
                            new ArmorDamageModule(1f, 20f, 2f)
                        };
                    }};
                }};
            }});

            weapons.add(new Weapon("create-end-mount") {{
                x = 52f; y = -39f;
                mirror = true;
                flipSprite = true;
                shootY = 9f;
                reload = 35f;
                inaccuracy = 5f;
                shoot.shots = 3;
                shoot.shotDelay = 5f;
                rotate = true;
                rotateSpeed = 15f;
                alternate = true;
                shootCone = 30f;
                shootSound = zzw.content.Z_Sounds.endBasicLarge;
                bullet = new EndBasicBulletType(5f, 260f) {{
                    lifetime = 70f;
                    width = 19f;
                    height = 27f;
                    backColor = lightColor = Color.valueOf("f53036");
                    frontColor = Color.black;
                    trailChance = 0.4f;
                    hitEffect = mindustry.content.Fx.hitLancer;
                    overDamage = 2200000f;
                    ratioDamage = 1f / 170f;
                    ratioStart = 4000f;
                    fragBullets = 3;
                    fragVelocityMax = 1.2f;
                    fragVelocityMin = 0.5f;
                    fragRandomSpread = 120f;  // ★ v158 用 fragRandomSpread 替代 PU132 fragCone
                    fragBullet = new VoidFractureBulletType(15f, 100f) {{
                        width = 9.5f;
                        maxTargets = 5;
                        spikesRange = 90f;
                        spikesDamage = 50f;
                        overDamage = 1800000f;
                        ratioDamage = 1f / 50f;
                        ratioStart = 50000f;
                        modules = new AntiCheatBulletModule[]{
                            new ArmorDamageModule(1f, 20f, 2f)
                        };
                    }};
                }};
            }});

            weapons.add(new Weapon("create-end-mount") {{
                x = 46.75f; y = -61.75f;
                mirror = true;
                flipSprite = true;
                shootY = 9f;
                reload = 35f;
                inaccuracy = 5f;
                shoot.shots = 3;
                shoot.shotDelay = 5f;
                rotate = true;
                rotateSpeed = 15f;
                alternate = true;
                shootCone = 30f;
                shootSound = zzw.content.Z_Sounds.endBasicLarge;
                bullet = new EndBasicBulletType(5f, 260f) {{
                    lifetime = 70f;
                    width = 19f;
                    height = 27f;
                    backColor = lightColor = Color.valueOf("f53036");
                    frontColor = Color.black;
                    trailChance = 0.4f;
                    hitEffect = mindustry.content.Fx.hitLancer;
                    overDamage = 2200000f;
                    ratioDamage = 1f / 170f;
                    ratioStart = 4000f;
                    fragBullets = 3;
                    fragVelocityMax = 1.2f;
                    fragVelocityMin = 0.5f;
                    fragRandomSpread = 120f;  // ★ v158 用 fragRandomSpread 替代 PU132 fragCone
                    fragBullet = new VoidFractureBulletType(15f, 100f) {{
                        width = 9.5f;
                        maxTargets = 5;
                        spikesRange = 90f;
                        spikesDamage = 50f;
                        overDamage = 1800000f;
                        ratioDamage = 1f / 50f;
                        ratioStart = 50000f;
                        modules = new AntiCheatBulletModule[]{
                            new ArmorDamageModule(1f, 20f, 2f)
                        };
                    }};
                }};
            }});

            // ===== 副武器 w2: end-mount-2 (2连发, 闪电+穿透) =====
            // PU132 4个 clnW(w2) 武器 (前2后2对称), v158 简化为 2 个 (前2个, 镜像对称)
            // 位置: (100.75, -13), (79, -23.5) + 镜像
            weapons.add(new Weapon("create-end-mount-2") {{
                x = 100.75f; y = -13f;
                mirror = true;
                shootY = 12f;
                reload = 100f;
                rotate = true;
                rotateSpeed = 5f;
                alternate = true;
                shootCone = 30f;
                shoot.shots = 2;
                shoot.shotDelay = 5f;
                shootSound = zzw.content.Z_Sounds.endBasicLarge;
                bullet = new EndBasicBulletType(7f, 380f, "shell") {{
                    lifetime = 95f;
                    pierceShields = pierce = pierceBuilding = true;
                    pierceCap = 3;
                    shrinkY = 0f;
                    backColor = lightningColor = lightColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("ff786e");  // endColor
                    lightning = 3;
                    lightningLength = 8;
                    lightningLengthRand = 4;
                    lightningDamage = 80f;
                    splashDamage = 220f;
                    splashDamageRadius = 80f;
                    width = 15f;
                    height = 21f;
                    hitEffect = mindustry.content.Fx.hitLancer;
                    shootEffect = mindustry.content.Fx.shootBig;
                }};
            }});

            weapons.add(new Weapon("create-end-mount-2") {{
                x = 79f; y = -23.5f;
                mirror = true;
                flipSprite = true;
                shootY = 12f;
                reload = 100f;
                rotate = true;
                rotateSpeed = 5f;
                alternate = true;
                shootCone = 30f;
                shoot.shots = 2;
                shoot.shotDelay = 5f;
                shootSound = zzw.content.Z_Sounds.endBasicLarge;
                bullet = new EndBasicBulletType(7f, 380f, "shell") {{
                    lifetime = 95f;
                    pierceShields = pierce = pierceBuilding = true;
                    pierceCap = 3;
                    shrinkY = 0f;
                    backColor = lightningColor = lightColor = Color.valueOf("f53036");
                    frontColor = Color.valueOf("ff786e");
                    lightning = 3;
                    lightningLength = 8;
                    lightningLengthRand = 4;
                    lightningDamage = 80f;
                    splashDamage = 220f;
                    splashDamageRadius = 80f;
                    width = 15f;
                    height = 21f;
                    hitEffect = mindustry.content.Fx.hitLancer;
                    shootEffect = mindustry.content.Fx.shootBig;
                }};
            }});

            // ===== 触手武器已移至 abilities (TentacleAbility), 不再作为 Weapon =====
            // ★ 原触手武器已删除, 改用 TentacleAbility 实现 (在上方 abilities 段)
            //   原因: Weapon 会显示炮台贴图 (误用触手贴图), 且无法实现触手分段动画
            //   TentacleAbility 自主渲染分段, 支持激光发射和碰撞伤害
        }};
    }
}
