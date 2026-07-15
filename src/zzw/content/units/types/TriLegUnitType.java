package zzw.content.units.types;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.entities.Leg;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;

/**
 * 三节腿 UnitType (v158 原生只支持两节: leg-base + leg)
 *
 * 在原版 drawLegs 基础上, 在 joint 和 base 之间插入 leg-middle 贴图, 实现三节腿:
 * - position → joint: 用 legRegion (小腿)
 * - joint → middle: 用 legMiddleRegion (中段)  ← 新增
 * - middle → base: 用 legBaseRegion (大腿)
 *
 * 贴图命名: {name}-leg-middle (由 load() 加载)
 *
 * 参考: v158 UnitType.drawLegs (L1827-1901)
 *       PU132 BasicLeg.draw (L56-85) - 三节腿渲染
 */
public class TriLegUnitType extends UnitType {
    private static final Vec2 legOffsetTmp = new Vec2();
    public TextureRegion legMiddleRegion;

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

        if (footRegion.found()) {
            for (Leg leg : legs) {
                Drawf.shadow(leg.base.x, leg.base.y, ssize, invDrown);
            }
        }

        // legs drawn front first
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

            Draw.mixcol(Tmp.c3, Tmp.c3.a);

            if (footRegion.found()) {
                Draw.rect(footRegion, leg.base.x, leg.base.y, position.angleTo(leg.base));
            }

            // ★ 三节腿渲染: position → joint (小腿), joint → middle (中段), middle → base (大腿)
            if (legMiddleRegion.found()) {
                // 计算 joint 和 base 的中点作为 middle
                float midX = (leg.joint.x + leg.base.x) / 2f;
                float midY = (leg.joint.y + leg.base.y) / 2f;

                // position → joint: 小腿 (legRegion)
                Lines.stroke(legRegion.height * legRegion.scl() * flips);
                Lines.line(legRegion, position.x, position.y, leg.joint.x, leg.joint.y, false);

                // joint → middle: 中段 (legMiddleRegion)
                Lines.stroke(legMiddleRegion.height * legMiddleRegion.scl() * flips);
                Lines.line(legMiddleRegion, leg.joint.x, leg.joint.y, midX, midY, false);

                // middle → base: 大腿 (legBaseRegion), 加上 legExtension 偏移
                Lines.stroke(legBaseRegion.height * legRegion.scl() * flips);
                Lines.line(legBaseRegion, midX + Tmp.v1.x * 0.5f, midY + Tmp.v1.y * 0.5f, leg.base.x, leg.base.y, false);
            } else {
                // 无中段贴图时退化为原版两节腿
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
            }

            if (jointRegion.found()) {
                Draw.rect(jointRegion, leg.joint.x, leg.joint.y);
            }
        }

        // base joints drawn after everything else
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
