package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Drawc;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.entities.units.UnitController;
import mindustry.graphics.Drawf;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.meta.Stat;
import zzw.content.Z_Sounds;
import zzw.content.units.effects.ScarFx;
import zzw.content.units.effects.SlowLightning;
import zzw.content.units.effects.SpecialFx;
import zzw.content.units.util.UnityUtils;

/**
 * EndGameTurret 移植自 PU132 (unity.world.blocks.defense.turrets.EndGameTurret)。
 *
 * <p>终局炮台 —— 三层旋转环 + 16 只眼睛追踪光束 + 全屏湮灭齐射。</p>
 *
 * <p>★ 本类是 PU132 原版的<b>逐行忠实移植</b>: draw / updateTile / updateEyes /
 * eyeShoot / killUnits / killTiles / shoot / collision / damage 的数值、判定顺序、
 * 特效参数与图层全部与原版一致, 能直接抄的一律直接抄。</p>
 *
 * <p>行为:</p>
 * <ul>
 *   <li><b>通电</b>: 眼睛亮起 (eyesAlpha 由 "电力满足度 + 储能" 驱动);</li>
 *   <li><b>通电 + 有目标 + 有弹药</b>: 环灯亮起、轮盘旋转、随机释放慢速闪电,
 *       并且 16 只眼睛按逆时针顺序轮流发射秒杀光束;</li>
 *   <li><b>装填完成</b>: 以自身为中心释放 "终局齐射" —— 范围内所有敌方单位被
 *       瞬间湮灭, 所有敌方建筑被汽化;</li>
 *   <li><b>限伤</b>: 单次伤害被 {@code resist} 折减到最多 410, 且一次掉血超过
 *       860 会被 {@link #verify()} 直接忽略 (防一击秒杀 / 防作弊)。</li>
 * </ul>
 *
 * <p>★ v132 → v158 适配要点 (与"通电就亮"反复不生效的根因):</p>
 * <ul>
 *   <li>PU132 的 {@code efficiency()} 只表示<b>电力满足度</b>; 而 v158 中
 *       {@code Building.efficiency} 是 "所有非可选消费者效率的最小值"
 *       (见 {@code BuildingComp.updateConsumption()}), 本炮台有
 *       {@code consumeItem(terminum, 2)} 这个必选消费者, 一旦没有 terminum
 *       就会把 efficiency 直接压成 0 —— 于是旧代码里
 *       {@code efficiency > 0.0001f} 永远为 false, 眼睛永远不亮。
 *       本类改用 {@code power.status} (PowerModule, 纯粹的电力满足度 0~1,
 *       与物品无关) 作为 "通电" 信号, 见 {@link #powerStatus()} /
 *       {@link #trueEfficiency()} —— 这才是原版语义的等价物;</li>
 *   <li>{@code reload / reloadTime} (旧版: 当前 / 总装填) → v158 的
 *       {@code reloadCounter / reload};</li>
 *   <li>{@code Utils.offsetSin / offsetSinB} → 本类内联实现, 保持
 *       {@code absin(Time.time + offset*radDeg, ...)} 的相位算法不变;</li>
 *   <li>{@code Utils.trueEachBlock} → 项目内 {@link UnityUtils#trueEachBlock};</li>
 *   <li>{@code Utils.nearbyEnemySorted} → 项目内 {@link UnityUtils#nearbyEnemySorted};</li>
 *   <li>{@code AntiCheat.annihilateEntity} → 本类的 {@link #annihilateUnit}
 *       (反射克制 FlameOut 共鸣/消沉等 "神秘特殊机制" 的不死单位) +
 *       {@link #annihilateEntity} 统一入口;</li>
 *   <li>{@code SlowLightningType.create(...)} → 项目内自包含的
 *       {@link SlowLightning} (由本建筑在 {@code updateTile/draw} 中驱动)。</li>
 * </ul>
 */
public class EndGameTurret extends PowerTurret {

    /** End 系列主色 (scarColor)。 */
    public static final Color scarColor = Color.valueOf("f53036");

    /** 三层环的旋转速度系数 (原版 ringProgresses)。 */
    private static final float[] ringProgresses = {0.013f, 0.035f, 0.024f};
    /** 三层环的旋转方向 (原版 ringDirections)。 */
    private static final int[] ringDirections = {1, -1, 1};

    /** 眼睛数量: 内环 8 只 + 外环 8 只。 */
    public static final int eyeCount = 16;

    /** 眼睛目标刷新计时器索引 (PU132 eyeTime)。 */
    protected int eyeTime = timers++;
    /** 反子弹扫描计时器索引 (PU132 bulletTime)。 */
    protected int bulletTime = timers++;

    // ===== 贴图 =====
    public TextureRegion baseRegion, baseLightsRegion, bottomLightsRegion, eyeMainRegion;
    public TextureRegion ringABottomRegion, ringAEyesRegion, ringARegion, ringALightsRegion;
    public TextureRegion ringBBottomRegion, ringBEyesRegion, ringBRegion, ringBLightsRegion;
    public TextureRegion ringCRegion, ringCLightsRegion;

