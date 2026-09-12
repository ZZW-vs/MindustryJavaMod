package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import zzw.content.graphics.UnityPal;

/**
 * 连续奇异点激光 (移植自 PU132 ContinuousSingularityLaserBulletType 完整版,
 * thalassophobia 主炮武器)
 *
 * <p>机制 (对照 PU132 原版):</p>
 * <p>1. 激光加速延伸: 每 tick 速度 velocity 随 velocityTime/accel 增长 (上限 laserSpeed),
 *     命中建筑/单位后 velocity 归零、restartTime 重新计时, 激光缩短到命中点 + pierceAmount,
 *     5 tick 内用 fastUpdateLength 快速逼近目标 (激光"咬住"目标的视觉效果);
 * <br>2. 每 5 tick 碰撞检测: 建筑健康 &gt; damage/100 或吸激光方块会阻挡光束;
 *     大型单位 (hitSize &gt; width×3 且血量 &gt; damage) 同样阻挡;
 *     所有沿线敌人受到防作弊伤害 (比例/超量/流血);
 * <br>3. 引力场: 光束两侧 gravityRange 范围内的单位被拉向光束
 *     (force = gravityStrength×距离衰减, 通过 impulse 拉扯, 小单位直接被吸进光束);
 * <br>4. 绘制: 5 层颜色 (红→黑) 嵌套光束, 每层带 oscScl 呼吸宽度与 laserInstability 抖动,
 *     根部圆球 + 根部三角翼 + 末端三角。</p>
 *
 * <p>碰撞检测说明: PU132 原版用 Utils.collideLineRawEnemyRatio (基于 tile raycast 的
 * 精确线扫描), 本移植沿用模组既有方案 (indexer.eachBlock + Geometry.raycastRect +
 * Units.nearbyEnemies, 见 EndCutterLaserBulletType), 并复刻 ratio 距离衰减公式,
 * 观感与原版一致。</p>
 */
public class ContinuousSingularityLaserBulletType extends AntiCheatBulletTypeBase {
    public float maxLength = 1000f;
    public float laserSpeed = 15f;
    public float accel = 25f;
    public float width = 12f, widthReduction = 2f, collisionWidth = 8f;
    /** 引力场半径/强度 (thalassophobia: 260/20×80) */
    public float gravityRange = 260f, gravityStrength = 20f;
    public float fadeTime = 60f;
    public float fadeInTime = 8f;
    /** 各层宽度呼吸: 相位偏移 oscOffset / 周期 oscScl */
    public float oscOffset = 1.4f, oscScl = 1.1f;
    /** 激光抖动幅度 (像素) */
    public float laserInstability = 1f;
    /** 命中后光束末端超出命中点的距离 (激光"包住"目标) */
    public float pierceAmount = 4f;
    /** 根部圆球尺寸倍率 / 根部三角长度 */
    public float baseScl = 3f, baseTriangleSize = 60f;
    /** 命中后 5 tick 内快速逼近目标 (true = PU132 原版行为) */
    public boolean fastUpdateLength = true;
    /** 穿透计分上限 (0 = 不穿透, 光束碰到大型目标即停) */
    public float pierceCap = 0f;
    public Color[] colors = {
        UnityPal.scarColor.cpy().a(0.4f), UnityPal.scarColor, UnityPal.endColor,
        Color.white, Color.black
    };

    public ContinuousSingularityLaserBulletType(float damage) {
        super(0f, damage);
        despawnEffect = Fx.none;
        collides = false;
        pierce = true;
        impact = true;
        keepVelocity = false;
        hittable = false;
        absorbable = false;
        // 黑色激光画在单位图层之上 (模组既有约定, 防止被单位身体遮挡)
        layer = Layer.flyingUnit + 0.5f;
    }

    @Override
    public void init() {
        super.init();
        drawSize = maxLength * 2f;
        despawnHit = false;
    }

    @Override
    public float estimateDPS() {
        return damage * (lifetime / 2f) / 5f * 3f;
    }

    @Override
    public float continuousDamage() {
        return damage / 5f * 60f;
    }

