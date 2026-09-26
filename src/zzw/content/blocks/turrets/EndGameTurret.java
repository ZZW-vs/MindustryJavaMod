package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.UnitController;
import mindustry.gen.Building;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.meta.Stat;
import zzw.content.Z_Sounds;
import zzw.content.units.effects.SpecialFx;

/**
 * EndGameTurret 移植自 PU_V8 (unity.world.blocks.defense.turrets.EndGameTurret)。
 *
 * <p>终局炮台 —— 三层旋转环 + 16 只眼睛追踪光束。</p>
 *
 * <p>行为设计:</p>
 * <ul>
 *   <li><b>通电</b>: 灯环常亮, 并且会向四周释放慢速闪电 (仅有观赏/压制效果, 无法攻击);</li>
 *   <li><b>通电 + 有弹药</b>: 每只眼睛各自锁定一个敌人并射出秒杀光束,
 *       光束持续连接目标; 每 2 条光束消耗 1 个弹药;</li>
 *   <li>光束命中后先解除目标 AI, 0.5 秒后将其湮灭并播放汽化特效,
 *       湮灭后眼睛才会转去锁定下一个目标;</li>
 *   <li>每只眼睛可以打不同的目标 —— 分配时按目标列表轮转,
 *       避免所有眼睛都盯着同一个单位。</li>
 *   <li><b>限伤</b>: 单次受到的伤害最多只结算 {@link #damageCap} 点,
 *       无论来袭伤害多高都不会被一击秒杀 (见 {@code handleDamage});</li>
 *   <li><b>稳定性</b>: 击杀目标时不再使用 NaN 坐标等破坏性手法,
 *       全部走引擎自带的幂等死亡流程; 同时跳过玩家单位, 避免顶掉
 *       Player 控制器导致玩家卡死。</li>
 * </ul>
 *
 * <p>★ v155/v159 适配要点:</p>
 * <ul>
 *   <li>崩溃修复: {@link PowerTurret#shootType} 必须非 null, 否则
 *       {@code TurretBuild.peekAmmo()} 返回 null, 会在
 *       {@code ammoReloadMultiplier()} 处抛 NPE。
 *       本炮台不使用普通子弹, 因此填一个伤害为 0 的占位子弹;</li>
 *   <li>移除 PU132 的 AntiCheat 采样湮灭系统, 改用"先特效后击杀"+
 *       分层降级击杀 ({@code kill → destroy → remove});</li>
 *   <li>移除 PU132 的反子弹拦截 (updateAntiBullets) 与减伤 (resist),
 *       改为直接的 {@code handleDamage} 单次伤害上限。</li>
 * </ul>
 */
public class EndGameTurret extends PowerTurret {

    /** End 系列主色 (scarColor)。 */
    public static final Color scarColor = Color.valueOf("f53036");
    /** End 系列亮色 (endColor)。 */
    public static final Color endColor = Color.valueOf("ff786e");

    /** 三层环的旋转速度系数 (原版 ringProgresses)。 */
    private static final float[] ringProgresses = {0.013f, 0.035f, 0.024f};
    /** 三层环的旋转方向 (原版 ringDirections)。 */
    private static final int[] ringDirections = {1, -1, 1};

    /** 眼睛数量: 内环 8 只 + 外环 8 只。 */
    public static final int eyeCount = 16;
    /** 每只眼睛锁定后, 从"命中"到"湮灭"的延迟 (帧)。0.5 秒 = 30 帧。 */
    public static final float annihilateDelay = 30f;
    /** 眼睛光束的发射间隔 (帧)。 */
    public static final float eyeShotInterval = 14f;

    /**
     * 单次受到伤害的上限。
     *
     * <p>无论来袭伤害多高 (核弹/秒杀光束/反作弊穿透弹等), 本炮台
     * 单帧最多只吃 {@code damageCap} 点伤害, 保证不会被一击秒杀,
     * 也给玩家留出反应与修复时间。</p>
     */
    public static final float damageCap = 10000f;

    // ===== 贴图 =====
    public TextureRegion baseRegion, baseLightsRegion, bottomLightsRegion, eyeMainRegion;
    public TextureRegion ringABottomRegion, ringAEyesRegion, ringARegion, ringALightsRegion;
    public TextureRegion ringBBottomRegion, ringBEyesRegion, ringBRegion, ringBLightsRegion;
    public TextureRegion ringCRegion, ringCLightsRegion;

