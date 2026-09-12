package zzw.content.units.ai;

import arc.util.Time;
import mindustry.ai.Pathfinder;
import mindustry.ai.types.GroundAI;
import mindustry.entities.Predict;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Tile;

import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;

/**
 * 远距地面 AI (PU132 unity.ai.DistanceGroundAI 移植)。
 *
 * <p>Scar 系列腿部单位 (hovos/ryzer/zena/sundown/rex/excelsus) 的专用 AI,
 * 在原版 {@link GroundAI} 基础上增加「距离锁定」行为:</p>
 *
 * <p>核心机制 (逐步解释):</p>
 * <ol>
 *   <li>若敌方核心进入射程 1/1.1 倍 (+核心半格), 将目标切换为核心;</li>
 *   <li>若当前目标进入射程 1/1.7 倍, 触发目标锁定 (lockTarget=true) 并重置锁定计时;</li>
 *   <li>锁定期间持续 60tick 未刷新则解除; 锁定时单位<b>向后拉开距离</b>
 *       (朝目标反方向 moveAt), 保持远程炮击姿态;</li>
 *   <li>未锁定时向敌方核心寻路 (v7+ 无 attack 指令, 等价于默认攻击行为);
 *       波次模式下避开空投区;</li>
 *   <li>面向逻辑: 目标有效且有武器时用预测拦截角, 否则朝移动方向。</li>
 * </ol>
 *
 * <p>★ v158 适配: Predict 移至 mindustry.entities; v7+ 的 UnitCommand 改为
 * 类常量且无 attack/rally (被 UnitStance 体系取代), BlockFlag.rally 与
 * Pathfinder.fieldRally 同步移除, 相关分支用等价的默认攻击行为替代;
 * UnitType.rotateShooting 字段已移除, 用 hasWeapons() 等价判断。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class DistanceGroundAI extends GroundAI{
    /** 是否处于目标锁定状态 (锁定后向后拉开距离保持远程攻击)。 */
    protected boolean lockTarget;

    /** 锁定计时器, 满 60tick 自动解除锁定 (初始 60f 表示开局未锁定)。 */
    protected float lockTimer = 60f;

    @Override
    public void updateMovement(){
        // 第1步: 获取最近的敌方核心, 作为远程单位的最终目标
        Building core = unit.closestEnemyCore();

        float range = unit.range();
        Team team = unit.team;
        UnitType type = unit.type;

        // 第2步: 敌方核心进入射程 1/1.1 倍 (含核心方块半格宽度) 时, 直接锁定核心为目标
        if(core != null && unit.within(core, range / 1.1f + core.block.size * tilesize / 2f)){
            target = core;
        }

        // 第3步: 目标进入射程 1/1.7 倍时触发锁定, 并重置锁定计时器
        if(target != null && target.team() != team && unit.within(target, range / 1.7f)){
            lockTarget = true;
            lockTimer = 0f;
        }

        // 第4步: 锁定计时器管理 —— 满 60tick 未刷新则解除锁定
        if(lockTimer >= 60f) lockTarget = false;
        else lockTimer += Time.delta;

        if(lockTarget){
            // 第5步: 锁定期间向目标反方向移动 (拉开到远程炮击距离)
            if(target != null && target.team() != team && unit.within(target, range / 1.72f))
                unit.moveAt(vec.trns(unit.angleTo(target) + 180f, unit.speed()));
        }else{
            // 第6步: 未锁定时向敌方核心寻路 (贴脸射程一半内则停下输出)
            if(core == null || !unit.within(core, range * 0.5f)){
                boolean move = true;
                // 波次模式下, 玩家阵营单位避开敌方空投区附近 (否则被刷出的敌军围殴)
                if(state.rules.waves && team == state.rules.defaultTeam){
                    Tile spawner = getClosestSpawner();
                    if(spawner != null && unit.within(spawner, state.rules.dropZoneRadius + 120f)) move = false;
                }
                if(move) pathfind(Pathfinder.fieldCore);
            }
        }

        // 第7步: 朝向逻辑 —— 目标有效且有武器时, 用预测拦截角盯目标; 否则朝移动方向
        if(!Units.invalidateTarget(target, unit, range)){
            if(type.hasWeapons()) unit.lookAt(Predict.intercept(unit, target, type.weapons.first().bullet.speed));
        }else if(unit.moving()) unit.lookAt(unit.vel.angle());
    }
}
