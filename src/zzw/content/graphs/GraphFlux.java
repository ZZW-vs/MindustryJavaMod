package zzw.content.graphs;

import arc.scene.ui.layout.Table;
import mindustry.graphics.Pal;
import zzw.content.modules.GraphFluxModule;

/** 磁通图 (PU132 unity.world.graphs.GraphFlux 移植) */
public class GraphFlux extends Graph{
    public final float baseFluxCapacity, baseFluxConductivity;

    public GraphFlux(float capacity, float conductivity){
        baseFluxCapacity = capacity;
        baseFluxConductivity = conductivity;
    }

    public GraphFlux(){
        this(10f, 0.5f);
    }

    @Override
    public void setStats(Table table){
        table.row().left();
        table.add("Flux system").color(Pal.accent).fillX();
        table.row().left();
        table.add("[lightgray]Flux Capacity:[] ").left();
        table.add(baseFluxCapacity + "Wb");
        table.row().left();
        table.add("[lightgray]Flux Conductivity:[] ").left();
        table.add(baseFluxConductivity + "H^-1");
        setStatsExt(table);
    }

    @Override
    public void setStatsExt(Table table){}

    @Override
    void drawPlace(int x, int y, int size, int rotation, boolean valid){}

    @Override
    public GraphType type(){ return GraphType.flux; }

    @Override
    public GraphFluxModule module(){
        return new GraphFluxModule().graph(this);
    }

    @Override
    boolean canBeMulti(){ return false; }
}