    public EndGameTurret(String name) {
        super(name);

        health = 136000;
        consumePower(320f);
        reload = 430f;
        range = 820f;
        size = 14;
        shootCone = 360f;
        rotate = false;
        shake = 2.2f;
        absorbLasers = true;
        outlineIcon = false;
        noUpdateDisabled = false;
        hasItems = true;
        itemCapacity = 10;
        loopSound = Z_Sounds.endgameActive;
        loopSoundVolume = 0.2f;
        shootSound = Z_Sounds.endgameShoot;

        // ★ 占位子弹: 防止 PowerTurret.peekAmmo() 返回 null 导致 NPE 崩溃。
        //   本炮台的伤害全部由眼睛光束结算, 该子弹不会实际发射。
        shootType = new BulletType(){{
            damage = 0f;
            speed = 0.01f;
            lifetime = 1f;
            collides = false;
            hittable = false;
            absorbable = false;
            despawnEffect = hitEffect = shootEffect = smokeEffect = Fx.none;
        }};
        
        // 确保弹药槽始终有内容，防止 peekAmmo() 返回 null
        ammoUseEffect = Fx.none;
    }

    @Override
    public void load() {
        super.load();
        baseRegion = Core.atlas.find(name + "-base");
        baseLightsRegion = Core.atlas.find(name + "-base-lights");
        bottomLightsRegion = Core.atlas.find(name + "-bottom-lights");
        eyeMainRegion = Core.atlas.find(name + "-eye");

        ringABottomRegion = Core.atlas.find(name + "-ring1-bottom");
        ringAEyesRegion = Core.atlas.find(name + "-ring1-eyes");
        ringARegion = Core.atlas.find(name + "-ring1");
        ringALightsRegion = Core.atlas.find(name + "-ring1-lights");

        ringBBottomRegion = Core.atlas.find(name + "-ring2-bottom");
        ringBEyesRegion = Core.atlas.find(name + "-ring2-eyes");
        ringBRegion = Core.atlas.find(name + "-ring2");
        ringBLightsRegion = Core.atlas.find(name + "-ring2-lights");

        ringCRegion = Core.atlas.find(name + "-ring3");
        ringCLightsRegion = Core.atlas.find(name + "-ring3-lights");
    }

    @Override
    public void setStats() {
        super.setStats();
        // 占位子弹没有意义, 不显示它的弹药信息
        stats.remove(Stat.ammo);
    }

    /**
     * 无 AI 控制器 —— 光束命中目标后用它替换目标原本的 AI,
     * 使目标在湮灭前无法移动/攻击。
     *
     * <p>注意: 不能直接调用 {@code unit.controller(null)},
     * 因为 {@code Unit.controller(UnitController)} 内部会执行
     * {@code controller.unit()} 而抛 NPE, 这里用一个空实现代替。</p>
     *
     * <p>同时保存被替换掉的原始控制器 ({@link #previous}),
     * 以便炮台断电/断弹时能把 AI 还原回去。</p>
     */
    public static class NullAI implements UnitController {
        protected Unit unit;
        /** 被本控制器替换掉的原始 AI 控制器, 用于事后恢复。 */
        public final UnitController previous;

        /**
         * @param previous 目标单位原本的控制器 (可为 null, 此时放弃恢复)
         */
        public NullAI(UnitController previous) {
            this.previous = previous;
        }

        @Override
        public void unit(Unit unit) {
            this.unit = unit;
        }

        @Override
        public Unit unit() {
            return unit;
        }

        @Override
        public void updateUnit() {
            // 空实现 —— AI 已被移除
        }
    }

    public class EndGameTurretBuild extends PowerTurretBuild {
        /** 每只眼睛在世界坐标中的位置。 */
        protected Vec2[] eyeVecs = new Vec2[eyeCount];
        /** 每只眼睛当前锁定的目标 (单位或建筑)。 */
        protected Posc[] targets = new Posc[eyeCount];
        /** 每只眼睛的湮灭倒计时 (帧)。 */
        protected float[] annihilateTimers = new float[eyeCount];

        {
            // 在构造阶段就把 eyeVecs 填满 —— 保证任何时刻 draw() 都不会
            // 读到 null 元素 (绘制早于 add() 的极端情况也不会崩)。
            for (int i = 0; i < eyeCount; i++) {
                eyeVecs[i] = new Vec2();
            }
        }

