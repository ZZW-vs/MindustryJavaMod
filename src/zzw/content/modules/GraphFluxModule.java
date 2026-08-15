package zzw.content.modules;

/** 磁通模块 (PU132 unity.world.modules.GraphFluxModule 移植) */
public class GraphFluxModule extends GraphModule{
    private float flux = 0f;

    public float getFlux(){ return flux; }
    public void setFlux(float f){ flux = f; }

    @Override
    public GraphFluxModule graph(zzw.content.graphs.Graph graph){
        super.graph(graph);
        return this;
    }
}
