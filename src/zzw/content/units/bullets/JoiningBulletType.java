package zzw.content.units.bullets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.math.geom.Rect;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Teamc;
import mindustry.gen.Healthc;
import mindustry.gen.Groups;
import mindustry.graphics.Layer;
import zzw.content.graphics.UnityPal;
import zzw.content.units.effects.UnityDrawf;

import static mindustry.Vars.tilesize;

/**
 * 聚合子弹类型 (PU132 unity.entities.bullet.monolith.energy.JoiningBulletType 移植)
 *
 * <p>核心机制: stele (石碑机甲) 的霰弹枪子弹。多发子弹飞行途中互相感应,
 * 靠近后"聚合"成一颗更大的子弹 (伤害 = max + min * 1.25), 聚合可连锁发生
 * (上限 maxDamage), 类似滚雪球。聚合时子弹之间有相互吸引力 (sensitivity)。</p>
 *
 * <p>渲染: 多层 3D 旋转圆环 (panningCircle) 叠加成能量球外壳,
 * 越大 (伤害越高) 圆环层数越多。</p>
 *
 * <p>★ v158 适配:
 * <ul>
 *   <li>PU Utils.q1/q2 (Quat 旋转) → UnityDrawf.panningCircle 的 Vec3.rotate 等效实现</li>
 *   <li>PU Float2.construct(radius, bulletRadius) 特效数据 → float[]{...} 数组</li>
 *   <li>Trails.soul 尾迹 (CTrail 系统) → 暂未移植, 保留 trailColor 配置位</li>
 * </ul></p>
 *
 * @author GlennFolker (原版), 移植适配
 */
public class JoiningBulletType extends BulletType {
    /** 与 homingPower 类似, 但用于聚合吸引 */
    public float sensitivity = 0.2f;
    /** 与 homingDelay 类似, 但用于聚合吸引 */
    public float joinDelay = 0f;
    /** 吸引的最大速度, -1 = speed * 2.5 */
    public float attractMaxSpeed = -1f;
    /** 聚合伤害上限, -1 = damage * 4 */
    public float maxDamage = -1f;
    /** 聚合时的伤害产出系数 */
    public float yieldScl = 1.25f;
    /** 聚合目标必须在该角度锥内 */
    public float joinCone = 60f;
    /** 感知其他聚合子弹的距离 */
    public float joinRange = 2f * tilesize;
    /** 两子弹速度点积的最小值 (方向相似度) */
    public float minDot = 0.2f;

    /** 能量球基础半径 (实际半径随伤害增大) */
    public float radius = 10f;
    /** 圆环颜色组 (随机取色) */
    public Color[] colors = {UnityPal.monolithGreenLight, UnityPal.monolithGreen, UnityPal.monolithGreenDark};
    public Color edgeColor = UnityPal.monolithGreenLight.cpy().a(0.8f);
    public Color centerColor = UnityPal.monolithGreenDark.cpy().a(0f);

    /** 聚合成功时的特效 */
    public Effect joinEffect = Fx.none;

    /** 同类型标识 (copy() 不会复制, 保证克隆弹与母弹同组) */
    private final int identifier;

    // 静态查找缓存 (避免每帧分配)
    private static Bullet lastBullet;
    private static float lastScore;
    private static int lastID;

    public JoiningBulletType(float speed, float damage) {
        super(speed, damage);
        identifier = lastID++;
    }

    /** 子弹实际半径系数: 伤害越高球越大 (0.84 ~ 无上限渐增) */
    public float bulletRadius(Bullet b) {
        return 0.84f + 0.16f * (b.damage / damage);
    }

    @Override
    public void init() {
        if (attractMaxSpeed == -1f) attractMaxSpeed = speed * 2.5f;
        if (maxDamage == -1f) maxDamage = damage * 4f;
        super.init();
    }

    @Override
    public void init(Bullet b) {
        super.init(b);

        JoinData data = new JoinData();
        data.bullet = b;
        b.data = data;
    }

    @Override
    public void draw(Bullet b) {
        drawTrail(b);

        // ★ 渲染: 中心光球 + 多层 3D 旋转圆环 (PU132 原版逐层叠加)
        float r = radius * bulletRadius(b), start = radius * 0.8f, stroke = 2f;
        float z = Layer.flyingUnitLow - 0.01f;

        Lines.stroke(stroke);
        // ★ mod 贴图在 atlas 中自动带 "create-" 前缀, 必须用前缀名查找
        TextureRegion reg = Core.atlas.white(), light = Core.atlas.find("create-line-shade");

        // 中心: 径向渐变光球
        Fill.light(b.x, b.y, Lines.circleVertices(r), r, centerColor, edgeColor);

        int startAmount = Math.max(Mathf.round((r - start) / stroke), 0),
            amount = Math.max(Mathf.round(r / stroke), 1);

        // 外壳: 每层一个随机轴向旋转的 panningCircle 圆环
        for (int i = startAmount; i < amount; i++) {
            Draw.color(colors[Mathf.randomSeed(b.id - i, 0, colors.length - 1)]);
            float sr = stroke + i * stroke;

            // 随机 3D 旋转轴 (X/Y/Z) + 随机相位
            Mathf.rand.setSeed(b.id + i);
            Vec3 axis = switch (Mathf.randomSeed(b.id * 2L, 0, 2)) {
                case 0 -> Vec3.X;
                case 1 -> Vec3.Y;
                default -> Vec3.Z;
            };
            float rotAngle = Time.time * 6f + Mathf.randomSeed((b.id + i) * 4L, 0f, 1000f);

            UnityDrawf.panningCircle(reg,
                b.x, b.y, 1f, 1f,
                sr, 360f, 0f,
                axis, rotAngle, z, z
            );

            // 叠加 additive 辉光层
            Draw.color(Draw.getColor(), Color.black, 0.33f);
            Draw.blend(Blending.additive);
            UnityDrawf.panningCircle(light,
                b.x, b.y, 5f, 5f,
                sr, 360f, 0f,
                axis, rotAngle, z, z
            );

            Draw.blend();
        }

        Draw.reset();
    }

