package zzw.content.units;

import arc.graphics.g2d.Draw;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.entities.Effect;
import mindustry.content.Fx;

/**
 * 分段虫子单位的头部 Entity
 *
 * 设计: 借鉴 PU132 WormDefaultUnit, 适配 154.3 原生 API
 * - 头部 (本类) 持有 segments[] 数组 (段身列表)
 * - 段身是 SegmentUnitEntity, 由头部直接控制位置
 * - 血量分布: 头部和所有段身共享血量
 *
 * 不使用 @EntityDef 注解, 不需要 Segmentc 接口
 *
 * ★ 重要: 重写 classId() 返回 UnitEntity 的 classId, 避开 v154.3 的 checkEntityMapping 检查 ★
 * 这让我们不需要 @EntityDef 注解处理器
 */
public class SegmentWormEntity extends UnitEntity {

    /** 工厂方法 (UnitType.constructor 用) */
    public static SegmentWormEntity create() {
        return new SegmentWormEntity();
    }

    /** 返回注册的 classId (绕过 v154.3 的 checkEntityMapping 检查) */
    @Override
    public int classId() {
        return ZEntityRegister.classId(SegmentWormEntity.class);
    }

    /**
     * ★ 关键: 头部不和自己段身碰撞 (借鉴 SegmentUnitEntity.collides)
     * 不重写会导致头部被自己段身推开, 待机时单位自己向前移动
     */
    @Override
    public boolean collides(mindustry.gen.Hitboxc other) {
        if (other instanceof SegmentUnitEntity seg && seg.head == this) return false;
        return super.collides(other);
    }

    /**
     * 是否处于待机状态 (无目标且无命令, 应静止)
     * ★ v154.3 玩家队伍用 CommandAI 不用 WormAI, 所以不能依赖 WormAI.isIdle
     *
     * 三种 controller 情况:
     *   1. Player (玩家进入单位): isPlayer()=true, 不静止
     *   2. CommandAI (玩家选中单位但不进入, 玩家队伍默认): 用 targetPos 移动, 不是 target
     *      → 用 hasCommand() (public, = targetPos != null) 判断, 有命令=玩家在指挥移动, 不静止
     *   3. AIController (敌方/刷怪, 如 FlyingAI/GroundAI): 用 target 字段, target==null=待机
     *      target 是 protected, 用反射访问
     *
     * ★ 关键修复: 之前只检查 target==null, 但 CommandAI 用 targetPos 不用 target
     *   导致玩家给单位下令移动时 (target==null 但 targetPos!=null) 被误判为待机, vel 被清零
     */
    public boolean isIdle() {
        // 玩家操控 (进入单位) 时不静止
        if (isPlayer()) return false;
        mindustry.entities.units.UnitController c = controller();
        // ★ CommandAI (玩家选中单位但不进入): 检查 hasCommand() 而非 target
        //   hasCommand() 返回 targetPos != null, 有命令=玩家在指挥单位移动, 不能清零 vel
        if (c instanceof mindustry.ai.types.CommandAI cmd) {
            return !cmd.hasCommand();
        }
        // ★ 普通 AIController (敌方/刷怪): 检查 target==null
        //   CommandAI 也继承 AIController, 但已在上面的分支处理, 这里只处理非 CommandAI 的
        if (c instanceof mindustry.entities.units.AIController ai) {
            try {
                java.lang.reflect.Field f = mindustry.entities.units.AIController.class.getDeclaredField("target");
                f.setAccessible(true);
                Object t = f.get(ai);
                return t == null;
            } catch (Throwable e) {
                return false;
            }
        }
        return false;
    }

    /** 段身列表 (顺序: 头部后方第一段到最后一段) */
    public SegmentUnitEntity[] segments = new SegmentUnitEntity[0];
    /** 段身位置缓存 (PU132 segments[], 用于物理模拟) */
    protected Vec2[] segPositions;
    /** 段身速度 (PU132 segmentVelocities[], 让段身有惯性) */
    protected Vec2[] segVelocities;
    /** 段身朝向 (PU132 segmentUnits[i].rotation, 每段独立朝向, 用于约束算法) */
    protected float[] segRotations;
    /** 段身朝向相对父段的最大角度差 (度, 154.3 原版 segmentRotationRange)
     *  调小 = 段身更硬 (转向幅度小), 调大 = 段身更软 (转向幅度大)
     *  当前 25f: 段身朝向相对父段最多 ±25°, 9 段累计最多 225°, 不会折返
     *  154.3 原版默认 80f (太软), 之前调 40f 仍嫌软, 调到 25f 更接近真实虫子 */
    public float segmentRotationRange = 25f;
    /** 段身朝向 slerp 到父段朝向的速率 (154.3 原版 baseRotateSpeed)
     *  默认 0.04f: 头部移动时段身朝向慢慢转向父段方向 */
    public float baseRotateSpeed = 0.04f;
    /** 段身间最大角度差 (度, PU132 angleLimit, 已弃用, 保留兼容) */
    public float angleLimit = 30f;
    /** 段身间距 (154.3 segmentSpacing, 原 PU132 segmentOffset) */
    public float segmentSpacing = 23f;
    /** 段身阻力 (PU132 drag, 段身速度衰减)
     *  PU132 原版 0.007f 太小 (每帧仅衰减 0.012%), 快速摆动时尾部惯性消散不掉导致抖动
     *  调到 3.0f: 每帧衰减约 5%, 让段身惯性快速消散, 尾部稳定 */
    public float segmentDrag = 3.0f;
    /** 血量分布速率 (PU132 healthDistribution) */
    public float healthDistributionRate = 0.1f;
    /** 血量分布效率 (受伤降低, 慢慢恢复, PU132 healthDistributionEfficiency) */
    protected float healthDistributionEfficiency = 1f;
    /** 段身伤害缩放 (PU132 segmentDamageScl, splittable 模式下有效)
     *  段身受伤害时, 血量减少 amount × segmentDamageScl
     *  越大 = 段身越脆, 越容易死亡分裂
     *  默认 6f (PU132 UnityUnitType 默认值), toxobyte 8f, catenapede 12f */
    public float segmentDamageScl = 6f;

