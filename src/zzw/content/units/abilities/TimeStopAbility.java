package zzw.content.units.abilities;

import arc.util.Time;
import arc.math.Mathf;
import mindustry.entities.abilities.Ability;
import mindustry.content.Fx;
import mindustry.gen.Unit;
import mindustry.gen.Teamc;
import mindustry.entities.Units;
import arc.audio.Sound;

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
 *
 * ★ 修复卡死: PU132 有 update 标志防止递归 (unit.update() → ability.update() → trigger() → unit.update()...)
 *   简化版缺少此保护, 300次迭代 × 递归 = 游戏卡死
 *   修复: 1) adding updating 标志防止递归 2) 限制迭代次数 (60次=3秒模拟时间)
 */
public class TimeStopAbility extends Ability {
    /** 持续时间 (ticks) */
    public float duration;
    /** 充能时间 (ticks) */
    public float rechargeTime;
    /** 触发范围 */
    public float range = 300f;
    /** 最大模拟迭代次数 (防止卡死, 60次=3秒模拟时间) */
    public int maxIterations = 60;

    /** ★ PU132 原版: timeStopSound = UnitySounds.stopTime */
    public Sound timeStopSound;

    /** 当前充能计时器 */
    protected float timer = 0f;
    /** ★ 递归保护: 正在模拟更新时为 true, 防止 unit.update() → ability.update() → trigger() 递归 */
    private boolean updating = false;

    public TimeStopAbility(float duration, float rechargeTime) {
        this.duration = duration;
        this.rechargeTime = rechargeTime;
    }

    @Override
    public void update(Unit unit) {
        // ★ 递归保护: 模拟更新期间跳过 (防止 unit.update() → ability.update() → trigger() 递归)
        if (updating) return;

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

        // ★ PU132 原版: timeStopSound.at(unit.x, unit.y)
        if (timeStopSound != null) {
            timeStopSound.at(unit.x, unit.y, Mathf.random(0.9f, 1.1f));
        }

        // 视觉特效 (v158 无 nuclearShockwave, 用 shockwave 替代)
        Fx.shockwave.at(unit.x, unit.y, unit.hitSize);
        Fx.smoke.at(unit.x, unit.y);

        // 快速模拟 (PU132 TimeStopAbility.use L43-53)
        // Time.delta 设为 3f, 循环调用 unit.update()
        // ★ PU132 有 update 标志防止递归, 这里用 updating 标志替代
        float delta = Time.delta;
        Time.delta = 3f;
        updating = true;  // ★ 递归保护开始

        int iterations = 0;
        for (float i = 0; i < duration && iterations < maxIterations; i += Time.delta) {
            try {
                unit.update();
            } catch (Throwable t) {
                break;
            }
            iterations++;
        }

        updating = false;  // ★ 递归保护结束
        Time.delta = delta;
    }
}
