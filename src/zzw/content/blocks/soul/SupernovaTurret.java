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
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.consumers.ConsumeLiquidBase;
import mindustry.world.draw.DrawBlock;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import zzw.content.Z_Sounds;
import zzw.content.exp.UnityFx;
import zzw.content.units.effects.UnityDrawf;

/**
 * Supernova 超新星激光炮台 (v155.4 严格按 PU132 原版重写)
 *
 * 基于 PU132 unity.world.blocks.defense.turrets.SupernovaTurret 完整移植
 *
 * 原版机制 (完全照搬):
 * - drawer: 绘制 6 部件 (core/wings/bottom/head) + outline 两层
 * - heatDrawer: heat region 加法混合渲染
 * - charge/phase/starHeat 三阶段充能 (chargeWarmup=0.002f 原版值)
 * - attractUnits: 吸引范围内单位 + 持续伤害 + 拉拽特效
 * - 充能满后 (charge >= 1 && phase >= 1): shoot(type)
 * - 激光期间: 持续闪电 + 星辰衰减特效 + 热浪特效
 * - draw(): UnityDrawf.shiningCircle 绘制星辰闪光环
 *
 * v155.4 适配要点:
 * - drawer 是 DrawBlock 类型 (非 Cons), 用 SupernovaDrawer 子类实现
 * - 无独立 heatDrawer 字段, 合并到 SupernovaDrawer.draw() 中
 * - tr2 (PU132 Vec2) → recoilOffset (v155.4 Vec2)
 * - tile.recoil (float) → recoilOffset.len() (v155.4 无 recoil 字段)
 * - charge → novaCharge (避免与 TurretBuild.charge 字段冲突)
 * - bulletLife/bullet → bullets.isEmpty() (v155.4 LaserTurret 用 Seq<BulletEntry>)
 * - consValid() → canConsume()
 * - efficiency() → efficiency 字段
 * - PitchedSoundLoop → 重写 shouldActiveSound/activeSoundVolume
 *
 * 关键修复 (相对于之前版本):
 * 1. 移除 efficiency *= soulEfficiency() 副作用 (在 SoulLaserTurret 中重写不调用 super 即可)
 * 2. ★ chargeWarmup 保持原版 0.002f (之前改 0.015 太快, 充能动画错乱)
 * 3. ★ phase 累积用 chargeWarmup * edelta() (原版公式, 之前直接写 0.015 错误)
 * 4. ★ 完全按原版顺序: charge 衰减 → attractUnits → phase 累积 → super.updateTile → charge 累积
 *
 * 参考: PU132 main/src/unity/world/blocks/defense/turrets/SupernovaTurret.java
 */
public class SupernovaTurret extends SoulLaserTurret {
    /** 充能速率: 原版 0.002f (8 秒充满 phase, 约 8.3 秒) */
    public float chargeWarmup = 0.002f;
    public float chargeCooldown = 0.01f;

    public float chargeSoundVolume = 1f;

    public float attractionStrength = 6f;
    public float attractionDamage = 60f;

    /** 6 部件绘制偏移向量 (与原版一致) */
    private static final Vec2[] phases = {new Vec2(), new Vec2(), new Vec2(), new Vec2(), new Vec2(), new Vec2()};
    public float starRadius = 8f;
    public float starOffset = -2.25f;

    public final int timerChargeStar = timers++;

    // PU_V8 UnityFx.supernovaXxx 完整移植 (位于 zzw.content.exp.UnityFx)
    public Effect chargeStarEffect = UnityFx.supernovaChargeStar;
    public Effect chargeStar2Effect = UnityFx.supernovaChargeStar2;
    public Effect starDecayEffect = UnityFx.supernovaStarDecay;
    public Effect heatWaveEffect = UnityFx.supernovaStarHeatwave;
    public Effect pullEffect = UnityFx.supernovaPullEffect;
    public Effect chargeBeginEffect = UnityFx.supernovaChargeBegin;

    /** v155.4 Turret 无 shootLength 字段, 自定义 */
    public float shootLength = 8f;

