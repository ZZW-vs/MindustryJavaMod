package zzw.content.mechanics.torque.graphs;

import arc.scene.ui.layout.Table;
import mindustry.graphics.Pal;
import zzw.content.mechanics.torque.meta.GraphType;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;

import static arc.Core.*;

/**
 * 坩埚图 (PU132 unity.world.graphs.GraphCrucible 移植)
 * <p>
 * 定义坩埚网络的基础参数:
 * <ul>
 *   <li>{@code baseLiquidCapacity} - 单个方块的基础液体容量 (PU132 原文拼写为 baseLiquidCapcity, 此处修正)</li>
 *   <li>{@code meltSpeed} - 熔化速度倍率</li>
 *   <li>{@code doesCrafting} - 是否参与熔化/合金计算 (纯容器为 false)</li>
 * </ul>
 */
public class GraphCrucible extends Graph{
    public final float baseLiquidCapacity, meltSpeed;
    public final boolean doesCrafting;

    public GraphCrucible(float capacity, float speed, boolean crafting){
        baseLiquidCapacity = capacity;
        meltSpeed = speed;
        doesCrafting = crafting;
    }

    public GraphCrucible(float capacity, boolean crafting){
        this(capacity, 0.8f, crafting);
    }

    public GraphCrucible(){
        this(6f, 0.8f, true);
    }

    @Override
    public void setStats(Table table){
        table.row().left();
        // ★ 汉化: 原版硬编码 "Crucible system"
        table.add("坩埚系统").color(Pal.accent).fillX().row();

        table.left();
        table.add("[lightgray]" + bundle.get("stat.unity.liquidcapacity", "Liquid Capacity") + ":[] ").left();
        table.add(baseLiquidCapacity + " Units").row();

        table.left();
        table.add("[lightgray]" + bundle.get("stat.unity.meltspeed", "Melt Speed") + ":[] ").left();

        setStatsExt(table);
    }

    @Override
    public void setStatsExt(Table table){}

    @Override
    void drawPlace(int x, int y, int size, int rotation, boolean valid){}

    @Override
    public GraphType type(){
        return GraphType.crucible;
    }

    @Override
    public GraphCrucibleModule module(){
        return new GraphCrucibleModule().graph(this);
    }

    @Override
    boolean canBeMulti(){
        return true;
    }
}