    /**
     * 射程 (PU132 range(): maxRange 与 maxLength/1.5 取值)。
     * 注: v155.4 BulletType 无 range() 方法 (protected calculateRange), 此为自定义方法。
     */
    public float range() {
        return maxRange > 0 ? maxRange : maxLength / 1.5f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = new VoidLaserData();
    }

    /**
     * 激光延伸 + 碰撞 + 引力场更新 (PU132 update 逐步移植)。
     */
    @Override
    public void update(Bullet b) {
        boolean timer = b.timer(0, 5f);

        if (b.data instanceof VoidLaserData) {
            VoidLaserData data = (VoidLaserData)b.data;

            if (data.restartTime >= 5f) {
                // ===== 自由延伸阶段: 速度随时间累积 (加速启动) =====
                if (accel > 0.01f) {
                    data.velocity = Mathf.clamp((data.velocityTime / accel) + data.velocity, 0f, laserSpeed);
                    b.fdata = Mathf.clamp(b.fdata + (data.velocity * Time.delta), 0f, maxLength);
                    data.velocityTime += Time.delta;
                } else if (timer) {
                    // 无加速参数时直接满长度
                    b.fdata = maxLength;
                }
            } else {
                // ===== 命中重启阶段: restartTime 计时, 快速逼近被咬住的目标 =====
                data.restartTime += Time.delta;
                if (fastUpdateLength && data.target != null) {
                    data.pierceOffsetSmooth = Mathf.lerpDelta(data.pierceOffsetSmooth, data.pierceOffset, 0.2f);
                    Tmp.v2.trns(b.rotation(), maxLength * 1.5f).add(b);
                    float dst = Intersector.distanceLinePoint(b.x, b.y, Tmp.v2.x, Tmp.v2.y, data.target.getX(), data.target.getY());
                    b.fdata = ((b.dst(data.target) - data.targetSize) + dst) + pierceAmount + (data.pierceOffsetSmooth * data.targetSize);
                }
            }

            if (timer) {
                checkCollision(b, data);
            }

            // ===== 引力场: 拉扯光束两侧 gravityRange 内的敌人 =====
            Tmp.v1.trns(b.rotation(), b.fdata).add(b);
            if (timer) {
                data.units.clear();
                float ex = Tmp.v1.x, ey = Tmp.v1.y;
                float minX = Math.min(b.x, ex) - gravityRange, minY = Math.min(b.y, ey) - gravityRange;
                float sizeX = Math.abs(ex - b.x) + gravityRange * 2f, sizeY = Math.abs(ey - b.y) + gravityRange * 2f;
                Groups.unit.intersect(minX, minY, sizeX, sizeY, unit -> {
                    if (unit.team != b.team && unit.isValid()) {
                        if (Intersector.distanceSegmentPoint(b.x, b.y, ex, ey, unit.x, unit.y) <= gravityRange + (unit.hitSize / 2f)) {
                            data.units.add(unit);
                        }
                    }
                });
            }

            for (Unit u : data.units) {
                if (u.isAdded()) {
                    // 拉向光束上离自己最近的点: 距离越近力越大
                    Vec2 p = Intersector.nearestSegmentPoint(b.x, b.y, Tmp.v1.x, Tmp.v1.y, u.x, u.y, Tmp.v2);
                    float force = (1f - Mathf.clamp((p.dst(u) - (u.hitSize / 2f)) / gravityRange)) * gravityStrength;
                    Vec2 m = Tmp.v3.set(p).sub(u).setLength2(Math.min(p.dst2(u) * u.mass() * u.mass(), force * force));
                    if (m.isNaN()) m.setZero();

                    u.impulse(m);
                }
            }
        }
    }

