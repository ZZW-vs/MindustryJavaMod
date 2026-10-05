package zzw.util;

import arc.Events;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import zzw.content.units.effects.ParticleFx;

import java.lang.reflect.Field;

/**
 * 全局防作弊管理器 —— PU132 {@code unity.mod.AntiCheat} 的纯 Java 移植版。
 *
 * <p>PU132 用一个全局 {@code Unity.antiCheat} 实例统管 End 阵营单位的防作弊。
 * 本模组是纯 Java mod (不能用 mixin / @EntityComponent), 因此把该管理器整体
 * 改写成一个静态工具类, 机制、节奏与判定顺序与 PU132 保持完全一致。</p>
 *
 * <p>包含四套子系统 (对照 PU132 内部类):</p>
 * <ul>
 *   <li><b>UnitQueue</b> —— 受保护单位登记表。被外部强行从实体组剔除的单位,
 *       每 15 帧校验一次并 {@code add()} 写回世界; 同时周期性地强制 {@code update()},
 *       防止被"时间停止"类作弊卡住。</li>
 *   <li><b>BuildingQueue</b> —— 受保护建筑登记表 (与 {@link AntiCheatBuildings} 合并,
 *       后者现已委托到本类)。被抹掉的写回地块、被剔除的重新 add、被同队拆除/真死时注销。</li>
 *   <li><b>EntitySampler</b> —— 血量采样。被采样实体若在一段时间内血量不降反升
 *       (且未被"已验证"排除), 累计 5 次后直接 {@link #annihilateEntity} 抹除,
 *       用于反"锁血/回血"作弊。</li>
 *   <li><b>DisableRegenStatus</b> —— 流血 (禁回血) 状态。被标记的单位在持续时间内
 *       血量一旦上涨就被立刻扣回, 并播放 endRegenDisable 粒子。</li>
 * </ul>
 *
 * <p>另外提供静态 {@link #annihilateEntity(Entityc, boolean)}: 把一个单位/建筑
 * 从全部实体组里剥离 (但对象本身不销毁, 供切割特效继续绘制)。</p>
 *
 * <p>★ 与 PU132 的差异 (仅适配层面, 不改机制):</p>
 * <ul>
 *   <li>{@code Unity.antiCheat} 单例 → 本类静态方法;</li>
 *   <li>{@code Triggers.listen(Trigger.update)} → {@link EventType.Trigger#update};</li>
 *   <li>对象池 {@code Pools.obtain/free} → 直接 new (数量极少, 无性能收益, 避免池 API 差异);</li>
 *   <li>PU132 的 WormDefaultUnit 段单位清理分支 → 本模组段单位体系不同, 未移植;</li>
 *   <li>v160 无 {@code Unit.clearCommand()}, 已去除该调用。</li>
 * </ul>
 */
public class AntiCheat{
    /** 15 帧校验节拍。 */
    private static final Interval timer = new Interval();
    /** 受保护单位登记表。 */
    private static final Seq<UnitQueue> unitSeq = new Seq<>();
    /** 受保护建筑登记表。 */
    private static final Seq<BuildingQueue> buildingSeq = new Seq<>();
    /** 血量采样表 (与 samplerMap 同步维护)。 */
    private static final Seq<EntitySampler> sampler = new Seq<>();
    private static final IntMap<EntitySampler> samplerMap = new IntMap<>(409);
    /** 豁免 id 集合 (受保护单位/建筑不参与采样与流血)。 */
    private static final IntSet exclude = new IntSet(204);

    /** 流血状态表 (与 statusMap 同步维护)。 */
    private static final Seq<DisableRegenStatus> status = new Seq<>();
    private static final IntMap<DisableRegenStatus> statusMap = new IntMap<>(204);

    private static float lastTime = 0f;
    private static boolean hooked = false;

    /** 反射缓存的 {@code UnitEntity.added} 字段 (v160 为 protected)。 */
    private static Field addedField;
    /** Building.added 反射缓存 (v160 为 protected)。 */
    private static Field buildingAddedField;

