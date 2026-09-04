package zzw.content.units;

import arc.Core;
import arc.func.Floatf;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.math.geom.Vec3;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;

import mindustry.content.StatusEffects;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.bullet.LaserBoltBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.pattern.ShootSpread;
import mindustry.gen.*;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.graphics.Trail;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.Tile;
import zzw.content.Z_Sounds;
import zzw.content.graphics.UnityPal;
import zzw.content.type.UnityUnitType;
import zzw.content.units.effects.UnityDrawf;
import zzw.content.units.bullets.JoiningBulletType;
import zzw.content.units.bullets.RicochetBulletType;
import zzw.content.units.bullets.UnityBullets;
import zzw.content.units.abilities.LightningSpawnAbility;
import zzw.content.units.effects.MonolithFx;
import zzw.content.units.effects.ParticleFx;
import zzw.content.units.effects.UnityDrawf;
import zzw.content.units.graphics.MultiTrail;
import zzw.content.units.graphics.MultiTrail.TrailHold;
import zzw.content.units.graphics.Trails;
import zzw.content.units.weapons.ChargeShotgunWeapon;
import zzw.content.units.weapons.ChargeShotgunWeapon.ChargeShotgunMount;

import static mindustry.Vars.headless;

/**
 * Z_MonolithUnits — Monolith (巨石) 阵营单位注册 (PU132 移植)
 *
 * <p>移植自 PU132 MonolithUnitTypes.java, 按难度递增顺序移植。</p>
 *
 * <p>★ v132 → v155.4 适配要点:</p>
 * <ul>
 *   <li>音效重命名: shootBig→shootArtillery, spark→shootArc, laser→shootLaser,
 *       laserblast→beamMeltdown, lasercharge→chargeLancer, railgun→shootForeshadow,
 *       lasershoot→shootLaser;</li>
 *   <li>PU132 UnitySounds.energyBolt/chainyShot/energyCharge/energyBlast →
 *       本项目 Z_Sounds 对应字段;</li>
 *   <li>shots/shotDelay/firstShotDelay 移入 ShootPattern (shoot.shots 等);</li>
 *   <li>BulletType.range() 方法在 v155.4 改为 public 字段 range,
 *       "range() * 2f" 类计算改为 "speed * lifetime * 2f" 手动换算;</li>
 *   <li>scaleVelocity 字段 v155.4 不存在, 直接省略 (TODO 注释);</li>
 *   <li>commandLimit / visualElevation / rotateShooting / forceWreckRegion /
 *       miningRange 字段 v155.4 UnitType 不存在, 省略并留 TODO 注释;</li>
 *   <li>Mechc 单位 (stele/pedestal/pilaster) 需显式 constructor = MechUnit::create,
 *       Legsc 单位 (pylon/monument/colossus/bastion) 需 constructor = LegsUnit::create
 *       (UnityUnitType 默认构造器为 UnitEntity)。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class Z_MonolithUnits{
    // ★ monolithSoul (巨石灵魂) 已删除: 依赖 PU132 的 Soul/monolithWorld 系统
    //   (单位死亡拆魂、灵魂加入容器增益) 未移植, 实际对局无用 (2026-09-05 用户决定删除)

    // 巨石机甲 (地面 Mechc)
    public static UnityUnitType stele, pedestal, pilaster;

    // 巨石构装体 (地面 Legsc 多足)
    public static UnityUnitType pylon, monument, colossus, bastion;

    // 巨石辅助无人机 (飞行, PU132 为 Assistantc, 此处简化为普通飞行单位)
    public static UnityUnitType adsect, comitate;

    // ★ 能量环单位 (stray/tendence/liminality/calenture) 按用户要求放弃移植

    public static void load(){
        // 子弹先于单位加载 (pylon/monument 武器引用 UnityBullets)
        UnityBullets.load();

        loadStele();
        loadAssistants();
        loadPedestal();
        loadPilaster();
        loadLegUnits();
        loadGiantUnits();
    }

    /**
     * stele — 石碑机甲 (PU132 L207-245)。
     *
     * <p>入门级巨石单位, 双臂聚合霰弹枪: 子弹为 {@link JoiningBulletType},
     * 多发子弹飞行途中互相吸引聚合, 聚合后伤害滚雪球。</p>
     */
    private static void loadStele(){
        stele = new UnityUnitType("stele"){{
            // PU132: Mechc 实体 (地面机甲, 带 -leg 机械腿贴图)
            constructor = mindustry.gen.MechUnit::create;

            health = 300f;
            speed = 0.6f;
            hitSize = 8f;
            armor = 5f;

            canBoost = true;
            boostMultiplier = 2.5f;
            outlineColor = UnityPal.darkOutline;

            weapons.add(new Weapon(name + "-shotgun"){{
                layerOffset = -0.01f;
                top = false;
                x = 5.25f;
                y = -0.25f;
                shootY = 5f;

                reload = 60f;
                recoil = 2.5f;
                inaccuracy = 0.5f;
                // ★ v155.4: Sounds.shootBig → Sounds.shootArtillery
                shootSound = Sounds.shootArtillery;

                bullet = new JoiningBulletType(3.5f, 36f){{
                    lifetime = 48f;
                    radius = 10f;
                    weaveScale = 5f;
                    weaveMag = 2f;
                    homingPower = 0.07f;
                    // PU132: homingRange = range() * 2f; v155.4 range 为字段, 手动换算
                    homingRange = speed * lifetime * 2f;
                    sensitivity = 0.5f;

                    trailInterval = 3f;
                    trailColor = UnityPal.monolithGreen;
                    hitEffect = despawnEffect = MonolithFx.soulConcentrateHit;
                    shootEffect = MonolithFx.soulConcentrateShoot;
                    smokeEffect = Fx.lightningShoot;
                }};
            }});
        }};
    }

    /**
     * adsect / comitate — 巨石辅助无人机 (PU132 L887-981)。
     *
     * <p>维修支援单位: 激光弹 (LaserBoltBulletType, healPercent 治疗友军)
     * + 可采矿建造 (mineTier/mineSpeed/buildSpeed)。</p>
     *
     * <p>★ TODO: PU132 使用 AssistantAI (跟随指挥官的辅助 AI,
     * Assistance.mendCore/mine/build/heal 行为树) — 未移植,
     * 当前为普通单位 (无 defaultController, 走默认 AI)。
     * 后续如需完整辅助行为需移植 unity.ai.AssistantAI。</p>
     */
    private static void loadAssistants(){
        // ===== adsect (PU132 L887-922) =====
        adsect = new UnityUnitType("adsect"){{
            // TODO: PU132 defaultController = AssistantAI.create(mendCore, mine, build)
            health = 180f;
            speed = 4f;
            accel = 0.4f;
            drag = 0.2f;
            rotateSpeed = 15f;
            flying = true;
            mineTier = 2;
            mineSpeed = 3f;
            buildSpeed = 0.8f;
            circleTarget = false;

            // ★ v158 已移除单位弹药系统 (mindustry.type.ammo.* 不存在, 运行时 NoClassDefFoundError),
            //   PU132 的 ammoType = new PowerAmmoType(...) 在 v158 无意义 (单位不再耗弹药), 删除
            engineColor = UnityPal.monolith;
            outlineColor = UnityPal.darkOutline;

            weapons.add(new Weapon(){{
                mirror = false;
                rotate = false;
                x = 0f;
                y = 4f;
                reload = 6f;
                shootCone = 40f;

                // ★ v155.4: Sounds.lasershoot → Sounds.shootLaser
                shootSound = Sounds.shootLaser;
                bullet = new LaserBoltBulletType(4f, 23f){{
                    healPercent = 1.5f;
                    lifetime = 40f;
                    collidesTeam = true;
                    frontColor = UnityPal.monolithLight;
                    backColor = UnityPal.monolith;
                    smokeEffect = hitEffect = despawnEffect = MonolithFx.hitMonolithLaser;
                }};
            }});
        }};

        // ===== comitate (PU132 L924-981) =====
        comitate = new UnityUnitType("comitate"){{
            // TODO: PU132 defaultController = AssistantAI.create(mendCore, mine, build, heal)
            health = 420f;
            speed = 4.5f;
            accel = 0.5f;
            drag = 0.15f;
            rotateSpeed = 15f;
            flying = true;
            mineTier = 3;
            mineSpeed = 5f;
            buildSpeed = 1.3f;
            circleTarget = false;

            // ★ v158 已移除单位弹药系统 (mindustry.type.ammo.* 不存在, 运行时 NoClassDefFoundError),
            //   PU132 的 ammoType = new PowerAmmoType(...) 在 v158 无意义 (单位不再耗弹药), 删除
            engineColor = UnityPal.monolith;
            outlineColor = UnityPal.darkOutline;

            weapons.add(new Weapon(){{
                mirror = false;
                rotate = false;
                x = 0f;
                y = 6f;
                reload = 12f;
                shootCone = 40f;

                shootSound = Z_Sounds.energyBolt;
                bullet = new LaserBoltBulletType(6.5f, 60f){{
                    width = 4f;
                    height = 12f;
                    keepVelocity = false;
                    healPercent = 3.5f;
                    lifetime = 35f;
                    collidesTeam = true;
                    frontColor = UnityPal.monolithLight;
                    backColor = UnityPal.monolith;
                    smokeEffect = hitEffect = despawnEffect = MonolithFx.hitMonolithLaser;
                }};
            }}, new Weapon("create-monolith-small-weapon-mount"){{
                top = false;
                mirror = alternate = true;
                x = 3f;
                y = 3f;
                reload = 40f;
                shoot.shots = 2;      // v155.4: shots → shoot.shots
                shoot.shotDelay = 5f; // v155.4: shotDelay → shoot.shotDelay
                shootCone = 20f;

                // ★ v155.4: Sounds.lasershoot → Sounds.shootLaser
                shootSound = Sounds.shootLaser;
                bullet = new LaserBoltBulletType(4f, 30f){{
                    healPercent = 1.5f;
                    lifetime = 40f;
                    collidesTeam = true;
                    frontColor = UnityPal.monolithLight;
                    backColor = UnityPal.monolith;
                    smokeEffect = hitEffect = despawnEffect = MonolithFx.hitMonolithLaser;
                }};
            }});
        }};
    }

    /**
     * pedestal — 基座机甲 (PU132 L247-352)。
     *
     * <p>双武器组合:</p>
     * <ul>
     *   <li>侧炮: {@link RicochetBulletType} 弹射弹 — 命中后弹跳到下一个敌人,
     *       生成 3 发闪电伴随弹;</li>
     *   <li>主炮: {@link ChargeShotgunWeapon} 蓄力霰弹 — 子弹环绕装填后齐射,
     *       drawCharge 覆写让装填中的子弹直接显示弹体贴图。</li>
     * </ul>
     */
    private static void loadPedestal(){
        pedestal = new UnityUnitType("pedestal"){{
            // PU132: Mechc 实体
            constructor = mindustry.gen.MechUnit::create;

            health = 1200f;
            speed = 0.5f;
            rotateSpeed = 2.6f;
            hitSize = 11f;
            armor = 10f;
            singleTarget = true;
            maxSouls = 4; // TODO: 灵魂机制未移植, 数据占位

            canBoost = true;
            boostMultiplier = 2.5f;
            engineSize = 3.5f;
            engineOffset = 6f;
            outlineColor = UnityPal.darkOutline;

            weapons.add(new Weapon(name + "-gun"){{
                top = false;
                x = 10.75f;
                y = 2.25f;

                reload = 40f;
                recoil = 3.2f;
                shootSound = Z_Sounds.energyBolt;

                // 伴随闪电弹 (主弹命中前即生成, 沿弹道飞行)
                mindustry.entities.bullet.BulletType subBullet = new LightningBulletType();
                subBullet.damage = 24f;

                bullet = new RicochetBulletType(3f, 72f, "shell"){
                    {
                        width = 16f;
                        height = 20f;
                        lifetime = 36f;
                        frontColor = UnityPal.monolithLight;
                        backColor = UnityPal.monolith.cpy().mul(0.75f);
                        trailColor = UnityPal.monolithDark.cpy().mul(0.5f);

                        trailChance = 0.25f;
                        trailEffect = MonolithFx.ricochetTrailBig;
                        shootEffect = Fx.hitLaserBlast;
                        smokeEffect = Fx.lightningShoot;
                        hitEffect = despawnEffect = MonolithFx.monolithHitBig;
                    }

                    @Override
                    public void init(mindustry.gen.Bullet b){
                        super.init(b);
                        for(int i = 0; i < 3; i++){
                            subBullet.create(b, b.x, b.y, b.rotation());
                            // ★ v155.4: Sounds.spark → Sounds.shootArc
                            Sounds.shootArc.at(b.x, b.y, arc.math.Mathf.random(0.6f, 0.8f));
                        }
                    }
                };
            }}, new ChargeShotgunWeapon(""){
                {
                    mirror = false;
                    rotate = true;
                    rotateSpeed = 8f;
                    x = 0f;
                    y = 4f;
                    shootX = 0f;
                    shootY = 24f;
                    shoot.shots = 5;       // v155.4: shots → shoot.shots
                    shoot.shotDelay = 3f;   // v155.4: shotDelay → shoot.shotDelay
                    reload = 72f;
                    addSequenceTime = 25f;
                    shootCone = 90f;

                    addEffect = MonolithFx.pedestalShootAdd;
                    addedEffect = MonolithFx.monolithHitBig;
                    shootSound = Z_Sounds.chainyShot;

                    // ★ 贴图名适配: "unity-twisting-shell" → "create-twisting-shell" (mod 贴图前缀规则)
                    bullet = new BasicBulletType(6f, 32f, "create-twisting-shell"){{
                        width = 12f;
                        height = 16f;
                        shrinkY = 0f;
                        lifetime = 36f;
                        homingPower = 0.07f;
                        // PU132: homingRange = range() * 2f
                        homingRange = speed * lifetime * 2f;

                        frontColor = UnityPal.monolith;
                        backColor = UnityPal.monolithDark;

                        trailChance = 0.25f;
                        trailEffect = MonolithFx.ricochetTrailBig;
                        shootEffect = Fx.hitLaserBlast;
                        smokeEffect = Fx.lightningShoot;
                        hitEffect = despawnEffect = MonolithFx.monolithHitBig;
                    }};
                }

                /**
                 * 装填中的子弹绘制: 直接把弹体贴图 (前后两层) 画在盘旋位置。
                 */
                @Override
                public void drawCharge(float x, float y, float rotation, float shootAngle, mindustry.gen.Unit unit, ChargeShotgunMount mount){
                    if(bullet instanceof BasicBulletType b){
                        float z = arc.graphics.g2d.Draw.z();
                        arc.graphics.g2d.Draw.z(mindustry.graphics.Layer.bullet);

                        arc.graphics.g2d.Draw.color(b.backColor);
                        arc.graphics.g2d.Draw.rect(b.backRegion, x, y, b.width, b.height, shootAngle - 90f);
                        arc.graphics.g2d.Draw.color(b.frontColor);
                        arc.graphics.g2d.Draw.rect(b.frontRegion, x, y, b.width, b.height, shootAngle - 90f);

                        arc.graphics.g2d.Draw.z(z);
                    }
                }
            });
        }};
    }

    /**
     * pilaster — 壁柱机甲 (PU132 L354-569)。
     *
     * <p>重型机甲, 双武器:</p>
     * <ul>
     *   <li>中炮: 激光 (LaserBulletType 160 伤害, 双侧 60° 副光束);</li>
     *   <li>大炮: 旋转壳弹 — 命中/消散时爆出 1 个 "链电场" (fragBullet):
     *       持续 96 tick 的圆形力场, 每 16 tick 链电击 96 范围内最近的
     *       3 个敌人 (闪电视觉 + 直接伤害), 中心为多层 shiningCircle 光环;</li>
     *   <li>壳弹自带 3 条幻影拖尾, 围绕弹体公转 (updateTrail 覆写)。</li>
     * </ul>
     */
    private static void loadPilaster(){
        pilaster = new UnityUnitType("pilaster"){{
            // PU132: Mechc 实体
            constructor = mindustry.gen.MechUnit::create;

            health = 2000f;
            speed = 0.4f;
            rotateSpeed = 2.2f;
            hitSize = 26.5f;
            armor = 15f;
            mechFrontSway = 0.55f;
            maxSouls = 5; // TODO: 灵魂机制未移植, 数据占位

            canBoost = true;
            boostMultiplier = 2.5f;
            engineSize = 5f;
            engineOffset = 10f;

            // ★ v158 已移除单位弹药系统, ammoType = new PowerAmmoType(1000) 删除 (同上)
            outlineColor = UnityPal.darkOutline;

            weapons.add(new Weapon("create-monolith-medium-weapon-mount"){{
                top = false;
                x = 4f;
                y = 7.5f;
                shootY = 6f;

                rotate = true;
                recoil = 3f;
                reload = 40f;
                // ★ v155.4: Sounds.laser → Sounds.shootLaser
                shootSound = Sounds.shootLaser;

                bullet = new LaserBulletType(160f){{
                    lifetime = 27f;
                    width = 20f;
                    sideAngle = 60f;
                    smokeEffect = MonolithFx.phantasmalLaserShoot;
                }};
            }}, new Weapon("create-monolith-large-weapon-mount"){{
                top = false;
                x = 13f;
                y = 2f;
                shootY = 10.5f;

                rotate = true;
                rotateSpeed = 10f;
                recoil = 2.5f;
                reload = 120f;
                shootSound = Z_Sounds.chainyShot;

                // ★ 贴图名适配: "unity-twisting-shell" → "create-twisting-shell" (mod 贴图前缀规则)
                bullet = new BasicBulletType(2.7f, 32f, "create-twisting-shell"){
                    {
                        width = 16f;
                        height = 20f;
                        shrinkY = 0f;
                        lifetime = 54f;
                        // TODO: v155.4 无 scaleVelocity 字段 (按目标距离缩放初速), 省略

                        frontColor = UnityPal.monolith;
                        backColor = UnityPal.monolithDark;
                        trailColor = UnityPal.monolithLight;
                        trailLength = 32;
                        trailWidth = 1f;
                        trailChance = 0.33f;

                        shootEffect = Fx.hitLaserBlast;
                        smokeEffect = MonolithFx.tendenceShoot;
                        hitEffect = despawnEffect = MonolithFx.monolithHitBig;

                        fragBullets = 1;
                        fragVelocityMin = fragVelocityMax = 0f;
                        fragBullet = new BulletType(0f, 16f){
                            private final Seq<Healthc> all = new Seq<>();

                            {
                                lifetime = 96f;
                                absorbable = hittable = collides = false;
                                keepVelocity = false;
                                // ★ v155.4: Sounds.spark → Sounds.shootArc
                                hitSound = Sounds.shootArc;
                                hitEffect = despawnEffect = Fx.none;
                            }

                            /** 力场强度进度: 前 10% 淡入 pow5Out, 后 20% 淡出 pow3Out。 */
                            float frac(Bullet b){
                                return Interp.pow5Out.apply(Mathf.curve(b.fin(), 0f, 0.1f)) * Interp.pow3Out.apply(1f - Mathf.curve(b.fin(), 0.8f, 1f));
                            }

                            /** 力场当前半径: 26f × 强度 + absin 脉动。 */
                            float radius(Bullet b){
                                float s = frac(b);
                                return 26f * s + Mathf.absin(6f, 3f) * s;
                            }

                            @Override
                            public void update(Bullet b){
                                updateTrail(b);

                                float r = radius(b);
                                if(Mathf.chanceDelta(0.17f)){
                                    Tmp.v1.trns(Mathf.random(360f), Mathf.random(r)).add(b);
                                    ParticleFx.monolithSpark.at(Tmp.v1.x, Tmp.v1.y, 0f);
                                }

                                if(Mathf.chanceDelta(0.33f)){
                                    ParticleFx.lightningPivot.at(b.x, b.y, UnityPal.monolith);
                                }

                                if(b.timer(0, 60f)){
                                    MonolithFx.monolithRingEffect.at(b.x, b.y, 0f, 1f);
                                }

                                // 每 16 tick: 链电击范围内最近 3 个敌人
                                if(b.timer(1, 16f)){
                                    all.clear();
                                    Units.nearbyEnemies(b.team, b.x, b.y, 96f, all::add);
                                    Units.nearbyBuildings(b.x, b.y, 96f, e -> {
                                        if(e.isValid() && e.team != b.team) all.add(e);
                                    });

                                    all.sort((Floatf<Healthc>)b::dst2);

                                    int len = Math.min(all.size, 3);
                                    for(int i = 0; i < len; i++){
                                        Healthc target = all.get(i);
                                        target.damage(damage);

                                        Fx.chainLightning.at(b.x, b.y, 0f, UnityPal.monolithLight, target);
                                        Fx.hitLancer.at(target);
                                    }

                                    if(len > 0) hitSound.at(b);
                                }
                            }

                            @Override
                            public void draw(Bullet b){
                                float r = radius(b);

                                // 双色光球: 内暗外亮
                                Fill.light(b.x, b.y, Lines.circleVertices(r), r, Tmp.c1.set(UnityPal.monolithDark).a(0f), Tmp.c2.set(UnityPal.monolith).a(0.8f));
                                Lines.stroke(2f, UnityPal.monolithLight);
                                Lines.circle(b.x, b.y, r);

                                // 三层递减的 lancerLaser 色闪光圆环
                                float ir = r / 4f;
                                Draw.color(Tmp.c1.set(Pal.lancerLaser).a(0.4f));
                                UnityDrawf.shiningCircle(b.id, Time.time * 0.67f, b.x, b.y, ir, 4, 16f, 30f, ir * 2f, 90f);

                                ir *= 0.5f;
                                Draw.color(Tmp.c1.set(Pal.lancerLaser));
                                UnityDrawf.shiningCircle(b.id, Time.time * 0.67f, b.x, b.y, ir, 4, 16f, 30f, ir * 2f, 90f);

                                ir *= 0.5f;
                                Draw.color();
                                UnityDrawf.shiningCircle(b.id, Time.time * 0.67f, b.x, b.y, ir, 4, 16f, 30f, ir * 2f, 90f);

                                Draw.reset();
                            }
                        };
                    }

                    /**
                     * 拖尾覆写: 3 条幻影拖尾围绕弹体公转
                     * (公转角 = b.id*56 + 时间*4 + 相位差 120°, 半径 8f)。
                     * <p>★ 注意: 匿名 MultiTrail 初始化块里的 trailChance/trailColor
                     * 解析到外层 BulletType 的字段 (PU132 同样作用域行为)。</p>
                     */
                    @Override
                    public void updateTrail(Bullet b){
                        if(!headless && trailLength > 0 && b.trail == null) b.trail = new MultiTrail(
                            new TrailHold(Trails.phantasmal(trailLength)),
                            new TrailHold(Trails.phantasmal(trailLength)),
                            new TrailHold(Trails.phantasmal(trailLength)))
                        {
                            boolean dead;
                            float time = Time.time;

                            {
                                trailChance = 0.25f;
                                trailColor = UnityPal.monolithLight;
                            }

                            @Override
                            public void update(float x, float y, float width){
                                if(!dead){
                                    time += Time.delta * 10f * (Mathf.randomSeed(b.id, 0, 1) * 2 - 1);
                                    if(!b.isAdded()) dead = true;
                                }

                                for(int i = 0; i < trails.length; i++){
                                    TrailHold trail = trails[i];
                                    Tmp.v1.trns(b.id * 56f + Time.time * 4f + 360f / trails.length * i, 8f).add(x, y);

                                    trail.trail.update(Tmp.v1.x, Tmp.v1.y, width * trail.width);
                                    if(trailChance > 0f && Mathf.chanceDelta(trailChance)){
                                        trailEffect.at(Tmp.v1.x, Tmp.v1.y, trail.width * trailWidth, trailColor);
                                    }
                                }

                                lastX = x;
                                lastY = y;
                            }

                            @Override
                            public void drawCap(Color color, float width){}

                            @Override
                            public void draw(Color color, float width){
                                for(TrailHold trail : trails){
                                    Trail t = trail.trail;

                                    Color col = trail.color == null ? color : trail.color;
                                    float w = width * trail.width;

                                    t.drawCap(col, w);
                                    t.draw(col, w);
                                }
                            }
                        };

                        super.updateTrail(b);
                    }

                    @Override
                    public void removed(Bullet b){
                        super.removed(b);
                        b.trail = null;
                    }
                };
            }});
        }};
    }

    /**
     * pylon / monument — 塔式巨构 (PU132 L571-696)。
     *
     * <p>多足悬浮巨兽 (Legsc + hovering):</p>
     * <ul>
     *   <li>pylon: 主激光 (2000 伤害 + 24 波闪电辐射) + 副激光,
     *       主激光开火时自身定身 (unmoving) — 类似炮台 "架设" 蓄力;</li>
     *   <li>monument: 双激光 + 中轴线电磁炮 (6000 伤害, 540 射程)。</li>
     * </ul>
     */
    private static void loadLegUnits(){
        // ===== pylon (PU132 L571-626) =====
        pylon = new UnityUnitType("pylon"){{
            // PU132: Legsc 实体 (多足)
            constructor = mindustry.gen.LegsUnit::create;

            health = 14400f;
            speed = 0.43f;
            rotateSpeed = 1.48f;
            hitSize = 36f;
            armor = 23f;
            // TODO: PU132 commandLimit = 8 (指挥半径系统) — v155.4 无该字段
            maxSouls = 7; // TODO: 灵魂机制未移植, 数据占位

            allowLegStep = hovering = true;
            // TODO: PU132 visualElevation = 0.2f — v155.4 无该字段
            legCount = 4;
            legExtension = 8f;
            legSpeed = 0.08f;
            legLength = 16f;
            legMoveSpace = 1.2f;
            legForwardScl = 0.5f; // ★ v155.4: PU132 legTrns → legForwardScl
            legBaseOffset = 11f;

            // ★ v158 已移除单位弹药系统, ammoType = new PowerAmmoType(2000) 删除 (同上)
            groundLayer = Layer.legUnit;
            outlineColor = UnityPal.darkOutline;

            weapons.add(new Weapon(name + "-laser"){{
                soundPitchMin = 1f;
                top = false;
                mirror = false;
                shake = 15f;
                shootY = 11f;
                x = y = 0f;
                reload = 280f;
                recoil = 0f;
                cooldownTime = 280f;

                // 开火时自身定身 1.8s (架设代价)
                shootStatusDuration = 60f * 1.8f;
                shootStatus = StatusEffects.unmoving;
                // ★ v155.4: Sounds.laserblast → Sounds.beamMeltdown
                shootSound = Sounds.beamMeltdown;
                // ★ v155.4: Sounds.lasercharge → Sounds.chargeLancer
                chargeSound = Sounds.chargeLancer;
                // PU132: firstShotDelay = UnityFx.pylonLaserCharge.lifetime / 2f
                // v155.4: firstShotDelay 移入 shoot
                shoot.firstShotDelay = MonolithFx.pylonLaserCharge.lifetime / 2f;

                bullet = UnityBullets.pylonLaser;
            }}, new Weapon("create-monolith-large2-weapon-mount"){{
                x = 14f;
                y = 5f;
                shootY = 14f;

                rotate = true;
                rotateSpeed = 3.5f;
                // ★ v155.4: Sounds.laser → Sounds.shootLaser
                shootSound = Sounds.shootLaser;
                shake = 5f;
                reload = 20f;
                recoil = 4f;

                bullet = UnityBullets.pylonLaserSmall;
            }});
        }};

        // ===== monument (PU132 L628-696) =====
        monument = new UnityUnitType("monument"){{
            // PU132: Legsc 实体 (多足)
            constructor = mindustry.gen.LegsUnit::create;

            health = 32000f;
            speed = 0.42f;
            rotateSpeed = 1.4f;
            hitSize = 48f;
            armor = 32f;
            // TODO: PU132 commandLimit = 8 — v155.4 无该字段
            maxSouls = 9; // TODO: 灵魂机制未移植, 数据占位

            // TODO: PU132 visualElevation = 0.3f — v155.4 无该字段
            allowLegStep = hovering = true;
            legCount = 6;
            legLength = 30f;
            legExtension = 8f;
            legSpeed = 0.1f;
            legForwardScl = 0.5f; // ★ v155.4: PU132 legTrns → legForwardScl
            legBaseOffset = 15f;
            legMoveSpace = 1.2f;
            legPairOffset = 3f;
            legSplashDamage = 64f;
            legSplashRange = 48f;

            // ★ v158 已移除单位弹药系统, ammoType = new PowerAmmoType(2000) 删除 (同上)
            groundLayer = Layer.legUnit;
            outlineColor = UnityPal.darkOutline;

            LaserBulletType laser = new LaserBulletType(640f);
            weapons.add(new Weapon("create-monolith-large2-weapon-mount"){{
                top = false;
                x = 14f;
                y = 12f;
                shootY = 14f;

                rotate = true;
                rotateSpeed = 3.5f;
                reload = 36f;
                recoil = shake = 5f;
                // ★ v155.4: Sounds.laser → Sounds.shootLaser
                shootSound = Sounds.shootLaser;

                bullet = laser;
            }}, new Weapon("create-monolith-large2-weapon-mount"){{
                top = false;
                x = 20f;
                y = 3f;
                shootY = 14f;

                rotate = true;
                rotateSpeed = 3.5f;
                reload = 48f;
                recoil = shake = 5f;
                // ★ v155.4: Sounds.laser → Sounds.shootLaser
                shootSound = Sounds.shootLaser;

                bullet = laser;
            }}, new Weapon("create-monolith-railgun-big"){{
                mirror = false;
                x = 0f;
                y = -12f;
                shootY = 35f;
                shadow = 30f;

                reload = 200f;
                recoil = shake = 8f;
                shootCone = 2f;
                cooldownTime = 210f;
                // ★ v155.4: Sounds.railgun → Sounds.shootForeshadow
                shootSound = Sounds.shootForeshadow;

                bullet = UnityBullets.monumentRailBullet;
            }});
        }};
    }

    /**
     * colossus / bastion — 巨像 / 堡垒 (PU132 L698-885)。
     *
     * <p>超重型多足巨兽:</p>
     * <ul>
     *   <li>colossus: 环绕闪电球能力 (8 球) + 主巨激光
     *       (1920 伤害, 400 射程, lancerLaser 闪电链);</li>
     *   <li>bastion: 12 闪电球 + 三组弹幕武器 —
     *       弹射弹 (RicochetBulletType) 高速连射,
     *       主炮 pierceCap=6 且每次更新随机放闪电。</li>
     * </ul>
     */
    private static void loadGiantUnits(){
        // ===== colossus (PU132 L698-754) =====
        colossus = new UnityUnitType("colossus"){{
            // PU132: Legsc 实体 (多足)
            constructor = mindustry.gen.LegsUnit::create;

            health = 60000f;
            speed = 0.4f;
            rotateSpeed = 1.2f;
            hitSize = 64f;
            armor = 45f;
            // TODO: PU132 commandLimit = 8 — v155.4 无该字段
            maxSouls = 12; // TODO: 灵魂机制未移植, 数据占位

            // TODO: PU132 visualElevation = 0.5f — v155.4 无该字段
            allowLegStep = hovering = true;
            legCount = 6;
            legLength = 48f;
            legExtension = 12f;
            legSpeed = 0.1f;
            legForwardScl = 0.5f; // ★ v155.4: PU132 legTrns → legForwardScl
            legBaseOffset = 15f;
            legMoveSpace = 0.82f;
            legPairOffset = 3f;
            legSplashDamage = 84f;
            legSplashRange = 48f;

            // ★ v158 已移除单位弹药系统, ammoType = new PowerAmmoType(2000) 删除 (同上)
            groundLayer = Layer.legUnit;
            outlineColor = UnityPal.darkOutline;

            abilities.add(new LightningSpawnAbility(8, 32f, 2f, 0.05f, 180f, 56f, 200f));

            weapons.add(new Weapon(name + "-weapon"){{
                top = false;
                x = 30f;
                y = 7.75f;
                shootY = 20f;

                reload = 144f;
                recoil = 8f;
                // ★ v155.4: PU132 spacing=1f (平行弹幕) → ShootSpread 角度扇形近似
                shoot = new ShootSpread(5, 2f);
                shoot.shotDelay = 3f; // v155.4: shotDelay → shoot.shotDelay
                inaccuracy = 6f;
                // ★ v155.4: Sounds.laserblast → Sounds.beamMeltdown
                shootSound = Sounds.beamMeltdown;

                bullet = new LaserBulletType(1920f){{
                    width = 45f;
                    length = 400f;
                    lifetime = 32f;

                    lightningSpacing = 35f;
                    lightningLength = 4;
                    lightningDelay = 1.5f;
                    lightningLengthRand = 6;
                    lightningDamage = 48f;
                    lightningAngleRand = 30f;
                    lightningColor = Pal.lancerLaser;
                }};
            }});
        }};

        // ===== bastion (PU132 L756-885) =====
        bastion = new UnityUnitType("bastion"){{
            // PU132: Legsc 实体 (多足)
            constructor = mindustry.gen.LegsUnit::create;

            health = 120000f;
            speed = 0.4f;
            rotateSpeed = 1.2f;
            hitSize = 67f;
            armor = 100f;
            // TODO: PU132 commandLimit = 8 — v155.4 无该字段
            maxSouls = 15; // TODO: 灵魂机制未移植, 数据占位

            // TODO: PU132 visualElevation = 0.7f — v155.4 无该字段
            allowLegStep = hovering = true;
            legCount = 6;
            legLength = 72f;
            legExtension = 16f;
            legSpeed = 0.12f;
            legForwardScl = 0.6f; // ★ v155.4: PU132 legTrns → legForwardScl
            legBaseOffset = 18f;
            legMoveSpace = 0.6f;
            legPairOffset = 3f;
            legSplashDamage = 140f;
            legSplashRange = 56f;

            // ★ v158 已移除单位弹药系统, ammoType = new PowerAmmoType(2000) 删除 (同上)
            groundLayer = Layer.legUnit;
            outlineColor = UnityPal.darkOutline;

            abilities.add(new LightningSpawnAbility(12, 16f, 3f, 0.05f, 300f, 96f, 640f));

            // 副武器共用弹: 弹射弹 (弹跳 3 次后转向下一目标)
            BulletType energy = new RicochetBulletType(6f, 50f, "shell"){{
                width = 9f;
                height = 11f;
                shrinkY = 0.3f;
                lifetime = 45f;
                weaveScale = weaveMag = 3f;
                trailChance = 0.3f;

                frontColor = UnityPal.monolithLight;
                backColor = UnityPal.monolith;
                trailColor = UnityPal.monolithDark;
                shootEffect = Fx.lancerLaserShoot;
                smokeEffect = Fx.hitLancer;
                hitEffect = despawnEffect = MonolithFx.monolithHitSmall;

                splashDamage = 60f;
                splashDamageRadius = 10f;

                lightning = 3;
                lightningDamage = 12f;
                lightningColor = Pal.lancerLaser;
                lightningLength = 6;
            }};

            weapons.add(new Weapon(name + "-mount"){{
                x = 9f;
                y = -11.5f;
                shootY = 10f;

                rotate = true;
                rotateSpeed = 8f;

                reload = 24f;
                recoil = 6f;
                // ★ v155.4: PU132 spacing=5f → ShootSpread 扇形近似
                shoot = new ShootSpread(8, 6f);
                velocityRnd = 0.3f;
                shootSound = Z_Sounds.energyBolt;

                bullet = energy;
            }}, new Weapon(name + "-mount"){{
                x = 23.5f;
                y = 5.5f;
                shootY = 10f;

                rotate = true;
                rotateSpeed = 8f;

                reload = 15f;
                recoil = 6f;
                // ★ v155.4: PU132 spacing=6f → ShootSpread 扇形近似
                shoot = new ShootSpread(5, 7f);
                velocityRnd = 0.3f;
                shootSound = Z_Sounds.energyBolt;

                bullet = energy;
            }}, new Weapon(name + "-gun"){{
                x = 12.5f;
                y = 12f;
                shootY = 13.5f;

                rotate = true;
                rotateSpeed = 6f;
                shoot.shots = 8;         // v155.4: shots → shoot.shots
                shoot.shotDelay = 3f;    // v155.4: shotDelay → shoot.shotDelay

                reload = 30f;
                recoil = 8f;
                // ★ v155.4: Sounds.shootBig → Sounds.shootArtillery
                shootSound = Sounds.shootArtillery;

                bullet = new RicochetBulletType(12.5f, 640f, "shell"){
                    {
                        width = 20f;
                        height = 25f;
                        shrinkY = 0.2f;
                        lifetime = 30f;
                        trailLength = 3;
                        pierceCap = 6;

                        frontColor = Color.white;
                        backColor = UnityPal.monolithLight;
                        trailColor = UnityPal.monolith;
                        shootEffect = Fx.lancerLaserShoot;
                        smokeEffect = Fx.hitLancer;
                        hitEffect = despawnEffect = MonolithFx.monolithHitBig;

                        lightning = 3;
                        lightningDamage = 12f;
                        lightningColor = Pal.lancerLaser;
                        lightningLength = 15;
                    }

                    /**
                     * 飞行途中 30% 概率/tick 在弹体位置放出半长闪电。
                     */
                    @Override
                    public void update(Bullet b){
                        super.update(b);
                        if(Mathf.chanceDelta(0.3f)){
                            Lightning.create(b, lightningColor, lightningDamage, b.x, b.y, b.rotation(), lightningLength / 2);
                        }
                    }
                };
            }});
        }};
    }
}