    public EndGameTurret(String name) {
        super(name);

        // ★ 与 PU132 EndGameTurret 构造 + UnityBlocks 方块定义逐项一致
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

        // ★ 占位子弹 (PU132 原版: damage = (float)Double.MAX_VALUE)。
        //   本炮台不使用普通子弹, 伤害全部由眼睛光束 / 齐射结算,
        //   这里必须给 shootType 一个非 null 值 —— 否则 PowerTurret.peekAmmo()
        //   返回 null, 会在 ammoReloadMultiplier() 处抛 NPE。
        shootType = new BulletType(){{
            damage = (float)Double.MAX_VALUE;
            speed = 0.01f;
            lifetime = 1f;
            collides = false;
            hittable = false;
            absorbable = false;
            despawnEffect = hitEffect = shootEffect = smokeEffect = Fx.none;
        }};
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

    public class EndGameTurretBuild extends PowerTurretBuild {

        // ===== 运行状态 (与 PU132 EndGameTurretBuilding 字段一一对应) =====

        /** PU132 charge: 由超大单次伤害积攒的储能, 提供额外效率/装填速度。 */
        protected float energyCharge = 0f;
        /** 伤害折减系数 (越大减伤越多)。 */
        protected float resist = 1f;
        /** 距上次受击的时间 (帧), 超过 15 帧后 resist 自动回落。 */
        protected float resistTime = 10f;
        /** 威胁等级 (1 + 范围内敌人强度), 放大眼睛光束伤害。 */
        protected float threatLevel = 1f;
        /** 上一帧血量, 用于 {@link #verify()} 检测异常掉血。 */
        protected float lastHealth = 0f;
        /** 停火后开始熄灭环灯的延时计时。 */
        protected float eyeResetTime = 0f;
        /** 眼睛亮度 (0~1)。 */
        protected float eyesAlpha = 0f;
        /** 环灯亮度 (0~1)。 */
        protected float lightsAlpha = 0f;
        /** 三层环的当前角度。 */
        protected float[] ringProgress = {0f, 0f, 0f};
        /** 内外环的发射计时: [0]=内环 15f, [1]=外环 5f。 */
        protected float[] eyeReloads = {0f, 0f};

        /** 内外环眼睛的轮转序号 (逆时针)。 */
        protected int eyeSequenceA = 0, eyeSequenceB = 0;

        /** 眼睛位置平滑偏移。 */
        protected Vec2 eyeOffset = new Vec2();
        protected Vec2 eyeOffsetB = new Vec2();
        protected Vec2 eyeTargetOffset = new Vec2();
        /** 16 只眼睛的世界坐标。 */
        protected Vec2[] eyesVecArray = new Vec2[eyeCount];
        /** 16 只眼睛当前锁定的目标。 */
        protected Posc[] targets = new Posc[eyeCount];

        // ===== 反子弹 / 齐射用的临时状态 (PU132 静态字段, 这里改为实例以免多炮台互相污染) =====
        protected float damageFull = 0f, damageB = 0f;
        protected int shouldLaser = 0;
        /** 复用的实体收集缓存 (PU132 entitySeq)。 */
        protected final Seq<Entityc> entitySeq = new Seq<>(512);
        /** 本炮台当前存活的所有慢速闪电。 */
        protected final Seq<SlowLightning> lightnings = new Seq<>();

        {
            // 构造阶段就填满眼睛坐标数组, 保证 draw/shoot 任何时刻都不会读到 null
            for(int i = 0; i < eyeCount; i++){
                eyesVecArray[i] = new Vec2();
            }
        }

        // ================= 效率 / 合法性 =================

        /**
         * ★ "通电" 信号 —— PU132 {@code efficiency()} 的 v158 等价物。
         *
         * <p>{@code power.status} 是 PowerModule 的电力满足度 (0~1),
         * 只与电力有关, 完全不受 {@code consumeItem(terminum, 2)}
         * 是否满足的影响 —— 这正是旧实现 (用 {@code efficiency}) 永远不亮的原因。</p>
         */
        float powerStatus(){
            return power == null ? 0f : power.status;
        }

        /** PU132 trueEfficiency(): 电力满足度 + 储能, 钳制到 [0, 1]。 */
        float trueEfficiency(){
            return Mathf.clamp(powerStatus() + energyCharge, 0f, 1f);
        }

        /** PU132 baseReloadSpeed(): 电力满足度 + 储能, 钳制到 [0, 1.2]。 */
        @Override
        protected float baseReloadSpeed(){
            return Mathf.clamp(powerStatus() + energyCharge, 0f, 1.2f);
        }

        /** PU132 deltaB(): 按 baseReloadSpeed 缩放的 delta。 */
        float deltaB(){
            return delta() * baseReloadSpeed();
        }

        /**
         * PU132 consValid(): "可以工作" = 电力满足 (或储能充足) 且 有 terminum 弹药。
         *
         * <p>v158 中 {@code canConsume()} (potentialEfficiency > 0) 已经同时表达了
         * "所有必选消费者 (电力 + 物品) 都满足", 因此先用它判定; 当它失败但
         * 仍有储能时, 额外允许 (储能 > 0 且 有弹药)。</p>
         */
        @Override
        public boolean canConsume(){
            boolean valid = powerStatus() > 0.0001f;
            valid |= energyCharge > 0.001f;
            valid &= items != null && items.total() > 0;
            return valid;
        }

        @Override
        public boolean shouldActiveSound(){
            return trueEfficiency() >= 0.0001f;
        }

        // ================= 受击 / 死亡 =================

        /**
         * PU132 damage(): 储能累积 + resist 折减 + 单次伤害上限 410。
         *
         * <p>这是原版 "打不死" 的核心: 无论来袭伤害多高, 单次最多结算 410 点,
         * 且每次受击都会提升 resist 让后续伤害更小。</p>
         */
        @Override
        public void damage(float amount){
            if(verify()) return;

            // 超过 1 万的巨额伤害会转化为储能 (PU132: /150, 上限 15)
            if(amount > 10000f) energyCharge += Mathf.clamp(amount - 10000f, 0f, 2000000f) / 150f;
            if(energyCharge > 15f) energyCharge = 15f;

            float trueAmount = Mathf.clamp(amount / resist, 0f, 410f);
            super.damage(trueAmount);

            resist += 0.125f + (Mathf.clamp(amount - 520f, 0f, Float.MAX_VALUE) / 70f);
            if(Float.isNaN(resist)) resist = Float.MAX_VALUE;
            resistTime = 0f;
        }

        /** PU132 verify(): 一帧内掉血超过 860 (或血量 NaN) 视为异常, 直接忽略后续伤害。 */
        boolean verify(){
            return (health < lastHealth - 860f) || Float.isNaN(health);
        }

        /**
         * PU132 kill(): 只有血量已经很低 (上一帧 lastHealth < 10) 才允许真正销毁,
         * 否则忽略外部的强制 kill (防一击删除 / 防作弊)。
         */
        @Override
        public void kill(){
            if(lastHealth < 10f) super.kill();
        }

        /** PU132 collision(): 吸收来袭子弹并按其伤害结算, 同时惩罚射程外的攻击者。 */
        @Override
        public boolean collision(Bullet other){
            float amount = other.owner != null && !ownerWithin(other.owner)
                ? 0f : other.damage() * other.type.buildingDamageMultiplier;
            damage(amount);

            if(other.owner != null && !ownerWithin(other.owner) && other.owner instanceof Healthc){
                Healthc en = (Healthc)other.owner;
                en.damage(0.5f * en.maxHealth() * Math.max(resist / 10f, 1f));
            }
            return true;
        }

        /** 攻击者是否位于本炮台射程之内 (用于 "射程外偷袭" 判定)。 */
        protected boolean ownerWithin(Entityc owner){
            Posc p = (Posc)owner;
            return Mathf.within(x, y, p.getX(), p.getY(), range);
        }

        // ================= 生命周期 =================

        @Override
        public void add() {
            for(int i = 0; i < eyeCount; i++){
                targets[i] = null;
            }
            super.add();
        }

        @Override
        public void onRemoved() {
            lightnings.clear();
            entitySeq.clear();
            super.onRemoved();
        }

        // ================= 主更新 =================

        @Override
        public void updateTile() {
            enabled = true;
            lastHealth = health;
            energyCharge = Math.max(0f, energyCharge - (Time.delta / 20f));

            // resist 回落: 距上次受击超过 15 帧才开始缓慢恢复
            if(resistTime >= 15f){
                resist = Math.max(1f, resist - Time.delta);
            }else{
                resistTime += Time.delta;
            }

            updateEyes();

            float trueEff = trueEfficiency();
            if(trueEff > 0.0001f){
                float value = eyesAlpha > trueEff ? 1f : trueEff;
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, trueEff, 0.06f * value);
            }else{
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, 0f, 0.06f);
            }

            // 只有 "通电 + 有弹药" 时才跑基类逻辑 (索敌 / 装填 / 开火)
            if(canConsume()){
                updateAntiBullets();
                super.updateTile();
            }

            // 眼睛朝目标 (或玩家鼠标) 偏移
            if(isControlled()){
                eyeTargetOffset.trns(angleTo(unit.aimX(), unit.aimY()), dst(unit.aimX(), unit.aimY()) / (range / 3f));
            }else if(target != null && trueEff > 0.0001f){
                eyeTargetOffset.trns(angleTo(targetPos.x, targetPos.y), dst(targetPos.x, targetPos.y) / (range / 3f));
            }
            eyeTargetOffset.limit(2f);

            if(((target != null && !isControlled()) || (isControlled() && unit.isShooting())) && trueEff > 0.0001f){
                // 攻击态: 环灯亮 + 轮盘转 + 随机慢速闪电
                eyeResetTime = 0f;
                float value = lightsAlpha > trueEff ? 1f : trueEff;
                lightsAlpha = Mathf.lerpDelta(lightsAlpha, trueEff, 0.07f * value);

                for(int i = 0; i < 3; i++){
                    ringProgress[i] = Mathf.lerpDelta(ringProgress[i], 360f * (float)ringDirections[i], ringProgresses[i] * trueEff);
                }

                float chance = (((reloadCounter / reload) * 0.90f) + (1f - 0.90f)) * trueEff;
                float randomAngle = Mathf.random(360f);
                Tmp.v1.trns(randomAngle, 18.5f).add(x, y);

                if(Mathf.chanceDelta(0.75f * chance)){
                    createLightning(Tmp.v1.x, Tmp.v1.y, randomAngle, 520f * trueEff);
                }
            }else{
                // 停火 60 帧后才开始熄灭环灯 / 让轮盘转回
                if(eyeResetTime >= 60f){
                    lightsAlpha = Mathf.lerpDelta(lightsAlpha, 0f, 0.07f);
                    for(int i = 0; i < 3; i++){
                        ringProgress[i] = Mathf.lerpDelta(ringProgress[i], 0f, ringProgresses[i] * trueEff);
                    }
                }else{
                    eyeResetTime += Time.delta;
                }
            }

            updateLightnings();
        }

