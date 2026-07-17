package zzw.content.blocks.soul;

/**
 * 灵魂炮台接口 (v158 简化版, 替代 PU_V8 @Merge 注解生成的 Soulc/Stemc 接口)
 *
 * 炮台实现此接口表示能接受灵魂(Soul), 灵魂数量影响炮台效率(0.7~1.8倍)和伤害
 * SoulInfuser 建筑会扫描附近实现了此接口的炮台, 给它们添加灵魂
 *
 * 参考: PU_V8 unity/entities/merge/SoulComp.java
 */
public interface ISoulTurret {
    /** 当前灵魂数量 */
    int souls();

    /** 最大灵魂数量 */
    int maxSouls();

    /** 是否有灵魂 */
    default boolean hasSouls() {
        return souls() > 0;
    }

    /** 添加一个灵魂 (来自 SoulInfuser), 返回是否成功 */
    boolean joinSoul();

    /** 移除一个灵魂 (炮台被摧毁时返还) */
    boolean unjoinSoul();

    /** 灵魂比例 (0~1) */
    default float soulf() {
        return maxSouls() > 0 ? souls() / (float) maxSouls() : 1f;
    }

    /** 是否需要灵魂才能工作 */
    boolean requireSoul();

    /** 灵魂效率起止范围 */
    float efficiencyFrom();
    float efficiencyTo();

    /** 灵魂效率倍率 (供 Building.efficiency 调用) */
    default float soulEfficiency() {
        if (requireSoul() && !hasSouls()) return 0f;
        return soulf() * (efficiencyTo() - efficiencyFrom()) + efficiencyFrom();
    }
}