    /**
     * 沿光束线段碰撞检测 (PU132 Utils.collideLineRawEnemyRatio + update lambda 移植)。
     *
     * <p>逐步解释:</p>
     * <p>1. 建筑: 健康 &gt; damage/100 或吸激光方块 → 阻挡 (光束缩短到命中点 + pierceAmount,
     *    velocity/restartTime 重置); 所有直接命中的建筑都受防作弊伤害;
     * <br>2. 单位: hitSize &gt; width×3 且血量 &gt; damage → 阻挡, 同样缩短光束;
     *    沿线所有敌人受防作弊伤害 (距离光束越近 ratio 越接近 1);
     * <br>3. 命中点触发 hitEffect。</p>
     */
    private void checkCollision(Bullet b, VoidLaserData data) {
        boolean p = pierceCap > 0;
        float ex = Tmp.v1.trns(b.rotation(), b.fdata).add(b.x, b.y).x;
        float ey = Tmp.v1.y;
        boolean[] stopped = {false};

        // ===== 建筑: 遍历光束范围内敌方建筑, raycastRect 找最近阻挡者 =====
        Vars.indexer.eachBlock(null, b.x, b.y, b.fdata + 32f,
            build -> build.team != b.team && build.health > 0,
            build -> {
                if (stopped[0]) return;
                boolean h = (build.health > damage / 100f) || build.block.absorbLasers;

                Tmp.r1.setCentered(build.x, build.y, build.block.size * Vars.tilesize);
                Vec2 hv = arc.math.geom.Geometry.raycastRect(b.x, b.y, ex, ey, Tmp.r1);
                if (hv == null) return;

                if (h) {
                    if (p) data.pierceScore += build.block.size * (build.block.absorbLasers ? 10f : 1f) * 1f;
                    if (!p || data.pierceScore >= pierceCap) {
                        Tmp.v2.trns(b.rotation(), maxLength * 1.5f).add(b);
                        float dst = Intersector.distanceLinePoint(b.x, b.y, Tmp.v2.x, Tmp.v2.y, build.x, build.y);
                        data.velocity = 0f;
                        data.restartTime = 0f;
                        data.velocityTime = 0f;
                        data.pierceOffset = 1f - Mathf.clamp(data.pierceScore - pierceCap);
                        if (fastUpdateLength) {
                            if (build != data.target) data.pierceOffsetSmooth = data.pierceOffset;
                            data.target = build;
                            data.targetSize = build.block.size * Vars.tilesize / 2f;
                        }
                        b.fdata = ((b.dst(build) - (build.block.size * Vars.tilesize / 2f)) + dst) + pierceAmount + (data.pierceOffsetSmooth * data.targetSize);
                        stopped[0] = true;
                    }
                }
                hitBuildingAntiCheat(b, build);
            });

        if (stopped[0]) {
            hitEffect.at(ex + Mathf.range(4f), ey + Mathf.range(4f), b.rotation() + 180f);
            return;
        }

        // ===== 单位: 沿光束线段查找敌人 (距离光束越近 ratio 越大) =====
        Tmp.v3.set((b.x + ex) / 2f, (b.y + ey) / 2f);
        float radius = Mathf.dst(b.x, b.y, ex, ey) / 2f + 32f;
        Seq<Unit> units = new Seq<>();
        mindustry.entities.Units.nearbyEnemies(b.team, Tmp.v3.x, Tmp.v3.y, radius, unit -> {
            if (!unit.isValid()) return;
            // 仅光束走廊 (collisionWidth) 命中的单位受伤
            float segDst = Intersector.distanceSegmentPoint(b.x, b.y, ex, ey, unit.x, unit.y);
            if (segDst > collisionWidth + unit.hitSize / 2f) return;
            // ratio 距离衰减 (PU132 collideLineRawEnemyRatio): 距离越近 ratio 越接近 1, 最低 0.05
            float size = unit.hitSize / 2f;
            float ratio = Mathf.clamp(1f - ((segDst - collisionWidth) / size), 0.05f, 1f);
            units.add(unit);

            boolean h = unit.hitSize > width * 3f && unit.health > damage;
            if (h && !stopped[0]) {
                if (p) data.pierceScore += (((unit.hitSize / Vars.tilesize) / 2f) + (unit.health / 4000f)) * ratio;
                if (!p || data.pierceScore >= pierceCap) {
                    Tmp.v2.trns(b.rotation(), maxLength * 1.5f).add(b);
                    float dst = Intersector.distanceLinePoint(b.x, b.y, Tmp.v2.x, Tmp.v2.y, unit.x, unit.y);
                    data.velocity = 0f;
                    data.restartTime = 0f;
                    data.velocityTime = 0f;
                    data.pierceOffset = 1f - Mathf.clamp(data.pierceScore - pierceCap);
                    if (fastUpdateLength) {
                        if (unit != data.target) data.pierceOffsetSmooth = data.pierceOffset;
                        data.target = unit;
                        data.targetSize = unit.hitSize / 2f;
                    }
                    b.fdata = ((b.dst(unit) - (unit.hitSize / 2f)) + dst) + pierceAmount + (data.pierceOffsetSmooth * data.targetSize);
                    stopped[0] = true;
                }
            }
        });

        for (Unit unit : units) {
            hitUnitAntiCheat(b, unit);
        }

        if (stopped[0]) {
            hitEffect.at(ex + Mathf.range(4f), ey + Mathf.range(4f), b.rotation() + 180f);
        }
    }

