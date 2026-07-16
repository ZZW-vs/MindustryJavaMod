package zzw.content.blocks;

import mindustry.world.blocks.defense.Wall;

/**
 * 简化版 LimitWall (移植自 PU_V8 unity.world.blocks.defense.LimitWall)
 * - 仅保留 maxDamage 特性 (单次伤害上限)
 * - 移除 blinkFrame 闪烁帧 (避免依赖 PU_V8 自定义特效)
 * - 不依赖经验等级系统
 */
public class LimitWall extends Wall {
    /** 单次受到伤害的最大值, 超过会被截断 (但仍是有效伤害) */
    public float maxDamage = 0f;
    /** 伤害阈值之上才生效 (避免被低伤害打破 maxDamage 限制) */
    public float over9000 = 90000000f;

    public LimitWall(String name) {
        super(name);
    }

    public class LimitWallBuild extends WallBuild {
        @Override
        public float handleDamage(float amount) {
            if (maxDamage > 0f && amount > maxDamage && amount < over9000) {
                // 限制单次最大伤害, 但仍是有效伤害
                return super.handleDamage(Math.min(amount, maxDamage));
            }
            return super.handleDamage(amount);
        }
    }
}
