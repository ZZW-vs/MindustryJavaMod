package zzw.content.graphs;

import arc.scene.ui.layout.Table;
import mindustry.graphics.Pal;
import zzw.content.modules.GraphTorqueModule;

/** 扭矩图 (PU132 unity.world.graphs.GraphTorque 移植) */
public class GraphTorque extends Graph{
    public final float baseFriction, baseInertia;

    public GraphTorque(float friction, float inertia){
        baseFriction = friction;
        baseInertia = inertia;
    }

    public GraphTorque(){
        this(0.1f, 10f);
    }

    @Override
    public void setStats(Table table){
        table.row().left();
        table.add("Torque system").color(Pal.accent).fillX();
        table.row().left();
        table.add("[lightgray]Friction:[] ").left();
        table.add(baseFriction + "");
        table.row().left();
        table.add("[lightgray]Inertia:[] ").left();
        table.add(baseInertia + "t m^2");
        setStatsExt(table);
    }

    @Override
    public void setStatsExt(Table table){}

    @Override
    void drawPlace(int x, int y, int size, int rotation, boolean valid){}

    @Override
    public GraphType type(){ return GraphType.torque; }

    @Override
    public GraphTorqueModule module(){
        return new GraphTorqueModule<>().graph(this);
    }

    @Override
    boolean canBeMulti(){ return true; }
}
