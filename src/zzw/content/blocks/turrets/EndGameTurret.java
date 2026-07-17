package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Tmp;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.bullet.BulletType;
import mindustry.Vars;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeItems;

/**
 * EndGameTurret 移植自 PU_V8 unity/world/blocks/defense/turrets/EndGameTurret.java
 *
 * v155.4 适配要点:
 * - consValid() -> canConsume()
 * - efficiency() 方法 -> efficiency 字段
 * - reload / reloadTime -> reloadCounter / reload (Block.reload 是总时长, Build.reloadCounter 是当前计数器)
 * - timers++ -> 整数常量 (v155.4 无 timers++ 字段)
 * - AntiCheat.annihilateEntity -> entity.damage(Float.MAX_VALUE) + remove()
 * - Unity.antiCheat.addBuilding/removeBuilding -> 忽略
 * - UnityFx.endgameLaser/vapourizeTile/SpecialFx.endgameVapourize/ShootFx.endGameShoot -> 自定义 Effect
 * - UnitySounds.endgameActive/Shoot/SmallShoot -> Z_Sounds
 * - Utils.offsetSin -> Mathf.absin(Time.time + offset, period, 0.5f) + 0.5f
 * - Utils.trueEachBlock -> Vars.indexer.eachBlock
 * - Utils.nearbyEnemySorted -> Units.nearbyEnemies 收集后排序
 * - Utils.getBulletDamage -> 递归累加 fragBullet 伤害 + splashDamage
 * - SlowLightningType -> Lightning.create (原版红色 Color.red)
 * - added (private) -> isAdded()
 */
public class EndGameTurret extends PowerTurret {
    private static int shouldLaser = 0;
    protected static float damageFull;
    protected static float damageB;
    protected static int totalFrags;
    private static final float[] ringProgresses = {0.013f, 0.035f, 0.024f};
    private static final int[] ringDirections = {1, -1, 1};
    private static final Seq<Entityc> entitySeq = new Seq<>(512);

    // v155.4 无 timers++ 字段, 用整数常量代替 PU_V8 的 timer id
    protected static final int eyeTime = 0;
    protected static final int bulletTime = 1;

    // 慢闪电颜色 (原版 SlowLightningType red/black, 简化版用红色 Color.red)
    private static final Color slowLightningColor = Color.red;

    public TextureRegion
        baseRegion, baseLightsRegion, bottomLightsRegion, eyeMainRegion,
        ringABottomRegion, ringAEyesRegion, ringARegion, ringALightsRegion,
        ringBBottomRegion, ringBEyesRegion, ringBRegion, ringBLightsRegion,
        ringCRegion, ringCLightsRegion;

    // 简化等效特效: 替代 PU_V8 UnityFx.endgameLaser
    public static final Effect endgameLaserEffect = new Effect(22f, 400f, e -> {
        if (!(e.data instanceof Object[])) return;
        Object[] data = (Object[]) e.data;
        if (data.length < 3) return;
        Vec2 from = (Vec2) data[0];
        Object target = data[1];
        float width = (Float) data[2];
        if (target instanceof Posc) {
            Posc p = (Posc) target;
            // 红色渐变到白色激光束
            Draw.color(Color.valueOf("f53036"), Color.white, e.fout());
            Lines.stroke(width * 2f * e.fout());
            Lines.line(from.x, from.y, p.getX(), p.getY(), false);
            Fill.circle(from.x, from.y, width * 2f * e.fout());
            Fill.circle(p.getX(), p.getY(), width * 3f * e.fout());
        }
    });

