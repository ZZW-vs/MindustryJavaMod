package zzw.content.units.entities;

import arc.Core;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Teamc;
import mindustry.gen.UnitEntity;
import mindustry.input.Binding;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import zzw.content.type.UnityUnitType;
import zzw.content.units.ZEntityRegister;
import zzw.content.units.Z_SoulUnits;
import zzw.content.units.effects.LineFx;
import zzw.content.units.effects.MonolithFx;
import zzw.content.units.effects.TrailFx;
import zzw.content.units.soul.MonolithWorld;
import zzw.content.units.soul.Soul;

import static mindustry.Vars.world;

/**
 * 巨石灵魂单位实体 (PU132 unity.entities.comp.MonolithSoulComp 的纯 Java 移植)。
 *
 * <p>PU132 用 {@code @EntityComponent} 把灵魂状态机织入 UnitEntity；
 * 本模组改为直接继承 {@link UnitEntity} 并覆写 {@link #update()} / {@link #add()} /
 * {@link #destroy()}。</p>
 *
 * <p>灵魂单位的两种形态：</p>
 * <ul>
 *   <li><b>非实体 (corporeal=false, 初始)</b>：半透明幽灵态，血量持续流失
 *       (若正在 join 容器则减缓流失)，可飞向容器 {@link #join} 或收集巨石地块
 *       {@link #form}。收集地块可回血，血量回满后转为实体态。</li>
 *   <li><b>实体 (corporeal=true)</b>：普通飞行单位外观；血量跌破一半时碎裂，
 *       退回非实体态 (可被玩家再次指挥)。</li>
 * </ul>
 *
 * <p>玩家操控 (本地客户端)：</p>
 * <ul>
 *   <li>右键/选择键点击地块 → {@link #form} 收集该巨石地块；</li>
 *   <li>右键/破坏键点击友方容器 → {@link #join} 前往加入，join 完成后
 *       把 1 个灵魂注入容器。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class MonolithSoulUnit extends UnitEntity {
    // ===== 灵魂状态机 (PU132 MonolithSoulComp @ReadOnly transient 字段) =====
    private boolean corporeal;
    private float joinTime;
    private Teamc joinTarget;
    private float ringRotation;
    private final Seq<Tile> forms = new Seq<>(5);
    private float formProgress;

    public static MonolithSoulUnit create() {
        return new MonolithSoulUnit();
    }

    /** 由 UnitType 创建灵魂单位 (PU132 MonolithSoulComp.create)。 */
    public static MonolithSoulUnit create(Team team) {
        // 走 UnitType.create(team): 设置 team / type / health, 并触发 setType 初始化拖尾
        return (MonolithSoulUnit) Z_SoulUnits.monolithSoul.create(team);
    }

    @Override
    public void setType(UnitType type) {
        super.setType(type);
        // PU132 CTrailComp.setType: 用 trailType 工厂创建拖尾
        // (幽灵态不会走 drawTrail, 因此必须在此提前创建, 否则拖尾为空)
        if (type instanceof UnityUnitType t) {
            trail = t.trailType.get(this);
        }
    }

    @Override
    public int classId() {
        return ZEntityRegister.classId(MonolithSoulUnit.class);
    }

    // ===== 状态访问器 (供 UnitType.draw/update 读取) =====

    public boolean corporeal() {
        return corporeal;
    }

    public float joinTime() {
        return joinTime;
    }

    public Teamc joinTarget() {
        return joinTarget;
    }

    public float ringRotation() {
        return ringRotation;
    }

    public Seq<Tile> forms() {
        return forms;
    }

    public float formProgress() {
        return formProgress;
    }

    public boolean joining() {
        return joinTarget != null && joinTarget.isAdded();
    }

    public boolean forming() {
        return forms.any();
    }

    /** 地块数量对血量的影响系数 (PU132 MonolithSoulComp.lifeDelta)。 */
    public float lifeDelta() {
        return (forms.size - 2.5f) * 0.18f;
    }

    @Override
    public void add() {
        super.add();
        // 出生血量减半 (PU132: health = min(maxHealth/2, health))
        health = Math.min(maxHealth / 2f, health);
    }

    @Override
    public void update() {
        if (!corporeal) {
            // 血量随时间变化：收集地块回血、join 时缓慢流失、否则按 lifeDelta
            health = Mathf.clamp(health + (joining() ? -0.2f : lifeDelta()) * Time.delta, 0f, maxHealth);

            // joinTime 朝目标推进；失去目标则回落
            joinTime = (joinTarget == null || !joinTarget.isAdded())
                ? Mathf.lerpDelta(joinTime, 0f, 0.1f)
                : Mathf.approachDelta(joinTime, 1f, 0.008f);
            formProgress = Mathf.lerpDelta(formProgress, forms.any() ? (health / maxHealth) : 0f, 0.17f);
            ringRotation = Mathf.slerp(ringRotation, joinTarget == null ? rotation : angleTo(joinTarget), 0.08f);

            if (!joinValid(joinTarget)) joinTarget = null;
            forms.removeAll(t -> !formValid(t));

            // 本地玩家操控
            if (controller() == Vars.player) {
                if (!Vars.mobile) {
                    float mx = Core.input.mouseWorldX(), my = Core.input.mouseWorldY();

                    if (Core.input.keyTap(Binding.select)) {
                        Tile tile = world.tileWorld(mx, my);
                        if (tile != null) form(tile);
                    } else if (Core.input.keyTap(Binding.rotate)) {
                        Teamc target = Units.closest(team, mx, my, 1f, u -> joinValid(u));
                        if (target == null) target = world.buildWorld(mx, my);

                        if (target != null && target.team() == team) join(target);
                    }
                }
            }
        } else if (health <= maxHealth * 0.5f) {
            // 实体态血量跌破一半 → 碎裂退回幽灵态
            MonolithFx.monolithSoulCrack.at(x, y, rotation);

            corporeal = false;
            joinTarget = null;
            forms.clear();
            formProgress = 0f;
        }

        if (isValid()) {
            if (Mathf.equal(joinTime, 1f) && joinValid(joinTarget)) {
                // 加入成功：死亡并把灵魂注入容器
                kill();
                MonolithFx.monolithSoulJoin.at(x, y, ringRotation, this);

                LineFx.monolithSoulTransfer.at(x, y, rotation, joinTarget);
                Time.run(LineFx.monolithSoulTransfer.lifetime, Soul.toSoul(joinTarget)::join);
            } else if (!corporeal && Mathf.equal(health, maxHealth)) {
                // 收集足够地块回满血 → 实体化
                corporeal = true;
                joinTime = 0f;
            }
        }

        super.update();
    }

    @Override
    public void destroy() {
        if (!isAdded()) return;
        // 消散拖尾 (PU132: trailFadeLow)
        TrailFx.trailFadeLow.at(x, y,
            (type.engineSize + Mathf.absin(Time.time, 2f, type.engineSize / 4f) * elevation) * type.trailScl,
            trail.copy());
        super.destroy();
    }

    /** 指派要加入的容器 (PU132 MonolithSoulComp.join)。 */
    public void join(Teamc other) {
        if (!joinValid(other)) return;
        if (forms.any()) forms.clear();
        joinTarget = other;
    }

    /** 添加/移除一个成形地块 (最多 5 块，PU132 MonolithSoulComp.form)。 */
    public void form(Tile tile) {
        if (forms.contains(tile)) {
            forms.remove(tile);
            return;
        }

        if (!formValid(tile)) return;
        if (forms.size >= 5) forms.remove(0);

        joinTarget = null;
        forms.add(tile);
    }

    /** 该容器是否可加入 (同队、可接受灵魂、在射程内)。 */
    public boolean joinValid(Teamc other) {
        Soul soul = Soul.toSoul(other);
        return soul != null && other != null && other.isAdded() && soul.acceptSoul(1) >= 1
            && within(other, type.range + (other instanceof mindustry.gen.Unit u ? (u.hitSize / 2f)
                : other instanceof mindustry.gen.Building b ? (b.hitSize() / 2f) : 0f));
    }

    /** 该地块是否可成形 (非合成、在射程内、是巨石地块)。 */
    public boolean formValid(Tile tile) {
        if (tile == null || tile.synthetic()) return false;
        if (Mathf.dst(x, y, tile.getX(), tile.getY()) > type.range) return false;
        return MonolithWorld.isMonolith(tile);
    }

    /** 让本灵魂单位原地消散 (玩家可用 / 供 AI 调用)。 */
    public void removeSoul() {
        kill();
    }
}