        /**
         * 生成一条慢速闪电。
         *
         * <p>★ 与 PU132 的差异 (按需求调整): PU132 原版 {@code range = 810f},
         * 闪电会一路延伸出去打远处单位; 本实现把 {@code range} 限制为 100,
         * 让闪电只在炮台本体附近闪烁、始终连在炮台上, 不再飞出去。</p>
         */
        protected void createLightning(float sx, float sy, float angle, float dmg){
            SlowLightning l = new SlowLightning();
            l.colorFrom = Color.red;
            l.colorTo = Color.black;
            l.damage = dmg;
            // 只在炮台附近: 延伸距离与单段长度都压到 180
            l.range = 180f;
            l.splitChance = 0.045f;
            l.nodeTime = 5f;
            l.nodeLength = 180f;
            l.lineWidth = 2f;
            l.lifetime = 140f;
            l.create(team, sx, sy, angle, targetPos);
            lightnings.add(l);
        }

        /** 推进并回收本炮台的慢速闪电。 */
        protected void updateLightnings(){
            for(int i = lightnings.size - 1; i >= 0; i--){
                SlowLightning l = lightnings.get(i);
                l.update();
                if(l.removed){
                    lightnings.remove(i);
                }
            }
        }

        // ================= 眼睛 (方位 / 索敌 / 发射) =================

