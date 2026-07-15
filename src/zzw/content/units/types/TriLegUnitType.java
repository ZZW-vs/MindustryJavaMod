package zzw.content.units.types;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.entities.Leg;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;

/**
 * 三节腿 UnitType (移植 PU132 TriJointLegsComp + TriJointInverseKinematics)
 *
 * v158 原生腿系统只有两节 (leg-base + leg), 本类在 joint 和 base 之间插入中间关节,
 * 实现三节腿: position → joint (小腿) → middle (中段) → base (大腿)
 *
 * 中间关节用 PU132 TriJointInverseKinematics 算法计算:
 * - 基于腿根到脚的距离和方向计算垂直偏移
 * - 腿伸直时偏移大, 腿弯曲时偏移小
 * - 添加时间相关摆动让中段有动画
 *
 * 参考: PU132 TriJointLegsComp (update), TriJointInverseKinematics.solve
 *       v158 UnitType.drawLegs (L1827-1901)
 */
public class TriLegUnitType extends UnitType {
    private static final Vec2 legOffsetTmp = new Vec2();

    // 三节腿贴图 (可选, 不存在时退化为两节腿)
    public TextureRegion legMiddleRegion;

    // 摆动参数
    public float swingSpeed = 0.04f;    // 摆动速度
    public float swingAmplitude = 3f;    // 摆动幅度

    public TriLegUnitType(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        // 加载中段腿贴图 (可选, 不存在时退化为两节腿)
        legMiddleRegion = Core.atlas.find(name + "-leg-middle");
    }

    @Override
    public <T extends Unit & mindustry.gen.Legsc> void drawLegs(T unit) {
        applyColor(unit);
        Tmp.c3.set(Draw.getMixColor());

        Leg[] legs = unit.legs();
        float ssize = footRegion.width * footRegion.scl() * 1.5f;
        float rotation = unit.baseRotation();
        float invDrown = 1f - unit.drownTime;

        // 画脚部阴影
        if (footRegion.found()) {
            for (Leg leg : legs) {
                Drawf.shadow(leg.base.x, leg.base.y, ssize, invDrown);
            }
        }

        // 无中段贴图时退化为原版两节腿
        if (!legMiddleRegion.found()) {
            drawTwoSegmentLegs(unit, legs, rotation, invDrown, Tmp.c3);
            return;
        }

        // ===== 三节腿渲染 (从后到前) =====
        for (int j = legs.length - 1; j >= 0; j--) {
            int i = (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2);
            Leg leg = legs[i];
            boolean flip = i >= legs.length / 2f;
            int flips = Mathf.sign(flip);

            Vec2 position = unit.legOffset(legOffsetTmp, i).add(unit);

            // legExtension 方向 (base 到 joint 的反方向)
            Tmp.v1.set(leg.base).sub(leg.joint).inv().setLength(legExtension);

            // 脚部阴影 (移动时)
            if (footRegion.found() && leg.moving && shadowElevation > 0) {
                float scl = shadowElevation * invDrown;
                float elev = Mathf.slope(1f - leg.stage) * scl;
                Draw.color(Pal.shadow);
                Draw.rect(footRegion, leg.base.x + shadowTX * elev, leg.base.y + shadowTY * elev, position.angleTo(leg.base));
                Draw.color();
            }

            Draw.mixcol(Tmp.c3, Tmp.c3.a);

            // 画脚
            if (footRegion.found()) {
                Draw.rect(footRegion, leg.base.x, leg.base.y, position.angleTo(leg.base));
            }

            // ★ PU132 TriJointInverseKinematics 算法 (完整移植)
            // 三节腿每段长度 = legLength/3, 总长 = legLength
            // 两个中间关节 joint1, joint2 在 position→base 连线上, 加垂直偏移
            float segLen = legLength / 3f;  // 每段长度 (固定值)
            float totalLen = position.dst(leg.base);  // 腿根到脚的实际距离
            totalLen = Math.min(totalLen, 3f * segLen);  // limit 到最大伸直长度

            float angle = position.angleTo(leg.base);

            // PU132 IK: offset = sineOut(cosDeg((totalLen - segLen)/(segLen*2) * 90)) * segLen * sign(side)
            // 腿伸直时(totalLen=3*segLen): lenRatio=1, cosDeg(90)=0, offset=0 (直腿)
            // 腿收缩时(totalLen<3*segLen): offset>0 (弯曲)
            float lenRatio = (totalLen - segLen) / (segLen * 2f);
            float offset = Interp.sineOut.apply(Mathf.cosDeg(Mathf.mod(lenRatio * 90f, 360f))) * segLen * flips;

            // 垂直方向
            float perpX = -Mathf.sinDeg(angle);
            float perpY = Mathf.cosDeg(angle);

            // 中点 = position + direction*(totalLen/2) + perp*offset
            float midDist = totalLen / 2f;
            float midX = position.x + Mathf.cosDeg(angle) * midDist + perpX * offset;
            float midY = position.y + Mathf.sinDeg(angle) * midDist + perpY * offset;

            // ★ 摆动偏移: 基于时间和腿索引, 让中段有动画
            float swing = Mathf.sin(Time.time * swingSpeed + i * 0.7f) * swingAmplitude * Mathf.slope(leg.stage);
            midX += perpX * swing;
            midY += perpY * swing;

            // 两个中间关节: 在中点 ± segLen/2 沿腿方向
            float j1x = midX - Mathf.cosDeg(angle) * (segLen / 2f);
            float j1y = midY - Mathf.sinDeg(angle) * (segLen / 2f);
            float j2x = midX + Mathf.cosDeg(angle) * (segLen / 2f);
            float j2y = midY + Mathf.sinDeg(angle) * (segLen / 2f);

            // ===== 渲染三段 =====
            // 段1: position → joint1 (用 legRegion)
            Lines.stroke(legRegion.height * legRegion.scl() * flips);
            Lines.line(legRegion, position.x, position.y, j1x, j1y, false);

            // 段2: joint1 → joint2 (中段, legMiddleRegion)
            Lines.stroke(legMiddleRegion.height * legMiddleRegion.scl() * flips);
            Lines.line(legMiddleRegion, j1x, j1y, j2x, j2y, false);

            // 段3: joint2 → base (大腿, legBaseRegion)
            Lines.stroke(legBaseRegion.height * legRegion.scl() * flips);
            Lines.line(legBaseRegion, j2x, j2y, leg.base.x, leg.base.y, false);

            // 关节贴图
            if (jointRegion.found()) {
                Draw.rect(jointRegion, j1x, j1y);
                Draw.rect(jointRegion, j2x, j2y);
            }
        }

        // base joints 画在最后
        if (baseJointRegion.found()) {
            for (int j = legs.length - 1; j >= 0; j--) {
                Vec2 position = unit.legOffset(legOffsetTmp, (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2)).add(unit);
                Draw.rect(baseJointRegion, position.x, position.y, rotation);
            }
        }

        if (baseRegion.found()) {
            Draw.rect(baseRegion, unit.x, unit.y, rotation - 90);
        }

        Draw.reset();
    }

