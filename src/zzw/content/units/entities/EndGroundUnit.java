package zzw.content.units.entities;

import zzw.content.units.ZEntityRegister;

import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.LegsUnit;

/**
 * End 阵营腿单位 (简化版防作弊, 完全用原版方法实现)
 *
 * ★ 与 EndLegsUnit 区别: 本类 extends LegsUnit (而非 UnitEntity)
 *   - EndLegsUnit extends UnitEntity: 用于 End 阵营飞行单位 (enigma/voidVessel/chronos/opticaecus)
 *   - EndGroundUnit extends LegsUnit: 用于 End 阵营腿单位 (ravager/desolation)
 *
 * 之所以分两个类: v158 腿单位(UnitType with legCount) 必须用 LegsUnit 作为 constructor,
 * 否则 UnitType.drawLegs() 中 `unit instanceof Legsc` 为 false 导致不画腿.
 *
 * 防作弊机制 (PU132 EndComp 完整移植, 参数来自 UnitType.antiCheatType):
 * 1. 多槽位无敌帧 (invFrames[]): 每次受伤占用一个槽位, 轮询使用
 * 2. 抗性累积 (resist): 高伤害累积抗性, 减少后续伤害
 * 3. 伤害曲线衰减 (Pow(2)): 超过 damageThreshold 的伤害按曲线衰减
 * 4. 单次伤害硬上限 (maxDamageTaken) — 只作用于真实血量台账
 * 5. 怒气系统 (aggression): 受伤后加速武器 reload
 * 6. 死亡拒绝+复活 (PU132 EndComp.destroy/remove L55-78):
 *    显示血量 (health) 先于台账耗尽 → destroy/kill 被拒绝 →
 *    播放红色蓄力特效 (SpecialFx.endDeny) + 狂暴 → 血量回充到台账值;
 *    台账 (trueHealth) 耗尽后才真正死亡。
 *
 * ★ 血量双轨制 (PU132 关键机制):
 *   - health (显示血量): 按原始伤害 (仅护甲/护盾修正) 扣减, 先归零;
 *   - trueHealth (真实台账): 按防作弊上限/曲线/抗性扣减, 慢得多;
 *   - health 归零触发 kill → 台账 > 0 → 拒绝+复活 (health = trueHealth)。
 *
 * ★ 无 antiCheatType 配置时回退到 PU132 voidVessel/chronos 默认参数。
 */
public class EndGroundUnit extends LegsUnit {
    private static final Interp curveType = new Interp.Pow(2);

    // 防作弊运行时数据
    private float trueHealth, trueMaxHealth;
    private float aggression = 0f;
    private float aggressionTime = 0f;
    private float[] invFrames;
    private int invIndex = 0;
    private float invTimer = 0f;
    private float resist, resistMax, resistTime;
    /** 单位配置的防作弊参数集 (add() 时从 UnitType.antiCheatType 读取) */
    private zzw.content.units.anticheat.EndCheatVars ac;

    /** 工厂方法 (UnitType.constructor 用) */
    public static EndGroundUnit create() {
        return new EndGroundUnit();
    }

    /** 返回注册的 classId (绕过 v154.3 的 checkEntityMapping 检查) */
    @Override
    public int classId() {
        return ZEntityRegister.classId(EndGroundUnit.class);
    }

    @Override
    public void add() {
        if (added) return;
        super.add();
        // 读取单位配置的防作弊参数 (无配置时回退 PU132 voidVessel/chronos 默认值)
        if (type instanceof zzw.content.type.UnityUnitType u && u.antiCheatType != null) {
            ac = u.antiCheatType;
        }
        // 初始化防作弊数据
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
        // 读取防作弊参数 (未配置时用 PU132 voidVessel/chronos 默认比例)
        float damageThreshold = ac != null ? ac.damageThreshold : trueMaxHealth / 20f;
        float maxDamageThreshold = ac != null ? ac.maxDamageThreshold : trueMaxHealth / 1.25f;
        float maxDamageTaken = ac != null ? ac.maxDamageTaken : trueMaxHealth / 15f;
        float resistStart = ac != null ? ac.resistStart : trueMaxHealth / 25f;
        float resistScl = ac != null ? ac.resistScl : 0.2f;
        float invincibilityDuration = ac != null ? ac.invincibilityDuration : 15f;
        float resistTimeMax = ac != null ? ac.resistTime : 3f * 60f;
        Interp curve = ac != null ? ac.curveType : curveType;

        // ★ 防作弊伤害处理 (复刻 PU132 EndComp.damage L210-257)
        if (invFrames[invIndex] <= 0f) {
            float nextAmount = Math.min(amount, maxDamageTaken);

            // 抗性累积
            if (amount > resistStart) {
                float a = amount - resistStart;
                resist += a;
                if (Float.isInfinite(resist)) resist = Float.MAX_VALUE;
                resistMax = Math.max(resistMax, resist);
                resistTime = resistTimeMax;
                aggression += Math.min(a / (trueMaxHealth / 5f), 1.5f);
                aggression = Math.min(aggression, 4f);
                aggressionTime = 5f * 60f;
            }

            // 伤害曲线衰减 (PU132 curveType, 默认 Pow(2))
            if (amount > damageThreshold) {
                float in = 1f - curve.apply(Mathf.clamp((amount - damageThreshold) / (maxDamageThreshold - damageThreshold)));
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

        // ★ 原版显示血量并行扣减 (PU132 关键机制):
        // 显示血量按"未经防作弊上限的原始伤害"扣减, 比台账先归零;
        // 归零触发 kill() → 台账 > 0 → 拒绝死亡 + 红色蓄力特效 + 复活。
        float rawAmount = Math.max(amount - armor, Vars.minArmorDamage * amount) / healthMultiplier;
        if (rawAmount > 0) {
            float shieldDamage = Math.min(Math.max(shield, 0), rawAmount);
            rawAmount -= shieldDamage;
            if (rawAmount > 0) {
                health -= rawAmount;
            }
        }
        this.hitTime = 1f;
        // 血量归零 → 触发原版死亡链 (kill → destroy → 拒绝判定)
        if (health <= 0f && !dead) {
            kill();
        }
    }

    /**
     * 死亡拒绝+复活 (PU132 EndComp.destroy/remove L55-78 完整移植):
     * 台账 (trueHealth) 未耗尽时, 播放红色蓄力特效并复活。
     */
    private boolean denyDeath() {
        if (trueHealth > 0f) {
            // 狂暴: 4 倍速 + 持续 10 秒 (PU32 aggression=4, aggressionTime=10*60)
            aggression = 4f;
            aggressionTime = 10f * 60f;
            // 复活: 血量回充到台账值 (台账 > 0)
            health = Math.max(health, Math.min(trueHealth, trueMaxHealth));
            hitTime = 1f;
            // 红色粒子蓄力特效 (PU132 SpecialFx.endDeny)
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
