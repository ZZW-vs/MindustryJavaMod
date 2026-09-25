package zzw.content.blocks.soul;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Strings;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Sounds;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.tilesize;

/**
 * 灵魂爆发电力炮台 (v158 移植版, 完整复制 PU_V8 BurstPowerTurret.shoot 逻辑)
 *
 * = SoulTurretPowerTurret (灵魂+电力炮台) + 充能+双弹幕爆发机制
 *
 * 机制 (与 PU_V8 BurstPowerTurret.shoot 一致):
 * - shoot(BulletType type) 重写:
 *   1. 触发 chargeBeginEffect + chargeSound
 *   2. 循环 chargeEffects 次随机延迟触发 chargeEffect (充能视觉)
 *   3. 延迟 chargeTime 后:
 *      - 主弹幕: shoot.shots 发, 间隔 burstSpacing*2*i (主弹 BulletType)
 *      - 副弹幕: subShots 发, 间隔 subBurstSpacing*i (副弹 subShootType)
 *      - 触发 shootSound + shootEffect + 充能完成
 *
 * ★ v158 适配 (PU_V8 字段在 v158 中不存在的, 全部在本类自定义):
 *   - chargeTime / chargeEffects / chargeMaxDelay / chargeBeginEffect / chargeEffect / chargeSound:
 *     PU_V8 (v7) PowerTurret 自带字段, v158 不存在, 在本类自定义
 *   - burstSpacing: PU_V8 (v7) PowerTurret 自带字段, v158 改为 ShootPattern, 在本类自定义
 *   - shots: 直接用 shoot.shots (v158 ShootPattern 字段)
 *   - charging: PU_V8 (v7) Build 字段, v158 改为方法 charging(), 在本类自定义字段
 *   - tr: PU_V8 (v7) Build 字段 (Vec2), v158 不存在, 在本类自定义
 *   - recoil: PU_V8 (v7) Build 字段 (0~1 进度), v158 改为 curRecoil (Block 级 recoil 是距离)
 *   - bullet(BulletType, float angle): PU_V8 (v7) 2 参数方法, v158 只有 5 参数版本, 在本类自定义
 *   - effects(): PU_V8 (v7) 方法 (播放 shootSound/shootEffect), v158 已合并到 bullet(), 在 shoot() 中直接调用
 *
 * 参考: PU_V8 unity/world/blocks/defense/turrets/BurstPowerTurret.java
 */
public class SoulBurstPowerTurret extends SoulTurretPowerTurret {
    /** 副弹幕子弹类型 */
    public BulletType subShootType = null;
    /** 副弹幕发射次数 */
    public int subShots = 1;
    /** 副弹幕发射间隔 (tick) */
    public float subBurstSpacing = 0f;
    /** 副弹幕发射特效 */
    public Effect subShootEffect = Fx.none;
    /** 副弹幕发射音效 */
    public Sound subShootSound = Sounds.none;
    /** 副弹幕发射音量 */
    public float subShootSoundVolume = 1f;

    /** 液体冷却强化表 */
    public arc.struct.ObjectFloatMap<mindustry.type.Liquid> coolantBoost = new arc.struct.ObjectFloatMap<>();
    
    // ★ v158 兼容字段 (PU_V8 v7 PowerTurret 自带, v158 不存在, 在本类自定义)
    /** 主弹幕连发间隔 (tick) - PU_V8 v7 PowerTurret.burstSpacing */
    public float burstSpacing = 0f;
    /** 充能时间 (tick), >0 时启用充能阶段 */
    public float chargeTime = 0f;
    /** 充能视觉特效触发次数 */
    public int chargeEffects = 0;
    /** 充能视觉特效随机延迟上限 */
    public float chargeMaxDelay = 0f;
    /** 充能开始时触发的特效 */
    public Effect chargeBeginEffect = Fx.none;
    /** 充能过程中触发的特效 (循环 chargeEffects 次) */
    public Effect chargeEffect = Fx.none;
    /** 充能音效 */
    public Sound chargeSound = Sounds.none;

    public SoulBurstPowerTurret(String name) {
        super(name);
    }

