package zzw.content.units.ai;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.types.*;
import mindustry.gen.*;
import mindustry.world.meta.*;

/**
 * 虫子单位专用 AI (完全复刻 PU132 WormAI, 继承 FlyingAI)
 *
 * PU132 原版继承 FlyingAI, 自动索敌+攻击+移动
 * v158 FlyingAI 的 updateTargeting/updateWeapons 自动处理攻击
 * 我们只需重写 updateMovement() 控制移动逻辑
 *
 * ★ v158 适配: 去掉 command() == UnitCommand.attack 检查
 *   (v158 无此 API, FlyingAI 默认就是攻击模式)
 */
public class WormAI extends FlyingAI{
    public Vec2 pos = new Vec2();
    public float score = 0f;
    public float time = 0f;
    protected float rotateTime = 0f;

    @Override
    public void updateMovement(){
        // PU132: 记仇机制 - 无 target 但有记仇位置时移动过去
        if(target == null && time > 0f){
            moveTo(pos, 0f);
        }
        // PU132: 有目标时移动+攻击
        if(target != null && unit.hasWeapons()){
            if(!unit.type.circleTarget){
                // 非盘旋模式 (arcnelidia): 冲向目标
                moveTo(target, unit.range() * 0.8f);
                unit.lookAt(target);
            }else{
                // 盘旋模式 (toxobyte/catenapede/devourer/oppression): 围绕目标转圈
                attack(120f);
            }
        }
        // PU132: 无目标且无记仇时朝 spawner 移动 (wave 模式)
        if(target == null && time <= 0f && Vars.state.rules.waves && unit.team == Vars.state.rules.defaultTeam){
            moveTo(getClosestSpawner(), Vars.state.rules.dropZoneRadius + 120f);
        }
        rotateTime = Math.max(0f, rotateTime - Time.delta);
        if(time <= 0f) score = 0f;
        time = Math.max(0f, time - Time.delta);
    }

    /**
     * PU132: 盘旋攻击
     * - 保持当前速度向前
     * - 朝向与目标方向差 > 100° 且距离 > circleLength 时平滑转向
     * - 转完后 rotateTime = 40f 冷却
     * ★ v158 FlyingAI 无此方法, 是 WormAI 自定义的 (PU132 继承自 v132 FlyingAI 有此方法)
     */
    protected void attack(float circleLength){
        vec.trns(unit.rotation, unit.speed());
        float diff = Angles.angleDist(unit.rotation, unit.angleTo(target));
        if((diff > 100f && !unit.within(target, circleLength)) || rotateTime > 0f){
            vec.setAngle(Mathf.slerpDelta(vec.angle(), unit.angleTo(target), 0.2f));
            if(rotateTime <= 0f) rotateTime = 40f;
        }
        unit.moveAt(vec);
    }

    /**
     * PU132: 段身受击时通知头部 (score高的覆盖低的, 持续180帧)
     */
    public void setTarget(float x, float y, float score){
        if(score < this.score) return;
        pos.set(x, y);
        this.score = score;
        time = 3f * 60f;
    }
}