    /**
     * 5 层嵌套光束绘制 (PU132 draw)。
     *
     * <p>每层: 根部圆球 (baseScl 放大) → 直线束 → 根部三角翼 (±90°) →
     * 末端三角; 宽度带 oscScl 呼吸 + fade 渐入渐出。</p>
     */
    @Override
    public void draw(Bullet b) {
        float fadeIn = fadeInTime <= 0f ? 1f : Mathf.clamp(b.time / fadeInTime);
        float fade = Mathf.clamp(b.time > b.lifetime - fadeTime ? 1f - (b.time - (b.lifetime - fadeTime)) / fadeTime : 1f) * fadeIn;
        float tipHeight = width / 2f;

        Lines.lineAngle(b.x, b.y, b.rotation(), b.fdata);
        for (int i = 0; i < colors.length; i++) {
            float rx = Mathf.range(laserInstability),
            ry = Mathf.range(laserInstability);
            float f = 1f - ((widthReduction * i) / width);
            float w = f * (width + Mathf.absin(Time.time + (i * oscOffset), oscScl, width / 8)) * fade;

            Tmp.v2.trns(b.rotation(), b.fdata - tipHeight).add(b);
            Tmp.v1.trns(b.rotation(), width * 2f).add(Tmp.v2);
            Draw.color(colors[i]);
            Fill.circle(b.x + rx, b.y + ry, (w / 2f) * baseScl);
            Lines.stroke(w);
            Lines.line(b.x + rx, b.y + ry, Tmp.v2.x + rx, Tmp.v2.y + ry, false);
            for (int s : Mathf.signs) {
                Drawf.tri(b.x + rx, b.y + ry, w, baseTriangleSize + w, b.rotation() + 90f * s);

                Tmp.v3.trns(b.rotation(), w * -0.7f, w * s);
                Fill.tri(Tmp.v2.x + rx, Tmp.v2.y + ry,
                Tmp.v1.x + rx, Tmp.v1.y + ry,
                Tmp.v2.x + Tmp.v3.x + rx, Tmp.v2.y + Tmp.v3.y + ry);
            }
        }
        Tmp.v2.trns(b.rotation(), b.fdata + tipHeight).add(b);
        // v155.4 无 light(Team,x,y,x2,y2,radius,...) 8参重载, 用 7 参线段光照等价替代
        Drawf.light(b.x, b.y, Tmp.v2.x, Tmp.v2.y, width * 2f, colors[0], 0.5f);
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b) {
        // 光照已在 draw() 内手绘 (PU132 原版为空实现)
    }

    /**
     * 奇异点激光数据 (PU132 VoidLaserData 扩展 LaserData):
     * target/targetSize/pierceOffset 用于命中后快速逼近目标,
     * units 为当前被引力场捕获的单位列表。
     */
    static class VoidLaserData {
        public float lastLength, velocity, velocityTime, targetSize, pierceOffset, pierceOffsetSmooth, pierceScore, restartTime = 5f;
        public Position target;
        public Seq<Unit> units = new Seq<>();
    }
}
