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
    private float lastHealth = 0f;

    public boolean isInvisible = false;

    /** 攻击结束后保持现身的时间 (tick)。计时结束后才开始渐隐 (用户要求, 默认 3 秒) */
    public float fadeDelay = 3f * 60f;
    /** 现身保持计时器 */
    protected float revealTimer = 0f;

    @Override
    public void setType(mindustry.type.UnitType type) {
        super.setType(type);
        antiCheat.lastHealth = type.health;
    }

    @Override
    public void add() {
        if (added) return;
        super.add();
        lastHealth = health;
    }

    @Override
    public void update() {
        if (health < lastHealth) health = lastHealth;
        lastHealth = health;

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
        // ★ 攻击后延迟变透明 (用户要求): 开火或持续弹存在时刷新现身保持计时,
        //   计时结束后才开始渐隐 (而不是停火下一帧就变透明)
        boolean attacking = isShooting();
        for (mindustry.entities.units.WeaponMount mount : mounts) {
            if (mount.bullet != null) {
                attacking = true;
                break;
            }
        }
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
    }

    @Override
    public void damage(float amount) {
        if (invFrame < 15f) return;
        float trueDamage = antiCheat.applyAntiCheatDamage(amount);
        if (trueDamage <= 0f) return;
        disabledTime = Math.max(1.4f * 60f, trueDamage / 25f);
        invFrame = 0f;
        super.damage(trueDamage);
    }

    @Override
    public void destroy() {
        // ★ 死亡拒绝: 原始血量(lastHealth) > 0 时不允许销毁 (PU132 EndComp 机制)
        if (lastHealth > 0f) {
            antiCheat.immunity += 3500f;
            return;
        }
        super.destroy();
    }

    @Override
    public void kill() {
        if (lastHealth > 0f) {
            antiCheat.immunity += 3500f;
            return;
        }
        super.kill();
    }

    @Override
    public void remove() {
        if (lastHealth > 0f) {
            antiCheat.immunity += 3500f;
            return;
        }
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
     * 原始扣血: 同时维护 lastHealth 原始血量追踪 (PU132: lastHealth -= v; health -= v)。
     *
     * @param v 要扣除的血量
     */
    protected void subtractHealthRaw(float v) {
        health -= v;
        lastHealth = health;
    }

    /**
     * 仅扣减原始血量追踪值 (PU132 ApocalypseUnit.damage: lastHealth -= trueAmount),
     * 血量本身由 {@link #damageMindustry(float)} 走 Mindustry 原版路径扣除。
     *
     * @param v 要从原始血量追踪中扣除的值
     */
    protected void subtractLastHealth(float v) {
        lastHealth -= v;
    }

    /**
     * 绕过子类防作弊覆写, 直接调用 Mindustry 原版扣血
     * (PU132 ApocalypseUnit.damage 末尾的 superDamage(trueAmount))。
     *
     * @param amount 实际造成的伤害
     */
    protected void damageMindustry(float amount) {
        super.damage(amount);
    }
}