    @Override
    public void setStats() {
        super.setStats();

        // ★ 强化配方: 直接展示固定百分比 (水 130%, 冷冻液 200%), 覆盖原版按热容计算的奇怪数值
        if (coolant != null && !coolantBoost.isEmpty()) {
            stats.replace(Stat.booster, table -> {
                table.row();
                table.table(c -> {
                    for (Liquid liquid : mindustry.Vars.content.liquids()) {
                        float boost = coolantBoost.get(liquid, -1f);
                        if (boost < 0f) continue;

                        c.table(Styles.grayPanel, b -> {
                            b.image(liquid.uiIcon).size(40).pad(10f).left();
                            b.table(info -> {
                                info.add(liquid.localizedName).left().row();
                                info.add(Strings.autoFixed(coolant.amount * 60f, 2) + StatUnit.perSecond.localized())
                                        .left().color(Color.lightGray);
                            });
                            b.add(Core.bundle.format("bullet.reload", Strings.autoFixed((1f + boost) * 100f, 2)))
                                    .pad(10f).right().grow().padRight(15f);
                        }).growX().pad(5).row();
                    }
                }).growX().colspan(table.getColumns());
                table.row();
            });
        }
    }

    public class SoulBurstPowerTurretBuild extends SoulTurretPowerTurretBuild {
        /** 临时向量 (PU_V8 v7 TurretBuild.tr 字段, v158 不存在) */
        public Vec2 tr = new Vec2();
        /** 是否正在充能 (PU_V8 v7 Build.charging 字段, v158 改为方法) */
        public boolean charging = false;

        @Override
        protected void shoot(BulletType type) {
            if (chargeTime > 0f) {
                // ★ PU_V8 BurstPowerTurret.shoot 充能阶段
                useAmmo();
                tr.trns(rotation, size * tilesize / 2f);

                chargeBeginEffect.at(x + tr.x, y + tr.y, rotation);
                chargeSound.at(x + tr.x, y + tr.y, 1f);

                for (int i = 0; i < chargeEffects; i++) {
                    Time.run(Mathf.random(chargeMaxDelay), () -> {
                        if (!isValid()) return;
                        tr.trns(rotation, size * tilesize / 2f);
                        chargeEffect.at(x + tr.x, y + tr.y, rotation);
                    });
                }
                charging = true;

                Time.run(chargeTime, () -> {
                    if (!isValid()) return;
                    tr.trns(rotation, size * tilesize / 2f);
                    // v158 适配: PU_V8 是 recoil = recoilAmount (Build 级 0~1 进度)
                    // v158 中 recoil 是 Block 级距离字段, Build 级进度字段是 curRecoil
                    curRecoil = 1f;
                    heat = 1f;
                    // ★ 修正装填累积: 蓄力结束时装填值刚好充满, 此时才重置 (开火)
                    reloadCounter %= reload;

                    // ★ 主弹幕: shoot.shots 发, 间隔 burstSpacing * 2f * i
                    // (PU_V8 原版是 burstSpacing * 2f 没有乘 i, v158 改为 *i 实现连发递增延迟)
                    for (int i = 0; i < shoot.shots; i++) {
                        final int idx = i;
                        Time.run(burstSpacing * 2f * idx, () -> {
                            if (!isValid()) return;
                            bullet(type, rotation + Mathf.range(inaccuracy));
                        });
                    }
                    // ★ 副弹幕: subShots 发, 间隔 subBurstSpacing * i
                    for (int i = 0; i < subShots; i++) {
                        final int idx = i;
                        Time.run(subBurstSpacing * idx, () -> {
                            if (!isValid()) return;
                            bullet(subShootType, rotation + Mathf.range(subShootType.inaccuracy));
                            subEffects();
                        });
                    }
                    // shootSound/shootEffect 在充能完成时触发 (v158 无 effects() 方法, 直接调用)
                    shootSound.at(x + tr.x, y + tr.y, 1f);
                    (shootEffect == null ? type.shootEffect : shootEffect).at(x + tr.x, y + tr.y, rotation);
                    charging = false;
                });
            } else {
                super.shoot(type);
            }
        }

