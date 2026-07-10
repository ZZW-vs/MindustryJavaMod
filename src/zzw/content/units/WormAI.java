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
 * - 无目标时: moveTo(getClosestSpawner()) 向敌方出生点移动
 *
 * v158 适配: 继承 CommandAI, 重写 updateUnit() 实现自动索敌+移动
 *
 * ★ v2.0 修复:
 * - attack() 加边界检测, 快到地图边缘时强制转向
 * - 无目标时向最近核心移动, 避免贴墙发呆
 * - moveTo() 加距离衰减, 避免到目标附近时抖动
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

    /** 地图边界退缩距离 (单位距离边界这么近时开始转向) */
    protected static final float BOUNDARY_MARGIN = 80f;

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
            // ★ 修复贴墙: 无目标时向最近敌方核心移动 (PU132 L35-37)
            // PU132: moveTo(getClosestSpawner(), dropZoneRadius + 120f)
            Teamc core = targetFlag(unit.x, unit.y, BlockFlag.core, true);
            if (core != null) {
                moveTo(core, 60f, 100f);
            } else {
                // 实在没有目标, 向地图中心移动 (避免贴墙发呆)
                float cx = world.unitWidth() / 2f;
                float cy = world.unitHeight() / 2f;
                vec.set(cx - unit.x, cy - unit.y);
                if (vec.len() > 10f) {
                    vec.setLength(unit.speed());
                    unit.moveAt(vec);
                }
            }
            faceTarget();
        }

        // ★ 修复贴墙: 边界检测, 距离地图边界太近时强制转向中心
        clampToMapBounds();

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
     * ★ 盘旋攻击 (移植 PU132 WormAI.attack L66-74, 加边界修正)
     *
     * 原版逻辑:
     * - 保持当前速度向前
     * - 当朝向与目标方向差 > 100° 且距离 > circleLength 时, 平滑转向目标
     * - 转完后 rotateTime = 40f (冷却)
     *
     * v2.0 修复:
     * - 加边界检测: 如果前进方向会撞墙/出界, 强制转向目标
     * - 避免单位贴墙移动
     */
    protected void attack(float circleLength) {
        float speed = unit.speed();

        // 大招期间减速
        if (unit instanceof SegmentWormEntity worm && worm.isUltActive()) {
            speed *= worm.ultSpeedMultiplier();
        }

        vec.trns(unit.rotation, speed);

        float diff = Angles.angleDist(unit.rotation, unit.angleTo(target));

        // ★ 边界修正: 检查前进方向是否会出界
        boolean nearBoundary = isNearBoundary();
        boolean headingToWall = isHeadingToWall();

        if ((diff > 100f && !unit.within(target, circleLength)) || rotateTime > 0f || nearBoundary || headingToWall) {
            // 需要转向: 平滑转到目标方向
            // ★ 边界附近时加大转向力度 (0.4 vs 0.2), 快速脱离贴墙
            float slerp = (nearBoundary || headingToWall) ? 0.4f : 0.2f;
            vec.setAngle(Mathf.slerpDelta(vec.angle(), unit.angleTo(target), slerp));
            if (rotateTime <= 0f && !nearBoundary && !headingToWall) rotateTime = 40f;
        }

        unit.moveAt(vec);
    }

    /**
     * ★ 检查单位是否接近地图边界
     */
    protected boolean isNearBoundary() {
        float w = world.unitWidth();
        float h = world.unitHeight();
        float m = BOUNDARY_MARGIN + unit.hitSize;
        return unit.x < m || unit.x > w - m || unit.y < m || unit.y > h - m;
    }

    /**
     * ★ 检查前进方向是否有实体墙 (solid block)
     * 用射线检测前方一段距离内是否有实心方块
     */
    protected boolean isHeadingToWall() {
        float checkDist = 60f + unit.hitSize;
        float nextX = unit.x + Angles.trnsx(unit.rotation, checkDist);
        float nextY = unit.y + Angles.trnsy(unit.rotation, checkDist);

        // 出界
        if (nextX < 0 || nextX > world.unitWidth() || nextY < 0 || nextY > world.unitHeight()) {
            return true;
        }

        // 检查前方是否有实心方块 (只对非飞行单位)
        if (!unit.isFlying()) {
            int tx = world.toTile(nextX);
            int ty = world.toTile(nextY);
            var tile = world.tile(tx, ty);
            if (tile != null && tile.solid()) {
                return true;
            }
        }

        return false;
    }

    /**
     * ★ 边界钳制: 距离地图边界太近时给一个向中心的力
     * 比纯转向更温和, 不会打断正常攻击动作
     */
    protected void clampToMapBounds() {
        float w = world.unitWidth();
        float h = world.unitHeight();
        float m = BOUNDARY_MARGIN;
        float force = 0f;
        float pushX = 0f, pushY = 0f;

        if (unit.x < m) { pushX = m - unit.x; force = Math.max(force, pushX / m); }
        if (unit.x > w - m) { pushX = (w - m) - unit.x; force = Math.max(force, -pushX / m); }
        if (unit.y < m) { pushY = m - unit.y; force = Math.max(force, pushY / m); }
        if (unit.y > h - m) { pushY = (h - m) - unit.y; force = Math.max(force, -pushY / m); }

        if (force > 0.1f) {
            // 向中心的推力, 力度随距离边界减小而增大
            vec.set(pushX, pushY).nor().scl(unit.speed() * Mathf.clamp(force, 0.2f, 1f));
            unit.moveAt(vec);
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
