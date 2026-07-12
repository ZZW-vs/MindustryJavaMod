package zzw.content.units;

import arc.util.Time;
import mindustry.entities.abilities.Ability;
import mindustry.entities.Effect;
import mindustry.content.Fx;
import mindustry.gen.Unit;
import mindustry.gen.Teamc;
import mindustry.entities.Units;

/**
 * 时间停止能力 (移植自 PU132 TimeStopAbility, 简化版)
 *
 * PU132 原版:
 * - 玩家触发: 加入全局 TimeStop 系统, 冻结所有其他实体
 * - AI 触发: 快速模拟单位更新 (Time.delta=3f, 循环调用 unit.update())
 *
 * 简化版:
 * - 自动触发: 当附近有敌方目标且充能完成时
 * - 效果: 快速模拟单位更新 (让单位在瞬间行动多次)
 * - 视觉: 冲击波特效
 */
public class TimeStopAbility extends Ability {
    /** 持续时间 (ticks) */
    public float duration;
    /** 充能时间 (ticks) */
    public float rechargeTime;
    /** 触发范围 */
    public float range = 300f;

    /** 当前充能计时器 */
    protected float timer = 0f;

    public TimeStopAbility(float duration, float rechargeTime) {
        this.duration = duration;
        this.rechargeTime = rechargeTime;
    }

    @Override
    public void update(Unit unit) {
        timer += Time.delta;

        if (timer >= rechargeTime) {
            // 检查附近是否有敌方目标
            Teamc target = Units.closestTarget(unit.team, unit.x, unit.y, range,
                u -> !u.dead, t -> true);
            if (target != null) {
                trigger(unit);
            }
        }
    }

    /**
     * 触发时间停止 (PU132 原版 AI 逻辑)
     * 快速模拟单位更新, 让单位在瞬间行动多次
     */
    protected void trigger(Unit unit) {
        timer = 0f;

        // 视觉特效 (v158 无 nuclearShockwave, 用 shockwave 替代)
        Fx.shockwave.at(unit.x, unit.y, unit.hitSize);
        Fx.smoke.at(unit.x, unit.y);

        // 快速模拟 (PU132 TimeStopAbility.use L43-53)
        // Time.delta 设为 3f, 循环调用 unit.update()
        // 这样单位会在一帧内执行 duration/3 ≈ 300 次更新
        float delta = Time.delta;
        Time.delta = 3f;
        for (float i = 0; i < duration; i += Time.delta) {
            try {
                unit.update();
            } catch (Throwable t) {
                break;
            }
        }
        Time.delta = delta;
    }
}
