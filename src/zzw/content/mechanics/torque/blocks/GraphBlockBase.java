package zzw.content.mechanics.torque.blocks;

import arc.scene.ui.layout.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.world.meta.*;
import zzw.content.mechanics.torque.graphs.*;
import zzw.content.mechanics.torque.meta.*;
import zzw.content.mechanics.torque.modules.*;

public interface GraphBlockBase{
    Graphs graphs();

    default void disableOgUpdate(){
        graphs().disableOgUpdate();
    }

    default void addGraph(Graph graph){
        graphs().setGraphConnectorTypes(graph);
    }

    default void setStatsExt(Stats stats){}

    interface GraphBuildBase extends Buildingc{
        GraphModules gms();

        // v155.4 适配: Buildingc 接口不暴露 Building 类方法 (block/rotation/tile/enabled/edelta 等),
        // 此方法把当前 GraphBuildBase 安全转换为 Building, 以访问这些方法。
        default Building asBuilding(){
            return (Building) this;
        }

        default GraphModule getGraphConnector(GraphType type){
            return gms().getGraphConnector(type);
        }

        default GraphTorqueModule<? extends GraphTorque> torque(){
            return gms().torque();
        }

        default void onGraphUpdate(){}

        default void onNeighboursChanged(){}

        default void onDelete(){}

        default void onDeletePost(){}

        default void updatePre(){}

        default void onRotationChanged(){}

        default void updatePost(){}

        default void proxUpdate(){}

        default void displayExt(Table table){}

        default void displayBarsExt(Table table){}

        default void writeExt(Writes write){}

        default void readExt(Reads read, byte revision){}
    }
}
