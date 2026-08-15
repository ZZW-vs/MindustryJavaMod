package zzw.content.graphs;

import arc.scene.ui.layout.Table;
import zzw.content.modules.GraphTorqueConsumeModule;

/** 扭矩消耗图 (PU132 unity.world.graphs.GraphTorqueConsume 移植) */
public class GraphTorqueConsume extends GraphTorque{
    public final float nominalSpeed, idleFriction, workingFriction;

    public GraphTorqueConsume(float inertia, float nominalS, float idleF, float workingF){
        super(idleF, inertia);
        nominalSpeed = nominalS;
        idleFriction = idleF;
        workingFriction = workingF;
    }

    public GraphTorqueConsume(){
        super();
        nominalSpeed = 10f;
        idleFriction = 0.01f;
        workingFriction = 0.1f;
    }

    @Override
    public void setStatsExt(Table table){
        table.row().left();
        table.add("[lightgray]Nominal Speed:[] ").left();
        table.add(nominalSpeed * 0.1f + "rps");
        table.row().left();
        table.add("[lightgray]Idle Friction:[] ").left();
        table.add(idleFriction * 1000f + "Nmv^-2");
        table.row().left();
        table.add("[lightgray]Working Friction:[] ").left();
        table.add(workingFriction * 1000f + "Nmv^-2");
    }

    @Override
    public GraphTorqueConsumeModule module(){
        return new GraphTorqueConsumeModule().graph(this);
    }

    @Override
    boolean canBeMulti(){ return false; }
}
