package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;
import arc.math.Rand;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

import static zzw.content.units.UnityDrawf.diamond;

/**
 * PU132 OppressionLaserBulletType 移植版 (简化)
 * - 7层渲染: 纺锤主体 + 末端虚空 + 尖刺边缘 + 散落粒子 + 白色闪光线 + 黑红菱形 + 内部线段 + 闪电
 * - 锥形收口 (cone): 前380距离内宽度从0圆形增长
 * - 防作弊伤害 (继承 AntiCheatBulletTypeBase)
 * 参考: PU132 main/src/unity/entities/bullet/anticheat/OppressionLaserBulletType.java
 */
public class OppressionLaserBulletType extends AntiCheatBulletTypeBase {
    private static final int detail = 24;
    private static final float timeMul = 4f;
    public static final float[] shape = new float[4 * detail], quad = new float[8];
    private static final float[] ltmp = new float[25], ltmp2 = new float[25];
    private static final Rand rand = new Rand(), rand2 = new Rand();
    private static final FloatSeq lines = new FloatSeq();
    private static final Color[] lightningColors = {Color.white, Color.valueOf("f53036"), Color.black};

    protected float length = 2150f, width = 140f, cone = 380f, endLength = 450f;
    protected float fadeInTime = 15f;

    // PU132 颜色常量
    private static final Color SCAR_COLOR = Color.valueOf("f53036");
    private static final Color END_COLOR = Color.valueOf("ff786e");
    private static final Color SCAR_COLOR_ALPHA = Color.valueOf("f5303690");

    static {
        // 初始化 shape 数组: 24个梯形, 用 circleIn/circleOut 生成纺锤形宽度曲线
        int sign = 1;
        for (int i = 0; i < detail; i++) {
            int id = i * 4;
            float de = detail - 1f;
            float f = Interp.circleIn.apply(i / de),
                  w = Interp.circleOut.apply(f);
            for (int s : Mathf.signs) {
                shape[id] = w * s * sign;
                shape[id + 1] = f;
                id += 2;
            }
            sign *= -1f;
        }
    }

    public OppressionLaserBulletType() {
        speed = 0f;
        damage = 9000f;
        buildingDamageMultiplier = 0.4f;
        despawnEffect = mindustry.content.Fx.none;
        // PU132: hitEffect = HitFx.endHitRedBig (红色大爆炸)
        // 用简化红色爆炸特效替代 (原版依赖 HitFx 自定义特效)
        hitEffect = HitEffect.endHitRedBig;
        hittable = collides = absorbable = keepVelocity = false;
        impact = true;
        pierce = true;
        lifetime = 8f * 60f;
        knockback = 9f;
        // ★ v154.3: chargeEffect 在 firstShotDelay 期间播放 (充能准备特效)
        // shootEffect 在子弹创建时播放 (由 init() 中 ShootEffect.oppressionShoot 替代)
        chargeEffect = ChargeEffect.oppressionCharge;
        shootEffect = mindustry.content.Fx.none;
        drawSize = 4200f;

        // PU132 防作弊参数 (OppressionLaserBulletType L33-57)
        ratioStart = 100000f;
        ratioDamage = 1f / 60f;
        overDamage = 650000f;
        overDamagePower = 2.7f;
        overDamageScl = 4000f;
        bleedDuration = 10f * 60f;
        pierceShields = true;

        // PU132 三模块组合
        modules = new AntiCheatBulletModule[]{
            new ArmorDamageModule(0.002f, 4f, 20f, 4f)
        };
    }

    @Override
    public void init() {
        super.init();
        drawSize = length * 2f;
        range = length / 3f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        // PU132: 在 init(Bullet b) 时播放 ShootFx.oppressionShoot 发射特效 (170tick, 纺锤闪光+75粒子)
        // 跟随父单位移动和旋转
        if (b.owner instanceof mindustry.gen.Rotc) {
            ShootEffect.oppressionShoot.at(b.x, b.y, ((mindustry.gen.Rotc) b.owner).rotation(), b.owner);
        }
    }

