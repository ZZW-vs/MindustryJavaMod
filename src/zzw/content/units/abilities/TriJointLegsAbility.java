package zzw.content.units.abilities;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Legsc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.world.blocks.environment.Floor;
import zzw.content.units.entities.TriJointLeg;
import zzw.content.units.util.TriJointInverseKinematics;

/**
 * 三节腿系统 Ability (完整移植 PU_V8 unity.entities.comp.TriJointLegsComp)
 *
 * 挂载到单位后, 完全接管三节腿 (3 关节) 的:
 * - 步态逻辑 (moveSpace/totalLength/stage, 分组交替移动)
 * - IK 解算 (调用 TriJointInverseKinematics.solve)
 * - 跨帧平滑 (jointLerp / legScl)
 * - 渲染 (drawLegs, 由 UnitType.drawLegs 委托调用)
 *
 * 单位仍使用 LegsUnit::create 构造 (保持 Legsc 接口 + legsSolid 碰撞),
 * 但原生腿位置不参与渲染, 仅用于碰撞和地面交互. 渲染完全由本类负责.
 *
 * PU_V8 中 v7 UnitType 拥有的字段 (maxStretch / legTrns / landShake) 在 v158 中
 * 已被移除或改名, 因此作为本 Ability 的可配置字段重新引入, 默认值取 PU132 原版.
 *
 * 参考: PU_V8 TriJointLegsComp (update), UnityUnitType.drawTriLegs, TriJointInverseKinematics
 */
public class TriJointLegsAbility extends Ability {
    private static final Vec2 legOffsetB = new Vec2();
    private static final float[][] jointOffsets = new float[2][2];

    // ===== PU132 配置参数 (v158 UnitType 中缺失的字段, 重新引入) =====
    /** 腿最大伸缩比例 (PU132 UnitType 默认 1.75f) */
    public float maxStretch = 1.75f;
    /** 腿前向偏移系数 (PU132 UnitType 默认 1f, 控制脚相对腿根的前向偏移) */
    public float legTrns = 1f;
    /** 脚落地震屏强度 (PU132 UnitType 默认 0) */
    public float landShake = 0f;

    // ===== 渲染用中段贴图 (PU132 三节腿特有, v158 UnitType 不加载) =====
    public TextureRegion legMiddleRegion;

    // ===== 运行时状态 (每个单位独立) =====
    private transient TriJointLeg[] legs;
    private transient float baseRotation;
    private transient float moveSpace;
    private transient float totalLength;
    private transient boolean inited = false;

    public TriJointLegsAbility() {}

    @Override
    public void init(UnitType type) {
        super.init(type);
        // 加载中段腿贴图, 不存在时退化为 legRegion (与 PU_V8 行为一致)
        legMiddleRegion = Core.atlas.find(type.name + "-leg-middle", type.legRegion);
    }

    @Override
    public void update(Unit unit) {
        super.update(unit);
        if (!inited) {
            baseRotation = unit.rotation;
            resetLegs(unit);
            inited = true;
        }
        updateLegs(unit);
    }

    /** Ability.draw 为空操作, 腿渲染由 UnitType.drawLegs() 委托 drawLegs() 完成 (确保在 body 之前) */
    @Override
    public void draw(Unit unit) {}

    // ===== 公开访问器 (供 UnitType 渲染时使用) =====
    public TriJointLeg[] legs() {
        return legs;
    }

    public float baseRotation() {
        return baseRotation;
    }

    /** 计算指定索引腿的外朝向角度 (与 PU_V8 legAngle / v158 defaultLegAngle 公式一致) */
    public float legAngle(float rotation, int index) {
        return rotation + (360f / legs.length * index + (360f / legs.length / 2f));
    }

