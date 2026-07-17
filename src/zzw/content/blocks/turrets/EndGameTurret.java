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
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/**
 * PU_V8 EndGameTurret 移植版 (endgame) - v158 适配
 *
 * 核心机制:
 * - 三层旋转环渲染 (ring1/ring2/ring3, 各自方向和速度)
 * - 16 个眼睛位置追踪射击 (8 外圈 + 8 内圈, 不同间隔)
 * - 威胁等级系统 (基于范围内敌方单位)
 * - 防作弊伤害系统 (resist 抗性, charge 充能)
 * - 反子弹系统 (拦截范围内高伤害子弹)
 * - 主射击 (范围摧毁所有敌方建筑和单位)
 * - 慢闪电 (持续随机闪电)
 * - 碰撞反弹 (受击反弹给范围外攻击者)
 *
 * 简化:
 * - 用 v158 原生 Lightning 替代 SlowLightningType
 * - 用简单 Effect 替代 UnityFx.endgameLaser / vapourizeTile / endgameVapourize / endGameShoot
 * - 移除 Unity.antiCheat 系统, 直接调用 entity.damage() + remove()
 * - 移除 ParentEffect, 用普通 Effect
 *
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/EndGameTurret.java
 */
public class EndGameTurret extends PowerTurret {
    private static final float[] RING_PROGRESSES = {0.013f, 0.035f, 0.024f};
    private static final int[] RING_DIRECTIONS = {1, -1, 1};
    private static final Seq<Entityc> ENTITY_SEQ = new Seq<>(512);

    public TextureRegion baseRegion, baseLightsRegion, bottomLightsRegion, eyeMainRegion,
        ringABottomRegion, ringAEyesRegion, ringARegion, ringALightsRegion,
        ringBBottomRegion, ringBEyesRegion, ringBRegion, ringBLightsRegion,
        ringCRegion, ringCLightsRegion;

    // 简化版特效
    public static final Effect endgameLaserEffect = new Effect(22f, 400f, e -> {
        if (!(e.data instanceof Object[])) return;
        Object[] data = (Object[]) e.data;
        if (data.length < 3) return;
        Vec2 from = (Vec2) data[0];
        Object target = data[1];
        float width = ((Float) data[2]);
        if (target instanceof Posc) {
            Posc p = (Posc) target;
            Draw.color(Color.valueOf("f53036"), Color.white, e.fout());
            Lines.stroke(width * 2f * e.fout());
            Lines.line(from.x, from.y, p.getX(), p.getY(), false);
            Fill.circle(from.x, from.y, width * 2f * e.fout());
            Fill.circle(p.getX(), p.getY(), width * 3f * e.fout());
        }
    });

