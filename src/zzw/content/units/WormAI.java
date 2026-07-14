package zzw.content.units;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.ai.types.*;
import mindustry.gen.*;
import mindustry.world.meta.BlockFlag;

/**
 * 虫子单位专用 AI (移植 PU132 WormAI, 适配 v158 CommandAI)
 *
 * ★ PU132 原版架构 (unity.ai.WormAI, 继承 FlyingAI):
 *   - updateMovement():
 *     1. target==null && time>0 → moveTo(记仇位置, 0f)
 *     2. target!=null && hasWeapons:
 *        - circleTarget=false → moveTo(target, range*0.8) + lookAt(target)
 *        - circleTarget=true  → attack(120f)  ← 固定 120f, 非 range
 *     3. target==null && time<=0 && waves && defaultTeam → moveTo(最近spawner, dropZone+120)
 *     4. command==rally → moveTo(rallyFlag, 60)
 *   - 自定义 attack(circleLength): 保持速度向前, 朝向差>100°时平滑转向
 *   - setTarget(x,y,score): 段身受击通知头部记仇机制
 *
 * ★ v158 适配 (继承 CommandAI 而非 FlyingAI):
 *   - 玩家队伍默认用 CommandAI (支持玩家命令), 不能继承 FlyingAI
 *   - 重写 updateUnit() 实现自动索敌+移动 (PU132 通过 FlyingAI 自动索敌)
 *   - target 用于武器瞄准, attackTarget 用于移动 (需手动同步)
 *
 * ★ 优化 (相比 PU132):
 *   1. retarget 加快: target==null 时每 10 帧 (PU132 默认 40 帧), 减少目标切换延迟
 *   2. findMainTarget: 选最近目标 (core 或 unit), 不再优先 core, 让单位主动追击近的敌方单位
 *   3. attack(120f) 用 PU132 原版固定值, 自然形成大圆盘旋
 */
public class WormAI extends CommandAI {

    /** PU132: 段身受击通知的记仇位置 (score高的覆盖低的, 持续180帧) */
    public Vec2 pos = new Vec2();
    public float score = 0f;
    public float time = 0f;

    /** 旋转计时器 (PU132 rotateTime) */
    protected float rotateTime = 0f;

    @Override
    public void updateUnit() {
        updateVisuals();
        updateTargeting();  // 自动找 target (用于武器瞄准)

        // 清除无效 attackTarget, 并清空 targetPos
        if (attackTarget != null && invalid(attackTarget)) {
            attackTarget = null;
            targetPos = null;
        }

        // ★ 自动索敌: 无玩家命令时, 把 target 升级为 attackTarget
        // 这样 target 既用于武器瞄准, 也用于移动 (解决"不主动打敌方单位")
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
                // PU132 原版用固定 120f 作为盘旋半径参考
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
            // ★ PU132 L35-37: 待机时朝最近 spawner 移动 (wave 模式, defaultTeam)
            // 让多节单位在 wave 模式下不会傻站着, 而是主动推进
            if (target == null && time <= 0f
                && mindustry.Vars.state.rules.waves
                && unit.team == mindustry.Vars.state.rules.defaultTeam) {
                moveTo(getClosestSpawner(), mindustry.Vars.state.rules.dropZoneRadius + 120f);
            }
            faceTarget();
        }

        // PU132 L42-44: rotateTime 衰减, time/score 衰减
        rotateTime = Math.max(0f, rotateTime - Time.delta);
        if (time <= 0f) score = 0f;
        time = Math.max(0f, time - Time.delta);
    }

    /**
     * ★ 优化: 重写 retarget(), 加快目标搜索速度
     *
     * v158 默认 (AIController.retarget): target==null 时每 40 帧, 有 target 时每 90 帧
     * v158 CommandAI 重写: target==null 时每 20 帧, 有 attackTarget 时每 10 帧
     *
     * 优化:
     * - target==null 时每 10 帧 (每秒 6 次搜索, 几乎立即响应)
     * - 有 target 时每 30 帧 (减少搜索开销, 但仍能及时切换目标)
     *
     * 这解决了"打完一个目标会愣一下"问题:
     * 原来 target 失效后要等 20-40 帧才搜下一个目标, 现在只需 10 帧
     */
    @Override
    public boolean retarget() {
        return timer.get(timerTarget, target == null ? 10f : 30f);
    }

    /**
     * ★ 优化: findMainTarget 选最近的目标 (core 或 unit)
     *
     * PU132 默认 (FlyingAI.findMainTarget): 如果 core 在 range 内, 优先 core
     * CommandAI.findMainTarget: 调用 super (FlyingAI 行为)
     *
     * 问题: 当 core 在 range 内但敌方单位更近, 仍会优先 core, 显得不主动打敌方单位
     * 优化: 直接选距离最近的 (core 或 unit), 不再优先 core
     *
     * 这样多节单位会主动追击近的敌方单位, 而不是傻站着打不到核心
     */
    @Override
    public Teamc findMainTarget(float x, float y, float range, boolean air, boolean ground) {
        Teamc core = targetFlag(x, y, BlockFlag.core, true);
        Teamc unitTarget = super.findMainTarget(x, y, range, air, ground);

        if (core == null) return unitTarget;
        if (unitTarget == null) return core;

        // ★ 取距离最近的 (优化: 不再优先 core)
        float coreDist = unit.dst(core);
        float unitDist = unit.dst(unitTarget);
        return coreDist < unitDist ? core : unitTarget;
    }

    /**
     * ★ 移植 PU132 attack() (unity.ai.WormAI.attack L66-74)
     *
     * PU132 原版逻辑非常简单:
     * - 保持当前速度向前 (vec.trns(rotation, speed))
     * - 当朝向与目标方向差 > 100° 且距离 > circleLength 时, 平滑转向目标
     * - 转完后 rotateTime = 40f (冷却, 防止抖动)
     *
     * 这样单位会自然形成围绕目标的大圆, 路径平滑不会变成六边形
     *
     * @param circleLength 盘旋半径参考 (PU132 固定 120f)
     */
    protected void attack(float circleLength) {
        float speed = unit.speed();

        // ★ 大招期间减速 (PU132 OppressionComp.updateLaserSpeed)
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
