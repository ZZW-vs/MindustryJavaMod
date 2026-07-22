package zzw.content.mechanics.torque.modules;

import arc.math.*;
import zzw.content.mechanics.torque.graphs.*;

public class GraphTorqueConsumeModule extends GraphTorqueModule<GraphTorqueConsume>{
    @Override
    void updateExtension(){
        if(!parent.build.asBuilding().enabled) friction = graph.idleFriction;
        else friction = graph.workingFriction;
    }

    @Override
    float efficiency(){
        float ratio = networks.get(0).lastVelocity / graph.nominalSpeed;
        if(ratio > 1f){
            ratio = Mathf.log2(ratio);
            ratio = 1f + ratio * graph.oversupplyFalloff;
        }
        return ratio;
    }

    @Override
    public GraphTorqueConsumeModule graph(GraphTorqueConsume graph){
        this.graph = graph;
        if(graph.isMultiConnector) multi = true;
        return this;
    }
}
