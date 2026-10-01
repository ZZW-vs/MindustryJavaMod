package zzw.content.mechanics.torque.graph;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.type.Item;
import mindustry.world.Tile;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;
import zzw.content.mechanics.torque.meta.CrucibleRecipes;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleIngredient;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleItem;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.RecipeIngredient;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;
import zzw.content.mechanics.torque.modules.GraphHeatModule;

/**
 * 坩埚网络 (PU_V8 unity.world.graph.CrucibleGraph 移植)
 * <p>
 * 新模型用 {@link CrucibleIngredient} 作为键、{@link CrucibleFluid} 作为值,
 * 每种原料在此网络中同时存在"固态 solid"和"熔融 melted"两种量, 依据热量网络的
 * 温度与相变能量相互转化:
 * <ul>
 *   <li>固→液 (熔化): 温度 ≥ 熔点, 消耗热量</li>
 *   <li>液→固 (凝固): 温度 &lt; 熔点, 释放热量</li>
 *   <li>液→汽 (汽化): 温度 ≥ 沸点, 消耗热量, 触发液体自身的汽化特效</li>
 *   <li>合金: 满足配方成分与最低温度时, 消耗输入生成输出</li>
 * </ul>
 * <p>
 * 与老实现一致, 内容物集中存储在网络侧 (而非 PU_V8 的节点自存),
 * 通过容量按方块比例分摊给绘制。
 */
public class CrucibleGraph extends BaseGraph<GraphCrucibleModule, CrucibleGraph>{
    /** 网络内所有内容物的加权平均颜色 (用于绘制) */
    public final Color color = Color.clear.cpy();
    /** 网络内容物: 原料 → 固/液两态量 */
    public final OrderedMap<CrucibleIngredient, CrucibleFluid> fluids = new OrderedMap<>();

    float totalCapacity;
    boolean crafts = true;

    // 更新用临时序列 (避免每帧分配)
    private final Seq<CrucibleIngredient> smeltOrder = new Seq<>();
    private final Seq<CrucibleIngredient> boilOrder = new Seq<>();
    private final Seq<CrucibleIngredient> coolOrder = new Seq<>();

    /** 拼装坩埚方块名 (连接白名单的主角) */
    private static final String MODULAR = "modular-crucible";
    /** 坩埚泵方块名 (拼装坩埚允许相连) */
    private static final String CRUCIBLE_PUMP = "crucible-pump";
    /** 保温坩埚方块名 (拼装坩埚允许相连) */
    private static final String HOLDING = "holding-crucible";

    @Override
    public CrucibleGraph create(){
        return new CrucibleGraph();
    }

    /**
     * 连接判定 (用户要求): 拼装坩埚 (modular-crucible) 自成一张网络, 只允许与
     * 同类拼装坩埚、坩埚泵 (crucible-pump) 与保温坩埚 (holding-crucible) 相连;
     * 其余坩埚系统方块 (3x3 坩埚 / 坩埚通道 / 铸造模具 / 液体装载器 / 坩埚源)
     * 都不能与它自动连通。
     *
     * <p>连边是双向的, 因此只要任一侧是拼装坩埚, 就要求另一侧也在白名单内;
     * 两侧都不是拼装坩埚时保持原行为 (默认可连)。</p>
     */
    @Override
    boolean canConnect(GraphCrucibleModule b1, GraphCrucibleModule b2){
        String n1 = b1.parent.build.asBuilding().block.name;
        String n2 = b2.parent.build.asBuilding().block.name;
        boolean m1 = MODULAR.equals(n1), m2 = MODULAR.equals(n2);

        if(!m1 && !m2) return true;
        return isModularPartner(n1) && isModularPartner(n2);
    }

    /** 拼装坩埚允许连接的方块白名单 */
    private static boolean isModularPartner(String name){
        return MODULAR.equals(name) || CRUCIBLE_PUMP.equals(name) || HOLDING.equals(name);
    }

