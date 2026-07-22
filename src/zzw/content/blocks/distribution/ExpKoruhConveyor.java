package zzw.content.blocks.distribution;

import arc.*;
import arc.audio.*;
import arc.func.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.meta.*;
import zzw.content.exp.*;

import static mindustry.Vars.ui;

/**
 * ExpKoruhConveyor 迪里姆合金传送带 (完美移植自 PU_V8 ExpKoruhConveyor)
 *
 * PU_V8 中此类由 @Dupe 注解处理器自动生成 (ExpTurret + KoruhConveyor 合并)
 * zzw 项目无注解处理器, 这里手动抄写全部字段和方法
 *
 * 机制:
 * - 继承 KoruhConveyor 的传送带行为 (含 absorbLasers=true 内部标志)
 * - 完整 ExpTurret 经验系统: 等级/exp/handleExp/handleOrb/handleTower/incExp/levelup
 * - 伤害减免 damageReduction (按等级)
 * - 死亡散落 30% 经验球
 * - draw 使用 DrawLevel (可选)
 * - pregrade: 可从低级传送带升级而来
 *
 * 注册: dirium-conveyor (迪里姆合金传送带 / Pulse Conveyor)
 * 参考: PU_V8 UnityBlocks.java L149-151 + ExpTurret.java
 */
public class ExpKoruhConveyor extends KoruhConveyor {
    // ===== ExpTurret 字段完全照搬 =====
    public int maxLevel = 10;
    public int maxExp;
    public EField<?>[] expFields;
    public boolean passive = false;
    public boolean updateExpFields = true;

    public @Nullable ExpKoruhConveyor pregrade = null;
    public int pregradeLevel = -1;

    public float orbScale = 0.8f;
    public int expScale = 1;
    public Effect upgradeEffect = UnityFx.expPoof;
    public Effect upgradeBlockEffect = UnityFx.expShineRegion;
    public Sound upgradeSound = Sounds.uiNotify;
    public Color fromColor = Pal.lancerLaser;
    public Color toColor = UnityPal.exp;
    public Color[] effectColors;

    protected @Nullable EField<Float> rangeField = null;
    protected float rangeStart, rangeEnd;
    private final Seq<Building> seqs = new Seq<>();

    public EField<Float> damageReduction;
    public @Nullable DrawLevel draw = null;

    public ExpKoruhConveyor(String name) {
        super(name);
    }

    @Override
    public void init() {
        super.init();
        if (expFields == null) expFields = new EField<?>[]{};
        maxExp = requiredExp(maxLevel);
        if (expLevel(maxExp) < maxLevel) maxExp++;

        for (EField<?> f : expFields) {
            if (f.stat == Stat.shootRange || f.stat == Stat.range) {
                rangeField = (EField<Float>) f;
                break;
            }
        }
        if (rangeField == null) {
            // Conveyor 无 range 字段, 用 0 占位
            rangeStart = rangeEnd = 0f;
        } else {
            rangeEnd = rangeField.fromLevel(maxLevel);
            rangeStart = rangeField.fromLevel(0);
        }
        setEFields(0);

        if (pregrade != null && pregradeLevel < 0) pregradeLevel = pregrade.maxLevel;
        if (damageReduction == null)
            damageReduction = new EField.EExpoZero(f -> {}, 0.1f,
                Mathf.pow(4f + size, 1f / maxLevel), true, null,
                v -> Strings.autoFixed(Mathf.roundPositive(v * 10000) / 100f, 2) + "%");
    }

    @Override
    public void load() {
        super.load();
        if (draw != null) draw.load(this);
    }

    @Override
    public void checkStats() {
        if (!stats.intialized) {
            setStats();
            addExpStats();
            stats.intialized = true;
        }
    }