    @Override
    public void hit(Bullet b, float x, float y) {
        super.hit(b, x, y);
        hitEffect.at(x, y, b.rotation(), hitColor, new float[]{radius, bulletRadius(b)});
    }

    @Override
    public void despawned(Bullet b) {
        super.despawned(b);
        despawnEffect.at(b.x, b.y, b.rotation(), hitColor, new float[]{radius, bulletRadius(b)});
    }

    @Override
    public void removed(Bullet b) {
        super.removed(b);
        b.trail = null;
    }

    @Override
    public void update(Bullet b) {
        if (!b.isAdded()) return;

        // ===== 第 1 步: 扫描范围内的其他聚合子弹, 找速度最相似的 =====
        lastBullet = null;
        lastScore = 0f;
        Groups.bullet.intersect(b.x - joinRange, b.y - joinRange, 2f * joinRange, 2f * joinRange, e -> {
            if (!e.isAdded() || e == b) return;

            float dot = 0f;
            if (e.damage < maxDamage && e.team == b.team && (lastBullet == null || (
                e.type instanceof JoiningBulletType type && type.identifier == identifier &&
                Angles.within(b.rotation(), e.rotation(), joinCone)
            ) && (dot = b.vel.dot(e.vel)) >= minDot && lastScore < dot)) {
                lastBullet = e;
                lastScore = dot;
            }
        });

        // ===== 第 2 步: 更新聚合目标 =====
        JoinData data = (JoinData) b.data;
        if (lastBullet == null) {
            data.target = null;
        } else if (data.target == null) {
            data.target = lastBullet;
            data.rotation = b.rotation();
        } else {
            data.target = lastBullet;
        }

        // ===== 第 3 步: 有目标时吸引 / 碰撞聚合 =====
        Bullet t = data.target;
        if (t != null) {
            b.hitbox(Tmp.r1);
            t.hitbox(Tmp.r2);

            if (Tmp.r1.overlaps(Tmp.r2)) {
                // ★ 聚合: 两弹消失, 中点生成更大的新弹 (伤害 = max + min * yieldScl)
                Effect bd = despawnEffect, td = t.type.despawnEffect;
                despawnEffect = joinEffect;
                t.type.despawnEffect = joinEffect;

                b.remove();
                t.remove();
                despawnEffect = bd;
                t.type.despawnEffect = td;

                JoinData other = t.data instanceof JoinData d ? d : null;
                float bt = b.fout(), tt = t.fout();

                Bullet n = create(
                    b.owner == null ? t.owner : b.owner, b.team,
                    (b.x + t.x) / 2f, (b.y + t.y) / 2f,
                    Mathf.slerp(data.rotation, other != null ? other.rotation : t.rotation(), Mathf.clamp(t.vel.len() / b.vel.len() / 2f)),
                    Math.max(b.damage, t.damage) + Math.min(b.damage, t.damage) * yieldScl,
                    1f, Math.max(bt, tt) + Math.min(bt, tt) / 2f, null
                );
                n.hitSize *= bulletRadius(n);
            }

            // 聚合吸引: 向目标加速 (受 speed*2.5 限制)
            if (b.time >= joinDelay) {
                float len = b.vel.len();

                b.vel.add(Tmp.v1.set(t).sub(b).setLength(sensitivity * Time.delta * 0.3f));
                b.vel.limit(Math.max(len, speed * 2.5f));
            }
        } else if (homingPower > 0.0001f && b.time >= homingDelay) {
            // 无聚合目标时: 常规追踪
            Teamc target;
            if (healPercent > 0) {
                target = Units.closestTarget(null, b.x, b.y, homingRange,
                    e -> e.checkTarget(collidesAir, collidesGround) && e.team != b.team && !b.hasCollided(e.id()),
                    e -> collidesGround && (e.team != b.team || e.damaged()) && !b.hasCollided(e.id())
                );
            } else {
                target = Units.closestTarget(b.team, b.x, b.y, homingRange, e -> e.checkTarget(collidesAir, collidesGround) && !b.hasCollided(e.id()), e -> collidesGround && !b.hasCollided(e.id()));
            }

            if (target != null) {
                b.vel.setAngle(Angles.moveToward(b.rotation(), b.angleTo(target), homingPower * Time.delta * 50f));
            }
        }

        updateTrail(b);
        if (weaveMag > 0) {
            b.vel.rotate(Mathf.sin(b.time + Mathf.pi * weaveScale / 2f, weaveScale, weaveMag * (Mathf.randomSeed(b.id, 0, 1) == 1 ? -1 : 1)) * Time.delta);
        }

        if (trailChance > 0) {
            if (Mathf.chanceDelta(trailChance)) {
                trailEffect.at(b.x, b.y, trailRotation ? b.rotation() : (trailParam * bulletRadius(b)), trailColor);
            }
        }

        if (trailInterval > 0f) {
            if (b.timer(0, trailInterval)) {
                trailEffect.at(b.x, b.y, trailRotation ? b.rotation() : (trailParam * bulletRadius(b)), trailColor);
            }
        }
    }

    /** 聚合状态数据 (挂在 bullet.data 上) */
    protected static class JoinData {
        protected Bullet bullet;
        protected Bullet target;
        protected float rotation;
    }
}
