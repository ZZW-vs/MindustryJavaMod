package zzw.content.units.entities;

import zzw.content.units.anticheat.AntiCheatBase;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.Groups;
import mindustry.gen.UnitEntity;

/**
 * 隐形单位基类 (移植自 PU132 EndInvisibleUnit, 简化版)
 * - 血量高于 50% 时隐身 (透明)
 * - 受到攻击时短暂现身
 * - 简化: 隐身仅做 alpha 渐变, 不移除 Groups.unit (v158 物理引擎会出问题)
 */
public class EndInvisibleUnit extends UnitEntity {
    private final AntiCheatBase antiCheat = new AntiCheatBase();
    private float disabledTime = 0f;
    private float invFrame = 0f;
    private float alphaLerp = 0f;
    private float scanInterval = 0f;

    public boolean isInvisible = false;

    /** 攻击结束后保持现身的时间 (tick)。计时结束后才开始渐隐 (用户要求, 默认 3 秒) */
    public float fadeDelay = 3f * 60f;
    /** 现身保持计时器 */
    protected float revealTimer = 0f;

    /** 怒气系统 (PU132 EndComp): 死亡拒绝后 4 倍速 + 加速武器装填 */
    protected float aggression = 0f;
    protected float aggressionTime = 0f;

    @Override
    public void setType(mindustry.type.UnitType type) {
        super.setType(type);
        antiCheat.lastHealth = type.health;
    }

    @Override
    public void add() {
        if (added) return;
        super.add();
        antiCheat.lastHealth = health;
    }

    @Override
    public void update() {
        // ★ 血量双轨 (修正): 台账(antiCheat.lastHealth)按防作弊上限独立扣减,
        //   不回充 health — 回充会抵消原始伤害使血量永远到不了 0, 死亡拒绝无法触发

        super.update();

        invFrame += Time.delta;
        disabledTime = Math.max(disabledTime - Time.delta, 0f);
        antiCheat.updateAntiCheat();

        // 扫描附近敌人/建筑, 距离过近则现身
        scanInterval += Time.delta;
        if (scanInterval >= 30f) {
            scanInterval = 0f;
            float size = hitSize() * 3f;
            final boolean[] near = {false};
            Groups.unit.intersect(x - size, y - size, size * 2f, size * 2f, u -> {
                if (u.team() != team() && Mathf.within(x, y, u.x, u.y, hitSize() * 3f)) {
                    near[0] = true;
                }
            });
            if (near[0]) {
                disabledTime = 1.2f * 60f;
            }
        }

        // 隐身: 血量高 + 不在攻击 + 没有 disabled
        // ★ 攻击后延迟变透明 (用户要求): 停火后保持现身 fadeDelay tick 再开始渐隐
        //   (isShooting 由 AI/玩家输入驱动, 停火即 false — mount.bullet 残留不作为现身依据)
        boolean attacking = isShooting();
        if (attacking) {
            revealTimer = fadeDelay;
        } else {
            revealTimer = Math.max(revealTimer - Time.delta, 0f);
        }

        if (!attacking && health > maxHealth / 2f && disabledTime <= 0f && revealTimer <= 0f) {
            alphaLerp = Mathf.lerpDelta(alphaLerp, 1f, 0.1f);
        } else {
            alphaLerp = Mathf.lerpDelta(alphaLerp, 0f, 0.1f);
        }
        isInvisible = alphaLerp > 0.5f;

        // ★ 怒气系统 (PU132 EndComp.update): 死亡拒绝后加速全部武器装填
        if (aggression > 0f) {
            for (mindustry.entities.units.WeaponMount mount : mounts) {
                mount.reload = Math.max(0f, mount.reload - (aggression * Time.delta));
            }
            if (aggressionTime > 0f) {
                aggressionTime -= Time.delta;
            } else {
                aggression = Mathf.lerpDelta(aggression, 0f, 0.1f);
            }
        }
    }

    @Override
    public void damage(float amount) {
        if (invFrame < 15f) return;
        // 台账按防作弊上限扣减 (慢); 显示血量按原始伤害走原版路径 (快, 含护甲/护盾/死亡触发)
        float trueDamage = antiCheat.applyAntiCheatDamage(amount);
        if (trueDamage <= 0f) return;
        disabledTime = Math.max(1.4f * 60f, trueDamage / 25f);
        invFrame = 0f;
        // ★ 传入原始 amount: health 比台账先归零 → kill() → 台账 > 0 → 拒绝死亡+复活
        super.damage(amount);
    }

    /**
     * 死亡拒绝+复活 (PU132 EndComp.destroy/remove 完整移植):
     * 台账 (antiCheat.lastHealth) 未耗尽时, 播放红色蓄力特效并复活。
     */
    private boolean denyDeath() {
        if (antiCheat.lastHealth > 0f) {
            // 狂暴: 4 倍速 + 持续 10 秒 (PU132 aggression=4, aggressionTime=10*60)
            aggression = 4f;
            aggressionTime = 10f * 60f;
            // 复活: 血量回充到台账值
            health = Math.max(health, antiCheat.lastHealth);
            hitTime = 1f;
            // 红色粒子蓄力特效 (PU132 SpecialFx.endDeny)
            zzw.content.units.effects.SpecialFx.endDeny.at(x, y, rotation, this);
            return true;
        }
        return false;
    }

    @Override
    public void heal(float amount) {
        super.heal(amount);
        // 治疗同步回台账
        antiCheat.lastHealth = Math.max(antiCheat.lastHealth, health);
    }

    @Override
    public void destroy() {
        if (denyDeath()) return;
        super.destroy();
    }

    @Override
    public void kill() {
        if (denyDeath()) return;
        super.kill();
    }

    @Override
    public void remove() {
        if (antiCheat.lastHealth > 0f && health > 0f) return;
        super.remove();
    }

    public float getAlphaLerp() {
        return alphaLerp;
    }

    // ===== 供子类 (ApocalypseUnit) 使用的受保护访问器 =====

    /** @return 无敌帧计时 (PU132 InvisibleComp.invFrame) */
    protected float getInvFrame() {
        return invFrame;
    }

    /** 重置无敌帧 (PU132: invFrame = 0f) */
    protected void resetInvFrame() {
        invFrame = 0f;
    }

    /**
     * 台账+显示血量同时扣减 (PU132 AntiCheatBase.overrideAntiCheatDamage):
     * lastHealth -= v; if(health > lastHealth) health = lastHealth。
     * 用于优先级无敌帧扣血 (绕过常规伤害路径)。
     *
     * @param v 要扣除的血量
     */
    protected void subtractHealthRaw(float v) {
        antiCheat.lastHealth -= v;
        if (health > antiCheat.lastHealth) health = antiCheat.lastHealth;
    }

    /**
     * 仅扣减台账 (PU132 ApocalypseUnit.damage: lastHealth -= trueAmount),
     * 显示血量由 {@link #damageMindustry(float)} 走 Mindustry 原版路径扣除。
     *
     * @param v 要从台账中扣除的值
     */
    protected void subtractLastHealth(float v) {
        antiCheat.lastHealth -= v;
    }

    /**
     * 绕过子类防作弊覆写, 直接调用 Mindustry 原版扣血
     * (原版路径: 护甲/护盾修正 + health 扣减 + 血量≤0 触发 kill)。
     *
     * @param amount 原始伤害
     */
    protected void damageMindustry(float amount) {
        super.damage(amount);
    }
}
