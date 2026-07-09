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
 * ★ v158 适配: 继承 CommandAI (playerControllable=true 单位默认使用 CommandAI)
 *   - 重写 updateUnit(): 先 updateTargeting() 找 target (findMainTarget 优先核心)
 *   - 无玩家命令时自动将 target 设为 attackTarget, 并驱动移动
 *   - 设置 targetPos 使 hasCommand()=true, 避免被 SegmentWormEntity 误判待机清零 vel
 *
 * PU132 原版移动逻辑 (WormAI.updateMovement):
 *   - circleTarget=false (arcnelidia): moveTo(target, range*0.8f) + lookAt(target)
 *     → 直线冲向目标, 越过目标后再折返, 整段身体都能造成伤害
 *   - circleTarget=true (toxobyte/catenapede/oppression/devourer): attack(120f)
 *     → 环绕目标盘旋, 最大化段身武器/持续伤害的作用
 *   - attack() 方法: 沿单位朝向移动, 角度差>100°且不在环绕范围内时slerp转向
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
    protected float rotateTime = 0f;

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

        // ===== 移动逻辑 (对齐 PU132 WormAI.updateMovement) =====
        if (attackTarget != null) {
            // 同步 target = attackTarget, 供 faceTarget / circleAttack / attack 使用
            target = attackTarget;
            // 设置 targetPos 使 hasCommand()=true, 避免被误判待机
            if (targetPos == null) targetPos = new Vec2();
            targetPos.set(attackTarget);

            if (!unit.type.circleTarget) {
                // ★ 直线冲过模式 (arcnelidia): moveTo(target, range*0.8f) + lookAt
                //   距离>range*0.8时冲向目标, 进入后继续沿朝向飞, 越过目标后折返
                //   配合 faceTarget=false, 单位保持朝向飞行方向而非盯着目标
                float circleLength = unit.type.range * 0.8f;
                moveTo(attackTarget, circleLength, unit.isFlying() ? 40f : 100f);
                unit.lookAt(attackTarget);
            } else {
                // ★ 环绕模式 (toxobyte/catenapede/oppression/devourer): attack(120f)
                //   沿单位朝向飞行, 角度差大时转向, 形成环绕效果
                //   配合 omniMovement=false, 单位只能向前飞, 更像虫子
                attack(120f);
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

        rotateTime = Math.max(0f, rotateTime - Time.delta);
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
     * PU132 attack(): 环绕攻击 (circleTarget=true 时使用)
     * - 沿单位朝向移动 (vec.trns(unit.rotation, unit.speed()))
     * - 角度差>100°且不在环绕范围内时, slerp转向目标
     * - 触发40帧的rotateTime反向旋转
     *
     * 原版行为: 单位一直向前飞, 当目标不在正前方且距离够远时大角度转向,
     * 形成"冲过去 → 绕一圈 → 再冲回来"的环绕效果, 让段身武器全部发挥作用
     */
    protected void attack(float circleLength) {
        vec.trns(unit.rotation, unit.speed());
        float diff = Angles.angleDist(unit.rotation, unit.angleTo(target));
        if ((diff > 100f && !unit.within(target, circleLength)) || rotateTime > 0f) {
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