    /** 挂载事件回调并清空登记表 (幂等, 多次调用只生效一次)。 */
    public static void setup(){
        if(hooked) return;
        hooked = true;

        Events.run(EventType.Trigger.update, AntiCheat::update);

        // 同队主动拆除建筑时放行 (沙盒模式有时不走 remove 路径, 这里补一手)
        Events.on(EventType.BlockBuildBeginEvent.class, event -> {
            if(event.breaking && event.tile != null && event.tile.build != null
                && event.unit != null && event.unit.team == event.tile.build.team){
                removeBuilding(event.tile.build);
            }
        });

        Events.on(EventType.ResetEvent.class, event -> {
            exclude.clear();
            unitSeq.clear();
            buildingSeq.clear();
            sampler.clear();
            samplerMap.clear();
            status.clear();
            statusMap.clear();
        });
    }

    // ==================================================================
    // annihilateEntity —— 把实体从所有实体组中剥离
    // ==================================================================

    public static void annihilateEntity(Entityc entity, boolean override){
        annihilateEntity(entity, override, false);
    }

    /**
     * 把实体从全部实体组里移除, 但不销毁对象本身。
     *
     * @param entity   目标实体
     * @param override true 时同时从防作弊登记表注销
     * @param setNaN   true 时把坐标/朝向置为 NaN (彻底破坏作弊状态)
     */
    public static void annihilateEntity(Entityc entity, boolean override, boolean setNaN){
        Groups.all.remove(entity);

        if(entity instanceof mindustry.gen.Drawc draw) Groups.draw.remove(draw);
        if(entity instanceof mindustry.gen.Syncc sync) Groups.sync.remove(sync);

        if(entity instanceof Unit unit){
            if(override) removeUnit(unit);

            // v160 added 为 protected, 只能反射置回 false
            setAddedFalse(unit);

            if(setNaN){
                unit.x = unit.y = unit.rotation = Float.NaN;
                for(mindustry.entities.units.WeaponMount mount : unit.mounts){
                    mount.reload = Float.NaN;
                }
            }

            unit.team.data().updateCount(unit.type, -1);
            unit.controller().removed(unit);

            Groups.unit.remove(unit);
            if(Vars.net.client()){
                Vars.netClient.addRemovedEntity(unit.id);
            }

            for(mindustry.entities.units.WeaponMount mount : unit.mounts){
                if(mount.bullet != null){
                    mount.bullet.time = mount.bullet.lifetime;
                    mount.bullet = null;
                }
                if(mount.sound != null){
                    mount.sound.stop();
                }
            }
        }

        if(entity instanceof Building building){
            Groups.build.remove(building);
            building.tile.remove();
            if(override) removeBuilding(building);
            if(setNaN){
                building.x = building.y = Float.NaN;
            }
            // v160 的 Building 没有 sound 循环音效字段, 无需停止;
            // v160 added 为 protected, 只能反射置回 false (与 unit 分支一致)
            setAddedFalse(building);
        }
    }

    /** 反射把 {@code Building.added} 置为 false (v160 为 protected)。 */
    private static void setAddedFalse(Building building){
        try{
            if(buildingAddedField == null){
                buildingAddedField = mindustry.gen.Building.class.getDeclaredField("added");
                buildingAddedField.setAccessible(true);
            }
            buildingAddedField.setBoolean(building, false);
        }catch(Exception ignored){
            // 反射失败也不影响主体流程
        }
    }

    /** 反射把 {@code UnitEntity.added} 置为 false。 */
    private static void setAddedFalse(Unit unit){
        try{
            if(addedField == null){
                addedField = mindustry.gen.UnitEntity.class.getDeclaredField("added");
                addedField.setAccessible(true);
            }
            addedField.setBoolean(unit, false);
        }catch(Exception ignored){
            // 反射失败也不影响主体流程
        }
    }