        /** PU132 updateEyes(): 眼睛偏移 + 目标刷新 + 内外环轮流发射。 */
        void updateEyes(){
            updateEyesOffset();
            eyeOffsetB.lerpDelta(eyeTargetOffset, 0.12f);

            eyeOffset.set(eyeOffsetB);
            eyeOffset.add(Mathf.range(reloadCounter / reload) / 2f, Mathf.range(reloadCounter / reload) / 2f);
            eyeOffset.limit(2f);

            if(((target != null && !isControlled()) || (isControlled() && unit.isShooting())) && canConsume() && trueEfficiency() >= 0.0001f){
                eyeReloads[0] += deltaB();
                eyeReloads[1] += deltaB();
            }

            if(canConsume() && trueEfficiency() > 0.0001f){
                updateEyesTargeting();
            }

            if(eyeReloads[0] >= 15f){
                eyeReloads[0] = 0f;
                if(!isControlled()){
                    if(targets[eyeSequenceA] != null) eyeShoot(eyeSequenceA);
                }else{
                    if(unit.isShooting()) playerShoot(eyeSequenceA);
                }
                eyeSequenceA = (eyeSequenceA + 1) % 8;
            }
            if(eyeReloads[1] >= 5f){
                eyeReloads[1] = 0f;
                if(!isControlled()){
                    if(targets[eyeSequenceB] != null) eyeShoot(eyeSequenceB + 8);
                }else{
                    if(unit.isShooting()) playerShoot(eyeSequenceB + 8);
                }
                eyeSequenceB = (eyeSequenceB + 1) % 8;
            }
        }

        /** PU132 updateEyesTargeting(): 失效剔除 + 威胁统计 + 每 15 帧轮转分配目标。 */
        void updateEyesTargeting(){
            for(int i = 0; i < eyeCount; i++){
                if(Units.invalidateTarget(targets[i], team, x, y)){
                    targets[i] = null;
                }
            }

            updateThreats();

            // 注意 PU132 的 eyeTime 计时与 "存在基类目标" 两个前置条件
            if(timer.get(eyeTime, 15f) && target != null && !isControlled()){
                Seq<Healthc> nTargets = UnityUtils.nearbyEnemySorted(team, x, y, range, 8f);
                if(!nTargets.isEmpty()){
                    for(int i = 0; i < targets.length; i++){
                        targets[i] = nTargets.get(i % nTargets.size);
                    }
                }
            }
        }

        /** PU132 updateThreats(): 统计范围内敌人强度, 并冻结高速单位。 */
        void updateThreats(){
            threatLevel = 1f;
            Units.nearbyEnemies(team, x - range, y - range, range * 2f, range * 2f, e -> {
                if(Mathf.within(x, y, e.x, e.y, range) && e.isAdded()){
                    threatLevel += Math.max(((e.maxHealth() + e.type.dpsEstimate) - 450f) / 1300f, 0f);
                    if(e.speed() >= 18f){
                        e.vel.setLength(0f);
                    }
                }
            });
        }

        /** PU132 updateEyesOffset(): 计算 16 只眼睛的世界坐标 (内环 36.75, 外环 25.75)。 */
        void updateEyesOffset(){
            for(int i = 0; i < eyeCount; i++){
                float angleC = ((360f / 8f) * (i % 8));
                if(i >= 8){
                    Tmp.v1.trns(angleC + 22.5f + ringProgress[1], 25.75f);
                }else{
                    Tmp.v1.trns(angleC + ringProgress[0], 36.75f);
                }
                eyesVecArray[i].set(Tmp.v1.x, Tmp.v1.y).add(x, y);
            }
        }

        // ================= 攻击 =================

