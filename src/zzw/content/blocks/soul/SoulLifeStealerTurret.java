package zzw.content.blocks.soul;

import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.graphics.Pal;
import mindustry.world.meta.Stat;

import static mindustry.Vars.indexer;

/**
 * 灵魂吸血炮台 (v158 移植版, 替代 PU_V8 SoulLifeStealerTurret)
 *
 * = TractorBeamTurret (持续激光伤害) + ISoulTurret (灵魂系统) + 吸血蓄能+范围治疗机制
 *
 * 机制 (与 PU_V8 一致):
 * - 持续激光伤害目标 (apply 中调用 damageContinuous)
 * - 累积伤害量到 contained 字段
 * - 当 contained >= maxContain 时触发 tryHeal():
 *   - 范围内所有受损友方建筑被治疗 (healPercent % of maxHealth)
 *   - 触发拉拽特效 (healTrnsEffect) 从炮台指向目标建筑
 *   - 延迟后在目标位置触发治疗特效 (healEffect)
 * - contained 取模 maxContain (保留溢出量, 非清零)
 *
 * 参考: PU_V8 unity/world/blocks/defense/turrets/LifeStealerTurret.java
 */
public class SoulLifeStealerTurret extends SoulTractorBeamTurret {
    /** 蓄能阈值: 累积伤害达到此值后触发范围治疗 */
    public float maxContain = 100f;
    /** 治疗百分比 (相对于 maxHealth) */
    public float healPercent = 0.05f;
    /** 治疗拉拽特效 (从炮台指向目标) */
    public Effect healTrnsEffect = Fx.healBlockFull;
    /** 治疗命中特效 (在目标位置) */
    public Effect healEffect = Fx.healBlockFull;

    public SoulLifeStealerTurret(String name) {
        super(name);
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.abilities, t -> t.add("[lightgray]Life Steal Heal: [accent]" + (int)(healPercent * 100) + "%"));
        stats.add(Stat.abilities, t -> t.add("[lightgray]Max Contain: [accent]" + (int)maxContain));
    }

    public class SoulLifeStealerTurretBuild extends SoulTractorBeamTurretBuild {
        /** 当前蓄能值 */
        public float contained = 0f;

        @Override
        public void updateTile() {
            // 先调用父类更新 (含激光伤害)
            super.updateTile();

            // ★ 吸血蓄能: 当有目标且激光命中时, 累积伤害
            if (target != null && efficiency > 0) {
                // 每帧累积的伤害量 = damage / 60 * efficiency * delta
                // (与 PU_V8 LifeStealerTurret.apply 一致)
                float healthPerFrame = (baseDamage * soulEfficiency() / 60f) * Time.delta;
                contained += healthPerFrame;

                // 达到阈值触发范围治疗
                if (contained >= maxContain) {
                    tryHeal();
                }
            }
        }

        /** 范围治疗: 治疗范围内所有受损友方建筑 */
        protected void tryHeal() {
            // indexer.eachBlock(this, range, predicate, consumer) 默认只查本队建筑
            indexer.eachBlock(this, range, b -> b.health < b.maxHealth, b -> {
                // 拉拽特效: 从炮台到目标
                healTrnsEffect.at(x, y, 0, Pal.heal, b);
                // 延迟触发治疗 (匹配特效时长)
                Time.run(healTrnsEffect.lifetime, () -> {
                    if (b != null && b.isValid() && b.health < b.maxHealth) {
                        healEffect.at(b.x, b.y, b.block.size, Pal.heal);
                        // ★ v158 修复: 直接修改 health 字段 + healthChanged (healFract 不修改字段)
                        float healAmount = b.maxHealth * healPercent;
                        b.health = Math.min(b.maxHealth, b.health + healAmount);
                        b.healthChanged();
                    }
                });
            });
            // 蓄能值取模 (保留溢出量, 与 PU_V8 一致)
            contained %= maxContain;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(contained);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            contained = read.f();
        }
    }
}
