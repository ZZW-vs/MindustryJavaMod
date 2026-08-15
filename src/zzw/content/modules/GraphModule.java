package zzw.content.modules;

import zzw.content.graphs.Graph;
import zzw.content.graphs.GraphBuildBase;

/** 图模块基类 (PU132 unity.world.modules.GraphModule 移植) */
public abstract class GraphModule{
    public Graph graph;
    public GraphBuildBase build;

    public GraphModule graph(Graph graph){
        this.graph = graph;
        return this;
    }

    public GraphModule build(GraphBuildBase build){
        this.build = build;
        return this;
    }

    public void created(){}
    public void updateTile(){}
    public void onProximityUpdate(){}
    public void updateGraphRemovals(){}
    public void prevTileRotation(int rotation){}
    public void display(arc.scene.ui.layout.Table table){}
    public void displayBars(arc.scene.ui.layout.Table table){}
    public void drawSelect(){}
    public void write(arc.util.io.Writes write){}
    public void read(arc.util.io.Reads read, byte revision){}

    public float efficiency(){ return 1f; }
}
