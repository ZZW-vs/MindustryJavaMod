package zzw.content.units.ai;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.types.FlyingAI;
import mindustry.entities.Sized;
import mindustry.entities.Units;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.weapons.RepairBeamWeapon;

/**
 * 治疗机单位专用 AI (移植 PU_V8 NewHealerAI + 增加敌方规避)
 *
 * 功能:
 * - findMainTarget(): 查找最近的受伤友军单位/建筑 (纯距离优先, 匹配用户需求 "找最近的没满血的单位")
 * - updateMovement(): 移动到受伤友军附近并治疗
 * - ★ 新增: updateMovement 中检测附近敌方单位, 叠加远离向量, 避免靠近敌方
 * - circleTarget=true 时用 v158 原生 circle(target, range) 围绕目标盘旋
 *
 * ★ v158 适配:
 *   - Vars.indexer.getDamaged / unit.team.data().units / unit.type.canHeal 在 v158 中均存在
 *   - RepairBeamWeapon 自带 autoTarget, 武器索敌由其内部 findTarget 处理, AI 只负责移动定位
 *   - RepairBeamWeapon.findTarget 只查找友军 damaged 单位, 不会攻击敌方
 */
public class NewHealerAI extends FlyingAI{
    /** 最多扫描多少个受损建筑 (避免大地图性能问题) */
    final static int depth = 32;

    /** 敌方规避距离: 当敌方单位进入此距离时, 治疗机会被推开 */
    final static float enemyAvoidRange = 220f;
    /** 敌方规避推力强度 (相对单位速度的倍数) */
    final static float enemyAvoidStrength = 1.5f;

    /** 切换目标冷却计时器, >0 时不重新索敌 */
    float switchTime = 0f;
    /** 是否查找受损建筑 (canHeal 或 武器带 targetBuildings) */
    boolean findTile = false;
    /** 临时向量用于敌方规避 */
    final Vec2 avoidVec = new Vec2();

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
        unloadPayloads();

        // ★ 敌方规避: 检测附近敌方单位, 叠加远离向量
        // 即使有治疗目标, 也要优先远离敌方, 避免被攻击
        avoidVec.setZero();
        Units.nearbyEnemies(unit.team, unit.x, unit.y, enemyAvoidRange, enemy -> {
            if (enemy.dead() || (!enemy.type.flying && !enemy.type.targetable)) return;
            float dst = unit.dst(enemy);
            if (dst > 0f && dst < enemyAvoidRange) {
                // 距离越近推力越大 (倒数衰减)
                float push = (1f - dst / enemyAvoidRange);
                avoidVec.add((unit.x - enemy.x) / dst * push, (unit.y - enemy.y) / dst * push);
            }
        });

        if(target != null){
            // 治疗距离 = 单位射程 * 0.8 + 目标体积/4 (让大单位能贴近些)
            float range = unit.type.range * 0.8f;
            if(target instanceof Sized) range += ((Sized)target).hitSize() / 4f;

            // ★ 计算治疗移动向量 (用 AIController.vec 静态字段)
            vec.set(target).sub(unit);

            if(!unit.type.circleTarget){
                // cherub/malakhim: 直接近身停下治疗
                unit.lookAt(target);
                float len = vec.len();
                if(len > range + 40f){
                    vec.setLength(unit.speed());
                }else{
                    vec.setZero();
                }
            }else{
                // seraphim: 围绕目标盘旋 (持续治疗)
                if(vec.len() < range){
                    float side = Mathf.randomSeed(unit.id, 0, 1) == 0 ? -1 : 1;
                    vec.rotate((range - vec.len()) / range * 180f * side);
                }
                vec.setLength(unit.speed());
            }

            // ★ 合并规避向量到治疗向量 (避免 moveTo + moveAt 互相覆盖)
            if(avoidVec.len() > 0.001f){
                avoidVec.setLength(unit.speed() * enemyAvoidStrength);
                vec.add(avoidVec);
                // 限制合并后最大速度
                float maxSpeed = unit.speed() * (1f + enemyAvoidStrength);
                if(vec.len() > maxSpeed) vec.setLength(maxSpeed);
            }

            unit.movePref(vec);
        }else{
            // 无目标: 仅远离敌方
            if(avoidVec.len() > 0.001f){
                avoidVec.setLength(unit.speed() * enemyAvoidStrength);
                unit.movePref(avoidVec);
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
     * ★ 用户需求: "找最近的没满血的单位" → 主要按距离优先, 距离越近分数越高
     * 同时保留少量 health gap 加成, 避免反复在两个等距离目标间切换
     */
    float calculateScore(Healthc target, float s){
        // 距离项: 距离越近分数越高 (主要项)
        float distScore = -unit.dst2(target) / (s * s + 1f) / 200f;
        // health 加成: 受伤越严重分数略高 (次要项, 避免完全忽略重伤单位)
        float healthBonus = Mathf.sqrt(Math.max(target.maxHealth() - target.health(), 0f)) * 5f;
        return distScore + healthBonus;
    }
}
