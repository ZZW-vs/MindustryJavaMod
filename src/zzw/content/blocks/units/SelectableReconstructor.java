package zzw.content.blocks.units;

import arc.*;
import arc.Core;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import arc.graphics.Color;
import mindustry.game.EventType;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.meta.Stat;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.UnitPayload;

/**
 * 可切换重构器 (PU132 unity.world.blocks.units.SelectableReconstructor 完整移植)
 *
 * <p>T6/T7 合用的升级工厂: 配置界面两个按钮切换当前工作的等级档位,
 * tier=minTier 时按 upgrades 配方升级 (T5→T6), tier=minTier+1 时按
 * otherUpgrades 配方升级 (T6→T7)。同一方块实现两档重构, 节省建造成本。</p>
 *
 * <p>适配 v155.4:
 * <ul>
 *   <li>"unity-factory-out/in-N" → "create-factory-out/in-N" (mod 贴图前缀)</li>
 *   <li>ReconstructorBuild.upgrade(UnitType) 覆写点 v155.4 仍存在, 直接沿用</li>
 *   <li>Styles.togglet 不变</li>
 * </ul></p>
 */
public class SelectableReconstructor extends Reconstructor {
    /** 第二档 (tier+1) 的升级配方 */
    public Seq<UnitType[]> otherUpgrades = new Seq<>();
    /** 最低工作档位 (T6 工厂 = 6) */
    protected int minTier;

    public SelectableReconstructor(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        outRegion = Core.atlas.find("create-factory-out-" + size);
        inRegion = Core.atlas.find("create-factory-in-" + size);
    }

    @Override
    public void setStats() {
        // T6 档位统计
        stats.add(Stat.output, table -> {
            table.row();
            table.add("[accent]T" + minTier + " 档位升级:").left().padTop(10f);
            table.row();
            table.add("[lightgray]T5 → T6 升级路径:").left().padBottom(5f);
            table.row();
            upgrades.each(upgrade -> {
                if (upgrade[0].unlockedNow() && upgrade[1].unlockedNow()) {
                    Table upgradeRow = new Table();
                    upgradeRow.left();
                    
                    // 第一列：原单位
                    Table firstColumn = new Table();
                    firstColumn.left();
                    firstColumn.image(upgrade[0].uiIcon).size(60f).scaling(arc.util.Scaling.fit);
                    firstColumn.row();
                    firstColumn.add(upgrade[0].localizedName).color(arc.graphics.Color.white).left().fontScale(1.3f);
                    table.add(firstColumn).padLeft(15f).padBottom(8f);
                    
                    // 第二列：升级箭头
                    Table arrowColumn = new Table();
                    arrowColumn.left();
                    arrowColumn.add("⇨").color(arc.graphics.Color.yellow).size(32f);
                    table.add(arrowColumn).padBottom(8f);
                    
                    // 第三列：升级单位
                    Table secondColumn = new Table();
                    secondColumn.left();
                    secondColumn.image(upgrade[1].uiIcon).size(60f).scaling(arc.util.Scaling.fit);
                    secondColumn.row();
                    secondColumn.add(upgrade[1].localizedName).color(arc.graphics.Color.white).left().fontScale(1.3f);
                    table.add(secondColumn).padBottom(8f);
                    
                    table.row();
                }
            });
        });
        
        // T7 档位统计
        stats.add(Stat.output, table -> {
            table.row();
            table.add("[accent]T" + (minTier + 1) + " 档位升级:").left().padTop(10f);
            table.row();
            table.add("[lightgray]T6 → T7 升级路径:").left().padBottom(5f);
            table.row();
            otherUpgrades.each(upgrade -> {
                if (upgrade[0].unlockedNow() && upgrade[1].unlockedNow()) {
                    Table upgradeRow = new Table();
                    upgradeRow.left();
                    
                    // 第一列：原单位
                    Table firstColumn = new Table();
                    firstColumn.left();
                    firstColumn.image(upgrade[0].uiIcon).size(60f).scaling(arc.util.Scaling.fit);
                    firstColumn.row();
                    firstColumn.add(upgrade[0].localizedName).color(arc.graphics.Color.white).left().fontScale(1.3f);
                    table.add(firstColumn).padLeft(15f).padBottom(8f);
                    
                    // 第二列：升级箭头
                    Table arrowColumn = new Table();
                    arrowColumn.left();
                    arrowColumn.add("⇨").color(arc.graphics.Color.yellow).size(32f);
                    table.add(arrowColumn).padBottom(8f);
                    
                    // 第三列：升级单位
                    Table secondColumn = new Table();
                    secondColumn.left();
                    secondColumn.image(upgrade[1].uiIcon).size(60f).scaling(arc.util.Scaling.fit);
                    secondColumn.row();
                    secondColumn.add(upgrade[1].localizedName).color(arc.graphics.Color.white).left().fontScale(1.3f);
                    table.add(secondColumn).padBottom(8f);
                    
                    table.row();
                }
            });
        });
    }

