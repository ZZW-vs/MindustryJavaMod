package zzw.content.blocks.soul;

import arc.Core;
import arc.audio.Sound;
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
import mindustry.entities.bullet.BulletType;
import mindustry.entities.Units;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.world.draw.DrawBlock;
import zzw.content.Z_Sounds;
import zzw.content.units.effects.UnityDrawf;

/**
 * Supernova 灵魂激光炮台 (v158 移植版, 完整移植 PU_V8 SupernovaTurret 逻辑)
 *
 * 机制 (与 PU_V8 一致):
 * - 继承 SoulLaserTurret (= LaserTurret + ISoulTurret)
 * - 充能阶段: 吸引范围内单位 (attractUnits), 累积 charge/phase
 * - 充能满后 (charge >= 1 && phase >= 1): 发射持续激光 (shoot)
 * - 激光激活期间: 持续闪电 + 星辰衰减特效 + 热浪特效
 * - 自定义 6 部件绘制 (core/wings/bottom/head) 含 outline 两层
 * - heat region 加法混合渲染
 * - UnityDrawf.shiningCircle 绘制星辰闪光环
 *
 * ★ v158 适配 (与 PU_V8 差异):
 * 1. charge 字段重命名为 novaCharge (避免 shadow v158 TurretBuild.charge 字段)
 * 2. bulletLife <= 0 && bullet == null → bullets.isEmpty() (v158 LaserTurretBuild 用 Seq<BulletEntry>)
 * 3. consValid() → canConsume() (v158 方法名)
 * 4. efficiency() → efficiency (v158 字段访问)
 * 5. PitchedSoundLoop 不存在: 改用 v158 内置 soundLoop 系统 (设置 loopSound, 重写 shouldActiveSound/activeSoundVolume)
 * 6. SVec2.construct 不存在: 改用 new Vec2(x, y)
 * 7. UnityPal.monolith 不存在: 用 Color.valueOf("87ceeb") 替代
 * 8. UnityFx.supernovaXxx 不存在: 用 Fx.* 等效替代 (见字段注释)
 * 9. Regions.supernovaXxxRegion 不存在: 用 TextureRegion 字段 + load() 手动加载
 * 10. drawer/heatDrawer lambda: 改为重写 draw() 方法直接绘制 (v158 DrawBlock 是抽象类)
 * 11. tr2 (PU_V8 TurretBuild 字段): 用 v158 TurretBuild.recoilOffset 替代
 * 12. shootLength (PU_V8 Turret 字段): v158 无此字段, 自定义 public float shootLength
 * 13. timers++: v158 仍支持 (Block.timers 字段 + TimerComp.timer 方法)
 * 14. range(): v158 BaseTurretBuild.range() 方法存在
 * 15. Z_Sounds.supernovaCharge 不存在: 用 Z_Sounds.supernovaActive 替代
 *
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/SupernovaTurret.java
 */
public class SupernovaTurret extends SoulLaserTurret {
    public float chargeWarmup = 0.002f;
    public float chargeCooldown = 0.01f;

    /** 充能循环音效音量 (PU_V8 chargeSoundVolume; v158 用 loopSound/loopSoundVolume 实现) */
    public float chargeSoundVolume = 1f;

    public float attractionStrength = 6f;
    public float attractionDamage = 60f;

    /** Temporary vector array to be used in the drawing method */
    private static final Vec2[] phases = {new Vec2(), new Vec2(), new Vec2(), new Vec2(), new Vec2(), new Vec2()};
    public float starRadius = 8f;
    public float starOffset = -2.25f;

    /** v158 支持 timers++ 模式分配 timer 索引 (与 PU_V8 一致) */
    public final int timerChargeStar = timers++;

    // PU_V8 UnityFx.supernovaXxx → v158 Fx.* 等效替代
    /** PU_V8: UnityFx.supernovaChargeStar → v158: Fx.lightningShoot */
    public Effect chargeStarEffect = Fx.lightningShoot;
    /** PU_V8: UnityFx.supernovaChargeStar2 → v158: Fx.sparkShoot */
    public Effect chargeStar2Effect = Fx.sparkShoot;
    /** PU_V8: UnityFx.supernovaStarDecay → v158: Fx.smoke */
    public Effect starDecayEffect = Fx.smoke;
    /** PU_V8: UnityFx.supernovaStarHeatwave → v158: Fx.ripple (Fx.pulseSmoke 不存在) */
    public Effect heatWaveEffect = Fx.ripple;
    /** PU_V8: UnityFx.supernovaPullEffect → v158: Fx.healBlockFull */
    public Effect pullEffect = Fx.healBlockFull;
    /** 充能起始特效 (PU_V8: 继承自 PowerTurret.chargeBeginEffect; v158: 自定义字段) */
    public Effect chargeBeginEffect = Fx.lightningShoot;

