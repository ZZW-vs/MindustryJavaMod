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
 * 简化版:
 * - 触手由多段组成, 每段通过关节角度限制连接
 * - 跟随单位身后, 有摆动动画
 * - 可以发射激光 (bullet 配置时)
 *
 * PU132 原版用 Tentaclec 组件 + NewTentacle 类, 需要修改 Entity 类
 * v158 简化为 Ability, 在 UnitType.abilities 中添加即可
 *
 * 参考: PU132 NewTentacle.java, TentacleType.java
 */
public class TentacleAbility extends Ability {
    private static final Vec2 tv = new Vec2(), tv2 = new Vec2();

    /** 触手贴图 (单段) */
    public TextureRegion region, tipRegion;
    /** 触手名称 (用于加载贴图) */
    public String regionName;
    /** 触手根位置 (相对单位中心, 单位旋转前) */
    public float x = 0f, y = 0f;
    /** 旋转偏移 (相对单位朝向) */
    public float rotationOffset = 180f;
    /** 段数 */
    public int segments = 10;
    /** 每段长度 */
    public float segmentLength = 30f;
    /** 关节角度限制 (度) */
    public float angleLimit = 30f;
    /** 第一段角度限制 */
    public float firstSegmentAngleLimit = 17f;
    /** 旋转速度 */
    public float rotationSpeed = 3f;
    /** 摆动参数 */
    public float swayScl = 120f, swayMag = 0.2f, swayOffset = 0f;
    /** 是否镜像 (true=两侧都有) */
    public boolean mirror = true;
    /** 是否画在单位上方 */
    public boolean top = true;
    /** 武器相关 */
    public BulletType bullet;
    public float reload = 3f * 60f;
    public float range = 220f;
    public float shootCone = 15f;
    public boolean continuous = false;
    public float bulletDuration = 1.5f * 60f;

    // 运行时数据
    private transient Seq<TentacleSegment[]> tentacles;
    private transient float[] reloadTimers;
    private transient Bullet[] bullets;

    public TentacleAbility(String regionName) {
        this.regionName = regionName;
    }

    @Override
    public void update(Unit unit) {
        super.update(unit);
        if (tentacles == null || tentacles.size == 0) {
            initTentacles(unit);
        }

        int count = mirror ? 2 : 1;
        for (int t = 0; t < count; t++) {
            float sideSign = t == 0 ? 1f : -1f;
            TentacleSegment[] segs = tentacles.get(t);
            Position rootPos = getRootPos(unit, sideSign);

            // 更新摆动
            float swaySclNow = 1f; // 简化: 总是摆动

            // 从末端向根部更新
            for (int i = 0; i < 2; i++) {
                if (i == 0) {
                    // 从末端到根部: 更新位置和旋转
                    for (int s = segs.length - 1; s >= 0; s--) {
                        TentacleSegment seg = segs[s];
                        // 摆动
                        if (swaySclNow >= 0.0001f) {
                            float sin = swaySclNow * Mathf.sin(Time.time + swayOffset + (s * 1.5f), swayScl, swayMag);
                            seg.rotation += sin;
                        }
                        // 关节约束
                        if (s == 0) {
                            // 第一段: 受 firstSegmentAngleLimit 约束
                            float parentAng = unit.rotation + rotationOffset * sideSign + 180f;
                            float ang = rootPos.angleTo(seg.x, seg.y);
                            seg.rotation = clampedAngle(ang, parentAng, firstSegmentAngleLimit);
                            tv.trns(seg.rotation, segmentLength).add(rootPos.getX(), rootPos.getY());
                        } else {
                            // 其他段: 受 angleLimit 约束, 跟随前一段
                            float childAng = segs[s - 1].rotation;
                            float ang = segs[s - 1].angleToSeg(seg);
                            seg.rotation = clampedAngle(ang, childAng, angleLimit);
                            tv.trns(seg.rotation, segmentLength).add(segs[s - 1].x, segs[s - 1].y);
                        }
                        seg.x = tv.x;
                        seg.y = tv.y;
                    }
                }
            }

            // 武器逻辑
            if (bullet != null) {
                updateWeapon(unit, t, segs, sideSign);
            }
        }
    }

    void updateWeapon(Unit unit, int t, TentacleSegment[] segs, float sideSign) {
        TentacleSegment end = segs[segs.length - 1];
        float ex = end.x, ey = end.y;

        // 找目标
        Teamc target = Units.closestTarget(unit.team, ex, ey, range,
            u -> u.isValid() && u.team != unit.team,
            b -> b.team != unit.team);

        if (target != null) {
            float tx = target.getX(), ty = target.getY();
            float ang = Angles.angle(ex, ey, tx, ty);
            end.rotation = Angles.moveToward(end.rotation, ang, rotationSpeed);

            // 更新末端段位置
            tv.trns(end.rotation, segmentLength).add(segs[segs.length - 2].x, segs[segs.length - 2].y);
            end.x = tv.x;
            end.y = tv.y;

            // 开火
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
    }

    @Override
    public void draw(Unit unit) {
        super.draw(unit);
        if (tentacles == null || tentacles.size == 0) return;

        float z = Draw.z();
        if (top) Draw.z(z + 0.01f);

        int count = mirror ? 2 : 1;
        for (int t = 0; t < count; t++) {
            float sideSign = t == 0 ? 1f : -1f;
            TentacleSegment[] segs = tentacles.get(t);
            Position rootPos = getRootPos(unit, sideSign);

            // 画触手 (从根到末端)
            float prevX = rootPos.getX(), prevY = rootPos.getY();
            for (int s = 0; s < segs.length; s++) {
                TentacleSegment seg = segs[s];
                TextureRegion reg = s == segs.length - 1 && tipRegion.found() ? tipRegion : region;
                if (reg == null || !reg.found()) continue;
                tv.set(seg.x, seg.y).sub(prevX, prevY).setLength(reg.width * Draw.scl).add(prevX, prevY);
                unit.type.applyColor(unit);
                Lines.stroke(reg.height * Draw.scl * Mathf.sign(sideSign > 0));
                Lines.line(reg, prevX, prevY, tv.x, tv.y, false);
                prevX = tv.x;
                prevY = tv.y;
            }
        }

        Draw.reset();
        Draw.z(z);
    }

    @Override
    public void init(UnitType type) {
        super.init(type);
        if (regionName != null) {
            region = Core.atlas.find(regionName);
            tipRegion = Core.atlas.find(regionName + "-tip", region);
        }
    }

    private void initTentacles(Unit unit) {
        int count = mirror ? 2 : 1;
        tentacles = new Seq<>(count);
        reloadTimers = new float[count];
        bullets = new Bullet[count];

        for (int t = 0; t < count; t++) {
            float sideSign = t == 0 ? 1f : -1f;
            TentacleSegment[] segs = new TentacleSegment[segments];
            Position rootPos = getRootPos(unit, sideSign);
            tv.trns(unit.rotation + rotationOffset * sideSign + 180f, segmentLength);

            for (int i = 0; i < segments; i++) {
                TentacleSegment seg = new TentacleSegment();
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
        float diff = Angles.angleDist(baseAng, ang);
        float signed = ang > baseAng ? diff : -diff;
        if (Math.abs(signed) > limit) {
            signed = Mathf.sign(signed) * limit;
        }
        return baseAng + signed;
    }

    private static class TentacleSegment {
        float x, y, rotation;

        float angleToSeg(TentacleSegment other) {
            return Angles.angle(x, y, other.x, other.y);
        }
    }
}
