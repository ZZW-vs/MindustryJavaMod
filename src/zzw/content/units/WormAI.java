package zzw.content.units;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.ai.UnitCommand;
import mindustry.ai.UnitStance;
import mindustry.ai.types.*;
import mindustry.gen.*;
import mindustry.world.meta.BlockFlag;

import static mindustry.Vars.*;

/**
 * 虫子单位专用 AI (融合 PU132 WormAI + v158 CommandAI)
 *
 * ★ 龙的攻击方式:
 *   1. 头部逐渐转向目标 (slerp平滑转向, 不是瞬间锁定)
 *   2. 沿当前朝向冲刺 (不是直接朝目标移动, 像龙一样飞)
 *   3. 身体自然蜿蜒跟随 (由段身物理约束控制)
 *   4. 到达攻击范围后, 头部锁定目标攻击
 *
 * ★ v158 适配: 继承 CommandAI (playerControllable=true 单位默认使用 CommandAI)
 *   - 重写 updateUnit(): 先 updateTargeting() 找 target (findMainTarget 优先核心)
 *   - 无玩家命令时自动将 target 设为 attackTarget, 并驱动移动
 *   - 设置 targetPos 使 hasCommand()=true, 避免被 SegmentWormEntity 误判待机清零 vel
 *
 * PU132 保留:
 * - setTarget(): 段身受击时通知头部 (记仇机制)
 */
public class WormAI extends CommandAI {

    /** 是否处于待机状态 (无目标), 供 SegmentWormEntity 判断是否清零 vel */
    public boolean isIdle = false;

    /** PU132: 段身受击通知的目标位置 (score高的覆盖低的, 持续180帧) */
    public Vec2 pos = new Vec2();
    public float score = 0f;
    public float time = 0f;

    /** 当前转向目标角度 (用于平滑转向) */
    protected float targetAngle = 0f;
    /** 是否正在转向目标 */
    protected boolean turning = false;

    @Override
    public void updateUnit() {
        updateVisuals();
        updateTargeting();

        // 清除无效 attackTarget
        if (attackTarget != null && invalid(attackTarget)) {
            attackTarget = null;
            targetPos = null;
        }

        // ★ 自动索敌: 无玩家命令时, 自动将 target 设为 attackTarget
        if (!hasCommand() && attackTarget == null && target != null) {
            attackTarget = target;
        }

        // PU132 记仇机制: 无 target 但有记仇位置时, 移动到记仇位置
        if (target == null && time > 0f && attackTarget == null && targetPos == null) {
            targetPos = new Vec2(pos);
        }

        // ===== 移动逻辑 (龙的攻击方式) =====
        if (attackTarget != null) {
            target = attackTarget;
            if (targetPos == null) targetPos = new Vec2();
            targetPos.set(attackTarget);

            float distance = unit.dst(attackTarget);
            float targetAngle = unit.angleTo(attackTarget);
            float angleDiff = Angles.angleDist(unit.rotation, targetAngle);
            float engageRange = unit.type.range - 10f;

            if (distance > engageRange) {
                // ★ 阶段1: 远离目标 → 平滑转向 + 沿当前朝向冲刺
                //   像龙一样: 先转向目标方向, 然后沿当前朝向飞过去
                //   身体会自然蜿蜒跟随, 形成龙飞行的姿态

                // 平滑转向目标, 转向速度受 rotateSpeed 限制
                // rotateSpeed 是单位每秒最大旋转角度, 与玩家操控保持一致
                float maxTurnPerFrame = unit.type.rotateSpeed / 60f;
                float turnSpeed = Math.min(1f, maxTurnPerFrame / Math.max(0.1f, angleDiff));
                unit.rotation = Mathf.slerpDelta(unit.rotation, targetAngle, turnSpeed);

                // 沿当前朝向冲刺
                vec.trns(unit.rotation, unit.speed());
                unit.moveAt(vec);

                turning = true;
            } else {
                // ★ 阶段2: 进入攻击范围 → 锁定目标 + 环绕攻击
                //   头部锁定目标方向, 身体摆动
                //   像龙一样: 到达目标附近后盘旋攻击, 身体摆动展示力量感

                if (unit.type.circleTarget) {
                    // 环绕模式: 沿朝向飞, 角度差大时转向, 形成盘旋
                    attack(engageRange);
                } else {
                    // 直线模式: 继续沿朝向飞, 越过目标后折返
                    vec.trns(unit.rotation, unit.speed());
                    unit.moveAt(vec);
                }

                // 大招期间头部平滑转向目标, 使用与玩家操控相同的旋转速度
                // rotateSpeed=2.2f 意味着每秒最多转 2.2 度, 避免瞬间转动
                float maxTurnPerFrame = unit.type.rotateSpeed / 60f;
                float turnSpeed = Math.min(1f, maxTurnPerFrame / Math.max(0.1f, angleDiff));
                unit.rotation = Mathf.slerpDelta(unit.rotation, unit.angleTo(attackTarget), turnSpeed);
                turning = false;
            }
        } else if (targetPos != null) {
            // 玩家移动命令 / 记仇位置
            moveTo(targetPos, 0f, unit.isFlying() ? 40f : 100f);
            if (unit.within(targetPos, Math.max(5f, unit.hitSize / 2f))) {
                targetPos = null;
            }
            faceTarget();
        } else {
            // 待机
            faceTarget();
        }

        // 待机判断
        isIdle = (target == null && attackTarget == null && targetPos == null);

        if (time <= 0f) score = 0f;
        time = Math.max(0f, time - Time.delta);
    }

    /**
     * ★ 优先攻击敌方核心, 无核心才追踪单位
     */
    @Override
    public Teamc findMainTarget(float x, float y, float range, boolean air, boolean ground) {
        Teamc core = targetFlag(x, y, BlockFlag.core, true);
        if (core != null) {
            return core;
        }
        return super.findMainTarget(x, y, range, air, ground);
    }

    /**
     * ★ 环绕攻击 (龙的盘旋攻击模式)
     * - 沿单位朝向移动 (像龙一样向前飞)
     * - 角度差>90°且不在攻击范围内时, slerp平滑转向目标
     * - 形成"冲过去 → 绕一圈 → 再冲回来"的盘旋效果
     * - 身体自然摆动, 展示力量感
     */
    protected void attack(float circleLength) {
        vec.trns(unit.rotation, unit.speed());
        float diff = Angles.angleDist(unit.rotation, unit.angleTo(target));
        if (diff > 90f && !unit.within(target, circleLength)) {
            // 平滑转向目标, 转向速度根据角度差动态调整
            vec.setAngle(Mathf.slerpDelta(vec.angle(), unit.angleTo(target), 0.15f));
        }
        unit.moveAt(vec);
    }

    /**
     * PU132: 段身受击时通知头部 (score高的覆盖低的, 持续180帧)
     * 由 SegmentUnitEntity.damage() 调用
     */
    public void setTarget(float x, float y, float score) {
        if (score < this.score) return;
        pos.set(x, y);
        this.score = score;
        time = 3f * 60f;
    }
}
