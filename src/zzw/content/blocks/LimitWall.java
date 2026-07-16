package zzw.content.blocks;

import arc.util.Time;
import mindustry.world.blocks.defense.Wall;

/**
 * LimitWall (移植自 PU_V8 unity.world.blocks.defense.LimitWall)
 * - maxDamage: 单次伤害上限, 超过会被截断
 * - blinkFrame: 闪烁帧免伤 (每隔 blinkFrame 帧完全免伤一次)
 */
public class LimitWall extends Wall {
    /** 单次受到伤害的最大值, 超过会被截断 (0=不限制) */
    public float maxDamage = 0f;
    /** 伤害阈值之上才生效 (避免被低伤害打破 maxDamage 限制) */
    public float over9000 = 90000000f;
    /** 闪烁帧间隔 (>0 时启用, 每隔此帧数完全免伤一次) */
    public float blinkFrame = -1f;

    public LimitWall(String name) {
        super(name);
    }

    public class LimitWallBuild extends WallBuild {
        protected float blink;

        @Override
        public float handleDamage(float amount) {
            // blinkFrame 闪烁免伤
            if (blinkFrame > 0f) {
                if (Time.time - blink >= blinkFrame) {
                    blink = Time.time;
                } else {
                    return 0f;
                }
            }
            // maxDamage 限伤
            if (maxDamage > 0f && amount > maxDamage && amount < over9000) {
                return super.handleDamage(Math.min(amount, maxDamage));
            }
            return super.handleDamage(amount);
        }
    }
}
