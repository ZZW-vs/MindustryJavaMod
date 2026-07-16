package zzw.content.units.ai;

import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.types.*;

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
                // 非盘旋模式 (arcnelidia): 冲向目标近距离
                // ★ 修复: 之前用 unit.range()*0.8f 停止距离太大 (如210*0.8=168f), 单位在远处就停了
                //   改用 30f 固定近距离, 让单位冲到目标面前
                moveTo(target, 30f);
                unit.lookAt(target);
            }else{
                // 盘旋模式 (toxobyte/catenapede/devourer/oppression): 围绕目标转圈
                // ★ 修复: 之前用自定义 attack() + moveAt, 不如 v158 原生 circleAttack + movePref 可靠
                //   circleAttack 用 movePref, 对 omniMovement=false 单位有正确处理
                // ★ v158 无 circleTargetRadius 字段, 固定用 120f (PU132 默认盘旋半径)
                circleAttack(120f);
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
     * PU132: 段身受击时通知头部 (score高的覆盖低的, 持续180帧)
     */
    public void setTarget(float x, float y, float score){
        if(score < this.score) return;
        pos.set(x, y);
        this.score = score;
        time = 3f * 60f;
    }
}
