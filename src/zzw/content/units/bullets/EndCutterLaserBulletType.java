package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import zzw.content.units.effects.UnitCutEffect;

/**
 * PU_V8 EndCutterLaserBulletType 移植版 (tenmeikiri 主激光)
 *
 * 完全按原版复制, v158 适配:
 * - 激光长度随时间增长 (laserSpeed, accel, maxLength)
 * - 4 色叠加渲染 (scarColorAlpha → scarColor → endColor → white)
 * - 沿激光路径产生闪电
 * - 命中障碍物时激光停止增长, 在尖端产生命中特效
 * - 防作弊伤害 (继承 AntiCheatBulletTypeBase)
 *
 * v158 适配:
 * - Drawf.light 无 Team 参数版本: light(x1, y1, x2, y2, stroke, color, alpha)
 * - 用 Vars.indexer.eachBlock + Units.nearbyEnemies 替代 Utils.collideLineRawEnemy
 * - 移除 Unity.antiCheat 采样湮灭系统 + UnitCutEffect
 *
 * 参考: PU_V8 main/src/unity/entities/bullet/anticheat/EndCutterLaserBulletType.java
 */
public class EndCutterLaserBulletType extends AntiCheatBulletTypeBase {
    public float maxLength = 1000f;
    public float laserSpeed = 15f;
    public float accel = 25f;
    public float width = 12f;
    public float antiCheatScl = 1f;
    public float fadeTime = 60f;
    public float fadeInTime = 8f;

    // PU132 颜色常量 (UnityPal)
    public Color[] colors = {
        Color.valueOf("f5303690"),  // scarColorAlpha (半透明红)
        Color.valueOf("f53036"),    // scarColor (红)
        Color.valueOf("ff786e"),    // endColor (淡红)
        Color.white
    };

    // 闪电参数
    public Color lightningColor = Color.valueOf("f53036");  // scarColor
    public float lightningDamage = 0f;
    public int lightningLength = 0;

    // 命中特效 (PU132 HitFx.tenmeikiriTipHit)
    public Effect tipHitEffect = new Effect(27f, e -> {
        randLenVectors(e.id, 8, 90f * e.fin(), e.rotation, 80f, (x, y) -> {
            float angle = Mathf.angle(x, y);
            Draw.color(Color.valueOf("f53036"), Color.valueOf("ff786e"), e.fin());
            Lines.stroke(1.5f);
            Lines.lineAngleCenter(e.x + x, e.y + y, angle, e.fslope() * 13f);
        });
    });

    // 激光数据 (PU132 LaserData)
    private static class LaserData {
        float velocity = 0f;
        float velocityTime = 0f;
        float restartTime = 0f;
        float lightningTime = 0f;
        float lastLength = 0f;
    }

    public EndCutterLaserBulletType(float damage) {
        super(0.005f, damage);
        despawnEffect = mindustry.content.Fx.none;
        collides = false;
        pierce = true;
        hittable = false;
        absorbable = false;
        lifetime = 3f * 60f;
        drawSize = 2400f;
    }

    @Override
    public float estimateDPS() {
        return damage * (lifetime / 2f) / 5f * 3f;
    }

    @Override
    protected float calculateRange() {
        return maxLength / 2f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = new LaserData();
        b.fdata = 0f;
    }

    @Override
    public void draw(Bullet b) {
        // ★ 原版不调用 Draw.blend(), 让激光保持 additive 渲染 (红色激光在 additive 模式下更亮更粗)
        // (黑色激光才需要 Draw.blend() 重置为 alpha 混合, 红色激光不需要)
        float fade = Mathf.clamp(b.time > b.lifetime - fadeTime ? 1f - (b.time - (lifetime - fadeTime)) / fadeTime : 1f) * Mathf.clamp(b.time / fadeInTime);
        float tipHeight = width / 2f;

        // 主激光线 (背景指示线)
        if (b.fdata > 0.001f) {
            Lines.lineAngle(b.x, b.y, b.rotation(), b.fdata);
        }

        for (int i = 0; i < colors.length; i++) {
            float f = ((float) (colors.length - i) / colors.length);
            float w = f * (width + Mathf.absin(Time.time + (i * 1.4f), 1.1f, width / 4)) * fade;

            Tmp.v2.trns(b.rotation(), b.fdata - tipHeight).add(b);
            Tmp.v1.trns(b.rotation(), width * 2f).add(Tmp.v2);
            Draw.color(colors[i]);
            Fill.circle(b.x, b.y, w / 2f);
            Lines.stroke(w);
            // ★ 检查线段长度 > 0, 避免 length=0 产生 NaN 顶点
            if (b.fdata > 0.001f) {
                Lines.line(b.x, b.y, Tmp.v2.x, Tmp.v2.y, false);
                // 尖端三角形
                for (int s : Mathf.signs) {
                    Tmp.v3.trns(b.rotation(), w * -0.7f, w * s);
                    Fill.tri(Tmp.v2.x, Tmp.v2.y, Tmp.v1.x, Tmp.v1.y, Tmp.v2.x + Tmp.v3.x, Tmp.v2.y + Tmp.v3.y);
                }
            }
        }
        Tmp.v2.trns(b.rotation(), b.fdata + tipHeight).add(b);
        // ★ v158 Drawf.light 无 Team 参数版本: light(x1, y1, x2, y2, stroke, color, alpha)
        Drawf.light(b.x, b.y, Tmp.v2.x, Tmp.v2.y, width * 2f, colors[0], 0.5f);
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b) {
        // 由 draw() 中 Drawf.light 处理, 此处空
    }

