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
        toxobyteSegment,       // 段身
        catenapede,            // 头部 (PU132 Catenapede, 15 段)
        catenapedeSegment,     // 段身
        devourer,              // 头部 (PU132 Devourer, 60 段)
        devourerSegment;       // 段身

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
                reload = 60f;
                rotateSpeed = 50f;
                shootCone = 180f;
                bullet = new mindustry.entities.bullet.BombBulletType(27f, 25f) {{
                    width = 10f;
                    height = 14f;
                    hitEffect = mindustry.content.Fx.flakExplosion;
                    shootEffect = mindustry.content.Fx.none;
                    smokeEffect = mindustry.content.Fx.none;
                    collidesAir = false;
                    collidesGround = true;
                    splashDamage = 25f;
                    splashDamageRadius = 25f;
                    status = mindustry.content.StatusEffects.blasted;
                    statusDuration = 60f;
                }};
            }});
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
            // ★ arcnelidia 关闭原版 wobble (振幅 0.05f 太大), 用自定义 wobbleEnabled (振幅 0.02f)
            wobble = false;
            // ★ drag 用飞行单位合理值 (v154.3 默认 0.3f 对飞行单位太大, 速度衰减太快显得僵硬)
            // 原版飞行单位 drag 通常 0.012f ~ 0.18f
            drag = 0.018f;

            // 用自定义 Entity (SegmentWormEntity)
            constructor = SegmentWormEntity::create;
            // ★ 使用 WormAI (待机静止, 不自动朝 spawn 移动)
            // v154.3: defaultController 改名为 aiController
            aiController = zzw.content.units.WormAI::new;

            // ===== 头部武器: 双激光 (PU132 原配置) =====
            // PU132 UnityUnitTypes.java 第3024-3037行原配置
            weapons.add(new Weapon("create-arcnelidia-laser") {{
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
        // PU132 原版 segmentLength=9, segmentOffset=23f
        // 段间距 22.7f (PU132 23f - 0.3f, 用户要求稍小一点)
        // wobble=true (arcnelidia 轻微晃动)
        // angleLimit=25f (电弧虫比较硬, 防止旋转过度)
        // segmentCast=4, jointStrength=1f, anglePhysicsSmooth=0f (PU132 默认值)
        SegmentWormEntity.configs.put(arcnelidia.name,
            new SegmentWormEntity.SegmentConfig(arcnelidiaSegment, 9, 22.7f, 0f, 0, true, false, false,
                25f, 6f, 0.1f, 1f, 4, 0f, false, 0f));

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
            health = 200;
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
            aiController = zzw.content.units.WormAI::new;

            // ===== 头部武器: 12 发散 SapBullet (PU132 第3250-3267行) =====
            // 瘟疫激光: 12 发同时散射, SapBullet 自动回血
            // ★ v154.3: shots/shotDelay 在 shoot (ShootPattern) 字段里, 不在 Weapon 上
            weapons.add(new Weapon("create-toxobyte-sap") {{
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
        // ★ angleLimit=25f (PU132 toxobyte 原值)
        // ★ segmentDamageScl=8f (PU132 原版 toxobyte 值, 段身受击时血量×8倍掉, 更脆更容易分裂)
        // segmentCast=4, jointStrength=1f, anglePhysicsSmooth=0f (PU132 默认值)
        SegmentWormEntity.configs.put(toxobyte.name,
            new SegmentWormEntity.SegmentConfig(toxobyteSegment, 25, 16.25f, 15f * 60f, 25, false, true, true,
                25f, 8f, 0.1f, 1f, 4, 0f, false, 0f));

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
            health = 500;
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
            health = 750f;
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
            aiController = zzw.content.units.WormAI::new;

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
                bullet = new zzw.content.units.PointDrainLaserBulletType(45f) {{
                    maxLength = 160f;
                    drainPercent = 0.5f;
                    width = 6f;
                    area = 9f;
                    knockback = -34f;
                    backColor = Color.valueOf("54de3b");
                    frontColor = Color.valueOf("a3f080");
                }};
            }});
        }};

        // ★ 注册 catenapede 段身配置 ★
        // PU132: segmentLength=2 (初始只生成2段), segmentOffset=31f
        // PU132: regenTime=30*60f (每30秒长一节), maxSegments=15 (最多15段)
        // PU132: splittable=true, chainable=true
        // PU132: segmentDamageScl=12f (段身受击时血量×12倍掉)
        // PU132: healthDistribution=0.15f (血量分布速率)
        // angleLimit=25f, segmentCast=4, jointStrength=1f (PU132 默认值)
        SegmentWormEntity.configs.put(catenapede.name,
            new SegmentWormEntity.SegmentConfig(catenapedeSegment, 2, 31f, 30f * 60f, 15, false, true, true,
                25f, 12f, 0.15f, 1f, 4, 0f, false, 0f));

        // ===== Devourer (PU132 devourer-of-eldrich-gods) =====
        // End 阵营超级虫子, 60段, 全免疫, 头部激光+段身多种武器

        // ★ Devourer 段身 ★
        devourerSegment = new UnitType("devourer-segment") {{
            health = 25000f;
            speed = 0f;
            hitSize = 40f;  // 原 30f + 10
            armor = 8f;
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
                    backColor = Color.valueOf("ec745855");
                    frontColor = Color.valueOf("ff9c5a");
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
                    backColor = Color.valueOf("ec745855");
                    frontColor = Color.valueOf("ff9c5a");
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
                    colors = new Color[]{Color.valueOf("ec745855"), Color.valueOf("ec7458aa"), Color.valueOf("ff9c5a"), Color.white};
                    width = 9f;
                }};
            }});
        }};

        // ★ Devourer 头部 ★
        devourer = new UnitType("devourer") {{
            health = 1250000f;
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
            aiController = zzw.content.units.WormAI::new;

            // 头部武器1: 主激光 (PU132 UnityBullets.endLaser, 简化版)
            weapons.add(new Weapon("create-devourer-main-laser") {{
                x = 0f;
                y = 23f;
                mirror = false;
                ignoreRotation = true;
                reload = 15f * 60f;
                continuous = true;
                shake = 4f;
                shoot.firstShotDelay = 41f;
                minShootVelocity = 0.01f;

                bullet = new EndContinuousLaserBulletType(2400f) {{
                    length = 340f;
                    lifetime = 5f * 60f;
                    colors = new Color[]{Color.valueOf("ec745855"), Color.valueOf("ec7458aa"), Color.valueOf("ff9c5a"), Color.white};
                    lightningChance = 0.8f;
                    lightningDamage = 80f;
                    lightningLength = 42;
                    lightningLengthRand = 5;
                    width = 15f;
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
                    backColor = Color.valueOf("ec745855");
                    frontColor = Color.valueOf("ff9c5a");
                }};
            }});
        }};

        // ★ 注册 devourer 段身配置 ★
        // PU132: segmentLength=60, segmentOffset=(41f*1.55)+7f ≈ 70.55f
        // PU132: splittable=false, chainable=false (不可分裂合并)
        // PU132: 无 regen (初始就60段)
        // PU132 原版: segmentCast=7, anglePhysicsSmooth=0.5f, jointStrength=1f, preventDrifting=true
        SegmentWormEntity.configs.put(devourer.name,
            new SegmentWormEntity.SegmentConfig(devourerSegment, 60, 70.55f, 0f, 60, false, false, false,
                30f, 6f, 0.1f, 1f, 7, 0.5f, true, 0f, 240f));

        // ★ 初始化分裂/合并音效 (PU132 默认 Sounds.door)
        try {
            SegmentWormEntity.splitSound = mindustry.gen.Sounds.door;
            SegmentWormEntity.chainSound = mindustry.gen.Sounds.door;
        } catch (Throwable t) {
            System.out.println("[Z_Units] 音效初始化失败: " + t);
        }

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

        System.out.println("[Z_Units] load done, configs=" + SegmentWormEntity.configs.keySet());
    }
}
