package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.ObjectFloatMap;
import arc.util.Strings;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * 带自定义冷却强化的物品炮台 (apparition 等使用)。
 *
 * <p>原版冷却强化公式为 {@code 1 + 消耗量 × coolantMultiplier × 液体热容}, 由于不同液体
 * 热容量不同 (水 0.4 / 冷冻液 0.9), 同一个 multiplier 算不出两个"整齐"的百分比。
 * 这里提供 {@link #coolantBoost} 表, 直接为每种液体指定额外装填速度比例,
 * 同时覆盖实际冷却效果与详情面板显示。</p>
 *
 * <p>表为空时完全走原版逻辑, 不影响其他炮台。</p>
 *
 * 参考: {@link ObjPowerTurret} 中的同款机制
 */
public class BoostItemTurret extends ItemTurret {

    /**
     * 自定义冷却强化表: 液体 → 额外装填速度比例 (0.2 表示 +20%, 即 120%)。
     */
    public ObjectFloatMap<Liquid> coolantBoost = new ObjectFloatMap<>();

    public BoostItemTurret(String name) {
        super(name);
    }

    @Override
    public void setStats() {
        super.setStats();

        // 自定义冷却强化显示: 直接展示固定百分比 (120% / 145% ...)
        if (coolant != null && !coolantBoost.isEmpty()) {
            stats.replace(Stat.booster, table -> {
                table.row();
                table.table(c -> {
                    for (Liquid liquid : mindustry.Vars.content.liquids()) {
                        float boost = coolantBoost.get(liquid, -1f);
                        if (boost < 0f) continue;

                        c.table(Styles.grayPanel, b -> {
                            b.image(liquid.uiIcon).size(40).pad(10f).left();
                            b.table(info -> {
                                info.add(liquid.localizedName).left().row();
                                info.add(Strings.autoFixed(coolant.amount * 60f, 2) + StatUnit.perSecond.localized())
                                        .left().color(Color.lightGray);
                            });
                            b.add(Core.bundle.format("bullet.reload", Strings.autoFixed((1f + boost) * 100f, 2)))
                                    .pad(10f).right().grow().padRight(15f);
                        }).growX().pad(5).row();
                    }
                }).growX().colspan(table.getColumns());
                table.row();
            });
        }
    }

    public class BoostItemTurretBuild extends ItemTurretBuild {

        /**
         * 覆写冷却推进: 使用 {@link BoostItemTurret#coolantBoost} 里的固定百分比。
         *
         * <p>原版实现是 {@code reloadCounter += 消耗量 × 热容 × coolantMultiplier},
         * 换成 {@code edelta() × boost} 后, 装填速度正好是 {@code 1 + boost}
         * (edelta 已包含基础每帧推进量), 例如 boost=0.2 → 120%。</p>
         */
        @Override
        protected void updateCooling() {
            if (coolantBoost.isEmpty()) {
                super.updateCooling();
                return;
            }

            if (coolant == null || coolant.efficiency(this) <= 0f || efficiency <= 0f) return;

            float boost = coolantBoost.get(liquids.current(), 0f);
            if (boost <= 0f) {
                super.updateCooling();
                return;
            }

            float amount = coolant.amount * coolant.efficiency(this);
            coolant.update(this);
            reloadCounter += edelta() * boost;

            if (Mathf.chance(0.06 * amount)) {
                coolEffect.at(x + Mathf.range(size * mindustry.Vars.tilesize / 2f), y + Mathf.range(size * mindustry.Vars.tilesize / 2f));
            }
        }
    }
}