        /**
         * PU132 eyeShoot(): 指定眼睛发射一束秒杀激光。
         *
         * <p>原版逻辑是先对目标造成 {@code 350 * threatLevel} 伤害, 若致死则
         * 汽化 + 湮灭, 然后<b>无论如何</b>都画一道 {@link ScarFx#endgameLaser}。
         * 本项目额外强化了秒杀: 目标即使没被这一击打死也会被直接湮灭
         * (需求: "秒杀这个游戏里几乎所有的单位")。</p>
         */
        void eyeShoot(int index){
            Posc t = targets[index];
            if(t == null) return;

            float dmg = 350f * threatLevel;

            if(t instanceof Healthc) ((Healthc)t).damage(dmg);

            // ★ 强化秒杀: 只要还活着也一并湮灭 (原版仅在致死时湮灭)
            if(!Vars.net.client()){
                if(t instanceof Unit u && u.isValid() && !u.dead()){
                    SpecialFx.endgameVapourize.at(u.x, u.y, angleTo(u), new Object[]{this, u});
                    annihilateUnit(u);
                }else if(t instanceof Building b && b.isValid()){
                    ScarFx.vapourizeTile.at(b.x, b.y, b.block.size);
                    annihilateEntity(b);
                }
            }

            Object[] data = {eyesVecArray[index], t, 0.625f};
            ScarFx.endgameLaser.at(eyesVecArray[index].x, eyesVecArray[index].y, 0f, data);
            Z_Sounds.endgameSmallShoot.at(x, y);

            targets[index] = null;
        }

        /** PU132 playerShoot(): 玩家手动控制时, 朝鼠标位置 15 范围内秒杀。 */
        void playerShoot(int index){
            final float rnge = 15f;
            float ux = unit.aimX();
            float uy = unit.aimY();

            if(!Mathf.within(x, y, ux, uy, range * 1.5f)) return;

            UnityUtils.trueEachBlock(ux, uy, rnge, b -> b.team != team && !b.dead, building -> {
                damaged(building, 490f);
                Object[] data = {new Vec2(ux, uy), building, 0.525f};
                ScarFx.endgameLaser.at(x, y, 0f, data);
            });

            Units.nearbyEnemies(team, ux - rnge, uy - rnge, rnge * 2f, rnge * 2f, e -> {
                if(Mathf.within(ux, uy, e.x, e.y, rnge + e.hitSize) && !e.dead){
                    e.damage(490f * threatLevel);
                    if(e.dead){
                        SpecialFx.endgameVapourize.at(e.x, e.y, angleTo(e), new Object[]{this, e});
                        annihilateUnit(e);
                    }
                    ScarFx.endgameLaser.at(x, y, 0f, new Object[]{new Vec2(ux, uy), e, 0.525f});
                }
            });

            Tmp.v1.set(eyesVecArray[index]).add(ux, uy).scl(0.5f);

            Object[] dataB = {eyesVecArray[index], new Vec2(ux, uy), 0.625f};
            ScarFx.endgameLaser.at(Tmp.v1.x, Tmp.v1.y, 0f, dataB);
            Z_Sounds.endgameSmallShoot.at(x, y);
        }

        /** 对建筑造成伤害 (统一入口, 便于日后替换为更强的破坏方式)。 */
        protected void damaged(Building b, float amount){
            b.damage(amount);
        }

        /**
         * PU132 updateAntiBullets(): 定时扫描范围内敌方子弹, 威胁过大者直接被湮灭。
         *
         * <p>判定条件 (任一满足): 单发威力 > 1600 / 溅射半径 > 120 /
         * 全场累计威力 > 13000 / 攻击者本身在射程之外。</p>
         */
        void updateAntiBullets(){
            entitySeq.clear();

            float trueEff = trueEfficiency();
            if(trueEff > 0.0001f && timer.get(bulletTime, 4f / Math.max(trueEff, 0.001f))){
                damageFull = 0f;

                Groups.bullet.intersect(x - range, y - range, range * 2f, range * 2f, b -> {
                    if(Mathf.within(x, y, b.x, b.y, range) && b.team != team){
                        damageFull += bulletDamage(b.type);
                    }
                });

                Groups.bullet.intersect(x - range, y - range, range * 2f, range * 2f, b -> {
                    if(Mathf.within(x, y, b.x, b.y, range) && b.team != team){
                        damageB = bulletDamage(b.type);
                        boolean ownerOut = b.owner != null && !ownerWithin(b.owner);

                        if(damageB > 1600f || b.type.splashDamageRadius > 120f || damageFull + damageB > 13000f || ownerOut){
                            entitySeq.add(b);
                            Object[] data = {new Vec2(x + (eyeOffset.x * 2f), y + (eyeOffset.y * 2f)), new Vec2(b.x, b.y), 0.625f};
                            ScarFx.endgameLaser.at(x, y, 0f, data);
                        }
                    }
                });

                if(!entitySeq.isEmpty()) Z_Sounds.endgameSmallShoot.at(x, y);
                for(int i = 0; i < entitySeq.size; i++){
                    entitySeq.get(i).remove();
                }
                entitySeq.clear();
            }
        }

        /** 计算一发子弹 (含所有破片) 的总威力 (PU132 Utils.getBulletDamage 语义)。 */
        protected float bulletDamage(BulletType type){
            float sum = zzw.util.UnityUtils.getBulletDamage(type);
            BulletType current = type;
            int totalFrags = 1;

            for(int i = 0; i < 16; i++){
                if(current.fragBullet == null) break;

                BulletType frag = current.fragBullet;
                totalFrags *= current.fragBullets;
                sum += zzw.util.UnityUtils.getBulletDamage(frag) * totalFrags;

                current = frag;
            }
            return sum;
        }

