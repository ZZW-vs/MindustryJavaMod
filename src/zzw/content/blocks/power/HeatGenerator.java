package zzw.content.blocks.power;

import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.modules.GraphHeatModule;

/**
 * 热量发生器基类 (PU132 unity.world.blocks.power.HeatGenerator 移植)
 *
 * <p>向热量图注入热量的方块基类: 每帧向所在网络增加
 * {@code (maxTemp - 当前温度) * mulCoeff * mul} 的热量 (带 mul 上限版本)。</p>
 *
 * <p>适配说明: 继承 zzw.content.mechanics.torque.blocks.GraphBlock (完整版图方块)。</p>
 */
public class HeatGenerator extends GraphBlock{
    /** 目标最高温度 (K) */
    protected float maxTemp = 9999f;
    /** 热量系数 (每帧升温比例) */
    protected float mulCoeff = 0.5f;

    public HeatGenerator(String name){
        super(name);
    }

    public class HeatGeneratorBuild extends GraphBuild{
        /**
         * 向热量图注入热量.
         * <p>mul 为产能系数 (如燃烧效率/热液量), 网络温度越接近 maxTemp 增量越小 (渐近收敛)。</p>
         */
        protected void generateHeat(float mul){
            GraphHeatModule hgraph = heat();
            hgraph.heat += Math.max(0f, maxTemp - hgraph.getTemp()) * mulCoeff * mul;
        }

        /** 带注入上限版本: 每帧最多注入 limit 热量 (太阳能集热器用) */
        protected void generateHeat(float limit, float mul){
            GraphHeatModule hgraph = heat();
            hgraph.heat += Math.min(limit, Math.max(0f, maxTemp - hgraph.getTemp()) * mulCoeff * mul);
        }
    }
}