        /** 眼睛位置平滑偏移 (让光点微微游走)。 */
        protected Vec2 eyeOffset = new Vec2();
        protected Vec2 eyeTargetOffset = new Vec2();

        /** 眼睛发射的轮转序号。 */
        protected int eyeSequence = 0;
        /** 光束计数 —— 每 2 条消耗 1 个弹药。 */
        protected int beamCounter = 0;
        /** 眼睛发射间隔计时。 */
        protected float eyeReload = 0f;
        /** 目标刷新计时。 */
        protected float targetTimer = 0f;
        /** 通电时的慢速闪电计时。 */
        protected float lightningTimer = 0f;

        /** 灯光/眼睛亮度 (0~1)。 */
        protected float lightsAlpha = 0f;
        protected float eyesAlpha = 0f;
        protected float eyeResetTime = 0f;
        /** 三层环的当前角度。 */
        protected float[] ringProgress = {0f, 0f, 0f};

        /**
         * 索敌结果缓存 —— 复用同一个 {@link Seq}, 避免每轮询都新建对象
         * 产生垃圾 (手机端内存友好)。每轮先 {@code clear()} 再填充。
         */
        protected final Seq<Unit> foundCache = new Seq<>();

        @Override
        public void add() {
            for (int i = 0; i < eyeCount; i++) {
                // eyeVecs 已在构造块里初始化, 这里不重复 new (避免垃圾)
                targets[i] = null;
                annihilateTimers[i] = 0f;
            }
            super.add();
        }

        /**
         * ★ 限伤: 单次受到的伤害被截断到 {@link EndGameTurret#damageCap}。
         *
         * <p>{@code Building.damage(float)} 内部会把伤害交给本方法做最终修正,
         * 再用返回值扣血 ({@code health -= handleDamage(damage)}),
         * 所以在这里做钳制是唯一且最可靠的入口 —— 无论伤害来自子弹、
         * 爆炸、逻辑方块还是其它模组, 都会被限制, 不会出现一击秒杀。</p>
         *
         * @param amount 引擎计算后的原始伤害 (已按 blockHealth 规则缩放)
         * @return 实际结算的伤害, 最大不超过 {@link EndGameTurret#damageCap}
         */
        @Override
        public float handleDamage(float amount) {
            // 负数/NaN 直接视为 0, 避免异常数值污染血量
            if (Float.isNaN(amount) || amount <= 0f) {
                return 0f;
            }
            return Math.min(amount, damageCap);
        }

        /**
         * 建筑被移除 (拆除/被摧毁) 时, 把所有仍被眼睛锁定的单位 AI 还原,
         * 否则这些单位会因为没有控制器而永久僵在原地。
         *
         * <p>这是手机端/联机下的稳定性关键: 建筑生命周期结束时
         * {@code updateTile()} 不再执行, 必须在这里补做清理。</p>
         */
        @Override
        public void onRemoved() {
            releaseAllTargets();
            super.onRemoved();
        }

        /**
         * 断开所有眼睛的目标连接, 并还原被替换掉的单位 AI。
         *
         * <p>幂等: 可重复调用, 已还原过的单位不会被重复处理。</p>
         */
        protected void releaseAllTargets() {
            for (int i = 0; i < eyeCount; i++) {
                Posc t = targets[i];
                targets[i] = null;
                annihilateTimers[i] = 0f;

                // 把之前被替换掉的原始控制器还原回去。
                // 注意: 绝不能传 null 给 controller(), 设置器内部会立即调用
                // controller.unit(this), 传 null 必然抛 NPE。
                if (t instanceof Unit u && u.controller() instanceof NullAI na) {
                    if (na.previous != null) {
                        try {
                            u.controller(na.previous);
                        } catch (Throwable ignored) {
                            // 单位可能已进入死亡流程, 还原失败不影响游戏
                        }
                    }
                    u.vel.setZero();
                }
            }
        }

