package zzw.content.graphs;

import arc.scene.ui.layout.Table;
import mindustry.graphics.Pal;
import zzw.content.modules.GraphHeatModule;

/** 热图 (PU132 unity.world.graphs.GraphHeat 移植) */
public class GraphHeat extends Graph{
    public final float baseHeatCapacity, baseHeatConductivity, baseHeatRadiativity;

    public GraphHeat(float capacity, float conductivity, float radiativity){
        baseHeatCapacity = capacity;
        baseHeatConductivity = conductivity;
        baseHeatRadiativity = radiativity;
    }

    public GraphHeat(){
        this(10f, 0.5f, 0.01f);
    }

    @Override
    public void setStats(Table table){
        table.row().left();
        table.add("Heat system").color(Pal.accent).fillX().row();
        table.left();
        table.add("[lightgray]Heat Capacity:[] ").left();
        table.add(baseHeatCapacity + "K J/K").row();
        table.left();
        table.add("[lightgray]Heat Conductivity:[] ").left();
        table.add(baseHeatConductivity + "W/mK").row();
        table.left();
        table.add("[lightgray]Heat Radiativity:[] ").left();
        table.add(baseHeatRadiativity * 1000f + "W/K");
        setStatsExt(table);
    }

    @Override
    public void setStatsExt(Table table){}

    @Override
    void drawPlace(int x, int y, int size, int rotation, boolean valid){}

    @Override
    public GraphType type(){ return GraphType.heat; }

    @Override
    public GraphHeatModule module(){
        return new GraphHeatModule().graph(this);
    }

    @Override
    boolean canBeMulti(){ return false; }
}
