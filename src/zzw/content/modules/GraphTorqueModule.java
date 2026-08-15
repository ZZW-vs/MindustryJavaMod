package zzw.content.modules;

import arc.util.Time;
import zzw.content.graphs.Graph;
import zzw.content.graphs.GraphTorque;

/** 扭矩模块基类 (PU132 unity.world.modules.GraphTorqueModule 移植) */
public class GraphTorqueModule<T extends GraphTorque> extends GraphModule{
    protected float rotation = 0f;
    protected float speed = 0f;

    public float getRotation(){ return rotation; }
    public float getSpeed(){ return speed; }

    public void setSpeed(float s){ speed = s; }

    @Override
    public void updateTile(){
        rotation += speed * Time.delta;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GraphTorqueModule<T> graph(Graph graph){
        super.graph(graph);
        return this;
    }
}
