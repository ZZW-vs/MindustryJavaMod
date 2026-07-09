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

        // 清除无效 attackTarget, 但不清零 targetPos
        // ★ 保留 targetPos 使 hasCommand()=true, 避免 isIdle() 清零 vel 导致走走停停
        if (attackTarget != null && invalid(attackTarget)) {
            attackTarget = null;
        }

        // ★ 自动索敌: 无玩家命令时, 自动将 target 设为 attackTarget
        // 检查 target 有效性, 避免把无效目标赋给 attackTarget
        if (!hasCommand() && attackTarget == null && target != null && !invalid(target)) {
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

            // ★ 边界检测: 接近地图边缘时强制朝目标方向快速转向
            float margin = 300f;
            float worldW = world.unitWidth();
            float worldH = world.unitHeight();
            boolean nearBorder = unit.x < margin || unit.x > worldW - margin
                    || unit.y < margin || unit.y > worldH - margin;

            if (distance > engageRange) {
                // ★ 阶段1: 远离目标 → 转向目标 + 朝目标冲刺
                //   非大招: 快速转向 (slerp 0.06f, 约每秒3.6度)
                //   大招期间: 用 rotateSpeed 限制, 与玩家操控一致
                boolean isUlt = (unit instanceof SegmentWormEntity) && ((SegmentWormEntity) unit).isUltActive();
                float turnSpeed;
                if (nearBorder) {
                    // 接近边界时快速转向目标, 避免飞出地图
                    turnSpeed = 0.08f;
                } else if (isUlt) {
                    // 大招期间: 与玩家操控相同的转速
                    float maxTurnPerFrame = unit.type.rotateSpeed / 60f;
                    turnSpeed = Math.min(1f, maxTurnPerFrame / Math.max(0.1f, angleDiff));
                } else {
                    // 非大招: 较快的转向, 但不是瞬间
                    turnSpeed = 0.06f;
                }
                unit.rotation = Mathf.slerpDelta(unit.rotation, targetAngle, turnSpeed);

                // 朝当前朝向移动 (龙飞行姿态)
                vec.trns(unit.rotation, unit.speed());
                unit.moveAt(vec);

                turning = true;
            } else {
                // ★ 阶段2: 进入攻击范围 → 环绕攻击
                //   像龙一样: 在目标周围盘旋, 头部转向目标

                if (unit.type.circleTarget) {
                    // 环绕模式: 切向移动形成圆周运动
                    attack(engageRange);
                } else {
                    // 直线模式: 朝目标移动
                    vec.trns(unit.rotation, unit.speed());
                    unit.moveAt(vec);
                }

                // 头部转向目标, 大招期间用 rotateSpeed 限制
                boolean isUlt = (unit instanceof SegmentWormEntity) && ((SegmentWormEntity) unit).isUltActive();
                float turnSpeed;
                if (isUlt) {
                    float maxTurnPerFrame = unit.type.rotateSpeed / 60f;
                    turnSpeed = Math.min(1f, maxTurnPerFrame / Math.max(0.1f, angleDiff));
                } else {
                    turnSpeed = 0.05f;
                }
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
     * - 距离目标远: 朝目标方向移动
     * - 距离目标近: 做切向移动 (垂直于目标方向), 形成圆周环绕
     * - 身体自然摆动, 展示力量感
     */
    protected void attack(float circleLength) {
        float distance = unit.dst(target);
        float angleToTarget = unit.angleTo(target);

        if (distance > circleLength) {
            // 目标太远: 朝目标方向移动
            vec.trns(angleToTarget, unit.speed());
        } else {
            // 在环绕范围内: 切向移动 (目标方向 + 90度), 形成圆周运动
            vec.trns(angleToTarget + 90f, unit.speed());
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
