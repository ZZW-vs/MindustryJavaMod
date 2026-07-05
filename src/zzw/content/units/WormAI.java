package zzw.content.units;

import mindustry.ai.types.*;

import static mindustry.Vars.*;

/**
 * 虫子单位专用 AI (借鉴 PU132 WormAI)
 *
 * 解决问题: v154.3 默认 FlyingAI 在波次模式 + 默认队伍下, 无目标时会自动
 * 朝最近 spawn 点移动 (FlyingAI L29-31), 导致待机时单位自己向前跑
 *
 * 这里重写 updateMovement(), 无目标时强制 vel 清零实现待机静止
 * ★ 用户要求: 待机时速度全去掉, 不管是不是波次模式都静止
 *
 * ★ isIdle 标志: 让头部 Entity 在 super.update() 之后再次清零 vel
 *   (v154.3 物理系统在 AI 之后运行, 会给 vel 加值, 单靠 AI 清零不够)
 */
public class WormAI extends FlyingAI {

    /** 是否处于待机状态 (无目标), 供头部 Entity 在 super.update() 后再次清零 vel */
    public boolean isIdle = false;

    @Override
    public void updateMovement() {
        unloadPayloads();

        if (target != null && unit.hasWeapons()) {
            // 有目标: 攻击/环绕 (circleTargetRadius 用默认值 80f, UnitType.java L97)
            isIdle = false;
            if (unit.type.circleTarget) {
                circleAttack(80f);
            } else {
                moveTo(target, unit.range() * 0.8f);
                unit.lookAt(target);
            }
        } else {
            // ★ 待机静止: 无目标时速度清零 (不管是不是波次模式)
            isIdle = true;
            unit.vel.setZero();
        }
    }
}
