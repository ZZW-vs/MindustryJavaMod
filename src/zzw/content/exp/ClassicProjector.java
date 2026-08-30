package zzw.content.exp;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import zzw.content.graphics.UnityPal;

import static mindustry.Vars.tilesize;

/**
 * 经典投影仪 (PU132 @Dupe(ExpTurret, ForceProjector) 生成的 ExpForceProjector 体系 + ClassicProjector 手动移植)
 *
 * <p>经验系力场投影仪: 力场护盾 + 经验等级系统。
 * <ul>
 *   <li>护盾吸收/偏折子弹 (deflectChance 概率反弹, 否则吸收)</li>
 *   <li>被打获得经验, 等级提升护盾半径 (EField rangeField) 与颜色 (effectColors 渐变)</li>
 *   <li>shield-generator: 标准经验力墙; deflect-generator: 带子弹反弹的偏折发生器</li>
 * </ul></p>
 *
 * <p>适配 v155.4:
 * <ul>
 *   <li>@Dupe 织入 → 继承 ForceProjector + 手动实现 ExpHolder/LevelHolder (同 ExpTurret 模式)</li>
 *   <li>UnityFx.deflect/absorb/shieldBreak/forceShrink → 内置等价 Effect</li>
 *   <li>consumes.get(ConsumeType.item).valid() → 消费者效率数组查询</li>
 *   <li>Core.settings.getBool("animatedshields") → Vars.renderer.animateShields</li>
 * </ul></p>
 */
public class ClassicProjector extends mindustry.world.blocks.defense.ForceProjector {
    /** 偏折概率 (每点伤害独立判定) */
    public float deflectChance = 0f;
    /** 偏折特效 */
    public Effect deflectEffect = new Effect(22f, e -> {
        Draw.color(e.color);
        Lines.stroke(3f * e.fout());
        Lines.spikes(e.x, e.y, 3f + e.rotation * e.fout(), 6f + 8f * e.fout(), 2);
    });
    /** 吸收特效 */
    public Effect absorbEffect = new Effect(14f, e -> {
        Draw.color(e.color);
        Fill.circle(e.x, e.y, 2.5f * e.fout());
    });
    /** 护盾破碎特效 */
    public Effect shieldBreakEffect = new Effect(50f, e -> {
        Draw.color(e.color);
        Lines.stroke(3f * e.fout());
        Lines.poly(e.x, e.y, 6, 15f + 60f * e.fin());
    });
    /** 移除时收缩特效 */
    public Effect forceShrinkEffect = new Effect(35f, e -> {
        Draw.color(e.color);
        Lines.stroke(4f * e.fout());
        Lines.poly(e.x, e.y, 6, e.rotation * e.fout());
    });

    /** 获得经验概率 (每次被击中) */
    public float expChance = 1f;
    /** 每次获得经验量 */
    public int expGain = 1;

    // ===== ExpTurret 经验体系字段 (手动织入) =====
    public int maxLevel = 30;
    public int maxExp = 300;
    public boolean passive = false;
    public float orbScale = 1f;
    public boolean updateExpFields = true;
    public int expScale = 10;

    public @Nullable ClassicProjector pregrade = null;
    protected float rangeStart, rangeEnd;
    public int pregradeLevel = -1;

    public Color fromColor = UnityPal.exp, toColor = UnityPal.exp;
    /** 等级渐变色 (shield-generator 多级渐变) */
    public Color[] effectColors;

    public EField<?>[] expFields = new EField[]{};
    protected @Nullable EField<Float> rangeField = null;

    /** 等级覆盖贴图 (name + "-1") */
    public TextureRegion altRegion;

    public ClassicProjector(String name){
        super(name);
        consumesPower = true;
        outputsPower = false;
    }

    @Override
    public void init(){
        // EField 提取 (同 ExpTurret.init: stat 判定而非 instanceof)
        maxExp = requiredExp(maxLevel);
        if(expLevel(maxExp) < maxLevel) maxExp++;

        for(EField<?> f : expFields){
            if(f.stat == Stat.shootRange || f.stat == Stat.range){
                rangeField = (EField<Float>) f;
                break;
            }
        }
        if(rangeField == null){
            rangeStart = rangeEnd = radius;
        }else{
            rangeEnd = rangeField.fromLevel(maxLevel);
            rangeStart = rangeField.fromLevel(0);
        }
        setEFields(0);

        if(pregrade != null && pregradeLevel < 0) pregradeLevel = pregrade.maxLevel;
        if(effectColors == null) effectColors = new Color[]{fromColor};

        super.init();
    }

