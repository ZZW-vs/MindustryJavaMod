package zzw.content.blocks;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.Collapser;
import mindustry.gen.Icon;
import arc.util.Nullable;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import zzw.content.exp.EField;
import zzw.content.exp.ExpHub;
import zzw.content.exp.ExpHolder;
import zzw.content.exp.ExpOrbs;
import zzw.content.exp.LevelHolder;
import zzw.content.exp.UnityFx;
import zzw.content.exp.UnityPal;

import static mindustry.Vars.*;

/**
 * ExpLimitWall (手动实现 PU_V8 @Dupe(base=ExpTurret, parent=LimitWall) 的生成结果)
 *
 * = LimitWall (限伤/闪烁) + ExpTurret (经验等级/减伤)
 * - maxLevel/expFields/expLevel/requiredExp/setEFields: 经验系统
 * - damageReduction: 等级越高减伤越强 (EExpoZero)
 * - handleExp/level/levelf/expf: 经验积累与等级提升
 * - killed: 死亡掉落经验球
 * - displayBars: 显示等级和经验条
 */
public class ExpLimitWall extends LimitWall {
    public int maxLevel = 10;
    public int maxExp;
    public EField<?>[] expFields;
    public boolean passive = false;
    public boolean updateExpFields = true;

    public float orbScale = 0.8f;
    public int expScale = 1;
    public Effect upgradeEffect = UnityFx.expPoof, upgradeBlockEffect = UnityFx.expShineRegion;
    public Sound upgradeSound = Sounds.uiNotify;
    public Color fromColor = Pal.lancerLaser, toColor = UnityPal.exp;
    public Color[] effectColors;

    public EField<Float> damageReduction;

    public ExpLimitWall(String name) {
        super(name);
    }

    @Override
    public void init() {
        super.init();
        if (expFields == null) expFields = new EField<?>[]{};
        maxExp = requiredExp(maxLevel);
        if (expLevel(maxExp) < maxLevel) maxExp++;
        setEFields(0);

        if (damageReduction == null) {
            damageReduction = new EField.EExpoZero(f -> {}, 0.1f, Mathf.pow(4f + size, 1f / maxLevel), true, null,
                v -> Strings.autoFixed(Mathf.roundPositive(v * 10000) / 100f, 2) + "%");
        }
    }

    /**
     * ★ 重写 checkStats: 在 setStats() 之后追加经验统计
     * (与 ExpTurret.checkStats 一致, 复刻 PU_V8 @Dupe 生成逻辑)
     */
    @Override
    public void checkStats() {
        if (!stats.intialized) {
            setStats();
            addExpStats();
            stats.intialized = true;
        }
    }

    /**
     * 添加经验相关统计到信息面板 (参考 ExpTurret.addExpStats)
     * 显示: 最大经验值, 最大等级 (主动/被动), 伤害减免曲线 (按等级)
     */
    public void addExpStats() {
        var map = stats.toMap();
        boolean removeAbil = false;
        // 遍历 expFields, 移除旧 stat 后重新添加 (支持表格/字符串两种形式)
        for (EField<?> f : expFields) {
            if (f.stat == null) continue;
            if (map.containsKey(f.stat.category) && map.get(f.stat.category).containsKey(f.stat)) {
                if (f.stat == Stat.abilities) {
                    if (!removeAbil) { stats.remove(f.stat); removeAbil = true; }
                } else {
                    stats.remove(f.stat);
                }
            }
            if (f.hasTable) {
                stats.add(f.stat, t -> buildGraphTable(t, f));
            } else {
                stats.add(f.stat, f.toString());
            }
        }

        // 最大经验值
        stats.add(Stat.itemCapacity, "@", Core.bundle.format("exp.expAmount", maxExp));

        // 最大等级 (主动/被动分别用 exp.lvlAmount / exp.lvlAmountP)
        stats.add(Stat.itemCapacity, t -> {
            t.add(Core.bundle.format(passive ? "exp.lvlAmountP" : "exp.lvlAmount", maxLevel))
                .tooltip(Core.bundle.get("exp.tooltip"));
        });

        // 伤害减免曲线 (等级越高减伤越强)
        stats.add(Stat.armor, t -> buildGraphTable(t, damageReduction));
    }

    /**
     * 折叠式曲线图组件 (与 ExpTurret.buildGraphTable 一致)
     * 点击文字展开/收起 EField 的等级曲线表
     */
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

    public int expLevel(int e) {
        return Math.min(maxLevel, (int)(Mathf.sqrt(e / (5f * expScale))));
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
        if (expFields == null) return;
        for (EField<?> f : expFields) {
            f.setLevel(l);
        }
    }

    public class ExpLimitWallBuild extends LimitWallBuild implements ExpHolder, LevelHolder {
        public int exp;
        public @Nullable
        ExpHub.ExpHubBuild hub = null;

        public int incExp(int amount, boolean hub) {
            int ehub = (hub && hubValid()) ? this.hub.takeAmount(amount, this) : 0;
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
            int a = (int)(orbScale * orbExp);
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
            if (upgradeBlockEffect != Fx.none) {
                upgradeBlockEffect.at(x, y, 0, Color.white, region);
            }
        }

        @Override
        public void update() {
            if (updateExpFields) setEFields(level());
            super.update();
        }

        @Override
        public void displayBars(Table table) {
            super.displayBars(table);
            table.table(t -> {
                t.defaults().height(18f).pad(4);
                t.label(() -> "Lv " + level()).color(passive ? UnityPal.passive : Pal.accent).width(65f);
                t.add(new Bar(() -> level() >= maxLevel ? "MAX" : Core.bundle.format("bar.expp", (int)(expf() * 100f)), () -> UnityPal.exp, this::expf)).growX();
            }).pad(0).growX().padTop(4).padBottom(4);
            table.row();
        }

        @Override
        public void killed() {
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

        // hub methods
        @Override
        public boolean hubbable() {
            return !passive;
        }

        @Override
        public boolean canHub(Building build) {
            return hubbable() && build.team == team && (build instanceof ExpHolder h) && h.acceptOrb();
        }

        @Override
        public void setHub(ExpHub.ExpHubBuild hub) {
            this.hub = hub;
        }

        public boolean hubValid() {
            return hub != null;
        }
    }
}
