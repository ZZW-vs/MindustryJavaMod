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
 * 鞭子触手 Ability (移植自 PU132 NewTentacle)
 *
 * 简化为 Ability, 在 UnitType.abilities 中添加即可
 * 参考: PU132 NewTentacle.java, TentacleType.java
 *
 * 平滑机制:
 * 1. 非攻击时只摆动, 不主动移动末端 (避免抽搐)
 * 2. drag 速度衰减
 * 3. 双向链表两遍更新 (末端→根部 摆动, 根部→末端 角度约束+位置硬约束)
 * 4. 角度限制 (angleLimit/firstSegmentAngleLimit)
 */
public class TentacleAbility extends Ability {
    private static final Vec2 tv = new Vec2(), tv2 = new Vec2();

    public String regionName;
    public TextureRegion region, tipRegion;

    // ===== 触手配置 =====
    public float x = 0f, y = 0f;
    public float rotationOffset = 180f;
    public int segments = 10;
    public float segmentLength = 30f;
    public float angleLimit = 65f;
    public float firstSegmentAngleLimit = 35f;
    public float rotationSpeed = 3f;
    public float speed = 8f;
    public float accel = 0.2f;
    public float drag = 0.06f;
    public float swayScl = 110f, swayMag = 0.6f, swayOffset = 0f, swaySegmentOffset = 1.5f;
    public boolean mirror = true;
    public boolean top = true;
    public boolean flipSprite = false;

    // ===== 武器配置 =====
    public BulletType bullet;
    public float reload = 60f;
    public float range = 220f;
    public float shootCone = 15f;
    public boolean continuous = false;
    public float bulletDuration = -1f;
    public boolean automatic = false;
    public float tentacleDamage = -1f;

    // 运行时数据
    private transient Seq<TentacleSeg[]> tentacles;
    private transient float[] reloadTimers;
    private transient Bullet[] bullets;
    private transient float[] swayScls;
    private transient boolean[] attacking;
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
            TentacleSeg end = segs[segs.length - 1];

            // ===== 武器逻辑 (确定 attacking 状态 + 末端追踪) =====
            attacking[t] = updateWeapon(unit, t, segs, sideSign, rootPos);

            // swayScl 平滑过渡
            if (!attacking[t]) {
                swayScls[t] = Mathf.lerpDelta(swayScls[t], 1f, 0.04f);
            } else {
                swayScls[t] = Mathf.lerpDelta(swayScls[t], 0f, 0.04f);
            }

            // ===== 第一遍: 末端→根部, 摆动 + 速度衰减 =====
            // ★ 非攻击时不主动移动位置, 只加摆动旋转
            // ★ 末端在 attacking 时不摆动 (保持朝向目标)
            for (int s = segs.length - 1; s >= 0; s--) {
                TentacleSeg seg = segs[s];
                seg.updateLastPosition();

                // drag 衰减速度
                seg.vx *= 1f - (drag * Time.delta);
                seg.vy *= 1f - (drag * Time.delta);

                // 摆动 (非攻击时摆动强, 攻击时摆动弱)
                // ★ 末端 attacking 时跳过摆动, 保持朝向目标
                // ★ 摆动索引从末端开始 (末端=0), 波浪从末端向根部传播 (与 PU132 一致)
                if (!(s == segs.length - 1 && attacking[t]) && swayScls[t] >= 0.0001f) {
                    int swayIdx = segs.length - 1 - s;
                    float sin = swayScls[t] * Mathf.sin(Time.time + swayOffset + (swayIdx * swaySegmentOffset), swayScl, swayMag) * Mathf.sign(flipSprite != flip);
                    seg.rotation += sin;
                }
            }

