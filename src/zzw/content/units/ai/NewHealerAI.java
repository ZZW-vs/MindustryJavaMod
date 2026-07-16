package zzw.content.units.ai;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.types.FlyingAI;
import mindustry.entities.Sized;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.weapons.RepairBeamWeapon;

/**
 * 治疗机单位专用 AI (移植 PU_V8 NewHealerAI, 继承 v158 FlyingAI)
 *
 * 功能:
 * - findMainTarget(): 查找受伤友军单位/建筑 (用 Vars.indexer.getDamaged + team.data().units)
 * - updateMovement(): 移动到受伤友军附近并治疗
 * - circleTarget=true 时用 v158 原生 circle() 围绕目标盘旋
 *
 * ★ v158 适配:
 *   - 删除自定义 circle() 方法, 改用 AIController.circle(target, range)
 *   - Vars.indexer.getDamaged / unit.team.data().units / unit.type.canHeal 在 v158 中均存在
 *   - RepairBeamWeapon 自带 autoTarget, 武器索敌由其内部 findTarget 处理, AI 只负责移动定位
 */
public class NewHealerAI extends FlyingAI{
    /** 最多扫描多少个受损建筑 (避免大地图性能问题) */
    final static int depth = 32;

    /** 切换目标冷却计时器, >0 时不重新索敌 */
    float switchTime = 0f;
    /** 是否查找受损建筑 (canHeal 或 武器带 targetBuildings) */
    boolean findTile = false;

    @Override
    public void init(){
        super.init();
        // canHeal 由 UnitType.init() 根据 bullet.heals() 计算 (healPercent>0 即为 true)
        findTile = unit.type.canHeal;
        // 任意 RepairBeamWeapon 带 targetBuildings 也启用建筑查找
        for(WeaponMount mount : unit.mounts){
            findTile |= mount.weapon instanceof RepairBeamWeapon && ((RepairBeamWeapon)mount.weapon).targetBuildings;
        }
    }

    @Override
    public void updateUnit(){
        super.updateUnit();
        switchTime -= Time.delta;
    }

    @Override
    public void updateMovement(){
        if(target != null){
            // 治疗距离 = 单位射程 * 0.8 + 目标体积/4 (让大单位能贴近些)
            float range = unit.type.range * 0.8f;
            if(target instanceof Sized) range += ((Sized)target).hitSize() / 4f;
            if(!unit.type.circleTarget){
                // cherub/malakhim: 直接近身停下治疗
                moveTo(target, range, 40f);
                unit.lookAt(target);
            }else{
                // seraphim: 用 v158 原生 circle 围绕目标盘旋 (持续治疗)
                circle(target, range);
            }
        }
    }

    @Override
    public boolean invalid(Teamc target){
        // 目标无效条件: null / 不同队 / 已满血或无效
        boolean in = target == null || target.team() != unit.team || (target instanceof Healthc && (!((Healthc)target).isValid() || !((Healthc)target).damaged()));
        if(in){
            // 目标失效后立即允许重新索敌
            switchTime = 0f;
        }
        return in;
    }

    @Override
    public Teamc findMainTarget(float x, float y, float range, boolean air, boolean ground){
        float sd = unit.speed() / 3f;

        // 1. 查找受伤建筑 (仅 ground 且 findTile 时)
        Building build = null;
        float buildScore = -Float.MAX_VALUE;
        if(ground && findTile){
            Seq<Building> buildings = Vars.indexer.getDamaged(unit.team);
            int len = Math.min(buildings.size, depth);
            for(int i = 0; i < len; i++){
                float s = calculateScore(buildings.get(i), sd);
                if(s > buildScore){
                    buildScore = s;
                    build = buildings.get(i);
                }
            }
        }

        // 2. 查找受伤友军单位
        Seq<Unit> units = unit.team.data().units;
        Unit un = null;
        float score = -Float.MAX_VALUE;
        for(Unit u : units){
            if(!u.dead && u != unit && u.damaged() && u.checkTarget(air, ground)){
                float sc = calculateScore(u, sd);
                if(sc > score){
                    score = sc;
                    un = u;
                }
            }
        }

        // 找到新目标后保持 160 帧 (避免频繁切换)
        if(un != null || build != null){
            switchTime = 160f;
        }

        // 分数高的优先 (单位 vs 建筑)
        return (score > buildScore || build == null) ? un : build;
    }

    @Override
    public boolean retarget(){
        // switchTime > 0 时不重新索敌 (保持当前目标)
        return switchTime <= 0f && super.retarget();
    }

    /**
     * 计算目标优先级分数:
     * - 血量缺口越大分数越高 (优先治疗重伤)
     * - 距离越近分数越高 (优先就近治疗)
     */
    float calculateScore(Healthc target, float s){
        float h = Mathf.sqrt(Math.max(target.maxHealth() - target.health(), 0f)) * 500f;
        return ((-unit.dst2(target) / (s * s)) / 2000f) + h;
    }
}
