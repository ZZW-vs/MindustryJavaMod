package zzw.content.blocks.soul;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.graphics.Pal;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.indexer;

/**
 * 灵魂吸血炮台 (v158 移植版, 替代 PU_V8 SoulLifeStealerTurret)
 *
 * = TractorBeamTurret (持续激光伤害) + ISoulTurret (灵魂系统) + 吸血蓄能+范围治疗机制
 *
 * 机制 (与 PU_V8 LifeStealerTurret 一致):
 * - 持续激光伤害目标 (父类 TractorBeamTurret 内部已调用 damageContinuous)
 * - 累积伤害量到 contained 字段
 * - 当 contained >= maxContain 时触发 tryHeal():
 *   - 范围内所有受损友方建筑被治疗 (healPercent % of maxHealth)
 *   - 触发拉拽特效 (healTrnsEffect) 从炮台指向目标建筑
 *   - 延迟后在目标位置触发治疗特效 (healEffect)
 * - 仅当存在治疗目标时, contained %= maxContain (保留溢出量, 非清零)
 *
 * 参考: PU_V8 unity/world/blocks/defense/turrets/LifeStealerTurret.java
 */
public class SoulLifeStealerTurret extends SoulTractorBeamTurret {
    /** 蓄能阈值: 累积伤害达到此值后触发范围治疗 */
    public float maxContain = 100f;
    /** 治疗百分比 (相对于 maxHealth) */
    public float healPercent = 0.05f;
    /** 治疗拉拽特效颜色 (PU_V8 UnityPal.monolithLight, 默认 Pal.heal) */
    public Color healColor = Pal.heal;
    /** 治疗拉拽特效 (从炮台指向目标, PU_V8 UnityFx.supernovaPullEffect) */
    public Effect healTrnsEffect = Fx.healBlockFull;
    /** 治疗命中特效 (在目标位置, PU_V8 Fx.healBlockFull) */
    public Effect healEffect = Fx.healBlockFull;

    public SoulLifeStealerTurret(String name) {
        super(name);
    }

    @Override
    public void setStats() {
        super.setStats();
        // 与 PU_V8 LifeStealerTurret.setStats 一致
        stats.add(Stat.damage, baseDamage / 60f, StatUnit.perSecond);
        stats.add(Stat.abilities, cont -> {
            cont.row();
            cont.table(bt -> {
                bt.left().defaults().padRight(3).left();
                bt.row();
                bt.add("[lightgray]Max Contain: [accent]" + (int)maxContain);
                bt.row();
                bt.add("[lightgray]Life Steal Heal: [accent]" + (int)(healPercent * 100) + "%");
            });
        });
    }

    public class SoulLifeStealerTurretBuild extends SoulTractorBeamTurretBuild {
        /** 当前蓄能值 */
        public float contained = 0f;

        @Override
        public void updateTile() {
            // 先调用父类更新 (含激光伤害, 父类会调用 target.damageContinuous)
            super.updateTile();

            // ★ 吸血蓄能: 当有目标且激光命中时, 累积伤害
            // 与 PU_V8 LifeStealerTurret.apply 一致:
            //   float health = (damage / 60f) * efficiency;
            //   h.damageContinuous(health);
            //   contained += health * Time.delta;
            // 但父类 TractorBeamTurret 已经调用 damageContinuous, 此处仅累积 contained
            if (target != null && efficiency > 0) {
                // 每帧累积的伤害量 = (damage / 60) * efficiency * delta
                float healthPerFrame = (baseDamage * soulEfficiency() / 60f) * Time.delta;
                contained += healthPerFrame;

                // 达到阈值触发范围治疗
                if (contained >= maxContain) {
                    tryHeal();
                }
            }
        }

        /**
         * 范围治疗: 治疗范围内所有受损友方建筑 (完整复刻 PU_V8 LifeStealerTurret.tryHeal)
         * - indexer.eachBlock 返回 boolean, 表示是否存在符合条件的建筑
         * - 仅当 any=true 时, contained %= maxContain (保留溢出量)
         */
        protected void tryHeal() {
            // indexer.eachBlock(this, range, predicate, consumer) 默认只查本队建筑
            // 返回值 any 表示是否有任意建筑被消费 (PU_V8 关键逻辑)
            boolean any = indexer.eachBlock(this, range,
                b -> b.health < b.maxHealth,
                b -> {
                    // 拉拽特效: 从炮台指向目标 (PU_V8 用 SVec2.construct(b.x, b.y) 作为 data, 此处用 b 替代)
                    healTrnsEffect.at(x, y, 2.5f + Mathf.range(0.3f), healColor, b);
                    // 延迟触发治疗 (匹配 healEffect.lifetime, 与 PU_V8 一致)
                    Time.run(healEffect.lifetime, () -> {
                        if (b != null && b.isValid() && b.health < b.maxHealth) {
                            healEffect.at(b.x, b.y, b.block.size, healColor);
                            // ★ v158 修复: 直接修改 health 字段 + healthChanged (替代 b.healFract)
                            float healAmount = b.maxHealth * healPercent;
                            b.health = Math.min(b.maxHealth, b.health + healAmount);
                            b.healthChanged();
                        }
                    });
                }
            );

            // 仅当存在治疗目标时取模蓄能值 (保留溢出量, 与 PU_V8 一致)
            if (any) {
                contained %= maxContain;
            }
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
