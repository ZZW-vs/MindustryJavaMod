package zzw.content.blocks.turrets;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.ObjectFloatMap;
import arc.util.Strings;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.blocks.defense.turrets.LaserTurret;
import mindustry.world.consumers.ConsumeLiquidBase;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.tilesize;

/**
 * 带自定义冷却强化的激光炮台 (fallout / blue-eclipse / catastrophe / calamity / extinction 使用)。
 *
 * <p>原版 {@link LaserTurret} 在装填期间按 {@code 消耗量 × 液体热容 × coolantMultiplier}
 * 扣减装填计时, 由于水 (0.4) 与冷冻液 (0.9) 热容不同, 同一个 multiplier 会算出两个"奇怪"的
 * 百分比。这里用 {@link #coolantBoost} 直接为每种液体指定固定加成, 实际效果与详情面板显示一致。</p>
 *
 * <p>语义为"提升效率": 面板显示 {@code 提升效率 xxx%} (xxx = (1 + boost) × 100, 末位为 0 或 5)。
 * 表为空时完全走原版逻辑, 不影响其他炮台。</p>
 *
 * 参考: {@link BoostItemTurret} 中的同款机制
 */
public class BoostLaserTurret extends LaserTurret {

    /**
     * 自定义冷却强化表: 液体 → 额外装填速度比例 (0.25 表示 +25%, 即 125%)。
     */
    public ObjectFloatMap<Liquid> coolantBoost = new ObjectFloatMap<>();

    public BoostLaserTurret(String name){
        super(name);
    }

    @Override
    public void setStats(){
        super.setStats();

        // 原版 LaserTurret.setStats() 把冷却液显示在 Stat.input 下 (按热容公式算出的百分比),
        // 这里移除它, 换成本表的固定百分比, 语义为"提升效率"。
        if(coolant != null && !coolantBoost.isEmpty()){
            stats.remove(Stat.input);
            stats.add(Stat.booster, table -> {
                table.row();
                table.table(c -> {
                    for(Liquid liquid : mindustry.Vars.content.liquids()){
                        float boost = coolantBoost.get(liquid, -1f);
                        if(boost < 0f) continue;

                        c.table(Styles.grayPanel, b -> {
                            b.image(liquid.uiIcon).size(40).pad(10f).left();
                            b.table(info -> {
                                info.add(liquid.localizedName).left().row();
                                info.add(Strings.autoFixed(coolant.amount * 60f, 2) + StatUnit.perSecond.localized())
                                        .left().color(Color.lightGray);
                            });
                            b.add("[lightgray]提升效率 [accent]" + Strings.autoFixed((1f + boost) * 100f, 0) + "%")
                                    .pad(10f).right().grow().padRight(15f);
                        }).growX().pad(5).row();
                    }
                }).growX().colspan(table.getColumns());
                table.row();
            });
        }
    }

    public class BoostLaserTurretBuild extends LaserTurretBuild{

        /**
         * 覆写装填推进: 使用 {@link BoostLaserTurret#coolantBoost} 里的固定百分比。
         *
         * <p>原版 {@code LaserTurret.updateTile()} 在 {@code reloadCounter > 0} 时按
         * {@code 消耗量 × 热容 × coolantMultiplier} 扣减; 这里先临时把 {@code coolant} 置空,
         * 让父类走 {@code reloadCounter -= edelta()} 的基础分支, 之后再按 boost 追加扣减,
         * 使实际装填速度 = {@code 1 + boost}。</p>
         */
        @Override
        public void updateTile(){
            if(coolantBoost.isEmpty() || coolant == null){
                super.updateTile();
                return;
            }

            Liquid liquid = liquids.current();
            float boost = coolantBoost.get(liquid, 0f);
            ConsumeLiquidBase saved = coolant;

            // 临时屏蔽冷却液, 让父类只走基础推进 (edelta), 之后由本方法按 boost 追加
            coolant = null;
            super.updateTile();
            coolant = saved;

            if(boost <= 0f || efficiency <= 0f || saved.efficiency(this) <= 0f || reloadCounter <= 0f) return;

            reloadCounter = Math.max(0f, reloadCounter - edelta() * boost);

            float used = (cheating() ? saved.amount : Math.min(liquids.get(liquid), saved.amount)) * delta();
            liquids.remove(liquid, used);

            if(Mathf.chance(0.06 * used)){
                coolEffect.at(x + Mathf.range(size * tilesize / 2f), y + Mathf.range(size * tilesize / 2f));
            }
        }
    }
}