    /** v158 Turret 无 shootLength 字段, 自定义 (PU_V8 Turret.shootLength 用于炮口位置计算) */
    public float shootLength = 8f;

    /** PU_V8: UnityPal.monolith → v158: Color.valueOf("87ceeb") */
    public static final Color monolithColor = Color.valueOf("87ceeb");

    // PU_V8 Regions.supernovaXxxRegion (注解生成) → v158: TextureRegion 字段 + load() 加载
    public TextureRegion supernovaHeadRegion, supernovaCoreRegion;
    public TextureRegion supernovaWingLeftRegion, supernovaWingRightRegion;
    public TextureRegion supernovaWingLeftBottomRegion, supernovaWingRightBottomRegion;
    public TextureRegion supernovaBottomRegion;
    public TextureRegion supernovaHeadOutlineRegion, supernovaCoreOutlineRegion;
    public TextureRegion supernovaWingLeftOutlineRegion, supernovaWingRightOutlineRegion;
    public TextureRegion supernovaWingLeftBottomOutlineRegion, supernovaWingRightBottomOutlineRegion;
    public TextureRegion supernovaBottomOutlineRegion;
    /** heat 贴图 (PU_V8 heatRegion; v158 DrawTurret.heat, 但我们用空 DrawBlock 所以自定义) */
    public TextureRegion heatRegion;
    /** 底座贴图 (v158 DrawTurret.base, 自定义) */
    public TextureRegion baseRegion;

    public SupernovaTurret(String name) {
        super(name);

        // PU_V8 chargeSound = UnitySounds.supernovaCharge; v158 无 supernovaCharge, 用 supernovaActive 替代
        // v158 Turret 已有 chargeSound 字段 (用于充能射击), 这里设置为 supernovaActive
        chargeSound = Z_Sounds.supernovaActive;
        // v158 用 loopSound + 内置 soundLoop 系统实现循环音效 (替代 PU_V8 PitchedSoundLoop)
        loopSound = Z_Sounds.supernovaActive;
        loopSoundVolume = chargeSoundVolume;

        // v158: 用空 DrawBlock 替代默认 DrawTurret, 避免绘制不存在的默认炮塔贴图
        // 所有绘制逻辑在 SupernovaTurretBuild.draw() 中实现
        drawer = new DrawBlock() {
            @Override
            public TextureRegion[] icons(mindustry.world.Block block) {
                return new TextureRegion[]{Core.atlas.find(block.name + "-head", block.region)};
            }
        };
    }

