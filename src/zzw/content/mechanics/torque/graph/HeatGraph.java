package zzw.content.mechanics.torque.graph;

import zzw.content.mechanics.torque.modules.GraphHeatModule;

/**
 * 热量网络 (PU132 unity.world.graph.HeatGraph 移植)
 * <p>
 * 管理连接的热量模块之间的热量传递。
 * <p>
 * 每帧将各模块的 {@code heatBuffer} (来自邻居传导和环境辐射) 加到 {@code heat} 上,
 * 并累计 {@code lastHeatFlow} 用于统计。
 */
public class HeatGraph extends BaseGraph<GraphHeatModule, HeatGraph>{
    float lastHeatFlow;

    @Override
    public HeatGraph create(){
        return new HeatGraph();
    }

    @Override
    void copyGraphStatsFrom(HeatGraph graph){}

    @Override
    void updateOnGraphChanged(){}

    @Override
    void updateGraph(){
        lastHeatFlow = 0f;
        connected.each(module -> {
            module.heat += module.heatBuffer;
            lastHeatFlow += module.heatBuffer;
        });
    }

    @Override
    void updateDirect(){}

    @Override
    void addMergeStats(GraphHeatModule module){}

    @Override
    void mergeStats(HeatGraph graph){
        lastHeatFlow += graph.lastHeatFlow;
    }
}