        /**
         * ★ 强化配方: 用固定百分比表推进装填 (水 130%, 冷冻液 200%), 与面板显示一致。
         */
        @Override
        protected void updateCooling() {
            if (coolantBoost.isEmpty()) {
                super.updateCooling();
                return;
            }

            if (coolant == null || coolant.efficiency(this) <= 0f || efficiency <= 0f) return;

            float boost = coolantBoost.get(liquids.current(), 0f);
            float amount = coolant.amount * coolant.efficiency(this);
            coolant.update(this);
            reloadCounter += edelta() * boost;

            if (Mathf.chance(0.06 * amount)) {
                coolEffect.at(x + Mathf.range(size * tilesize / 2f), y + Mathf.range(size * tilesize / 2f));
            }
        }

        /**
         * ★ 修正装填/蓄力时序 (修复"蓄力时装填值不会增加、未满就开火")。
         *
         * <p>原实现只在装填满后由父类 {@code updateShooting()} 调用 {@link #shoot} 开始蓄力,
         * 于是蓄力期间 {@code reloadCounter} 从 0 重新累积, 开火时装填值远未满。
         * 现改为: 当装填剩余时间 ≤ {@link #chargeTime} 时提前进入蓄力阶段,
         * 蓄力期间 {@code reloadCounter} 继续由父类 {@code handleReload()} 累加,
         * 蓄力结束时装填值刚好充满 → 开火, 做到"蓄力时累加装填值、装填值满了才开火"。</p>
         */
        @Override
        protected void updateShooting() {
            if (chargeTime <= 0f) {
                super.updateShooting();
                return;
            }
            if (!charging && shootWarmup >= minWarmup
                && reloadCounter >= Mathf.clamp(reload - chargeTime, 0f, reload)) {
                shoot(peekAmmo());
            }
        }

        /** 副弹幕特效和音效 (PU_V8 BurstPowerTurret.subEffects) */
        protected void subEffects() {
            subShootEffect.at(x + tr.x, y + tr.y, rotation);
            subShootSound.at(x + tr.x, y + tr.y, 1f);
        }

        /**
         * PU_V8 兼容: 以指定绝对角度发射子弹
         * v158 没有 2 参数 bullet 方法, 自定义实现 (基于 v158 Turret bullet 方法, 但):
         * - 使用传入的绝对角度 (不再叠加 rotation + inaccuracy)
         * - 不触发 shootSound (由 shoot() 统一处理, 避免连发时多次播放)
         */
        protected void bullet(BulletType type, float angle) {
            if (dead || (!consumeAmmoOnce && !hasAmmo())) return;

            float bulletX = x + Angles.trnsx(rotation - 90, shootX, shootY);
            float bulletY = y + Angles.trnsy(rotation - 90, shootX, shootY);

            float lifeScl = type.scaleLife ? Mathf.clamp(
                (1 + scaleLifetimeOffset) * Mathf.dst(bulletX, bulletY, targetPos.x, targetPos.y) / type.range,
                minRange() / type.range, range() / type.range
            ) : 1f;

            Bullet b = type.create(this, team, bulletX, bulletY, angle, -1f,
                (1f - velocityRnd) + Mathf.random(velocityRnd), lifeScl, null, null, targetPos.x, targetPos.y);
            handleBullet(b, 0, 0, angle - rotation);

            (shootEffect == null ? type.shootEffect : shootEffect).at(bulletX, bulletY, angle, type.hitColor);
            (smokeEffect == null ? type.smokeEffect : smokeEffect).at(bulletX, bulletY, angle, type.hitColor);
            // 不触发 shootSound - 由 shoot() 统一处理 (避免连发时多次播放)

            ammoUseEffect.at(
                x - Angles.trnsx(rotation, ammoEjectBack),
                y - Angles.trnsy(rotation, ammoEjectBack),
                rotation
            );

            if (shake > 0) {
                Effect.shake(shake, shake, this);
            }

            curRecoil = 1f;
            heat = 1f;
            totalShots++;

            if (!consumeAmmoOnce) {
                useAmmo();
            }
        }
    }
}