    /** PU132 UnityPal.monolith → Color.valueOf("87ceeb") */
    public static final Color monolithColor = Color.valueOf("87ceeb");

    // 6 部件贴图 (PU132 @LoadRegs 注解自动生成, v155.4 手动加载)
    public TextureRegion supernovaHeadRegion, supernovaCoreRegion;
    public TextureRegion supernovaWingLeftRegion, supernovaWingRightRegion;
    public TextureRegion supernovaWingLeftBottomRegion, supernovaWingRightBottomRegion;
    public TextureRegion supernovaBottomRegion;
    public TextureRegion supernovaHeadOutlineRegion, supernovaCoreOutlineRegion;
    public TextureRegion supernovaWingLeftOutlineRegion, supernovaWingRightOutlineRegion;
    public TextureRegion supernovaWingLeftBottomOutlineRegion, supernovaWingRightBottomOutlineRegion;
    public TextureRegion supernovaBottomOutlineRegion;
    public TextureRegion heatRegion;
    public TextureRegion baseRegion;

    public SupernovaTurret(String name) {
        super(name);

        // PU_V8 原版: chargeSound = UnitySounds.supernovaCharge (充能音效)
        // loopSound 保持 supernovaActive (激活循环音效)
        chargeSound = Z_Sounds.supernovaCharge;
        loopSound = Z_Sounds.supernovaActive;
        loopSoundVolume = chargeSoundVolume;

        drawer = new SupernovaDrawer();
    }

    @Override
    public void load() {
        super.load();

        supernovaHeadRegion = Core.atlas.find(name + "-head");
        supernovaCoreRegion = Core.atlas.find(name + "-core");
        supernovaWingLeftRegion = Core.atlas.find(name + "-wing-left");
        supernovaWingRightRegion = Core.atlas.find(name + "-wing-right");
        supernovaWingLeftBottomRegion = Core.atlas.find(name + "-wing-left-bottom");
        supernovaWingRightBottomRegion = Core.atlas.find(name + "-wing-right-bottom");
        supernovaBottomRegion = Core.atlas.find(name + "-bottom");

        supernovaHeadOutlineRegion = Core.atlas.find(name + "-head-outline");
        supernovaCoreOutlineRegion = Core.atlas.find(name + "-core-outline");
        supernovaWingLeftOutlineRegion = Core.atlas.find(name + "-wing-left-outline");
        supernovaWingRightOutlineRegion = Core.atlas.find(name + "-wing-right-outline");
        supernovaWingLeftBottomOutlineRegion = Core.atlas.find(name + "-wing-left-bottom-outline");
        supernovaWingRightBottomOutlineRegion = Core.atlas.find(name + "-wing-right-bottom-outline");
        supernovaBottomOutlineRegion = Core.atlas.find(name + "-bottom-outline");

        heatRegion = Core.atlas.find(name + "-heat");
        baseRegion = Core.atlas.find(name + "-base", Core.atlas.find("unity-block-" + size));
    }

