package zzw.content.units.effects;

import zzw.content.units.entities.SlowLightningEntity;
import zzw.content.units.utils.SlowLightningUtils;

import arc.func.Floatp;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.game.Team;
import mindustry.entities.Effect;
import mindustry.gen.Bullet;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.gen.Building;
import mindustry.graphics.Pal;

/**
 * PU132 SlowLightningType 移植版 (适配 v150.1)
 * - 节点树管理 (splitChance 分裂, maxNodes 上限)
 * - 旋转速度按距离插值 (minRotationSpeed ~ maxRotationSpeed)
 * - continuous 模式每 5 tick 触发沿线碰撞
 * 参考: PU132 unity.entities.effects.SlowLightningType
 */
public class SlowLightningType {
    private static int seed = 1;
    /** ★ 优化：减少节点上限，从60降到30，降低渲染开销 */
    public static final int maxNodes = 30;
    /** ★ 优化：最大递归层数，防止闪电过度延伸 */
    public static final int maxLayers = 8;
    public static final arc.util.pooling.Pool<SlowLightningNode> nodes =
        new arc.util.pooling.Pool<SlowLightningNode>(8, 300) {
            @Override
            protected SlowLightningNode newObject() {
                return new SlowLightningNode();
            }
        };

    public Color colorFrom = Color.white, colorTo = Pal.lancerLaser;
    public float damage = 12;
    public float colorTime = 32f, fadeTime = 20f;
    /** ★ 优化：降低分裂概率，从0.035降到0.02，减少节点指数增长 */
    public float splitChance = 0.02f;
    public float nodeLength = 50f, nodeTime = 3f, range = 150f;
    public float randSpacing = 20f, splitRandSpacing = 60f;
    public float lineWidth = 2f, lifetime = 120f;
    public float maxRotationSpeed = 22f, minRotationSpeed = 1.5f, rotationDistance = 600f;
    public boolean continuous = false;
    /** ★ 优化：碰撞检测间隔，从5tick增加到10tick */
    public float collideInterval = 10f;
    public Effect hitEffect = mindustry.content.Fx.hitLancer;
    /** ★ 锯齿渲染：每段中间插入的锯齿点数 (0=禁用, 2-3=闪电感) */
    public int jaggedPoints = 0;
    /** ★ 锯齿幅度：占线段长度的比例 (0.12 = 12%) */
    public float jaggedness = 0.12f;

    public SlowLightningEntity create(Team team, float x, float y, float rotation, Floatp liveDamage, Posc parent, Position target) {
        return create(team, null, x, y, rotation, liveDamage, parent, target);
    }

    public SlowLightningEntity create(Team team, Bullet b, float x, float y, float rotation, Floatp liveDamage, Posc parent, Position target) {
        SlowLightningEntity s = SlowLightningEntity.create();
        s.seed = seed;
        s.type = this;
        s.team = team;
        s.bullet = b;
        s.set(x, y);
        s.rotation = rotation;
        s.liveDamage = liveDamage;
        s.parent = parent;
        s.target = target;
        s.add();
        seed++;
        return s;
    }

    public void damageUnit(SlowLightningNode s, Unit unit) {
        Floatp l = s.main.liveDamage;
        unit.damage(l != null ? l.get() : damage);
    }

    public void damageBuilding(SlowLightningNode s, Building building) {
        Floatp l = s.main.liveDamage;
        building.damage(l != null ? l.get() : damage);
    }

    public void hit(SlowLightningNode s, float x, float y) {
        hitEffect.at(x, y, s.rotation, colorFrom);
    }

    public static class SlowLightningNode implements Position, arc.util.pooling.Pool.Poolable {
        /** ★ 性能优化：锯齿顶点静态缓冲区，避免每帧 new float[] */
        private static final float[] jaggedVerts = new float[20];

        public float x, y, colorProgress, time, rotation, rotRand, dist;
        public int layer = 0;
        public SlowLightningEntity main;
        public SlowLightningNode parent;
        public boolean ended = false;