    public void addExpStats() {
        var map = stats.toMap();
        boolean removeAbil = false;
        for (EField<?> f : expFields) {
            if (f.stat == null) continue;
            if (map.containsKey(f.stat.category) && map.get(f.stat.category).containsKey(f.stat)) {
                if (f.stat == Stat.abilities) {
                    if (!removeAbil) {
                        stats.remove(f.stat);
                        removeAbil = true;
                    }
                } else {
                    stats.remove(f.stat);
                }
            }
            if (f.hasTable) {
                stats.add(f.stat, t -> buildGraphTable(t, f));
            } else stats.add(f.stat, f.toString());
        }

        if (pregrade != null) {
            stats.add(Stat.buildCost, "[#84ff00]" + Iconc.up + Core.bundle.format("exp.upgradefrom", pregradeLevel, pregrade.localizedName) + "[]");
            stats.add(Stat.buildCost, t -> {
                t.button(Icon.infoCircleSmall, Styles.cleari, 20f, () -> ui.content.show(pregrade)).size(26).color(UnityPal.exp);
            });
        }

        stats.add(Stat.itemCapacity, "@", Core.bundle.format("exp.expAmount", maxExp));
        stats.add(Stat.itemCapacity, t -> {
            t.add(Core.bundle.format(passive ? "exp.lvlAmountP" : "exp.lvlAmount", maxLevel)).tooltip(Core.bundle.get("exp.tooltip"));
        });
        stats.add(Stat.armor, t -> buildGraphTable(t, damageReduction));
    }

    protected void buildGraphTable(Table t, EField<?> f) {
        Label l = t.add(f.toString()).get();
        Collapser c = new Collapser(tc -> {
            f.buildTable(tc, maxLevel);
        }, true);

        Runnable toggle = () -> c.toggle(false);
        l.clicked(toggle);
        t.button(Icon.downOpenSmall, Styles.clearTogglei, 20f, toggle).size(26f).color(UnityPal.exp).padLeft(8);
        t.row();
        t.add(c).colspan(2).left();
    }

    @Override
    public void setBars() {
        super.setBars();
        removeBar("health");
        // 添加 exp 条
        addBar("exp", (ExpKoruhConveyorBuild e) -> new Bar(() -> Core.bundle.get("bar.exp"), () -> UnityPal.exp, e::expf));
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        drawPotentialLinks(x, y);
        if (!valid && pregrade != null)
            drawPlaceText(Core.bundle.format("exp.pregrade", pregradeLevel, pregrade.localizedName), x, y, false);
    }

    @Override
    public boolean canReplace(Block other) {
        return super.canReplace(other) || (pregrade != null && other == pregrade);
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation) {
        if (tile == null) return false;
        if (pregrade == null) return super.canPlaceOn(tile, team, rotation);

        mindustry.world.blocks.storage.CoreBlock.CoreBuild core = team.core();
        if (core == null || (!mindustry.Vars.state.rules.infiniteResources && !core.items.has(requirements, mindustry.Vars.state.rules.buildCostMultiplier)))
            return false;

        seqs.clear();
        tile.getLinkedTilesAs(this, inside -> {
            if (inside.build == null || seqs.contains(inside.build) || seqs.size > 1) return;
            if (inside.block() == pregrade && ((ExpKoruhConveyorBuild) inside.build).level() >= pregradeLevel)
                seqs.add(inside.build);
        });
        return seqs.size == 1;
    }

    @Override
    public void placeBegan(Tile tile, Block previous) {
        if (pregrade != null && previous == pregrade) {
            tile.setBlock(this, tile.team());
            UnityFx.placeShine.at(tile.drawx(), tile.drawy(), tile.block().size * mindustry.Vars.tilesize, UnityPal.exp);
            Fx.upgradeCore.at(tile, tile.block().size);
        } else super.placeBegan(tile, previous);
    }

    public int expLevel(int e) {
        return Math.min(maxLevel, (int) (Mathf.sqrt(e / (5f * expScale))));
    }

    public float expCap(int l) {
        if (l < 0) return 0f;
        if (l > maxLevel) l = maxLevel;
        return requiredExp(l + 1);
    }

    public int requiredExp(int l) {
        return l * l * 5 * expScale;
    }

    public void setEFields(int l) {
        for (EField<?> f : expFields) {
            f.setLevel(l);
        }
    }

    public class ExpKoruhConveyorBuild extends KoruhConveyorBuild implements ExpHolder, LevelHolder {
        public int exp;
        public @Nullable ExpHub.ExpHubBuild hub = null;

        public int incExp(int amount, boolean hubTake) {
            int ehub = (hubTake && hubValid()) ? this.hub.takeAmount(amount, this) : 0;
            int e = Math.min(amount - ehub, maxExp - exp);
            if (e == 0) return 0;
            int before = level();
            exp += e;
            int after = level();
            if (exp > maxExp) exp = maxExp;
            if (exp < 0) exp = 0;
            if (after > before) levelup();
            return e;
        }

        @Override
        public int getExp() {
            return exp;
        }

        @Override
        public int handleExp(int amount) {
            return incExp(amount, true);
        }

        @Override
        public int unloadExp(int amount) {
            if (passive) return 0;
            int e = Math.min(amount, exp);
            exp -= e;
            return e;
        }

