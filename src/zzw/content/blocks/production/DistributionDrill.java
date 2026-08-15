package zzw.content.blocks.production;

import arc.struct.Seq;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.blocks.production.Drill;

/**
 * 分配钻头 (PU132 unity.world.blocks.production.DistributionDrill 移植)
 * <p>
 * 行为: 钻头之间互相传递物品, 当普通输出路径都被其他钻头占据时,
 * 才允许向同类型钻头输出, 避免钻头链路堵塞。
 * <p>
 * 机制说明:
 * <ol>
 *   <li>每 tick 检查邻近方块中是否有非 DistributionDrillBuild 的方块可接收产物</li>
 *   <li>若有, 则标记 canDistribute=false, 禁止向同类钻头输出</li>
 *   <li>handleItem 时记录来源 (若来源也是同类钻头), 防止物品在钻头间无限循环</li>
 * </ol>
 */
public class DistributionDrill extends Drill{
    /** 额外的 dump 计时器索引, 用于控制物品输出频率 */
    protected int timerDumpAlt = timers++;

    public DistributionDrill(String name){
        super(name);
    }

    public class DistributionDrillBuild extends DrillBuild{
        /** 已接收过物品的同类钻头列表, 这些钻头不会再被当作输出目标 */
        protected Seq<Building> invalidBuildings = new Seq<>();
        /** 当前是否允许向同类钻头输出物品 */
        protected boolean canDistribute = true;

        @Override
        public boolean acceptItem(Building source, Item item){
            return items.get(item) < getMaximumAccepted(item);
        }

        @Override
        public boolean canDump(Building to, Item item){
            // 向同类钻头输出时, 检查目标是否在黑名单中且当前允许分配
            if(to instanceof DistributionDrillBuild b){
                return !b.invalidBuildings.contains(to) && canDistribute;
            }
            return super.canDump(to, item);
        }

        @Override
        public void handleItem(Building source, Item item){
            // 若来源是同类钻头, 加入黑名单避免循环
            if(source instanceof DistributionDrillBuild) invalidBuildings.add(source);
            super.handleItem(source, item);
        }

        /**
         * 检查邻近方块中是否存在非同类的可接收产物方块。
         * <p>若存在, 则禁止向同类钻头输出 (canDistribute=false)。</p>
         */
        protected void canDistribute(){
            for(int i = 0; i < proximity.size; i++){
                Building other = proximity.get((i + cdump) % proximity.size);
                if(!(other instanceof DistributionDrillBuild) && other.acceptItem(this, dominantItem)){
                    canDistribute = false;
                    return;
                }
            }
        }

        @Override
        public void updateTile(){
            if(dominantItem != null) canDistribute();
            if(timer.get(timerDumpAlt, dumpTime)) dump();
            super.updateTile();
            // 每帧重置黑名单和分配标志
            invalidBuildings.clear();
            canDistribute = true;
        }
    }
}
