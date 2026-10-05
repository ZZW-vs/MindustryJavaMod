package zzw.content.blocks;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.entities.Damage;
import mindustry.world.meta.Stat;
import zzw.content.mechanics.torque.blocks.GraphBlock;

import static arc.Core.bundle;

/**
 * 铜镍合金墙/热墙 (PU132 unity.world.blocks.defense.HeatWall 移植)
 *
 * <p>接入热量网络的墙体: 墙体温度越高, 越会向周围持续施放灼烧状态并造成伤害。
 * 每隔 {@code statusTime} 秒根据当前温度计算一次强度
 * ({@code Mathf.map(温度, 400..1000 → 0..1)}):</p>
 * <ul>
 *   <li>状态: 半径 {@code intensity*statusRadiusMul + minStatusRadius}, 灼烧时长
 *       {@code minStatusDuration + intensity*statusDurationMul}</li>
 *   <li>伤害 (maxDamage>0 时): 半径 {@code intensity*10 + 8}, 伤害 {@code intensity*maxDamage}</li>
 * </ul>
 *
 * <p>★ 实现方式: 原版直接 extends Block 实现 GraphBlockBase; 本移植直接继承项目的
 * {@link GraphBlock} (已封装 graphs/gms 与热图绘制), 仅重写 {@code updatePost()}。</p>
 */
public class HeatWall extends GraphBlock {
    /** 灼烧状态最小半径 */
    protected float minStatusRadius = 4f;
    /** 灼烧状态半径随强度增量 */
    protected float statusRadiusMul = 20f;
    /** 灼烧状态最短时长 */
    protected float minStatusDuration = 3f;
    /** 灼烧状态时长随强度增量 */
    protected float statusDurationMul = 40f;
    /** 灼烧判定间隔 (秒) */
    protected float statusTime = 60f;
    /** 伤害强度上限 (0=不造成伤害, 仅状态) */
    protected float maxDamage;

    // ===== 温度限伤机制 (铜镍合金墙专属) =====
    /** 常温下的单次承受伤害上限 (<=0 表示不启用该机制) */
    protected float damageLimitBase = 0f;
    /** 常温 (K) */
    protected float ambientTemp = 293.15f;
    /** 高于常温: 每升高 hotLimitSpan 摄氏度, 限伤下降 hotLimitStep */
    protected float hotLimitSpan = 100f, hotLimitStep = 5f;
    /** 低于常温: 每降低 coldLimitSpan 摄氏度, 限伤上升 coldLimitStep */
    protected float coldLimitSpan = 50f, coldLimitStep = 5f;

    public HeatWall(String name) {
        super(name);
        update = true;
        solid = true;
    }

    @Override
    public void setStats() {
        super.setStats();
        // 温度限伤: 显示常温基准限伤与随温度变化的规则
        if (damageLimitBase > 0f) {
            stats.add(Stat.abilities, "@", bundle.format("stat.unity.tempdamagelimit", damageLimitBase));
            stats.add(Stat.abilities, "@", bundle.format("stat.unity.tempdamagelimit.rule", hotLimitSpan, hotLimitStep, coldLimitSpan, coldLimitStep));
        }
    }

    public class HeatWallBuild extends GraphBuild {
        /** 按当前温度换算的限伤值 (常温 damageLimitBase, 越高越低, 越低越高) */
        public float damageLimit() {
            var h = heat();
            float tC = (h == null ? ambientTemp : h.getTemp()) - 273.15f;
            float delta = tC - (ambientTemp - 273.15f);
            float cap = damageLimitBase;
            if (delta > 0f) {
                cap -= hotLimitStep * (delta / hotLimitSpan);
            } else if (delta < 0f) {
                cap += coldLimitStep * (-delta / coldLimitSpan);
            }
            return Math.max(cap, 0f);
        }

        @Override
        public float handleDamage(float amount) {
            // 温度限伤: 单次伤害超过当前上限则截断 (温度越高上限越低)
            if (damageLimitBase > 0f) {
                return super.handleDamage(Math.min(amount, damageLimit()));
            }
            return super.handleDamage(amount);
        }

        @Override
        public void updatePost() {
            // 按间隔判定 (timerDump = 计时器槽位索引, statusTime = 间隔; v160 语义一致)
            if (timer(timerDump, statusTime)) {
                float intensity = Mathf.clamp(Mathf.map(heat().getTemp(), 400f, 1000f, 0f, 1f));
                Damage.status(team, x, y, intensity * statusRadiusMul + minStatusRadius, StatusEffects.burning, minStatusDuration + intensity * statusDurationMul, false, true);
                if (maxDamage > 0f) Damage.damage(team, x, y, intensity * 10f + 8f, intensity * maxDamage, false, true);
            }
        }
    }
}