        @Override
        public boolean acceptOrb() {
            return !passive && exp < maxExp;
        }

        @Override
        public boolean handleOrb(int orbExp) {
            int a = (int) (orbScale * orbExp);
            if (a < 1) return false;
            incExp(a, false);
            return true;
        }

        @Override
        public int handleTower(int amount, float angle) {
            if (passive) return 0;
            return incExp(amount, false);
        }

        @Override
        public int level() {
            return expLevel(exp);
        }

        @Override
        public int maxLevel() {
            return maxLevel;
        }

        public float expf() {
            int lv = level();
            if (lv >= maxLevel) return 1f;
            float lb = expCap(lv - 1);
            float lc = expCap(lv);
            return ((float) exp - lb) / (lc - lb);
        }

        @Override
        public float levelf() {
            return level() / (float) maxLevel;
        }

        public void levelup() {
            upgradeSound.at(this);
            upgradeEffect.at(this);
            if (upgradeBlockEffect != Fx.none)
                upgradeBlockEffect.at(x, y, rotation - 90, Color.white, region);
        }

        public Color shootColor(Color tmp) {
            return tmp.set(fromColor).lerp(toColor, exp / (float) maxExp);
        }

        public Color effectColor() {
            if (effectColors == null) return Color.white;
            return effectColors[Math.min((int) (levelf() * effectColors.length), effectColors.length - 1)];
        }

        @Override
        public void update() {
            if (updateExpFields) setEFields(level());
            super.update();
        }

        @Override
        public void draw() {
            // ★ 先调用 KoruhConveyor.draw (含 drawMultiplier 动画)
            super.draw();
            // 等级贴图绘制 (可选)
            if (draw != null) draw.draw(this);
        }

        @Override
        public void drawLight() {
            if (draw != null) draw.drawLight(this);
            super.drawLight();
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            drawPlaceText(exp + "/" + maxExp, tile.x, tile.y, exp > 0);
        }

        @Override
        public void displayBars(Table table) {
            table.table(this::buildHBar).pad(0).growX().padTop(8).padBottom(4);
            table.row();

            super.displayBars(table);

            table.table(t -> {
                t.defaults().height(18f).pad(4);
                t.label(() -> "Lv " + level()).color(passive ? UnityPal.passive : Pal.accent).width(65f);
                t.add(new Bar(() -> level() >= maxLevel ? "MAX" : Core.bundle.format("bar.expp", (int) (expf() * 100f)),
                    () -> UnityPal.exp, this::expf)).growX();
            }).pad(0).growX().padTop(4).padBottom(4);
            table.row();
        }

        protected void buildHBar(Table t) {
            t.clearChildren();
            t.defaults().height(18f).pad(4);
            final int l = level();
            if (damageReduction.fromLevel(level()) >= 0.01f) {
                Image ii = new Image(Icon.defense, Pal.health);
                ii.setSize(14f);
                Label ll = new Label(() -> Mathf.roundPositive(damageReduction.fromLevel(level()) * 100) + "");
                ll.setStyle(new Label.LabelStyle(Styles.outlineLabel));
                ll.setSize(26f, 18f);
                ll.setAlignment(Align.center);
                t.stack(ii, ll).size(26f, 18f).pad(4).padRight(8).center();
            } else t.update(() -> {
                if (level() != l) buildHBar(t);
            });
            t.add(new Bar("stat.health", Pal.health, this::healthf).blink(Color.white)).growX();
        }

        @Override
        public void killed() {
            // 死亡散落 30% 经验球 (与 ExpTurret 一致)
            ExpOrbs.spreadExp(x, y, exp * 0.3f, 3f * size);
            super.killed();
        }

        @Override
        public float handleDamage(float amount) {
            return super.handleDamage(amount) * Mathf.clamp(1f - damageReduction.fromLevel(level()));
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(exp);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            exp = read.i();
            if (exp > maxExp) exp = maxExp;
        }

        // ===== hub 方法 (ExpHolder) =====
        @Override
        public boolean hubbable() {
            return !passive;
        }

        @Override
        public boolean canHub(Building build) {
            return !hubValid() || (build != null && build == hub);
        }

        @Override
        public void setHub(ExpHub.ExpHubBuild hub) {
            this.hub = hub;
        }

        public boolean hubValid() {
            boolean val = hub != null && hub.isValid() && !hub.dead && hub.links.contains(pos());
            if (!val) hub = null;
            return val;
        }
    }
}