        /**
         * PU132 killUnits(): 齐射时湮灭范围内所有敌方单位。
         *
         * <p>★ 顺序调整: 先播放汽化特效, 再执行湮灭 —— 特效需要目标的实时
         * 坐标/朝向, 而湮灭会把单位移出世界 (旧实现先杀后放, 导致特效画在
         * 无效坐标上而不可见)。</p>
         */
        void killUnits(){
            entitySeq.clear();

            Units.nearbyEnemies(team, x - range, y - range, range * 2f, range * 2f, e -> {
                if(Mathf.within(x, y, e.x, e.y, range) && !e.dead){
                    Object[] data = {new Vec2(x + eyeOffset.x, y + eyeOffset.y), e, 1f};
                    ScarFx.endgameLaser.at(x, y, 0f, data);
                    entitySeq.add(e);
                }
            });

            for(int i = 0; i < entitySeq.size; i++){
                Entityc e = entitySeq.get(i);
                if(!(e instanceof Unit)) continue;
                Unit u = (Unit)e;
                SpecialFx.endgameVapourize.at(u.x, u.y, angleTo(u), new Object[]{this, u});
                annihilateUnit(u);
            }
            entitySeq.clear();
        }

        /**
         * PU132 killTiles(): 齐射时摧毁范围内所有敌方建筑。
         *
         * <p>大的方块 (size>=3) 播放汽化; 每 5 个 (或 size>=5) 画一道终局激光;
         * 最后以 "最远建筑距离 ×2" 作为半径放一次批量建筑汽化。</p>
         */
        void killTiles(){
            entitySeq.clear();
            damageB = 0f;
            shouldLaser = 0;

            UnityUtils.trueEachBlock(x, y, range + 5f, b -> b.team != team, building -> {
                if(!building.dead && building != this){
                    if(building.block.size >= 3){
                        ScarFx.vapourizeTile.at(building.x, building.y, building.block.size, building);
                    }
                    if((shouldLaser % 5) == 0 || building.block.size >= 5){
                        Object[] data = {new Vec2(x + (eyeOffset.x * 2f), y + (eyeOffset.y * 2f)), building, 1f};
                        ScarFx.endgameLaser.at(x, y, 0f, data);
                    }
                    entitySeq.add(building);
                    shouldLaser++;
                }
            });

            for(int i = 0; i < entitySeq.size; i++){
                Entityc e = entitySeq.get(i);
                Posc ep = (Posc)e;
                damageB = Math.max(Mathf.dst2(ep.getX(), ep.getY(), x, y), damageB);
                annihilateEntity(e);
            }

            damageB = Mathf.sqrt(damageB) * 2f;

            Building[] arr = new Building[entitySeq.size];
            for(int i = 0; i < entitySeq.size; i++){
                arr[i] = (Building)entitySeq.get(i);
            }
            SpecialFx.endgameVapourize.at(x, y, damageB, new Object[]{this, arr, hitSize() / 4f});
            entitySeq.clear();
        }

        /** PU132 shoot(): 装填完成 → 全屏湮灭齐射。 */
        @Override
        protected void shoot(BulletType type){
            consume();
            killTiles();
            killUnits();

            ScarFx.endGameShoot.at(x, y);
            Z_Sounds.endgameShoot.at(x, y, 1f, 1.5f);
        }

        // ================= 湮灭 (秒杀机制核心) =================

        /** 统一湮灭入口: 单位走 {@link #annihilateUnit}, 建筑走标准摧毁。 */
        void annihilateEntity(Entityc e){
            if(e instanceof Unit){
                annihilateUnit((Unit)e);
            }else if(e instanceof Building){
                try{
                    ((Building)e).kill();
                }catch(Throwable ignored){
                }
            }
        }

        /**
         * ★ 湮灭单位 —— 分层击杀 + 跨模组 "不死单位" 克制, 保证任何单位都能被真正消灭。
         *
         * <p>与普通扣血的区别:</p>
         * <ul>
         *   <li><b>不写 NaN 坐标</b>。旧实现用 NaN 让引用失效, 但这会连带把汽化
         *       特效画到 NaN 上 (特效消失), 且 NaN 会沿弹道/索敌/统计系统扩散;</li>
         *   <li><b>不手工拆解实体</b>。统一交给 {@code kill() → destroy() → remove()}
         *       这三个自带幂等守卫的引擎方法, 队伍单位计数不会被重复扣减;</li>
         *   <li><b>专门克制 "外部不死单位"</b>: 部分模组 (如 FlameOut 的共鸣
         *       Empathy / 消沉 Despondency) 用 "独立血量池 + 每帧回血 + 拒绝
         *       {@code remove()} + 注册表复活" 实现打不死, 这里逐层拆解;</li>
         *   <li><b>层层兜底</b>: 每一层都只在前一层没把单位清掉时才执行,
         *       对原版单位只有第一层会生效, 开销与副作用最小。</li>
         * </ul>
         *
         * @param u 要湮灭的单位
         */
        void annihilateUnit(Unit u){
            if(u == null) return;

            // 外部 "复活注册表" (FlameOut EmpathyDamage) 的启用开关,
            // 必须在整段击杀流程里保持关闭, 否则 remove() 时会 duplicate() 重生。
            Class<?> reviveRegistry = findModClass(u, "flame.unit.empathy.EmpathyDamage");
            Boolean reviveWasOn = disableExternalRevive(reviveRegistry);

            try{
                clearProtectionFields(u);

                if(reviveRegistry != null){
                    purgeReviveRegistry(reviveRegistry, u);
                }

                breakExternalTrueHealth(u);

                try{
                    if(u.health > 0f) u.health = 0f;
                }catch(Throwable ignored){
                }

                try{
                    if(!u.dead) u.kill();
                }catch(Throwable ignored){
                }

                if(u.isAdded()){
                    try{
                        u.destroy();
                    }catch(Throwable ignored){
                    }
                }

                if(u.isAdded()){
                    try{
                        u.remove();
                    }catch(Throwable ignored){
                    }
                }

                // 最终兜底: 只有重写了 remove()/destroy() 并拒绝死亡的单位才会走到这里
                if(u.isAdded()){
                    hardRemove(u);
                }
            }catch(Throwable ignored){
            }finally{
                restoreExternalRevive(reviveRegistry, reviveWasOn);
            }
        }

