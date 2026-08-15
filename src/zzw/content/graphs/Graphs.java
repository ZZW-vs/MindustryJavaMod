package zzw.content.graphs;

import arc.struct.ObjectSet;
import mindustry.world.meta.Stats;
import zzw.content.modules.GraphModule;
import zzw.content.modules.GraphModules;

/** 图容器 (PU132 unity.world.graphs.Graphs 移植) */
public class Graphs{
    private final Graph[] graphBlocks = new Graph[GraphType.values().length];
    private final ObjectSet<GraphType> results = new ObjectSet<>(4);
    boolean useOriginalUpdate = true;

    public <T extends Graph> T getGraphConnectorBlock(GraphType type){
        if(graphBlocks[type.ordinal()] == null) throw new IllegalArgumentException();
        return (T)graphBlocks[type.ordinal()];
    }

    public boolean hasGraph(GraphType type){
        return results.contains(type);
    }

    public void setGraphConnectorTypes(Graph graph){
        int i = graph.type().ordinal();
        graphBlocks[i] = graph;
        results.add(graph.type());
    }

    public void injectGraphConnector(GraphModules gms){
        for(GraphType type : results){
            int i = type.ordinal();
            gms.setGraphConnector(graphBlocks[i].module());
        }
    }

    public void setStats(Stats stats){
        stats.add(mindustry.world.meta.Stat.abilities, table -> {
            for(GraphType type : results) graphBlocks[type.ordinal()].setStats(table);
        });
    }

    public void drawPlace(int x, int y, int size, int rotation, boolean valid){
        for(GraphType type : results) graphBlocks[type.ordinal()].drawPlace(x, y, size, rotation, valid);
    }

    public boolean useOriginalUpdate(){
        return useOriginalUpdate;
    }

    public void disableOgUpdate(){
        useOriginalUpdate = false;
    }
}