    // ==================================================================
    // 主循环
    // ==================================================================

    private static void update(){
        if(Vars.state.isPaused()) return;

        // ---- 每 15 帧: 校验登记表 + 采样衰减 ----
        if(timer.get(15f) && (!unitSeq.isEmpty() || !buildingSeq.isEmpty())){
            // 先假定全部"已被剔除", 扫描实体组后再逐个确认
            for(UnitQueue u : unitSeq) u.allAdded = false;
            for(BuildingQueue b : buildingSeq) b.allAdded = false;

            for(Entityc e : Groups.all){
                if(e instanceof Unit){
                    for(UnitQueue u : unitSeq){
                        if(e == u.unit) u.allAdded = true;
                    }
                }else if(e instanceof Building){
                    for(BuildingQueue b : buildingSeq){
                        if(e == b.build) b.allAdded = true;
                    }
                }
            }

            // 不在实体组里的单位 → 重新 add() 写回世界
            for(UnitQueue u : unitSeq){
                if(!u.allAdded && !u.removed) u.unit.add();
                u.allAdded = false;
                u.counter++;
            }
            // 建筑: 被同队拆除 → 注销; 被抹掉 → 写回地块; 被剔除 → add()
            for(BuildingQueue b : buildingSeq){
                if(deconstructed(b.build)){
                    removeBuilding(b.build);
                    continue;
                }
                if(!b.allAdded && !b.removed){
                    b.build.tile.setBlock(b.build.block, b.build.team, b.build.rotation, () -> b.build);
                }
                b.allAdded = false;
                b.counter++;
            }

            // 清理已注销项
            unitSeq.removeAll(u -> u.removed);
            buildingSeq.removeAll(b -> b.removed);

            // 采样衰减 / 到期回收
            for(int i = sampler.size - 1; i >= 0; i--){
                EntitySampler es = sampler.get(i);
                es.excludeDuration -= 15f;
                es.duration -= 15f;
                if(es.duration <= 0f && es.excludeDuration <= 0f){
                    sampler.remove(i);
                    samplerMap.remove(es.entity.id());
                }
            }
        }

        // ---- 每帧 (Time.time 去重): 强制 update 受保护实体 + 推进流血状态 ----
        if(Time.time > lastTime){
            for(UnitQueue u : unitSeq){
                if(u.counter > 10) u.unit.update();
            }
            for(BuildingQueue b : buildingSeq){
                if(b.counter > 10) b.build.update();
            }
            for(int i = status.size - 1; i >= 0; i--){
                DisableRegenStatus s = status.get(i);
                s.update();
                if(s.duration <= 0f || !s.unit.isValid()){
                    status.remove(i);
                    statusMap.remove(s.unit.id);
                }
            }
            lastTime = Time.time;
        }
    }

    // ==================================================================
    // 流血 (禁回血) 状态
    // ==================================================================

    /** 子弹造成伤害后回报血量差 (delta < 0 时累加到流血状态台账)。 */
    public static void notifyDamage(int unitId, float delta){
        if(delta > 0) return;
        DisableRegenStatus s = statusMap.get(unitId);
        if(s != null){
            s.lastHealth += delta;
        }
    }

    /** 给单位施加/刷新流血状态 (持续期间血量不许上涨)。 */
    public static void applyStatus(Unit unit, float duration){
        if(exclude.contains(unit.id)) return;
        DisableRegenStatus s = statusMap.get(unit.id);

        if(s != null){
            s.duration = Math.max(s.duration, duration);
        }else{
            DisableRegenStatus ns = new DisableRegenStatus();
            ns.unit = unit;
            ns.lastHealth = unit.health;
            ns.duration = duration;
            status.add(ns);
            statusMap.put(unit.id, ns);
        }
    }

    // ==================================================================
    // 血量采样 (反锁血/回血)
    // ==================================================================

    public static void samplerAdd(Healthc entity){
        samplerAdd(entity, false);
    }