    @Override
    public void update(Bullet b) {
        // 激光增长
        if (b.data instanceof LaserData) {
            LaserData vec = (LaserData) b.data;
            if (vec.restartTime >= 5f) {
                vec.velocity = Mathf.clamp((vec.velocityTime / accel) + vec.velocity, 0f, laserSpeed);
                b.fdata = Mathf.clamp(b.fdata + (vec.velocity * Time.delta), 0f, maxLength);
                vec.velocityTime += Time.delta;
            } else {
                vec.restartTime += Time.delta;
            }
        }

        // 碰撞检测 (每 5 tick 一次)
        if (b.timer(0, 5f)) {
            checkCollision(b);
        }

        // 闪电生成 (沿激光新增段)
        if (b.data instanceof LaserData) {
            LaserData vec = (LaserData) b.data;
            if (vec.lightningTime >= 1f && b.fdata > vec.lastLength && lightningLength > 0) {
                int dst = Math.max(Mathf.round((b.fdata - vec.lastLength) / 5), 1);
                for (int i = 0; i < dst; i++) {
                    float f = Mathf.lerp(vec.lastLength, b.fdata, (float) i / dst);
                    Tmp.v1.trns(b.rotation(), f).add(b);
                    Lightning.create(b.team, lightningColor, lightningDamage, Tmp.v1.x, Tmp.v1.y, b.rotation() + Mathf.range(20f), lightningLength);
                }
                vec.lightningTime -= 1f;
                vec.lastLength = b.fdata;
            }
            vec.lightningTime += Time.delta;
        }
    }