    public static final Effect endgameVapourizeEffect = new Effect(60f, 400f, e -> {
        // 简化蒸发特效: 红色粒子环爆开
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

    public static final Effect endGameShootEffect = new Effect(80f, 800f, e -> {
        // 主射击大型爆炸: 红色冲击波 + 中心闪光
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
        reload = 430f;
        range = 820f;
        size = 14;
        shootCone = 360f;
        absorbLasers = true;
        shake = 2.2f;
        outlineIcon = false;
        noUpdateDisabled = false;
        // 原版 powerUse = 320f (v158 用 consumePower 替代 v7 powerUse 字段)
        consumePower(320f);
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
        protected float chargeValue = 0f;
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

        public EndGameTurretBuild() {
            for (int i = 0; i < 16; i++) {
                eyesVecArray[i] = new Vec2();
                targets[i] = null;
            }
        }

        @Override
        public void damage(float damage) {
            if (verify()) return;
            if (damage > 10000) chargeValue += Mathf.clamp(damage - 10000f, 0f, 2000000f) / 150f;
            if (chargeValue > 15) chargeValue = 15f;

            float trueAmount = Mathf.clamp(damage / resist, 0f, 410f);
            super.damage(trueAmount);

            resist += 0.125f + (Mathf.clamp(damage - 520f, 0f, Float.MAX_VALUE) / 70f);
            if (Float.isNaN(resist)) resist = Float.MAX_VALUE;
            resistTime = 0f;
        }

        @Override
        protected float baseReloadSpeed() {
            return Mathf.clamp(efficiency + chargeValue, 0f, 1.2f);
        }

        float trueEfficiency() {
            return Mathf.clamp(efficiency + chargeValue);
        }

        /**
         * 原版 consValid 逻辑: (电力 OR 蓄能) AND 物品
         * 电力缺失时若被攻击累积了 chargeValue, 仍可发射 (但需有 terminum 弹药)
         * v158 用 canConsume() 替代 v7 consValid()
         */
        @Override
        public boolean canConsume() {
            boolean valid = false;
            if (power != null) {
                valid = power.status > 0.0001f;
            }
            valid |= chargeValue > 0.001f;
            // 检查物品消耗 (terminum)
            if (items == null) return false;
            for (var cons : block.consumers) {
                if (cons instanceof mindustry.world.consumers.ConsumeItems ci && !ci.booster) {
                    if (!items.has(ci.items, 1f)) return false;
                }
            }
            return valid;
        }

        float deltaB() {
            return delta() * baseReloadSpeed();
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            chargeValue = read.f();
            resist = read.f();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(chargeValue);
            write.f(resist);
        }

        @Override
        public void draw() {
            float oz = Draw.z();
            Draw.rect(baseRegion, x, y);

            Draw.z(oz + 0.01f);
            if (ringABottomRegion.found()) Draw.rect(ringABottomRegion, x, y, ringProgress[0]);
            if (ringBBottomRegion.found()) Draw.rect(ringBBottomRegion, x, y, ringProgress[1]);

            Draw.z(oz + 0.02f);
            if (ringARegion.found()) mindustry.graphics.Drawf.spinSprite(ringARegion, x, y, ringProgress[0]);
            if (ringBRegion.found()) mindustry.graphics.Drawf.spinSprite(ringBRegion, x, y, ringProgress[1]);
            if (ringCRegion.found()) mindustry.graphics.Drawf.spinSprite(ringCRegion, x, y, ringProgress[2]);

            Draw.blend(Blending.additive);

            Draw.z(oz + 0.005f);
            Draw.color(1f, offsetSin(0f, 5f), offsetSin(90f, 5f), eyesAlpha);
            if (bottomLightsRegion.found()) Draw.rect(bottomLightsRegion, x, y);
            Draw.color(1f, offsetSin(0f, 5f), offsetSin(90f, 5f), lightsAlpha * offsetSin(0f, 12f));
            if (baseLightsRegion.found()) Draw.rect(baseLightsRegion, x, y);

            TextureRegion[] regions = {ringAEyesRegion, ringBEyesRegion, eyeMainRegion};
            TextureRegion[] regionsB = {ringALightsRegion, ringBLightsRegion, ringCLightsRegion};
            float[] trnsScl = {1f, 0.9f, 2f};

            for (int i = 0; i < 3; i++) {
                int h = i + 1;
                Draw.z(oz + 0.015f);
                Draw.color(1f, offsetSin(10f * h, 5f), offsetSin(90f + (10f * h), 5f), eyesAlpha);
                if (regions[i].found()) {
                    Draw.rect(regions[i], x + (eyeOffset.x * trnsScl[i]), y + (eyeOffset.y * trnsScl[i]), ringProgress[i]);
                }

                Draw.z(oz + 0.025f);
                Draw.color(1f, offsetSin(10f * h, 5f), offsetSin(90f + (10f * h), 5f), lightsAlpha * offsetSin(5 * h, 12f));
                if (regionsB[i].found()) Draw.rect(regionsB[i], x, y, ringProgress[i]);
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
            ENTITY_SEQ.clear();
            mindustry.entities.Units.nearbyEnemies(team, x - range, y - range, range * 2f, range * 2f, e -> {
                if (Mathf.within(x, y, e.x, e.y, range) && !e.dead) {
                    Object[] data = {new Vec2(x + eyeOffset.x, y + eyeOffset.y), e, 1f};
                    endgameLaserEffect.at(x, y, 0f, data);
                    ENTITY_SEQ.add(e);
                }
            });
            for (Entityc e : ENTITY_SEQ) {
                if (e instanceof Unit) {
                    Unit u = (Unit) e;
                    u.damage(Float.MAX_VALUE);
                    endgameVapourizeEffect.at(u.x, u.y, angleTo(u));
                }
            }
            ENTITY_SEQ.clear();
        }

        void killTiles() {
            ENTITY_SEQ.clear();
            Vars.indexer.eachBlock(null, x, y, range + 5f,
                build -> build.team != team && !build.dead && build != this,
                build -> {
                    if (build.block.size >= 3) {
                        // 简化: 不渲染 vapourizeTile 特效, 仅用 endgameVapourizeEffect
                        endgameVapourizeEffect.at(build.x, build.y, build.block.size);
                    }
                    Object[] data = {new Vec2(x + (eyeOffset.x * 2f), y + (eyeOffset.y * 2f)), build, 1f};
                    endgameLaserEffect.at(x, y, 0f, data);
                    ENTITY_SEQ.add(build);
                });
            for (Entityc e : ENTITY_SEQ) {
                if (e instanceof Building) {
                    Building b = (Building) e;
                    b.damage(Float.MAX_VALUE);
                }
            }
            endGameShootEffect.at(x, y, 0f);
            ENTITY_SEQ.clear();
        }

        @Override
        public void kill() {
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
                    }
                    Object[] data = {new Vec2(ux, uy), e, 0.525f};
                    endgameLaserEffect.at(x, y, 0f, data);
                }
            });

            Vec2 eyePos = eyesVecArray[index];
            Object[] dataB = {eyePos, new Vec2(ux, uy), 0.625f};
            endgameLaserEffect.at((eyePos.x + ux) / 2f, (eyePos.y + uy) / 2f, 0f, dataB);
        }

        void eyeShoot(int index) {
            Posc target = targets[index];
            if (target != null && target instanceof Healthc) {
                Healthc e = (Healthc) target;
                e.damage(350f * threatLevel);
                if (e.dead()) {
                    if (e instanceof Unit) {
                        Unit ut = (Unit) e;
                        endgameVapourizeEffect.at(ut.x, ut.y, angleTo(ut));
                    } else if (e instanceof Building) {
                        Building build = (Building) e;
                        endgameVapourizeEffect.at(build.x, build.y, build.block.size);
                    }
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
            if (timer.get(0, 15) && target != null && !isControlled()) {
                // 简化: 取最近 8 个敌方目标
                Seq<Healthc> nTargets = new Seq<>();
                mindustry.entities.Units.nearbyEnemies(team, x, y, range, e -> {
                    if (!e.dead) nTargets.add(e);
                });
                // 按距离排序
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
                    Tmp.v1.trns(angleC + 22.5f + ringProgress[1], 25.75f);
                } else {
                    Tmp.v1.trns(angleC + ringProgress[0], 36.75f);
                }
                eyesVecArray[i].set(Tmp.v1.x, Tmp.v1.y).add(x, y);
            }
        }

        void updateAntiBullets() {
            if (trueEfficiency() > 0.0001f && timer.get(1, 4f / Math.max(trueEfficiency(), 0.001f))) {
                ENTITY_SEQ.clear();
                Groups.bullet.intersect(x - range, y - range, range * 2f, range * 2f, b -> {
                    if (within(b, range) && b.team != team) {
                        float bulletDamage = getBulletDamage(b.type);
                        boolean shouldRemove = bulletDamage > 1600f
                            || b.type.splashDamageRadius > 120f
                            || (b.owner != null && !within((Posc) b.owner, range));
                        if (shouldRemove) {
                            ENTITY_SEQ.add(b);
                            Object[] data = {
                                new Vec2(x + (eyeOffset.x * 2f), y + (eyeOffset.y * 2f)),
                                new Vec2(b.x, b.y),
                                0.625f
                            };
                            endgameLaserEffect.at(x, y, 0f, data);
                        }
                    }
                });
                for (Entityc e : ENTITY_SEQ) {
                    if (e instanceof Bullet) ((Bullet) e).remove();
                }
                ENTITY_SEQ.clear();
            }
        }

        boolean verify() {
            return (health < lastHealth - 860f) || Float.isNaN(health);
        }

        void updateEyes() {
            updateEyesOffset();
            eyeOffsetB.lerpDelta(eyeTargetOffset, 0.12f);

            eyeOffset.set(eyeOffsetB);
            eyeOffset.add(Mathf.range(reload / reload) / 2, Mathf.range(reload / reload) / 2);
            eyeOffset.limit(2);

            boolean hasTarget = ((target != null && !isControlled()) || (isControlled() && unit.isShooting()));
            if (hasTarget && canConsume() && trueEfficiency() >= 0.0001f) {
                eyeReloads[0] += deltaB();
                eyeReloads[1] += deltaB();
            }

            if (canConsume() && trueEfficiency() > 0.0001) {
                updateEyesTargeting();
            }

            if (eyeReloads[0] >= 15f) {
                eyeReloads[0] = 0f;
                if (!isControlled()) {
                    if (targets[eyeSequenceA] != null) eyeShoot(eyeSequenceA);
                } else {
                    if (unit.isShooting()) playerShoot(eyeSequenceA);
                }
                eyeSequenceA = (eyeSequenceA + 1) % 8;
            }
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
            chargeValue = Math.max(0f, chargeValue - (Time.delta / 20f));

            if (resistTime >= 15) {
                resist = Math.max(1f, resist - Time.delta);
            } else {
                resistTime += Time.delta;
            }

            updateEyes();

            if (trueEfficiency() > 0.0001f) {
                float value = eyesAlpha > trueEfficiency() ? 1f : trueEfficiency();
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, trueEfficiency(), 0.06f * value);
            } else {
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, 0f, 0.06f);
            }

            if (canConsume()) {
                updateAntiBullets();
                super.updateTile();
            }

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

                for (int i = 0; i < 3; i++) {
                    ringProgress[i] = Mathf.lerpDelta(ringProgress[i], 360f * RING_DIRECTIONS[i], RING_PROGRESSES[i] * trueEfficiency());
                }

                // 慢闪电
                float chance = (((reload / reload) * 0.90f) + (1f - 0.90f)) * trueEfficiency();
                float randomAngle = Mathf.random(360f);
                Tmp.v1.trns(randomAngle, 18.5f).add(x, y);

                if (Mathf.chanceDelta(0.75f * chance)) {
                    Lightning.create(team, Color.valueOf("f53036"), 520f * trueEfficiency(),
                        Tmp.v1.x, Tmp.v1.y, randomAngle, 25);
                }
            } else {
                if (eyeResetTime >= 60f) {
                    lightsAlpha = Mathf.lerpDelta(lightsAlpha, 0f, 0.07f);
                    for (int i = 0; i < 3; i++) {
                        ringProgress[i] = Mathf.lerpDelta(ringProgress[i], 0f, RING_PROGRESSES[i] * trueEfficiency());
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
            if (added) return;
            for (int i = 0; i < 16; i++) {
                eyesVecArray[i] = new Vec2();
                targets[i] = null;
            }
            super.add();
        }

        /** 简化版 offsetSin (PU132 Utils.offsetSin) */
        private float offsetSin(float offset, float period) {
            return Mathf.absin(Time.time + offset, period, 0.5f) + 0.5f;
        }

        /** 简化版 getBulletDamage (PU132 Utils.getBulletDamage) */
        private float getBulletDamage(BulletType type) {
            if (type == null) return 0f;
            float dmg = type.damage;
            // 递归累加 fragBullet 伤害
            BulletType current = type;
            int totalFrags = 1;
            for (int i = 0; i < 16; i++) {
                if (current.fragBullet == null) break;
                BulletType frag = current.fragBullet;
                totalFrags *= current.fragBullets;
                dmg += frag.damage * totalFrags;
                current = frag;
            }
            // 累加 splashDamage
            if (type.splashDamageRadius > 0) {
                dmg += type.splashDamage;
            }
            return dmg;
        }
    }
}
