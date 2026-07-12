package zzw.content.units;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.IntIntMap;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.pooling.Pool;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;

/**
 * PU132 VoidPortalBulletType 虚空门户子弹移植版 (完整移植)
 * - 菱形区域伤害 (两个三角形拼接, 由 bullet->mid 和 mid->end 组成)
 * - 虚空触手机制: 随机生成触手吸附拉拽敌人, 距离过远造成持续伤害
 * - 触手 16 段折线渲染成螺旋弯曲形态
 * - 防作弊伤害 (继承 AntiCheatBulletTypeBase)
 * - 自定义 Blending (shadowRealm: srcAlphaSaturate, oneMinusSrcAlpha)
 * 参考: PU132 main/src/unity/entities/bullet/anticheat/VoidPortalBulletType.java
 * 简化: SpecialFx.fragmentationFast 用原版死亡爆炸替代 (仅影响视觉, 不影响机制)
 */
public class VoidPortalBulletType extends AntiCheatBulletTypeBase {
    public float length = 800f;
    public float width = 95f;
    public float fadeInTime = 180f, fadeOutTime = 20f;
    public float tentacleRange = 500f, tentacleWidth = 7f, tentaclePull = 3f;
    public float tentacleOutOfRangeStrength = 1f / 80f;
    public float tentacleRangeReduction = 4f;
    public float tentacleDamage = 50f;

    /** shadowRealm 混合模式 (srcAlphaSaturate, oneMinusSrcAlpha) */
    public static final Blending shadowRealm = new Blending(Gl.srcAlphaSaturate, Gl.oneMinusSrcAlpha);

    private static final IntSet collided = new IntSet(102);
    private static final Pool<VoidTentacle> tentaclePool = new Pool<>(8, 250) {
        @Override
        protected VoidTentacle newObject() {
            return new VoidTentacle();
        }
    };
    private static final Rect rect = new Rect(), rectAlt = new Rect();

    public VoidPortalBulletType(float damage) {
        super(0f, damage);
        lifetime = 4f * 60f;
        collides = false;
        hittable = false;
        absorbable = false;
        keepVelocity = false;
        pierce = true;
        pierceShields = true;
        despawnEffect = mindustry.content.Fx.none;
        countsAsSkill = true;
        maxActive = 10;  // 菱形(虚空门户)最多10个
    }

    @Override
    public void init() {
        super.init();
        drawSize = length * 2f;
    }

    @Override
    public float estimateDPS() {
        return damage * (lifetime / 2f) / 5f * 3f;
    }

    @Override
    public float continuousDamage() {
        return damage / 5f * 60f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = new VoidPortalData();

        // ★ 优先在敌方密集处生成菱形
        Vec2 densePos = DensityCalculator.findDensePosition(b.x, b.y, b.rotation(), length, b.team);
        if (densePos != null) {
            float dist = b.dst(densePos);
            if (dist > 0 && dist < length) {
                float angle = b.angleTo(densePos);
                b.rotation(angle);
            }
        }
    }

