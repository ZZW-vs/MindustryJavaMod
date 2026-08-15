package zzw.content.mechanics.torque.meta;

import mindustry.type.Item;

/**
 * 坩埚数据 (PU132 unity.world.meta.CrucibleData 移植)
 * <p>
 * 记录坩埚中某种物品的体积和熔化比例。
 * <ul>
 *   <li>{@code id} - 对应 {@link MeltInfo} 的 id</li>
 *   <li>{@code volume} - 体积 (单位: 格)</li>
 *   <li>{@code meltedRatio} - 熔化比例 (0=完全固态, 1=完全液态)</li>
 *   <li>{@code item} - 对应的物品 (可能为 null, 如碳元素)</li>
 * </ul>
 */
public class CrucibleData{
    public final int id;
    public final Item item;

    public float volume, meltedRatio;

    public CrucibleData(int id, float volume, float meltedRatio, Item item){
        this.id = id;
        this.volume = volume;
        this.meltedRatio = meltedRatio;
        this.item = item;
    }
}
