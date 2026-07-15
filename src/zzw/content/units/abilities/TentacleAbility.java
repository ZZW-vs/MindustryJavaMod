package zzw.content.units.abilities;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.Units;
import mindustry.gen.*;
import mindustry.type.UnitType;

/**
 * 鞭子触手 Ability (移植自 PU132 NewTentacle + TentacleComp)
 *
 * 简化为 Ability, 在 UnitType.abilities 中添加即可
 * 参考: PU132 NewTentacle.java, TentacleType.java
 *
 * 平滑机制 (避免抽搐):
 * 1. swayScl 用 lerpDelta 平滑过渡
 * 2. drag 速度衰减
 * 3. 双向链表两遍更新 (末端→根部 速度/位置, 根部→末端 角度约束)
 * 4. 角度限制 (angleLimit/firstSegmentAngleLimit)
 */
public class TentacleAbility extends Ability {
    private static final Vec2 tv = new Vec2(), tv2 = new Vec2();

    /** 触手贴图名称 (用于加载贴图, 会自动加 mod 前缀) */
    public String regionName;
    public TextureRegion region, tipRegion;

    // ===== 触手配置 (对应 PU132 TentacleType) =====
    public float x = 0f, y = 0f;           // 触手根位置 (相对单位中心)
    public float rotationOffset = 180f;    // 旋转偏移 (相对单位朝向)
    public int segments = 10;              // 段数
    public float segmentLength = 30f;       // 每段长度
    public float angleLimit = 65f;         // 中间段角度限制
    public float firstSegmentAngleLimit = 35f; // 第一段角度限制
    public float rotationSpeed = 3f;       // 末端旋转速度
    public float speed = 8f;               // 末端最大速度
    public float accel = 0.2f;             // 末端加速度
    public float drag = 0.06f;             // 速度衰减
    public float swayScl = 110f, swayMag = 0.6f, swayOffset = 0f, swaySegmentOffset = 1.5f;
    public boolean mirror = true;          // 是否镜像
    public boolean top = true;             // 是否画在单位上方
    public boolean flipSprite = false;

    // ===== 武器配置 =====
    public BulletType bullet;
    public float reload = 60f;
    public float range = 220f;
    public float shootCone = 15f;
    public boolean continuous = false;
    public float bulletDuration = -1f;
    public boolean automatic = false;     // 是否自动寻敌
    public float tentacleDamage = -1f;      // 碰撞伤害 (<0 不生效)

    // 运行时数据 (每个触手一组, mirror=true 时 size=2)
    private transient Seq<TentacleSeg[]> tentacles;
    private transient float[] reloadTimers;
    private transient Bullet[] bullets;
    private transient float[] swayScls;
    private transient boolean inited = false;

    public TentacleAbility(String regionName) {
        this.regionName = regionName;
    }

    @Override
    public void init(UnitType type) {
        super.init(type);
        if (regionName != null) {
            region = Core.atlas.find(regionName);
            tipRegion = Core.atlas.find(regionName + "-tip", region);
        }
    }

