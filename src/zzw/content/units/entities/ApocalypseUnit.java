package zzw.content.units.entities;

import arc.math.Mathf;
import arc.util.Time;
import zzw.content.units.ZEntityRegister;

/**
 * 天启单位 (移植自 PU132 entities/units/ApocalypseUnit 完整版)
 *
 * <p>继承隐形单位基类 EndInvisibleUnit (PU132 EndInvisibleUnit = EndUnit + InvisibleComp),
 * 额外实现 PU132 ApocalypseUnit 的两层防作弊:</p>
 *
 * <p>1. damage() 伤害免疫累积 (PU132 ApocalypseUnit.damage L44-57):
 * <br>- 无敌帧 &lt; 30 tick 时完全免伤;
 * <br>- 单次伤害上限 max(220, health/700) (health 为类型满血 1725000 → 约 2464);
 * <br>- 免疫累积 immunity: 超过上限×1.5 的伤害按平方增长累积抗性,
 *     抗性随时间以 Time.delta/4 速度衰减回 1。</p>
 *
 * <p>2. overrideAntiCheatDamage(v, priority) 优先级无敌帧 (PU132 L33-42):
 * <br>- 5 个优先级槽位, 各自独立 30 tick 无敌帧;
 * <br>- 供 AntiCheatBulletModule 等模块调用, 绕过 immunity 直接扣原始血量。</p>
 *
 * <p>触手系统: PU132 原版通过 Tentaclec 接口在实体上挂 Tentacle 列表,
 * 本移植把触手实现为 TentacleAbility (能力驱动, 自主更新/渲染),
 * 因此实体侧不再持有 tentacles 列表。</p>
 */
public class ApocalypseUnit extends EndInvisibleUnit {
    /** 伤害免疫累积系数 (PU132 ApocalypseUnit.immunity, 1=无抗性) */
    private float immunity = 1f;
    /** 5 个优先级无敌帧槽位 (PU132 invFrames[5]) */
    private final float[] invFrames = new float[5];

    /** 工厂方法 (UnitType.constructor 用) */
    public static ApocalypseUnit create() {
        return new ApocalypseUnit();
    }

    /** 返回注册的 classId (v155.4+ 要求显式实体注册) */
    @Override
    public int classId() {
        return ZEntityRegister.classId(ApocalypseUnit.class);
    }

    /**
     * 优先级无敌帧扣血 (PU132 overrideAntiCheatDamage L33-42)。
     *
     * <p>算法逐步解释:</p>
     * <p>1. 取 priority 对应槽位 (clamp 到 0-4 防越界);
     * <br>2. 槽位无敌帧 &lt; 30 tick → 该优先级刚被用过, 拒绝伤害;
     * <br>3. 否则闪白 (hitTime=1), 清零该槽位, 直接从原始血量与当前血量中扣除 v。</p>
     *
     * @param v 要扣除的伤害
     * @param priority 优先级槽位 (0-4)
     */
    public void overrideAntiCheatDamage(float v, int priority) {
        if (invFrames[Mathf.clamp(priority, 0, invFrames.length - 1)] < 30f) return;
        hitTime = 1f;
        invFrames[Mathf.clamp(priority, 0, invFrames.length - 1)] = 0f;
        subtractHealthRaw(v);
    }

    /** @see #overrideAntiCheatDamage(float, int) 默认 0 号槽位 */
    public void overrideAntiCheatDamage(float v) {
        overrideAntiCheatDamage(v, 0);
    }

    /**
     * 伤害免疫处理 (PU132 ApocalypseUnit.damage L44-57 完整复刻)。
     *
     * <p>与基类 EndInvisibleUnit.damage 的区别:</p>
     * <p>- 无敌帧阈值 30 tick (基类 15 tick);
     * <br>- 抗性使用本类独立的 immunity 字段 (基类共用 AntiCheatBase);
     * <br>- 不设置 disabledTime (受伤不强制现身, 只靠近距离扫描现身, 与 PU132 一致);
     * <br>- 单次上限按类型满血 health/700 计算 (基类按当前 maxHealth)。</p>
     *
     * <p>算法逐步解释:</p>
     * <p>1. invFrame &lt; 30 tick → 免伤直接返回;
     * <br>2. 上限 max = max(220, 类型满血/700);
     * <br>3. 实际伤害 = clamp(伤害/immunity, 0, max);
     * <br>4. 超过 max×1.5 的部分按平方累积抗性 (打法越猛抗性涨越快);
     * <br>5. 原始血量与当前血量同时扣除, 最后走 Mindustry 原版扣血路径。</p>
     *
     * @param amount 原始伤害
     */
    @Override
    public void damage(float amount) {
        if (getInvFrame() < 30f) return;
        resetInvFrame();

        float max = Math.max(220f, type.health / 700f);
        float trueAmount = Mathf.clamp(amount / immunity, 0f, max);

        max *= 1.5f;
        immunity += (float)Math.pow(Math.max(amount - max, 0f) / max, 2) * 2f;

        // 台账扣减 + 原版显示血量扣减 (原始伤害 → health 比台账先归零 → 触发拒绝死亡)
        subtractLastHealth(trueAmount);
        damageMindustry(amount);
    }

    @Override
    public void update() {
        super.update();

        // 无敌帧推进 (PU132 update L60-66)
        for (int i = 0; i < invFrames.length; i++) {
            invFrames[i] += Time.delta;
        }
        // 抗性衰减: 每 4 tick 回落 1 点 (PU132: immunity -= Time.delta / 4f)
        immunity = Math.max(1f, immunity - (Time.delta / 4f));
    }
}