    /**
     * 沿激光线段检测碰撞 (PU132 Utils.collideLineRawEnemy 简化版)
     * - 命中建筑/单位时, 激光长度停止在该位置, 重置 velocity, 触发命中特效
     */
    private void checkCollision(Bullet b) {
        float ex = Tmp.v1.trns(b.rotation(), b.fdata).add(b.x, b.y).x;
        float ey = Tmp.v1.trns(b.rotation(), b.fdata).add(b.x, b.y).y;
        boolean[] hit = {false};

        // 检测建筑: 使用 Vars.indexer.eachBlock 遍历激光范围内的建筑
        Vars.indexer.eachBlock(null, b.x, b.y, b.fdata + 32f,
            build -> build.team != b.team && build.health > 0,
            build -> {
                if (hit[0]) return;
                // 检查激光线段是否穿过建筑碰撞箱
                Tmp.r1.setCentered(build.x, build.y, build.block.size * Vars.tilesize);
                Vec2 hv = Geometry.raycastRect(b.x, b.y, ex, ey, Tmp.r1);
                if (hv != null) {
                    // 检查是否健康度高于伤害阈值 (激光被阻挡)
                    if (build.health > damage * buildingDamageMultiplier * 0.5f) {
                        float dst = Intersector.distanceLinePoint(b.x, b.y, ex, ey, build.x, build.y);
                        b.fdata = ((b.dst(build) - (build.block.size * Vars.tilesize / 2f)) + dst) + 4f;
                        if (b.data instanceof LaserData) {
                            LaserData data = (LaserData) b.data;
                            data.velocity = 0f;
                            data.restartTime = 0f;
                            data.velocityTime = 0f;
                        }
                        // 重新计算激光末端位置
                        Tmp.v2.trns(b.rotation(), b.fdata).add(b);
                        // 命中特效
                        for (int i = 0; i < 2; i++) {
                            tipHitEffect.at(Tmp.v2.x + Mathf.range(4f), Tmp.v2.y + Mathf.range(4f), b.rotation() + 180f);
                        }
                        hitBuildingAntiCheat(b, build);
                        hit[0] = true;
                    } else {
                        hitBuildingAntiCheat(b, build);
                    }
                }
            });

        if (hit[0]) return;

        // 检测单位: 沿激光线段查找最近的敌方单位
        Tmp.v3.set(b.x + ex, b.y + ey).scl(0.5f);  // 中点
        float radius = b.dst(ex, ey) * 0.5f + 32f;
        Seq<Unit> units = new Seq<>();
        Units.nearbyEnemies(b.team, Tmp.v3.x, Tmp.v3.y, radius, unit -> {
            if (unit.health > 0 && !unit.dead) {
                float d = Intersector.distanceLinePoint(b.x, b.y, ex, ey, unit.x, unit.y);
                if (d < unit.hitSize) {
                    units.add(unit);
                }
            }
        });

        if (units.size > 0) {
            // 找最近单位 (激光阻挡)
            Unit nearest = null;
            float minDist = Float.MAX_VALUE;
            for (Unit u : units) {
                float d = b.dst2(u);
                if (d < minDist) {
                    minDist = d;
                    nearest = u;
                }
            }
            if (nearest != null && nearest.health > damage) {
                float dst = Intersector.distanceLinePoint(b.x, b.y, ex, ey, nearest.x, nearest.y);
                b.fdata = ((b.dst(nearest) - (nearest.hitSize / 2f)) + dst) + 4f;
                if (b.data instanceof LaserData) {
                    LaserData data = (LaserData) b.data;
                    data.velocity = 0f;
                    data.restartTime = 0f;
                    data.velocityTime = 0f;
                }
                Tmp.v2.trns(b.rotation(), b.fdata).add(b);
                for (int i = 0; i < 2; i++) {
                    tipHitEffect.at(Tmp.v2.x + Mathf.range(4f), Tmp.v2.y + Mathf.range(4f), b.rotation() + 180f);
                }
            }
            // 对所有命中单位造成伤害
            for (Unit u : units) {
                hitUnitAntiCheat(b, u);
                // ★ PU_V8 切割效果: 大单位被击杀时触发切割动画
                // 原版条件: (unit.dead || unit.health >= Float.MAX_VALUE) && (hitSize >= 30 || health >= MAX_VALUE)
                // ★ 修复: unit.damage() → kill() → remove() 后 isValid() 返回 false, 但 dead=true 或 health<=0 仍可判断
                // ★ 修复: 移除 createCut 中的 isValid() 检查, 改用 unit.type != null
                if ((u.dead || u.health <= 0f) && u.hitSize >= 30f) {
                    // 激光延伸方向 (用于切割方向计算)
                    Tmp.v2.trns(b.rotation(), maxLength * 1.5f).add(b);
                    UnitCutEffect.createCut(u, b.x, b.y, Tmp.v2.x, Tmp.v2.y);
                    // ★ 延迟 remove, 让切割特效有时间渲染 unit
                    // PU_V8 用 AntiCheat.annihilateEntity(unit, true) 仅移除 groups 但不调用 unit.remove()
                    // 此处标记 dead 并用 Time.run 延迟 remove (特效持续时间内保持可绘制)
                    // createCut 已将 unit 从 Groups.draw 移除, 防止引擎自动绘制与特效重叠
                    u.health = 0f;
                    u.dead = true;
                    Time.run(UnitCutEffect.CUT_DURATION, () -> {
                        if (u != null && u.isAdded()) u.remove();
                    });
                }
            }
        }
    }

    @Override
    public void init() {
        super.init();
        drawSize = maxLength * 2f;
    }

    /** 简化版 randLenVectors (PU132 Angles.randLenVectors, 内联避免导入冲突) */
    private static void randLenVectors(int seed, int amount, float length, float rotation, float width, arc.func.Floatc2 cons) {
        arc.math.Rand rand = new arc.math.Rand(seed);
        for (int i = 0; i < amount; i++) {
            float a = rand.range(180f) + rotation;
            float len = rand.random(length * 0.5f, length);
            float x = Mathf.cosDeg(a) * len;
            float y = Mathf.sinDeg(a) * len;
            cons.get(x, y);
        }
    }
}