    @Override
    public void update(Bullet b) {
        if (b.timer(1, 5f)) {
            float fw = b.time < fadeInTime ? Interp.pow2Out.apply(b.time / fadeInTime) : 1f;
            float fow = Mathf.clamp((b.lifetime - b.time) / 120f);
            float w = this.width * fw * fow;

            Tmp.v1.trns(b.rotation(), length + endLength).add(b);
            float ex = Tmp.v1.x, ey = Tmp.v1.y;

            // ★ 对齐原版 Utils.collideLineLarge: nearestSegmentPoint + within + raycastRect
            Rect rect = Tmp.r1;
            rect.set(b.x, b.y, 0, 0).merge(ex, ey).grow(w * 2f + 100f);

            // 单位检测
            Units.nearbyEnemies(b.team, rect, unit -> {
                if (!unit.hittable() || !unit.checkTarget(collidesAir, collidesGround)) return;
                // nearestSegmentPoint 获取线段上离单位最近的点
                Vec2 nearest = arc.math.geom.Intersector.nearestSegmentPoint(b.x, b.y, ex, ey, unit.x, unit.y, Tmp.v2);
                float dst = b.dst(nearest);
                float cw = getWidthCollision(dst, w);
                if (cw > 0f && unit.within(nearest.x, nearest.y, cw + unit.hitSize / 2f)) {
                    // 二次精确检测: raycastRect
                    Tmp.r2.setCentered(unit.x, unit.y, unit.hitSize()).grow(w * 2f);
                    Vec2 hv = arc.math.geom.Geometry.raycastRect(b.x, b.y, ex, ey, Tmp.r2);
                    if (hv != null) {
                        hit(b, hv.x, hv.y);
                        hitUnitAntiCheat(b, unit);
                        if (b.owner instanceof mindustry.gen.Healthc h) {
                            h.heal(damage * 0.1f);
                        }
                    }
                }
            });

            // 建筑检测
            Vars.indexer.eachBlock(null, b.x, b.y, length + endLength,
                    build -> build.team != b.team && build.health > 0,
                    build -> {
                        Vec2 nearest = arc.math.geom.Intersector.nearestSegmentPoint(b.x, b.y, ex, ey, build.x, build.y, Tmp.v2);
                        float dst = b.dst(nearest);
                        float cw = getWidthCollision(dst, w);
                        if (cw > 0f && build.within(nearest.x, nearest.y, cw)) {
                            Tmp.r2.setCentered(build.x, build.y, build.block.size * Vars.tilesize).grow(w * 2f);
                            Vec2 hv = arc.math.geom.Geometry.raycastRect(b.x, b.y, ex, ey, Tmp.r2);
                            if (hv != null) {
                                hit(b, hv.x, hv.y);
                                hitBuildingAntiCheat(b, build);
                            }
                        }
                    });
        }
    }

    /** 锥形收口宽度: 前 cone 距离内从0圆形增长到满宽 */
    float getWidth(float length, float width) {
        return length >= cone ? width : Interp.circleOut.apply(length / cone) * width;
    }

    /** 碰撞宽度: length 后线性衰减到0 */
    float getWidthCollision(float length, float width) {
        return length < this.length ? getWidth(length, width) : Mathf.clamp(1f - (length - this.length) / endLength) * width;
    }

    @Override
    public void drawLight(Bullet b) {
        // 光源在 draw() 中处理
    }