    /** 再生间隔 (PU132 regenTime, 单位 tick, 0=不再生)
     *  每 regenTime tick 长出一节新尾部段身, 期间会扣血 (health/段数/2)
     *  PU132 toxobyte 原值: 15*60f = 900 tick (15秒长一节)
     *  默认 0: 不启用再生 */
    public float regenTime = 0f;
    /** 段身数上限 (PU132 maxSegments, 达到上限后停止再生)
     *  toxobyte 原值 25, arcnelidia 不启用再生 */
    public int maxSegments = 0;
    /** 当前再生计时器 (累加 Time.delta, 达到 regenTime 后重置) */
    protected float repairTime = 0f;
    /** 调试计数器: 每 60 tick 打一次再生进度 */
    protected float debugRegenLogTimer = 0f;
    /** 是否启用轻微晃动 (arcnelidia=true 轻微晃动, toxobyte=false 完全静止)
     *  借鉴 v154.3 UnitComp.wobble(), 但振幅更小 (0.02f vs 原版 0.05f) */
    public boolean wobbleEnabled = false;
    /** 是否启用分裂 (PU132 splittable, 段身有独立血量, 死亡时虫子分裂) */
    public boolean splittable = false;
    /** 是否启用链式合并 (PU132 chainable, 两条同类型虫子靠近时合并) */
    public boolean chainable = false;
    /** 链式合并扫描计时器 (每 5 秒扫描一次附近尾部虫子) */
    protected float chainScanTimer = 0f;
    /** 分裂音效 (PU132 默认 Sounds.door) */
    public static arc.audio.Sound splitSound = null;
    /** 链式合并音效 (PU132 默认 Sounds.door) */
    public static arc.audio.Sound chainSound = null;

    /** 贴图前缀缓存 (type.name + "-", 用于快速查找段身贴图) */
    protected String texturePrefix = null;

    /** 段身配置 (在 Z_Units.load 中注册, 支持多个分段单位) */
    public static class SegmentConfig {
        public final mindustry.type.UnitType segmentType;
        public final int count;
        public final float spacing;
        /** 再生间隔 (0=不再生, toxobyte=15*60f) */
        public final float regenTime;
        /** 段身数上限 (0=不限制, 与 regenTime 配合使用) */
        public final int maxSegments;
        /** 是否启用轻微晃动 (arcnelidia=true, toxobyte=false) */
        public final boolean wobble;
        /** 是否启用分裂 (PU132 splittable, 段身有独立血量, 死亡时虫子分裂)
         *  true: 段身独立承受伤害, 中间段身死亡时后半段变成新虫子
         *  false: 段身伤害转移给头部 (默认) */
        public final boolean splittable;
        /** 是否启用链式合并 (PU132 chainable, 两条同类型虫子靠近时合并)
         *  true: 头部每 5 秒扫描附近尾部虫子, 合并成更长虫子
         *  false: 不合并 (默认) */
        public final boolean chainable;
        /** 段身朝向相对父段的最大角度差 (度, 154.3 segmentRotationRange)
     *  调小 = 段身更硬 (转向幅度小), 调大 = 段身更软 (转向幅度大)
     *  默认 25f: arcnelidia 9 段够用
     *  toxobyte 25 段用 35f: 转弯时段身能转更多, 避免位置偏离 ideal 导致卡顿 */
        public final float segmentRotationRange;
        /** 段身伤害缩放 (PU132 segmentDamageScl, splittable 模式下有效)
         *  段身受到伤害时, 血量减少 amount × segmentDamageScl
         *  越大 = 段身越脆, 越容易死亡分裂
         *  toxobyte 原版 8f, catenapede 原版 12f */
        public final float segmentDamageScl;
        /** 血量分布速率 (PU132 healthDistribution)
         *  默认 0.1f, catenapede 原版 0.15f */
        public final float healthDistribution;
        public SegmentConfig(mindustry.type.UnitType t, int c, float s) {
            this(t, c, s, 0f, 0, false, false, false);
        }
        public SegmentConfig(mindustry.type.UnitType t, int c, float s, float regenTime, int maxSegments) {
            this(t, c, s, regenTime, maxSegments, false, false, false);
        }
        public SegmentConfig(mindustry.type.UnitType t, int c, float s, float regenTime, int maxSegments, boolean wobble) {
            this(t, c, s, regenTime, maxSegments, wobble, false, false);
        }
        public SegmentConfig(mindustry.type.UnitType t, int c, float s, float regenTime, int maxSegments, boolean wobble, boolean splittable, boolean chainable) {
            this(t, c, s, regenTime, maxSegments, wobble, splittable, chainable, 25f);
        }
        /** 带段身转角范围的新构造函数 (toxobyte 用 35f, arcnelidia 用默认 25f) */
        public SegmentConfig(mindustry.type.UnitType t, int c, float s, float regenTime, int maxSegments, boolean wobble, boolean splittable, boolean chainable, float segmentRotationRange) {
            this(t, c, s, regenTime, maxSegments, wobble, splittable, chainable, segmentRotationRange, 6f);
        }
        /** 完整构造函数: 带段身转角范围和伤害缩放 (toxobyte 用 35f/8f, arcnelidia 用 25f/6f 默认) */
        public SegmentConfig(mindustry.type.UnitType t, int c, float s, float regenTime, int maxSegments, boolean wobble, boolean splittable, boolean chainable, float segmentRotationRange, float segmentDamageScl) {
            this(t, c, s, regenTime, maxSegments, wobble, splittable, chainable, segmentRotationRange, segmentDamageScl, 0.1f);
        }
        public SegmentConfig(mindustry.type.UnitType t, int c, float s, float regenTime, int maxSegments, boolean wobble, boolean splittable, boolean chainable, float segmentRotationRange, float segmentDamageScl, float healthDistribution) {
            segmentType = t; count = c; spacing = s;
            this.regenTime = regenTime; this.maxSegments = maxSegments;
            this.wobble = wobble; this.splittable = splittable; this.chainable = chainable;
            this.segmentRotationRange = segmentRotationRange;
            this.segmentDamageScl = segmentDamageScl;
            this.healthDistribution = healthDistribution;
        }
    }
    /** 按 UnitType.name 注册的段身配置 (key = 头部名字, 如 "arcnelidia" / "toxobyte") */
    public static final java.util.Map<String, SegmentConfig> configs = new java.util.HashMap<>();

    /** 旧静态字段 (向后兼容, 优先用 configs Map) */
    public static mindustry.type.UnitType defaultSegmentType = null;
    public static int defaultSegmentCount = 5;
    public static float defaultSegmentSpacing = 23f;

    /** 段身是否已创建 (兜底: add() 没触发就在 update() 里创建) */
    private boolean segmentsCreated = false;