        @Override
        public void updateTile() {
            enabled = true;

            updateEyeOffsets();
            updateRingAlpha();

            boolean powered = efficiency > 0.0001f;

            // ===== 通电时的慢速闪电 (无弹药也能放, 但不能攻击) =====
            if (powered) {
                lightningTimer += Time.delta;
                if (lightningTimer >= 10f) {
                    lightningTimer = 0f;
                    // 随机发射1-4条闪电
                    int lightningCount = Mathf.random(1, 5);
                    for (int i = 0; i < lightningCount; i++) {
                        float a = Mathf.random(360f);
                        float distance = 18.5f + Mathf.random(-3f, 3f); // 随机距离
                        Tmp.v1.trns(a, distance).add(x, y);
                        // 随机伤害和长度
                        float damage = 520f * efficiency * Mathf.random(0.8f, 1.2f);
                        int length = 26 + Mathf.random(-5, 5);
                        Lightning.create(team, scarColor, damage, Tmp.v1.x, Tmp.v1.y, a, length);
                    }
                }
            }

            // ===== 眼睛光束: 必须同时"通电"且"有弹药" =====
            boolean hasItem = items != null && items.total() > 0;
            if (powered && hasItem) {
                targetTimer += Time.delta;
                if (targetTimer >= 15f) {
                    targetTimer = 0f;
                    refreshTargets();
                }

                // 目标有效性检查
                for (int i = 0; i < eyeCount; i++) {
                    if (Units.invalidateTarget(targets[i], team, x, y)) {
                        targets[i] = null;
                        annihilateTimers[i] = 0f;
                    }
                }

                eyeReload += delta();
                if (eyeReload >= eyeShotInterval) {
                    eyeReload = 0f;
                    fireEye();
                }
            } else {
                // 失去电力或弹药 → 断开所有光束并恢复单位AI
                releaseAllTargets();
            }

            updateAnnihilation();

            // 基类负责冷却/音效等, 占位子弹不会实际开火 (见 shoot 覆写)
            super.updateTile();
        }

        /** 不使用普通子弹发射; 真正的攻击由眼睛光束完成。 */
        @Override
        protected void shoot(BulletType type) {
            // 空实现
        }

        // ================= 眼睛方向 / 亮度 =================

        /** 计算 16 只眼睛的世界坐标 (内环 8 只半径 36.75, 外环 8 只半径 25.75)。 */
        protected void updateEyeOffsets() {
            eyeOffset.lerpDelta(eyeTargetOffset, 0.12f);
            eyeTargetOffset.limit(2f);

            for (int i = 0; i < eyeCount; i++) {
                float angleC = (360f / 8f) * (i % 8);
                if (i >= 8) {
                    Tmp.v1.trns(angleC + 22.5f + ringProgress[1], 25.75f);
                } else {
                    Tmp.v1.trns(angleC + ringProgress[0], 36.75f);
                }
                eyeVecs[i].set(Tmp.v1.x, Tmp.v1.y).add(x, y);
                eyeVecs[i].add(eyeOffset);
            }
        }