    /** 经验等级曲线 (同 ExpTurret) */
    public int expLevel(int e){
        return Math.min(maxLevel, (int)(arc.math.Mathf.sqrt(e / (5f * expScale))));
    }

    public int requiredExp(int l){
        return l * l * 5 * expScale;
    }

    @Override
    public void load(){
        super.load();
        altRegion = Core.atlas.find(name + "-1");
    }

    @Override
    public void setStats(){
        super.setStats();
        if(deflectChance > 0f) stats.add(Stat.baseDeflectChance, deflectChance, StatUnit.none);
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("exp", (ClassicProjectorBuild entity) -> new mindustry.ui.Bar(
            () -> Core.bundle.get("bar.exp", "Exp") + " " + entity.level() + "/" + maxLevel,
            () -> UnityPal.exp,
            () -> entity.levelf()
        ));
    }

    /** 经验信息面板 (与 ExpTurret.addExpStats 相同结构: EField 折线图 + 升级来源 + 经验容量) */
    @Override
    public void checkStats(){
        if(!stats.intialized){
            setStats();
            addExpStats();
            stats.intialized = true;
        }
    }

    public void addExpStats(){
        var map = stats.toMap();
        boolean removeAbil = false;
        for(EField<?> f : expFields){
            if(f.stat == null) continue;
            if(map.containsKey(f.stat.category) && map.get(f.stat.category).containsKey(f.stat)){
                if(f.stat == Stat.abilities){
                    if(!removeAbil){
                        stats.remove(f.stat);
                        removeAbil = true;
                    }
                }else{
                    stats.remove(f.stat);
                }
            }
            if(f.hasTable){
                stats.add(f.stat, t -> buildGraphTable(t, f));
            }
            else stats.add(f.stat, f.toString());
        }

        if(pregrade != null){
            stats.add(Stat.buildCost, "[#84ff00]" + mindustry.gen.Iconc.up + Core.bundle.format("exp.upgradefrom", pregradeLevel, pregrade.localizedName) + "[]");
            stats.add(Stat.buildCost, t -> {
                t.button(mindustry.gen.Icon.infoCircleSmall, mindustry.ui.Styles.cleari, 20f, () -> mindustry.Vars.ui.content.show(pregrade)).size(26f).color(UnityPal.exp);
            });
        }

        stats.add(Stat.itemCapacity, "@", Core.bundle.format("exp.expAmount", maxExp));
        stats.add(Stat.itemCapacity, t -> {
            t.add(Core.bundle.format("exp.lvlAmount", maxLevel)).tooltip(Core.bundle.get("exp.tooltip"));
        });
    }

    /** EField 折线图折叠面板 (ExpTurret.buildGraphTable 同款) */
    protected void buildGraphTable(arc.scene.ui.layout.Table t, EField<?> f){
        arc.scene.ui.Label l = t.add(f.toString()).get();
        arc.scene.ui.layout.Collapser c = new arc.scene.ui.layout.Collapser(tc -> {
            f.buildTable(tc, maxLevel);
        }, true);

        Runnable toggle = () -> c.toggle(false);
        l.clicked(toggle);
        t.button(mindustry.gen.Icon.downOpenSmall, mindustry.ui.Styles.clearTogglei, 20f, toggle).size(26f).color(UnityPal.exp).padLeft(8f);
        t.row();
        t.add(c).colspan(2).left();
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        drawPotentialLinks(x, y);

        if(rangeStart != rangeEnd) Drawf.circles(x * tilesize + offset, y * tilesize + offset, rangeEnd, UnityPal.exp);
        Drawf.circles(x * tilesize + offset, y * tilesize + offset, rangeStart, fromColor);

        if(!valid && pregrade != null) drawPlaceText(Core.bundle.format("exp.pregrade", pregradeLevel, pregrade.localizedName), x, y, false);
    }

    public void setEFields(int l){
        for(EField<?> f : expFields){
            f.setLevel(l);
        }
    }

    public class ClassicProjectorBuild extends ForceBuild implements ExpHolder, LevelHolder {
        public int exp;

        // ===== ExpHolder =====