    /**
     * 采样一个实体。
     *
     * @param verified true = 本次伤害"确实生效"(血量下降), 把实体加入豁免期,
     *                 避免误判; false = 走作弊判定, 若血量不降反升累计 penalty。
     */
    public static void samplerAdd(Healthc entity, boolean verified){
        if(!verified){
            if(exclude.contains(entity.id())) return;
            EntitySampler ent = samplerMap.get(entity.id());
            if(ent != null){
                if(entity.health() >= ent.lastHealth && ent.excludeDuration <= 0f){
                    ent.duration = Math.max(30f, ent.duration);
                    if(ent.penalty++ >= 5){
                        annihilateEntity(entity, false);
                        samplerMap.remove(entity.id());
                        sampler.remove(ent);
                    }
                }
                return;
            }
            EntitySampler s = new EntitySampler();
            s.entity = entity;
            s.duration = 2f * 60f;
            s.lastHealth = entity.health();
            sampler.add(s);
            samplerMap.put(entity.id(), s);
        }else{
            EntitySampler ent = samplerMap.get(entity.id());
            if(ent != null){
                ent.excludeDuration = 2 * 60f;
            }else{
                EntitySampler s = new EntitySampler();
                s.entity = entity;
                s.excludeDuration = 2 * 60f;
                s.duration = 0f;
                sampler.add(s);
                samplerMap.put(entity.id(), s);
            }
        }
    }

    // ==================================================================
    // 单位 / 建筑登记
    // ==================================================================

    public static void removeBuilding(Building building){
        exclude.remove(building.id);
        for(BuildingQueue bq : buildingSeq){
            if(bq.build == building) bq.removed = true;
        }
    }

    public static void removeUnit(Unit unit){
        exclude.remove(unit.id);
        for(UnitQueue uq : unitSeq){
            if(uq.unit == unit) uq.removed = true;
        }
    }

    public static void addBuilding(Building build){
        if(exclude.add(build.id)){
            buildingSeq.add(new BuildingQueue(build));
        }
    }

    public static void addUnit(Unit unit){
        if(exclude.add(unit.id)){
            unitSeq.add(new UnitQueue(unit));
        }
    }

    /** 建筑地块上是否是本队的重构脚手架 (说明是玩家主动拆除)。 */
    private static boolean deconstructed(Building building){
        if(building.tile == null) return true;
        Building alt = building.tile.build;
        return alt instanceof ConstructBuild && alt.team == building.team;
    }

    // ==================================================================
    // 内部数据结构
    // ==================================================================

    static class EntitySampler{
        Healthc entity;
        float duration, excludeDuration = 0f, lastHealth;
        int penalty = 0;
    }

    static class UnitQueue{
        Unit unit;
        boolean allAdded = true;
        int counter = 0;
        boolean removed = false;

        UnitQueue(Unit unit){
            this.unit = unit;
        }
    }

    static class BuildingQueue{
        Building build;
        boolean allAdded = true;
        int counter = 0;
        boolean removed = false;

        BuildingQueue(Building build){
            this.build = build;
        }
    }

    static class DisableRegenStatus{
        Unit unit;
        float lastHealth;
        float duration;

        void update(){
            if(unit.health == Float.POSITIVE_INFINITY || Float.isNaN(unit.health)){
                unit.health = unit.maxHealth == Float.POSITIVE_INFINITY || Float.isNaN(unit.maxHealth) ? 800000f : unit.maxHealth;
            }
            float delta = unit.health - lastHealth;
            if(delta > 0){
                unit.health -= delta;
            }
            if(unit.health <= 0f) unit.damage(0f);

            if(Mathf.chanceDelta(0.19f)){
                Tmp.v1.rnd(Mathf.range(unit.type.hitSize / 2f));
                ParticleFx.endRegenDisable.at(unit.x + Tmp.v1.x, unit.y + Tmp.v1.y);
            }

            lastHealth = unit.health;
            duration -= Time.delta;
        }
    }
}