    @Override
    public void draw(Bullet b) {
        // ★ 渲染在飞行单位之上 (用户要求子弹/激光显示在最上层)
        // PU132 原版用默认 Layer.bullet (100), 会被 flyingUnit (115) 覆盖
        // 改为 Layer.flyingUnit + 1f (116), 确保激光显示在单位上方
        float oldZ = Draw.z();
        Draw.z(Layer.flyingUnit + 1f);
        float wid = this.width * 0.125f;
        float fw = b.time < fadeInTime ? Interp.pow2Out.apply(b.time / fadeInTime) : 1f;
        float fow = Mathf.clamp((b.lifetime - b.time) / 120f);
        float inout = fw * fow;
        float width = this.width * inout + Mathf.absin(Time.time, 5f, 2f * inout);
        float sin = Mathf.absin(Time.time, 3f, 0.35f);
        Color col = Tmp.c1.set(SCAR_COLOR).mul(1f + sin);

        // ===== 层1: 纺锤主体 (24梯形) =====
        Draw.color(col);
        Draw.blend();
        for (int i = 0; i < shape.length; i += 4) {
            if (i < shape.length - 4) {
                for (int j = 0; j < quad.length; j += 2) {
                    Vec2 v = Tmp.v1.trns(b.rotation(), shape[i + j + 1] * cone, shape[i + j] * width).add(b);
                    quad[j] = v.x;
                    quad[j + 1] = v.y;
                }
                Fill.quad(quad[0], quad[1], quad[2], quad[3], quad[4], quad[5], quad[6], quad[7]);
            } else {
                // 末端线段 + 末端虚空
                Vec2 v = Tmp.v1.trns(b.rotation(), length + wid).add(b);
                Vec2 v2 = Tmp.v2.trns(b.rotation(), cone).add(b);
                Lines.stroke(width * 2f);
                Lines.line(v2.x, v2.y, v.x, v.y, false);
                drawEndVoid(b, v.x, v.y, width);
            }
        }

        // ===== 层2: 末端尖刺边缘 (两侧各14个) =====
        long seed = b.id * 9999L + 7813;
        for (int s : Mathf.signs) {
            float stroke = (2f + (sin / 0.35f)) * 1.5f * fow;
            Vec2 v = Tmp.v1.trns(b.rotation(), length + wid, width * s).add(b);
            drawEndEdge(b, seed, v.x, v.y, width * s, stroke);
            seed += rand.nextInt();
        }

        // ===== 层3: 散落粒子 (减到22个, 线段+方块) =====
        rand.setSeed(b.id * 9999L + 8957324);
        float time = Time.time;

        Lines.stroke(3f);
        for (int i = 0; i < 22; i++) {
            float d = rand.random(14f, 22f);
            float timeOffset = rand.random(d);
            int timeSeed = Mathf.floor((time + timeOffset) / d) + rand.nextInt();
            float fin = ((time + timeOffset) % d) / d;

            rand2.setSeed(timeSeed);
            float trns = rand2.random(90f, 270f) * Interp.pow2Out.apply(fin) * inout;
            float rot = (rand2.chance(0.5f) ? 1f : -1f) * rand2.random(50f, 85f) + b.rotation();
            Tmp.v1.trns(rot, trns).add(b);
            if (rand2.chance(0.75f)) {
                float l = rand2.random(10f, 35f) * (1f - fin) * inout;
                Lines.lineAngle(Tmp.v1.x, Tmp.v1.y, rot, l, false);
            } else {
                float scl = rand2.random(6f, 12f) * (1f - fin) * inout;
                Fill.square(Tmp.v1.x, Tmp.v1.y, scl, 45f);
            }
        }

        // ===== 层4: 白色闪光线段 (减到9个) =====
        for (int i = 0; i < 9; i++) {
            boolean alt = i < 4;
            float as = alt ? 1f : 3f;
            float wid2 = alt ? width : width / 1.5f;
            float d = alt ? rand.random(12f, 22f) : rand.random(22f, 60f);
            float timeOffset = rand.random(d);
            int timeSeed = Mathf.floor((time + timeOffset) / d) + rand.nextInt();
            float fin = ((time + timeOffset) % d) / d;

            rand2.setSeed(timeSeed);
            Draw.color(Color.white);

            float delay = rand2.random(0.55f, 0.8f);
            float pos1 = Mathf.pow(rand2.nextFloat(), 2f);
            float w = rand2.random(2f, 7f + (1f - pos1) * 3f) * as * Mathf.lerp(1f, rand2.random(0.8f, 1.2f), fin);
            float l = rand2.random(length / 2f);
            float pos2 = pos1 * Math.max(wid2 - (w * 2f), 0f) * Mathf.sign(rand2.chance(0.5f));

            float trns = ((length + endLength * (1f - pos1)) - l) * rand2.random(1f - (pos1 * 0.25f), 1.1f);
            float f1 = Mathf.curve(fin, 0f, 1f - delay), f2 = Mathf.curve(fin, delay, 1f);
            Lines.stroke(w * inout);
            drawLine(b, Interp.pow2In.apply(f1) * trns + l, Interp.pow2In.apply(f2) * trns + l, pos2);
        }

        // ===== 层5: 黑/红菱形粒子 (减到20个) =====
        for (int i = 0; i < 20; i++) {
            boolean alt = i < 8;
            float as = alt ? 1f : 2.25f;
            float wid2 = alt ? width : width / 1.5f;
            float d = alt ? rand.random(16f, 27f) : rand.random(34f, 65f);
            float timeOffset = rand.random(d);
            int timeSeed = Mathf.floor((time + timeOffset) / d) + rand.nextInt();
            float fin = ((time + timeOffset) % d) / d;

            rand2.setSeed(timeSeed);
            Draw.color(rand2.chance(0.75f) ? Color.black : col);

            float w = rand2.random(20f, 35f) * inout * as * Mathf.slope(fin);
            float l = rand2.random(90f, 190f) * as * (alt ? 1f : 1.5f);
            float p1 = Mathf.pow(rand2.nextFloat(), 2f);
            float trns = rand2.random(length / 12f, length / 5f);
            float yps = rand2.random(l, Math.max((length + endLength * (1f - p1)) - (l + trns), l));
            float xps = (p1 * Math.max(wid2 - w, 0f) * Mathf.sign(rand2.chance(0.5f))) + rand2.range(8f) * fin;
            float wScl = getWidth(yps, 1f);
            Tmp.v1.trns(b.rotation(), trns * Interp.pow2In.apply(fin) + yps, xps * wScl).add(b);
            diamond(Tmp.v1.x + Mathf.range(6f) * fin, Tmp.v1.y + Mathf.range(6f) * fin, w, l, b.rotation());
        }

        // ===== 层6: 内部线段 (减到10个, 黑/红交替) =====
        for (int i = 0; i < 10; i++) {
            boolean alt = i < 6;
            float as = alt ? 1f : 3f;
            float wid2 = alt ? width : width / 2f;
            float d = alt ? rand.random(12f, 22f) : rand.random(22f, 60f);
            float timeOffset = rand.random(d);
            int timeSeed = Mathf.floor((time + timeOffset) / d) + rand.nextInt();
            float fin = ((time + timeOffset) % d) / d;

            rand2.setSeed(timeSeed);
            Draw.color(rand2.chance(alt ? 0.5f : 0.75f) ? Color.black : col);

            float delay = rand2.random(0.55f, 0.8f);
            float pos1 = Mathf.pow(rand2.nextFloat(), 2f);
            float w = rand2.random(2f, 7f + (1f - pos1) * 3f) * as * Mathf.lerp(1f, rand2.random(0.8f, 1.2f), fin);
            float l = rand2.random(length / 2f);
            float pos2 = pos1 * Math.max(wid2 - (w * 2f), 0f) * Mathf.sign(rand2.chance(0.5f));

            float trns = ((length + endLength * (1f - pos1)) - l) * rand2.random(1f - (pos1 * 0.25f), 1.1f);
            float f1 = Mathf.curve(fin, 0f, 1f - delay), f2 = Mathf.curve(fin, delay, 1f);
            Lines.stroke(w * inout);
            drawLine(b, Interp.pow2In.apply(f1) * trns + l, Interp.pow2In.apply(f2) * trns + l, pos2);
        }

        // ===== 层7: 闪电 (减到2条, 25节点) =====
        rand.setSeed(b.id * 999L + 7452);
        for (int i = 0; i < 2; i++) {
            float d = rand.random(30f, 50f);
            float timeOffset = rand.random(d);
            int timeSeed = (int)((time + timeOffset) / d) + rand.nextInt();
            float fin = ((time + timeOffset) % d) / d;
            drawLightning(b, timeSeed, fin, fow, width);
        }

        Draw.blend();
        Draw.reset();
        Draw.z(oldZ);
    }

