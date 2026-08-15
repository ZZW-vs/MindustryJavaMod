package zzw.content.modules;

import zzw.content.graphs.Graph;
import zzw.content.graphs.GraphTorqueGenerate;

/** 扭矩产生模块 (PU132 unity.world.modules.GraphTorqueGenerateModule 移植) */
public class GraphTorqueGenerateModule extends GraphTorqueModule<GraphTorqueGenerate>{
    @Override
    public void updateTile(){
        // 简化: 产生模块按额定速度旋转
        speed = ((GraphTorqueGenerate)graph).maxSpeed * 0.1f;
        super.updateTile();
    }

    @Override
    public GraphTorqueGenerateModule graph(Graph graph){
        super.graph(graph);
        return this;
    }
}