    // 简化等效特效: 替代 PU_V8 SpecialFx.endgameVapourize
    public static final Effect endgameVapourizeEffect = new Effect(60f, 400f, e -> {
        Draw.color(Color.valueOf("f53036"), Color.valueOf("ff786e"), e.fin());
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, e.rotation + e.fin() * 60f);
        Fill.circle(e.x, e.y, 6f * e.fout());
        for (int i = 0; i < 6; i++) {
            float a = (360f / 6f) * i + e.rotation;
            float d = e.fin() * 50f;
            Vec2 v = Tmp.v1.trns(a, d).add(e.x, e.y);
            Fill.circle(v.x, v.y, 4f * e.fout());
        }
    });

    // 简化等效特效: 替代 PU_V8 ShootFx.endGameShoot
    public static final Effect endGameShootEffect = new Effect(80f, 800f, e -> {
        Draw.color(Color.valueOf("f53036"), Color.white, e.fin());
        Lines.stroke(8f * e.fout());
        Lines.circle(e.x, e.y, e.fin() * 400f);
        Fill.circle(e.x, e.y, 40f * e.fout());
        Draw.color(Color.white);
        for (int i = 0; i < 12; i++) {
            float a = (360f / 12f) * i + e.rotation;
            float d = e.fin() * 200f;
            Vec2 v = Tmp.v1.trns(a, d).add(e.x, e.y);
            Fill.circle(v.x, v.y, 8f * e.fout());
        }
    });

    public EndGameTurret(String name) {
        super(name);
        health = 68000;
        // 原版 powerUse = 320f (v155.4 用 consumePower 替代)
        consumePower(320f);
        reload = 430f;
        range = 820f;
        size = 14;
        shootCone = 360f;
        absorbLasers = true;
        shake = 2.2f;
        outlineIcon = false;
        noUpdateDisabled = false;
        // loopSound / shootSound 由外部注册时设置
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

    public class EndGameTurretBuild extends PowerTurretBuild {
        protected float charge = 0f;
        protected float resist = 1f;
        protected float resistTime = 10f;
        protected float threatLevel = 1f;
        protected float lastHealth = 0f;
        protected float eyeResetTime = 0f;
        protected float eyesAlpha = 0f;
        protected float lightsAlpha = 0f;
        protected float[] ringProgress = {0, 0, 0};
        protected float[] eyeReloads = {0, 0};

        protected int eyeSequenceA = 0;
        protected int eyeSequenceB = 0;

        protected Vec2 eyeOffset = new Vec2();
        protected Vec2 eyeOffsetB = new Vec2();
        protected Vec2 eyeTargetOffset = new Vec2();
        protected Vec2[] eyesVecArray = new Vec2[16];

        protected Posc[] targets = new Posc[16];

        @Override
        public void damage(float damage) {
            if (verify()) return;
            // 防作弊: 大伤害累积 charge (受击 > 10000 时累积, 最大 15)
            if (damage > 10000) charge += Mathf.clamp(damage - 10000f, 0f, 2000000f) / 150f;
            if (charge > 15) charge = 15f;

            // 抗性: clamp damage/resist 到 410, 受击增加 resist
            float trueAmount = Mathf.clamp(damage / resist, 0f, 410f);
            super.damage(trueAmount);

            resist += 0.125f + (Mathf.clamp(damage - 520f, 0f, Float.MAX_VALUE) / 70f);
            if (Float.isNaN(resist)) resist = Float.MAX_VALUE;
            resistTime = 0f;
        }

        @Override
        protected float baseReloadSpeed() {
            return Mathf.clamp(efficiency + charge, 0f, 1.2f);
        }

        float trueEfficiency() {
            return Mathf.clamp(efficiency + charge);
        }

        float deltaB() {
            return delta() * baseReloadSpeed();
        }

        /**
         * 原版 consValid() 逻辑: (电力 OR 蓄能) AND 物品
         * 电力缺失时若被攻击累积了 charge, 仍可发射 (但需有 terminum 弹药)
         * v155.4 用 canConsume() 替代 v7 consValid()
         */
        @Override
        public boolean canConsume() {
            boolean valid = false;
            // 电力检查 (原版 block.consumes.getPower().valid(this))
            if (power != null) {
                valid = power.status > 0.0001f;
            }
            // 蓄能检查 (charge > 0.001 时也认为有效)
            valid |= charge > 0.001f;
            // 物品检查 (terminum): AND 逻辑
            Consume itemCons = block.findConsumer(c -> c instanceof ConsumeItems && !c.booster);
            if (itemCons != null) {
                valid &= itemCons.efficiency(self()) > 0f;
            }
            return valid;
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            charge = read.f();
            resist = read.f();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(charge);
            write.f(resist);
        }

        @Override
        public void draw() {
            float oz = Draw.z();
            Draw.rect(baseRegion, x, y);

            Draw.z(oz + 0.01f);
            Draw.rect(ringABottomRegion, x, y, ringProgress[0]);
            Draw.rect(ringBBottomRegion, x, y, ringProgress[1]);

            Draw.z(oz + 0.02f);
            mindustry.graphics.Drawf.spinSprite(ringARegion, x, y, ringProgress[0]);
            mindustry.graphics.Drawf.spinSprite(ringBRegion, x, y, ringProgress[1]);
            mindustry.graphics.Drawf.spinSprite(ringCRegion, x, y, ringProgress[2]);

            Draw.blend(Blending.additive);

            Draw.z(oz + 0.005f);
            Draw.color(1f, offsetSin(0f, 5f), offsetSin(90f, 5f), eyesAlpha);
            Draw.rect(bottomLightsRegion, x, y);
            Draw.color(1f, offsetSin(0f, 5f), offsetSin(90f, 5f), lightsAlpha * offsetSin(0f, 12f));
            Draw.rect(baseLightsRegion, x, y);

            TextureRegion[] regions = {ringAEyesRegion, ringBEyesRegion, eyeMainRegion};
            TextureRegion[] regionsB = {ringALightsRegion, ringBLightsRegion, ringCLightsRegion};
            float[] trnsScl = {1f, 0.9f, 2f};

            for (int i = 0; i < 3; i++) {
                int h = i + 1;
                Draw.z(oz + 0.015f);
                Draw.color(1f, offsetSin(10f * h, 5f), offsetSin(90f + (10f * h), 5f), eyesAlpha);
                Draw.rect(regions[i], x + (eyeOffset.x * trnsScl[i]), y + (eyeOffset.y * trnsScl[i]), ringProgress[i]);

                Draw.z(oz + 0.025f);
                Draw.color(1f, offsetSin(10f * h, 5f), offsetSin(90f + (10f * h), 5f), lightsAlpha * offsetSin(5 * h, 12f));
                Draw.rect(regionsB[i], x, y, ringProgress[i]);
            }

            Draw.blend();
            Draw.z(oz);
            Draw.color();
        }

        @Override
        public boolean shouldActiveSound() {
            return trueEfficiency() >= 0.0001;
        }

        void killUnits() {
            entitySeq.clear();
            mindustry.entities.Units.nearbyEnemies(team, x - range, y - range, range * 2f, range * 2f, e -> {
                if (Mathf.within(x, y, e.x, e.y, range) && !e.dead) {
                    Object[] data = {new Vec2(x + eyeOffset.x, y + eyeOffset.y), e, 1f};
                    endgameLaserEffect.at(x, y, 0f, data);
                    entitySeq.add(e);
                }
            });
            for (Entityc e : entitySeq) {
                if (e instanceof Unit) {
                    Unit u = (Unit) e;
                    // v155.4 无 AntiCheat.annihilateEntity, 用 damage(MAX) + remove() 替代
                    u.damage(Float.MAX_VALUE);
                    endgameVapourizeEffect.at(u.x, u.y, angleTo(u));
                    u.remove();
                }
            }
            entitySeq.clear();
        }

        void killTiles() {
            entitySeq.clear();
            damageB = 0f;
            shouldLaser = 0;
            // v155.4: Utils.trueEachBlock -> Vars.indexer.eachBlock
            Vars.indexer.eachBlock(null, x, y, range + 5f,
                build -> build.team != team && !build.dead && build != this,
                build -> {
                    if (build.block.size >= 3) {
                        // 简化: 用 endgameVapourizeEffect 替代 UnityFx.vapourizeTile
                        endgameVapourizeEffect.at(build.x, build.y, build.block.size);
                    }
                    if ((shouldLaser % 5) == 0 || build.block.size >= 5) {
                        Object[] data = {new Vec2(x + (eyeOffset.x * 2f), y + (eyeOffset.y * 2f)), build, 1f};
                        endgameLaserEffect.at(x, y, 0f, data);
                    }
                    entitySeq.add(build);
                    shouldLaser++;
                });
            for (Entityc e : entitySeq) {
                damageB = Math.max(((Posc) e).dst2(this), damageB);
                if (e instanceof Building) {
                    Building b = (Building) e;
                    b.damage(Float.MAX_VALUE);
                    b.remove();
                }
            }
            damageB = Mathf.sqrt(damageB) * 2f;
            endGameShootEffect.at(x, y, damageB);
            entitySeq.clear();
        }

        @Override
        public void kill() {
            // 防作弊: lastHealth < 10 才真正死亡
            if (lastHealth < 10f) super.kill();
        }

        void playerShoot(int index) {
            final float rnge = 15f;
            float ux = unit.aimX();
            float uy = unit.aimY();

            if (!Mathf.within(x, y, ux, uy, range * 1.5f)) return;

            // 检测建筑
            Vars.indexer.eachBlock(null, ux, uy, rnge,
                b -> b.team != team && !b.dead,
                building -> {
                    building.damage(490f);
                    Object[] data = {new Vec2(ux, uy), building, 0.525f};
                    endgameLaserEffect.at(x, y, 0f, data);
                });

            // 检测单位
            mindustry.entities.Units.nearbyEnemies(team, ux - rnge, uy - range, range * 2f, range * 2f, e -> {
                if (e.within(ux, uy, rnge + e.hitSize) && !e.dead) {
                    e.damage(490f * threatLevel);
                    if (e.dead) {
                        endgameVapourizeEffect.at(e.x, e.y, angleTo(e));
                        e.remove();
                    }
                    Object[] data = {new Vec2(ux, uy), e, 0.525f};
                    endgameLaserEffect.at(x, y, 0f, data);
                }
            });

            Tmp.v1.set(eyesVecArray[index]);
            Tmp.v1.add(ux, uy);
            Tmp.v1.scl(0.5f);

            Object[] dataB = {eyesVecArray[index], new Vec2(ux, uy), 0.625f};
            endgameLaserEffect.at(Tmp.v1.x, Tmp.v1.y, 0f, dataB);
        }

        void eyeShoot(int index) {
            Healthc e = (Healthc) targets[index];
            if (e != null) {
                e.damage(350f * threatLevel);
                if (e.dead()) {
                    if (e instanceof Unit) {
                        Unit ut = (Unit) e;
                        endgameVapourizeEffect.at(ut.x, ut.y, angleTo(ut));
                    } else if (e instanceof Building) {
                        Building build = (Building) e;
                        endgameVapourizeEffect.at(build.x, build.y, build.block.size);
                    }
                    if (e instanceof Entityc) ((Entityc) e).remove();
                }
                Object[] data = {eyesVecArray[index], e, 0.625f};
                endgameLaserEffect.at(x, y, 0f, data);
            }
        }

        void updateThreats() {
            threatLevel = 1f;
            mindustry.entities.Units.nearbyEnemies(team, x - range, y - range, range * 2, range * 2, e -> {
                if (within(e, range) && e.isAdded()) {
                    float dps = e.type != null ? e.type.dpsEstimate : 0f;
                    threatLevel += Math.max(((e.maxHealth + dps) - 450f) / 1300f, 0f);
                    if (e.speed() >= 18f) {
                        e.vel.setLength(0f);
                    }
                }
            });
        }

        void updateEyesTargeting() {
            for (int i = 0; i < 16; i++) {
                if (mindustry.entities.Units.invalidateTarget(targets[i], team, x, y)) {
                    targets[i] = null;
                }
            }
            updateThreats();
            if (timer.get(eyeTime, 15) && target != null && !isControlled()) {
                // v155.4 无 Utils.nearbyEnemySorted, 自己实现: 收集后按距离排序
                Seq<Healthc> nTargets = new Seq<>();
                mindustry.entities.Units.nearbyEnemies(team, x, y, range, e -> {
                    if (!e.dead) nTargets.add(e);
                });
                nTargets.sort(h -> h instanceof Posc ? ((Posc) h).dst2(this) : Float.MAX_VALUE);
                if (!nTargets.isEmpty()) {
                    int max = Math.min(nTargets.size, 8);
                    for (int i = 0; i < targets.length; i++) {
                        targets[i] = nTargets.get(i % max);
                    }
                }
            }
        }

        void updateEyesOffset() {
            for (int i = 0; i < 16; i++) {
                float angleC = ((360f / 8f) * (i % 8f));
                if (i >= 8) {
                    // 内圈 8 个眼睛 (跟随 ring2 旋转)
                    Tmp.v1.trns(angleC + 22.5f + ringProgress[1], 25.75f);
                } else {
                    // 外圈 8 个眼睛 (跟随 ring1 旋转)
                    Tmp.v1.trns(angleC + ringProgress[0], 36.75f);
                }
                eyesVecArray[i].set(Tmp.v1.x, Tmp.v1.y).add(x, y);
            }
        }

        void updateAntiBullets() {
            entitySeq.clear();
            // 反子弹: 拦截范围内高伤害子弹
            if (trueEfficiency() > 0.0001f && timer.get(bulletTime, 4f / Math.max(trueEfficiency(), 0.001f))) {
                damageFull = 0f;
                // 第一遍: 累加所有敌方子弹伤害
                Groups.bullet.intersect(x - range, y - range, range * 2f, range * 2f, b -> {
                    if (within(b, range) && b.team != team) {
                        damageFull += getBulletDamage(b.type);
                        BulletType current = b.type;
                        totalFrags = 1;

                        for (int i = 0; i < 16; i++) {
                            if (current.fragBullet == null) break;
                            BulletType frag = current.fragBullet;
                            totalFrags *= current.fragBullets;
                            damageFull += getBulletDamage(frag) * totalFrags;
                            current = frag;
                        }
                    }
                });
                // 第二遍: 标记需移除的子弹 (满足任一条件)
                Groups.bullet.intersect(x - range, y - range, range * 2f, range * 2f, b -> {
                    if (within(b, range) && b.team != team) {
                        damageB = getBulletDamage(b.type);
                        BulletType current = b.type;
                        totalFrags = 1;
                        for (int i = 0; i < 16; i++) {
                            if (current.fragBullet == null) break;
                            BulletType frag = current.fragBullet;
                            totalFrags *= current.fragBullets;
                            damageB += getBulletDamage(frag) * totalFrags;
                            current = frag;
                        }
                        if (damageB > 1600f || b.type.splashDamageRadius > 120f
                            || damageFull + damageB > 13000f
                            || (b.owner != null && !within((Posc) b.owner, range))) {
                            entitySeq.add(b);
                            Object[] data = {new Vec2(x + (eyeOffset.x * 2f), y + (eyeOffset.y * 2f)),
                                new Vec2(b.x, b.y), 0.625f};
                            endgameLaserEffect.at(x, y, 0f, data);
                        }
                    }
                });
                if (!entitySeq.isEmpty()) {
                    // v155.4 用 Sounds 或 Z_Sounds 替代 UnitySounds.endgameSmallShoot
                    shootSound.at(x, y);
                }
                for (Entityc e : entitySeq) {
                    e.remove();
                }
                entitySeq.clear();
            }
        }

        boolean verify() {
            // 防作弊: 检测 health 异常 (突然下降超过 860 或 NaN)
            return (health < lastHealth - 860f) || Float.isNaN(health);
        }

        void updateEyes() {
            updateEyesOffset();
            eyeOffsetB.lerpDelta(eyeTargetOffset, 0.12f);

            eyeOffset.set(eyeOffsetB);
            // 关键: reloadCounter / reload (0-1 之间的进度), 不是 reload / reload
            eyeOffset.add(Mathf.range(reloadCounter / reload) / 2, Mathf.range(reloadCounter / reload) / 2);
            eyeOffset.limit(2);

            boolean hasTarget = ((target != null && !isControlled()) || (isControlled() && unit.isShooting()));
            if (hasTarget && canConsume() && trueEfficiency() >= 0.0001f) {
                eyeReloads[0] += deltaB();
                eyeReloads[1] += deltaB();
            }

            if (canConsume() && trueEfficiency() > 0.0001) {
                updateEyesTargeting();
            }

            // 外圈眼睛 (间隔 15tick)
            if (eyeReloads[0] >= 15f) {
                eyeReloads[0] = 0f;
                if (!isControlled()) {
                    if (targets[eyeSequenceA] != null) eyeShoot(eyeSequenceA);
                } else {
                    if (unit.isShooting()) playerShoot(eyeSequenceA);
                }
                eyeSequenceA = (eyeSequenceA + 1) % 8;
            }
            // 内圈眼睛 (间隔 5tick)
            if (eyeReloads[1] >= 5f) {
                eyeReloads[1] = 0f;
                if (!isControlled()) {
                    if (targets[eyeSequenceB] != null) eyeShoot(eyeSequenceB + 8);
                } else {
                    if (unit.isShooting()) playerShoot(eyeSequenceB + 8);
                }
                eyeSequenceB = (eyeSequenceB + 1) % 8;
            }
        }

        @Override
        public void updateTile() {
            enabled = true;
            lastHealth = health;
            // charge 衰减
            charge = Math.max(0f, charge - (Time.delta / 20f));

            // resist 衰减 (15tick 后开始衰减)
            if (resistTime >= 15) {
                resist = Math.max(1f, resist - Time.delta);
            } else {
                resistTime += Time.delta;
            }

            updateEyes();

            // 眼睛透明度 (有 trueEfficiency 时渐显)
            if (trueEfficiency() > 0.0001f) {
                float value = eyesAlpha > trueEfficiency() ? 1f : trueEfficiency();
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, trueEfficiency(), 0.06f * value);
            } else {
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, 0f, 0.06f);
            }

            // 主攻击 gate: 仅 canConsume (有电/蓄能 + 有 terminum) 时才更新 super
            if (canConsume()) {
                updateAntiBullets();
                super.updateTile();
            }

            // 眼睛目标偏移计算
            if (isControlled()) {
                mindustry.gen.Player con = (mindustry.gen.Player) unit.controller();
                eyeTargetOffset.trns(angleTo(con.mouseX, con.mouseY), dst(con.mouseX, con.mouseY) / (range / 3f));
            } else if (target != null && trueEfficiency() > 0.0001f) {
                eyeTargetOffset.trns(angleTo(targetPos.x, targetPos.y), dst(targetPos.x, targetPos.y) / (range / 3f));
            }
            eyeTargetOffset.limit(2f);

            boolean hasTarget = ((target != null && !isControlled()) || (isControlled() && unit.isShooting()))
                && trueEfficiency() > 0.0001f;
            if (hasTarget) {
                eyeResetTime = 0f;
                float value = lightsAlpha > trueEfficiency() ? 1f : trueEfficiency();
                lightsAlpha = Mathf.lerpDelta(lightsAlpha, trueEfficiency(), 0.07f * value);

                // 三层环旋转
                for (int i = 0; i < 3; i++) {
                    ringProgress[i] = Mathf.lerpDelta(ringProgress[i], 360f * ringDirections[i],
                        ringProgresses[i] * trueEfficiency());
                }

                // 慢闪电: 关键 reloadCounter / reload (不是 reload / reload)
                float chance = (((reloadCounter / reload) * 0.90f) + (1f - 0.90f)) * trueEfficiency();
                float randomAngle = Mathf.random(360f);
                // 慢闪电发射位置: 炮台中心 + 18.5f 半径随机偏移 (不是从炮台中心)
                Tmp.v1.trns(randomAngle, 18.5f);
                Tmp.v1.add(x, y);

                if (Mathf.chanceDelta(0.75f * chance)) {
                    // v155.4 简化版: Lightning.create 替代 SlowLightningType, 原版红色 Color.red
                    Lightning.create(team, slowLightningColor, 520f * trueEfficiency(),
                        Tmp.v1.x, Tmp.v1.y, randomAngle, 25);
                }
            } else {
                if (eyeResetTime >= 60f) {
                    lightsAlpha = Mathf.lerpDelta(lightsAlpha, 0f, 0.07f);
                    for (int i = 0; i < 3; i++) {
                        ringProgress[i] = Mathf.lerpDelta(ringProgress[i], 0f,
                            ringProgresses[i] * trueEfficiency());
                    }
                } else {
                    eyeResetTime += Time.delta;
                }
            }
        }

        @Override
        protected void shoot(BulletType type) {
            consume();
            killTiles();
            killUnits();

            endGameShootEffect.at(x, y, 0f);
            shootSound.at(x, y, 1f, 1.5f);
        }

        @Override
        public boolean collision(Bullet other) {
            // 碰撞反弹: 范围外攻击者不受伤, 反弹给攻击者
            float amount = other.owner != null && !within((Posc) other.owner, range) ? 0f
                : other.damage() * other.type.buildingDamageMultiplier;
            damage(amount);
            if (other.owner != null && !within((Posc) other.owner, range)) {
                Healthc en = (Healthc) other.owner;
                en.damage(0.5f * en.maxHealth() * Math.max(resist / 10f, 1f));
            }
            return true;
        }

        @Override
        public void add() {
            // v155.4 用 isAdded() 替代 added 字段检查 (added 为 private)
            if (isAdded()) return;
            for (int i = 0; i < 16; i++) {
                eyesVecArray[i] = new Vec2();
                targets[i] = null;
            }
            super.add();
        }

        @Override
        public void remove() {
            // v155.4 用 isAdded() 替代 added 字段检查
            if (!isAdded()) return;
            // v155.4 无 Unity.antiCheat, 直接 super.remove
            super.remove();
        }

        /** 简化版 offsetSin (PU_V8 Utils.offsetSin) */
        private float offsetSin(float offset, float period) {
            return Mathf.absin(Time.time + offset, period, 0.5f) + 0.5f;
        }

        /** 简化版 getBulletDamage (PU_V8 Utils.getBulletDamage): 递归累加 fragBullet 伤害 + splashDamage */
        private float getBulletDamage(BulletType type) {
            if (type == null) return 0f;
            float dmg = type.damage;
            if (type.splashDamageRadius > 0) {
                dmg += type.splashDamage;
            }
            return dmg;
        }
    }
}