    public class SelectableReconstructorBuild extends ReconstructorBuild {
        /** 当前档位 (minTier 或 minTier+1) */
        protected int tier = minTier;

        @Override
        public void buildConfiguration(Table table) {
            // 改进的档位切换按钮布局
            Table tierTable = new Table();
            tierTable.margin(4f);
            
            // T6 档位按钮
            tierTable.button("[accent]T" + minTier, Styles.togglet, () -> tier = minTier)
                .size(45f, 45f)
                .update(b -> {
                    b.setChecked(tier == minTier);
                    // 添加视觉反馈
                    if (tier == minTier) {
                        b.getStyle().over = Styles.flatOver;
                    }
                })
                .with(button -> {
                    button.getCells().first().pad(2f);
                });
            
            // 添加分隔符
            tierTable.add().padLeft(8f);
            
            // T7 档位按钮
            tierTable.button("[accent]T" + (minTier + 1), Styles.togglet, () -> tier = minTier + 1)
                .size(45f, 45f)
                .update(b -> {
                    b.setChecked(tier == minTier + 1);
                    // 添加视觉反馈
                    if (tier == minTier + 1) {
                        b.getStyle().over = Styles.flatOver;
                    }
                })
                .with(button -> {
                    button.getCells().first().pad(2f);
                });
            
            table.add(tierTable);
        }

        @Override
        public boolean acceptPayload(Building source, Payload payload) {
            if(!(this.payload == null
            && (this.enabled || source == this)
            && relativeTo(source) != rotation
            && payload instanceof UnitPayload pay)){
                return false;
            }

            UnitType upgrade = null;
            if (tier == minTier) {
                UnitType[] result = upgrades.find(u -> u[0] == pay.unit.type);
                upgrade = result != null ? result[1] : null;
            } else if (tier == minTier + 1) {
                UnitType[] result = otherUpgrades.find(u -> u[0] == pay.unit.type);
                upgrade = result != null ? result[1] : null;
            }

            if (upgrade != null) {
                if(!upgrade.unlockedNowHost() && !team.isAI()){
                    pay.showOverlay(Icon.tree);
                    arc.Events.fire(Trigger.cannotUpgrade);
                }

                if(upgrade.isBanned()){
                    pay.showOverlay(Icon.cancel);
                }
            }

            return upgrade != null && (team.isAI() || upgrade.unlockedNowHost()) && !upgrade.isBanned();
        }

        @Override
        public UnitType upgrade(UnitType type) {
            UnitType[] ret = null;
            if (tier == minTier) ret = upgrades.find(u -> u[0] == type);
            else if (tier == minTier + 1) ret = otherUpgrades.find(u -> u[0] == type);
            return ret == null ? null : ret[1];
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(tier);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            tier = read.b();
        }
    }
}