    /** 上一帧速度 (PU132 lastVelocityC, 用于段身速度平滑) */
    protected final Vec2 lastVelocityC = new Vec2();
    /** 上上帧速度 (PU132 lastVelocityD, 用于 3 帧平均) */
    protected final Vec2 lastVelocityD = new Vec2();

    @Override
    public void update() {
        // ★ 待机静止保障 (关键!):
        // v154.3 VelComp.update() 用 @MethodPriority(-1) 在所有 update 之前执行,
        // 会用 vel 移动位置 (VelComp.java L28: move(vel.x * delta, vel.y * delta))
        // 所以必须在 super.update() 之前清零 vel, 否则单位会以"上一帧残留 vel"持续平移
        //
        // ★ 根本原因: v154.3 玩家队伍用 CommandAI 而非 aiController (UnitType.java L280)
        //   playerControllable=true && team.isAI()=false → 用 CommandAI 不用 WormAI
        //   所以 WormAI.updateMovement 从未被调用, isIdle 标志没用
        //   修复: 直接检查 controller.target==null, 不依赖 WormAI
        if (isIdle()) {
            vel.setZero();
        }

        // PU132 WormDefaultUnit.update L71-72: 保存速度历史 (用于 updateSegmentVLocal 3 帧平均)
        lastVelocityD.set(lastVelocityC);
        lastVelocityC.set(vel);
        super.update();

        // ★ 待机静止保障 2: super.update() 后物理系统可能给 vel 加了值, 再次清零
        if (isIdle()) {
            vel.setZero();
        }

        // ★ 兜底: 如果 add() 没创建段身, 在第一次 update() 时创建 ★
        if (!segmentsCreated) {
            SegmentConfig cfg = type != null ? configs.get(type.name) : null;
            if (cfg != null) {
                try {
                    // 缓存贴图前缀 (兜底路径)
                    if (type != null && texturePrefix == null) texturePrefix = type.name + "-";
                    segmentSpacing = cfg.spacing;
                    regenTime = cfg.regenTime;
                    maxSegments = cfg.maxSegments;
                    wobbleEnabled = cfg.wobble;
                    splittable = cfg.splittable;
                    chainable = cfg.chainable;
                    segmentRotationRange = cfg.segmentRotationRange;
                    segmentDamageScl = cfg.segmentDamageScl;
                    healthDistributionRate = cfg.healthDistribution;
                    createSegments(cfg.count, cfg.segmentType);
                    segmentsCreated = true;
                    System.out.println("[头部] 段身创建: " + cfg.count + "节 间距=" + cfg.spacing
                        + " 转角=" + segmentRotationRange
                        + " 再生=" + (regenTime > 0 ? "开" : "关") + " 晃动=" + (wobbleEnabled ? "开" : "关")
                        + " 分裂=" + (splittable ? "开" : "关") + " 合并=" + (chainable ? "开" : "关"));
                } catch (Throwable t) {
                    System.out.println("[头部] 段身创建失败: " + t);
                    t.printStackTrace();
                    segmentsCreated = true;
                }
            } else if (defaultSegmentType != null) {
                // 旧路径 (向后兼容)
                try {
                    segmentSpacing = defaultSegmentSpacing;
                    createSegments(defaultSegmentCount, defaultSegmentType);
                    segmentsCreated = true;
                } catch (Throwable t) {
                    System.out.println("[头部] 旧路径创建失败: " + t);
                    t.printStackTrace();
                    segmentsCreated = true;
                }
            }
        }

        // 初始化位置/速度/朝向数组
        if (segPositions == null || segPositions.length != segments.length) {
            segPositions = new Vec2[segments.length];
            segVelocities = new Vec2[segments.length];
            segRotations = new float[segments.length];
            for (int i = 0; i < segments.length; i++) {
                segPositions[i] = new Vec2(x - (i + 1) * segmentSpacing, y);
                segVelocities[i] = new Vec2();
                segRotations[i] = rotation;  // 初始朝向 = 头部朝向
            }
        }

        // ★★★ 组合方案: PU132 约束算法 + 速度传播 + 154.3 clampRange ★★★
        //
        // 问题历史:
        // - 纯 PU132 算法: 会超过 90° 脱节 (clampedAngle 限制的是 seg.angleTo(ideal) 向量角度,
        //   不是段身真实朝向, 段身偏离 ideal 时可接近 180°)
        // - 纯 154.3 算法: 整体一起转, 无 "一节拉一节" 拖尾感 (slerp 让段身直接朝父段方向)
        //
        // 组合方案:
        // 1. updateSegmentVLocal (PU132): 速度传播, 让段身有惯性, 产生拖尾感
        // 2. PU132 约束算法: seg = ideal - trns(segRot, offset), 段身沿自己朝向滞后 ideal
        // 3. clampRange (154.3): 额外限制 segRot 相对父段不超过 ±segmentRotationRange
        //    从根源避免段身真实朝向超过父段±range, 防止脱节
        //
        // 单位修正: PU132 原版 segV 没乘 Time.delta (bug), 段身比头部快 60 倍导致抖动
        //          这里修正: segments[i].add(segV.x * delta, segV.y * delta)

        // 1. 速度传播 (PU132 updateSegmentVLocal L101-124)
        updateSegmentVLocal(lastVelocityC);

        // 2. PU132 约束算法 (updateSegmentsLocal L126-164) + clampRange 防脱节
        float segmentOffset = segmentSpacing / 2f;
        float parentRot = rotation;
        float parentX = x;
        float parentY = y;

        // === 第 0 段: 跟随头部 ===
        if (segments.length > 0 && segments[0] != null && segments[0].isAdded()) {
            Vec2 seg0 = segPositions[0];
            Vec2 segV0 = segVelocities[0];

            // 段身位置 += 速度 (PU132 L134), ★ 修正单位: 乘 Time.delta
            seg0.add(segV0.x * arc.util.Time.delta, segV0.y * arc.util.Time.delta);

            // 头部 rotation 调整: 向段身朝向转一点 (PU132 L136)
            rotation -= angleDistSigned(rotation, segRotations[0], angleLimit) / 1.25f;

            // 理想位置: 头部后方 segmentOffset 处 (PU132 L137)
            Tmp.v1.trns(rotation + 180f, segmentOffset).add(x, y);

            // 段身朝向 = 从段身指向理想位置 (PU132 L138)
            segRotations[0] = clampedAngle(seg0.angleTo(Tmp.v1), rotation, angleLimit);

            // ★ 154.3 clampRange: 额外限制段身真实朝向相对头部不超过 ±segmentRotationRange
            //   防止 PU132 算法在段身偏离 ideal 时 segRot 超过 90° 脱节
            segRotations[0] = clampRange(segRotations[0], rotation, segmentRotationRange);

            // PU132 约束 (L139-140): seg = ideal - trns(segRot, offset)
            Tmp.v2.trns(segRotations[0], segmentOffset).add(seg0).sub(Tmp.v1);
            seg0.sub(Tmp.v2);

            // 速度衰减 (PU132 L142)
            segV0.scl(Mathf.clamp(1f - (segmentDrag * arc.util.Time.delta)));

            // 应用到段身实体
            segments[0].syncToHead(seg0.x, seg0.y, segRotations[0]);

            parentRot = segRotations[0];
            parentX = seg0.x;
            parentY = seg0.y;
        }

        // === 后续段身 (i >= 1): 跟随前一段 ===
        for (int i = 1; i < segments.length; i++) {
            SegmentUnitEntity seg = segments[i];
            if (seg == null || !seg.isAdded()) continue;

            Vec2 segPos = segPositions[i];
            Vec2 segV = segVelocities[i];
            Vec2 segLast = segPositions[i - 1];
            float segLastRot = segRotations[i - 1];

            // 段身位置 += 速度 (PU132 L152), ★ 修正单位: 乘 Time.delta
            segPos.add(segV.x * arc.util.Time.delta, segV.y * arc.util.Time.delta);

            // 前一段朝向调整: 向当前段朝向转一点 (PU132 L154)
            segRotations[i - 1] -= angleDistSigned(segRotations[i - 1], segRotations[i], angleLimit) / 1.25f;

            // 理想位置: 前一段后方 segmentOffset 处 (PU132 L155)
            Tmp.v1.trns(segRotations[i - 1] + 180f, segmentOffset).add(segLast);

            // 段身朝向 = 从段身指向理想位置 (PU132 L156)
            segRotations[i] = clampedAngle(segPos.angleTo(Tmp.v1), segRotations[i - 1], angleLimit);

            // ★ 154.3 clampRange: 额外限制段身真实朝向相对前一段不超过 ±segmentRotationRange
            segRotations[i] = clampRange(segRotations[i], segRotations[i - 1], segmentRotationRange);

            // PU132 约束 (L157-158): seg = ideal - trns(segRot, offset)
            Tmp.v2.trns(segRotations[i], segmentOffset).add(segPos).sub(Tmp.v1);
            segPos.sub(Tmp.v2);

            // 速度衰减 (PU132 L160)
            segV.scl(Mathf.clamp(1f - (segmentDrag * arc.util.Time.delta)));

            // 应用到段身实体
            seg.syncToHead(segPos.x, segPos.y, segRotations[i]);
        }

        // 血量分布效率恢复 (PU132 WormDefaultUnit.update L74)
        healthDistributionEfficiency = Mathf.clamp(healthDistributionEfficiency + (arc.util.Time.delta / 160f));

        // 血量分布: 每段单独计算 (3 邻居局部平均, PU132 WormDefaultUnit.distributeHealth L179-202)
        // ★ splittable=true 时段身有独立血量, 不进行血量分布 (PU132 WormComp.update L249-258)
        if (healthDistributionRate > 0 && !splittable) {
            for (int i = 0; i < segments.length; i++) {
                distributeHealth(i);
            }
        }

        // ★ 再生 (PU132 WormDefaultUnit.update L81-94, regenTime > 0 时启用)
        if (regenAvailable()) {
            repairTime += arc.util.Time.delta;
            // 调试: 每 10 秒打一次再生进度
            debugRegenLogTimer += arc.util.Time.delta;
            if (debugRegenLogTimer >= 600f) {
                debugRegenLogTimer = 0f;
                System.out.println("[再生] 进度: " + segments.length + "/" + maxSegments
                    + " 计时=" + String.format("%.0f%%", repairTime / regenTime * 100));
            }
            if (repairTime >= regenTime) {
                // 扣血 + 长出新段
                float damage = (health / segments.length) / 2f;
                damage(damage);
                addSegment();
                repairTime = 0f;
                System.out.println("[再生] 新段: " + segments.length + "/" + maxSegments
                    + " 扣血=" + (int)damage + " 剩余=" + (int)health);
            }
        }

        // ★ 轻微晃动 (arcnelidia 启用, toxobyte 不启用)
        // 借鉴 v154.3 UnitComp.wobble(): 振幅 0.05f, 这里用 0.02f 更轻微
        if (wobbleEnabled) {
            x += Mathf.sin(arc.util.Time.time + (id % 10) * 12f, 25f, 0.02f) * arc.util.Time.delta * elevation;
            y += Mathf.cos(arc.util.Time.time + (id % 10) * 12f, 25f, 0.02f) * arc.util.Time.delta * elevation;
        }

        // ★ 链式合并扫描 (PU132 WormComp.updatePost L341-350, 每 5 秒扫描一次)
        if (chainable && segments.length > 0) {
            chainScanTimer += arc.util.Time.delta;
            if (chainScanTimer >= 300f) {  // 5 秒
                chainScanTimer = 0f;
                tryChainMerge();
            }
        }
    }

