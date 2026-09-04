package zzw.content.mechanics.torque.graphs;

import arc.scene.ui.layout.Table;
import mindustry.graphics.Pal;
import zzw.content.mechanics.torque.meta.GraphType;
import zzw.content.mechanics.torque.modules.GraphHeatModule;

import static arc.Core.*;

/**
 * 热量图 (PU132 unity.world.graphs.GraphHeat 移植)
 * <p>
 * 定义热量网络的基础参数:
 * <ul>
 *   <li>{@code baseHeatCapacity} - 热容量 (K·J/K), 决定升温速度</li>
 *   <li>{@code baseHeatConductivity} - 热传导率 (W/mK), 决定邻居间热量传递速度</li>
 *   <li>{@code baseHeatRadiativity} - 热辐射率 (W/K), 决定向环境散热的速度</li>
 * </ul>
 */
public class GraphHeat extends Graph{
    public final float baseHeatCapacity, baseHeatConductivity, baseHeatRadiativity;

    public GraphHeat(float capacity, float conductivity, float radiativity){
        baseHeatCapacity = capacity;
        baseHeatConductivity = conductivity;
        baseHeatRadiativity = radiativity;
    }

    public GraphHeat(){
        this(10f, 0.5f, 0.01f);
    }

    @Override
    public void setStats(Table table){
        table.row().left();
        // ★ 汉化: 原版硬编码 "Heat system"
        table.add("热量系统").color(Pal.accent).fillX().row();
        table.left();
        table.add("[lightgray]" + bundle.get("stat.unity.heatcapacity", "Heat Capacity") + ":[] ").left();
        table.add(baseHeatCapacity + "K J/K").row();
        table.left();
        table.add("[lightgray]" + bundle.get("stat.unity.heatconductivity", "Heat Conductivity") + ":[] ").left();
        table.add(baseHeatConductivity + "W/mK").row();
        table.left();
        table.add("[lightgray]" + bundle.get("stat.unity.heatradiativity", "Heat Radiativity") + ":[] ").left();
        table.add(baseHeatRadiativity * 1000f + "W/K");
        setStatsExt(table);
    }

    @Override
    public void setStatsExt(Table table){}

    @Override
    void drawPlace(int x, int y, int size, int rotation, boolean valid){}

    @Override
    public GraphType type(){
        return GraphType.heat;
    }

    @Override
    public GraphHeatModule module(){
        return new GraphHeatModule().graph(this);
    }

    @Override
    boolean canBeMulti(){
        return false;
    }
}