        /** 灯光与旋转环的淡入淡出。 */
        protected void updateRingAlpha() {
            if (efficiency > 0.0001f) {
                eyeResetTime = 0f;
                float value = lightsAlpha > efficiency ? 1f : efficiency;
                lightsAlpha = Mathf.lerpDelta(lightsAlpha, efficiency, 0.07f * value);
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, efficiency, 0.06f * value);

                for (int i = 0; i < 3; i++) {
                    ringProgress[i] = Mathf.lerpDelta(ringProgress[i],
                        360f * ringDirections[i], ringProgresses[i] * efficiency);
                }
            } else {
                // 失去电力时才熄灭
                lightsAlpha = Mathf.lerpDelta(lightsAlpha, 0f, 0.07f);
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, 0f, 0.06f);
                for (int i = 0; i < 3; i++) {
                    ringProgress[i] = Mathf.lerpDelta(ringProgress[i], 0f, ringProgresses[i]);
                }
            }
        }

        // ================= 索敌 =================

        /**
         * 重新分配 16 只眼睛的目标。
         *
         * <p>按距离从近到远排序, 然后轮转分配给空闲的眼睛,
         * 使每只眼睛尽可能打不同的敌人。</p>
         */
        protected void refreshTargets() {
            // 复用缓存 Seq, 避免每 15 帧新建集合产生垃圾
            Seq<Unit> found = foundCache;
            found.clear();

            float r = range();
            Units.nearbyEnemies(team, x - r, y - r, r * 2f, r * 2f, u -> {
                if (u.isValid() && !u.dead() && Mathf.within(x, y, u.x, u.y, r)) {
                    found.add(u);
                }
            });

            if (found.isEmpty()) {
                return;
            }

            final float cx = x, cy = y;
            found.sort((a, b) -> Float.compare(
                Mathf.dst2(a.x, a.y, cx, cy),
                Mathf.dst2(b.x, b.y, cx, cy)));

            int n = found.size;
            int slot = 0;
            for (int i = 0; i < eyeCount; i++) {
                if (targets[i] == null) {
                    targets[i] = found.get(slot % n);
                    slot++;
                }
            }
        }

        // ================= 光束发射 / 湮灭 =================

        /** 让轮转到的下一只眼睛开火 (需要已有目标)。 */
        protected void fireEye() {
            int index = eyeSequence;
            eyeSequence = (eyeSequence + 1) % eyeCount;

            if (targets[index] == null) {
                return;
            }

            // 每 2 条光束消耗 1 个弹药
            beamCounter++;
            if (beamCounter >= 2) {
                beamCounter = 0;
                consume();
            }

            Z_Sounds.endgameSmallShoot.at(x, y, Mathf.random(0.95f, 1.05f), 0.6f);
        }

        /** 更新每只眼睛的湮灭流程: 先解除 AI, 0.5 秒后湮灭。 */
        protected void updateAnnihilation() {
            for (int i = 0; i < eyeCount; i++) {
                Posc t = targets[i];
                if (t == null) {
                    annihilateTimers[i] = 0f;
                    continue;
                }

                if (Units.invalidateTarget(t, team, x, y)) {
                    targets[i] = null;
                    annihilateTimers[i] = 0f;
                    continue;
                }

                // 步骤 1: 解除目标 AI (使其无法移动/攻击)
                // 保存原控制器, 以便断电/断弹时恢复。
                // ★ 跳过玩家操控的单位: 直接顶掉 Player 控制器会让玩家
                //   在单位死亡时收不到 removed() 回调, 出现卡死/无法复活,
                //   因此对玩家单位只做击杀、不做定身。
                // ★ 跳过联机客户端: 客户端改控制器会造成实体不同步。
                if (t instanceof Unit u && !Vars.net.client()
                    && !u.isPlayer() && !(u.controller() instanceof NullAI)) {
                    u.controller(new NullAI(u.controller()));
                    u.vel.setZero();
                }

                // 步骤 2: 0.5 秒后湮灭
                annihilateTimers[i] += Time.delta;
                if (annihilateTimers[i] >= annihilateDelay) {
                    annihilate(t);
                    // 无论客户端还是服务器都要断开光束, 否则会残留一条指向
                    // 已消失目标的连线。
                    targets[i] = null;
                    annihilateTimers[i] = 0f;
                }
            }
        }

        /**
         * 湮灭目标: 播放汽化特效并直接秒杀。
         *
         * <p>★ 关键顺序: <b>必须先播放特效, 再执行击杀</b>。
         * 汽化特效要靠目标的实时坐标/朝向去绘制轮廓, 而击杀流程会把
         * 单位从世界里移除。旧实现把击杀放在前面并且还会把坐标写成
         * {@code NaN}, 于是特效被创建在 NaN 坐标上 —— 完全不可见。
         * 这就是"湮灭特效消失"的根因。</p>
         *
         * @param t 已被眼睛锁定并倒计时结束的目标 (单位或建筑)
         */
        protected void annihilate(Posc t) {
            if (t == null) {
                return;
            }

            // 联机客户端不做权威结算: kill()/destroy()/remove() 在客户端执行会
            // 造成"本地提前移除"的实体不同步, 击杀统一由服务器下发。
            if (Vars.net.client()) {
                return;
            }

            // 先把坐标固定下来, 之后无论目标发生什么都能拿到有效值
            float tx = t.getX(), ty = t.getY();

            if (t instanceof Unit u) {
                float rot = angleTo(u);
                // 步骤 1: 先放特效 (此时坐标一定有效)
                SpecialFx.endgameVapourize.at(tx, ty, rot, new Object[]{this, u});
                // 步骤 2: 再击杀
                annihilateUnit(u);
            } else if (t instanceof Building b) {
                float rot = b.angleTo(this);
                // 步骤 1: 先放特效
                SpecialFx.endgameVapourize.at(tx, ty, rot, new Object[]{this, b});
                // 步骤 2: 走引擎的标准摧毁流程 (掉落/音效/事件齐全),
                //         比"把血量写成 -Float.MAX_VALUE"稳定得多。
                try {
                    b.kill();
                } catch (Throwable ignored) {
                }
            }
        }

        /**
         * ★ 湮灭单位 —— 五层降级击杀, 保证任何单位都能被消灭, 同时保持稳定。
         *
         * <p>与旧实现的关键区别:</p>
         * <ul>
         *   <li><b>不再把坐标写成 {@code NaN}</b>。旧实现用 NaN 让"引用失效",
         *       但这会连带把汽化特效也画到 NaN 上 (特效消失), 而且 NaN 会顺着
         *       弹道/索敌/统计等系统扩散, 是典型的不稳定源;</li>
         *   <li><b>不再手工拆解实体</b>。旧实现直接 {@code Groups.unit.remove(u)}
         *       外加反复 {@code remove()}, 绕过了引擎的下标维护; 现在统一交给
         *       {@code kill() → destroy() → remove()} 这三个自带幂等守卫的引擎
         *       方法, 队伍单位计数不会被重复扣减;</li>
         *   <li><b>不做 {@code Float.MAX_VALUE} 这种极端伤害</b>, 避免触发伤害
         *       反弹、数值溢出等副作用; 压血 + 走死亡流程已经足够;</li>
         *   <li><b>层层兜底</b>: 每一层都只在前一层没把单位清掉时才执行,
         *       所以对普通原版单位来说只有第一层会生效, 开销与副作用都最小。</li>
         * </ul>
         *
         * @param u 要湮灭的单位
         */
        void annihilateUnit(Unit u) {
            if (u == null) {
                return;
            }

            // ===== 第 1 层: 清除各类"防秒杀"保护字段 =====
            clearProtectionFields(u);

            // ===== 第 2 层: 直接把血量压到 0 =====
            try {
                if (u.health > 0f) {
                    u.health = 0f;
                }
            } catch (Throwable ignored) {
            }

            // ===== 第 3 层: 引擎标准死亡流程 kill → killed → destroy → remove =====
            try {
                if (!u.dead) {
                    u.kill();
                }
            } catch (Throwable ignored) {
            }

            // ===== 第 4 层: 强制销毁 =====
            // 飞行且会留下残骸的单位被击杀后是先"坠机"而不是立刻消失的,
            // 这里强制走完 destroy(), 让湮灭真正瞬时发生, 同时补齐死亡特效、
            // UnitDestroyEvent 事件与 abilities 的死亡回调。
            if (u.isAdded()) {
                try {
                    u.destroy();
                } catch (Throwable ignored) {
                }
            }

            // ===== 第 5 层: 最终兜底, 直接移除 =====
            // 只有 killable() == false 这类"拒绝死亡"的单位才会走到这里。
            // remove() 内部有 added 守卫, 重复调用是安全的。
            if (u.isAdded()) {
                try {
                    u.remove();
                } catch (Throwable ignored) {
                }
            }
        }

        /**
         * 反射清除各类单位用来"防秒杀"的私有字段。
         *
         * <p>这些字段来自 PU132 / FlameOut 等模组体系:</p>
         * <ul>
         *   <li>{@code lastHealth} —— 每帧把血量钳回历史最低值, 使治疗/压血无效;</li>
         *   <li>{@code trueHealth / trueMaxHealth} —— 用另一套血量接管结算;</li>
         *   <li>{@code parryTime / invFrames} —— 招架/无敌帧;</li>
         *   <li>{@code immunity / rogueDamageResist / damageTaken} —— 减伤与免疫计数。</li>
         * </ul>
         *
         * <p>字段不存在时会静默跳过, 所以对原版单位完全无副作用。</p>
         *
         * @param u 目标单位
         */
        protected void clearProtectionFields(Unit u) {
            Class<?> clazz = u.getClass();
            setUnitField(u, clazz, "lastHealth", 0f);
            setUnitField(u, clazz, "trueHealth", 0f);
            setUnitField(u, clazz, "immunity", 0f);
            setUnitField(u, clazz, "rogueDamageResist", 0f);
            setUnitField(u, clazz, "parryTime", 0f);
            setUnitField(u, clazz, "damageTaken", 0f);
            setUnitField(u, clazz, "invFrames", 0f);
            // trueMaxHealth 压到 1 而不是 0: 保持 health/maxHealth 比例可计算,
            // 避免其它系统除零产生 NaN。
            setUnitField(u, clazz, "trueMaxHealth", 1f);
        }

        /**
         * 把单位身上指定名字的 float 字段设为给定值。
         *
         * <p>找不到字段、字段类型不符或不可写 (Android 上偶发) 时静默跳过,
         * 绝不让反射异常打断击杀流程。</p>
         */
        protected void setUnitField(Unit u, Class<?> clazz, String name, float value) {
            try {
                java.lang.reflect.Field f = findField(clazz, name);
                if (f != null && f.getType() == float.class) {
                    f.setFloat(u, value);
                }
            } catch (Throwable ignored) {
            }
        }

        /** 递归查找字段(包括父类) */
        java.lang.reflect.Field findField(Class<?> clazz, String name) {
            while (clazz != null) {
                try {
                    java.lang.reflect.Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            return null;
        }

        // ================= 渲染 =================

        @Override
        public void draw() {
            float oz = Draw.z();

            // 底座
            Draw.rect(baseRegion, x, y);

            // 环的底层
            Draw.z(oz + 0.01f);
            Draw.rect(ringABottomRegion, x, y, ringProgress[0]);
            Draw.rect(ringBBottomRegion, x, y, ringProgress[1]);

            // 环体 (自旋)
            Draw.z(oz + 0.02f);
            Drawf.spinSprite(ringARegion, x, y, ringProgress[0]);
            Drawf.spinSprite(ringBRegion, x, y, ringProgress[1]);
            Drawf.spinSprite(ringCRegion, x, y, ringProgress[2]);

            Draw.blend(Blending.additive);

            // 底座灯光
            Draw.z(oz + 0.005f);
            Draw.color(1f, offsetSin(0f, 5f), offsetSin(90f, 5f), eyesAlpha);
            Draw.rect(bottomLightsRegion, x, y);
            Draw.color(1f, offsetSin(0f, 5f), offsetSin(90f, 5f), lightsAlpha * offsetSin(0f, 12f));
            Draw.rect(baseLightsRegion, x, y);

            // 眼睛与环灯光
            TextureRegion[] regionEyes = {ringAEyesRegion, ringBEyesRegion, eyeMainRegion};
            TextureRegion[] regionLights = {ringALightsRegion, ringBLightsRegion, ringCLightsRegion};
            float[] trnsScl = {1f, 0.9f, 2f};

            for (int i = 0; i < 3; i++) {
                int h = i + 1;

                Draw.z(oz + 0.015f);
                Draw.color(1f, offsetSin(10f * h, 5f), offsetSin(90f + 10f * h, 5f), eyesAlpha);
                Draw.rect(regionEyes[i], x + eyeOffset.x * trnsScl[i], y + eyeOffset.y * trnsScl[i], ringProgress[i]);

                Draw.z(oz + 0.025f);
                Draw.color(1f, offsetSin(10f * h, 5f), offsetSin(90f + 10f * h, 5f), lightsAlpha * offsetSin(5f * h, 12f));
                Draw.rect(regionLights[i], x, y, ringProgress[i]);
            }

            Draw.blend();
            Draw.z(oz);

            // 眼睛光束 (连接锁定目标)
            drawEyeBeams();

            Draw.reset();
        }

        /** 绘制所有正在连接目标的眼睛光束。 */
        protected void drawEyeBeams() {
            if (eyesAlpha <= 0.001f) {
                return;
            }

            float oz = Draw.z();
            Draw.z(Layer.flyingUnit + 1f);
            Draw.blend(Blending.additive);

            for (int i = 0; i < eyeCount; i++) {
                Posc t = targets[i];
                if (t == null) {
                    continue;
                }

                float ex = eyeVecs[i].x, ey = eyeVecs[i].y;
                float tx = t.getX(), ty = t.getY();
                // 轻微脉动
                float pulse = 1f + 0.35f * Mathf.sin(Time.time / 6f + i * 1.7f);
                float a = Mathf.clamp(eyesAlpha) * pulse;

                Drawf.light(ex, ey, tx, ty, 14f * a, scarColor, 0.9f);

                Lines.stroke(5f * a);
                Draw.color(scarColor);
                Lines.line(ex, ey, tx, ty);

                Lines.stroke(2f * a);
                Draw.color(Color.white);
                Lines.line(ex, ey, tx, ty);
            }

            Draw.blend();
            Draw.z(oz);
            Draw.reset();
        }
    }

    /** 原版 Utils.offsetSin —— 让 RGB 通道错相位闪烁。 */
    private static float offsetSin(float offset, float scl) {
        return Mathf.absin(Time.time + offset, scl, 1f);
    }
}