    // ===== 渲染: 完整移植 UnityUnitType.drawTriLegs =====
    public <T extends Unit & Legsc> void drawLegs(T unit) {
        if (!inited || legs == null) return;

        UnitType type = unit.type;
        type.applyColor(unit);

        // 脚部阴影大小 (与 PU_V8 一致使用 Draw.scl)
        float ssize = type.footRegion.width * Draw.scl * 1.5f;
        float rotation = baseRotation;

        // 1. 画所有脚的阴影
        for (TriJointLeg leg : legs) {
            Drawf.shadow(leg.joints[2].x, leg.joints[2].y, ssize);
        }

        // 2. 从后到前画腿 (i 索引顺序: 偶数从前半段取, 奇数从后半段取, 形成对称)
        for (int j = legs.length - 1; j >= 0; j--) {
            int i = (j % 2 == 0 ? (j / 2) : legs.length - 1 - (j / 2));
            TriJointLeg leg = legs[i];
            float angle = legAngle(rotation, i);
            boolean flip = i >= legs.length / 2f;
            int flips = Mathf.sign(flip);

            // 腿根位置 = unit + trns(legAngle, legBaseOffset)
            Vec2 position = legOffsetB.trns(angle, type.legBaseOffset).add(unit);

            // 预计算两个关节处的延伸偏移 (legExtension), 让相邻段在关节处重叠, 视觉上连接
            for (int k = 0; k < 2; k++) {
                Tmp.v1.set(leg.joints[1 + k]).sub(leg.joints[k]).inv().setLength(type.legExtension);
                jointOffsets[k][0] = Tmp.v1.x;
                jointOffsets[k][1] = Tmp.v1.y;
            }

            // 移动时脚部抬起阴影 (visualElevation > 0 时)
            if (leg.moving && type.shadowElevation > 0f) {
                float scl = type.shadowElevation;
                float elev = Mathf.slope(1f - leg.stage) * scl;
                Draw.color(Pal.shadow);
                Draw.rect(type.footRegion,
                        leg.joints[2].x + UnitType.shadowTX * elev,
                        leg.joints[2].y + UnitType.shadowTY * elev,
                        position.angleTo(leg.joints[2]));
                type.applyColor(unit);
            }

            // 3. 画脚
            Draw.rect(type.footRegion, leg.joints[2].x, leg.joints[2].y, position.angleTo(leg.joints[2]));

            // 4. 段1: position → joints[0] (用 legRegion, 大腿靠近身体段)
            Lines.stroke(type.legRegion.height * Draw.scl * flips);
            Lines.line(type.legRegion, position.x, position.y, leg.joints[0].x, leg.joints[0].y, false);

            // 5. 段2-3: joints[k] → joints[k+1] (中段用 legMiddleRegion, 末段用 legBaseRegion)
            for (int k = 0; k < 2; k++) {
                TextureRegion region = k == 0 ? legMiddleRegion : type.legBaseRegion;
                Lines.stroke(region.height * Draw.scl * flips);
                Lines.line(region,
                        leg.joints[k].x + jointOffsets[k][0],
                        leg.joints[k].y + jointOffsets[k][1],
                        leg.joints[k + 1].x,
                        leg.joints[k + 1].y, false);
            }

            // 6. 关节贴图: 根部用 baseJointRegion (带 rotation), 中间关节用 jointRegion (无 rotation)
            if (type.baseJointRegion.found() || type.jointRegion.found()) {
                for (int k = -1; k < 2; k++) {
                    Vec2 pos = k == -1 ? position : leg.joints[k];
                    TextureRegion region = k == -1 ? type.baseJointRegion : type.jointRegion;
                    if (region.found()) {
                        Draw.rect(region, pos.x, pos.y, k == -1 ? rotation : 0f);
                    }
                }
            }
        }

        // 7. baseRegion 画在最后 (单位底盘, PU_V8 用 rotation 不偏 90 度)
        if (type.baseRegion.found()) {
            Draw.rect(type.baseRegion, unit.x, unit.y, rotation);
        }

        Draw.reset();
    }

    // ===== 腿数据初始化 (移植 TriJointLegsComp.resetlegs) =====
    private void resetLegs(Unit unit) {
        UnitType type = unit.type;
        float rot = baseRotation;
        int count = type.legCount;
        float legLength = type.legLength;
        float spacing = 360f / count;

        legs = new TriJointLeg[count];

        // 每条腿的 3 个关节沿 dstRot 方向, 距离腿根 (legLength/3)*(j+1) 初始化
        for (int i = 0; i < legs.length; i++) {
            TriJointLeg l = new TriJointLeg();
            for (int j = 0; j < 3; j++) {
                l.joints[j].trns(i * spacing + rot, (legLength / 3f) * (j + 1f)).add(unit);
            }
            legs[i] = l;
        }
    }