    /** 是否可再生 (PU132 WormDefaultUnit.regenAvailable L97-99)
     *  需 regenTime > 0, 段数未达上限 */
    public boolean regenAvailable() {
        return regenTime > 0f && segments.length < maxSegments;
    }

    /** 添加新段身 (PU132 WormDefaultUnit.addSegment L336-368, 简化版)
     *  在尾部追加一个新段身, 扩展所有数组 */
    public void addSegment() {
        if (segments.length <= 0) return;
        int oldLen = segments.length;
        int newLen = oldLen + 1;

        SegmentUnitEntity[] oldSegs = segments;
        Vec2[] oldPos = segPositions;
        Vec2[] oldVel = segVelocities;
        float[] oldRot = segRotations;

        segments = new SegmentUnitEntity[newLen];
        segPositions = new Vec2[newLen];
        segVelocities = new Vec2[newLen];
        segRotations = new float[newLen];

        for (int i = 0; i < oldLen; i++) {
            segments[i] = oldSegs[i];
            segPositions[i] = oldPos[i];
            segVelocities[i] = oldVel[i];
            segRotations[i] = oldRot[i];
        }

        if (segments[oldLen - 1] != null) {
            segments[oldLen - 1].isTail = false;
        }

        mindustry.type.UnitType segType = segments[oldLen - 1] != null ? segments[oldLen - 1].type : defaultSegmentType;
        SegmentUnitEntity newSeg = (SegmentUnitEntity) segType.create(team);

        Vec2 oldTailPos = oldPos[oldLen - 1];
        float oldTailRot = oldRot[oldLen - 1];
        Vec2 newPos = new Vec2();
        newPos.trns(oldTailRot + 180f, segmentSpacing).add(oldTailPos);

        newSeg.set(newPos.x, newPos.y);
        newSeg.rotation = oldTailRot;
        newSeg.head = this;
        newSeg.segmentIndex = oldLen;
        newSeg.isTail = true;
        newSeg.texturePrefix = type.name + "-";
        newSeg.elevation = elevation;
        newSeg.health = health;
        newSeg.maxHealth = maxHealth;
        newSeg.dead = false;
        newSeg.add();

        segPositions[oldLen] = newPos;
        segVelocities[oldLen] = new Vec2(segVelocities[oldLen - 1]);
        segRotations[oldLen] = oldTailRot;
        segments[oldLen] = newSeg;
    }

