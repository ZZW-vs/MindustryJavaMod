package zzw.content.blocks.soul;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.draw.DrawBlock;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import zzw.content.Z_Sounds;
import zzw.content.units.effects.UnityDrawf;

/**
 * Supernova 灵魂激光炮台 (v158 完整移植版, 严格遵循 PU_V8 原版结构)
 *
 * = SoulLaserTurret (LaserTurret + ISoulTurret) + Supernova 充能机制
 *
 * 原版机制 (完全照搬 PU_V8 SupernovaTurret.java):
 * - drawer lambda: 绘制 6 部件 (core/wings/bottom/head) + outline 两层
 * - heatDrawer lambda: heat region 加法混合渲染
 * - charge/phase/starHeat 三阶段充能
 * - attractUnits: 吸引范围内单位 + 持续伤害 + 拉拽特效
 * - 充能满后 (charge >= 1 && phase >= 1): shoot(type)
 * - 激光期间: 持续闪电 + 星辰衰减特效 + 热浪特效
 * - draw(): UnityDrawf.shiningCircle 绘制星辰闪光环
 * - PitchedSoundLoop: 充能循环音效
 *
 * ★ v158 适配 (最小改动, 保留原版结构):
 * 1. drawer/heatDrawer lambda (Cons<SupernovaTurretBuild>) → 合并为自定义 DrawBlock 子类
 *    (v158 Turret.drawer 是 DrawBlock 类型, 非 Cons)
 * 2. tr2 (PU_V8 TurretBuild 字段) → recoilOffset (v158 字段)
 * 3. charge 字段 → novaCharge (避免 shadow v158 TurretBuild.charge)
 * 4. bulletLife <= 0f && bullet == null → bullets.isEmpty() (v158 LaserTurret 用 Seq<BulletEntry>)
 * 5. consValid() → canConsume()
 * 6. efficiency() → efficiency (字段访问)
 * 7. PitchedSoundLoop → v158 内置 soundLoop (重写 shouldActiveSound/activeSoundVolume)
 * 8. UnitySounds.supernovaCharge → Z_Sounds.supernovaActive (无独立 charge 音效)
 * 9. UnityFx.supernovaXxx → Fx.* 等效替代
 * 10. Regions.supernovaXxxRegion → TextureRegion 字段 + load() 手动加载
 * 11. UnityPal.monolith → Color.valueOf("87ceeb")
 * 12. SVec2.construct → new Vec2(x, y)
 * 13. Utils.pow6In → Interp.pow6In (v158 arc-core 已有)
 *
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/SupernovaTurret.java
 */
public class SupernovaTurret extends SoulLaserTurret {
    public float chargeWarmup = 0.002f;
    public float chargeCooldown = 0.01f;

    /** PU_V8: chargeSound = UnitySounds.supernovaCharge; v158 用 Z_Sounds.supernovaActive 替代 */
    public float chargeSoundVolume = 1f;

    public float attractionStrength = 6f;
    public float attractionDamage = 60f;

    /** Temporary vector array to be used in the drawing method (与原版完全一致) */
    private static final Vec2[] phases = {new Vec2(), new Vec2(), new Vec2(), new Vec2(), new Vec2(), new Vec2()};
    public float starRadius = 8f;
    public float starOffset = -2.25f;

    /** v158 支持 timers++ 模式分配 timer 索引 (与 PU_V8 一致) */
    public final int timerChargeStar = timers++;

    // PU_V8 UnityFx.supernovaXxx → v158 Fx.* 等效替代
    public Effect chargeStarEffect = Fx.lightningShoot;
    public Effect chargeStar2Effect = Fx.sparkShoot;
    public Effect starDecayEffect = Fx.smoke;
    public Effect heatWaveEffect = Fx.ripple;
    public Effect pullEffect = Fx.healBlockFull;
    /** 充能起始特效 (PU_V8: 继承自 PowerTurret.chargeBeginEffect; v158: 自定义字段) */
    public Effect chargeBeginEffect = Fx.lightningShoot;

