package zzw.content.mechanics.torque.meta;

import arc.struct.ObjectMap;
import mindustry.type.Item;

/**
 * 熔化信息 (PU132 unity.world.meta.MeltInfo 移植)
 * <p>
 * 定义每种物品在坩埚中的熔化行为:
 * <ul>
 *   <li>{@code meltPoint} - 熔点 (K)</li>
 *   <li>{@code meltSpeed} - 熔化速度倍率</li>
 *   <li>{@code evaporationTemp} - 蒸发温度 (K), -1 表示不蒸发</li>
 *   <li>{@code evaporation} - 蒸发速度</li>
 *   <li>{@code priority} - 铸造优先级 (数字越大越优先铸造)</li>
 *   <li>{@code additive} - 是否为添加剂 (如煤/石墨→碳)</li>
 * </ul>
 * <p>
 * 使用静态数组 {@link #all} 和映射 {@link #map} 全局注册所有熔化信息。
 */
public class MeltInfo{
    /** 所有熔化信息 (按 id 索引) */
    public static final MeltInfo[] all = new MeltInfo[14];
    /** 物品 → 熔化信息 映射 */
    public static final ObjectMap<Item, MeltInfo> map = new ObjectMap<>(14);

    public final Item item;
    public final MeltInfo additiveID;
    public final String name;

    public final float meltPoint, meltSpeed, evaporation, evaporationTemp, additiveWeight;
    public final int priority;
    public final byte id;
    public final boolean additive;

    private static byte total;

    public MeltInfo(Item item, MeltInfo additiveID, String name, float meltPoint, float meltSpeed, float evaporation, float evaporationTemp, float additiveWeight, int priority, boolean additive){
        this.item = item;
        this.additiveID = additiveID;
        this.name = name;

        this.meltPoint = meltPoint;
        this.meltSpeed = meltSpeed;
        this.evaporation = evaporation;
        this.evaporationTemp = evaporationTemp;
        this.additiveWeight = additiveWeight;
        this.priority = priority;
        this.additive = additive;

        all[total] = this;

        if(item != null) map.put(item, this);
        id = total++;
    }

    public MeltInfo(Item item, float meltPoint, float meltSpeed, float evaporation, float evaporationTemp, int priority){
        this(item, null, item.name, meltPoint, meltSpeed, evaporation, evaporationTemp, -1f, priority, false);
    }

    public MeltInfo(String name, float meltPoint, float meltSpeed, float evaporation, float evaporationTemp, int priority){
        this(null, null, name, meltPoint, meltSpeed, evaporation, evaporationTemp, -1f, priority, false);
    }

    public MeltInfo(Item item, MeltInfo additiveID, float additiveWeight, int priority, boolean additive){
        this(item, additiveID, item.name, -1f, -1f, -1f, -1f, additiveWeight, priority, additive);
    }

    public MeltInfo(Item item, float meltPoint, float meltSpeed, int priority){
        this(item, meltPoint, meltSpeed, -1f, -1f, priority);
    }
}
