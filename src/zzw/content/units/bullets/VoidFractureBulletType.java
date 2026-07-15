package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;
import arc.struct.IntSet;
import arc.util.Tmp;
import arc.audio.Sound;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

/**
 * 虚空碎裂弹 (完全复刻 PU132 VoidFractureBulletType)
 *
 * PU132 原版两阶段状态机:
 * - Phase 1 (delay=30 tick): 悬停跟踪目标, 黑色小三角
 * - Phase 2 (nextLifetime=10 tick): 直线冲刺穿透, 黑色激光束效果 (类似压迫者大招激光)
 * - 冲刺结束 (removed): 播放 voidFractureEffect 特效 (30tick 三层激光+spikes)
 *
 * 关键: bullet.fdata 控制阶段 (<=0=Phase1, >0=Phase2)
 *       bullet.data = FractureData (存储目标、起点、collided集合)
 *
 * ★ 完全按 PU132 原版, 纯黑色绘制, 不加描边
 */
public class VoidFractureBulletType extends AntiCheatBulletTypeBase {
    public float length = 28f;
    public float width = 12f;
    public float widthTo = 3f;
    public float delay = 30f;
    public float targetingRange = 320f;
    public float trueSpeed;
    public float nextLifetime = 10f;
    public int maxTargets = 15;
    public float spikesRange = 100f;
    public float spikesDamage = 200f;
    public float spikesRand = 8f;
    /** ★ PU132 原版: activeSound = UnitySounds.fractureShoot (Phase 2 冲刺启动时播放) */
    public Sound activeSound;
    /** ★ PU132 原版: spikesSound = UnitySounds.spaceFracture (spikes 命中时播放) */
    public Sound spikesSound;

    /** ★ PU132: layer = Layer.effect + 0.03f (渲染层级在 effect 之上) */
    private static Effect voidFractureEffect;

    public VoidFractureBulletType(float speed, float damage) {
        // ★ PU132 原版设计: super(4.3f, damage) 是有意的
        //   4.3f 是 Phase 1 悬停段初速度, 配合 drag=0.11f 在 30 帧内衰减到 ~0.13 实现悬停
        //   trueSpeed(=入参 speed) 是 Phase 2 冲刺速度, 在 update 中显式设置
        super(4.3f, damage);
        drag = 0.11f;
        trueSpeed = speed;
        collides = false;
        collidesTiles = false;
        keepVelocity = false;
        // ★ PU132 原版: layer = Layer.effect + 0.03f
        layer = Layer.effect + 0.03f;
        pierce = true;
        despawnEffect = Fx.none;
        smokeEffect = Fx.none;
        hitEffect = Fx.hitLancer;  // PU132: HitFx.voidHit, v158 用 Fx.hitLancer 替代
        // ★ 关键: lifetime 必须 > delay, 否则子弹在 Phase 1 悬停阶段就消失
        lifetime = 60f;
    }