    /** v158 Turret 无 shootLength 字段, 自定义 (PU_V8 Turret.shootLength 用于炮口位置计算) */
    public float shootLength = 8f;

    /** PU_V8: UnityPal.monolith → v158: Color.valueOf("87ceeb") */
    public static final Color monolithColor = Color.valueOf("87ceeb");

    // PU_V8 Regions.supernovaXxxRegion (@LoadRegs 注解自动生成) → v158: TextureRegion 字段 + load() 加载
    public TextureRegion supernovaHeadRegion, supernovaCoreRegion;
    public TextureRegion supernovaWingLeftRegion, supernovaWingRightRegion;
    public TextureRegion supernovaWingLeftBottomRegion, supernovaWingRightBottomRegion;
    public TextureRegion supernovaBottomRegion;
    public TextureRegion supernovaHeadOutlineRegion, supernovaCoreOutlineRegion;
    public TextureRegion supernovaWingLeftOutlineRegion, supernovaWingRightOutlineRegion;
    public TextureRegion supernovaWingLeftBottomOutlineRegion, supernovaWingRightBottomOutlineRegion;
    public TextureRegion supernovaBottomOutlineRegion;
    /** heat 贴图 (PU_V8 heatRegion; v158 DrawTurret.heat, 但我们用自定义 DrawBlock 所以自定义) */
    public TextureRegion heatRegion;
    /** 底座贴图 (PU_V8: baseRegion = "unity-block-" + size; v158: 自定义加载) */
    public TextureRegion baseRegion;

    public SupernovaTurret(String name) {
        super(name);

        // PU_V8 chargeSound = UnitySounds.supernovaCharge; v158 无 supernovaCharge, 用 supernovaActive 替代
        chargeSound = Z_Sounds.supernovaActive;
        // v158 用 loopSound + 内置 soundLoop 系统实现循环音效 (替代 PU_V8 PitchedSoundLoop)
        loopSound = Z_Sounds.supernovaActive;
        loopSoundVolume = chargeSoundVolume;

        // ★ v158 适配: drawer/heatDrawer lambda (Cons<SupernovaTurretBuild>) → 自定义 DrawBlock 子类
        // PU_V8: drawer = b -> {...}; heatDrawer = tile -> {...};
        // v158: Turret.drawer 是 DrawBlock 类型, 不是 Cons, 所以用 SupernovaDrawer 合并两者
        drawer = new SupernovaDrawer();
    }

    @Override
    public void load() {
        super.load();

        // 加载 6 部件贴图 (PU_V8 @LoadRegs 注解自动加载, v158 手动加载)
        supernovaHeadRegion = Core.atlas.find(name + "-head");
        supernovaCoreRegion = Core.atlas.find(name + "-core");
        supernovaWingLeftRegion = Core.atlas.find(name + "-wing-left");
        supernovaWingRightRegion = Core.atlas.find(name + "-wing-right");
        supernovaWingLeftBottomRegion = Core.atlas.find(name + "-wing-left-bottom");
        supernovaWingRightBottomRegion = Core.atlas.find(name + "-wing-right-bottom");
        supernovaBottomRegion = Core.atlas.find(name + "-bottom");

        // outline 版本 (PU_V8 @LoadRegs(outline = true) 自动生成)
        supernovaHeadOutlineRegion = Core.atlas.find(name + "-head-outline");
        supernovaCoreOutlineRegion = Core.atlas.find(name + "-core-outline");
        supernovaWingLeftOutlineRegion = Core.atlas.find(name + "-wing-left-outline");
        supernovaWingRightOutlineRegion = Core.atlas.find(name + "-wing-right-outline");
        supernovaWingLeftBottomOutlineRegion = Core.atlas.find(name + "-wing-left-bottom-outline");
        supernovaWingRightBottomOutlineRegion = Core.atlas.find(name + "-wing-right-bottom-outline");
        supernovaBottomOutlineRegion = Core.atlas.find(name + "-bottom-outline");

        // heat + base 贴图
        heatRegion = Core.atlas.find(name + "-heat");
        baseRegion = Core.atlas.find(name + "-base");
    }

