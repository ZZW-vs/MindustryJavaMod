package zzw.content.units.entities;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.entities.Units;
import mindustry.gen.*;
import mindustry.world.Tile;
import zzw.content.units.ZEntityRegister;
import zzw.content.units.effects.DeathFx;
import zzw.content.units.effects.LineFx;

import static mindustry.Vars.*;

/**
 * MonolithSoul — 巨石灵魂实体 (PU132/PU_V8 移植)
 *
 * <p>对应 PU_V8 的 unity.entities.comp.MonolithSoulComp + unity.ai.MonolithSoulAI,
 * 因本项目无 Soul/monolithWorld 系统, 将 AI 的移动逻辑合并进实体 update(), 简化移植。</p>
 *
 * <p>★ 生命周期 (PU_V8 原版机制):</p>
 * <ul>
 *   <li>生成时生命值封顶为最大值的一半 (add()), 灵魂处于"虚体"状态;</li>
 *   <li>生命值按 lifeDelta() 自然变化: forms.size(拾取方块数) &lt; 2.5 时衰减, &gt; 2.5 时恢复;</li>
 *   <li>生命值回满 → 实体化 (corporeal=true, 绘制完整单位贴图);</li>
 *   <li>实体化后生命跌破 50% → 碎裂特效, 退回虚体状态;</li>
 *   <li>虚体状态下寻找友方容器 (单位/建筑), 飞过去 "加入" (joinTime 逼近 1);</li>
 *   <li>joinTime 到达 1 → 灵魂消散, 播放加入/转移特效 (★ 简化: 无 Soul 系统, 不转移增益, 留 TODO)。</li>
 * </ul>
 *
 * <p>★ v132 → v155.4 适配要点:</p>
 * <ul>
 *   <li>抽象类 → 普通类, 继承 UnitEntity, ZEntityRegister 注册 classId;</li>
 *   <li>PU132 的 MathU.addLength → 手动实现 (Vec2.addLength);</li>
 *   <li>PU132 的 monolithWorld.getChunk / Soul.toSoul → 本项目暂无此概念, 留 TODO。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class MonolithSoulEntity extends UnitEntity {

    /** 类加载时注册到 EntityMapping, 分配唯一 classId (v155.4 必需, 否则存档/网络同步错乱) */
    static {
        ZEntityRegister.register(MonolithSoulEntity.class, MonolithSoulEntity::new);
    }

    /** 工厂方法 (UnitType.constructor 用) — ★ 必须存在, 否则方法引用会解析到父类 UnitEntity.create, 创建出普通单位 */
    public static MonolithSoulEntity create() {
        return new MonolithSoulEntity();
    }

    /** 返回注册的 classId (绕过 v155.4 的实体映射检查) */
    @Override
    public int classId() {
        return ZEntityRegister.classId(MonolithSoulEntity.class);
    }

    // ===== 状态字段 =====
    /** 是否已实体化 (聚合为完整单位) */
    public boolean corporeal = false;
    /** 加入目标单位/建筑 */
    public Teamc joinTarget;
    /** 已拾取的方块列表 (forming) */
    public Seq<Tile> forms = new Seq<>();
    /** 加入时间 0→1 (到 1 时完成加入并消散) */
    public float joinTime;
    /** 形成进度 (0-1, 动画插值用) */
    public float formProgress;
    /** 环形贴图旋转角 (平滑转向目标) */
    public float ringRotation;

    // ===== 临时变量 (PU132 原版为 transient) =====
    private static final Vec2 vec = new Vec2();
    /** contemplate 计时器 (每 12f 重新思考一次人生) */
    private float timer = 0f;

    // ★ 注意: 不覆写 controller() 强转 MonolithSoulAI —
    // 玩家接管/logic 控制时 controller 不是 MonolithSoulAI, 强转会 ClassCastException

    /**
     * 生成时生命值封顶为一半 (PU_V8 add()):
     * 灵魂初生即为虚弱虚体, 必须寻找容器或拾取方块才能恢复。
     */
    @Override
    public void add() {
        super.add();
        health = Math.min(maxHealth / 2f, health);
    }

    @Override
    public void update() {
        super.update();

        // ===== 生命循环 (PU_V8 MonolithSoulComp.update 逐行对应) =====
        if(!corporeal) {
            // 生命值变化: 加入中每 tick 扣 0.2, 否则按 lifeDelta() (拾取方块少则衰减)
            health = Mathf.clamp(health + (joining() ? -0.2f : lifeDelta()) * Time.delta, 0f, maxHealth);

            // joinTime: 有目标时缓慢逼近 1 (约 2 秒), 无目标时快速回落
            joinTime = (joinTarget == null || !joinTarget.isAdded())
                ? Mathf.lerpDelta(joinTime, 0f, 0.1f)
                : Mathf.approachDelta(joinTime, 1f, 0.008f);

            // 形成进度: 有拾取方块时跟随生命比例, 否则回落到 0
            formProgress = Mathf.lerpDelta(formProgress, forms.any() ? (health / maxHealth) : 0f, 0.17f);

            // 环旋转角: 平滑转向目标方向, 无目标时跟随自身朝向
            ringRotation = Mathf.slerp(ringRotation, joinTarget == null ? rotation : angleTo(joinTarget), 0.08f);

            // 目标失效则清空
            if(!joinValid(joinTarget)) joinTarget = null;
            forms.removeAll(t -> !formValid(t));

            // 避免死亡: 生命归零前定期重新寻找容器
            timer += Time.delta;
            if(timer >= 12f) {
                contemplate();
                timer = 0f;
            }
        } else if(health <= maxHealth * 0.5f) {
            // 实体化后生命跌破 50%: 碎裂退回虚体
            DeathFx.monolithSoulCrack.at(x, y, rotation);

            corporeal = false;
            joinTarget = null;
            forms.clear();
            formProgress = 0f;
        }

        // 加入完成 / 实体化判定
        if(isValid()) {
            if(Mathf.equal(joinTime, 1f) && joinValid(joinTarget)) {
                // ★ 简化: PU_V8 会通过 Soul.toSoul(joinTarget).join() 把灵魂转移进容器,
                //   本项目无 Soul 系统, 仅播放消散+转移特效后移除灵魂 (TODO)
                kill();
                DeathFx.monolithSoulJoin.at(x, y, ringRotation, this);
                LineFx.monolithSoulTransfer.at(x, y, rotation, joinTarget);
            } else if(!corporeal && Mathf.equal(health, maxHealth)) {
                // 生命回满 → 实体化
                corporeal = true;
                joinTime = 0f;
            }
        }

        // ===== 移动逻辑 (PU_V8 MonolithSoulAI.updateUnit 对应) =====
        if(joinTarget != null) {
            // 朝目标附近的随机偏移点移动, 停在射程边缘 80% 处 (环绕容器)
            vec.set(joinTarget)
                .add(Mathf.randomSeedRange(id, 24f), Mathf.randomSeedRange(id + 1, 24f))
                .sub(this);
            addLength(vec, -type.range * 0.8f);
            vec.limit(type.speed);
            moveAt(vec);
            lookAt(prefRotation());
        }
    }

    /** 每 tick 生命值变化率: 拾取方块数相对 2.5 的差值 × 0.18 (少则衰减多则恢复) */
    public float lifeDelta() {
        return (forms.size - 2.5f) * 0.18f;
    }

    /**
     * 重新思考人生 (PU_V8 MonolithSoulAI.contemplate):
     * 在范围内寻找最近的友方容器 (单位或建筑) 作为加入目标。
     */
    public void contemplate() {
        // 已实体化 / 正在加入 / 拾取方块正收益时无需寻找
        float delta = lifeDelta();
        if(corporeal || joining() || (forming() && delta > 0f)) return;

        // 搜索半径 = 速度 × (当前生命 / 衰减率) / 2 — 衰减越快、生命越多, 找得越远
        // ★ delta < 0 (生命正在衰减) 才会得出正半径; delta >= 0 时直接跳过寻找
        if(delta >= 0f) return;
        float range = type.speed * (health / -delta) / 2f;
        if(range <= 0f) return;

        // 寻找最近的单位/建筑作为加入目标 (两者取更近者)
        Unit vesselUnit = Units.closest(team, x, y, range, this::accept);
        Building vesselBuild = Units.findAllyTile(team, x, y, range, this::accept);
        joinTarget = (vesselUnit != null || vesselBuild != null)
            ? (vesselUnit == null ? vesselBuild : vesselBuild == null ? vesselUnit :
                Math.max(dst(vesselUnit) - vesselUnit.hitSize / 2f, 0f) <=
                Math.max(dst(vesselBuild) - vesselBuild.hitSize() / 2f, 0f)
                ? vesselUnit : vesselBuild)
            : null;
    }

    /**
     * 是否接受某容器加入 (PU_V8 用 Soul.toSoul 判断容器是否还有灵魂容量)。
     * <p>★ 简化: 无 Soul 系统, 接受所有有效友方单位/建筑。</p>
     */
    public <T extends Teamc & Healthc> boolean accept(T other) {
        // TODO: PU_V8 的 Soul.toSoul(other).acceptSoul(1) >= 1 — 本项目暂无 Soul 系统
        return other.isValid();
    }

    /** 加入目标是否仍有效 (存活 + 在加入射程内) */
    public boolean joinValid(Teamc other) {
        return other != null && other.isAdded() && within(other, type.range + (other instanceof Unit u ? u.hitSize / 2f : other instanceof Building b ? b.hitSize() / 2f : 0f));
    }

    public boolean corporeal() {
        return corporeal;
    }

    public boolean joining() {
        return joinTarget != null && joinTarget.isAdded();
    }

    public boolean forming() {
        return forms.any();
    }

    public Seq<Tile> forms() {
        return forms;
    }

    public float formProgress() {
        return formProgress;
    }

    public float joinTime() {
        return joinTime;
    }

    public float ringRotation() {
        return ringRotation;
    }

    /** 拾取方块是否仍有效 (PU_V8: 需为巨石阵营方块且在射程内; 简化为任意非空方块) */
    public boolean formValid(Tile tile) {
        // TODO: PU_V8 判断 FactionMeta.map(...) == Faction.monolith — 本项目暂无阵营方块概念
        return tile != null && Mathf.dst(x, y, tile.worldx(), tile.worldy()) <= type.range;
    }

    /** 手动实现 PU_V8 的 MathU.addLength: 向量长度增加 len (可为负), 长度会截断到 0 */
    private static Vec2 addLength(Vec2 v, float len) {
        float l = v.len() + len;
        if(l <= 0f) {
            v.setZero();
        } else {
            v.setLength(l);
        }
        return v;
    }

    @Override
    public void write(Writes write) {
        super.write(write);
        write.bool(corporeal);
        write.f(joinTime);
        write.f(formProgress);
        write.i(forms.size);
        for(Tile tile : forms) {
            write.i(tile.pos());
        }
    }

    @Override
    public void read(Reads read) {
        super.read(read);
        corporeal = read.bool();
        joinTime = read.f();
        formProgress = read.f();
        int count = read.i();
        forms.clear();
        for(int i = 0; i < count; i++) {
            Tile tile = world.tile(read.i());
            if(tile != null) forms.add(tile);
        }
    }

}