    // ===== 腿系统更新 (完整移植 TriJointLegsComp.update) =====
    private void updateLegs(Unit unit) {
        UnitType type = unit.type;
        float deltaX = unit.deltaX();
        float deltaY = unit.deltaY();

        // 1. baseRotation 跟踪移动方向 (用 baseRotateSpeed, 与 PU_V8 一致)
        if (Mathf.dst(deltaX, deltaY) > 0.001f) {
            baseRotation = Angles.moveToward(baseRotation, Mathf.angle(deltaX, deltaY), type.baseRotateSpeed);
        }

        float rot = baseRotation;
        float legLength = type.legLength;

        // 腿数量变化时重建
        if (legs == null || legs.length != type.legCount) {
            resetLegs(unit);
        }

        // 2. 步态参数: 分组数 div, 每步移动距离 moveSpace, 累计行走距离 totalLength
        int div = Math.max(legs.length / type.legGroupSize, 2);
        moveSpace = legLength / 2.4f / (div / 2f) * type.legMoveSpace;
        totalLength += Mathf.dst(deltaX, deltaY);

        // 3. 腿前向偏移 (腿相对移动方向的前向偏移, 由 legTrns 控制)
        float trns = moveSpace * 0.85f * legTrns;

        Vec2 moveOffset = Tmp.v4.trns(rot, trns);
        boolean moving = unit.moving();

        for (int i = 0; i < legs.length; i++) {
            float dstRot = legAngle(rot, i);
            // 腿根位置 = unit + trns(dstRot, legBaseOffset)
            Vec2 baseOffset = Tmp.v5.trns(dstRot, type.legBaseOffset).add(unit);

            TriJointLeg l = legs[i];

            // 4. 步态分组: stage = 累计步数, group = stage % div, 每 div 条腿中只有一条在移动
            float stageF = (totalLength + (i * type.legPairOffset)) / moveSpace;
            int stage = (int) stageF;
            int group = stage % div;
            boolean move = i % div == group;
            // side 决定 IK 弯曲方向, 前半段 true, 后半段 false
            boolean side = i < legs.length / 2;
            // 后腿翻转 (PU_V8 flipBackLegs)
            boolean backLeg = Math.abs((i + 0.5f) - (legs.length / 2f)) <= 0.501f;
            if (backLeg && type.flipBackLegs) side = !side;

            l.moving = move;
            // 静止时 stage 平滑回到 0 (腿放平)
            l.stage = moving ? stageF % 1f : Mathf.lerpDelta(l.stage, 0f, 0.1f);

            // 5. 步态切换: 当 group 改变且本腿刚落地时, 触发脚步效果 (水波/灰尘/震屏/伤害)
            if (l.group != group) {
                if (!move && i % div == l.group) {
                    Floor floor = Vars.world.floorWorld(l.joints[2].x, l.joints[2].y);
                    if (floor.isLiquid) {
                        floor.walkEffect.at(l.joints[2].x, l.joints[2].y, type.rippleScale, floor.mapColor);
                        floor.walkSound.at(unit.x, unit.y, 1f, floor.walkSoundVolume);
                    } else {
                        Fx.unitLandSmall.at(l.joints[2].x, l.joints[2].y, type.rippleScale, floor.mapColor);
                    }

                    if (landShake > 0) {
                        Effect.shake(landShake, landShake, l.joints[2]);
                    }

                    if (type.legSplashDamage > 0) {
                        Damage.damage(unit.team, l.joints[2].x, l.joints[2].y, type.legSplashRange, type.legSplashDamage, false, true);
                    }
                }
                l.group = group;
            }

            // 6. 计算脚 (joints[2]) 的目标位置 legDest
            //    移动时: 目标 = 腿根 + trns(dstRot, legLength) + moveOffset*(后腿1.5x)
            //    然后用 stageF%1 在当前位置和目标之间插值 (Interp.pow2)
            //    静止时: 保持当前位置
            if (move) {
                float moveFract = stageF % 1f;
                Tmp.v1.trns(dstRot, legLength).add(baseOffset).add(moveOffset, backLeg ? 1.5f : 1f);
                Tmp.v6.set(l.joints[2]).lerpDelta(Tmp.v1, Interp.pow2.apply(moveFract));
            } else {
                Tmp.v6.set(l.joints[2]);
            }

            // 7. legScl 计算: 腿根到脚距离超过 legLength 时, 按比例伸缩腿段长度
            //    scl = (min(dst, legLength*maxStretch) / legLength) - 1
            //    移动时: legScl 平滑插值到 1+scl, jointLerp = legSpeed/4 (慢)
            //    静止时: legScl 直接设为 1+scl, jointLerp lerp 到 1 (快)
            //    距离未超 legLength 时: legScl 平滑回 1, jointLerp = legSpeed/4
            Vec2 legDest = Tmp.v6;
            if (!baseOffset.within(legDest, legLength)) {
                float scl = ((Math.min(baseOffset.dst(legDest), type.legLength * maxStretch) / legLength) - 1f);
                if (move) {
                    float moveFract = stageF % 1f;
                    l.legScl = Mathf.lerpDelta(l.legScl, 1f + scl, moveFract);
                    l.jointLerp = type.legSpeed / 4f;
                } else {
                    l.legScl = 1f + scl;
                    l.jointLerp = Mathf.lerpDelta(l.jointLerp, 1f, type.legSpeed / 2f);
                }
            } else {
                l.legScl = Mathf.lerpDelta(l.legScl, 1f, type.legSpeed / 4f);
                l.jointLerp = type.legSpeed / 4f;
            }

            // 8. 调用 IK 解算 3 个关节位置
            //    每段长度 = (legLength/3) * legScl (伸缩)
            //    jointLerp: 移动时强制 1 (立即跟随), 静止且超出范围时用 l.jointLerp*Time.delta (跨帧平滑)
            TriJointInverseKinematics.solve(
                    baseOffset.x, baseOffset.y, l.joints,
                    (type.legLength / 3f) * l.legScl, legDest, side,
                    move ? 1f : (baseOffset.within(legDest, legLength) ? 1f : l.jointLerp * Time.delta));
        }
    }
}
