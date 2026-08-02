package zzw.content.blocks.distribution;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.Element;
import arc.scene.event.*;
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
 * - 12 个颜色频道 (原版 8 + 新增 4)
 * - 自定义信号区: 信号名 + 批注, 鼠标悬停显示批注
 * - 两个标签页: 颜色快捷区 (默认) / 自定义信号区
 * - 按 [队伍 x 频道] 分桶, 同频道同队互相传送
 * - 仅在刚传送过物品 (duration > 0) 时耗电
 */
public class Teleporter extends Block {
    /** 12 个颜色频道 (原版 8 个 + 新增 4 个) */
    protected static final Color[] selection = new Color[]{
        Color.royal,    // 0 皇家蓝
        Color.orange,   // 1 橙
        Color.scarlet,  // 2 猩红
        Color.forest,   // 3 森林绿
        Color.purple,   // 4 紫
        Color.gold,     // 5 金
        Color.pink,     // 6 粉
        Color.black,    // 7 黑
        Color.cyan,     // 8 青 (新增)
        Color.magenta,  // 9 品红 (新增)
        Color.olive,    // 10 橄榄 (新增)
        Color.coral     // 11 珊瑚 (新增)
    };

    /** 颜色频道: [team.id][color_index] -> 同色同队的传送器集合 */
    protected static final ObjectSet<TeleporterBuild>[][] teleporters;

    /** 自定义信号: [team.id] -> ObjectMap<信号名, SignalInfo> */
    protected static final ObjectMap<String, SignalInfo>[] customSignals;

    /** 全局信号列表 (所有队伍共享, 用于面板显示) */
    protected static final Seq<SignalEntry> allSignals = new Seq<>();

    static {
        @SuppressWarnings("unchecked")
        ObjectSet<TeleporterBuild>[][] tmp = new ObjectSet[Team.all.length][selection.length];
        teleporters = tmp;
        @SuppressWarnings("unchecked")
        ObjectMap<String, SignalInfo>[] customTmp = new ObjectMap[Team.all.length];
        customSignals = customTmp;
        for (int i = 0; i < Team.all.length; i++) {
            for (int j = 0; j < selection.length; j++) teleporters[i][j] = new ObjectSet<>();
            customSignals[i] = new ObjectMap<>();
        }
        Events.on(WorldLoadEvent.class, e -> {
            for (int i = 0; i < teleporters.length; i++) {
                for (int j = 0; j < teleporters[i].length; j++) teleporters[i][j].clear();
                customSignals[i].clear();
            }
            allSignals.clear();
        });
    }

    /** 自定义信号信息 */
    public static class SignalInfo {
        public ObjectSet<TeleporterBuild> members = new ObjectSet<>();
        public String note = "";
    }

    /** 全局信号条目 (用于面板显示) */
    public static class SignalEntry {
        public String name;
        public String note;
        public int teamId;
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
        // 颜色频道配置 (0-11)
        config(Integer.class, (TeleporterBuild build, Integer value) -> {
            if (value < -1 || value >= selection.length) return;
            // 切换到颜色频道前, 先退出自定义信号
            if (build.customSignal != null) {
                SignalInfo info = customSignals[build.team.id].get(build.customSignal);
                if (info != null) info.members.remove(build);
                build.customSignal = null;
            }
            if (build.toggle != -1) teleporters[build.team.id][build.toggle].remove(build);
            if (value != -1) teleporters[build.team.id][value].add(build);
            build.toggle = value;
        });
        // 自定义信号配置 (格式: "信号名|批注")
        config(String.class, (TeleporterBuild build, String value) -> {
            if (value == null || value.isEmpty()) return;
            // 解析 "信号名|批注"
            String[] parts = value.split("\\|", 2);
            String signalName = parts[0].trim();
            String note = parts.length > 1 ? parts[1].trim() : "";
            if (signalName.isEmpty()) return;

            // 切换到自定义信号前, 先退出颜色频道
            if (build.toggle != -1) {
                teleporters[build.team.id][build.toggle].remove(build);
                build.toggle = -1;
            }
            // 退出旧的自定义信号
            if (build.customSignal != null && !build.customSignal.equals(signalName)) {
                SignalInfo oldInfo = customSignals[build.team.id].get(build.customSignal);
                if (oldInfo != null) oldInfo.members.remove(build);
            }
            // 加入新的自定义信号
            build.customSignal = signalName;
            SignalInfo info = customSignals[build.team.id].get(signalName);
            if (info == null) {
                info = new SignalInfo();
                customSignals[build.team.id].put(signalName, info);
                // 添加到全局列表
                SignalEntry entry = new SignalEntry();
                entry.name = signalName;
                entry.note = note;
                entry.teamId = build.team.id;
                allSignals.add(entry);
            }
            // 更新批注
            if (!note.isEmpty()) info.note = note;
            // 同步全局列表中的批注
            for (SignalEntry e : allSignals) {
                if (e.name.equals(signalName) && e.teamId == build.team.id) {
                    if (!note.isEmpty()) e.note = note;
                    break;
                }
            }
            info.members.add(build);
        });
        configClear((TeleporterBuild build) -> {
            if (build.toggle != -1) teleporters[build.team.id][build.toggle].remove(build);
            if (build.customSignal != null) {
                SignalInfo info = customSignals[build.team.id].get(build.customSignal);
                if (info != null) info.members.remove(build);
                build.customSignal = null;
            }
            build.toggle = -1;
        });
    }

