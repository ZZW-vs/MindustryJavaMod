package zzw.content.units;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.UnitEntity;
import mindustry.gen.Unitc;

/**
 * 防作弊单位基类 (移植自 PU132 AntiCheatBase 接口)
 * - lastHealth 记录上次血量
 * - invTime 无敌帧
 * - immunity 抗性递增
 * - 死亡拒绝 (lastHealth > 0 时不死亡)
 */
public class AntiCheatBase {
    public float lastHealth = 0f;
    public float lastMaxHealth = 0f;
    public float invTime = 0f;
    public float immunity = 1f;

    /**
     * 应用防作弊伤害
     * @param amount 原始伤害
     * @return 实际造成的伤害
     */
    public float applyAntiCheatDamage(float amount) {
        if (invTime < 30f) {
            return 0f;
        }
        invTime = 0f;
        float max = Math.max(220f, lastMaxHealth / 700f);
        float trueDamage = Mathf.clamp(amount / immunity, 0f, max);
        max *= 1.5f;
        immunity += (float) Math.pow(Math.max(amount - max, 0f) / max, 2) * 2f;
        lastHealth -= trueDamage;
        return trueDamage;
    }

    public void updateAntiCheat() {
        invTime += Time.delta;
        immunity = Math.max(1f, immunity - (Time.delta / 4f));
    }
}
