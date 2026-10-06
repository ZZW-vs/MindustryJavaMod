package zzw.content.blocks.soul;

import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.gen.Building;
import mindustry.gen.Player;
import zzw.content.units.entities.MonolithSoulUnit;

/**
 * 灵魂炮台接口 (v158 简化版, 替代 PU_V8 @Merge 注解生成的 Soulc/Stemc 接口)
 *
 * 炮台实现此接口表示能接受灵魂(Soul), 灵魂数量影响炮台效率(0.7~1.8倍)和伤害
 * SoulInfuser 建筑会扫描附近实现了此接口的炮台, 给它们添加灵魂
 *
 * <p>★ 灵魂系统统一: 炮台与巨石单位通过 {@code Soul.toSoul(炮台建筑)} 适配为
 * 统一的 {@code Soul} —— 巨石单位死亡拆出的灵魂单位可以飞到炮台上
 * ({@code Soul.toSoul} 会识别本接口并用 {@code Soul.TurretSoul} 包装)。</p>
 *
 * <p>★ 灵魂释放: 炮台被摧毁时调用 {@link #spreadSouls()} 把体内灵魂拆成灵魂单位
 * (PU132 {@code SoulComp.onRemoved -> Soul.spreadSouls})。</p>
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

    /** 添加一个灵魂 (来自 SoulInfuser / 灵魂单位), 返回是否成功 */
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

    /**
     * 把体内灵魂拆解为灵魂单位飞散 (PU132 Soul.spreadSouls / SoulComp.onRemoved)。
     *
     * <p>每个灵魂从建筑中心随机方向偏移出生, 再沿圆周均匀方向赋予初速度;
     * 若炮台受玩家/逻辑操控, 按概率把其中一只灵魂的操控权交给玩家
     * (PU132 SoulComp.apply)。应在服务端调用。</p>
     */
    default void spreadSouls() {
        if (!(this instanceof Building build)) return;

        int n = souls();
        if (n <= 0) return;

        // PU132 SoulComp.apply: 仅当炮台受控时, 才把一只灵魂交给玩家操控
        Player controller = null;
        if (build instanceof mindustry.world.blocks.ControlBlock cb && cb.isControlled()
            && cb.unit() != null && cb.unit().getPlayer() != null) {
            controller = cb.unit().getPlayer();
        }

        boolean transferred = false;
        float start = Mathf.random(360f);

        for (int i = 0; i < n; i++) {
            MonolithSoulUnit soul = MonolithSoulUnit.create(build.team);

            // 出生位置: 建筑范围内随机偏移
            Tmp.v1.trns(Mathf.random(360f), Mathf.random(build.hitSize()));
            soul.set(build.x + Tmp.v1.x, build.y + Tmp.v1.y);

            // 初速度: 沿圆周均匀分布方向
            Tmp.v1.trns(start + 360f / n * i, Mathf.random(6f, 12f));
            soul.rotation = Tmp.v1.angle();
            soul.vel.set(Tmp.v1.x, Tmp.v1.y);

            if (controller != null && !transferred && (Mathf.chance(1f / n) || i == n - 1)) {
                soul.controller(controller);
                transferred = true;
            }

            soul.add();
        }
    }
}