    @Override
    public boolean outputsItems() {
        return true;
    }

    @Override
    public void init() {
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
        if (content instanceof Integer temp) {
            if (temp < 0 || temp >= selection.length) return;
            Draw.color(selection[temp]);
            Draw.rect(blankRegion, req.drawx(), req.drawy());
            Draw.color();
        } else if (content instanceof String name && !name.isEmpty()) {
            Draw.color(Color.white);
            Draw.rect(blankRegion, req.drawx(), req.drawy());
            Draw.color();
        }
    }

    public class TeleporterBuild extends Building {
        protected int toggle = -1, entry;
        protected float duration;
        protected TeleporterBuild target;
        protected Team previousTeam;
        /** 自定义信号名称 (null 表示未使用) */
        protected String customSignal;

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
            } else if (customSignal != null) {
                Draw.color(Color.white);
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
            if (isTeamChanged()) {
                if (toggle != -1) {
                    teleporters[team.id][toggle].add(this);
                    if (previousTeam != null && previousTeam.id < teleporters.length) {
                        teleporters[previousTeam.id][toggle].remove(this);
                    }
                }
                if (customSignal != null) {
                    SignalInfo info = customSignals[team.id].get(customSignal);
                    if (info != null) info.members.add(this);
                    if (previousTeam != null && previousTeam.id < customSignals.length) {
                        SignalInfo oldInfo = customSignals[previousTeam.id].get(customSignal);
                        if (oldInfo != null) oldInfo.members.remove(this);
                    }
                }
                previousTeam = team;
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();

            // ===== 标签页按钮 =====
            Table tabBtns = new Table();
            tabBtns.defaults().pad(2f);
            Button colorBtn = tabBtns.button("颜色频道", Styles.flatTogglet, () -> {}).size(110f, 34f).get();
            Button signalBtn = tabBtns.button("自定义信号", Styles.flatTogglet, () -> {}).size(110f, 34f).padLeft(6f).get();

            // 内容容器
            Table content = new Table();

            colorBtn.clicked(() -> {
                colorBtn.setChecked(true);
                signalBtn.setChecked(false);
                rebuildColorTab(content);
            });
            signalBtn.clicked(() -> {
                signalBtn.setChecked(true);
                colorBtn.setChecked(false);
                rebuildSignalTab(content);
            });

            table.add(tabBtns).padBottom(4f).row();
            table.add(content).growX();

            // 默认显示颜色频道
            colorBtn.setChecked(true);
            rebuildColorTab(content);
        }

        /** 重建颜色频道标签页 */
        private void rebuildColorTab(Table content) {
            content.clear();
            content.table(Tex.pane, t -> {
                t.add("颜色频道选择").pad(4f).row();
                for (int i = 0; i < selection.length; i++) {
                    int j = i;
                    ImageButton button = t.button(Tex.whiteui, Styles.clearTogglei, 24f, () -> {}).size(36f).get();
                    button.changed(() -> configure(button.isChecked() ? j : -1));
                    button.getStyle().imageUpColor = selection[j];
                    button.update(() -> button.setChecked(toggle == j));
                    if (i % 4 == 3) t.row();
                }
                t.row();
                t.button("取消选择", Styles.flatt, () -> configure(-1)).size(100f, 28f).padTop(6f);
            }).growX().pad(4f);
        }

        /** 重建自定义信号标签页 */
        private void rebuildSignalTab(Table content) {
            content.clear();
            content.table(Tex.pane, t -> {
                t.add("自定义信号").pad(4f).row();

                // 输入区: 信号名 + 批注
                final TextField[] fields = new TextField[2];
                t.table(input -> {
                    input.left().defaults().pad(2f);
                    input.add("信号:");
                    fields[0] = input.field("", text -> {}).width(130f).get();
                    fields[0].setMessageText("支持中文");
                    input.add("批注:").padLeft(8f);
                    fields[1] = input.field("", text -> {}).width(130f).get();
                    fields[1].setMessageText("可选");
                }).growX().pad(4f).row();

                // 添加按钮
                t.button("添加信号", Styles.flatt, () -> {
                    String name = fields[0].getText().trim();
                    String note = fields[1].getText().trim();
                    if (!name.isEmpty()) {
                        configure(name + "|" + note);
                        fields[0].clearText();
                        fields[1].clearText();
                        // 刷新信号列表
                        Table listTable = t.find("signalList");
                        if (listTable != null) fillSignalList(listTable);
                    }
                }).size(120f, 32f).padTop(4f).row();

                // 分隔线
                t.image().height(4f).color(Color.gray).growX().padTop(6f).padBottom(4f).row();
                t.add("可用信号:").left().padBottom(4f).row();

                // 信号列表容器
                Table listTable = new Table();
                listTable.name = "signalList";
                t.add(listTable).growX();

                fillSignalList(listTable);
            }).growX().pad(4f);
        }

        /** 填充信号列表 (场上所有自定义信号, 单选, 可删除) */
        private void fillSignalList(Table listTable) {
            listTable.clear();

            if (allSignals.isEmpty()) {
                listTable.add("[gray]暂无自定义信号").pad(4f);
                return;
            }

            // ButtonGroup 保证单选 (minCheckCount=0 允许取消选择)
            ButtonGroup<Button> group = new ButtonGroup<>();
            group.setMinCheckCount(0);
            group.setMaxCheckCount(1);

            // 遍历副本, 避免删除时 ConcurrentModificationException
            for (SignalEntry entry : allSignals.copy()) {
                final String signalName = entry.name;
                final String signalNote = entry.note;
                final int entryTeamId = entry.teamId;

                Table signalRow = new Table();
                signalRow.defaults().pad(2f);

                // 信号按钮 (选择该信号)
                final Button[] holder = new Button[1];
                holder[0] = signalRow.button(signalName, Styles.flatTogglet, () -> {
                    if (holder[0].isChecked()) {
                        configure(signalName + "|" + signalNote);
                    } else {
                        configure(-1);
                    }
                }).size(150f, 30f).get();
                Button btn = holder[0];
                group.add(btn);
                btn.setChecked(customSignal != null && customSignal.equals(signalName));

                // 鼠标悬停显示批注 tooltip
                String noteText = signalNote.isEmpty() ? "[gray](无批注)" : signalNote;
                addTooltip(btn, "[accent]信号: [white]" + signalName + "\n[accent]批注: [white]" + noteText);

                // 批注预览 (简短)
                if (!signalNote.isEmpty()) {
                    String preview = signalNote.length() > 12 ? signalNote.substring(0, 12) + "..." : signalNote;
                    signalRow.label(() -> "[gray]" + preview).padLeft(6f).growX();
                }

                // 删除按钮
                signalRow.button("×", Styles.flatt, () -> {
                    deleteSignal(signalName, entryTeamId);
                    fillSignalList(listTable);
                }).size(30f, 30f).padLeft(4f).get();

                listTable.add(signalRow).growX().row();
            }
        }

        /** 删除自定义信号 (移除全局列表 + 队伍桶中的信号, 并重置使用该信号的传送器) */
        private void deleteSignal(String signalName, int teamId) {
            allSignals.removeAll(e -> e.name.equals(signalName) && e.teamId == teamId);
            if (teamId < customSignals.length) {
                SignalInfo info = customSignals[teamId].remove(signalName);
                if (info != null) {
                    for (TeleporterBuild member : info.members) {
                        member.customSignal = null;
                    }
                    info.members.clear();
                }
            }
        }

        /** 给元素添加 tooltip (鼠标悬停显示, 自动跟随鼠标) */
        private void addTooltip(Element element, String text) {
            Tooltip tooltip = new Tooltip(t -> {
                t.background(Tex.pane);
                t.margin(6f);
                t.label(() -> text).left();
            });
            element.addListener(tooltip);
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
            for (int i = 0; i < entry; i++) {
                TeleporterBuild other = entries.get(i);
                if (other != this) {
                    entry = i + 1;
                    return other;
                }
            }
            return null;
        }

        /** 在自定义信号中查找目标 */
        protected TeleporterBuild findLinkCustom(String signal) {
            SignalInfo info = customSignals[team.id].get(signal);
            if (info == null) return null;
            Seq<TeleporterBuild> entries = info.members.toSeq();
            if (entries.isEmpty()) return null;
            if (entry >= entries.size) entry = 0;
            for (int i = entry, len = entries.size; i < len; i++) {
                TeleporterBuild other = entries.get(i);
                if (other != this) {
                    entry = i + 1;
                    return other;
                }
            }
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
            if (toggle == -1 && customSignal == null) return false;
            if (toggle != -1) {
                target = findLink(toggle);
            } else {
                target = findLinkCustom(customSignal);
            }
            if (target == null) return false;
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
            if (customSignal != null) {
                SignalInfo info = customSignals[team.id].get(customSignal);
                if (info == null) {
                    info = new SignalInfo();
                    customSignals[team.id].put(customSignal, info);
                }
                info.members.add(this);
            }
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
            if (customSignal != null) {
                int teamId = (isTeamChanged() && previousTeam != null && previousTeam.id < customSignals.length) ? previousTeam.id : team.id;
                SignalInfo info = customSignals[teamId].get(customSignal);
                if (info != null) info.members.remove(this);
            }
        }

        @Override
        public Object config() {
            if (customSignal != null) return customSignal;
            return toggle;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(toggle);
            write.str(customSignal != null ? customSignal : "");
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            toggle = read.b();
            String sig = read.str();
            customSignal = sig.isEmpty() ? null : sig;
        }
    }
}