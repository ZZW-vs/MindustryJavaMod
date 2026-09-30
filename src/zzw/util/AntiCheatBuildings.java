package zzw.util;

import arc.Events;
import arc.struct.Seq;
import arc.util.Interval;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;

/**
 * 建筑防作弊守卫 —— PU132 {@code unity.mod.AntiCheat} 中 building 部分的精简移植。
 *
 * <p>PU132 的 endgame 炮台在 {@code add()} 里调用
 * {@code Unity.antiCheat.addBuilding(this)} 把自己登记进防作弊表, 并在
 * {@code remove()} 里 (仅在真正死亡时) 注销。登记后系统每 15 帧检查一次:</p>
 *
 * <ul>
 *   <li><b>被从地图上抹掉</b> (地块上的建筑不再是它) → 把它原样写回地块;</li>
 *   <li><b>被从实体组里剔除</b> → 重新 {@code add()} 回世界;</li>
 *   <li><b>同队正在拆除它</b> (地块上是本队的 ConstructBuild 脚手架) → 尊重
 *       玩家的拆除操作, 主动注销, 不再保护;</li>
 *   <li><b>血量归零</b> (真的被打死了) → 注销, 不再复活。</li>
 * </ul>
 *
 * <p>本项目只移植 building 部分 (单位部分由 {@code zzw.content.units.anticheat}
 * 系列负责); 节奏与判定顺序与 PU132 一致, 只是把 PU132 的
 * {@code Triggers.listen(Trigger.update, ...)} 换成项目内通用的
 * {@link EventType.Trigger#update}, 并在剧情重置 ({@code ResetEvent}) 时清空登记表,
 * 避免跨存档残留旧引用。</p>
 */
public class AntiCheatBuildings{
    /** 已登记 (受保护) 的建筑。 */
    private static final Seq<Building> tracked = new Seq<>();
    /** 15 帧校验节拍。 */
    private static final Interval timer = new Interval();
    /** 回调是否已挂载 (只挂一次)。 */
    private static boolean hooked = false;

    /** 登记一个需要保护的建筑 (幂等)。 */
    public static void add(Building build){
        hook();
        if(build != null && !tracked.contains(build)){
            tracked.add(build);
        }
    }

    /** 注销建筑 (真正被摧毁 / 被同队拆除时调用)。 */
    public static void remove(Building build){
        tracked.remove(build);
    }

    /** 挂载 Trigger.update 与 ResetEvent (只执行一次)。 */
    private static void hook(){
        if(hooked) return;
        hooked = true;
        Events.run(EventType.Trigger.update, AntiCheatBuildings::update);
        Events.on(EventType.ResetEvent.class, e -> tracked.clear());
    }

    /** 每 15 帧校验一次: 被抹掉的写回、被剔除的加回, 真死/被拆的注销。 */
    private static void update(){
        if(tracked.isEmpty() || !timer.get(15f)) return;

        for(int i = tracked.size - 1; i >= 0; i--){
            Building b = tracked.get(i);

            // 已真死 (血量归零) → 注销, 不再复活
            if(b == null || b.health <= 0f){
                tracked.remove(i);
                continue;
            }

            // 同队正在拆除它 → 尊重玩家操作, 放弃保护
            if(b.tile != null && b.tile.build instanceof ConstructBuild cb && cb.team == b.team){
                tracked.remove(i);
                continue;
            }

            // 被从地图上抹掉 → 原样写回
            if(b.tile != null && b.tile.build != b){
                b.tile.setBlock(b.block, b.team, b.rotation, () -> b);
            }

            // 被从实体组里剔除 → 重新加入世界
            if(!b.isAdded()){
                b.add();
            }
        }
    }
}