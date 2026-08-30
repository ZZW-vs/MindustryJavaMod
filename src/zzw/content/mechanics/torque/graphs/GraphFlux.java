package zzw.content.mechanics.torque.graphs;

import arc.scene.ui.layout.Table;
import zzw.content.mechanics.torque.meta.GraphType;
import zzw.content.mechanics.torque.modules.GraphFluxModule;

import static arc.Core.*;

/**
 * 磁通量图 (PU132 unity.world.graphs.GraphFlux 移植)
 * <p>定义磁力网络的基础磁通量 (Wb) 与生产者标志。
 * 永磁体 (定子) 是 fluxProducer=true, 转子是 false (只取用不产出)。</p>
 */
public class GraphFlux extends Graph{
    /** 基础磁通量 (Wb) */
    public final float baseFlux;
    /** 是否为磁通生产者 (永磁体) */
    public final boolean fluxProducer;

    public GraphFlux(float flux, boolean producer){
        baseFlux = flux;
        fluxProducer = producer;
    }

    public GraphFlux(float flux){
        this(flux, true);
    }

    public GraphFlux(boolean producer){
        this(0f, producer);
    }

    public GraphFlux(){
        this(0f, true);
    }

    @Override
    public void setStats(Table table){
        table.row().left();
        table.add("[lightgray]" + bundle.get("stat.unity.flux", "Flux") + ":[] ").left();
        table.add(baseFlux + "Wb");
        setStatsExt(table);
    }

    @Override
    public void setStatsExt(Table table){}

    @Override
    void drawPlace(int x, int y, int size, int rotation, boolean valid){}

    @Override
    public GraphType type(){
        return GraphType.flux;
    }

    @Override
    public GraphFluxModule module(){
        return new GraphFluxModule().graph(this);
    }

    @Override
    boolean canBeMulti(){
        return false;
    }
}