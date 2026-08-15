package zzw.content.modules;

import zzw.content.graphs.Graph;
import zzw.content.graphs.GraphTorqueTrans;

/** 扭矩传输模块 (PU132 unity.world.modules.GraphTorqueTransModule 移植) */
public class GraphTorqueTransModule extends GraphTorqueModule<GraphTorqueTrans>{
    @Override
    public GraphTorqueTransModule graph(Graph graph){
        super.graph(graph);
        return this;
    }
}
