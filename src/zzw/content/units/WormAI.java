package zzw.content.units;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.ai.types.*;
import mindustry.gen.*;
import mindustry.world.meta.BlockFlag;

import static mindustry.Vars.*;

/**
 * 虫子单位专用 AI (融合 PU132 WormAI + v158 CommandAI)
 *
 * ★ 龙的攻击方式:
 *   1. 发现目标 → 径直朝目标冲刺
 *   2. 冲过目标 → 大幅折返 (像龙甩尾一样)
 *   3. 再次朝目标冲刺 → 反复穿梭
 *   4. 加入随机偏移和摆动, 使移动更生动不死板
 *   5. 身体自然蜿蜒跟随, 展示力量感
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

    /** 穿梭攻击的甩尾方向 (1 或 -1) */
    protected int weaveSide = 1;
    /** 上次切换甩尾方向的时间 */
    protected float lastWeaveSwitch = 0f;
    /** 当前随机摆动相位 */
    protected float weavePhase = 0f;

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

        // ===== 移动逻辑 (龙的穿梭+盘旋攻击方式) =====
        if (attackTarget != null) {
            target = attackTarget;
            if (targetPos == null) targetPos = new Vec2();
            targetPos.set(attackTarget);

            float distance = unit.dst(attackTarget);
            float targetAngle = unit.angleTo(attackTarget);
            float angleDiff = Angles.angleDist(unit.rotation, targetAngle);
            float engageRange = unit.type.range - 10f;

            // ★ 盘旋半径: 根据单位大小自动判定
            // 单位总长度 ≈ segmentSpacing * 段数, 盘旋半径 = 总长度的 40% + hitSize
            // 至少为 engageRange * 0.5 (保证在攻击范围内)
            float orbitRadius = engageRange * 0.55f;
            if (unit instanceof SegmentWormEntity) {
                SegmentWormEntity worm = (SegmentWormEntity) unit;
                if (worm.segments.length > 0) {
                    float totalLength = worm.segmentSpacing * worm.segments.length;
                    orbitRadius = Math.max(orbitRadius, totalLength * 0.4f + unit.hitSize);
                }
            }

            // ★ 边界检测: 接近地图边缘时强制朝目标方向快速转向
            float margin = 300f;
            float worldW = world.unitWidth();
            float worldH = world.unitHeight();
            boolean nearBorder = unit.x < margin || unit.x > worldW - margin
                    || unit.y < margin || unit.y > worldH - margin;

            if (distance > engageRange) {
                // ★ 阶段1: 远离目标 → 转向目标 + 朝目标冲刺
                boolean isUlt = (unit instanceof SegmentWormEntity) && ((SegmentWormEntity) unit).isUltActive();
                float ultScl = isUlt ? ((SegmentWormEntity) unit).ultSpeedMultiplier() : 1f;
                float turnSpeed;
                if (nearBorder) {
                    turnSpeed = 0.08f;
                } else {
                    turnSpeed = 0.06f * ultScl;
                }
                unit.rotation = Mathf.slerpDelta(unit.rotation, targetAngle, turnSpeed);

                // 朝当前朝向移动 (龙飞行姿态)
                vec.trns(unit.rotation, unit.speed());
                unit.moveAt(vec);
            } else if (distance > orbitRadius) {
                // ★ 阶段2: 在攻击范围内但离目标较远 → 朝目标冲近
                boolean isUlt = (unit instanceof SegmentWormEntity) && ((SegmentWormEntity) unit).isUltActive();
                float ultScl = isUlt ? ((SegmentWormEntity) unit).ultSpeedMultiplier() : 1f;
                float turnSpeed = 0.06f * ultScl;
                unit.rotation = Mathf.slerpDelta(unit.rotation, targetAngle, turnSpeed);
                vec.trns(unit.rotation, unit.speed());
                unit.moveAt(vec);
            } else {
                // ★ 阶段3: 进入盘旋半径 → 围绕目标盘旋攻击
                //   盘旋方向: 垂直于目标方向 (切线方向)
                //   旋转速度根据单位大小调整: 大单位转慢, 小单位转快
                orbitAttack(attackTarget, orbitRadius);
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
     * ★ 穿梭折返攻击 (龙的攻击方式)
     * - 朝目标径直冲刺, 加入轻微左右摆动
     * - 冲过目标后(背对目标且距离变远), 大幅折返
     * - 折返方向有随机偏移, 每次不完全对称
     * - 身体自然甩动, 展示力量感
     */
    protected void attack(float engageRange) {
        float distance = unit.dst(target);
        float angleToTarget = unit.angleTo(target);
        float angleDiff = Angles.angleDist(unit.rotation, angleToTarget);

        // 判断是否"冲过"了目标:
        // 1. 距离目标很近 (在攻击范围的60%内)
        // 2. 且当前朝向背离目标 (angleDiff > 90°, 说明正在远离目标)
        boolean passedThrough = distance < engageRange * 0.6f && angleDiff > 90f;

        // 判断是否"远离"了目标: 距离很远且背对目标
        boolean tooFarAway = distance > engageRange * 2.5f && angleDiff > 100f;

        if (passedThrough || tooFarAway) {
            // 需要折返: 朝目标方向 + 随机偏移
            // 偏移角度: 基础30° + 随机0~25°, 由 weaveSide 决定左右
            float randomOffset = weaveSide * (30f + Mathf.random(25f));
            float targetDir = angleToTarget + randomOffset;

            // 平滑转向折返方向 (比正常转向更快, 模拟龙甩尾)
            unit.rotation = Mathf.slerpDelta(unit.rotation, targetDir, 0.06f);

            // 随机切换下次折返方向 (增加生动性)
            if (Time.time - lastWeaveSwitch > 60f + Mathf.random(60f)) {
                weaveSide *= -1;
                lastWeaveSwitch = Time.time;
            }
        } else {
            // 继续朝目标冲刺: 加入正弦波摆动, 模拟龙飞行时的身体起伏
            if (weavePhase == 0f) weavePhase = unit.id * 0.5f;
            float weaveFreq = 0.004f + Mathf.randomSeed(unit.id, 0.002f);
            float weaveAmp = 10f + Mathf.randomSeed(unit.id + 1, 5f);
            float weave = Mathf.sin(Time.time * weaveFreq + weavePhase) * weaveAmp;

            float desiredRot = unit.rotation + weave * 0.015f;
            unit.rotation = Mathf.slerpDelta(unit.rotation, desiredRot, 0.03f);
        }

        // 沿当前朝向移动
        vec.trns(unit.rotation, unit.speed());
        unit.moveAt(vec);
    }

    /**
     * ★ 盘旋攻击 (围绕目标做圆周运动)
     *
     * - 在 orbitRadius 处围绕目标盘旋
     * - 朝向: 切线方向 (垂直于到目标的方向)
     * - 盘旋速度: 根据单位大小调整, 大单位转慢, 小单位转快
     * - 加入轻微径向修正, 保持距离稳定
     */
    protected void orbitAttack(Teamc target, float orbitRadius) {
        float angleToTarget = unit.angleTo(target);
        float distance = unit.dst(target);

        // ★ 切线方向: 垂直于到目标的方向 (weaveSide 决定顺时针/逆时针)
        float tangentAngle = angleToTarget + 90f * weaveSide;

        // ★ 径向修正: 距离过远时朝目标拉近, 过近时推远
        float radialCorrection = 0f;
        if (distance > orbitRadius * 1.2f) {
            // 太远: 朝目标方向修正
            radialCorrection = -15f;
        } else if (distance < orbitRadius * 0.8f) {
            // 太近: 背离目标方向修正
            radialCorrection = 15f;
        }
        float desiredAngle = tangentAngle + radialCorrection;

        // ★ 旋转速度: 大单位转慢, 小单位转快
        // base 0.08f, 根据段身数衰减
        float turnSpeed = 0.08f;
        if (unit instanceof SegmentWormEntity) {
            SegmentWormEntity worm = (SegmentWormEntity) unit;
            int segCount = worm.segments.length;
            // 段数越多转越慢, 但不低于 0.04f
            turnSpeed = Math.max(0.04f, 0.08f - segCount * 0.003f);

            // 大招期间减速
            if (worm.isUltActive()) {
                turnSpeed *= worm.ultSpeedMultiplier();
            }
        }

        unit.rotation = Mathf.slerpDelta(unit.rotation, desiredAngle, turnSpeed);

        // 沿当前朝向移动
        vec.trns(unit.rotation, unit.speed());
        unit.moveAt(vec);

        // 随机切换盘旋方向 (每 10-20 秒切换一次, 增加生动性)
        if (Time.time - lastWeaveSwitch > 600f + Mathf.random(600f)) {
            weaveSide *= -1;
            lastWeaveSwitch = Time.time;
        }
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
