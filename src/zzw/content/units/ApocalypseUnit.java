package zzw.content.units;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;

/**
 * 天启单位 (移植自 PU132 ApocalypseUnit, 简化版)
 * - 继承隐形单位基类
 * - 集成优先伤害控制 (5 个优先级无敌帧)
 * - 简化: 跳过 Tentacle 触手系统, 保留基础防作弊
 * - 玩家可用作隐身 BOSS 模板
 */
public class ApocalypseUnit extends EndInvisibleUnit {
    public Seq<Object> tentacles = new Seq<>();
    private float immunity = 1f;
    private final float[] invFrames = new float[5];

    public Seq<Object> tentacles() {
        return tentacles;
    }

    public void tentacles(Seq<Object> t) {
        tentacles = t;
    }

    public void overrideAntiCheatDamage(float v) {
        overrideAntiCheatDamage(v, 0);
    }

    public void overrideAntiCheatDamage(float v, int priority) {
        if (invFrames[Mathf.clamp(priority, 0, invFrames.length - 1)] < 30f) return;
        hitTime = 1f;
        invFrames[Mathf.clamp(priority, 0, invFrames.length - 1)] = 0f;
        // 简化: 直接用 damage() 走标准路径
        damage(v);
    }

    @Override
    public void update() {
        super.update();
        for (int i = 0; i < invFrames.length; i++) {
            invFrames[i] += Time.delta;
        }
        immunity = Math.max(1f, immunity - (Time.delta / 4f));
    }

    public void addTentacles() {
        // 简化: 跳过实际触手创建, 仅保留空列表
        // 实际游戏中需要 UnitType 配置 tentacles 数组
    }
}