    @Override
    public void update(Bullet b) {
        if (!checkSkillLimit(b)) return;
        float fout = Mathf.clamp(b.time > b.lifetime - fadeOutTime ? 1f - (b.time - (lifetime - fadeOutTime)) / fadeOutTime : 1f);
        float fin = b.time < fadeInTime ? Mathf.clamp(b.time / fadeInTime) : 1f;
        float fin2 = Mathf.curve(b.fin(), 0f, 15f / lifetime);

        Vec2 end = Tmp.v1.trns(b.rotation(), length * fin2).add(b);
        Vec2 mid = Tmp.v2.set(end).sub(b).scl(1f / 2f).add(b);
        Vec2 s = Tmp.v3.trns(b.rotation() - 90f, width * fin * fout);

        Effect.shake(5f * fin, 5f * fin, mid);

        if (b.timer(0, 5f)) {
            float ex = end.x, ey = end.y, mx = mid.x, my = mid.y, sx = s.x, sy = s.y;
            collided.clear();
            int[] hitCount = {0}; // 限制每次更新产生的特效数量

            // 三角形1: b -> mid+s -> mid-s (建筑)
            hitCount[0] += inTriangleBuildingEnemy(b.team, b.x, b.y, mx + sx, my + sy, mx - sx, my - sy, building -> collided.add(building.id), building -> {
                hitBuildingAntiCheat(b, building);
                if (hitCount[0] < 5) hit(b, building.x, building.y);
            });
            // 三角形2: mid+s -> mid-s -> end (建筑)
            hitCount[0] += inTriangleBuildingEnemy(b.team, mx + sx, my + sy, mx - sx, my - sy, ex, ey, building -> collided.add(building.id), building -> {
                hitBuildingAntiCheat(b, building);
                if (hitCount[0] < 5) hit(b, building.x, building.y);
            });

            // 三角形1: b -> mid+s -> mid-s (单位)
            inTriangleUnit(b.team, b.x, b.y, mx + sx, my + sy, mx - sx, my - sy, u -> u.team != b.team && collided.add(u.id), u -> {
                hit(b, u.x, u.y);
                hitUnitAntiCheat(b, u);
            });
            // 三角形2: mid+s -> mid-s -> end (单位)
            inTriangleUnit(b.team, mx + sx, my + sy, mx - sx, my - sy, ex, ey, u -> u.team != b.team && collided.add(u.id), u -> {
                hit(b, u.x, u.y);
                hitUnitAntiCheat(b, u);
            });
        }

        // 触手生成与更新
        if (b.data instanceof VoidPortalData data) {
            if (Mathf.chanceDelta(0.2f)) {
                Tmp.v1.set(b).sub(mid);
                float l = Mathf.range(1f);
                float o = Mathf.range(1f) * (1f - Math.abs(l));
                float x = (Tmp.v1.x * l) + mid.x + (s.x * o);
                float y = (Tmp.v1.y * l) + mid.y + (s.y * o);
                Unit unit = Units.bestEnemy(b.team, x, y, tentacleRange, Healthc::isValid, (u, sx, sy) ->
                        data.map.get(u.id, 0) + (b.dst(u) / (tentacleRange * 2f)));
                if (unit != null) {
                    VoidTentacle t = tentaclePool.obtain();
                    t.set(unit, x, y, tentacleRange);
                    data.tentacles.add(t);
                    data.map.put(unit.id, data.map.get(unit.id, 0) + 1);
                }
            }
            data.tentacles.removeAll(t -> t.update(b, this, mid.x, mid.y));
        }
    }

    @Override
    public void draw(Bullet b) {
        float fout = Mathf.clamp(b.time > b.lifetime - fadeOutTime ? 1f - (b.time - (b.lifetime - fadeOutTime)) / fadeOutTime : 1f);
        float fin = b.time < fadeInTime ? Mathf.clamp(b.time / fadeInTime) : 1f;
        float fin2 = Mathf.curve(b.fin(), 0f, 15f / lifetime);

        Vec2 end = Tmp.v1.trns(b.rotation(), length * fin2).add(b);
        Vec2 mid = Tmp.v2.set(end).sub(b).scl(1f / 2f).add(b);
        Vec2 s = Tmp.v3.trns(b.rotation() - 90f, width * fin * fout);

        float z = Draw.z();
        // 第1层: 黑色三角形门户 (shadowRealm 混合模式)
        // ★ 渲染在飞行单位上方, 确保黑色菱形/圆形盖住空中单位
        Draw.z(Layer.flyingUnit + 1f);
        Draw.color(Color.valueOf("f53036"));
        Draw.blend(shadowRealm);
        Fill.tri(b.x, b.y, mid.x + s.x, mid.y + s.y, mid.x - s.x, mid.y - s.y);
        Fill.tri(end.x, end.y, mid.x + s.x, mid.y + s.y, mid.x - s.x, mid.y - s.y);
        Draw.blend();

        // 第2层: 触手渲染 (同样在最上层)
        Draw.z(Layer.flyingUnit + 1f);
        if (b.data instanceof VoidPortalData data) {
            Draw.color(Color.black);
            for (VoidTentacle t : data.tentacles) {
                t.draw(tentacleWidth, fout);
            }
        }
        Draw.z(z);
        Draw.color();
    }

