package zzw.content.blocks.units;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.meta.Stat;

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
        stats.add(Stat.output, table -> {
            table.row();
            table.add("[accent]T" + minTier);
        });
        super.setStats();
        stats.add(Stat.output, table -> {
            float size = 8f * 3f;
            table.row();
            table.add("[accent]T" + (minTier + 1)).row();
            otherUpgrades.each(upgrade -> {
                if (upgrade[0].unlockedNow() && upgrade[1].unlockedNow()) {
                    table.image(upgrade[0].uiIcon).size(size).padRight(4f).padLeft(10f).scaling(arc.util.Scaling.fit).right();
                    table.add(upgrade[0].localizedName).left();
                    table.add("[lightgray] -> ");
                    table.image(upgrade[1].uiIcon).size(size).padRight(4f).scaling(arc.util.Scaling.fit);
                    table.add(upgrade[1].localizedName).left();
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
            table.button("T" + minTier, Styles.togglet, () -> tier = minTier)
                .width(50f).height(50f)
                .update(b -> b.setChecked(tier == minTier));

            table.button("T" + (minTier + 1), Styles.togglet, () -> tier = minTier + 1)
                .width(50f).height(50f)
                .update(b -> b.setChecked(tier == minTier + 1));
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