package zzw.content.modules;

import arc.scene.ui.layout.Table;
import zzw.content.graphs.GraphBuildBase;
import zzw.content.graphs.GraphType;

/** 图模块容器 (PU132 unity.world.modules.GraphModules 移植) */
public class GraphModules{
    private final GraphModule[] modules = new GraphModule[GraphType.values().length];
    private final GraphBuildBase build;

    public GraphModules(GraphBuildBase build){
        this.build = build;
    }

    public void setGraphConnector(GraphModule module){
        modules[module.graph.type().ordinal()] = module;
        module.build(build);
    }

    @SuppressWarnings("unchecked")
    public <T extends GraphModule> T getGraphConnector(GraphType type){
        return (T)modules[type.ordinal()];
    }

    public void created(){
        for(GraphModule m : modules) if(m != null) m.created();
    }

    public void updateTile(){
        for(GraphModule m : modules) if(m != null) m.updateTile();
    }

    public void onProximityUpdate(){
        for(GraphModule m : modules) if(m != null) m.onProximityUpdate();
    }

    public void updateGraphRemovals(){
        for(GraphModule m : modules) if(m != null) m.updateGraphRemovals();
    }

    public void prevTileRotation(int rotation){
        for(GraphModule m : modules) if(m != null) m.prevTileRotation(rotation);
    }

    public float efficiency(){
        float eff = 1f;
        for(GraphModule m : modules) if(m != null){
            eff *= m.efficiency();
        }
        return eff;
    }

    public void display(Table table){
        for(GraphModule m : modules) if(m != null) m.display(table);
    }

    public void displayBars(Table table){
        for(GraphModule m : modules) if(m != null) m.displayBars(table);
    }

    public void drawSelect(){
        for(GraphModule m : modules) if(m != null) m.drawSelect();
    }

    public void write(arc.util.io.Writes write){
        for(GraphModule m : modules) if(m != null) m.write(write);
    }

    public void read(arc.util.io.Reads read, byte revision){
        for(GraphModule m : modules) if(m != null) m.read(read, revision);
    }
}
