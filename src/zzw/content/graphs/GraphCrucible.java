package zzw.content.graphs;

import arc.scene.ui.layout.Table;
import mindustry.graphics.Pal;
import zzw.content.modules.GraphCrucibleModule;

/** 坩埚图 (PU132 unity.world.graphs.GraphCrucible 移植) */
public class GraphCrucible extends Graph{
    public final float baseLiquidCapacity, meltSpeed;
    public final boolean doesCrafting;

    public GraphCrucible(float capacity, float speed, boolean crafting){
        baseLiquidCapacity = capacity;
        meltSpeed = speed;
        doesCrafting = crafting;
    }

    public GraphCrucible(float capacity, boolean crafting){
        this(capacity, 0.8f, crafting);
    }

    public GraphCrucible(){
        this(6f, 0.8f, true);
    }

    @Override
    public void setStats(Table table){
        table.row().left();
        table.add("Crucible system").color(Pal.accent).fillX();
        table.row().left();
        table.add("[lightgray]Liquid Capacity:[] ").left();
        table.add(baseLiquidCapacity + " Units");
        table.row().left();
        table.add("[lightgray]Melt Speed:[] ").left();
        table.add(meltSpeed + "x");
        setStatsExt(table);
    }

    @Override
    public void setStatsExt(Table table){}

    @Override
    void drawPlace(int x, int y, int size, int rotation, boolean valid){}

    @Override
    public GraphType type(){ return GraphType.crucible; }

    @Override
    public GraphCrucibleModule module(){
        return new GraphCrucibleModule().graph(this);
    }

    @Override
    boolean canBeMulti(){ return true; }
}