    @Override
    public void init() {
        super.init();
        // v158: range 是字段, 直接设置 (PU132 原版 range() = trueSpeed * nextLifetime)
        range = trueSpeed * nextLifetime;
        drawSize = (range * 2f) + 30f;
        // 初始化 voidFractureEffect (PU132 SpecialFx.voidFractureEffect)
        if (voidFractureEffect == null) {
            voidFractureEffect = new Effect(30f, 700f, e -> {
                if (!(e.data instanceof VoidFractureData data)) return;
                float rot = Angles.angle(data.x, data.y, data.x2, data.y2);

                // ★ 显式设置渲染层级, 确保余晖激光显示在飞行单位之上
                Draw.z(Layer.flyingUnit + 1f);
                Draw.blend();
                Draw.color(Color.black);
                for (int i = 0; i < 3; i++) {
                    float f = Mathf.lerp(data.b.width, data.b.widthTo, i / 2f);
                    float a = Mathf.lerp(0.25f, 1f, (i / 2f) * (i / 2f));

                    Draw.alpha(a);
                    Lines.stroke(f * e.fout());
                    Lines.line(data.x, data.y, data.x2, data.y2, false);
                    Drawf.tri(data.x2, data.y2, f * 1.22f * e.fout(), f * 2f, rot);
                    Drawf.tri(data.x, data.y, f * 1.22f * e.fout(), f * 2f, rot + 180f);
                }

                FloatSeq s = data.spikes;
                if (!s.isEmpty()) {
                    for (int i = 0; i < data.spikes.size; i += 4) {
                        float x1 = s.get(i), y1 = s.get(i + 1), x2 = s.get(i + 2), y2 = s.get(i + 3);
                        Drawf.tri(x1, y1, (data.b.widthTo + 1f) * e.fout(),
                            Mathf.dst(x1, y1, x2, y2) * 2f * Mathf.curve(e.fin(), 0f, 0.2f),
                            Angles.angle(x1, y1, x2, y2));
                        Fill.circle(x1, y1, ((data.b.widthTo + 1f) / 1.22f) * e.fout());
                    }
                }
            }).layer(Layer.flyingUnit + 1f);
            // ★ v158: Effect 必须调用 init() 注册到 Effects, 否则 Effect.at() 不会渲染
            voidFractureEffect.init();
        }
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data(new FractureData());
        System.out.println("[VF] init id=" + b.id + " pos=(" + b.x() + "," + b.y() + ") rot=" + b.rotation()
            + " team=" + b.team + " vel=(" + b.vel().x + "," + b.vel().y + ")"
            + " lifetime=" + b.lifetime() + " data=" + (b.data() == null ? "null" : b.data().getClass().getSimpleName()));
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        if (!(b.data() instanceof FractureData data)) {
            System.out.println("[VF] update no-FractureData id=" + b.id + " data=" + (b.data() == null ? "null" : b.data().getClass().getName()));
            return;
        }

        try {
            if (b.fdata() <= 0f) {
                // ===== Phase 1: 悬停跟踪 (PU132 L62-100) =====
                if (data.target != null && !data.target.isValid()) data.target = null;
                if (data.target == null && b.timer(1, 5f)) {
                    searchTarget(b, data);
                }
                if (data.target != null) {
                    b.rotation(Mathf.slerpDelta(b.rotation(), b.angleTo(data.target), 0.1f));
                }
                // 每 10 帧打印一次 Phase 1 状态 (调试用)
                if (((int) b.time()) % 10 == 0 && ((int) b.time()) > 0) {
                    System.out.println("[VF] P1 id=" + b.id + " t=" + b.time() + " pos=(" + b.x() + "," + b.y()
                        + ") vel=(" + b.vel().x + "," + b.vel().y + ")"
                        + " target=" + (data.target == null ? "null" : "ok"));
                }
                // 到时间进入 Phase 2
                if (b.time() >= delay) {
                    System.out.println("[VF] >>> P2 START id=" + b.id + " pos=(" + b.x() + "," + b.y()
                        + ") rot=" + b.rotation() + " vel_before=(" + b.vel().x + "," + b.vel().y + ")");
                    b.time(0f);
                    b.lifetime(nextLifetime);
                    b.fdata(1f);
                    // ★ v158: BulletComp.update() 用 type.drag 衰减 vel, Bullet 无 drag 字段
                    //   不能用 b.drag(0f), 改为在 Phase 2 每帧重置 vel 克服衰减
                    b.vel().trns(b.rotation(), trueSpeed);
                    data.x = b.x();
                    data.y = b.y();
                    System.out.println("[VF]     P2 setup done vel_after=(" + b.vel().x + "," + b.vel().y
                        + ") data.xy=(" + data.x + "," + data.y + ")");
                    // ★ PU132 L99: activeSound.at(b.x, b.y, Mathf.random(0.9f, 1.1f))
                    if (activeSound != null) {
                        activeSound.at(b.x(), b.y(), Mathf.random(0.9f, 1.1f));
                    }
                }
            } else {
                // ===== Phase 2: 冲刺穿透 (PU132 L101-122) =====
                // ★ v158: type.drag 会每帧衰减 vel, 需每帧重置 vel 维持冲刺速度
                b.vel().trns(b.rotation(), trueSpeed);
                // 每帧打印 Phase 2 状态 (调试用)
                System.out.println("[VF] P2 id=" + b.id + " t=" + b.time() + " pos=(" + b.x() + "," + b.y()
                    + ") last=(" + b.lastX() + "," + b.lastY() + ") vel=(" + b.vel().x + "," + b.vel().y
                    + ") dist_from_data=" + Mathf.dst(b.x(), b.y(), data.x, data.y));
                collideLineRawEnemySimple(b, b.lastX(), b.lastY(), b.x(), b.y(), data);
            }
        } catch (Throwable t) {
            // 防崩溃: 出错时打印异常栈
            System.out.println("[VoidFracture] update error: " + t);
            t.printStackTrace();
        }
    }