    /** 获取 (不存在则创建) 指定原料的流体数据 */
    public CrucibleFluid getFluid(CrucibleIngredient i){
        CrucibleFluid f = fluids.get(i);
        if(f == null){
            f = new CrucibleFluid(i);
            fluids.put(i, f);
        }
        return f;
    }

    /** 网络内容物总体积 */
    public float getVolumeContained(){
        float t = 0f;
        for(var f : fluids) t += f.value.total();
        return t;
    }

    /** 剩余空间 */
    public float getRemainingSpace(){
        return Math.max(0f, totalCapacity - getVolumeContained());
    }

    public boolean canContainMore(float amount){
        return getVolumeContained() + amount <= totalCapacity;
    }

    /** 添加一个物品 (固态), 返回是否成功 */
    public boolean addItem(Item item){
        CrucibleItem ci = CrucibleRecipes.items.get(item);
        if(ci == null) return false;
        return addIngredient(ci, 1f) > 0f;
    }

    /** 添加固态原料, 返回实际加入量 */
    public float addIngredient(CrucibleIngredient i, float amount){
        if(i == null || amount <= 0f) return 0f;
        CrucibleFluid f = getFluid(i);
        float space = totalCapacity - getVolumeContained();
        float add = Math.min(amount, Math.max(0f, space));
        if(add <= 0f) return 0f;
        f.solid += add;
        return add;
    }

    /** 添加液态原料, 返回实际加入量 */
    public float addLiquidIngredient(CrucibleIngredient i, float amount){
        if(i == null || amount <= 0f) return 0f;
        CrucibleFluid f = getFluid(i);
        float space = totalCapacity - getVolumeContained();
        float add = Math.min(amount, Math.max(0f, space));
        if(add <= 0f) return 0f;
        f.melted += add;
        return add;
    }

    public float totalCapacity(){
        return totalCapacity;
    }

    /** 按熔融量加权计算网络颜色, 透明度随填充度提升 */
    public void updateColor(){
        float r = 0f, g = 0f, b = 0f, t = 0f;
        for(var fluid : fluids){
            float tt = fluid.value.melted;
            t += tt;
            r += fluid.key.color.r * tt;
            g += fluid.key.color.g * tt;
            b += fluid.key.color.b * tt;
        }
        if(t <= 0f){
            color.set(Color.clear);
            return;
        }
        float inv = 1f / t;
        color.set(r * inv, g * inv, b * inv, Mathf.clamp(10f * t / totalCapacity));
    }

    @Override
    void copyGraphStatsFrom(CrucibleGraph graph){}

    @Override
    void updateOnGraphChanged(){
        totalCapacity = 0f;
        crafts = false;

        for(var module : connected){
            int bitmask = 0;
            if(!module.initialized()){
                module.tilingIndex = 0;
                return;
            }
            for(int i = 0; i < 8; i++){
                Tile tile = module.parent.build.asBuilding().tile.nearby(Geometry.d8(i));
                if(tile == null || !(tile.build instanceof GraphBuildBase build)) continue;

                GraphCrucibleModule conModule = build.crucible();
                if(conModule == null || conModule.dead() || !canConnect(module, conModule)) continue;

                bitmask += 1 << i;
            }

            // PU_V8 模型: 每个方块贡献固定的基础容量 (旧版按直连邻居数打折会令孤立坩埚容量为 0)
            module.tilingIndex = bitmask;
            module.liquidCap = module.graph.baseLiquidCapacity;
            totalCapacity += module.liquidCap;
            crafts |= module.graph.doesCrafting;
        }

        // 网络收缩时按比例削减内容物
        if(getVolumeContained() > totalCapacity && totalCapacity > 0f){
            float decRatio = totalCapacity / getVolumeContained();
            for(var f : fluids){
                f.value.solid *= decRatio;
                f.value.melted *= decRatio;
            }
        }
    }