    /**
     * PU_V8 drawer + heatDrawer lambda 合并实现 (v158 适配)
     *
     * PU_V8 原版:
     *   drawer = b -> { 绘制 6 部件 outline + 主体 + core (z+0.001) };
     *   heatDrawer = tile -> { heat region 加法混合渲染 };
     *
     * v158 适配: 合并到单个 DrawBlock.draw() 中 (heat 在主体之后绘制)
     */
    public class SupernovaDrawer extends DrawBlock {
        @Override
        public void draw(Building build) {
            if (!(build instanceof SupernovaTurretBuild)) {
                throw new IllegalStateException("building isn't an instance of SupernovaTurretBuild");
            }
            SupernovaTurretBuild tile = (SupernovaTurretBuild) build;

            // v158: tr2 (PU_V8 recoil 偏移向量) → recoilOffset
            float ox = tile.x + tile.recoilOffset.x;
            float oy = tile.y + tile.recoilOffset.y;
            float rot = tile.rotation - 90f;

            // === PU_V8 drawer lambda 完整移植 ===
            // core
            phases[0].trns(tile.rotation, -tile.recoilOffset.len() + Mathf.curve(tile.phase, 0f, 0.3f) * -2f);
            // left wing
            phases[1].trns(tile.rotation - 90,
                Mathf.curve(tile.phase, 0.2f, 0.5f) * -2f,
                -tile.recoilOffset.len() + Mathf.curve(tile.phase, 0.2f, 0.5f) * 2f +
                    Mathf.curve(tile.phase, 0.5f, 0.8f) * 3f
            );
            // left bottom wing
            phases[2].trns(tile.rotation - 90,
                Mathf.curve(tile.phase, 0f, 0.3f) * -1.5f +
                    Mathf.curve(tile.phase, 0.6f, 1f) * -2f,
                -tile.recoilOffset.len() + Mathf.curve(tile.phase, 0f, 0.3f) * 1.5f +
                    Mathf.curve(tile.phase, 0.6f, 1f) * -1f
            );
            // bottom
            phases[3].trns(tile.rotation, -tile.recoilOffset.len() + Mathf.curve(tile.phase, 0f, 0.6f) * -4f);
            // right wing
            phases[4].trns(tile.rotation - 90,
                Mathf.curve(tile.phase, 0.2f, 0.5f) * 2f,
                -tile.recoilOffset.len() + Mathf.curve(tile.phase, 0.2f, 0.5f) * 2f +
                    Mathf.curve(tile.phase, 0.5f, 0.8f) * 3f
            );
            // right bottom wing
            phases[5].trns(tile.rotation - 90,
                Mathf.curve(tile.phase, 0f, 0.3f) * 1.5f +
                    Mathf.curve(tile.phase, 0.6f, 1f) * 2f,
                -tile.recoilOffset.len() + Mathf.curve(tile.phase, 0f, 0.3f) * 1.5f +
                    Mathf.curve(tile.phase, 0.6f, 1f) * -1f
            );

            // outline 层 (PU_V8 原版顺序: wing-bottom → wing → bottom → head → core)
            Draw.rect(supernovaWingLeftBottomOutlineRegion, tile.x + phases[2].x, tile.y + phases[2].y, rot);
            Draw.rect(supernovaWingRightBottomOutlineRegion, tile.x + phases[5].x, tile.y + phases[5].y, rot);
            Draw.rect(supernovaWingLeftOutlineRegion, tile.x + phases[1].x, tile.y + phases[1].y, rot);
            Draw.rect(supernovaWingRightOutlineRegion, tile.x + phases[4].x, tile.y + phases[4].y, rot);
            Draw.rect(supernovaBottomOutlineRegion, tile.x + phases[3].x, tile.y + phases[3].y, rot);
            Draw.rect(supernovaHeadOutlineRegion, ox, oy, rot);
            Draw.rect(supernovaCoreOutlineRegion, tile.x + phases[0].x, tile.y + phases[0].y, rot);

            // 主体层
            Draw.rect(supernovaWingLeftBottomRegion, tile.x + phases[2].x, tile.y + phases[2].y, rot);
            Draw.rect(supernovaWingRightBottomRegion, tile.x + phases[5].x, tile.y + phases[5].y, rot);
            Draw.rect(supernovaWingLeftRegion, tile.x + phases[1].x, tile.y + phases[1].y, rot);
            Draw.rect(supernovaWingRightRegion, tile.x + phases[4].x, tile.y + phases[4].y, rot);
            Draw.rect(supernovaBottomRegion, tile.x + phases[3].x, tile.y + phases[3].y, rot);
            Draw.rect(supernovaHeadRegion, ox, oy, rot);

            // core 层 (z + 0.001f 微小提升, 与 PU_V8 一致)
            float z = Draw.z();
            Draw.z(z + 0.001f);
            Draw.rect(supernovaCoreRegion, tile.x + phases[0].x, tile.y + phases[0].y, rot);
            Draw.z(z);

            // === PU_V8 heatDrawer lambda 完整移植 (合并到 draw 末尾) ===
            if (tile.heat <= 0.00001f) return;

            // PU_V8: Utils.pow6In.apply(tile.heat) → v158: arc-core 无 pow6In, 用 pow5In 近似
            float r = Interp.pow5In.apply(tile.heat);
            float g = Interp.pow3In.apply(tile.heat);
            float b = Interp.pow2Out.apply(tile.heat);
            float a = Interp.pow2In.apply(tile.heat);

            Draw.color(Tmp.c1.set(r, g, b, a));
            Draw.blend(Blending.additive);

            Draw.rect(heatRegion, ox, oy, rot);

            Draw.color();
            Draw.blend();
        }