    /**
     * 搜索最近目标 (PU132 L67-86)
     * 用圆形 nearbyEnemies + allBuildings, 按 score(距离+角度差) 选择
     */
    protected void searchTarget(Bullet b, FractureData data) {
        final float[] bestScore = {Float.MAX_VALUE};
        final Healthc[] best = {null};
        Units.nearbyEnemies(b.team, b.x(), b.y(), targetingRange, u -> {
            if (!u.isValid()) return;
            if (!Angles.within(b.rotation(), b.angleTo(u), 45f)) return;
            float score = u.dst2(b) + Mathf.pow(Angles.angleDist(b.rotation(), b.angleTo(u)), 4f);
            if (score < bestScore[0]) {
                bestScore[0] = score;
                best[0] = u;
            }
        });
        Vars.indexer.allBuildings(b.x(), b.y(), targetingRange, build -> {
            if (build.team == b.team) return;
            if (!Angles.within(b.rotation(), b.angleTo(build), 45f)) return;
            float score = build.dst2(b) + Mathf.pow(Angles.angleDist(b.rotation(), b.angleTo(build)), 4f);
            if (score < bestScore[0]) {
                bestScore[0] = score;
                best[0] = build;
            }
        });
        data.target = best[0];
    }

    /**
     * 简化版 collideLineRawEnemy (PU132 原版用 arc struct + 复杂排序)
     * 沿 (x1,y1) → (x2,y2) 线段检测敌方单位和建筑
     */
    protected void collideLineRawEnemySimple(Bullet b, float x1, float y1, float x2, float y2, FractureData data) {
        // 检测单位 (用圆形 nearbyEnemies, 范围=线段长度)
        float segLen = Mathf.len(x2 - x1, y2 - y1);
        float radius = segLen * 0.5f + 8f;
        float cx = (x1 + x2) * 0.5f;
        float cy = (y1 + y2) * 0.5f;
        Units.nearbyEnemies(b.team, cx, cy, radius, u -> {
            if (!u.hittable()) return;
            if (!u.checkTarget(collidesAir, collidesGround)) return;
            float r = u.hitSize / 2f;
            if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, u.x(), u.y()) <= r) {
                if (data.collided.add(u.id())) {
                    hitUnitAntiCheat(b, u);
                }
            }
        });
        // 检测建筑
        Vars.indexer.allBuildings(cx, cy, radius, build -> {
            if (build.team == b.team) return;
            float r = build.block.size * Vars.tilesize / 2f;
            if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, build.x(), build.y()) <= r) {
                if (data.collided.add(build.id())) {
                    hitBuildingAntiCheat(b, build);
                }
                // absorbLasers 检查: 阻挡子弹
                if (build.block.absorbLasers) {
                    Tmp.v1.set(b.x(), b.y()).sub(b.lastX(), b.lastY()).setLength2(build.dst2(b.lastX(), b.lastY())).add(b.lastX(), b.lastY());
                    b.set(Tmp.v1.x, Tmp.v1.y);
                    b.vel().setZero();
                }
            }
        });
    }

    /**
     * ★ PU132 原版 hitEntity (L127-138)
     * 对 Healthc 造成 max(damage, maxHealth*ratioDamage) 伤害
     */
    @Override
    public void hitEntity(Bullet b, Hitboxc entity, float health) {
        if (entity instanceof Healthc h) {
            h.damage(Math.max(b.damage, h.maxHealth() * ratioDamage));
        }

        if (entity instanceof Unit unit) {
            Tmp.v3.set(unit).sub(b).nor().scl(knockback * 80f);
            if (impact) Tmp.v3.setAngle(b.rotation() + (knockback < 0 ? 180f : 0f));
            unit.impulse(Tmp.v3);
            unit.apply(status, statusDuration);
        }
    }

    @Override
    public void removed(Bullet b) {
        super.removed(b);
        // ===== Phase 2 结束: 生成 spikes + 播放 voidFractureEffect (PU132 L141-186) =====
        try {
            System.out.println("[VF] removed id=" + b.id + " pos=(" + b.x() + "," + b.y()
                + ") fdata=" + b.fdata() + " hit=" + b.hit()
                + " data=" + (b.data() == null ? "null" : b.data().getClass().getSimpleName()));
            if (b.fdata() >= 1f && b.data() instanceof FractureData data) {
                VoidFractureData d = new VoidFractureData();
                d.x = data.x;
                d.y = data.y;
                d.x2 = b.x();
                d.y2 = b.y();
                d.b = this;
                System.out.println("[VF]     removed d.x=" + d.x + " d.y=" + d.y + " d.x2=" + d.x2 + " d.y2=" + d.y2
                    + " laser_len=" + Mathf.dst(d.x, d.y, d.x2, d.y2));

                if (!b.hit()) {
                    spawnSpikes(b, data, d);
                }
                // ★ PU132 L182: spikesSound.at((d.x + d.x2)/2, (d.y + d.y2)/2, Mathf.random(0.9f, 1.1f))
                if (!d.spikes.isEmpty() && spikesSound != null) {
                    spikesSound.at((d.x + d.x2) / 2f, (d.y + d.y2) / 2f, Mathf.random(0.9f, 1.1f));
                }
                // ★ PU132: 播放 voidFractureEffect (30tick 三层激光+spikes 特效)
                voidFractureEffect.at((d.x + d.x2) / 2f, (d.y + d.y2) / 2f, 0f, d);
                System.out.println("[VF]     voidFractureEffect.at called, spikes=" + d.spikes.size / 4);
            }
        } catch (Throwable t) {
            System.out.println("[VoidFracture] removed error: " + t);
            t.printStackTrace();
        }
    }

    /**
     * 沿冲刺路径生成 spikes 伤害 (PU132 L153-184)
     */
    protected void spawnSpikes(Bullet b, FractureData data, VoidFractureData d) {
        int[] count = {0};
        // 线段中心点和半径
        float cx = (data.x + b.x()) * 0.5f;
        float cy = (data.y + b.y()) * 0.5f;
        float radius = Mathf.len(b.x() - data.x, b.y() - data.y) * 0.5f + spikesRange;
        // 单位
        Units.nearbyEnemies(b.team, cx, cy, radius, u -> {
            if (count[0] >= maxTargets) return;
            if (!u.hittable()) return;
            if (!u.checkTarget(collidesAir, collidesGround)) return;
            Vec2 v = Intersector.nearestSegmentPoint(data.x, data.y, b.x(), b.y(),
                u.x() + Mathf.range(spikesRand), u.y() + Mathf.range(spikesRand), Tmp.v1);
            if (v.dst(u) > spikesRange + u.hitSize / 2f) return;
            count[0]++;
            u.damagePierce(spikesDamage);
            u.apply(status, statusDuration);
            hitEffect.at(v.x, v.y, v.angleTo(u));
            // 记录到 spikes 序列 (用于 voidFractureEffect 绘制)
            d.spikes.add(v.x, v.y, u.x(), u.y());
        });
        // 建筑
        Vars.indexer.allBuildings(cx, cy, radius, build -> {
            if (count[0] >= maxTargets) return;
            if (build.team == b.team) return;
            Vec2 v = Intersector.nearestSegmentPoint(data.x, data.y, b.x(), b.y(),
                build.x() + Mathf.range(spikesRand), build.y() + Mathf.range(spikesRand), Tmp.v1);
            if (v.dst(build) > spikesRange + build.block.size * Vars.tilesize / 2f) return;
            count[0]++;
            build.damagePierce(spikesDamage);
            hitEffect.at(v.x, v.y, v.angleTo(build));
            d.spikes.add(v.x, v.y, build.x(), build.y());
        });
    }

    /**
     * ★ 完全按 PU132 原版 draw (L189-213)
     * 纯黑色绘制, 三层激光束叠加
     *
     * ★ v158 适配:
     *   - 显式设置 Draw.z(Layer.flyingUnit + 1f), 确保激光显示在飞行单位之上
     *   (BulletComp.draw() 设的 Draw.z(type.layer) = Layer.effect+0.03f 可能被单位覆盖)
     *   - 显式调用 Draw.blend() 重置混合模式
     *   - P2 第一帧 pos==data 时跳过 Lines.line, 避免 length=0 导致 NaN 顶点
     */
    @Override
    public void draw(Bullet b) {
        if (!(b.data() instanceof FractureData data)) {
            return;
        }
        // ★ 显式设置渲染层级 (像 OppressionLaserBulletType 那样)
        Draw.z(Layer.flyingUnit + 1f);
        Draw.blend();
        Draw.color(Color.black);
        if (b.fdata() <= 0f) {
            // ===== Phase 1: 小三角 (PU132 L192-195) =====
            float in = Mathf.clamp(b.time() / delay);
            Drawf.tri(b.x(), b.y(), width * in, length, b.rotation());
            Drawf.tri(b.x(), b.y(), width * in, length, b.rotation() + 180f);
        } else {
            // ===== Phase 2: 激光束 (PU132 L196-211) =====
            float w = width * b.fout();
            Drawf.tri(b.x(), b.y(), w, length, b.rotation());
            Drawf.tri(b.x(), b.y(), w, length / 2f, b.rotation() + 180f);
            // ★ P2 第一帧 pos==data 时, Lines.line 会因 length=0 产生 NaN 顶点, 跳过
            float segLen2 = b.dst2(data.x, data.y);
            float ang = segLen2 >= 0.0001f ? b.angleTo(data.x, data.y) : b.rotation() + 180f;
            if (segLen2 >= 0.0001f) {
                // 三层叠加的激光束 (PU132 原版)
                for (int i = 0; i < 3; i++) {
                    float f = Mathf.lerp(width, widthTo, i / 2f);
                    float a = Mathf.lerp(0.25f, 1f, (i / 2f) * (i / 2f));
                    Draw.alpha(a);
                    Lines.stroke(f);
                    Lines.line(data.x, data.y, b.x(), b.y(), false);
                    Drawf.tri(b.x(), b.y(), f * 1.22f, f * 2f, ang + 180f);
                    Drawf.tri(data.x, data.y, f * 1.22f, f * 2f, ang);
                }
            }
        }
    }

    @Override
    public void drawLight(Bullet b) {
    }

    /**
     * Fracture 数据 (PU132 L226-230)
     * - target: 跟踪目标 (悬停阶段)
     * - x, y: 冲刺起点 (用于绘制激光束)
     * - collided: 已碰撞 id 集合 (避免重复伤害)
     */
    public static class FractureData {
        public Healthc target;
        public float x, y;
        public IntSet collided = new IntSet();
    }

    /**
     * VoidFractureData (PU132 SpecialFx.VoidFractureData L185-189)
     * 用于 removed() 时传递给 voidFractureEffect 绘制
     */
    public static class VoidFractureData {
        public float x, y, x2, y2;
        public VoidFractureBulletType b;
        public FloatSeq spikes = new FloatSeq();
    }
}
