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
 * ★ v2.1 修复:
 * - 卡住检测: 连续多帧速度极低但目标在远处 → 强制转向
 * - 多射线墙体检测: 正前方+侧前方3条射线, 更可靠地发现墙体
 * - 动态边界回避: 速度越快, 提前转向距离越大
 * - attack() 贴墙时直接转向目标, 不继续前冲
 * - 非飞行单位避开实心方块
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

    // ===== 卡住检测 =====
    /** 上几帧的位置 (用于计算实际移动距离) */
    protected float lastX = Float.NaN, lastY = Float.NaN;
    /** 连续低速帧计数 */
    protected float stuckTimer = 0f;
    /** 判定卡住的速度阈值 */
    protected static final float STUCK_SPEED_THRESHOLD = 0.3f;
    /** 判定卡住需要的持续帧数 (约2秒) */
    protected static final float STUCK_TIME = 120f;

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

        // ★ 卡住检测: 检查是否被墙/边界卡住
        updateStuckDetection();

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
     * ★ 盘旋攻击 (移植 PU132 WormAI.attack L66-74, 加边界/墙体/卡住修正)
     *
     * 原版逻辑:
     * - 保持当前速度向前
     * - 当朝向与目标方向差 > 100° 且距离 > circleLength 时, 平滑转向目标
     * - 转完后 rotateTime = 40f (冷却)
     *
     * v2.1 修复:
     * - 多射线墙体检测: 前方3条射线 (正前方±30°), 更可靠发现墙体
     * - 卡住时强制转向: 连续2秒低速 + 目标在远处 → 直接转向目标
     * - 边界附近加大转向力度 (0.4 vs 0.2), 快速脱离贴墙
     */
    protected void attack(float circleLength) {
        float speed = unit.speed();

        // 大招期间减速
        if (unit instanceof SegmentWormEntity worm && worm.isUltActive()) {
            speed *= worm.ultSpeedMultiplier();
        }

        vec.trns(unit.rotation, speed);

        float diff = Angles.angleDist(unit.rotation, unit.angleTo(target));

        // ★ 检测条件
        boolean nearBoundary = isNearBoundary();
        boolean headingToWall = isHeadingToWall();
        boolean isStuck = stuckTimer >= STUCK_TIME;

        // ★ 转向条件 (PU132原版 + 边界/墙体/卡住)
        boolean shouldTurn = (diff > 100f && !unit.within(target, circleLength)) || rotateTime > 0f;

        // ★ 边界/墙体/卡住时必须转向, 即使角度差不大
        if (nearBoundary || headingToWall || isStuck) {
            shouldTurn = true;
        }

        if (shouldTurn) {
            // 需要转向: 平滑转到目标方向
            // ★ 转向力度根据紧迫程度调整
            float slerp;
            if (isStuck) {
                // ★ 卡住时强力转向, 快速脱困
                slerp = 0.6f;
            } else if (nearBoundary || headingToWall) {
                // 边界/墙体附近加大转向力度
                slerp = 0.4f;
            } else {
                // PU132 原版力度
                slerp = 0.2f;
            }
            vec.setAngle(Mathf.slerpDelta(vec.angle(), unit.angleTo(target), slerp));
            if (rotateTime <= 0f && !nearBoundary && !headingToWall && !isStuck) rotateTime = 40f;
        }

        unit.moveAt(vec);
    }

    /**
     * ★ 卡住检测: 连续多帧速度极低但目标在远处 → 判定卡住
     *
     * 原因: 盘旋模式的单位在贴墙时会反复撞墙然后被弹开,
     * 每帧速度很低但 AI 认为还在移动, 不触发转向,
     * 导致卡在墙角一直抖动
     */
    protected void updateStuckDetection() {
        float actualSpeed = 0f;
        if (!Float.isNaN(lastX) && !Float.isNaN(lastY)) {
            float dx = unit.x - lastX;
            float dy = unit.y - lastY;
            actualSpeed = (float) Math.sqrt(dx * dx + dy * dy) / Time.delta;
        }
        lastX = unit.x;
        lastY = unit.y;

        // 有目标且目标在远处, 但自身速度极低 → 可能在卡住
        boolean hasFarTarget = (target != null && !unit.within(target, unit.range() * 0.5f))
            || (targetPos != null && !unit.within(targetPos, 60f));

        if (hasFarTarget && actualSpeed < STUCK_SPEED_THRESHOLD) {
            stuckTimer += Time.delta;
        } else {
            // 正常移动时快速衰减卡住计时
            stuckTimer = Math.max(0f, stuckTimer - Time.delta * 2f);
        }
    }

    /**
     * ★ 检查单位是否接近地图边界
     * 动态边界: 速度越快, 提前量越大
     */
    protected boolean isNearBoundary() {
        float w = world.unitWidth();
        float h = world.unitHeight();
        // ★ 动态边界: 速度越快, 提前量越大 (最少80, 最多200)
        float margin = BOUNDARY_MARGIN + unit.hitSize + Math.min(unit.speed() * 8f, 120f);
        return unit.x < margin || unit.x > w - margin || unit.y < margin || unit.y > h - margin;
    }

    /**
     * ★ 多射线墙体检测: 前方3条射线 (正前方、+30°、-30°)
     * 比单点检测更可靠, 能发现斜向墙体
     */
    protected boolean isHeadingToWall() {
        float baseDist = 50f + unit.hitSize + Math.min(unit.speed() * 6f, 100f);

        // 出界检测 (正前方)
        float nextX = unit.x + Angles.trnsx(unit.rotation, baseDist);
        float nextY = unit.y + Angles.trnsy(unit.rotation, baseDist);
        if (nextX < 0 || nextX > world.unitWidth() || nextY < 0 || nextY > world.unitHeight()) {
            return true;
        }

        // 非飞行单位: 检查实心方块 (3条射线)
        if (!unit.isFlying()) {
            // 正前方
            if (isSolidAt(unit.x, unit.y, unit.rotation, baseDist)) return true;
            // 左前方 30°
            if (isSolidAt(unit.x, unit.y, unit.rotation - 30f, baseDist * 0.8f)) return true;
            // 右前方 30°
            if (isSolidAt(unit.x, unit.y, unit.rotation + 30f, baseDist * 0.8f)) return true;
        } else {
            // 飞行单位: 只检测地图边界 (更远距离)
            for (float angle : new float[]{unit.rotation, unit.rotation - 25f, unit.rotation + 25f}) {
                float fx = unit.x + Angles.trnsx(angle, baseDist * 1.5f);
                float fy = unit.y + Angles.trnsy(angle, baseDist * 1.5f);
                if (fx < 0 || fx > world.unitWidth() || fy < 0 || fy > world.unitHeight()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查从 (x,y) 沿 angle 方向 dist 距离处是否有实心方块
     */
    protected boolean isSolidAt(float x, float y, float angle, float dist) {
        float tx = x + Angles.trnsx(angle, dist);
        float ty = y + Angles.trnsy(angle, dist);
        if (tx < 0 || tx > world.unitWidth() || ty < 0 || ty > world.unitHeight()) return true;
        int tileX = world.toTile(tx);
        int tileY = world.toTile(ty);
        var tile = world.tile(tileX, tileY);
        return tile != null && tile.solid();
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
