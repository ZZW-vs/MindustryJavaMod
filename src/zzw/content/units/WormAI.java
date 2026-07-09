package zzw.content.units;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.ai.types.*;

import static mindustry.Vars.*;

/**
 * 虫子单位专用 AI (融合 PU132 WormAI + v158 FlyingAI)
 *
 * PU132 保留:
 * - attack() 方法: 非omni单位沿朝向移动, 角度差>100°时slerp转向 (比v158 circleAttack更适合虫子)
 * - setTarget(): 段身受击时通知头部
 *
 * v158 适配:
 * - updateMovement(): 无目标时强制静止 (v158 FlyingAI 会朝spawn跑)
 * - 索敌交给父类 FlyingAI.findMainTarget (支持 v158 targetFlags 系统)
 */
public class WormAI extends FlyingAI {

    /** 是否处于待机状态 (无目标), 供头部 Entity 在 super.update() 后再次清零 vel */
    public boolean isIdle = false;

    /** PU132: 段身受击通知的目标位置 (score高的覆盖低的, 持续180帧) */
    public Vec2 pos = new Vec2();
    public float score = 0f;
    public float time = 0f;
    protected float rotateTime = 0f;

    @Override
    public void updateMovement() {
        unloadPayloads();

        if (target != null && unit.hasWeapons()) {
            isIdle = false;
            if (unit.type.circleTarget) {
                // ★ PU132 attack() 而非 v158 circleAttack()
                // v158 circleAttack() 对 omniMovement=false 单位效果差 (朝目标方向移动但单位只能朝 facing 方向走)
                // PU132 attack() 沿单位朝向移动, 角度差>100°时 slerp 转向
                attack(120f);
            } else {
                moveTo(target, unit.range() * 0.8f);
                if (unit.type.faceTarget) {
                    unit.lookAt(target);
                }
            }
        } else {
            // ★ 待机静止: 无目标时速度清零 (不管是不是波次模式)
            isIdle = true;
            unit.vel.setZero();
        }

        rotateTime = Math.max(0f, rotateTime - Time.delta);
        if (time <= 0f) score = 0f;
        time = Math.max(0f, time - Time.delta);
    }

    /**
     * PU132 attack(): 适合非omni单位的环绕攻击
     * - 沿单位朝向移动 (vec.trns(unit.rotation, unit.speed()))
     * - 角度差>100°且不在环绕范围内时, slerp转向目标
     * - 触发40帧的rotateTime反向旋转
     * vs v158 circleAttack(): 朝目标方向移动, 对omniMovement=false单位效果差
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
