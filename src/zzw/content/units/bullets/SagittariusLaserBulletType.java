package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;

/**
 * 人马座 8 道激光 (PU_V8 SagittariusLaserBulletType 移植版)
 * - 8 道旋转激光, 螺旋分布
 * - 每道激光独立碰撞检测, 命中后停止
 * - 自定义绘制: 多色叠加 + 三角形收口
 * 简化: 用 v158 Damage.collideLaser / Units.nearbyEnemies 替代 Utils.collideLineRawEnemy
 *       移除 UnityStatusEffects.speedFatigue (despawned 时不再施加)
 *       Drawf.light 使用单 team 参数版 (v158 API)
 * 参考: PU_V8 main/src/unity/entities/bullet/laser/SagittariusLaserBulletType.java
 */
public class SagittariusLaserBulletType extends BulletType {
    public Color[] colors = {Pal.heal.cpy().a(0.2f), Pal.heal.cpy().a(0.5f), Pal.heal.cpy().mul(1.2f), Color.white};
    public float length = 550f, width = 45f;
    public int lasers = 8;

    public SagittariusLaserBulletType(float damage) {
        super(0f, damage);
        keepVelocity = false;
        collides = false;
        pierce = true;
        hittable = false;
        absorbable = false;

        hitEffect = Fx.hitBulletSmall;
        hitColor = Pal.heal;
    }

    @Override
    public void init() {
        super.init();
        range = length;
        despawnHit = false;
        drawSize = length * 2f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = new SagittariusLaserData();
    }

    @Override
    public void update(Bullet b) {
        if (b.timer(1, 5f) && b.data instanceof SagittariusLaserData) {
            SagittariusLaserData data = (SagittariusLaserData) b.data;

            float fin = b.time < 200 ? b.time / 200f : 1f;
            float sfn = (b.time < 60f ? b.time / 60f : 1f);
            float fn = sfn + (fin / 15f);
            float w = (width * sfn) / 3f;

            // 给 owner 反作用力
            if (b.owner instanceof mindustry.gen.Physicsc) {
                mindustry.gen.Physicsc owner = (mindustry.gen.Physicsc) b.owner;
                Tmp.v1.trns(b.rotation() + 180f, 25f * fin);
                owner.impulse(Tmp.v1);
            }

            for (int i = 0; i < lasers; i++) {
                float ang = i * 360f / lasers, time = b.time * 2f;
                float sin = Mathf.sinDeg(time + ang), cos = Mathf.cosDeg(time + ang);
                Vec2 p = Tmp.v1.trns(b.rotation() + 90f, sin * 9f * fin, cos * 4f * fin).add(b),
                        end = Tmp.v2.trns(b.rotation() + sin * 20f * fin, length).add(p);

                float hitLen = checkLaserCollision(b, p.x, p.y, end.x, end.y, w, fn);
                data.length[i] = hitLen;
            }
        }
    }

    /** 简化版激光碰撞检测: 返回命中长度 (未命中返回 length) */
    private float checkLaserCollision(Bullet b, float x1, float y1, float x2, float y2, float width, float fn) {
        float[] hitLen = {Mathf.dst(x1, y1, x2, y2)};
        boolean[] hit = {false};
        float[] hitXY = {x2, y2};

        // 检测建筑
        mindustry.Vars.indexer.eachBlock(null, x1, y1, hitLen[0] + 50f,
                build -> build.team != b.team,
                build -> {
                    if (hit[0]) return;
                    float size = build.block.size * mindustry.Vars.tilesize / 2f;
                    if (arc.math.geom.Intersector.distanceSegmentPoint(x1, y1, x2, y2, build.x, build.y) > width + size)
                        return;
                    build.damage(damage * fn * buildingDamageMultiplier);
                    if (build.block.absorbLasers) {
                        hit[0] = true;
                        hitXY[0] = build.x;
                        hitXY[1] = build.y;
                    }
                });

        // 检测单位
        if (!hit[0]) {
            mindustry.entities.Units.nearbyEnemies(b.team, x1, y1, hitLen[0] + 50f, unit -> {
                if (hit[0]) return;
                if (!unit.hittable()) return;
                if (arc.math.geom.Intersector.distanceSegmentPoint(x1, y1, x2, y2, unit.x, unit.y) > width + unit.hitSize / 2f)
                    return;
                unit.damage(damage * fn);
                Tmp.v3.set(unit).sub(b).nor().scl(knockback * 80f * fn);
                unit.impulse(Tmp.v3);
                unit.apply(status, statusDuration);
            });
        }

        if (hit[0]) {
            hitLen[0] = Mathf.dst(x1, y1, hitXY[0], hitXY[1]);
        }
        return hitLen[0];
    }

    @Override
    public void draw(Bullet b) {
        if (b.data instanceof SagittariusLaserData) {
            SagittariusLaserData data = (SagittariusLaserData) b.data;
            float cw = width + Mathf.absin(0.8f, 1.5f);
            float fout = Mathf.clamp((b.lifetime - b.time) / 16f);
            float fin = b.time < 200 ? b.time / 200f : 1f;
            float sfn = (b.time < 60f ? b.time / 60f : 1f);

            for (Color color : colors) {
                float w = cw * (sfn + (fin / 15f)) * fout;
                for (int i = 0; i < lasers; i++) {
                    Draw.color(color);

                    float length = data.length[i];
                    float ang = i * 360f / lasers, time = b.time * 2f;
                    float sin = Mathf.sinDeg(time + ang), cos = Mathf.cosDeg(time + ang);
                    float rot = b.rotation() + sin * 20f * fin;

                    Vec2 p = Tmp.v1.trns(b.rotation() + 90f, sin * 9f * fin, cos * 4f * fin).add(b),
                            end = Tmp.v2.trns(rot, length).add(p);

                    Lines.stroke(w);
                    Lines.line(p.x, p.y, end.x, end.y, false);
                    Drawf.tri(end.x, end.y, Lines.getStroke() * 1.22f, cw * 3 + width / 1.5f, rot);
                    Draw.color(Tmp.c1.set(color).a(Mathf.pow(color.a, lasers / 3f)));
                    Fill.circle(p.x, p.y, w);
                    if (color == colors[0])
                        Drawf.light(p.x, p.y, end.x, end.y, w * 1.7f * b.fout(), colors[0], 0.6f);
                }
                cw *= 0.5f;
            }
        }
    }

    @Override
    public void drawLight(Bullet b) {
        // 不绘制光源 (在 draw 中处理)
    }

    class SagittariusLaserData {
        float[] length = new float[lasers];
    }
}