    /** 退化为原版两节腿渲染 (无 legMiddleRegion 贴图时使用) */
    private <T extends Unit & mindustry.gen.Legsc> void drawTwoSegmentLegs(T unit, Leg[] legs, float rotation, float invDrown, arc.graphics.Color mixCol) {
        for (int j = legs.length - 1; j >= 0; j--) {
            int i = (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2);
            Leg leg = legs[i];
            boolean flip = i >= legs.length / 2f;
            int flips = Mathf.sign(flip);

            Vec2 position = unit.legOffset(legOffsetTmp, i).add(unit);
            Tmp.v1.set(leg.base).sub(leg.joint).inv().setLength(legExtension);

            if (footRegion.found() && leg.moving && shadowElevation > 0) {
                float scl = shadowElevation * invDrown;
                float elev = Mathf.slope(1f - leg.stage) * scl;
                Draw.color(Pal.shadow);
                Draw.rect(footRegion, leg.base.x + shadowTX * elev, leg.base.y + shadowTY * elev, position.angleTo(leg.base));
                Draw.color();
            }

            Draw.mixcol(mixCol, mixCol.a);

            if (footRegion.found()) {
                Draw.rect(footRegion, leg.base.x, leg.base.y, position.angleTo(leg.base));
            }

            if (legBaseUnder) {
                Lines.stroke(legBaseRegion.height * legRegion.scl() * flips);
                Lines.line(legBaseRegion, leg.joint.x + Tmp.v1.x, leg.joint.y + Tmp.v1.y, leg.base.x, leg.base.y, false);
                Lines.stroke(legRegion.height * legRegion.scl() * flips);
                Lines.line(legRegion, position.x, position.y, leg.joint.x, leg.joint.y, false);
            } else {
                Lines.stroke(legRegion.height * legRegion.scl() * flips);
                Lines.line(legRegion, position.x, position.y, leg.joint.x, leg.joint.y, false);
                Lines.stroke(legBaseRegion.height * legRegion.scl() * flips);
                Lines.line(legBaseRegion, leg.joint.x + Tmp.v1.x, leg.joint.y + Tmp.v1.y, leg.base.x, leg.base.y, false);
            }

            if (jointRegion.found()) {
                Draw.rect(jointRegion, leg.joint.x, leg.joint.y);
            }
        }

        if (baseJointRegion.found()) {
            for (int j = legs.length - 1; j >= 0; j--) {
                Vec2 position = unit.legOffset(legOffsetTmp, (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2)).add(unit);
                Draw.rect(baseJointRegion, position.x, position.y, rotation);
            }
        }

        if (baseRegion.found()) {
            Draw.rect(baseRegion, unit.x, unit.y, rotation - 90);
        }

        Draw.reset();
    }
}