    @Override
    public void drawLight(Bullet b) {
        // 光源在 draw() 中处理
    }

    @Override
    public void removed(Bullet b) {
        super.removed(b);
        if (b.data instanceof VoidPortalData data) {
            for (VoidTentacle tentacle : data.tentacles) {
                tentaclePool.free(tentacle);
            }
            data.tentacles.clear();
        }
    }

    // ====== 三角形碰撞检测工具方法 (移植自 PU132 Utils.inTriangle/inTriangleBuilding) ======

    /** 点是否在三角形内 (含半径扩展, 用于碰撞箱检测) */
    private static boolean inTriangleCircle(float x1, float y1, float x2, float y2, float x3, float y3, float cx, float cy, float radius) {
        if (Intersector.isInTriangle(cx, cy, x1, y1, x2, y2, x3, y3)) return true;
        if (radius <= 0f) return false;
        if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, cx, cy) <= radius) return true;
        if (Intersector.distanceSegmentPoint(x2, y2, x3, y3, cx, cy) <= radius) return true;
        return Intersector.distanceSegmentPoint(x3, y3, x1, y1, cx, cy) <= radius;
    }

    /** 矩形与三角形碰撞检测 (用于大型建筑) */
    private static boolean inTriangleRect(float x1, float y1, float x2, float y2, float x3, float y3, Rect rectBox) {
        float cx = rectBox.x + (rectBox.width / 2f), cy = rectBox.y + (rectBox.height / 2f);
        if (Intersector.isInTriangle(cx, cy, x1, y1, x2, y2, x3, y3)) return true;
        if (rectBox.width <= 0f && rectBox.height <= 0f) return false;
        if (rectBox.contains(x1, y1) || rectBox.contains(x2, y2) || rectBox.contains(x3, y3)) return true;
        // 用线段-矩形相交检测 (三条边)
        if (Intersector.intersectSegmentRectangle(x1, y1, x2, y2, rectBox)) return true;
        if (Intersector.intersectSegmentRectangle(x2, y2, x3, y3, rectBox)) return true;
        return Intersector.intersectSegmentRectangle(x3, y3, x1, y1, rectBox);
    }

    /** 三角形内敌方单位扫描 (替代 PU132 Utils.inTriangle) */
    private static void inTriangleUnit(Team team, float x1, float y1, float x2, float y2, float x3, float y3, arc.func.Boolf<Unit> filter, arc.func.Cons<Unit> cons) {
        Rect r = rect.setCentered(x1, y1, 0f);
        r.merge(x2, y2);
        r.merge(x3, y3);
        // 用 Units.nearby 替代 Groups.unit.intersect (更稳定的 API)
        Units.nearby(r, u -> {
            if (filter.get(u) && inTriangleCircle(x1, y1, x2, y2, x3, y3, u.x, u.y, u.hitSize / 2f)) {
                cons.get(u);
            }
        });
    }

    /**
     * 三角形内敌方建筑扫描 (替代 PU132 Utils.inTriangleBuilding)
     * ★ 性能优化: 使用 indexer.eachBlock 只检查区域内的建筑
     * @return 命中建筑数量
     */
    private static int inTriangleBuildingEnemy(Team team, float x1, float y1, float x2, float y2, float x3, float y3, arc.func.Boolf<Building> filter, arc.func.Cons<Building> cons) {
        Rect r = rect.setCentered(x1, y1, 0f);
        r.merge(x2, y2);
        r.merge(x3, y3);
        float cx = r.x + r.width / 2f, cy = r.y + r.height / 2f;
        float range = Math.max(r.width, r.height) / 2f + 8f;
        int[] count = {0};
        // ★ 使用 indexer.eachBlock 按区域查询, 比遍历所有建筑快得多
        Vars.indexer.eachBlock(null, cx, cy, range,
            build -> build.team != team && filter.get(build),
            build -> {
                build.hitbox(rectAlt);
                int sz = build.block.size;
                boolean hit = sz > 3 ? inTriangleRect(x1, y1, x2, y2, x3, y3, rectAlt) : inTriangleCircle(x1, y1, x2, y2, x3, y3, build.x, build.y, sz * Vars.tilesize / 2f);
                if (hit) {
                    cons.get(build);
                    count[0]++;
                }
            });
        return count[0];
    }

    // ====== 数据类和触手类 ======

    static class VoidPortalData {
        Seq<VoidTentacle> tentacles = new Seq<>();
        IntIntMap map = new IntIntMap(102);
    }

    static class VoidTentacle implements Pool.Poolable { // NOPMD
        float x, y, randLen, randAng, length, time, timer;
        Unit unit;
        boolean side = Mathf.randomBoolean();

        void set(Unit unit, float x, float y, float length) {
            this.unit = unit;
            this.x = x;
            this.y = y;
            this.length = length;
            randAng = Mathf.range(360f);
            randLen = Mathf.random(unit.hitSize / 3f);
            side = Mathf.randomBoolean();
        }

        @Override
        public void reset() {
            unit = null;
            x = y = randLen = randAng = length = time = timer = 0f;
        }

        boolean update(Bullet b, VoidPortalBulletType type, float cx, float cy) {
            if (unit.isValid()) {
                time = Math.min(20f, time + Time.delta);
                timer += Time.delta;
                float tx = unit.x + Angles.trnsx(unit.rotation + randAng, randLen);
                float ty = unit.y + Angles.trnsy(unit.rotation + randAng, randLen);

                Tmp.v1.set(x, y).sub(tx, ty).nor();
                float mx = Tmp.v1.x, my = Tmp.v1.y;
                float scl = type.tentaclePull;

                float dst = Mathf.dst(x, y, tx, ty);

                if (dst > length) {
                    float s = (dst - length) * type.tentacleOutOfRangeStrength;
                    if (timer >= 5f) {
                        boolean wasDead = unit.dead;
                        // 触手超距伤害: 负基础伤害 + 按超距倍率的触手伤害
                        type.hitUnitAntiCheat(b, unit, -b.damage + (type.tentacleDamage * s));
                        if (unit.dead && !wasDead) {
                            if (unit.isAdded()) {
                                unit.destroy();
                            }
                            // PU132: 用 SpecialFx.fragmentationFast (自定义 shader 特效)
                            // 简化: 用原版死亡爆炸特效 (仅影响视觉, 不影响机制)
                            if (Vars.renderer.animateShields) {
                                unit.type.deathExplosionEffect.at(unit.x, unit.y, unit.bounds() / 2f / 8f);
                                unit.type.deathSound.at(unit);
                            }
                        }
                        timer = 0f;
                    }
                    scl += s;
                } else {
                    // 目标在范围内时, 逐步缩小触手长度 (让目标更难逃脱)
                    length = Math.max(dst, length - ((length - dst) * type.tentacleOutOfRangeStrength * Time.delta));
                }
                length = Math.max(0f, length - (type.tentacleRangeReduction * Time.delta));
                scl *= 20f;
                unit.impulse(mx * scl * Time.delta, my * scl * Time.delta);
            } else {
                time -= Time.delta;
            }
            return time < 0f && !unit.isValid();
        }

        void draw(float width, float fout) {
            if (unit == null) return;
            float fin = (time / 20f) * fout;

            float tx = unit.x + Angles.trnsx(unit.rotation + randAng, randLen);
            float ty = unit.y + Angles.trnsy(unit.rotation + randAng, randLen);

            int res = 16;
            float dst = (Mathf.dst(x, y, tx, ty) / res) * Mathf.clamp(time / 13f);
            float angle = Angles.angle(x, y, tx, ty);

            Tmp.v1.set(x, y);
            float w = width * fin;
            for (int i = 0; i < res; i++) {
                float bend = (1f - fin) * (360f / res) * Mathf.sign(side) * i;
                float lx = Tmp.v1.x;
                float ly = Tmp.v1.y;
                float w2 = w - ((w * (i / (float) res)) / 1.5f);
                Vec2 v = Tmp.v1.add(Tmp.v2.trns(bend + angle, dst));

                if (i == 0) Fill.circle(lx, ly, w2 / 2f);
                Lines.stroke(w2);
                Lines.line(lx, ly, v.x, v.y, false);
                Fill.circle(v.x, v.y, w2 / 2f);
            }
        }
    }
}