    @Override
    void updateGraph(){
        if(fluids.isEmpty()) return;
        if(!crafts){
            updateColor();
            return;
        }

        for(var module : connected){
            if(!module.graph.doesCrafting) continue;
            updateModule(module);
        }

        removeEmpty();
        updateColor();
    }

    /** 对单个参与合成的坩埚方块执行熔化/凝固/汽化/合金 */
    private void updateModule(GraphCrucibleModule module){
        GraphHeatModule heat = module.parent.build.heat();
        if(heat == null) return;

        float temp = heat.getTemp();
        float heatCapacity = heat.graph.baseHeatCapacity;

        // 内容物集中在网络侧 (PU_V8 为节点自存), 因此每个合成方块只能处理"自己容量份额"的那部分,
        // 否则网络内 N 个方块会把同一份内容物重复处理 N 次 → 熔化/合金速率放大 N 倍。
        float share = totalCapacity > 0f ? module.liquidCap / totalCapacity : 1f;
        if(share <= 0f) return;

        smeltOrder.clear();
        coolOrder.clear();
        boilOrder.clear();

        for(var fluid : fluids){
            CrucibleIngredient i = fluid.key;
            if(i.meltingpoint != -1 && temp >= i.meltingpoint && fluid.value.solid > 0){
                smeltOrder.add(i);
            }
            if(i.meltingpoint != -1 && temp < i.meltingpoint && fluid.value.melted > 0){
                coolOrder.add(i);
            }
            if(i.boilpoint != -1 && temp >= i.boilpoint && fluid.value.melted > 0){
                boilOrder.add(i);
            }
        }

        smeltOrder.sort((a, b) -> Float.compare(a.meltingpoint, b.meltingpoint));
        coolOrder.sort((a, b) -> -Float.compare(a.meltingpoint, b.meltingpoint));
        boilOrder.sort((a, b) -> -Float.compare(a.boilpoint, b.boilpoint));

        // 凝固 (放热)
        for(var item : coolOrder){
            float remaining = (item.meltingpoint - temp) * heatCapacity;
            if(remaining <= 0f) break;

            float reqSmelt = share * Math.min(fluids.get(item).melted, Math.max(0.1f, fluids.get(item).melted * item.meltspeed * Time.delta));
            float reqSmeltEnergy = reqSmelt * item.phaseChangeEnergy;
            if(reqSmeltEnergy <= 0f) continue;
            float smeltRatio = Mathf.clamp(remaining / reqSmeltEnergy);
            getFluid(item).melt(-smeltRatio * reqSmelt);
            heat.addHeatEnergy(smeltRatio * reqSmeltEnergy);
        }

        // 熔化 (吸热)
        for(var item : smeltOrder){
            float remaining = (temp - item.meltingpoint) * heatCapacity;
            if(remaining <= 0f) break;

            float reqSmelt = share * Math.min(fluids.get(item).solid, Math.max(0.1f, fluids.get(item).solid * item.meltspeed * Time.delta));
            float reqSmeltEnergy = reqSmelt * item.phaseChangeEnergy;
            if(reqSmeltEnergy <= 0f) continue;
            float smeltRatio = Mathf.clamp(remaining / reqSmeltEnergy);
            getFluid(item).melt(smeltRatio * reqSmelt);
            heat.addHeatEnergy(-smeltRatio * reqSmeltEnergy);
        }

        // 汽化 (吸热 + 特效)
        for(var item : boilOrder){
            float remaining = (temp - item.boilpoint) * heatCapacity;
            if(remaining <= 0f) break;

            float reqSmelt = share * Math.min(fluids.get(item).melted, Math.max(0.1f, fluids.get(item).melted * item.boilspeed * Time.delta));
            float reqSmeltEnergy = reqSmelt * item.phaseChangeEnergy;
            if(reqSmeltEnergy <= 0f) continue;
            float smeltRatio = Mathf.clamp(remaining / reqSmeltEnergy);
            float am = smeltRatio * reqSmelt;
            getFluid(item).vapourise(am);
            item.onVapourise(module.parent.build, am);
            heat.addHeatEnergy(-smeltRatio * reqSmeltEnergy);
        }

        // 合金合成
        for(var recipe : CrucibleRecipes.recipes){
            if(recipe.minTemp > temp) continue;

            float maxam = totalCapacity - getFluid(recipe.output).total();
            for(RecipeIngredient ri : recipe.items){
                CrucibleFluid fluid = getFluid(ri.ingredient);
                if(fluid.total() == 0f){
                    maxam = 0f;
                    break;
                }
                maxam = Math.min(maxam, (ri.melted ? fluid.melted : (ri.requiresSolid ? fluid.solid : fluid.total())) / ri.amount);
            }

            if(maxam <= 0f) continue;
            maxam *= recipe.speed * share;

            for(RecipeIngredient ri : recipe.items){
                CrucibleFluid fluid = getFluid(ri.ingredient);
                if(ri.melted){
                    fluid.melted -= maxam * ri.amount;
                }else if(ri.requiresSolid){
                    fluid.solid -= maxam * ri.amount;
                }else{
                    fluid.melted -= maxam * ri.amount;
                    if(fluid.melted < 0f){
                        fluid.solid += fluid.melted;
                        fluid.melted = 0f;
                    }
                }
            }
            getFluid(recipe.output).melted += maxam;
        }
    }

