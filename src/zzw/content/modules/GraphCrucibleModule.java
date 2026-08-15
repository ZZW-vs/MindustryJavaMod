package zzw.content.modules;

import arc.graphics.Color;

/** 坩埚模块 (PU132 unity.world.modules.GraphCrucibleModule 移植) */
public class GraphCrucibleModule extends GraphModule{
    private float volume = 0f;
    private Color color = Color.clear;

    public float getVolumeContained(){ return volume; }
    public void setVolume(float v){ volume = v; }
    public Color getColor(){ return color; }
    public void setColor(Color c){ color = c; }

    public static class CrucibleNetwork{
        public Color color = Color.clear;
    }

    private CrucibleNetwork network = new CrucibleNetwork();

    public CrucibleNetwork getNetwork(){ return network; }

    @Override
    public GraphCrucibleModule graph(zzw.content.graphs.Graph graph){
        super.graph(graph);
        return this;
    }
}