    /** 受伤降低血量分布效率 (PU132 WormDefaultUnit.damage L64-67) */
    @Override
    public void damage(float amount) {
        super.damage(amount);
        healthDistributionEfficiency = Mathf.clamp(healthDistributionEfficiency - (amount / 15f));
    }

    /** 获取段身 (index=-1 表示头部自己, PU132 WormDefaultUnit.getSegment L204-208) */
    protected Unit getSegment(int index) {
        if (index < 0) return this;
        if (index >= segments.length) return null;
        return segments[index];
    }

    /**
     * 3 邻居局部血量平均 (PU132 WormDefaultUnit.distributeHealth L179-202)
     * 对 index 段身, 取 (index-1, index, index+1) 三段做平均
     * index=-1 时表示头部自己 (与第 0 段一起平均)
     */
    protected void distributeHealth(int index) {
        int idx = 0;
        float mHealth = 0f;
        float mMaxHealth = 0f;
        for (int i = -1; i <= 1; i++) {
            Unit seg = getSegment(index + i);
            if (seg == null) break;
            mHealth += seg.health;
            mMaxHealth += seg.maxHealth;
            idx++;
        }
        if (idx == 0) return;
        mMaxHealth /= idx;
        mHealth /= idx;
        for (int i = -1; i <= 1; i++) {
            Unit seg = getSegment(index + i);
            if (seg == null) break;
            if (!Mathf.equal(seg.health, mHealth, 0.001f)) {
                seg.health = Mathf.lerpDelta(seg.health, mHealth, healthDistributionRate * healthDistributionEfficiency);
            }
            if (!Mathf.equal(seg.maxHealth, mMaxHealth, 0.001f)) {
                seg.maxHealth = Mathf.lerpDelta(seg.maxHealth, mMaxHealth, healthDistributionRate * healthDistributionEfficiency);
            }
        }
    }

    /**
     * 限制角度到 relative ± range 范围内 (154.3 Angles.clampRange 等价实现)
     * 直接限制段身真实朝向相对父段, 从根源避免超过 90° 脱节
     */
    protected static float clampRange(float angle, float relative, float range) {
        if (range >= 180f) return angle;
        float diff = angleDistSigned(angle, relative);
        if (Math.abs(diff) > range) {
            float target = diff > 0 ? relative + range : relative - range;
            return target % 360f;
        }
        return angle;
    }

    /**
     * 速度传播: 让段身继承头部速度, 产生丝滑惯性 (拖尾感)
     * (PU132 WormDefaultUnit.updateSegmentVLocal L101-124, 改良版)
     *
     * 每段速度 = max(前一段速度, 自己速度, 头部 3 帧平均速度)
     * 方向 = 段身 → 前一段 (跟在头部后面)
     *
     * ★ 静止抖动修复: 头部速度低于阈值时, 不传播速度, 段身 segV 快速衰减到 0
     * 之前 bug: trueVel = max(velocity, segV.len(), headVel), 头部静止时 segV.len() 仍有值
     *          导致段身持续移动产生抖动
     *
     * vec = lastVelocityC (上一帧头部速度)
     * lastVelocityD = 上上帧头部速度
     */
    protected void updateSegmentVLocal(Vec2 vec) {
        int len = segments.length;
        // ★ 静止阈值: 头部速度低于此值视为静止, 段身速度快速衰减
        float stillThreshold = type.speed * 0.05f;
        // 头部 3 帧速度平均
        Tmp.v3.set(vel).add(vec).add(lastVelocityD).scl(1f / 3f);
        float headAvgVel = Tmp.v3.len();
        boolean headStill = headAvgVel < stillThreshold;

        for (int i = 0; i < len; i++) {
            Vec2 seg = segPositions[i];
            Vec2 segV = segVelocities[i];
            segV.limit(type.speed);

            // ★ 静止时: 段身速度直接归零 (避免残留速度导致微震/漂移)
            if (headStill) {
                segV.setZero();
                segments[i].vel.setZero();
                continue;
            }

            // 方向: 段身指向前一段 (i=0 时指向头部)
            float angleB = i != 0
                ? Angles.angle(seg.x, seg.y, segPositions[i - 1].x, segPositions[i - 1].y)
                : Angles.angle(seg.x, seg.y, x, y);
            // 速度大小: 取前一段速度 (i=0 时取头部上一帧速度)
            float velocity = i != 0 ? segVelocities[i - 1].len() : vec.len();

            // 真实速度 = 三者最大值
            float trueVel = Math.max(Math.max(velocity, segV.len()), headAvgVel);
            // ★ 限制段身速度不超过头部速度, 避免段身冲过理想位置然后被拉回造成抖动
            // (toxobyte 25 段, 段间距 16.25 小, 段身密集更容易抖)
            trueVel = Math.min(trueVel, headAvgVel * 1.2f);
            Tmp.v1.trns(angleB, trueVel);
            segV.add(Tmp.v1);
            segV.setLength(trueVel);
            // counterDrag=false, 不额外衰减 (PU132 默认)
            // 同步到段身实体 vel, 让段身有真实物理速度
            segments[i].vel.set(segV);
        }
    }

    // ==================== PU132 角度工具方法 (Utils.java) ====================