        @Override
        public int getExp(){
            return exp;
        }

        public int incExp(int amount){
            int e = Math.min(amount, maxExp - exp);
            if(e == 0) return 0;
            int before = level();
            exp += e;
            int after = level();
            if(after > before) levelup();
            return e;
        }

        @Override
        public int handleExp(int amount){
            return amount > 0 ? incExp(amount) : -Math.min(-amount, exp);
        }

        @Override
        public int unloadExp(int amount){
            int e = Math.min(amount, exp);
            exp -= e;
            return e;
        }

        @Override
        public boolean acceptOrb(){
            return !passive && exp < maxExp;
        }

        @Override
        public boolean handleOrb(int orbExp){
            int a = (int)(orbScale * orbExp);
            if(a < 1) return false;
            incExp(a);
            return true;
        }

        // ===== LevelHolder =====

        @Override
        public int level(){
            return expLevel(exp);
        }

        @Override
        public int maxLevel(){
            return maxLevel;
        }

        public float levelf(){
            return level() / (float)maxLevel;
        }

        public void levelup(){
            zzw.content.exp.UnityFx.expPoof.at(this);
        }

        public Color effectColor(){
            return effectColors[Math.min((int)(levelf() * effectColors.length), effectColors.length - 1)];
        }

        /** 经验等级护盾半径 */
        @Override
        public float realRadius(){
            return (rangeField == null ? radius : rangeField.fromLevel(level())) * radscl;
        }

        /** 子弹命中处理: 偏折/吸收 + 获得经验 (PU132 ClassicProjectorBuild.hitBullet) */
        public void hitBullet(Bullet b, float r){
            if(!b.type.absorbable || b.team == team || dst2(b) > r * r) return;
            if(b.type.reflectable && deflectChance > 0f && Mathf.chance(deflectChance / Math.max(b.damage(), 1f))){
                // 偏折: 镜面反射
                float a = b.angleTo(this);
                float rb = b.rotation();
                if(Angles.near(a, rb, 90f)){
                    b.trns(-b.vel.x, -b.vel.y);
                    b.rotation(rb + 2 * (a - rb) + 180f);
                }
                b.owner = this;
                b.team = team;
                b.time += 1f;
                deflectEffect.at(b.x, b.y, angleTo(b), effectColor());
            }else{
                // 吸收
                b.absorb();
                absorbEffect.at(b.x, b.y, 0f, effectColor());
            }
            hit = 1f;
            buildup += b.damage();

            if(Mathf.chance(expChance)) handleExp(expGain);
        }

        @Override
        public void updateTile(){
            super.updateTile();

            if(updateExpFields) setEFields(level());

            // 护盾范围内子弹拦截 (PU132 原版: super 不做 intersect, 手动扫描)
            float realRadius = realRadius();
            if(realRadius > 0 && !broken){
                Groups.bullet.intersect(x - realRadius, y - realRadius, realRadius * 2f, realRadius * 2f, (arc.func.Cons<Bullet>)b -> hitBullet(b, realRadius));
            }
        }

        @Override
        public void onRemoved(){
            float radius = realRadius();
            if(!broken && radius > 1f) forceShrinkEffect.at(x, y, radius, effectColor());
            super.onRemoved();
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);
            if(altRegion.found()){
                Draw.alpha(levelf());
                Draw.rect(altRegion, x, y);
                Draw.color();
            }

            if(buildup > 0f){
                Draw.alpha(buildup / shieldHealth * 0.75f);
                Draw.color(effectColor());
                Draw.blend(Blending.additive);
                Draw.rect(topRegion, x, y);
                Draw.blend();
                Draw.reset();
            }

            drawShield();
        }

        @Override
        public void drawShield(){
            if(!broken){
                float radius = realRadius();

                Draw.z(Layer.shields);

                Draw.color(effectColor(), Color.white, Mathf.clamp(hit));

                if(mindustry.Vars.renderer.animateShields){
                    Fill.poly(x, y, Lines.circleVertices(radius), radius);
                }else{
                    Lines.stroke(1.5f);
                    Draw.alpha(0.09f + Mathf.clamp(0.08f * hit));
                    Fill.circle(x, y, radius);
                    Draw.alpha(1f);
                    Lines.circle(x, y, radius);
                    Draw.reset();
                }
            }

            Draw.reset();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(exp);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            exp = read.i();
        }
    }
}