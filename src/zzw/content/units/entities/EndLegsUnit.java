package zzw.content.units.entities;

import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.UnitEntity;

/**
 * End 阵营飞行单位 (简化版防作弊, 完全用原版方法实现)
 *
 * ★ 仅用于 End 阵营飞行单位 (flying=true, 无腿):
 *   - enigma, voidVessel, chronos, opticaecus
 *   - 对于 End 阵营腿单位 (ravager/desolation), 请用 EndGroundUnit (extends LegsUnit)
 *
 * 替代 PU132 的 AntiCheatVariables 9参数系统 + EndComp @EntityComponent mixin
 * 直接在 EndLegsUnit 中内嵌简化版防作弊逻辑, 参数按 PU132 voidVessel/chronos 配置硬编码
 *
 * 防作弊机制 (完全照搬 PU132 EndComp 核心, 简化参数配置):
 * 1. 多槽位无敌帧 (invFrames[]): 每次受伤占用一个槽位, 轮询使用
 * 2. 抗性累积 (resist): 高伤害累积抗性, 减少后续伤害
 * 3. 伤害曲线衰减 (Pow(2)): 超过 damageThreshold 的伤害按曲线衰减
 * 4. 单次伤害硬上限 (maxDamageTaken)
 * 5. 怒气系统 (aggression): 受伤后加速武器 reload
 * 6. 死亡拒绝: trueHealth > 0 时拒绝 destroy/kill/remove
 *
 * ★ 参数按单位 health 比例计算 (PU132 voidVessel/chronos 配置 L3804/L3839):
 *   damageThreshold       = health / 20f
 *   maxDamageThreshold    = health / 1.25f
 *   maxDamageTaken        = health / 15f
 *   resistStart           = health / 25f
 *   resistScl             = 0.2f
 *   resistDuration        = 6f * 60f
 *   resistTime            = 3f * 60f
 *   invincibilityDuration = 15f
 *   invincibilityArray    = 4
 */
public class EndLegsUnit extends UnitEntity {
    private static final Interp curveType = new Interp.Pow(2);

    // 防作弊运行时数据
    private float trueHealth, trueMaxHealth;
    private float aggression = 0f;
    private float aggressionTime = 0f;
    private float[] invFrames;
    private int invIndex = 0;
    private float invTimer = 0f;
    private float resist, resistMax, resistTime;

    public static EndLegsUnit create() {
        return new EndLegsUnit();
    }

    @Override
    public void add() {
        if (added) return;
        super.add();
        // 初始化防作弊数据 (按 PU132 voidVessel/chronos 配置: invincibilityArray=4)
        trueHealth = type.health;
        trueMaxHealth = type.health;
        invFrames = new float[4];
    }

    @Override
    public void update() {
        // ★ 防作弊更新 (PU132 EndComp.update L141-178, 在 super.update() 之前)
        // 血量防回退 (防作弊)
        if (health < trueHealth || Float.isNaN(health)) health = trueHealth;
        trueHealth = health;
        if (maxHealth < trueMaxHealth || Float.isNaN(maxHealth)) maxHealth = trueMaxHealth;
        trueMaxHealth = maxHealth;
        if (trueHealth > 0f) dead = false;

        // 抗性衰减 (按 PU132 配置: resistDuration=6*60, resistTime=3*60)
        if (resistTime <= 0f) {
            resist -= resistMax / (6f * 60f);
            resist = Math.max(resist, 0f);
        } else {
            resistTime -= Time.delta;
        }
        if (resist <= 0f) {
            resistMax = 0f;
        }

        // 无敌帧倒计时
        for (int i = 0; i < invFrames.length; i++) {
            invFrames[i] = Math.max(invFrames[i] - Time.delta, 0f);
        }
        if (invTimer > 0f) invTimer -= Time.delta;

        // 怒气系统: 加速武器 reload
        if (aggression > 0f) {
            for (WeaponMount mount : mounts) {
                mount.reload = Math.max(0f, mount.reload - (aggression * Time.delta));
            }
            if (aggressionTime > 0f) {
                aggressionTime -= Time.delta;
            } else {
                aggression = Mathf.lerpDelta(aggression, 0f, 0.1f);
            }
        }

        super.update();

        // super.update() 后再次同步 (super 可能改了 health)
        if (trueHealth > 0f) {
            if (this.health > trueHealth) {
                this.health = trueHealth;
            } else {
                trueHealth = this.health;
            }
        }
    }