    /** 绘制带宽度衰减的线段 */
    void drawLine(Bullet b, float l1, float l2, float width) {
        lines.clear();
        float sw = Lines.getStroke();
        float s = Mathf.clamp((sw - 3f) / 2f);
        if (l1 < l2) {
            float l = l1;
            l1 = l2;
            l2 = l;
        }
        float h = Math.min(cone, l1) - l2;
        if (h > 0) {
            float l = l2;
            while (l < Math.min(cone, l1)) {
                Tmp.v1.trns(b.rotation(), l, getWidth(l, width)).add(b);
                lines.add(Tmp.v1.x, Tmp.v1.y);
                l += 4f;
            }
            if (l1 > cone) {
                Tmp.v1.trns(b.rotation(), cone, width).add(b);
                lines.add(Tmp.v1.x, Tmp.v1.y);
            }
        } else {
            Tmp.v1.trns(b.rotation(), l2, width).add(b);
            lines.add(Tmp.v1.x, Tmp.v1.y);
        }
        Tmp.v1.trns(b.rotation(), l1, getWidth(l1, width)).add(b);
        lines.add(Tmp.v1.x, Tmp.v1.y);

        for (int i = 0; i < lines.size - 2; i += 2) {
            float x1 = lines.get(i), y1 = lines.get(i + 1), x2 = lines.get(i + 2), y2 = lines.get(i + 3);
            Lines.line(x1, y1, x2, y2, false);
            if (sw > 3f) {
                if (i == 0) {
                    Drawf.tri(x1, y1, sw * 1.22f, sw * s * 2f, arc.math.Angles.angle(x2, y2, x1, y1));
                }
                if (i == lines.size - 4) {
                    Drawf.tri(x2, y2, sw * 1.22f, sw * s * 2f, arc.math.Angles.angle(x1, y1, x2, y2));
                }
            }
        }
    }

