package zzw.content.units.soul;

import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.game.Team;
import mindustry.gen.BlockUnitc;
import mindustry.gen.Unit;
import zzw.content.units.entities.MonolithSoulUnit;

/**
 * 灵魂持有者接口 (PU132 unity.entities.Soul 的纯 Java 移植)。
 *
 * <p>PU132 用 {@code interface Soul extends Teamc, Healthc, Sized} 统一描述
 * "能容纳灵魂的实体"——既可以是单位 ({@code MonolithComp})，也可以是建筑
 * ({@code SoulComp})。本模组无法使用注解织入，因此改为一个独立接口，由
 * 具体的单位实体 / 建筑 build 内部实现。</p>
 *
 * <p>为兼容"单位"和"建筑"两种宿主，接口只声明双方都具备的访问器：
 * {@link #team()} {@link #x()} {@link #y()} {@link #hitSize()} {@link #maxHealth()}。
 * (Unit 与 Building 都实现了这些方法。)</p>
 *
 * <p>核心机制：</p>
 * <ul>
 *   <li>{@link #join()} / {@link #unjoin()}：灵魂的进出，表现为 {@code souls} 计数增减；</li>
 *   <li>{@link #spreadSouls()}：实体死亡时把体内灵魂拆解为 {@link MonolithSoulUnit}
 *       (灵魂单位)，由 {@link #apply} 决定是否把其中一个交给玩家操控；</li>
 *   <li>{@link #soulf()}：灵魂充盈比例，供炮台/单位按比例缩放效率或伤害。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public interface Soul {
    /** 当前灵魂数量 */
    int souls();

    /** 最大灵魂数量 (0 = 该实体不承载灵魂) */
    int maxSouls();

    /** 所属队伍 */
    Team team();

    /** 世界坐标 X */
    float x();

    /** 世界坐标 Y */
    float y();

    /** 碰撞半径 (用于拆魂散布) */
    float hitSize();

    /** 最大血量 */
    float maxHealth();

    /** 是否还能再接纳灵魂 */
    default boolean canJoin() {
        return souls() < maxSouls();
    }

    /** 体内是否有灵魂 */
    default boolean hasSouls() {
        return souls() > 0;
    }

    /**
     * 计算还能接纳多少灵魂 (PU132 Soul.acceptSoul(int))。
     * 不实际写入，仅返回可接受数量。
     */
    default int acceptSoul(int amount) {
        return Math.min(maxSouls() - souls(), amount);
    }

    /** 尝试接纳另一实体的全部灵魂，返回实际接纳数量。 */
    default int acceptSoul(Object other) {
        Soul soul = toSoul(other);
        return soul != null ? acceptSoul(soul.souls()) : 0;
    }

    /** 尝试接纳另一灵魂持有者的全部灵魂，返回实际接纳数量。 */
    default int acceptSoul(Soul other) {
        return acceptSoul(other.souls());
    }

    /** 加入一个灵魂 (计数 +1)。 */
    void join();

    /** 移除一个灵魂 (计数 -1)。 */
    void unjoin();

    /** 灵魂充盈比例 (0~1)。maxSouls=0 时返回 0。 */
    default float soulf() {
        return maxSouls() <= 0 ? 0f : souls() / (float) maxSouls();
    }

    /**
     * 死亡时把体内灵魂拆解为灵魂单位 (PU132 Soul.spreadSouls)。
     *
     * <p>每个灵魂从本体中心随机方向偏移出生，再沿圆周均匀方向赋予初速度，
     * 随后 {@link #apply} 可能把其中一个交给玩家操控 (玩家可以指挥它去 join 别的容器)。
     * 该方法应在服务端调用 (单机即 {@code !net.active()} 或 {@code net.server()})。</p>
     */
    default void spreadSouls() {
        int n = souls();
        if (n <= 0) return;

        boolean transferred = false;
        float start = Mathf.random(360f);

        for (int i = 0; i < n; i++) {
            MonolithSoulUnit soul = MonolithSoulUnit.create(team());

            // 出生位置：本体范围内随机偏移
            Tmp.v1.trns(Mathf.random(360f), Mathf.random(hitSize()));
            soul.set(x() + Tmp.v1.x, y() + Tmp.v1.y);

            // 初速度：沿圆周均匀分布方向
            Tmp.v1.trns(start + 360f / n * i, Mathf.random(6f, 12f));
            soul.rotation = Tmp.v1.angle();
            soul.vel.set(Tmp.v1.x, Tmp.v1.y);

            transferred = apply(soul, i, transferred);
            soul.add();
        }
    }

    /**
     * 拆魂时的回调：决定是否把某个灵魂单位的操控权交给玩家
     * (PU132 Soul.apply)。返回值为"是否已转移过操控权"。
     */
    boolean apply(MonolithSoulUnit soul, int index, boolean transferred);

    /** 判断任意对象 (含控制器/BlockUnitc) 是否为灵魂持有者。 */
    static boolean isSoul(Object e) {
        return toSoul(e) != null;
    }

    /**
     * 把控制器 / 建筑占位单位解析为灵魂持有者 (PU132 Soul.toSoul)。
     *
     * <p>除了本身实现 {@link Soul} 的单位 / 建筑, 本模组还额外把实现了
     * {@code ISoulTurret} 的灵魂炮台建筑用 {@link TurretSoul} 适配为统一 {@link Soul},
     * 于是巨石单位拆出的灵魂单位可以飞到炮台上。</p>
     */
    static Soul toSoul(Object e) {
        if (e instanceof Unit cont) e = cont;
        if (e instanceof BlockUnitc unit) e = unit.tile();
        if (e instanceof Soul soul) return soul;
        // 灵魂炮台建筑: 适配为统一 Soul
        if (e instanceof mindustry.gen.Building build
            && e instanceof zzw.content.blocks.soul.ISoulTurret turret) {
            return new TurretSoul(build, turret);
        }
        return null;
    }

    /**
     * 灵魂炮台建筑的 {@link Soul} 适配器。
     *
     * <p>把炮台的 {@code souls()/maxSouls()/joinSoul()/unjoinSoul()} 语义映射到
     * 统一的 {@link Soul#join()}/{@link Soul#unjoin()} 上, 使灵魂单位能将其视为容器。</p>
     */
    class TurretSoul implements Soul {
        private final mindustry.gen.Building build;
        private final zzw.content.blocks.soul.ISoulTurret turret;

        public TurretSoul(mindustry.gen.Building build, zzw.content.blocks.soul.ISoulTurret turret) {
            this.build = build;
            this.turret = turret;
        }

        @Override public int souls() { return turret.souls(); }
        @Override public int maxSouls() { return turret.maxSouls(); }
        @Override public Team team() { return build.team(); }
        @Override public float x() { return build.x(); }
        @Override public float y() { return build.y(); }
        @Override public float hitSize() { return build.hitSize(); }
        @Override public float maxHealth() { return build.maxHealth(); }
        @Override public void join() { turret.joinSoul(); }
        @Override public void unjoin() { turret.unjoinSoul(); }
        @Override public boolean apply(MonolithSoulUnit soul, int index, boolean transferred) { return transferred; }
    }
}