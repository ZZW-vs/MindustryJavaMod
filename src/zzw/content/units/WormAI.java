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
 */
public class WormAI extends FlyingAI {

    @Override
    public void updateMovement() {
        unloadPayloads();

        if (target != null && unit.hasWeapons()) {
            // 有目标: 攻击/环绕 (circleTargetRadius 用默认值 80f, UnitType.java L97)
            if (unit.type.circleTarget) {
                circleAttack(80f);
            } else {
                moveTo(target, unit.range() * 0.8f);
                unit.lookAt(target);
            }
        } else if (target == null && state.rules.waves && unit.team == state.rules.defaultTeam) {
            // 波次模式 + 默认队伍: 朝最近 spawn 移动 (保持原版行为)
            moveTo(getClosestSpawner(), state.rules.dropZoneRadius + 130f);
        } else {
            // ★ 待机静止: 无目标且非波次默认队伍时, 速度清零
            unit.vel.setZero();
        }
    }
}
