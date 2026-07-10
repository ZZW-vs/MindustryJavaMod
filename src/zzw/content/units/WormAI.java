package zzw.content.units;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.ai.types.*;
import mindustry.gen.*;
import mindustry.world.meta.BlockFlag;

import static mindustry.Vars.*;

/**
 * 虫子单位专用 AI (移植 PU132 WormAI, 适配 v158 CommandAI)
 *
 * PU132 原版逻辑 (WormAI.updateMovement L22-45):
 * - circleTarget=false: moveTo(target, range*0.8) + lookAt(target)
 * - circleTarget=true:  attack(circleLength) 围绕目标转圈
 *
 * v158 适配: 继承 CommandAI, 重写 updateUnit() 实现自动索敌+移动
 */
public class WormAI extends CommandAI {

    /** 是否处于待机状态 */
    public boolean isIdle = false;

    /** PU132: 段身受击通知的记仇位置 (score高的覆盖低的, 持续180帧) */
    public Vec2 pos = new Vec2();
    public float score = 0f;
    public float time = 0f;

    /** 盘旋方向 (1 或 -1) */
    protected int orbitDir = 1;
    /** 旋转计时器 (PU132 rotateTime) */
    protected float rotateTime = 0f;

    @Override
    public void updateUnit() {
        updateVisuals();
        updateTargeting();

        // 清除无效 attackTarget, 并清空 targetPos
        if (attackTarget != null && invalid(attackTarget)) {
            attackTarget = null;
            targetPos = null;
        }

        // 自动索敌: 无玩家命令时自动将 target 设为 attackTarget
        if (!hasCommand() && attackTarget == null && target != null && !invalid(target)) {
            attackTarget = target;
        }

        // PU132 记仇机制: 无 target 但有记仇位置时移动过去
        if (target == null && time > 0f && attackTarget == null && targetPos == null) {
            targetPos = new Vec2(pos);
        }

        // ===== 移动逻辑 (参考 PU132 WormAI.updateMovement L26-33) =====
        if (attackTarget != null) {
            target = attackTarget;
            if (targetPos == null) targetPos = new Vec2();
            targetPos.set(attackTarget);

            if (!unit.type.circleTarget) {
                // ★ 非盘旋模式 (arcnelidia): 盯着目标冲过去
                // PU132: moveTo(target, unit.range() * 0.8f); unit.lookAt(target);
                // 武器 rotate=false, 必须正面对着目标才能打, 所以 lookAt(target) 很重要
                moveTo(target, unit.range() * 0.8f, unit.isFlying() ? 20f : 100f);
                unit.lookAt(target);
            } else {
                // ★ 盘旋模式 (toxobyte/catenapede/devourer/oppression)
                // 攻击范围作为盘旋半径的参考
                attack(unit.range());
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

        // rotateTime 衰减 (PU132 L42)
        rotateTime = Math.max(0f, rotateTime - Time.delta);
        if (time <= 0f) score = 0f;
        time = Math.max(0f, time - Time.delta);
    }

    /**
     * 优先攻击最近的目标 (核心或单位)
     */
    @Override
    public Teamc findMainTarget(float x, float y, float range, boolean air, boolean ground) {
        Teamc core = targetFlag(x, y, BlockFlag.core, true);
        Teamc unitTarget = super.findMainTarget(x, y, range, air, ground);

        if (core == null) return unitTarget;
        if (unitTarget == null) return core;

        float coreDist = unit.dst(core);
        float unitDist = unit.dst(unitTarget);
        return coreDist < unitDist ? core : unitTarget;
    }

    /**
     * ★ 盘旋攻击 (移植 PU132 WormAI.attack L66-74)
     *
     * 原版逻辑非常简单:
     * - 保持当前速度向前
     * - 当朝向与目标方向差 > 100° 且距离 > circleLength 时, 平滑转向目标
     * - 转完后 rotateTime = 40f (冷却)
     *
     * 这样单位会自然形成围绕目标的大圆, 路径平滑不会变成六边形
     */
    protected void attack(float circleLength) {
        float speed = unit.speed();

        // 大招期间减速
        if (unit instanceof SegmentWormEntity worm && worm.isUltActive()) {
            speed *= worm.ultSpeedMultiplier();
        }

        vec.trns(unit.rotation, speed);

        float diff = Angles.angleDist(unit.rotation, unit.angleTo(target));
        if ((diff > 100f && !unit.within(target, circleLength)) || rotateTime > 0f) {
            // 需要转向: 平滑转到目标方向
            vec.setAngle(Mathf.slerpDelta(vec.angle(), unit.angleTo(target), 0.2f));
            if (rotateTime <= 0f) rotateTime = 40f;
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
