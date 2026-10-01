package zzw.content.blocks;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.entities.Damage;
import zzw.content.mechanics.torque.blocks.GraphBlock;

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

    public HeatWall(String name) {
        super(name);
        update = true;
        solid = true;
    }

    public class HeatWallBuild extends GraphBuild {
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