        @Override
        public TextureRegion[] icons(Block block) {
            // ★ 修复信息显示界面: 返回完整组合图标 (底座+所有部件)
            // 之前只返回 -head 单张图, 导致显示不全
            return new TextureRegion[]{
                Core.atlas.find(block.name + "-base"),
                Core.atlas.find(block.name + "-bottom"),
                Core.atlas.find(block.name + "-wing-left-bottom"),
                Core.atlas.find(block.name + "-wing-right-bottom"),
                Core.atlas.find(block.name + "-wing-left"),
                Core.atlas.find(block.name + "-wing-right"),
                Core.atlas.find(block.name + "-head"),
                Core.atlas.find(block.name + "-core")
            };
        }
    }

    public class SupernovaTurretBuild extends SoulLaserTurretBuild {
        /**
         * PU_V8: charge; v158 重命名为 novaCharge 避免与 TurretBuild.charge (充能进度) 字段冲突
         * TurretBuild.charge 用于 v158 的 chargeTime 机制 (float, 0~1)
         * PU_V8 supernova 的 charge 是超新星充能进度 (0~1), 语义不同, 必须分开
         */
        public float novaCharge;
        public float phase;
        public float starHeat;

        // PU_V8: PitchedSoundLoop sound = new PitchedSoundLoop(chargeSound, chargeSoundVolume);
        // v158: 通过重写 shouldActiveSound/activeSoundVolume 由父类 soundLoop 系统处理