    /** 带符号角度差 (PU132 Utils.angleDistSigned 第177行)
     *  返回 a 相对 b 的角度差, 范围 -180~180 */
    private static float angleDistSigned(float a, float b) {
        a += 360f;
        a %= 360f;
        b += 360f;
        b %= 360f;
        float d = Math.abs(a - b) % 360f;
        int sign = (a - b >= 0f && a - b <= 180f) || (a - b <= -180f && a - b >= -360f) ? 1 : -1;
        return (d > 180f ? 360f - d : d) * sign;
    }

    /** 带符号角度差, 超过 start 才返回差值 (PU132 Utils.angleDistSigned 第187行)
     *  用于头部/前一段朝向调整: 超过 angleLimit 才转 */
    private static float angleDistSigned(float a, float b, float start) {
        float dst = angleDistSigned(a, b);
        if (Math.abs(dst) > start) {
            return dst > 0 ? dst - start : dst + start;
        }
        return 0f;
    }

    /** 限制角度差 (PU132 Utils.clampedAngle 第200行)
     *  将 angle 限制在 relative ± limit 范围内
     *  用于段身朝向: 不能相对前一段转超过 angleLimit */
    private static float clampedAngle(float angle, float relative, float limit) {
        if (limit >= 180f) return angle;
        if (limit <= 0f) return relative;
        float dst = angleDistSigned(angle, relative);
        if (Math.abs(dst) > limit) {
            float val = dst > 0 ? dst - limit : dst + limit;
            return (angle - val) % 360f;
        }
        return angle;
    }

    @Override
    public void destroy() {
        super.destroy();
        // 销毁所有段身 (借鉴 PU132 WormDefaultUnit.destroy L234-267, 简化版)
        for (SegmentUnitEntity seg : segments) {
            if (seg == null || !seg.isAdded()) continue;
            seg.head = null;  // 避免段身死亡时再通知头部
            // 段身位置爆炸特效 (简化版, 不做 item 爆炸/wreck decal)
            float shake = seg.hitSize / 3f;
            Fx.explosion.at(seg);
            Effect.shake(shake, shake, seg);
            type.deathSound.at(seg);
            seg.remove();
        }
    }

    /** 重写 remove(): 确保所有段身也被移除 (借鉴 PU132 WormDefaultUnit.remove L270-276) */
    @Override
    public void remove() {
        super.remove();
        for (SegmentUnitEntity seg : segments) {
            if (seg != null && seg.isAdded()) {
                seg.head = null;
                seg.remove();
            }
        }
    }

    /** 重写 clipSize(): 让镜头边缘能看到整条虫子 (借鉴 PU132 WormDefaultUnit.clipSize L216-218) */
    @Override
    public float clipSize() {
        if (segments.length == 0) return super.clipSize();
        return segments.length * segmentSpacing * 2f;
    }

    // 注: v154.3 中影子由 UnitType.draw() 内部自动调用 drawShadow(unit)
    //    段身 flying=true 会自动画影子, 不需要重写 drawShadow() (UnitEntity 也没有此方法)
    //    段身 type.region 在 SegmentUnitEntity.draw() 中切换为 segment/tail 贴图,
    //    影子会用切换后的贴图, 与头部影子一起由 UnitType 自动绘制

    /** 段身死亡通知 (由 SegmentUnitEntity.kill 调用)
     *  ★ 分裂逻辑 (PU132 WormComp.remove L397-424):
     *  - splittable=true 且中间段身死亡: 后半段创建为新虫子
     *  - splittable=true 且尾部段身死亡: 直接移除尾部
     *  - splittable=false: 伤害已转移给头部, 段身死亡只移除自己 */
    public void onSegmentDied(SegmentUnitEntity seg) {
        int deadIdx = -1;
        for (int i = 0; i < segments.length; i++) {
            if (segments[i] == seg) { deadIdx = i; break; }
        }
        if (deadIdx < 0) return;

        // ★ 分裂: 中间段身死亡, 后半段创建为新虫子 (PU132 splittable)
        if (splittable && deadIdx < segments.length - 1) {
            // 后半段段身 [deadIdx+1, end)
            int tailLen = segments.length - deadIdx - 1;
            SegmentUnitEntity[] tailSegs = new SegmentUnitEntity[tailLen];
            Vec2[] tailPos = new Vec2[tailLen];
            Vec2[] tailVel = new Vec2[tailLen];
            float[] tailRot = new float[tailLen];
            for (int i = 0; i < tailLen; i++) {
                tailSegs[i] = segments[deadIdx + 1 + i];
                tailPos[i] = segPositions[deadIdx + 1 + i];
                tailVel[i] = segVelocities[deadIdx + 1 + i];
                tailRot[i] = segRotations[deadIdx + 1 + i];
            }
            // 创建新头部 (与原头部同类型)
            try {
                SegmentWormEntity newHead = (SegmentWormEntity) type.create(team);
                newHead.set(tailSegs[0].x, tailSegs[0].y);
                newHead.rotation = tailRot[0];
                newHead.segmentsCreated = true;  // 跳过自动创建段身
                newHead.splittable = true;
                newHead.chainable = chainable;
                newHead.segmentSpacing = segmentSpacing;
                newHead.regenTime = regenTime;
                newHead.maxSegments = maxSegments;
                newHead.wobbleEnabled = wobbleEnabled;
                newHead.add();
                // 转移后半段段身给新头部
                newHead.segments = tailSegs;
                newHead.segPositions = tailPos;
                newHead.segVelocities = tailVel;
                newHead.segRotations = tailRot;
                for (int i = 0; i < tailLen; i++) {
                    tailSegs[i].head = newHead;
                    tailSegs[i].segmentIndex = i;
                    tailSegs[i].isTail = (i == tailLen - 1);
                }
                // 播放分裂音效
                if (splitSound != null) splitSound.at(this);
                System.out.println("[分裂] 中间段#" + deadIdx + "死亡, 后半段" + tailLen + "节成为新虫子");
            } catch (Throwable t) {
                System.out.println("[分裂] 创建新头部失败: " + t);
                t.printStackTrace();
            }
        } else if (splittable && splitSound != null) {
            // 尾部段身死亡, 播放分裂音效
            splitSound.at(this);
        }

        // 从当前头部列表中移除死段及后半段 (分裂时后半段已转移给新头部)
        int newLen = splittable ? deadIdx : 0;
        if (!splittable) {
            // 非分裂模式: 保留所有存活段身
            for (SegmentUnitEntity s : segments) {
                if (s != null && s != seg && s.isAdded()) newLen++;
            }
        }
        SegmentUnitEntity[] newSegs = new SegmentUnitEntity[newLen];
        Vec2[] newPos = new Vec2[newLen];
        Vec2[] newVel = new Vec2[newLen];
        float[] newRot = new float[newLen];
        int idx = 0;
        if (splittable) {
            // 分裂模式: 只保留死段之前的段身 [0, deadIdx)
            for (int i = 0; i < deadIdx; i++) {
                if (segments[i] != null && segments[i].isAdded()) {
                    newSegs[idx] = segments[i];
                    newPos[idx] = segPositions[i];
                    newVel[idx] = segVelocities[i];
                    newRot[idx] = segRotations[i];
                    newSegs[idx].segmentIndex = idx;
                    newSegs[idx].isTail = (idx == newLen - 1);
                    idx++;
                }
            }
        } else {
            // 非分裂模式: 保留所有存活段身
            for (int i = 0; i < segments.length; i++) {
                if (segments[i] != null && segments[i] != seg && segments[i].isAdded()) {
                    newSegs[idx] = segments[i];
                    newPos[idx] = segPositions[i];
                    newVel[idx] = segVelocities[i];
                    newRot[idx] = segRotations[i];
                    newSegs[idx].segmentIndex = idx;
                    newSegs[idx].isTail = (idx == newLen - 1);
                    idx++;
                }
            }
        }
        segments = newSegs;
        segPositions = newPos;
        segVelocities = newVel;
        segRotations = newRot;
        System.out.println("[头部] 段身死亡: #" + deadIdx + " 剩余=" + segments.length
            + (splittable && deadIdx < segments.length + 1 ? " 已分裂" : ""));
    }

