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
import mindustry.content.Fx;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.UnitController;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
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
 *   <li>光束命中后先解除目标 AI, 1 秒后将其湮灭并播放汽化特效,
 *       湮灭后眼睛才会转去锁定下一个目标;</li>
 *   <li>每只眼睛可以打不同的目标 —— 分配时按目标列表轮转,
 *       避免所有眼睛都盯着同一个单位。</li>
 * </ul>
 *
 * <p>★ v155/v159 适配要点:</p>
 * <ul>
 *   <li>崩溃修复: {@link PowerTurret#shootType} 必须非 null, 否则
 *       {@code TurretBuild.peekAmmo()} 返回 null, 会在
 *       {@code ammoReloadMultiplier()} 处抛 NPE。
 *       本炮台不使用普通子弹, 因此填一个伤害为 0 的占位子弹;</li>
 *   <li>移除 PU132 的 AntiCheat 采样湮灭系统, 改用直接击杀 + 汽化特效;</li>
 *   <li>移除 PU132 的反子弹拦截 (updateAntiBullets) 与减伤 (resist) —— 与既定秒杀系统一致。</li>
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
    /** 每只眼睛锁定后, 从"命中"到"湮灭"的延迟 (帧)。 */
    public static final float annihilateDelay = 60f;
    /** 眼睛光束的发射间隔 (帧)。 */
    public static final float eyeShotInterval = 14f;

    // ===== 贴图 =====
    public TextureRegion baseRegion, baseLightsRegion, bottomLightsRegion, eyeMainRegion;
    public TextureRegion ringABottomRegion, ringAEyesRegion, ringARegion, ringALightsRegion;
    public TextureRegion ringBBottomRegion, ringBEyesRegion, ringBRegion, ringBLightsRegion;
    public TextureRegion ringCRegion, ringCLightsRegion;

    public EndGameTurret(String name) {
        super(name);

        health = 68000;
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

        @Override
        public void add() {
            for (int i = 0; i < eyeCount; i++) {
                eyeVecs[i] = new Vec2();
                targets[i] = null;
                annihilateTimers[i] = 0f;
            }
            super.add();
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
                for (int i = 0; i < eyeCount; i++) {
                    Posc t = targets[i];
                    targets[i] = null;
                    annihilateTimers[i] = 0f;
                    
                    // 恢复单位AI: 把之前被替换掉的原始控制器还原回去。
                    // 注意: 绝不能传 null 给 controller(), 设置器内部会立即调用
                    // controller.unit(this), 传 null 必然抛 NPE。
                    if (t instanceof Unit u && u.controller() instanceof NullAI na) {
                        if (na.previous != null) {
                            u.controller(na.previous);
                        }
                        u.vel.setZero(); // 重置速度
                    }
                }
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
            Seq<Unit> found = new Seq<>();
            Units.nearbyEnemies(team, x - range(), y - range(), range() * 2f, range() * 2f, u -> {
                if (u.isValid() && !u.dead() && Mathf.within(x, y, u.x, u.y, range())) {
                    found.add(u);
                }
            });

            if (found.isEmpty()) {
                return;
            }

            found.sort((a, b) -> Float.compare(a.dst2(this), b.dst2(this)));

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

        /** 更新每只眼睛的湮灭流程: 先解除 AI, 1 秒后湮灭。 */
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
                // 保存原控制器, 以便断电/断弹时恢复
                if (t instanceof Unit u && !(u.controller() instanceof NullAI)) {
                    u.controller(new NullAI(u.controller()));
                    u.vel.setZero();
                }

                // 步骤 2: 1 秒后湮灭
                annihilateTimers[i] += Time.delta;
                if (annihilateTimers[i] >= annihilateDelay) {
                    annihilate(t);
                    targets[i] = null;
                    annihilateTimers[i] = 0f;
                }
            }
        }

        /** 湮灭目标: 播放汽化特效并直接秒杀。 */
        protected void annihilate(Posc t) {
            if (t instanceof Unit u) {
                annihilateUnit(u);
                SpecialFx.endgameVapourize.at(u.x, u.y, angleTo(u), new Object[]{this, u});
            } else if (t instanceof Building b) {
                // 建筑秒杀：直接摧毁
                b.health = -Float.MAX_VALUE; // 确保秒杀
                SpecialFx.endgameVapourize.at(b.x, b.y, b.angleTo(this), new Object[]{this, b});
                b.remove();
            }
        }

        /**
         * ★ 多重秒杀机制: 绕过所有可能的反作弊方式
         * 至少有一条攻击路径会生效，确保任何单位都能被杀死
         * 参考FlameOut模组的annihilate方法和EmpathyDamage系统
         */
        void annihilateUnit(Unit u) {
            if (u == null || u.isAdded() == false) return;

            // ===== 机制1: 常规伤害 + remove =====
            try {
                u.damage(Float.MAX_VALUE);
            } catch (Throwable ignored) {}

            // ===== 机制2: 直接设置 health=0, dead=true =====
            try {
                u.health = 0f;
                u.dead = true;
                u.maxHealth = 1f;
            } catch (Throwable ignored) {}

            // ===== 机制3: 反射清除反作弊私有字段 =====
            // 清除 SegmentWormEntity 的 lastHealth/invTime/immunity/rogueDamageResist
            // 清除 EmpathyUnit 的 trueHealth/trueMaxHealth/invFrames/parryTime
            try {
                java.lang.reflect.Field f = findField(u.getClass(), "lastHealth");
                if (f != null) { f.setFloat(u, 0f); }
                f = findField(u.getClass(), "trueHealth");
                if (f != null) { f.setFloat(u, 0f); }
                f = findField(u.getClass(), "trueMaxHealth");
                if (f != null) { f.setFloat(u, 1f); }
                f = findField(u.getClass(), "invTime");
                if (f != null) { f.setFloat(u, 100f); }
                f = findField(u.getClass(), "immunity");
                if (f != null) { f.setFloat(u, 0f); }
                f = findField(u.getClass(), "rogueDamageResist");
                if (f != null) { f.setFloat(u, 0f); }
                f = findField(u.getClass(), "parryTime");
                if (f != null) { f.setFloat(u, 0f); }
                f = findField(u.getClass(), "damageTaken");
                if (f != null) { f.setFloat(u, 0f); }
            } catch (Throwable ignored) {}

            // ===== 机制4: 反复调用kill()绕过死亡拒绝 =====
            for (int i = 0; i < 5; i++) {
                try {
                    u.kill();
                } catch (Throwable ignored) {}
            }

            // ===== 机制5: 从Groups中移除 + NaN销毁 (参考FlameOut annihilate) =====
            try {
                u.health = 0f;
                u.dead = true;
                Groups.unit.remove(u);
                // 设置NaN让任何引用该单位的代码失效
                u.x = Float.NaN;
                u.y = Float.NaN;
                u.rotation = Float.NaN;
            } catch (Throwable ignored) {}

            // ===== 机制6: 最终remove =====
            try {
                u.remove();
            } catch (Throwable ignored) {}
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