        @Override
        public void updateTile() {
            // ★ PU_V8 逻辑完整移植, v158 API 适配
            // v158: bullets.isEmpty() 替代 (bulletLife <= 0f && bullet == null)
            // v158: canConsume() 替代 consValid()
            if (!isShooting() || !validateTarget() || !canConsume()) {
                novaCharge = Mathf.lerpDelta(novaCharge, 0f, chargeCooldown);
                novaCharge = novaCharge > 0.001f ? novaCharge : 0f;
            }

            if (isShooting() && bullets.isEmpty()) attractUnits();

            if (isShooting() || !bullets.isEmpty()) {
                phase = Mathf.clamp(phase + chargeWarmup * edelta(), 0f, 1f);
            } else {
                phase = Mathf.lerpDelta(phase, 0f, chargeCooldown);
                phase = phase > 0.001f ? phase : 0f;
            }

            super.updateTile();

            if (isShooting() && bullets.isEmpty()) {
                // PU_V8: consumes.<ConsumeLiquidBase>get(ConsumeType.liquid).amount
                // v158: BaseTurret.coolant (ConsumeLiquidBase 字段) + coolant.amount
                float used;
                if (coolant != null) {
                    Liquid liquid = liquids.current();
                    float maxUsed = coolant.amount;
                    // v158: cheating() 方法存在, efficiency 字段 (非方法)
                    used = baseReloadSpeed() * ((cheating() ? maxUsed : Math.min(liquids.get(liquid), maxUsed * Time.delta)) * liquid.heatCapacity * coolantMultiplier);
                } else {
                    // ★ v158 适配: 无 coolant consumer 时 (Z_AdvTurrets 未配置 consumeLiquid)
                    // 用等效默认值累积 novaCharge, 避免永远无法开炮
                    // 等效于 maxUsed=1, heatCapacity=1, coolantMultiplier=1
                    used = baseReloadSpeed() * Time.delta;
                }
                novaCharge = Mathf.clamp(novaCharge + 120f * chargeWarmup * used);
            }

            // v158: 音效由父类 soundLoop 系统 (shouldActiveSound + activeSoundVolume) 处理
            // PU_V8: sound.update(x, y, Mathf.curve(charge, 0f, 0.4f) * 1.2f, prog)
            // → v158: shouldActiveSound = novaCharge > 0.01; activeSoundVolume = curve(novaCharge, 0, 0.4) * 1.2

            boolean notShooting = bullets.isEmpty();
            boolean tick = Mathf.chanceDelta(1f);
            boolean tickCharge = Mathf.chanceDelta(novaCharge);

            starHeat = Mathf.approachDelta(starHeat, notShooting ? novaCharge : 1f, chargeWarmup * 60f);

            // v158: 用 recoilOffset.len() 替代 PU_V8 recoil (per-build 后坐距离)
            // recoilOffset 由父类 updateTile 更新: recoilOffset.trns(rotation, -Mathf.pow(curRecoil, recoilPow) * recoil)
            float recoilDist = recoilOffset.len();
            Tmp.v1.trns(rotation, -recoilDist + starOffset + Mathf.curve(phase, 0f, 0.3f) * -2f);
            if (notShooting) {
                if (novaCharge > 0.1f && timer(timerChargeStar, 20f)) {
                    chargeStarEffect.at(
                        x + Tmp.v1.x,
                        y + Tmp.v1.y,
                        rotation, novaCharge
                    );
                }

                if (!Mathf.zero(novaCharge) && tickCharge) {
                    chargeStar2Effect.at(
                        x + Tmp.v1.x,
                        y + Tmp.v1.y,
                        rotation, novaCharge
                    );
                }

                if (tickCharge) {
                    chargeBeginEffect.at(
                        x + Angles.trnsx(rotation, -recoilDist + shootLength),
                        y + Angles.trnsy(rotation, -recoilDist + shootLength),
                        rotation, novaCharge
                    );
                }
            } else {
                if (tick) {
                    starDecayEffect.at(
                        x + Tmp.v1.x,
                        y + Tmp.v1.y,
                        rotation
                    );
                }

                if (timer(timerChargeStar, 20f)) {
                    heatWaveEffect.at(
                        x + Tmp.v1.x,
                        y + Tmp.v1.y,
                        rotation
                    );
                }
            }

            if (Mathf.chanceDelta(notShooting ? novaCharge : 1f)) {
                Tmp.v1
                    .trns(rotation, -recoilDist + starOffset + Mathf.curve(phase, 0f, 0.3f) * -2f)
                    .add(this);

                Lightning.create(
                    team,
                    Pal.lancerLaser,
                    60f, Tmp.v1.x, Tmp.v1.y,
                    Mathf.randomSeed((long) (id + Time.time), 360f),
                    Mathf.round(Mathf.randomTriangular(12f, 18f) * (notShooting ? novaCharge : 1f))
                );
            }
        }

