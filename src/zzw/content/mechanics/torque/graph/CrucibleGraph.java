package zzw.content.mechanics.torque.graph;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.type.Item;
import mindustry.world.Tile;
import zzw.content.graphics.UnityPal;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;
import zzw.content.mechanics.torque.meta.CrucibleData;
import zzw.content.mechanics.torque.meta.CrucibleRecipe;
import zzw.content.mechanics.torque.meta.MeltInfo;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;

/**
 * 坩埚网络 (PU132 unity.world.graph.CrucibleGraph 移植)
 * <p>
 * 管理连接的坩埚模块之间的熔融物共享、熔化、合金、蒸发等逻辑。
 * <p>
 * 核心功能:
 * <ol>
 *   <li>{@link #addItem(Item)} - 添加物品到坩埚网络</li>
 *   <li>{@link #updateGraph()} - 每帧更新熔化/合金/蒸发</li>
 *   <li>{@link #updateOnGraphChanged()} - 网络拓扑变化时重算容量和 tiling 索引</li>
 *   <li>{@link #updateColor()} - 根据熔融物颜色更新网络颜色</li>
 * </ol>
 */
public class CrucibleGraph extends BaseGraph<GraphCrucibleModule, CrucibleGraph>{
    static final float[] capacityMul = new float[]{0f, 0.1f, 0.2f, 0.5f, 1f};
    public final Color color = Color.clear.cpy();
    final Seq<CrucibleData> contains = new Seq<>();
    float totalVolume, totalCapacity, containedAmCache;
    boolean containChanged = true, crafts = true;

    @Override
    public CrucibleGraph create(){
        return new CrucibleGraph();
    }

    /** 获取网络中熔融物的总体积 */
    public float getVolumeContained(){
        if(containChanged){
            containedAmCache = 0f;
            for(int i = 0, len = contains.size; i < len; i++) containedAmCache += contains.get(i).volume;
        }
        return containedAmCache;
    }

    /** 添加物品到坩埚网络, 返回是否成功 */
    public boolean addItem(Item item){
        MeltInfo meltProd = MeltInfo.map.get(item);
        if(meltProd == null) return false;
        if(meltProd.additive){
            return addMeltItem(meltProd.additiveID, meltProd.additiveWeight, false);
        }else{
            return addMeltItem(meltProd, 1f, false);
        }
    }

    /** 根据 MeltInfo id 获取熔融物数据 */
    public CrucibleData getMeltFromID(int id){
        return contains.find(i -> i.id == id);
    }

    /**
     * 添加熔融物到坩埚网络
     * @param meltProd 熔化信息
     * @param am 数量
     * @param liquid 是否为液态
     * @return 是否成功
     */
    public boolean addMeltItem(MeltInfo meltProd, float am, boolean liquid){
        CrucibleData avalslot = null;
        int totalContained = 0;

        for(var i : contains){
            if(i.id == meltProd.id) avalslot = i;
            totalContained += i.volume;
        }

        if(totalContained + am > totalCapacity) return false;
        if(avalslot != null){
            if(liquid) addLiquidToSlot(avalslot, am);
            else addSolidToSlot(avalslot, am);
        }else{
            contains.add(new CrucibleData(meltProd.id, am, liquid ? 1f : 0f, meltProd.item));
        }

        containChanged = true;
        return true;
    }

    /** 是否还能容纳更多 */
    public boolean canContainMore(float amount){
        return getVolumeContained() + amount <= totalCapacity;
    }

    /** 剩余空间 */
    public float getRemainingSpace(){
        return Math.max(0, totalCapacity - getVolumeContained());
    }

    void addSolidToSlot(CrucibleData slot, float am){
        float melted = slot.meltedRatio * slot.volume;
        slot.volume += am;
        slot.meltedRatio = melted / slot.volume;

        if(slot.volume <= 0f || slot.meltedRatio <= 0f) slot.meltedRatio = 0f;
        containChanged = true;
    }