    /** 末端尖刺边缘 */
    void drawEndEdge(Bullet b, long seed, float x, float y, float width, float stroke) {
        float spikeLength = endLength;
        float time = Time.time * timeMul;
        rand.setSeed(b.id * 9999L + seed);
        Drawf.tri(x, y, stroke * 1.22f, Math.abs(width) / 4f, b.rotation());
        for (int i = 0; i < 7; i++) {
            float d = rand.random(30f, 60 * 2f);
            float timeOffset = rand.random(d);
            int timeSeed = Mathf.floor((time + timeOffset) / d) + rand.nextInt();
            float fin = ((time + timeOffset) % d) / d;

            rand2.setSeed(timeSeed);
            float w = rand2.random(stroke * 3f, stroke * 5f);
            float ofRand = rand2.random(0.5f, 0.8f);
            float of = -width * Interp.pow3In.apply(fin) * ofRand;
            float l = w * 5f * rand2.random(1f, 2f) * Interp.pow3Out.apply(fin);
            float w2 = w * Mathf.slope(fin);
            float trns = (spikeLength * ofRand) + rand2.random(60f, 110f);
            Vec2 v = Tmp.v3.trns(b.rotation(), fin * fin * trns, of).add(x, y);
            diamond(v.x, v.y, w2, l, b.rotation());
        }
    }

