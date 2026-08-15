package zzw.content.modules;

import zzw.content.graphs.Graph;
import zzw.content.graphs.GraphTorqueConsume;

/** 扭矩消耗模块 (PU132 unity.world.modules.GraphTorqueConsumeModule 移植) */
public class GraphTorqueConsumeModule extends GraphTorqueModule<GraphTorqueConsume>{
    @Override
    public float efficiency(){
        // 简化: 如果有扭矩输入就正常工作
        return 1f;
    }

    @Override
    public GraphTorqueConsumeModule graph(Graph graph){
        super.graph(graph);
        return this;
    }
}
