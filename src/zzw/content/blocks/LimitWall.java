package zzw.content.blocks;

import arc.util.Time;
import mindustry.entities.Effect;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.Stat;
import zzw.content.exp.UnityFx;

import static arc.Core.bundle;

/**
 * LimitWall (移植自 PU_V8 unity.world.blocks.defense.LimitWall)
 * - maxDamage: 单次伤害上限, 超过会被截断
 * - blinkFrame: 闪烁帧免伤 (每隔 blinkFrame 帧完全免伤一次)
 * - setStats: 在方块信息UI中显示 maxDamage 和 blinkFrame 属性
 */
public class LimitWall extends Wall {
    /** 单次受到伤害的最大值, 超过会被截断 (0=不限制) */
    public float maxDamage = 0f;
    /** 伤害阈值之上才生效 (避免被低伤害打破 maxDamage 限制) */
    public float over9000 = 90000000f;
    /** 闪烁帧间隔 (>0 时启用, 每隔此帧数完全免伤一次) */
    public float blinkFrame = -1f;

    /** 限伤命中特效 (PU132 LimitWall.maxDamageFx) */
    protected Effect maxDamageFx = UnityFx.maxDamageFx;
    /** 承受限伤特效 (PU132 LimitWall.withstandFx) */
    protected Effect withstandFx = UnityFx.withstandFx;
    /** 闪烁免伤特效 (PU132 LimitWall.blinkFx) */
    protected Effect blinkFx = UnityFx.blinkFx;

    public LimitWall(String name) {
        super(name);
    }

    @Override
    public void setStats() {
        super.setStats();
        if (maxDamage > 0f) stats.add(Stat.abilities, "@", bundle.format("stat.unity.maxdamage", maxDamage));
        if (blinkFrame > 0f) stats.add(Stat.abilities, "@", bundle.format("stat.unity.blinkframe", blinkFrame));
    }

    public class LimitWallBuild extends WallBuild {
        protected float blink;

        @Override
        public float handleDamage(float amount) {
            // blinkFrame 闪烁免伤
            if (blinkFrame > 0f) {
                if (Time.time - blink >= blinkFrame) {
                    blink = Time.time;
                    blinkFx.at(x, y, size);
                } else {
                    return 0f;
                }
            }
            // maxDamage 限伤
            if (maxDamage > 0f && amount > maxDamage && amount < over9000) {
                withstandFx.at(x, y, size);
                return super.handleDamage(Math.min(amount, maxDamage));
            }
            return super.handleDamage(amount);
        }
    }
}
