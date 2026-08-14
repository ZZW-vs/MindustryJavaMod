package zzw.content.blocks.distribution;

import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.production.*;

/**
 * PU132 DistributionDrill 完整移植
 * 源码: unity/world/blocks/production/DistributionDrill.java
 *
 * 钻机之间可互相传递产物，用TTL机制避免回流振荡。
 * 优化: 原版每tick清空黑名单导致跨tick回流，改为TTL过期机制(2秒内不回传)。
 */
public class DistributionDrill extends Drill{
    protected int timerDumpAlt = timers++;

    /** 来源记录的TTL（tick），防止跨tick回流振荡 */
    public static final float SOURCE_TTL = 120f;

    public DistributionDrill(String name){
        super(name);
    }

    public class DistributionDrillBuild extends DrillBuild{
        /** 记录最近给我发过物品的钻机及过期时间，TTL内不回传 */
        protected ObjectMap<Building, Float> recentSources = new ObjectMap<>();

        @Override
        public boolean acceptItem(Building source, Item item){
            return items.get(item) < getMaximumAccepted(item);
        }

        @Override
        public boolean canDump(Building to, Item item){
            if(to instanceof DistributionDrillBuild b){
                // TTL内不回传给刚给我发过物品的钻机，避免物品来回弹跳
                Float expire = recentSources.get(to);
                return expire == null || expire < Time.time;
            }
            return super.canDump(to, item);
        }

        @Override
        public void handleItem(Building source, Item item){
            if(source instanceof DistributionDrillBuild){
                // 记录来源钻机，SOURCE_TTL tick后过期
                recentSources.put(source, Time.time + SOURCE_TTL);
            }
            super.handleItem(source, item);
        }

        @Override
        public void updateTile(){
            // 自定义dump：转储所有物品种类（原版只dump dominantItem，会导致外来非主矿物卡住）
            if(timer.get(timerDumpAlt, dumpTime)) dump();
            super.updateTile();
            // 清理过期的来源记录，避免Map无限增长
            if(recentSources.size > 0){
                var iter = recentSources.iterator();
                while(iter.hasNext()){
                    if(iter.next().value < Time.time) iter.remove();
                }
            }
        }
    }
}
