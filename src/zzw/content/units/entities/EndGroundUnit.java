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
 * 防作弊机制 (与 EndLegsUnit 完全一致, 仅父类不同):
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

        // 自行计算伤害 (绕过原版 health 处理, 直接扣 trueHealth)
        float tmpAmount = Math.max(amount - armor, Vars.minArmorDamage * amount) / healthMultiplier;

        if (tmpAmount > 0) {
            float shieldDamage = Math.min(Math.max(shield, 0), tmpAmount);
            tmpAmount -= shieldDamage;

            if (tmpAmount > 0) {
                trueHealth -= tmpAmount;
            }
        }

        // 同步 health 让原版处理 hitTime (红光闪烁)
        this.hitTime = 1f;
    }

    @Override
    public void destroy() {
        if (trueHealth > 0f) {
            // 死亡拒绝: 增加怒气 (PU132 EndComp.destroy L72-78)
            aggression = 4f;
            aggressionTime = 10f * 60f;
            return;
        }
        super.destroy();
    }

    @Override
    public void kill() {
        if (trueHealth > 0f) {
            aggression = 4f;
            aggressionTime = 10f * 60f;
            return;
        }
        super.kill();
    }

    @Override
    public void remove() {
        if (trueHealth > 0f) {
            aggression = 4f;
            aggressionTime = 10f * 60f;
            return;
        }
        super.remove();
    }
}
