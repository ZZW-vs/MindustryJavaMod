package zzw.content.mechanics.torque.graphs;

import arc.scene.ui.layout.Table;
import mindustry.graphics.Pal;
import zzw.content.mechanics.torque.meta.CrucibleRecipes;
import zzw.content.mechanics.torque.meta.GraphType;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;
import zzw.content.mechanics.torque.ui.CrucibleMeltStatElement;

import static arc.Core.*;

/**
 * 坩埚图 (PU_V8 unity.world.graphs.GraphCrucible 移植)
 * <p>
 * 定义坩埚网络的基础参数:
 * <ul>
 *   <li>{@code baseLiquidCapacity} - 单个方块的基础内容物容量</li>
 *   <li>{@code doesCrafting} - 是否参与熔化/合金计算 (纯容器为 false)</li>
 * </ul>
 */
public class GraphCrucible extends Graph{
    public final float baseLiquidCapacity;
    public final boolean doesCrafting;

    public GraphCrucible(float capacity, boolean crafting){
        baseLiquidCapacity = capacity;
        doesCrafting = crafting;
    }

    public GraphCrucible(){
        this(6f, true);
    }

    @Override
    public void setStats(Table table){
        table.row().left();
        table.add(bundle.get("stat.unity.crucible.system", "Crucible system")).color(Pal.accent).fillX().row();

        table.left();
        table.add("[lightgray]" + bundle.get("stat.unity.liquidcapacity", "Liquid Capacity") + ":[] ").left();
        table.add(baseLiquidCapacity + " Units").row();

        if(doesCrafting){
            table.left();
            table.add("[lightgray]" + bundle.get("stat.unity.meltpoints", "Melt Points") + ":[] ").left();
            table.row();
            table.table(t -> {
                for(var melt : CrucibleRecipes.items){
                    t.row();
                    t.add(new CrucibleMeltStatElement(melt.key));
                }
            }).left().row();
        }

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
