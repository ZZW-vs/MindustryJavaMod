package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.struct.IntSet;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

/**
 * 虚空碎裂弹 (完全复刻 PU132 VoidFractureBulletType)
 *
 * PU132 原版两阶段状态机:
 * - Phase 1 (delay=30 tick): 悬停跟踪目标, 黑色小三角
 * - Phase 2 (nextLifetime=10 tick): 直线冲刺穿透, 黑色激光束效果 (类似压迫者大招激光)
 * - 冲刺结束时: 沿路径生成 spikes 伤害
 *
 * 关键: bullet.fdata 控制阶段 (<=0=Phase1, >0=Phase2)
 *       bullet.data = FractureData (存储目标、起点、collided集合)
 *
 * ★ 防崩溃: 所有关键逻辑用 try-catch 包裹, 出错时只跳过不崩溃
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

    public VoidFractureBulletType(float speed, float damage) {
        super(4.3f, damage);
        drag = 0.11f;
        trueSpeed = speed;
        collides = false;
        collidesTiles = false;
        keepVelocity = false;
        pierce = true;
        despawnEffect = Fx.none;
        smokeEffect = Fx.none;
        hitEffect = Fx.hitLancer;
        lifetime = 60f;
    }

    @Override
    public void init() {
        super.init();
        // v158: range 是字段, 直接设置 (PU132 原版 range() = trueSpeed * nextLifetime)
        range = trueSpeed * nextLifetime;
        drawSize = (range * 2f) + 30f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data(new FractureData());
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        if (!(b.data() instanceof FractureData data)) return;

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
                // 到时间进入 Phase 2
                if (b.time() >= delay) {
                    b.time(0f);
                    b.lifetime(nextLifetime);
                    b.fdata(1f);
                    b.drag(0f);
                    b.vel().trns(b.rotation(), trueSpeed);
                    data.x = b.x();
                    data.y = b.y();
                }
            } else {
                // ===== Phase 2: 冲刺穿透 (PU132 L101-122) =====
                collideLineRawEnemySimple(b, b.lastX(), b.lastY(), b.x(), b.y(), data);
            }
        } catch (Throwable t) {
            // 防崩溃: 出错时跳过本帧 update
            System.out.println("[VoidFracture] update error: " + t);
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

    @Override
    public void removed(Bullet b) {
        super.removed(b);
        // ===== Phase 2 结束: 生成 spikes (PU132 L141-186) =====
        try {
            if (b.fdata() >= 1f && b.data() instanceof FractureData data) {
                spawnSpikes(b, data);
            }
        } catch (Throwable t) {
            System.out.println("[VoidFracture] removed error: " + t);
        }
    }

    /**
     * 沿冲刺路径生成 spikes 伤害 (PU132 L153-184)
     */
    protected void spawnSpikes(Bullet b, FractureData data) {
        if (b.hit()) return;
        int[] count = {0};
        // 线段中心点和半径
        float cx = (data.x + b.x()) * 0.5f;
        float cy = (data.y + b.y()) * 0.5f;
        float segLen = Mathf.len(b.x() - data.x, b.y() - data.y);
        float radius = segLen * 0.5f + spikesRange;
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
        });
    }

    @Override
    public void draw(Bullet b) {
        if (!(b.data() instanceof FractureData data)) return;
        Draw.color(Color.black);
        if (b.fdata() <= 0f) {
            // ===== Phase 1: 小三角 (PU132 L192-195) =====
            float in = Mathf.clamp(b.time() / delay);
            Drawf.tri(b.x(), b.y(), width * in, length, b.rotation());
            Drawf.tri(b.x(), b.y(), width * in, length, b.rotation() + 180f);
        } else {
            // ===== Phase 2: 激光束 (PU132 L196-211, 类似压迫者大招激光) =====
            Drawf.tri(b.x(), b.y(), width * b.fout(), length, b.rotation());
            Drawf.tri(b.x(), b.y(), width * b.fout(), length / 2f, b.rotation() + 180f);
            float ang = b.dst2(data.x, data.y) >= 0.0001f ? b.angleTo(data.x, data.y) : b.rotation() + 180f;
            // 三层叠加的激光束 (类似压迫者大招的 3 层颜色)
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
        Draw.reset();
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
}