    /** 末端虚空 */
    void drawEndVoid(Bullet b, float x, float y, float width) {
        float spikeLength = endLength;
        float tx1 = arc.math.Angles.trnsx(b.rotation() + 90f, width),
              ty1 = arc.math.Angles.trnsy(b.rotation() + 90f, width),
              tx2 = arc.math.Angles.trnsx(b.rotation(), spikeLength) + x,
              ty2 = arc.math.Angles.trnsy(b.rotation(), spikeLength) + y;
        Fill.tri(x + tx1, y + ty1, x - tx1, y - ty1, tx2, ty2);

        rand.setSeed(b.id * 9999L + 1411);
        float time = Time.time * timeMul;
        for (int i = 0; i < 11; i++) {
            float d = rand.random(40f, 60 * 3f);
            float timeOffset = rand.random(d);
            int timeSeed = Mathf.floor((time + timeOffset) / d) + rand.nextInt();
            float fin = ((time + timeOffset) % d) / d;
            float fr = 1f - fin;

            rand2.setSeed(timeSeed);

            float w = rand2.random(width / 5f, width / 1.75f);
            float fr2 = Mathf.lerp(fr, 1f, rand2.random(0.1f, 0.6f));
            float w2 = w * Mathf.curve(fr, 0f, 0.8f) * Mathf.curve(fin, 0f, 0.2f);
            float l = w * 3f * rand2.random(0.8f, 2f) * Interp.pow3Out.apply(fin);
            float pos1 = rand2.range(1f);
            float pos = pos1 * Math.max(width - w2 / 2.05f, 0f);
            float sclL = 1 + Math.abs(pos1) * 0.25f;
            float trns = rand2.random(220f, 380f);
            float offset = ((1f - Math.abs(pos1)) * spikeLength) + rand2.random(-34f, 4f) - (trns * 0.2f * 0.2f);
            Vec2 v = Tmp.v3.trns(b.rotation(), fin * fin * sclL * trns + offset, pos * fr2).add(x, y);
            diamond(v.x + Mathf.range(6f) * fin, v.y + Mathf.range(6f) * fin, w2, l, b.rotation());
        }
    }

    /** 闪电绘制 (25节点, 三色渐变) */
    void drawLightning(Bullet b, int seed, float fin, float fout, float width) {
        rand2.setSeed(seed * 2L + 856387231L);
        float time = b.time / 3f;
        float f2 = time % 1f;
        int timeSeed = (int)time;
        float pos = 0f, max = 0f;
        float pos2 = 0, max2 = 0f;
        float drift = rand2.range(1f);
        float length = this.length + (endLength * (1f - Math.abs(drift)));

        for (int i = 0; i < ltmp.length; i++) {
            rand2.setSeed(seed * 9999L + (timeSeed + ltmp.length - i));
            float r = rand2.range(1.5f);
            rand2.setSeed(seed * 9999L + (timeSeed + ltmp.length - (i + 1)));
            float r2 = rand2.range(1.5f);
            pos += r;
            pos2 += r2;
            ltmp[i] = pos;
            ltmp2[i] = pos2;
        }
        float drft2 = drift > 0 ? (1f + drift) : (1f + drift / 2f);
        float delta = (pos / ltmp.length) * drft2;
        float delta2 = (pos2 / ltmp.length) * drft2;
        for (int i = 0; i < ltmp.length; i++) {
            float v = ltmp[i] - delta * i;
            float v2 = ltmp2[i] - delta2 * i;
            ltmp[i] = v;
            ltmp2[i] = v2;
            max = Math.max(max, Math.abs(v));
            max2 = Math.max(max2, Math.abs(v2));
        }
        float lx = b.x, ly = b.y;
        Tmp.c1.lerp(lightningColors, Mathf.curve(fin, 0.01f, 0.7f));
        Draw.color(Tmp.c1);
        Lines.stroke(Mathf.clamp(1f - fin, 0f, 0.4f) * 11f * fout);
        for (int i = 1; i < ltmp.length; i++) {
            float v = (ltmp[i] / max) * width;
            float v2 = (ltmp2[i] / max2) * width;
            float w = Mathf.lerp(v, v2, 1f - f2);
            Vec2 nv = Tmp.v1.trns(b.rotation(), (i / (ltmp.length - 1f)) * length, w).add(b);
            Lines.line(lx, ly, nv.x, nv.y, false);
            lx = nv.x;
            ly = nv.y;
        }
    }
}