    /**
     * 链式合并扫描 (PU132 WormComp.updatePost L341-350)
     * 每 5 秒扫描附近, 找到同类型尾部虫子合并
     */
    protected void tryChainMerge() {
        if (maxSegments > 0 && segments.length >= maxSegments) return;  // 已达上限
        if (segments.length == 0) return;
        // 扫描头部前方 segmentSpacing/2 范围内的同类型尾部虫子
        mindustry.entities.Units.nearby(team, x, y, segmentSpacing * 2f, u -> {
            if (!(u instanceof SegmentWormEntity)) return;
            SegmentWormEntity other = (SegmentWormEntity) u;
            if (other == this) return;
            if (other.type != type) return;  // 同类型
            if (other.segments.length == 0) return;  // 对方有段身
            // 对方尾部段身
            SegmentUnitEntity otherTail = other.segments[other.segments.length - 1];
            // 距离检查
            if (!within(otherTail, segmentSpacing * 1.2f)) return;
            // 总段数检查
            int totalLen = segments.length + other.segments.length;
            if (maxSegments > 0 && totalLen >= maxSegments) return;
            // 合并: 把对方的段身追加到自己后面
            mergeFrom(other);
        });
    }

    /**
     * 把 other 的所有段身合并到自己后面 (PU132 WormComp.connect L62-81)
     * 合并后 other 头部移除 (段身全部转移给 this)
     */
    public void mergeFrom(SegmentWormEntity other) {
        int myLen = segments.length;
        int otherLen = other.segments.length;
        int newLen = myLen + otherLen;

        SegmentUnitEntity[] newSegs = new SegmentUnitEntity[newLen];
        Vec2[] newPos = new Vec2[newLen];
        Vec2[] newVel = new Vec2[newLen];
        float[] newRot = new float[newLen];

        // 复制自己的段身
        for (int i = 0; i < myLen; i++) {
            newSegs[i] = segments[i];
            newPos[i] = segPositions[i];
            newVel[i] = segVelocities[i];
            newRot[i] = segRotations[i];
        }
        // 追加 other 的段身
        for (int i = 0; i < otherLen; i++) {
            SegmentUnitEntity s = other.segments[i];
            newSegs[myLen + i] = s;
            newPos[myLen + i] = other.segPositions[i];
            newVel[myLen + i] = other.segVelocities[i];
            newRot[myLen + i] = other.segRotations[i];
            s.head = this;  // 段身归属改为 this
            s.segmentIndex = myLen + i;
            s.isTail = (myLen + i == newLen - 1);  // 最后一节是尾部
        }
        // 旧尾部的 isTail 改为 false (不再是尾部)
        if (myLen > 0) {
            newSegs[myLen - 1].isTail = false;
        }
        segments = newSegs;
        segPositions = newPos;
        segVelocities = newVel;
        segRotations = newRot;

        // 移除 other 头部 (段身已转移, other 不再持有段身)
        other.segments = new SegmentUnitEntity[0];
        other.segPositions = null;
        other.segVelocities = null;
        other.segRotations = null;
        other.remove();

        // 播放合并音效
        if (chainSound != null) chainSound.at(this);
        System.out.println("[合并] " + type.name + " 吸收对方" + otherLen + "节, 总段数=" + newLen);
    }

    /** 创建段身 (在 add() 时调用一次) */
    public void createSegments(int count, mindustry.type.UnitType segmentType) {
        segments = new SegmentUnitEntity[count];
        segPositions = new Vec2[count];
        segVelocities = new Vec2[count];
        segRotations = new float[count];

        // ★ 段数多的虫子生成时卷起来 (如 toxobyte 25 段)
        //   波浪形排列: 正弦波 + 随机偏移, 越靠后摆动越大
        //   段数 <= 3: 基本直线 (catenapede 2 段)
        //   段数 4~10: 轻微波浪 (arcnelidia 9 段)
        //   段数 > 10: 明显卷起来 (toxobyte 25 段)
        float maxWaveAmp = count > 3 ? Math.min(count * 3f, 90f) : 0f; // 最大摆动角度
        float waveFreq = 0.15f + Mathf.random(0.25f); // 随机频率, 每次生成不同
        float wavePhase = Mathf.random(360f);         // 随机相位, 每次生成不同
        float baseAngle = rotation + 180f;

        float segX = x;
        float segY = y;
        for (int i = 0; i < count; i++) {
            // 越靠后的段, 摆动幅度越大 (distFactor 0~1)
            float distFactor = i / (float) Math.max(count - 1, 1);
            float waveAmp = maxWaveAmp * distFactor;

            // 正弦波偏移 + 随机噪声
            float sineOffset = Mathf.sin(distFactor * waveFreq * Mathf.PI2 + wavePhase) * waveAmp;
            float randomOffset = Mathf.random(-waveAmp * 0.35f, waveAmp * 0.35f);
            float angle = baseAngle + sineOffset + randomOffset;

            // 沿角度方向前进 segmentSpacing
            segX += Angles.trnsx(angle, segmentSpacing);
            segY += Angles.trnsy(angle, segmentSpacing);

            // 用 segmentType 创建段身 (SegmentUnitEntity 实例)
            SegmentUnitEntity seg = (SegmentUnitEntity) segmentType.create(team);
            seg.set(segX, segY);
            seg.rotation = angle;
            seg.head = this;
            seg.segmentIndex = i;  // 段身在数组中的索引, 用于 z 层级
            // 最后一节段身是 tail (用 tail 贴图而不是 segment 贴图)
            seg.isTail = (i == count - 1);
            // ★ 设置贴图前缀 = 头部 type.name + "-" (如 "arcnelidia-" / "toxobyte-")
            seg.texturePrefix = type.name + "-";
            seg.add();

            segments[i] = seg;
            segPositions[i] = new Vec2(seg.x, seg.y);
            segVelocities[i] = new Vec2();  // 初始速度为 0
            segRotations[i] = angle;        // 初始朝向 = 该段角度
        }
    }