        /** 关掉 FlameOut 的 "共鸣复活" 总开关 (EmpathyDamage.activeAdd)。 */
        protected Boolean disableExternalRevive(Class<?> registry){
            if(registry == null) return null;
            try{
                java.lang.reflect.Field f = findField(registry, "activeAdd");
                if(f == null) return null;
                boolean old = f.getBoolean(null);
                f.setBoolean(null, false);
                return old;
            }catch(Throwable ignored){
                return null;
            }
        }

        /** 还原 {@link #disableExternalRevive} 关掉的开关。 */
        protected void restoreExternalRevive(Class<?> registry, Boolean old){
            if(registry == null || old == null) return;
            try{
                java.lang.reflect.Field f = findField(registry, "activeAdd");
                if(f != null) f.setBoolean(null, old);
            }catch(Throwable ignored){
            }
        }

        /** 反射清除各类单位用来 "防秒杀" 的私有字段。 */
        protected void clearProtectionFields(Unit u){
            Class<?> clazz = u.getClass();
            setUnitField(u, clazz, "lastHealth", 0f);
            setUnitField(u, clazz, "trueHealth", 0f);
            setUnitField(u, clazz, "immunity", 0f);
            setUnitField(u, clazz, "rogueDamageResist", 0f);
            setUnitField(u, clazz, "parryTime", 0f);
            setUnitField(u, clazz, "damageTaken", 0f);
            setUnitField(u, clazz, "invFrames", 0f);
            // trueMaxHealth 压到 1 而不是 0: 保持 health/maxHealth 比例可计算, 避免除零 NaN
            setUnitField(u, clazz, "trueMaxHealth", 1f);
        }

        /** 把单位身上指定名字的 float 字段设为给定值 (找不到/不可写时静默跳过)。 */
        protected void setUnitField(Unit u, Class<?> clazz, String name, float value){
            try{
                java.lang.reflect.Field f = findField(clazz, name);
                if(f != null && f.getType() == float.class) f.setFloat(u, value);
            }catch(Throwable ignored){
            }
        }

        /** 把单位身上指定名字的 boolean 字段设为给定值 (找不到/不可写时静默跳过)。 */
        protected void setUnitBoolField(Unit u, Class<?> clazz, String name, boolean value){
            try{
                java.lang.reflect.Field f = findField(clazz, name);
                if(f != null && f.getType() == boolean.class) f.setBoolean(u, value);
            }catch(Throwable ignored){
            }
        }

        /** 读取单位身上指定名字的字段值 (找不到时返回 null)。 */
        protected Object getUnitField(Unit u, Class<?> clazz, String name){
            try{
                java.lang.reflect.Field f = findField(clazz, name);
                return f == null ? null : f.get(u);
            }catch(Throwable ignored){
                return null;
            }
        }

        /** 通过目标单位自己的类加载器查找其它模组的类 (找不到返回 null)。 */
        protected Class<?> findModClass(Unit u, String name){
            try{
                return Class.forName(name, false, u.getClass().getClassLoader());
            }catch(Throwable ignored){
            }
            try{
                return Class.forName(name);
            }catch(Throwable ignored){
            }
            return null;
        }

        /**
         * 打穿模组的 "独立血量池" (FlameOut 共鸣/消沉等)。
         *
         * <p>数组字段只在确认目标确实是这类单位 (存在 {@code trueController}
         * 特征字段) 时才处理, 避免误伤其它模组中同名的无关字段。</p>
         */
        protected void breakExternalTrueHealth(Unit u){
            Class<?> clazz = u.getClass();
            setUnitField(u, clazz, "trueHealth", 0f);

            if(findField(clazz, "trueController") == null) return;

            Object dObj = getUnitField(u, clazz, "d");
            if(dObj instanceof float[]){
                float[] d = (float[])dObj;
                if(d.length > 0) d[0] = 0f;
                if(d.length > 1) d[1] = 1f;
            }

            Object d2Obj = getUnitField(u, clazz, "d2");
            if(d2Obj instanceof boolean[]){
                boolean[] d2 = (boolean[])d2Obj;
                if(d2.length > 0) d2[0] = true;
            }

            setUnitBoolField(u, clazz, "decoy", true);
        }