    /** 向指定槽位添加液体 (am 可为负, 表示取出) */
    public void addLiquidToSlot(CrucibleData slot, float am){
        float melted = slot.meltedRatio * slot.volume + am;
        slot.volume += am;
        slot.meltedRatio = melted / slot.volume;

        if(slot.volume <= 0f || slot.meltedRatio <= 0f) slot.meltedRatio = 0f;
        containChanged = true;
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
            int directNeighbour = 0;
            for(int i = 0; i < 8; i++){
                Tile tile = module.parent.build.asBuilding().tile.nearby(Geometry.d8(i));

                if(tile == null || !(tile.build instanceof GraphBuildBase build)) continue;

                GraphCrucibleModule conModule = build.crucible();
                if(conModule == null || conModule.dead() || !canConnect(module, conModule)) continue;
                if(i % 2 == 0) directNeighbour++;

                bitmask += 1 << i;
            }

            module.tilingIndex = bitmask;
            module.liquidCap = (module.parent.build.asBuilding().block.size == 1 ? capacityMul[directNeighbour] : 1f) * module.graph.baseLiquidCapacity;

            totalCapacity += module.liquidCap;
            crafts |= module.graph.doesCrafting;
        }
        if(getVolumeContained() > totalCapacity){
            float decRatio = totalCapacity / getVolumeContained();

            for(int i = 0, len = contains.size; i < len; i++) contains.get(i).volume *= decRatio;
            containChanged = true;
        }
    }

    /** 获取网络平均温度 (K) */
    public float getAverageTemp(){
        float speed = 0f;
        int count = 0;

        for(var module : connected){
            if(!module.graph.doesCrafting) continue;
            speed += module.parent.build.heat().getTemp();
            count++;
        }
        if(count == 0) return 0f;
        return speed / count;
    }

    float getAverageTempDecay(float meltPoint, float meltSpeed, float tmpDep, float coolDep){
        float speed = 0f;
        int count = 0;

        for(var module : connected){
            if(!module.graph.doesCrafting) continue;

            float temp = module.parent.build.heat().getTemp();

            if(temp > meltPoint){
                speed += (1f + temp / meltPoint * tmpDep) * meltSpeed;
            }else{
                speed -= (1f - temp / meltPoint) * coolDep * meltSpeed;
            }
            count++;
        }
        if(count == 0) return 0;

        return speed / count;
    }

    float getAverageMeltSpeed(MeltInfo m, float tmpDep, float coolDep){
        return getAverageTempDecay(m.meltPoint, m.meltSpeed, tmpDep, coolDep);
    }

    float getAverageMeltSpeedIndex(int index, float tmpDep, float coolDep){
        return getAverageMeltSpeed(MeltInfo.all[index], tmpDep, coolDep);
    }

    /** 更新网络颜色 (根据所有熔融物的加权平均颜色) */
    public void updateColor(){
        color.set(0f, 0f, 0f);
        float tLiquid = 0f;

        for(var i : contains){
            if(i.meltedRatio > 0f){
                float liquidVol = i.meltedRatio * i.volume;
                tLiquid += liquidVol;
                Color itemCol = UnityPal.youngchaGray;
                if(i.item != null) itemCol = i.item.color;
                color.r += itemCol.r * liquidVol;
                color.g += itemCol.g * liquidVol;
                color.b += itemCol.b * liquidVol;
            }
        }
        float invt = 1f / tLiquid;
        color.mul(invt).a(Mathf.clamp(2f * tLiquid / totalCapacity));
    }

    @Override
    void updateGraph(){
        if(contains.isEmpty()) return;
        if(!crafts){
            removeEmptyMelts();
            updateColor();
            return;
        }

        float capcityMul = Mathf.sqrt(totalCapacity / 15f);

        for(var i : contains){
            float meltMul = Time.delta / i.volume;

            if(i.id < MeltInfo.all.length){
                MeltInfo m = MeltInfo.all[i.id];
                i.meltedRatio += meltMul * getAverageMeltSpeed(m, 0.002f, 0.5f) * 0.4f * capcityMul;
                i.meltedRatio = Mathf.clamp(i.meltedRatio);

                if(m.evaporationTemp >= 0f){
                    float evap = getAverageTempDecay(m.evaporationTemp, m.evaporation, 0f, 1f);
                    if(evap > 0f){
                        i.volume -= evap;
                        containChanged = true;
                    }
                }
            }
        }
        for(var z : CrucibleRecipe.all){
            boolean valid = true;
            float maxCraftable = 9999999f;
            int len = z.input.length;
            int[] inputSlots = new int[len];

            for(int r = 0; r < len; r++){
                boolean found = false;
                for(var ingre : contains){
                    CrucibleRecipe.InputRecipe alyInput = z.input[r];
                    if(MeltInfo.all[ingre.id] == alyInput.material && (!alyInput.needsLiquid || ingre.meltedRatio > 0f)){
                        found = true;
                        inputSlots[r] = ingre.id;
                        maxCraftable = Math.min(maxCraftable, (alyInput.needsLiquid ? ingre.meltedRatio : 1f) * ingre.volume / alyInput.amount);
                        break;
                    }
                }
                if(!found){
                    valid = false;
                    break;
                }
            }
            if(valid && maxCraftable > 0f){
                float craftAm = Math.min(maxCraftable, z.alloySpeed * Time.delta * 0.2f * capcityMul);
                if(craftAm <= 0f) return;

                for(int r = 0; r < len; r++){
                    CrucibleRecipe.InputRecipe alyInput = z.input[r];
                    if(alyInput.needsLiquid){
                        addLiquidToSlot(contains.get(inputSlots[r]), -alyInput.amount * craftAm);
                    }else{
                        contains.get(inputSlots[r]).volume -= alyInput.amount * craftAm;
                        containChanged = true;
                    }
                }
                addMeltItem(z.melt, craftAm, true);
            }
        }
        removeEmptyMelts();
        updateColor();
    }

    void removeEmptyMelts(){
        contains.removeAll(i -> i.volume <= 0f);
    }

    @Override
    void killGraph(){
        for(var module : connected){
            Seq<CrucibleData> nc = new Seq<>();
            float ratio = module.liquidCap / totalCapacity;

            for(var i : contains) nc.add(new CrucibleData(i.id, i.volume * ratio, i.meltedRatio, i.item));
            module.propsList.put(module.getPortOfNetwork(this), nc);
        }
        connected.clear();
    }

    @Override
    void updateDirect(){}

    @Override
    void addMergeStats(GraphCrucibleModule module){
        int port = module.getPortOfNetwork(this);
        totalCapacity += module.liquidCap;

        Seq<CrucibleData> cc = module.propsList.get(port);
        if(cc == null || cc.isEmpty()) return;
        MeltInfo[] melts = MeltInfo.all;

        for(var i : cc){
            addMeltItem(melts[i.id], i.volume * (1f - i.meltedRatio), false);
            addMeltItem(melts[i.id], i.volume * i.meltedRatio, true);
        }
    }

    @Override
    void mergeStats(CrucibleGraph graph){
        MeltInfo[] melts = MeltInfo.all;
        totalCapacity += graph.totalCapacity;

        for(var i : graph.contains){
            addMeltItem(melts[i.id], i.volume * (1f - i.meltedRatio), false);
            addMeltItem(melts[i.id], i.volume * i.meltedRatio, true);
        }
    }

    public Seq<CrucibleData> contains(){
        return contains;
    }

    public float totalCapacity(){
        return totalCapacity;
    }
}
