package zzw.content.blocks.distribution;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;

import static arc.Core.*;

/**
 * Teleporter 物品传送器 (移植自 PU_V8 unity.world.blocks.distribution.Teleporter)
 *
 * ★ 优化: 颜色频道从原版 8 个扩展到 12 个 (新增 cyan/magenta/olive/coral)
 * ★ 机制完全照搬原版:
 * - 按 [队伍 × 颜色] 二维分桶, 同色同队互相传送
 * - 接收物品时立即转送到目标传送器的 items 中
 * - 多个同色时按 entry 游标轮询, 避免重复命中同一个
 * - 仅在刚传送过物品 (duration > 0) 时耗电
 * - 点击切换颜色 (-1 关闭)
 *
 * v155.4 适配:
 * - consumes.powerCond -> consumePowerCond(PowerNode.condition)
 * - Team.baseTeams -> Team.all (包含 mod 自定义队)
 */
public class Teleporter extends Block {
    /**
     * 12 个颜色频道 (原版 8 个 + 新增 4 个)
     * 新增: cyan(青) / magenta(品红) / olive(橄榄) / coral(珊瑚)
     */
    protected static final Color[] selection = new Color[]{
        Color.royal,    // 0 皇家蓝
        Color.orange,   // 1 橙
        Color.scarlet,  // 2 猩红
        Color.forest,   // 3 森林绿
        Color.purple,   // 4 紫
        Color.gold,     // 5 金
        Color.pink,     // 6 粉
        Color.black,    // 7 黑
        Color.cyan,     // 8 青 (★ 新增)
        Color.magenta,  // 9 品红 (★ 新增)
        Color.olive,    // 10 橄榄 (★ 新增)
        Color.coral     // 11 珊瑚 (★ 新增)
    };

    /** [team.id][color_index] -> 同色同队的传送器集合 */
    protected static final ObjectSet<TeleporterBuild>[][] teleporters;

    static {
        @SuppressWarnings("unchecked")
        ObjectSet<TeleporterBuild>[][] tmp = new ObjectSet[Team.all.length][selection.length];
        teleporters = tmp;
        for (int i = 0; i < Team.all.length; i++) {
            for (int j = 0; j < selection.length; j++) teleporters[i][j] = new ObjectSet<>();
        }
        // 世界加载时清空所有桶 (避免跨存档残留)
        Events.on(WorldLoadEvent.class, e -> {
            for (int i = 0; i < teleporters.length; i++) {
                for (int j = 0; j < teleporters[i].length; j++) teleporters[i][j].clear();
            }
        });
    }

    public float powerUse = 2.5f;
    public TextureRegion blankRegion, topRegion;

    public Teleporter(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true;
        saveConfig = true;
        unloadable = false;
        hasItems = true;
        config(Integer.class, (TeleporterBuild build, Integer value) -> {
            if (value < -1 || value >= selection.length) return;
            if (build.toggle != -1) teleporters[build.team.id][build.toggle].remove(build);
            if (value != -1) teleporters[build.team.id][value].add(build);
            build.toggle = value;
        });
        configClear((TeleporterBuild build) -> {
            if (build.toggle != -1) teleporters[build.team.id][build.toggle].remove(build);
            build.toggle = -1;
        });
    }

    @Override
    public boolean outputsItems() {
        return true;
    }

    @Override
    public void init() {
        // v155.4: consumePowerCond(condition, amount) 用法
        consumePowerCond(powerUse, (TeleporterBuild b) -> b.isConsuming());
        super.init();
    }

    @Override
    public void load() {
        super.load();
        blankRegion = atlas.find(name + "-blank");
        topRegion = atlas.find(name + "-top");
    }

    @Override
    public void drawPlanConfig(BuildPlan req, Eachable<BuildPlan> list) {
        drawPlanConfigCenter(req, req.config, "nothing", false);
    }

    @Override
    public void drawPlanConfigCenter(BuildPlan req, Object content, String region, boolean cross) {
        if (!(content instanceof Integer temp)) return;
        if (temp < 0 || temp >= selection.length) return;
        Draw.color(selection[temp]);
        Draw.rect(blankRegion, req.drawx(), req.drawy());
        Draw.color();
    }

    public class TeleporterBuild extends Building {
        protected int toggle = -1, entry;
        protected float duration;
        protected TeleporterBuild target;
        protected Team previousTeam;

        protected void onDuration() {
            if (duration < 0f) duration = 0f;
            else duration -= Time.delta;
        }

        protected boolean isConsuming() {
            return duration > 0f;
        }

        protected boolean isTeamChanged() {
            return previousTeam != team;
        }

        @Override
        public void draw() {
            super.draw();
            if (toggle != -1) {
                Draw.color(selection[toggle]);
                Draw.rect(blankRegion, x, y);
            }
            Draw.color(Color.white);
            Draw.alpha(0.45f + Mathf.absin(7f, 0.26f));
            Draw.rect(topRegion, x, y);
            Draw.reset();
        }

        @Override
        public void updateTile() {
            onDuration();
            if (items.any()) dump();
            if (isTeamChanged() && toggle != -1) {
                teleporters[team.id][toggle].add(this);
                if (previousTeam != null && previousTeam.id < teleporters.length) {
                    teleporters[previousTeam.id][toggle].remove(this);
                }
                previousTeam = team;
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            final ButtonGroup<Button> group = new ButtonGroup<>();
            group.setMinCheckCount(0);
            for (int i = 0; i < selection.length; i++) {
                int j = i;
                // v155.4: Styles.clearToggleTransi -> Styles.clearTogglei
                ImageButton button = table.button(Tex.whiteui, Styles.clearTogglei, 24f, () -> {}).size(34f).group(group).get();
                button.changed(() -> configure(button.isChecked() ? j : -1));
                button.getStyle().imageUpColor = selection[j];
                button.update(() -> button.setChecked(toggle == j));
                if (i % 4 == 3) table.row();
            }
        }

        protected TeleporterBuild findLink(int value) {
            ObjectSet<TeleporterBuild> teles = teleporters[team.id][value];
            Seq<TeleporterBuild> entries = teles.toSeq();
            if (entries.isEmpty()) return null;
            if (entry >= entries.size) entry = 0;
            for (int i = entry, len = entries.size; i < len; i++) {
                TeleporterBuild other = entries.get(i);
                if (other != this) {
                    entry = i + 1;
                    return other;
                }
            }
            // 回绕一遍
            for (int i = 0; i < entry; i++) {
                TeleporterBuild other = entries.get(i);
                if (other != this) {
                    entry = i + 1;
                    return other;
                }
            }
            return null;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (toggle == -1) return false;
            target = findLink(toggle);
            if (target == null) return false;
            // v155.4: consValid() -> canConsume(), efficiency() -> efficiency (字段)
            return source != this && canConsume() && Mathf.zero(1 - efficiency) && target.items.total() < target.getMaximumAccepted(item);
        }

        @Override
        public void handleItem(Building source, Item item) {
            target.items.add(item, 1);
            duration = 0f;
        }

        @Override
        public void created() {
            if (toggle != -1) teleporters[team.id][toggle].add(this);
            previousTeam = team;
        }

        @Override
        public void onRemoved() {
            if (toggle != -1) {
                if (isTeamChanged() && previousTeam != null && previousTeam.id < teleporters.length) {
                    teleporters[previousTeam.id][toggle].remove(this);
                } else {
                    teleporters[team.id][toggle].remove(this);
                }
            }
        }

        @Override
        public Integer config() {
            return toggle;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(toggle);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            toggle = read.b();
        }
    }
}
