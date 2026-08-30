package zzw.content.mechanics.torque.graph;

import zzw.content.mechanics.torque.modules.GraphFluxModule;

/**
 * 磁通量网络 (PU132 unity.world.graph.FluxGraph 移植)
 *
 * <p>磁通量合成规则 (PU132 原版):
 * <ol>
 *   <li>累加所有连接模块的 flux, 统计磁通生产者 (永磁体) 数量</li>
 *   <li>多个生产者时按对数权重衰减: weight = 1.5*n/(log10(n)+1) - 0.5
 *       (磁体靠近互相干扰, 磁通无法简单叠加)</li>
 *   <li>网络总磁通 flux = fluxTotal / weight</li>
 * </ol></p>
 */
public class FluxGraph extends BaseGraph<GraphFluxModule, FluxGraph>{
    /** 网络当前总磁通量 (Wb) */
    float flux, fluxTotal;

    @Override
    public FluxGraph create(){
        return new FluxGraph();
    }

    @Override
    void copyGraphStatsFrom(FluxGraph graph){}

    @Override
    void updateOnGraphChanged(){}

    @Override
    void updateGraph(){
        fluxTotal = 0f;
        int totalMags = 0;
        for(var module : connected){
            fluxTotal += module.flux();
            if(module.graph.fluxProducer) totalMags++;
        }
        // 多磁体对数衰减权重 (PU132 原版公式)
        float weight = 1f;
        if(totalMags > 1) weight = (float)(1.5 * totalMags / (Math.log10(totalMags) + 1) - 0.5);
        flux = fluxTotal / weight;
    }

    @Override
    void updateDirect(){}

    @Override
    void addMergeStats(GraphFluxModule module){}

    @Override
    void mergeStats(FluxGraph graph){}

    public float flux(){
        return flux;
    }
}