package zzw.content.mechanics.torque.modules;

import arc.scene.ui.layout.Table;
import arc.struct.IntMap;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.type.Item;
import zzw.content.mechanics.torque.graph.CrucibleGraph;
import zzw.content.mechanics.torque.graph.CrucibleGraph.CrucibleFluid;
import zzw.content.mechanics.torque.graphs.GraphCrucible;
import zzw.content.mechanics.torque.meta.CrucibleRecipes;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleIngredient;
import zzw.content.mechanics.torque.meta.GraphType;
import zzw.content.mechanics.torque.ui.CrucibleDisplayElement;

/**
 * 坩埚模块 (PU_V8 unity.world.modules.GraphCrucibleModule 移植)
 * <p>
 * 每个坩埚方块的组件, 记录该方块贡献给网络的容量 ({@code liquidCap}) 与
 * 邻居连接位掩码 ({@code tilingIndex}), 以及网络拆分/合并时缓存的内容物 ({@code propsList})。
 * <p>
 * 内容物现在以 {@link CrucibleFluid} (固/液两态) 表示, 详见 {@link CrucibleGraph}。
 */
public class GraphCrucibleModule extends GraphModule<GraphCrucible, GraphCrucibleModule, CrucibleGraph>{
    /** 网络拆分/合并时缓存各端口的内容物 */
    public final IntMap<Seq<CrucibleFluid>> propsList = new IntMap<>(4);
    /** 该方块贡献给网络的容量 */
    public float liquidCap;
    /** 邻居连接位掩码 (8 方向), 决定贴图变体 */
    public int tilingIndex;

    public boolean addItem(Item item){
        CrucibleGraph net = networks.get(0);
        return net != null && net.addItem(item);
    }

    /** 获取网络内容物 (原料 → 固/液两态) */
    public OrderedMap<CrucibleIngredient, CrucibleFluid> getContained(){
        CrucibleGraph net = networks.get(0);
        if(net != null) return net.fluids;
        return new OrderedMap<>();
    }

    public float getVolumeContained(){
        CrucibleGraph net = networks.get(0);
        return net != null ? net.getVolumeContained() : 0f;
    }

    public boolean canContainMore(float amount){
        CrucibleGraph net = networks.get(0);
        return net != null && net.canContainMore(amount);
    }

    public float getTotalLiquidCapacity(){
        CrucibleGraph net = networks.get(0);
        return net != null ? net.totalCapacity() : 0f;
    }

    @Override
    void applySaveState(CrucibleGraph graph, int index){
        CrucibleFluid[] cache = (CrucibleFluid[])saveCache.get(index);
        graph.fluids.clear();
        if(cache == null) return;
        for(var f : cache) graph.fluids.put(f.getIngredient(), f);
    }

    @Override
    void updateExtension(){}

    @Override
    void updateProps(CrucibleGraph graph, int index){}

    @Override
    void proximityUpdateCustom(){}

    @Override
    void display(Table table){}

    @Override
    void initStats(){
        tilingIndex = 0;
        liquidCap = 0f;
        propsList.clear();
    }

    @Override
    void displayBars(Table table){
        CrucibleGraph net = networks.get(0);
        if(net == null) return;
        table.row();
        var cell = table.add(new CrucibleDisplayElement(net.fluids, 3)).grow();
        cell.update(element -> cell.height(element.getMinHeight()));
    }

    @Override
    CrucibleGraph newNetwork(){
        return new CrucibleGraph();
    }

    @Override
    void writeGlobal(Writes write){}

    @Override
    void readGlobal(Reads read, byte revision){}

    @Override
    void writeLocal(Writes write, CrucibleGraph graph){
        write.i(graph.fluids.size);
        for(var f : graph.fluids){
            write.s(f.key.id);
            write.f(f.value.solid);
            write.f(f.value.melted);
        }
    }

    @Override
    CrucibleFluid[] readLocal(Reads read, byte revision){
        int len = read.i();
        CrucibleFluid[] save = new CrucibleFluid[len];
        for(int i = 0; i < len; i++){
            int id = read.s();
            float solid = read.f();
            float melted = read.f();
            CrucibleFluid f = new CrucibleFluid(CrucibleRecipes.ingredients.get(id));
            f.solid = solid;
            f.melted = melted;
            save[i] = f;
        }
        return save;
    }

    @Override
    public GraphType type(){
        return GraphType.crucible;
    }
}
