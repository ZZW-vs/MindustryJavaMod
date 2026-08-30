package zzw.content.mechanics.torque.modules;

import arc.graphics.Color;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import zzw.content.mechanics.torque.graph.FluxGraph;
import zzw.content.mechanics.torque.graphs.GraphFlux;
import zzw.content.mechanics.torque.meta.GraphType;

/**
 * 磁通量模块 (PU132 unity.world.modules.GraphFluxModule 移植)
 * <p>每个磁体 (定子/电磁铁) 持有一个磁通量值, 由电力满意度调谐
 * (mulFlux: flux = 电力满意度 * 基础磁通); 网络合成见 FluxGraph。</p>
 */
public class GraphFluxModule extends GraphModule<GraphFlux, GraphFluxModule, FluxGraph>{
    /** 当前磁通量 (Wb) */
    float flux;

    @Override
    void applySaveState(FluxGraph graph, int index){}

    @Override
    void updateExtension(){}

    @Override
    void updateProps(FluxGraph graph, int index){}

    @Override
    void proximityUpdateCustom(){}

    @Override
    void display(Table table){
        FluxGraph net = networks.get(0);
        if(net == null) return;
        String ps = " Wb";

        table.row();
        table.table(sub -> {
            sub.clearChildren();
            sub.left();
            sub.label(() -> Strings.fixed(net.flux(), 2) + ps).color(Color.lightGray);
        }).left();
    }

    @Override
    void initStats(){
        flux = graph.baseFlux;
    }

    @Override
    void displayBars(Table table){}

    @Override
    FluxGraph newNetwork(){
        return new FluxGraph();
    }

    @Override
    void writeGlobal(Writes write){}

    @Override
    void readGlobal(Reads read, byte revision){}

    @Override
    void writeLocal(Writes write, FluxGraph graph){}

    @Override
    Object[] readLocal(Reads read, byte revision){
        return null;
    }

    @Override
    public GraphType type(){
        return GraphType.flux;
    }

    /** 当前磁通量 (Wb) */
    public float flux(){
        return flux;
    }

    /** 按倍率调谐磁通 (Magnet 用电力满意度调用) */
    public void mulFlux(float mul){
        flux = mul * graph.baseFlux;
    }
}