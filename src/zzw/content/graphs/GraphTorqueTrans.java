package zzw.content.graphs;

import arc.scene.ui.layout.Table;
import zzw.content.modules.GraphTorqueTransModule;

/** 扭矩传输图 (PU132 unity.world.graphs.GraphTorqueTrans 移植) */
public class GraphTorqueTrans extends GraphTorque{
    public final float[] ratio = new float[]{1f, 2f};

    public GraphTorqueTrans(float friction, float inertia){
        super(friction, inertia);
        multi();
    }

    public GraphTorqueTrans(){
        super();
        multi();
    }

    public GraphTorqueTrans setRatio(float ratio1, float ratio2){
        ratio[0] = ratio1;
        ratio[1] = ratio2;
        return this;
    }

    @Override
    public void setStatsExt(Table table){
        table.row().left();
        table.add("[lightgray]Transmission Ratio:[] ").left();
        table.add(ratio[0] + ":" + ratio[1]);
    }

    @Override
    public GraphTorqueTransModule module(){
        return new GraphTorqueTransModule().graph(this);
    }

    @Override
    boolean canBeMulti(){ return true; }
}
