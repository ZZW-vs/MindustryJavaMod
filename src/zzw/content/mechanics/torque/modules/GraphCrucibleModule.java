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
        for(var f : cache){
            // 跳过无效条目 (readLocal 中原料已不存在的槽位为 null)
            if(f == null || f.getIngredient() == null) continue;
            graph.fluids.put(f.getIngredient(), f);
        }
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
        // ★ 不要在此清零 liquidCap: onCreate 里 initAllNets() 会经由
        //   CrucibleGraph.updateOnGraphChanged() 先把 liquidCap 设为 baseLiquidCapacity,
        //   而 initStats() 随后执行。若在此清零, 孤立坩埚的 liquidCap 会一直为 0
        //   (直到发生一次图变化), 影响熔融液绘制比例与网络拆分分摊。
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

            // 先读完数据保证读流位置正确, 再判断原料是否仍存在
            CrucibleIngredient ing = CrucibleRecipes.ingredients.get(id);
            // ★ 旧存档中已不存在的原料 (配方表改动过) → 跳过该条目。
            // 否则会构造出 ingredient 为 null 的 CrucibleFluid, 之后被 put 进网络的
            // OrderedMap 时会因 key.hashCode() 抛空指针。
            if(ing == null) continue;

            CrucibleFluid f = new CrucibleFluid(ing);
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