        public void move(int originLayer, float mx, float my) {
            float scl = 1f - (layer / (float) originLayer);
            x += mx * scl;
            y += my * scl;
        }

        public void update() {
            SlowLightningType type = main.type;
            if (colorProgress < 1f) colorProgress = Math.min(1f, colorProgress + (Time.delta / type.colorTime));
            if (time < 1f) {
                time = Math.min(1f, time + (Time.delta / type.nodeTime));
                if (time >= 1f) {
                    end();
                }
            }
        }

        public void draw() {
            SlowLightningType type = main.type;
            Draw.color(type.colorFrom, type.colorTo, colorProgress);
            Position p = getLast();
            float sx = p.getX(), sy = p.getY();
            float ex, ey;
            if (time >= 1f) {
                ex = x;
                ey = y;
            } else {
                Vec2 v = Tmp.v1.set(this).sub(p).scl(time).add(p);
                ex = v.x;
                ey = v.y;
            }

            int jp = type.jaggedPoints;
            // 无锯齿或线段过短：直接画直线
            if (jp <= 0) {
                Lines.line(sx, sy, ex, ey);
                return;
            }

            float dx = ex - sx, dy = ey - sy;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) {
                Lines.line(sx, sy, ex, ey);
                return;
            }

            // ★ 锯齿渲染：起点 + jp 中间点 + 终点，使用 polyline 一次性绘制
            int count = jp + 2;
            float[] verts = jaggedVerts;
            verts[0] = sx;
            verts[1] = sy;
            verts[count * 2 - 2] = ex;
            verts[count * 2 - 1] = ey;

            // 垂直方向（归一化）
            float inv = 1f / len;
            float nx = -dy * inv, ny = dx * inv;

            // 插入中间点：基于位置生成稳定 hash 偏移，避免每帧抖动
            for (int i = 1; i <= jp; i++) {
                float t = (float)i / (count - 1);
                float cx = sx + dx * t, cy = sy + dy * t;
                // 稳定伪随机：基于线段端点位置 + 索引
                float h = sx * 127.1f + sy * 311.7f + ex * 74.7f + ey * 93.3f + i * 53.7f;
                float frac = h - Mathf.floor(h);
                float offset = (frac * 2f - 1f) * len * type.jaggedness;
                verts[i * 2] = cx + nx * offset;
                verts[i * 2 + 1] = cy + ny * offset;
            }

            Lines.polyline(verts, count * 2, false);
        }

        void line(float x, float y, float x2, float y2) {
            SlowLightningType type = main.type;
            SlowLightningUtils.collideLineRawEnemy(main.team, x, y, x2, y2, type.lineWidth / 3f,
                (building) -> {
                    type.damageBuilding(this, building);
                },
                (unit) -> {
                    type.damageUnit(this, unit);
                },
                null, (ex, ey) -> type.hit(this, ex, ey), false);
        }

        void end() {
            SlowLightningType type = main.type;
            if (!type.continuous) {
                Position p = getLast();
                line(p.getX(), p.getY(), x, y);
            }
            /** ★ 优化：增加层数限制，防止闪电过度延伸 */
            if (!ended && main.distance < type.range && main.nodes.size < maxNodes && layer < maxLayers) {
                main.end(this);
            }
        }

        public void collide() {
            Position p = getLast();
            if (time >= 1f) {
                line(p.getX(), p.getY(), x, y);
            } else {
                Vec2 v = Tmp.v1.set(this).sub(p).scl(time).add(p);
                line(p.getX(), p.getY(), v.x, v.y);
            }
        }

        Position getLast() {
            return parent != null ? parent : main;
        }

        @Override
        public float getX() { return x; }

        @Override
        public float getY() { return y; }

        @Override
        public void reset() {
            x = y = colorProgress = time = rotation = rotRand = 0f;
            layer = 0;
            main = null;
            parent = null;
            ended = false;
        }
    }
}