    @Override
    public void update(Unit unit) {
        super.update(unit);
        if (!inited) {
            initTentacles(unit);
            inited = true;
        }

        int count = mirror ? 2 : 1;
        for (int t = 0; t < count; t++) {
            float sideSign = t == 0 ? 1f : -1f;
            boolean flip = t == 1;
            TentacleSeg[] segs = tentacles.get(t);
            Position rootPos = getRootPos(unit, sideSign);

            // ★ 武器逻辑 (先更新武器, 确定 attacking 状态)
            boolean attacking = updateWeapon(unit, t, segs, sideSign);

            // ★ swayScl 平滑过渡 (避免攻击切换时抽搐)
            if (!attacking) {
                swayScls[t] = Mathf.lerpDelta(swayScls[t], 1f, 0.04f);
            } else {
                swayScls[t] = Mathf.lerpDelta(swayScls[t], 0f, 0.04f);
            }

            // ★ 双向链表两遍更新
            // 第一遍: 末端→根部, 更新速度/位置/摆动
            for (int s = segs.length - 1; s >= 0; s--) {
                TentacleSeg seg = segs[s];
                seg.updateLastPosition();

                // 速度限制 + drag 衰减
                tv.set(seg.vx, seg.vy).limit(speed);
                seg.vx = tv.x;
                seg.vy = tv.y;
                seg.x += seg.vx * Time.delta;
                seg.y += seg.vy * Time.delta;
                seg.vx *= 1f - (drag * Time.delta);
                seg.vy *= 1f - (drag * Time.delta);

                // 摆动
                if (swayScls[t] >= 0.0001f) {
                    float sin = swayScls[t] * Mathf.sin(Time.time + swayOffset + (s * swaySegmentOffset), swayScl, swayMag) * Mathf.sign(flipSprite != flip);
                    seg.rotation += sin;
                }
            }

            // 第二遍: 根部→末端, 角度约束 + 位置硬约束
            for (int s = 0; s < segs.length; s++) {
                TentacleSeg seg = segs[s];
                if (s == 0) {
                    // 第一段: 受 firstSegmentAngleLimit 约束
                    float parentAng = unit.rotation + rotationOffset * sideSign + 180f;
                    float ang = rootPos.angleTo(seg.x, seg.y);
                    seg.rotation = clampedAngle(ang, parentAng, firstSegmentAngleLimit);
                    tv.trns(seg.rotation, segmentLength).add(rootPos.getX(), rootPos.getY());
                } else {
                    // 其他段: 受 angleLimit 约束, 跟随前一段
                    TentacleSeg prev = segs[s - 1];
                    float childAng = prev.rotation;
                    float ang = prev.angleToSeg(seg);
                    seg.rotation = clampedAngle(ang, childAng, angleLimit);
                    tv.trns(seg.rotation, segmentLength).add(prev.x, prev.y);
                }
                seg.x = tv.x;
                seg.y = tv.y;
            }
        }
    }

    /** 武器逻辑, 返回是否正在攻击 */
    boolean updateWeapon(Unit unit, int t, TentacleSeg[] segs, float sideSign) {
        TentacleSeg end = segs[segs.length - 1];
        float ex = end.x, ey = end.y;

        // 寻敌
        Teamc target = null;
        boolean player = unit.isPlayer();
        if (automatic || !player) {
            target = Units.closestTarget(unit.team, ex, ey, range,
                u -> u.isValid() && u.team != unit.team,
                b -> b.team != unit.team);
        } else if (unit.isShooting()) {
            // 玩家手动瞄准
            target = unit;
        }

        boolean attacking = target != null || (player && unit.isShooting());

        if (bullet != null) {
            // 有子弹: 末端追踪目标
            if (target != null) {
                float tx = player ? unit.aimX() : target.getX();
                float ty = player ? unit.aimY() : target.getY();
                float ang = Angles.angle(ex, ey, tx, ty);
                end.rotation = Angles.moveToward(end.rotation, ang, rotationSpeed);

                // 开火条件: reload 满 + 朝向在 shootCone 内
                reloadTimers[t] += Time.delta * unit.reloadMultiplier;
                if (reloadTimers[t] >= reload && Angles.within(end.rotation, ang, shootCone)) {
                    Bullet b = bullet.create(unit, unit.team, ex, ey, end.rotation);
                    if (continuous) {
                        if (bulletDuration > 0) b.lifetime = bulletDuration;
                        bullets[t] = b;
                    }
                    reloadTimers[t] = 0f;
                }
            }

            // continuous 模式: 同步子弹位置
            if (continuous && bullets[t] != null) {
                Bullet b = bullets[t];
                if (b.type != bullet || !b.isAdded()) {
                    bullets[t] = null;
                } else {
                    b.set(ex, ey);
                    b.rotation(end.rotation);
                }
            }
        } else if (tentacleDamage > 0 && attacking) {
            // 无子弹: 碰撞伤害 (简化版, 每帧检测末端附近单位)
            Units.nearby(unit.team, ex, ey, 5f, other -> {
                if (other.team != unit.team && other.isValid()) {
                    other.damage(tentacleDamage * Time.delta);
                }
            });
        }

        return attacking;
    }