    /**
     * PU132 drawer + heatDrawer lambda 合并实现 (v155.4 适配)
     *
     * PU132 原版:
     *   drawer = b -> { 绘制 6 部件 outline + 主体 + core (z+0.001) };
     *   heatDrawer = tile -> { heat region 加法混合渲染 };
     *
     * v155.4: 合并到单个 DrawBlock.draw() 中
     * ★ 必须手动绘制底座 (v155.4 TurretBuild.draw() 只调用 drawer.draw(this))
     */
    public class SupernovaDrawer extends DrawBlock {
        @Override
        public void draw(Building build) {
            if (!(build instanceof SupernovaTurretBuild)) {
                throw new IllegalStateException("building isn't an instance of SupernovaTurretBuild");
            }
            SupernovaTurretBuild tile = (SupernovaTurretBuild) build;

            // 1. 手动绘制底座
            Draw.rect(baseRegion, tile.x, tile.y);

            // 2. PU132 drawer lambda 完整移植
            float recoil = tile.recoilOffset.len();
            float rot = tile.rotation - 90f;
            float ox = tile.x + tile.recoilOffset.x;
            float oy = tile.y + tile.recoilOffset.y;

            // core
            phases[0].trns(tile.rotation, -recoil + Mathf.curve(tile.phase, 0f, 0.3f) * -2f);
            // left wing
            phases[1].trns(tile.rotation - 90,
                Mathf.curve(tile.phase, 0.2f, 0.5f) * -2f,
                -recoil + Mathf.curve(tile.phase, 0.2f, 0.5f) * 2f +
                    Mathf.curve(tile.phase, 0.5f, 0.8f) * 3f
            );
            // left bottom wing
            phases[2].trns(tile.rotation - 90,
                Mathf.curve(tile.phase, 0f, 0.3f) * -1.5f +
                    Mathf.curve(tile.phase, 0.6f, 1f) * -2f,
                -recoil + Mathf.curve(tile.phase, 0f, 0.3f) * 1.5f +
                    Mathf.curve(tile.phase, 0.6f, 1f) * -1f
            );
            // bottom
            phases[3].trns(tile.rotation, -recoil + Mathf.curve(tile.phase, 0f, 0.6f) * -4f);
            // right wing
            phases[4].trns(tile.rotation - 90,
                Mathf.curve(tile.phase, 0.2f, 0.5f) * 2f,
                -recoil + Mathf.curve(tile.phase, 0.2f, 0.5f) * 2f +
                    Mathf.curve(tile.phase, 0.5f, 0.8f) * 3f
            );
            // right bottom wing
            phases[5].trns(tile.rotation - 90,
                Mathf.curve(tile.phase, 0f, 0.3f) * 1.5f +
                    Mathf.curve(tile.phase, 0.6f, 1f) * 2f,
                -recoil + Mathf.curve(tile.phase, 0f, 0.3f) * 1.5f +
                    Mathf.curve(tile.phase, 0.6f, 1f) * -1f
            );

            // outline 层
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

            // core 层 (z + 0.001f 微小提升)
            float z = Draw.z();
            Draw.z(z + 0.001f);
            Draw.rect(supernovaCoreRegion, tile.x + phases[0].x, tile.y + phases[0].y, rot);
            Draw.z(z);

            // 3. PU132 heatDrawer lambda 完整移植
            if (tile.heat > 0.00001f) {
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
        }

        @Override
        public TextureRegion[] icons(Block block) {
            return new TextureRegion[]{
                Core.atlas.find(block.name + "-base", Core.atlas.find("unity-block-" + block.size)),
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
        public float novaCharge;
        public float phase;
        public float starHeat;

        /**
         * PU132 updateTile 完整移植 - 严格按原版顺序
         *
         * 原版顺序:
         * 1. 不射击/无效目标/无消耗时 charge 衰减
         * 2. attractUnits (射击中且无子弹时)
         * 3. phase 累积 (射击中或有子弹时)
         * 4. super.updateTile()
         * 5. charge 累积 (射击中且无子弹时, 用液体消耗)
         * 6. 音效/特效 (starHeat/chargeStar/chargeStar2/chargeBegin)
         * 7. 持续闪电 (Lightning.create)
         */
        @Override
        public void updateTile() {
            // 1. 不射击/无效目标/无消耗时 charge 衰减 (原版第一行)
            if (!isShooting() || !validateTarget() || !canConsume()) {
                novaCharge = Mathf.lerpDelta(novaCharge, 0f, chargeCooldown);
                novaCharge = novaCharge > 0.001f ? novaCharge : 0f;
            }

            // 2. attractUnits (射击中且无子弹时)
            if (isShooting() && bullets.isEmpty()) attractUnits();

            // 3. phase 累积 (射击中或有子弹时, 用 chargeWarmup * edelta())
            if (isShooting() || !bullets.isEmpty()) {
                phase = Mathf.clamp(phase + chargeWarmup * edelta(), 0f, 1f);
            } else {
                phase = Mathf.lerpDelta(phase, 0f, chargeCooldown);
                phase = phase > 0.001f ? phase : 0f;
            }

            // 4. super.updateTile() (原版在 phase 累积之后调用)
            super.updateTile();

            // 5. charge 累积 (射击中且无子弹时, 用液体消耗)
            // PU_V8 原版公式 (完整恢复):
            //   Liquid liquid = liquids.current();
            //   float maxUsed = consumes.<ConsumeLiquidBase>get(ConsumeType.liquid).amount;
            //   float used = baseReloadSpeed() * ((cheating() ? maxUsed : Math.min(liquids.get(liquid), maxUsed * Time.delta)) * liquid.heatCapacity * coolantMultiplier);
            //   charge = Mathf.clamp(charge + 120f * chargeWarmup * used);
            // v155.4 适配: ConsumeType.liquid 不存在, 改用 BaseTurret.coolant 字段 (ConsumeLiquidBase 类型)
            //   - 由 consumeCoolant(0.01f) 添加, BaseTurret.init() 自动赋值给 coolant 字段
            //   - cheating() 方法可用, liquids.get(liquid) 可用, liquid.heatCapacity 可用
            if (isShooting() && bullets.isEmpty() && coolant != null) {
                Liquid liquid = liquids.current();
                float maxUsed = coolant.amount;
                float used = baseReloadSpeed() * ((cheating() ? maxUsed : Math.min(liquids.get(liquid), maxUsed * Time.delta)) * liquid.heatCapacity * coolantMultiplier);
                novaCharge = Mathf.clamp(novaCharge + 120f * chargeWarmup * used);
            }

            // 6. 音效 + 特效
            float prog = novaCharge * 1.5f + 0.5f;
            boolean notShooting = bullets.isEmpty();
            boolean tick = Mathf.chanceDelta(1f);
            boolean tickCharge = Mathf.chanceDelta(novaCharge);

            starHeat = Mathf.approachDelta(starHeat, notShooting ? novaCharge : 1f, chargeWarmup * 60f);

            float recoil = recoilOffset.len();
            Tmp.v1.trns(rotation, -recoil + starOffset + Mathf.curve(phase, 0f, 0.3f) * -2f);
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
                        x + Angles.trnsx(rotation, -recoil + shootLength),
                        y + Angles.trnsy(rotation, -recoil + shootLength),
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

            // 7. 持续闪电 (Lightning.create)
            if (Mathf.chanceDelta(notShooting ? novaCharge : 1f)) {
                Tmp.v1
                    .trns(rotation, -recoil + starOffset + Mathf.curve(phase, 0f, 0.3f) * -2f)
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
            super.draw();

            boolean notShooting = bullets.isEmpty();
            float recoil = recoilOffset.len();
            Tmp.v1.trns(rotation, -recoil + starOffset + Mathf.curve(phase, 0f, 0.3f) * -2f);

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
                        x + Angles.trnsx(rotation, -recoil + shootLength),
                        y + Angles.trnsy(rotation, -recoil + shootLength),
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
            return super.isShooting() && efficiency > 0f;
        }

        @Override
        protected void updateShooting() {
            if (!bullets.isEmpty()) return;

            if (novaCharge >= 1f && phase >= 1f && (canConsume() || cheating())) {
                BulletType type = peekAmmo();
                shoot(type);
                novaCharge = 0f;
            }
        }

        @Override
        protected float baseReloadSpeed() {
            return Mathf.clamp(efficiency + novaCharge, 0f, 1.2f);
        }

        float trueEfficiency() {
            return Mathf.clamp(efficiency + novaCharge);
        }

        @Override
        public boolean canConsume() {
            return super.canConsume() || novaCharge > 0.001f;
        }

        @Override
        public boolean shouldActiveSound() {
            return novaCharge > 0.01f && loopSound != Sounds.none;
        }

        @Override
        public float activeSoundVolume() {
            return Mathf.curve(novaCharge, 0f, 0.4f) * 1.2f;
        }

        /** PU132 attractUnits 完整移植 */
        protected void attractUnits() {
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
                            .trns(rotation, recoilOffset.len() + starOffset + Mathf.curve(phase, 0f, 0.3f) * -2f)
                            .add(this);

                        pullEffect.at(unit.x, unit.y, novaCharge * (3f + Mathf.range(0.2f)), new Vec2(Tmp.v1.x, Tmp.v1.y));
                    }
                }
            });
        }
    }
}