        @Override
        public void draw() {
            // ★ v158 适配: 父类 draw() 调用 drawer (SupernovaDrawer) 绘制 6 部件 + heat
            // PU_V8: super.draw() 调用 drawer lambda 绘制 6 部件, heatDrawer lambda 绘制 heat
            super.draw();

            boolean notShooting = bullets.isEmpty();
            Tmp.v1.trns(rotation, -recoilOffset.len() + starOffset + Mathf.curve(phase, 0f, 0.3f) * -2f);

            float z = Draw.z();
            Draw.z(Layer.effect);

            Draw.color(monolithColor);
            UnityDrawf.shiningCircle(id, Time.time,
                x + Tmp.v1.x,
                y + Tmp.v1.y,
                starHeat * starRadius,
                6, 20f,
                starHeat * starRadius, starHeat * starRadius * 1.5f,
                120f
            );

            if (notShooting) {
                if (!Mathf.zero(novaCharge)) {
                    UnityDrawf.shiningCircle(id + 1, Time.time,
                        x + Angles.trnsx(rotation, -recoilOffset.len() + shootLength),
                        y + Angles.trnsy(rotation, -recoilOffset.len() + shootLength),
                        novaCharge * 4f,
                        6, 12f,
                        novaCharge * 4f, novaCharge * 8f,
                        120f
                    );
                }
            }

            Draw.reset();
            Draw.z(z);
        }

        @Override
        public boolean isShooting() {
            // v158: efficiency 是字段 (非方法)
            return super.isShooting() && efficiency > 0f;
        }

        @Override
        protected void updateShooting() {
            // v158: bullets.isEmpty() 替代 (bulletLife > 0f && bullet != null)
            if (!bullets.isEmpty()) return;

            // v158: canConsume() 替代 consValid(), cheating() 方法存在
            if (novaCharge >= 1f && phase >= 1f && (canConsume() || cheating())) {
                BulletType type = peekAmmo();

                shoot(type);
                novaCharge = 0f;
            }
        }

        // ★ v158 音效系统: 重写 shouldActiveSound/activeSoundVolume 控制 soundLoop
        // 替代 PU_V8 PitchedSoundLoop.update(x, y, curve(charge, 0, 0.4) * 1.2f, prog)
        @Override
        public boolean shouldActiveSound() {
            return novaCharge > 0.01f && loopSound != Sounds.none;
        }

        @Override
        public float activeSoundVolume() {
            // PU_V8: Mathf.curve(charge, 0f, 0.4f) * 1.2f
            return Mathf.curve(novaCharge, 0f, 0.4f) * 1.2f;
        }

        // remove() 无需重写: v158 TurretBuild.remove() 已处理 soundLoop.stop()
        // PU_V8: sound.stop() 由 v158 父类自动处理

        /** PU_V8 attractUnits 完整移植: 吸引范围内单位 + 持续伤害 + 拉拽特效 */
        protected void attractUnits() {
            // v158: range() 方法存在 (BaseTurretBuild.range() 返回 block.range)
            float rad = range() * 2f;
            Units.nearby(x - rad, y - rad, rad * 2f, rad * 2f, unit -> {
                if (unit.isValid() && unit.within(this, rad)) {
                    float dst = unit.dst(this);
                    float strength = 1 - (dst / rad);
                    Tmp.v1.set(x - unit.x, y - unit.y)
                        .rotate(10f * (1f - novaCharge))
                        .setLength(attractionStrength * novaCharge * Time.delta)
                        .scl(strength);

                    unit.impulseNet(Tmp.v1);
                    if (unit.team != team) unit.damageContinuous((attractionDamage / 60f) * novaCharge * strength);

                    if (Mathf.chanceDelta(0.1f)) {
                        Tmp.v1
                            .trns(rotation, -recoilOffset.len() + starOffset + Mathf.curve(phase, 0f, 0.3f) * -2f)
                            .add(this);

                        // PU_V8: SVec2.construct(Tmp.v1.x, Tmp.v1.y) → v158: new Vec2(x, y)
                        pullEffect.at(unit.x, unit.y, novaCharge * (3f + Mathf.range(0.2f)), new Vec2(Tmp.v1.x, Tmp.v1.y));
                    }
                }
            });
        }
    }
}