    @Override
    public void add() {
        super.add();
        // 缓存贴图前缀
        if (type != null) texturePrefix = type.name + "-";
        // 在头部被添加到世界时创建段身 (优先用 configs Map, 否则用旧静态字段)
        if (!segmentsCreated && segments.length == 0) {
            SegmentConfig cfg = type != null ? configs.get(type.name) : null;
            if (cfg != null) {
                segmentSpacing = cfg.spacing;
                regenTime = cfg.regenTime;
                maxSegments = cfg.maxSegments;
                wobbleEnabled = cfg.wobble;
                splittable = cfg.splittable;
                chainable = cfg.chainable;
                healthDistributionRate = cfg.healthDistribution;
                try {
                    createSegments(cfg.count, cfg.segmentType);
                    segmentsCreated = true;
                    System.out.println("[头部] 启动: " + type.name + " 段身=" + cfg.count + "节"
                        + " 再生=" + (regenTime > 0 ? "开" : "关") + " 晃动=" + (wobbleEnabled ? "开" : "关")
                        + " 分裂=" + (splittable ? "开" : "关") + " 合并=" + (chainable ? "开" : "关"));
                } catch (Throwable t) {
                    System.out.println("[头部] 段身创建失败: " + t);
                    t.printStackTrace();
                    segmentsCreated = true;
                }
            } else if (defaultSegmentType != null) {
                segmentSpacing = defaultSegmentSpacing;
                try {
                    createSegments(defaultSegmentCount, defaultSegmentType);
                    segmentsCreated = true;
                } catch (Throwable t) {
                    System.out.println("[头部] 旧路径创建失败: " + t);
                    t.printStackTrace();
                    segmentsCreated = true;
                }
            }
        }
    }

    /**
     * 重写 draw(): 只画头部, 段身自己画 (SegmentUnitEntity.draw)
     *
     * 段身的 draw 会临时切换 type.region 为 segment/tail 贴图, 然后调用 super.draw()
     * 这样段身用 UnitType 默认逻辑画, 贴图查找最可靠
     *
     * z 层级控制:
     * - 头部 z = Layer.flyingUnit (默认)
     * - 段身 0 z = 头部z - 1/10000 (头部覆盖第1节)
     * - 段身 1 z = 头部z - 2/10000 (第1节覆盖第2节)
     * - ...
     *
     * (借鉴 PU132 UnityUnitType.drawBody 第669行)
     */
    @Override
    public void draw() {
        // 只画头部, 段身会自己画
        super.draw();

        // ★ 再生建造动画 (参考 PU132 UnityUnitType.drawBody 第670-675行 + UnitSpawnAbility.draw)
        // 当可再生时, 在尾部后面绘制扫描效果
        if (regenAvailable() && segments.length > 0) {
            SegmentUnitEntity tail = segments[segments.length - 1];
            if (tail != null && tail.isAdded()) {
                arc.util.Tmp.v1.trns(tail.rotation + 180f, segmentSpacing).add(tail);
                float sx = arc.util.Tmp.v1.x, sy = arc.util.Tmp.v1.y;

                // 查找尾部贴图 (与 SegmentUnitEntity.draw 中相同的逻辑)
                String p = texturePrefix != null ? texturePrefix : "arcnelidia-";
                String modP = "create-" + p;
                arc.graphics.g2d.TextureRegion tailRegion = findRegion(p + "tail", modP + "tail");

                float progress = repairTime / regenTime;
                // 在尾部段身 z 层级之下绘制 (更靠后), 避免与段身贴图重叠
                float drawZ = Draw.z() - (segments.length + 2f) / 10000f;

                Draw.draw(drawZ, () -> {
                    // 1) 始终可见的脉动扫描圈 (progress 低时也看得见)
                    float pulse = 0.6f + 0.4f * Mathf.sin(repairTime / 10f); // 脉动 0.6~1.0
                    float radius = 6f + pulse * 8f; // 半径 6~14
                    Draw.color(mindustry.graphics.Pal.accent, pulse * 0.5f);
                    arc.graphics.g2d.Lines.stroke(2f * pulse);
                    arc.graphics.g2d.Lines.circle(sx, sy, radius);

                    // 2) 扫描线 (随 progress 从底部扫到顶部)
                    float scanY = Mathf.lerp(-radius, radius, progress);
                    Draw.alpha(pulse * 0.7f);
                    arc.graphics.g2d.Lines.stroke(1.5f);
                    arc.graphics.g2d.Lines.line(sx - radius, sy + scanY, sx + radius, sy + scanY);

                    Draw.reset();

                    // 3) Drawf.construct 显示建造进度 (shader 裁剪, progress 低时很淡)
                    if (tailRegion.found()) {
                        mindustry.graphics.Drawf.construct(
                            sx, sy,
                            tailRegion,
                            tail.rotation - 90f,
                            progress,
                            1f,
                            repairTime
                        );
                    }
                });
            }
        }
    }

    /** 查找贴图: 先试 name, 找不到再试 prefixedName (与 SegmentUnitEntity 相同逻辑) */
    private static arc.graphics.g2d.TextureRegion findRegion(String name, String prefixedName) {
        arc.graphics.g2d.TextureRegion r = arc.Core.atlas.find(name);
        if (r.found()) return r;
        return arc.Core.atlas.find(prefixedName);
    }
}