    @Override
    public void load() {
        super.load();

        // 加载 6 部件贴图 + outline (PU_V8 @LoadRegs 注解自动加载, v158 手动加载)
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

    public class SupernovaTurretBuild extends SoulLaserTurretBuild {
        /** PU_V8 charge; v158 重命名为 novaCharge 避免与 TurretBuild.charge 字段冲突 */
        public float novaCharge;
        public float phase;
        public float starHeat;

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
                Liquid liquid = liquids.current();
                float maxUsed = coolant != null ? coolant.amount : 0f;

                // v158: cheating() 方法存在, efficiency 字段 (非方法)
                float used = baseReloadSpeed() * ((cheating() ? maxUsed : Math.min(liquids.get(liquid), maxUsed * Time.delta)) * liquid.heatCapacity * coolantMultiplier);
                novaCharge = Mathf.clamp(novaCharge + 120f * chargeWarmup * used);
            }

            // v158: 音效由父类 soundLoop 系统 (shouldActiveSound + activeSoundVolume) 处理, 无需手动 update

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
            // ★ v158 适配: 父类 draw() 调用空 DrawBlock (无操作), 所有绘制在此实现
            // PU_V8: super.draw() 调用 drawer lambda 绘制 6 部件 + heatDrawer 绘制 heat
            // v158: 合并到 draw() 方法中
            super.draw();

            float rot = drawrot(); // v158: rotation - 90
            float ox = x + recoilOffset.x;
            float oy = y + recoilOffset.y;

            // 1. 绘制底座 (v158 DrawTurret 默认会画, 但我们用空 DrawBlock 所以手动画)
            if (baseRegion != null && baseRegion.found()) {
                Draw.rect(baseRegion, x, y);
            }

            // 2. PU_V8 drawer lambda: 计算 6 部件相位偏移 + 绘制 outline + 主体
            // core
            phases[0].trns(rotation, -recoilOffset.len() + Mathf.curve(phase, 0f, 0.3f) * -2f);
            // left wing
            phases[1].trns(rotation - 90,
                Mathf.curve(phase, 0.2f, 0.5f) * -2f,
                -recoilOffset.len() + Mathf.curve(phase, 0.2f, 0.5f) * 2f +
                    Mathf.curve(phase, 0.5f, 0.8f) * 3f
            );
            // left bottom wing
            phases[2].trns(rotation - 90,
                Mathf.curve(phase, 0f, 0.3f) * -1.5f +
                    Mathf.curve(phase, 0.6f, 1f) * -2f,
                -recoilOffset.len() + Mathf.curve(phase, 0f, 0.3f) * 1.5f +
                    Mathf.curve(phase, 0.6f, 1f) * -1f
            );
            // bottom
            phases[3].trns(rotation, -recoilOffset.len() + Mathf.curve(phase, 0f, 0.6f) * -4f);
            // right wing
            phases[4].trns(rotation - 90,
                Mathf.curve(phase, 0.2f, 0.5f) * 2f,
                -recoilOffset.len() + Mathf.curve(phase, 0.2f, 0.5f) * 2f +
                    Mathf.curve(phase, 0.5f, 0.8f) * 3f
            );
            // right bottom wing
            phases[5].trns(rotation - 90,
                Mathf.curve(phase, 0f, 0.3f) * 1.5f +
                    Mathf.curve(phase, 0.6f, 1f) * 2f,
                -recoilOffset.len() + Mathf.curve(phase, 0f, 0.3f) * 1.5f +
                    Mathf.curve(phase, 0.6f, 1f) * -1f
            );

            // outline 层 (PU_V8: 先画 outline 再画主体)
            Draw.rect(supernovaWingLeftBottomOutlineRegion, x + phases[2].x, y + phases[2].y, rot);
            Draw.rect(supernovaWingRightBottomOutlineRegion, x + phases[5].x, y + phases[5].y, rot);
            Draw.rect(supernovaWingLeftOutlineRegion, x + phases[1].x, y + phases[1].y, rot);
            Draw.rect(supernovaWingRightOutlineRegion, x + phases[4].x, y + phases[4].y, rot);
            Draw.rect(supernovaBottomOutlineRegion, x + phases[3].x, y + phases[3].y, rot);
            Draw.rect(supernovaHeadOutlineRegion, ox, oy, rot);
            Draw.rect(supernovaCoreOutlineRegion, x + phases[0].x, y + phases[0].y, rot);

            // 主体层
            Draw.rect(supernovaWingLeftBottomRegion, x + phases[2].x, y + phases[2].y, rot);
            Draw.rect(supernovaWingRightBottomRegion, x + phases[5].x, y + phases[5].y, rot);
            Draw.rect(supernovaWingLeftRegion, x + phases[1].x, y + phases[1].y, rot);
            Draw.rect(supernovaWingRightRegion, x + phases[4].x, y + phases[4].y, rot);
            Draw.rect(supernovaBottomRegion, x + phases[3].x, y + phases[3].y, rot);
            Draw.rect(supernovaHeadRegion, ox, oy, rot);

            // core 层 (z + 0.001f 微小提升, 与 PU_V8 一致)
            float z = Draw.z();
            Draw.z(z + 0.001f);
            Draw.rect(supernovaCoreRegion, x + phases[0].x, y + phases[0].y, rot);
            Draw.z(z);

            // 3. PU_V8 heatDrawer lambda: heat region 加法混合渲染
            if (heat > 0.00001f && heatRegion != null && heatRegion.found()) {
                // PU_V8: Utils.pow6In.apply(tile.heat) → v158: Interp.pow5In.apply(heat) (pow6In 不存在)
                float r = Interp.pow5In.apply(heat);
                float g = Interp.pow3In.apply(heat);
                float b = Interp.pow2Out.apply(heat);
                float a = Interp.pow2In.apply(heat);

                Draw.color(Tmp.c1.set(r, g, b, a));
                Draw.blend(Blending.additive);

                Draw.rect(heatRegion, ox, oy, rot);

                Draw.color();
                Draw.blend();
            }

            // 4. PU_V8 draw() override: shiningCircle 闪光环
            boolean notShooting = bullets.isEmpty();
            Tmp.v1.trns(rotation, -recoilOffset.len() + starOffset + Mathf.curve(phase, 0f, 0.3f) * -2f);

            float z2 = Draw.z();
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
            Draw.z(z2);
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
