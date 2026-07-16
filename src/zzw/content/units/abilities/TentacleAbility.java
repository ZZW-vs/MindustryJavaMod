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
 * 鞭子触手 Ability (完整移植 PU132 NewTentacle)
 *
 * 机制:
 * 1. updateMovement: attacking 时末端主动追踪目标
 *    - 有bullet: 远程分支, 末端追踪目标位置
 *    - 无bullet: stab刺击分支, stab=true冲刺/stab=false蓄力两阶段循环
 * 2. 第一遍 (末端→根部): 速度积分位置 + drag衰减 + 摆动 + child同步
 * 3. 第二遍 (根部→末端): 位置约束 + 角度限制 (clampedAngle)
 * 4. updateWeapon: 目标选择 + 射击 + stab伤害判定
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
    // ★ 减小摆动幅度 (默认0.6太大导致波浪线, 0.08让鞭子更硬更稳定)
    public float swayScl = 110f, swayMag = 0.08f, swayOffset = 0f, swaySegmentOffset = 1.5f;
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

    // 运行时数据 (每条触手一组)
    private transient Seq<TentacleSeg[]> tentacles;
    private transient float[] reloadTimers;
    private transient Bullet[] bullets;
    private transient float[] swayScls;
    private transient boolean[] attacking;
    private transient boolean[] stab;
    private transient float[] attackTimes;
    private transient float[] stabTimes;
    private transient float[] retargets;
    private transient Vec2[] stabStarts;     // (alx, aly) stab射线起点
    private transient Teamc[] targets;
    private transient float[] targetXs, targetYs;
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

            // ===== PU_V8 NewTentacle.update() 流程 =====
            // 1. swayScl 过渡 + updateMovement (如果 attacking, 用上一帧的 attacking 状态)
            if (!attacking[t]) {
                swayScls[t] = Mathf.lerpDelta(swayScls[t], 1f, 0.04f);
            } else {
                swayScls[t] = Mathf.lerpDelta(swayScls[t], 0f, 0.04f);
                updateMovement(t, segs, sideSign, rootPos);
            }

            // 2. 第一遍: 末端→根部, 速度积分 + drag + 摆动 + child 同步
            //    所有段都积分速度 (无特殊处理, 与 PU_V8 一致)
            for (int s = segs.length - 1; s >= 0; s--) {
                TentacleSeg seg = segs[s];
                seg.updateLastPosition();

                tv.set(seg.vx, seg.vy).limit(speed);
                seg.vx = tv.x;
                seg.vy = tv.y;
                seg.x += seg.vx * Time.delta;
                seg.y += seg.vy * Time.delta;

                seg.vx *= 1f - (drag * Time.delta);
                seg.vy *= 1f - (drag * Time.delta);

                if (swayScls[t] >= 0.0001f) {
                    int swayIdx = segs.length - 1 - s;
                    float sin = swayScls[t] * Mathf.sin(Time.time + swayOffset + (swayIdx * swaySegmentOffset), swayScl, swayMag) * Mathf.sign(flipSprite != flip);
                    seg.rotation += sin;
                }

                if (s > 0) {
                    TentacleSeg child = segs[s - 1];
                    float cx = Angles.trnsx(child.rotation + 180f, segmentLength) + child.x;
                    float cy = Angles.trnsy(child.rotation + 180f, segmentLength) + child.y;
                    float sx = Angles.trnsx(seg.rotation + 180f, segmentLength) + seg.x;
                    float sy = Angles.trnsy(seg.rotation + 180f, segmentLength) + seg.y;

                    child.rotation = Angles.angle(cx, cy, sx, sy);
                    float ang = angleDistSigned(seg.rotation, child.rotation, angleLimit);
                    child.rotation += ang;

                    child.x = sx;
                    child.y = sy;
                }
            }

            // 3. 第二遍: 根部→末端, 位置约束 + 角度限制
            //    所有段都执行 clampedAngle (无 skipRotOverride, 与 PU_V8 一致)
            //    末端 rotation 受 child 约束, 但位置已由 updateMovement 朝目标移动
            for (int s = 0; s < segs.length; s++) {
                TentacleSeg seg = segs[s];
                if (s == 0) {
                    float parentAng = unit.rotation + rotationOffset * sideSign + 180f;
                    float ang = rootPos.angleTo(seg.x, seg.y);
                    seg.rotation = clampedAngle(ang, parentAng, firstSegmentAngleLimit);
                    tv.trns(seg.rotation, segmentLength).add(rootPos.getX(), rootPos.getY());
                } else {
                    TentacleSeg prev = segs[s - 1];
                    float childAng = prev.rotation;
                    float ang = prev.angleToSeg(seg);
                    seg.rotation = clampedAngle(ang, childAng, angleLimit);
                    tv.trns(seg.rotation, segmentLength).add(prev.x, prev.y);
                }
                seg.x = tv.x;
                seg.y = tv.y;
            }

            // 4. updateWeapon (设置下一帧的 attacking 状态)
            attacking[t] = updateWeapon(unit, t, segs, sideSign, rootPos);

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

    /** 末端追踪目标 (attacking 时调用) */
    void updateMovement(int t, TentacleSeg[] segs, float sideSign, Position rootPos) {
        TentacleSeg end = segs[segs.length - 1];
        float tx = targetXs[t], ty = targetYs[t];
        // prevPos = 前一段位置 (end 的 child)
        float prevX = segs.length > 1 ? segs[segs.length - 2].x : rootPos.getX();
        float prevY = segs.length > 1 ? segs[segs.length - 2].y : rootPos.getY();

        end.updateLastPosition();

        if (bullet != null) {
            // ===== 有bullet分支: 远程追踪 =====
            // 虚拟目标点 = 从target退range/3的点 (让触手不要顶到目标脸上)
            tv2.set(end.x, end.y).sub(tx, ty).setLength(range / 3f).add(tx, ty);
            if (!tv2.isNaN()) {
                tv.set(tv2).sub(end.x, end.y).scl(1f / 40f).limit(speed);
                float ang = Angles.angle(end.x, end.y, tx, ty);
                end.rotation = Angles.moveToward(end.rotation, ang, rotationSpeed);
                float scl = Mathf.clamp((90f - Angles.angleDist(end.rotation, ang)) / 90f, 0.7f, 1f);

                // 位置 = prevPos + trns(end.rotation, segmentLength)
                tv2.trns(end.rotation, segmentLength).add(prevX, prevY);
                end.x = tv2.x;
                end.y = tv2.y;

                // 速度累加
                end.vx += tv.x * scl * accel;
                end.vy += tv.y * scl * accel;
            }
        } else {
            // ===== 无bullet分支: stab刺击 =====
            // 外推目标点 (让触手伸得更远)
            tv.set(tx, ty).sub(rootPos.getX(), rootPos.getY()).scl(2f).add(rootPos.getX(), rootPos.getY());
            float vx = tv.x, vy = tv.y;
            float ang = Angles.angle(end.x, end.y, vx, vy);
            float scl = Mathf.clamp(Math.abs(90f - Angles.angleDist(end.rotation, ang)) / 90f, 0.7f, 1f);

            if (stab[t]) {
                // stab=true: 末端高速冲向目标
                tv.set(vx, vy).sub(end.x, end.y).limit(speed);
                end.rotation = Angles.moveToward(end.rotation, ang, rotationSpeed);
                tv2.trns(end.rotation, segmentLength).add(prevX, prevY);
                end.x = tv2.x;
                end.y = tv2.y;
                end.vx += tv.x * scl * accel;
                end.vy += tv.y * scl * accel;

                attackTimes[t] += Time.delta;
                if (attackTimes[t] >= 80f) {
                    attackTimes[t] = 0f;
                    stab[t] = false;
                }
            } else {
                // stab=false: 蓄力阶段, 末端在目标附近徘徊
                stabStarts[t].set(end.x, end.y);
                tv2.set(tx, ty).sub(rootPos.getX(), rootPos.getY()).setLength(range / 5f).add(rootPos.getX(), rootPos.getY());
                if (!tv2.isNaN()) {
                    tv.set(tv2).sub(end.x, end.y).scl(1f / 25f).limit(speed);
                    end.rotation = Angles.moveToward(end.rotation, ang, rotationSpeed);
                    tv2.trns(end.rotation, segmentLength).add(prevX, prevY);
                    end.x = tv2.x;
                    end.y = tv2.y;
                    end.vx += tv.x * scl * accel;
                    end.vy += tv.y * scl * accel;
                }

                attackTimes[t] += Time.delta;
                if (attackTimes[t] >= 80f) {
                    targets[t] = null;
                    attackTimes[t] = 0f;
                    stab[t] = true;
                }
            }
        }
    }

    /** 武器逻辑: 目标选择 + 射击 + stab伤害, 返回是否正在攻击 */
    boolean updateWeapon(Unit unit, int t, TentacleSeg[] segs, float sideSign, Position rootPos) {
        TentacleSeg end = segs[segs.length - 1];
        float ex = end.x, ey = end.y;
        boolean player = unit.isPlayer();

        // ===== 目标选择 =====
        if (automatic || !player) {
            if (targets[t] == null && (retargets[t] += Time.delta) >= 20f) {
                float rootRange = (segments * segmentLength) + (bullet != null ? bullet.range * 0.75f : 0f);
                targets[t] = Units.closestTarget(unit.team, ex, ey, range,
                    u -> u.isValid() && u.team != unit.team && rootPos.within(u, rootRange),
                    b -> b.team != unit.team && rootPos.within(b, rootRange));
                retargets[t] = 0f;
            }
            if (targets[t] != null) {
                targetXs[t] = targets[t].getX();
                targetYs[t] = targets[t].getY();
            }
        } else if (unit.isShooting()) {
            targetXs[t] = unit.aimX();
            targetYs[t] = unit.aimY();
        }

        // 目标失效检查
        if (targets[t] != null) {
            float rootRange = (segments * segmentLength) + (bullet != null ? bullet.range * 0.75f : 0f);
            if (Units.invalidateTarget(targets[t], unit.team, rootPos.getX(), rootPos.getY(), rootRange) || (player && !automatic)) {
                targets[t] = null;
            }
        }

        boolean isAttacking = targets[t] != null || (player && unit.isShooting());

        // ===== 射击 =====
        if (bullets[t] == null && bullet != null) reloadTimers[t] += Time.delta * unit.reloadMultiplier;

        if (isAttacking && bullet != null && reloadTimers[t] >= reload
            && Angles.within(end.rotation, Angles.angle(ex, ey, targetXs[t], targetYs[t]), shootCone)) {
            Bullet b = bullet.create(unit, unit.team, ex, ey, end.rotation);
            if (continuous) {
                if (bulletDuration > 0) b.lifetime = bulletDuration;
                bullets[t] = b;
            }
            reloadTimers[t] = 0f;
        }

        // continuous 子弹同步
        if (continuous) {
            if (bullets[t] != null && (bullets[t].type != bullet || !bullets[t].isAdded())) bullets[t] = null;
            if (bullets[t] != null) {
                bullets[t].set(end.x, end.y);
                bullets[t].rotation(end.rotation);
            }
        }

        // ===== stab 伤害判定 (无bullet触手) =====
        if (bullets[t] == null && isAttacking && tentacleDamage > 0 && end.len() > 0.2f) {
            if (stab[t]) {
                stabTimes[t] += Time.delta;
                if (stabTimes[t] >= 5f) {
                    // 沿 (stabStart -> end) 射线检测伤害
                    float sx = stabStarts[t].x, sy = stabStarts[t].y;
                    Units.nearby(unit.team, sx, sy, Math.abs(ex - sx) + Math.abs(ey - sy) + 10f, other -> {
                        if (other.team != unit.team && other.isValid()) {
                            // 简化: 检测单位到线段的距离
                            float dist = pointToLineDist(other.x, other.y, sx, sy, ex, ey);
                            if (dist < 5f) {
                                other.damage(tentacleDamage);
                            }
                        }
                    });
                    stabStarts[t].set(end.x, end.y);
                    stabTimes[t] = 0f;
                }
            } else {
                stabStarts[t].set(end.x, end.y);
            }
        }

        return isAttacking;
    }

    @Override
    public void draw(Unit unit) {
        super.draw(unit);
        if (!inited || tentacles == null) return;

        float z = Draw.z();

        int count = mirror ? 2 : 1;
        for (int t = 0; t < count; t++) {
            float sideSign = t == 0 ? 1f : -1f;
            boolean flip = t == 1;
            TentacleSeg[] segs = tentacles.get(t);
            Position rootPos = getRootPos(unit, sideSign);

            float prevX = rootPos.getX(), prevY = rootPos.getY();
            for (int s = 0; s < segs.length; s++) {
                TentacleSeg seg = segs[s];
                // ★ PU_V8: if(type.top && cur != root) Draw.z(z + 0.01001f);
                // 第一节(root, s==0)画在身体下方(默认层级), 其他节画在身体上方
                if (top && s > 0) {
                    Draw.z(z + 0.01001f);
                } else {
                    Draw.z(z);
                }
                TextureRegion reg = (s == segs.length - 1 && tipRegion.found()) ? tipRegion : region;
                if (reg == null || !reg.found()) continue;
                unit.type.applyColor(unit);
                Lines.stroke(reg.height * Draw.scl * Mathf.sign(flipSprite != flip));
                Lines.line(reg, prevX, prevY, seg.x, seg.y, false);
                prevX = seg.x;
                prevY = seg.y;
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
        stab = new boolean[count];
        attackTimes = new float[count];
        stabTimes = new float[count];
        retargets = new float[count];
        stabStarts = new Vec2[count];
        targets = new Teamc[count];
        targetXs = new float[count];
        targetYs = new float[count];
        for (int i = 0; i < count; i++) {
            swayScls[i] = 1f;
            stab[i] = true;
            stabStarts[i] = new Vec2();
        }

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

    // ===== PU132 角度工具方法 =====

    /** 有符号角度差 (-180..180) */
    private static float angleDistSigned(float a, float b) {
        a = ((a % 360f) + 360f) % 360f;
        b = ((b % 360f) + 360f) % 360f;
        float d = Math.abs(a - b) % 360f;
        int sign = (a - b >= 0f && a - b <= 180f) || (a - b <= -180f && a - b >= -360f) ? 1 : -1;
        return (d > 180f ? 360f - d : d) * sign;
    }

    /** 带起始区的有符号角度差: 超过 start 才返回超出部分 */
    private static float angleDistSigned(float a, float b, float start) {
        float dst = angleDistSigned(a, b);
        if (Math.abs(dst) > start) {
            return dst > 0 ? dst - start : dst + start;
        }
        return 0f;
    }

    /** PU132 clampedAngle: 将 angle 限制在 relative ± limit 范围内 */
    private static float clampedAngle(float angle, float relative, float limit) {
        if (limit >= 180f) return angle;
        if (limit <= 0f) return relative;
        float dst = angleDistSigned(angle, relative);
        if (Math.abs(dst) > limit) {
            float val = dst > 0 ? dst - limit : dst + limit;
            return (angle - val) % 360f;
        }
        return angle;
    }

    /** 点到线段距离 */
    private static float pointToLineDist(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float len2 = dx * dx + dy * dy;
        if (len2 < 0.0001f) return Mathf.dst(px, py, x1, y1);
        float t = Mathf.clamp(((px - x1) * dx + (py - y1) * dy) / len2, 0f, 1f);
        return Mathf.dst(px, py, x1 + dx * t, y1 + dy * t);
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