    private void removeEmpty(){
        Seq<CrucibleIngredient> rm = new Seq<>();
        for(var f : fluids){
            if(f.value.total() <= 0.0001f) rm.add(f.key);
        }
        for(var k : rm) fluids.remove(k);
    }

    @Override
    void updateDirect(){}

    @Override
    void addMergeStats(GraphCrucibleModule module){
        int port = module.getPortOfNetwork(this);
        totalCapacity += module.liquidCap;

        Seq<CrucibleFluid> cc = module.propsList.get(port);
        if(cc == null || cc.isEmpty()) return;

        for(var f : cc){
            addIngredient(f.getIngredient(), f.solid);
            addLiquidIngredient(f.getIngredient(), f.melted);
        }
    }

    @Override
    void mergeStats(CrucibleGraph graph){
        totalCapacity += graph.totalCapacity;
        for(var f : graph.fluids){
            addIngredient(f.key, f.value.solid);
            addLiquidIngredient(f.key, f.value.melted);
        }
    }

    @Override
    void killGraph(){
        for(var module : connected){
            float ratio = totalCapacity > 0f ? module.liquidCap / totalCapacity : 0f;
            Seq<CrucibleFluid> nc = new Seq<>();
            for(var f : fluids){
                CrucibleFluid copy = new CrucibleFluid(f.key);
                copy.solid = f.value.solid * ratio;
                copy.melted = f.value.melted * ratio;
                nc.add(copy);
            }
            module.propsList.put(module.getPortOfNetwork(this), nc);
        }
        connected.clear();
    }

    /**
     * 坩埚中的一种原料: 记录固态与熔融态的量。
     */
    public static class CrucibleFluid{
        CrucibleIngredient ingredient;
        public float melted;
        public float solid;

        public CrucibleFluid(CrucibleIngredient item){
            this.ingredient = item;
        }

        public float total(){
            return solid + melted;
        }

        public float meltedRatio(){
            return total() <= 0f ? 0f : melted / total();
        }

        public CrucibleIngredient getIngredient(){
            return ingredient;
        }

        public Item getItem(){
            if(ingredient instanceof CrucibleItem ci) return ci.item;
            return null;
        }

        /** 固↔液转化: t>0 表示熔化 t, t<0 表示凝固 */
        public void melt(float t){
            solid -= t;
            melted += t;
        }

        /** 汽化: 减少熔融量 */
        public void vapourise(float t){
            melted -= t;
            if(melted < 0f) melted = 0f;
        }
    }
}