        /** 把目标单位从 FlameOut 的复活注册表里摘除。 */
        protected void purgeReviveRegistry(Class<?> registry, Unit u){
            purgeSeqField(registry, "units", u);
            purgeSeqField(registry, "excludeSeq", u);
            purgeSeqField(registry, "queueExcludeRemoval", u);
            purgeSeqField(registry, "excludeReAdd", u);

            removeIntKeyField(registry, "empathyMap", u.id);
            removeIntKeyField(registry, "exclude", u.id);
            removeIntKeyField(registry, "excludeTime", u.id);
        }

        /** 从静态 {@link Seq} 字段中移除所有指向目标单位的元素。 */
        protected void purgeSeqField(Class<?> registry, String name, Unit u){
            try{
                java.lang.reflect.Field f = findField(registry, name);
                if(f == null) return;
                Object value = f.get(null);
                if(!(value instanceof Seq)) return;
                Seq<Object> raw = (Seq<Object>)value;
                for(int i = raw.size - 1; i >= 0; i--){
                    Object e = raw.get(i);
                    if(e == u || holderUnit(e) == u){
                        raw.remove(i);
                    }
                }
            }catch(Throwable ignored){
            }
        }

        /** 从静态 int 键容器 (IntMap / IntSet / IntIntMap) 中移除目标单位的 id。 */
        protected void removeIntKeyField(Class<?> registry, String name, int id){
            try{
                java.lang.reflect.Field f = findField(registry, name);
                if(f == null) return;
                Object value = f.get(null);
                if(value == null) return;
                value.getClass().getMethod("remove", int.class).invoke(value, id);
            }catch(Throwable ignored){
            }
        }

        /** 取出注册表元素所引用的单位 (元素本身就是单位, 或元素带有 unit 字段)。 */
        protected Object holderUnit(Object holder){
            if(holder == null) return null;
            if(holder instanceof Unit) return holder;
            try{
                java.lang.reflect.Field f = findField(holder.getClass(), "unit");
                return f == null ? null : f.get(holder);
            }catch(Throwable ignored){
                return null;
            }
        }

        /** 最终兜底: 直接从实体组里摘除单位。 */
        protected void hardRemove(Unit u){
            try{
                setUnitBoolField(u, u.getClass(), "added", false);
                try{
                    u.team.data().updateCount(u.type, -1);
                }catch(Throwable ignored){
                }
                try{
                    UnitController c = u.controller();
                    if(c != null) c.removed(u);
                }catch(Throwable ignored){
                }
            }catch(Throwable ignored){
            }

            try{
                Groups.unit.remove(u);
            }catch(Throwable ignored){
            }
            try{
                Groups.all.remove(u);
            }catch(Throwable ignored){
            }
            try{
                Groups.draw.remove((Drawc)u);
            }catch(Throwable ignored){
            }
        }

        /** 递归查找字段 (包括父类)。 */
        java.lang.reflect.Field findField(Class<?> clazz, String name){
            while(clazz != null){
                try{
                    java.lang.reflect.Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                }catch(NoSuchFieldException e){
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

            // 底座灯光 (颜色 G/B 通道错相位闪烁)
            Draw.z(oz + 0.005f);
            Draw.color(1f, offsetSin(0f, 5f), offsetSin(90f, 5f), eyesAlpha);
            Draw.rect(bottomLightsRegion, x, y);
            Draw.color(1f, offsetSin(0f, 5f), offsetSin(90f, 5f), lightsAlpha * offsetSin(0f, 12f));
            Draw.rect(baseLightsRegion, x, y);

            // 眼睛与环灯光
            TextureRegion[] regions = {ringAEyesRegion, ringBEyesRegion, eyeMainRegion};
            TextureRegion[] regionsB = {ringALightsRegion, ringBLightsRegion, ringCLightsRegion};
            float[] trnsScl = {1f, 0.9f, 2f};

            for(int i = 0; i < 3; i++){
                int h = i + 1;

                Draw.z(oz + 0.015f);
                Draw.color(1f, offsetSin(10f * h, 5f), offsetSin(90f + (10f * h), 5f), eyesAlpha);
                Draw.rect(regions[i], x + (eyeOffset.x * trnsScl[i]), y + (eyeOffset.y * trnsScl[i]), ringProgress[i]);

                Draw.z(oz + 0.025f);
                Draw.color(1f, offsetSin(10f * h, 5f), offsetSin(90f + (10f * h), 5f), lightsAlpha * offsetSin(5f * h, 12f));
                Draw.rect(regionsB[i], x, y, ringProgress[i]);
            }

            Draw.blend();
            Draw.z(oz);

            // 慢速闪电 (节点式, 覆盖在炮台上方)
            Draw.blend(Blending.additive);
            for(int i = 0; i < lightnings.size; i++){
                lightnings.get(i).draw();
            }
            Draw.blend();

            Draw.reset();
        }
    }

    /**
     * PU132 Utils.offsetSin: 让 RGB 通道错相位闪烁 (带 0.5 基线)。
     *
     * <p>与 {@code offsetSinB} 一样先乘 {@link Mathf#radDeg} 再叠加 Time.time。</p>
     */
    private static float offsetSin(float offset, float scl){
        return Mathf.absin(Time.time + (offset * Mathf.radDeg), scl, 0.5f) + 0.5f;
    }
}