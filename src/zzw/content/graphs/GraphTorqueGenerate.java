package zzw.content.graphs;

import arc.scene.ui.layout.Table;
import zzw.content.modules.GraphTorqueGenerateModule;

/** 扭矩产生图 (PU132 unity.world.graphs.GraphTorqueGenerate 移植) */
public class GraphTorqueGenerate extends GraphTorque{
    public final float maxSpeed, torqueCoeff, maxTorque, startTorque;

    public GraphTorqueGenerate(float friction, float inertia, float maxSpeed, float torqueCoeff, float maxTorque, float startTorque){
        super(friction, inertia);
        this.maxSpeed = maxSpeed;
        this.torqueCoeff = torqueCoeff;
        this.maxTorque = maxTorque;
        this.startTorque = startTorque;
    }

    public GraphTorqueGenerate(float friction, float inertia, float maxSpeed, float maxTorque){
        this(friction, inertia, maxSpeed, 1f, maxTorque, 5f);
    }

    public GraphTorqueGenerate(){
        super();
        this.maxSpeed = 10f;
        this.torqueCoeff = 1f;
        this.maxTorque = 5f;
        this.startTorque = 5f;
    }

    @Override
    public void setStatsExt(Table table){
        table.row().left();
        table.add("[lightgray]Max Speed:[] ").left();
        table.add(maxSpeed * 0.1f + "rps");
        table.row().left();
        table.add("[lightgray]Max Torque:[] ").left();
        table.add(maxTorque + "KNm");
    }

    @Override
    public GraphTorqueGenerateModule module(){
        return new GraphTorqueGenerateModule().graph(this);
    }

    @Override
    boolean canBeMulti(){ return false; }
}