    @Override
    public void damage(float amount) {
        // ★ 防作弊伤害处理 (完全复刻 PU132 EndComp.damage L210-257)
        if (invFrames[invIndex] <= 0f) {
            // 按单位 health 比例计算参数 (PU132 voidVessel/chronos 配置)
            float damageThreshold = trueMaxHealth / 20f;
            float maxDamageThreshold = trueMaxHealth / 1.25f;
            float maxDamageTaken = trueMaxHealth / 15f;
            float resistStart = trueMaxHealth / 25f;
            float resistScl = 0.2f;
            float invincibilityDuration = 15f;

            float nextAmount = Math.min(amount, maxDamageTaken);

            // 抗性累积
            if (amount > resistStart) {
                float a = amount - resistStart;
                resist += a;
                if (Float.isInfinite(resist)) resist = Float.MAX_VALUE;
                resistMax = Math.max(resistMax, resist);
                resistTime = 3f * 60f;  // PU132: resistTime=3*60
                aggression += Math.min(a / (trueMaxHealth / 5f), 1.5f);
                aggression = Math.min(aggression, 4f);
                aggressionTime = 5f * 60f;
            }

            // 伤害曲线衰减 (Pow(2))
            if (amount > damageThreshold) {
                float in = 1f - curveType.apply(Mathf.clamp((amount - damageThreshold) / (maxDamageThreshold - damageThreshold)));
                nextAmount *= in;
            }

            // 抗性缩放
            amount = nextAmount / ((resist * resistScl) + 1f);

            // 占用无敌帧槽位
            invFrames[invIndex] = invincibilityDuration;
            if (invTimer <= 0f) {
                invIndex++;
                invIndex %= invFrames.length;
                invTimer = 3f;
            }
        } else {
            // 无敌帧中, 拒绝伤害
            return;
        }

        // 台账扣减 (按防作弊上限/曲线/抗性后的金额)
        float tmpAmount = Math.max(amount - armor, Vars.minArmorDamage * amount) / healthMultiplier;

        if (tmpAmount > 0) {
            float shieldDamage = Math.min(Math.max(shield, 0), tmpAmount);
            tmpAmount -= shieldDamage;

            if (tmpAmount > 0) {
                trueHealth -= tmpAmount;
            }
        }

        // ★ 原版显示血量并行扣减 (PU132 关键机制): 显示血量比台账先归零
        float rawAmount = Math.max(amount - armor, Vars.minArmorDamage * amount) / healthMultiplier;
        if (rawAmount > 0) {
            float shieldDamage = Math.min(Math.max(shield, 0), rawAmount);
            rawAmount -= shieldDamage;
            if (rawAmount > 0) {
                health -= rawAmount;
            }
        }
        this.hitTime = 1f;
        if (health <= 0f && !dead) {
            kill();
        }
    }

    /**
     * 死亡拒绝+复活 (PU132 EndComp.destroy/remove 完整移植):
     * 台账 (trueHealth) 未耗尽时, 播放红色蓄力特效并复活。
     */
    private boolean denyDeath() {
        if (trueHealth > 0f) {
            aggression = 4f;
            aggressionTime = 10f * 60f;
            health = Math.max(health, Math.min(trueHealth, trueMaxHealth));
            hitTime = 1f;
            zzw.content.units.effects.SpecialFx.endDeny.at(x, y, rotation, this);
            return true;
        }
        return false;
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
        if (trueHealth > 0f && health > 0f) return;
        super.remove();
    }
}