    @Override
    public void draw(Unit unit) {
        super.draw(unit);
        if (!inited || tentacles == null) return;

        float z = Draw.z();
        if (top) Draw.z(z + 0.01f);

        int count = mirror ? 2 : 1;
        for (int t = 0; t < count; t++) {
            float sideSign = t == 0 ? 1f : -1f;
            boolean flip = t == 1;
            TentacleSeg[] segs = tentacles.get(t);
            Position rootPos = getRootPos(unit, sideSign);

            // 画触手 (从根到末端)
            float prevX = rootPos.getX(), prevY = rootPos.getY();
            for (int s = 0; s < segs.length; s++) {
                TentacleSeg seg = segs[s];
                TextureRegion reg = (s == segs.length - 1 && tipRegion.found()) ? tipRegion : region;
                if (reg == null || !reg.found()) continue;
                // 末端位置从 prev 沿当前方向延伸 region.width
                tv.set(seg.x, seg.y).sub(prevX, prevY).setLength(reg.width * Draw.scl).add(prevX, prevY);
                unit.type.applyColor(unit);
                Lines.stroke(reg.height * Draw.scl * Mathf.sign(flipSprite != flip));
                Lines.line(reg, prevX, prevY, tv.x, tv.y, false);
                prevX = tv.x;
                prevY = tv.y;
            }
        }

        Draw.reset();
        Draw.z(z);
    }

    private void initTentacles(Unit unit) {
        int count = mirror ? 2 : 1;
        tentacles = new Seq<>(count);
        reloadTimers = new float[count];
        bullets = new Bullet[count];
        swayScls = new float[count];
        for (int i = 0; i < count; i++) swayScls[i] = 1f;

        for (int t = 0; t < count; t++) {
            float sideSign = t == 0 ? 1f : -1f;
            TentacleSeg[] segs = new TentacleSeg[segments];
            Position rootPos = getRootPos(unit, sideSign);
            tv.trns(unit.rotation + rotationOffset * sideSign + 180f, segmentLength);

            for (int i = 0; i < segments; i++) {
                TentacleSeg seg = new TentacleSeg();
                int s = (i + 1);
                seg.x = rootPos.getX() + (tv.x * s);
                seg.y = rootPos.getY() + (tv.y * s);
                seg.rotation = unit.rotation + rotationOffset * sideSign + 180f;
                segs[i] = seg;
            }
            tentacles.add(segs);
        }
    }

    private Position getRootPos(Unit unit, float sideSign) {
        return Tmp.v1.trns(unit.rotation - 90f, x * sideSign, y).add(unit);
    }

    /** 角度限制: 将 ang 限制在 baseAng ± limit 范围内 (正确判断方向, 避免抽搐) */
    private float clampedAngle(float ang, float baseAng, float limit) {
        // 归一化角度差到 -180..180, 正确判断顺/逆时针
        float delta = ang - baseAng;
        delta = ((delta % 360f) + 540f) % 360f - 180f;  // -180..180
        if (Math.abs(delta) > limit) {
            delta = Mathf.clamp(delta, -limit, limit);
        }
        return baseAng + delta;
    }

    private static class TentacleSeg {
        float lx, ly;  // 上一帧位置
        float x, y, rotation;
        float vx, vy;

        void updateLastPosition() {
            lx = x;
            ly = y;
        }

        float len() {
            return Mathf.len(x - lx, y - ly);
        }

        float angleToSeg(TentacleSeg other) {
            return Angles.angle(x, y, other.x, other.y);
        }
    }
}
