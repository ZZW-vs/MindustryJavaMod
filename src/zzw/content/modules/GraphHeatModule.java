package zzw.content.modules;

import arc.math.Mathf;

/** 热模块 (PU132 unity.world.modules.GraphHeatModule 移植) */
public class GraphHeatModule extends GraphModule{
    private float temp = 293.15f; // 默认室温 (K)

    public float getTemp(){ return temp; }
    public void setTemp(float t){ temp = t; }

    @Override
    public void updateTile(){
        // 简化: 温度缓慢趋向室温
        temp = Mathf.lerpDelta(temp, 293.15f, 0.001f);
    }

    @Override
    public float efficiency(){ return 1f; }

    @Override
    public GraphHeatModule graph(zzw.content.graphs.Graph graph){
        super.graph(graph);
        return this;
    }
}
