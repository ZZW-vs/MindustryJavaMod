package zzw.content.units;

import arc.struct.Seq;
import mindustry.gen.Unitc;

/**
 * 触手系统接口 (移植自 PU132 TentaclesBase, 极简版)
 * - 简化为空接口, 仅保留 tentacles 字段
 * - 实际游戏中需要在 UnitType 中配置 tentacles 数组
 */
public interface TentaclesBase extends Unitc {
    Seq<Object> tentacles();

    void tentacles(Seq<Object> t);

    /** 简化: 跳过实际 update, 仅保留空操作 */
    default void updateTentacles() {
    }

    /** 简化: 跳过实际 draw, 仅保留空操作 */
    default void drawTentacles() {
    }

    default void addTentacles() {
        if (type() != null) {
            tentacles(new Seq<>());
        }
    }
}