            // ===== 第二遍: 根部→末端, 角度约束 + 位置硬约束 =====
            // ★ 位置完全由角度约束决定, 不用速度直接改位置 (避免抽搐)
            // ★ 末端在 attacking 时不修改 rotation (保持 updateWeapon 设置的目标朝向)
            for (int s = 0; s < segs.length; s++) {
                TentacleSeg seg = segs[s];
                if (s == 0) {
                    // 根段: 约束到 unit
                    float parentAng = unit.rotation + rotationOffset * sideSign + 180f;
                    float ang = rootPos.angleTo(seg.x, seg.y);
                    seg.rotation = clampedAngle(ang, parentAng, firstSegmentAngleLimit);
                    tv.trns(seg.rotation, segmentLength).add(rootPos.getX(), rootPos.getY());
                } else {
                    TentacleSeg prev = segs[s - 1];
                    if (s == segs.length - 1 && attacking[t]) {
                        // ★ 末端 attacking 时: 不修改 rotation, 只修改位置
                        // end.rotation 已由 updateWeapon 设置朝向目标, 保持直线指向目标
                        tv.trns(seg.rotation, segmentLength).add(prev.x, prev.y);
                    } else {
                        // 中间段或非攻击末端: 角度约束 + 位置约束
                        float childAng = prev.rotation;
                        float ang = prev.angleToSeg(seg);
                        seg.rotation = clampedAngle(ang, childAng, angleLimit);
                        tv.trns(seg.rotation, segmentLength).add(prev.x, prev.y);
                    }
                }
                seg.x = tv.x;
                seg.y = tv.y;
            }

            // continuous 模式: 同步子弹位置到末端
            if (continuous && bullets[t] != null) {
                Bullet b = bullets[t];
                if (b.type != bullet || !b.isAdded()) {
                    bullets[t] = null;
                } else {
                    b.set(end.x, end.y);
                    b.rotation(end.rotation);
                }
            }
        }
    }

    /** 武器逻辑, 返回是否正在攻击 */
    boolean updateWeapon(Unit unit, int t, TentacleSeg[] segs, float sideSign, Position rootPos) {
        TentacleSeg end = segs[segs.length - 1];
        float ex = end.x, ey = end.y;
        boolean player = unit.isPlayer();

        // 寻找目标
        Teamc target = null;
        float tx = ex, ty = ey;
        if (automatic || !player) {
            if (target == null) {
                target = Units.closestTarget(unit.team, ex, ey, range,
                    u -> u.isValid() && u.team != unit.team,
                    b -> b.team != unit.team);
            }
            if (target != null) {
                tx = target.getX();
                ty = target.getY();
            }
        } else if (unit.isShooting()) {
            tx = unit.aimX();
            ty = unit.aimY();
        }

        boolean isAttacking = target != null || (player && unit.isShooting());

        if (isAttacking) {
            // ★ 末端旋转朝向目标 (无论有无 bullet, 包括碰撞伤害型触手)
            float ang = Angles.angle(ex, ey, tx, ty);
            end.rotation = Angles.moveToward(end.rotation, ang, rotationSpeed);

            // 开火条件: reload 满 + 朝向在 shootCone 内
            if (bullet != null) {
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
        }

        // 碰撞伤害 (无子弹触手)
        if (tentacleDamage > 0 && isAttacking) {
            Units.nearby(unit.team, ex, ey, 5f, other -> {
                if (other.team != unit.team && other.isValid()) {
                    other.damage(tentacleDamage * Time.delta);
                }
            });
        }

        return isAttacking;
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

            float prevX = rootPos.getX(), prevY = rootPos.getY();
            for (int s = 0; s < segs.length; s++) {
                TentacleSeg seg = segs[s];
                TextureRegion reg = (s == segs.length - 1 && tipRegion.found()) ? tipRegion : region;
                if (reg == null || !reg.found()) continue;
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
        attacking = new boolean[count];
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

    /** 角度限制: 将 ang 限制在 baseAng ± limit 范围内 */
    private float clampedAngle(float ang, float baseAng, float limit) {
        float delta = ang - baseAng;
        delta = ((delta % 360f) + 540f) % 360f - 180f;
        if (Math.abs(delta) > limit) {
            delta = Mathf.clamp(delta, -limit, limit);
        }
        return baseAng + delta;
    }

    private static class TentacleSeg {
        float lx, ly;
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
