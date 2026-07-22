package zzw.content.mechanics.torque.modules;

import arc.scene.ui.layout.*;
import arc.util.io.*;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.*;
import zzw.content.mechanics.torque.graphs.*;
import zzw.content.mechanics.torque.meta.*;

public class GraphModules{
    public final GraphBuildBase build;

    private GraphTorqueModule<? extends GraphTorque> torque;

    private boolean hasTorque;
    int prevTileRotation = -1;

    public GraphModules(GraphBuildBase build){
        this.build = build;
    }

    public GraphModule getGraphConnector(GraphType type){
        if(type == GraphType.heat) return null;
        if(type == GraphType.torque) return torque;
        if(type == GraphType.crucible) return null;
        if(type == GraphType.flux) return null;

        return null;
    }

    public <T extends GraphModule> void setGraphConnector(T graph){
        graph.parent = this;

        if(graph instanceof GraphTorqueModule torque){
            this.torque = torque;
            hasTorque = torque != null;
        }
    }

    public GraphTorqueModule<? extends GraphTorque> torque(){
        return torque;
    }

    public GraphData getConnectSidePos(int index){
        return GraphData.getConnectSidePos(index, build.asBuilding().block.size, build.asBuilding().rotation);
    }

    public void created(){
        if(hasTorque) torque.onCreate(build);
        prevTileRotation = -1;
    }

    public float efficiency(){
        float e = 1f;

        if(hasTorque) e *= torque.efficiency();

        return Math.max(0f, e);
    }

    //onDestroyed. 중복 호출?
    public void updateGraphRemovals(){
        if(hasTorque) torque.onRemoved();
    }

    public void updateTile(){
        if(!build.asBuilding().block.rotate) build.asBuilding().rotation = 0;
        if(prevTileRotation != build.asBuilding().rotation){
            if(hasTorque) torque.onRotationChanged(prevTileRotation, build.asBuilding().rotation);

            build.onRotationChanged();
        }
        if(hasTorque) torque.onUpdate();
    }

    public void onProximityUpdate(){
        if(hasTorque) torque.proximityUpdateCustom();
    }

    public void display(Table table){
        if(hasTorque) torque.display(table);
    }

    public void displayBars(Table table){
        if(hasTorque) torque.displayBars(table);
    }

    public void write(Writes write){
        if(hasTorque) torque.write(write);
    }

    public void read(Reads read, byte revision){
        if(hasTorque) torque.read(read, revision);
    }

    public void prevTileRotation(int r){
        prevTileRotation = r;
    }

    public void drawSelect(){
        if(hasTorque) torque.drawSelect();
    }
}
