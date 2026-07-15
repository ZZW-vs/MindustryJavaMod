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
 * 混合腿型 UnitType (移植 PU132 toxoswarmer 的 6小腿+4大腿)
 *
 * PU132 用 CLegType.createGroup + BasicLegType 实现混合腿型:
 * - 小腿组 (3条定义×2镜像=6条): baseLength=endLength=32, legTrns=0.8
 * - 大腿组 (2条定义×2镜像=4条): baseLength=55, endLength=71, legTrns=0.7
 *
 * v158 原生腿系统只支持一种腿型 (单一 legRegion/legBaseRegion/footRegion/jointRegion).
 * 本类重写 drawLegs, 在渲染时区分前 smallLegCount 条用小腿贴图, 其余用大腿贴图.
 *
 * 腿的位置和移动逻辑由 v158 原生 LegsComp 处理 (legCount=10, legLength=折中值),
 * 渲染时根据腿索引选择对应贴图.
 *
 * 参考: PU132 UnityUnitTypes.java L3126-3229 (toxoswarmer 配置)
 *       PU132 BasicLeg.draw() (腿渲染逻辑)
 *       v158 UnitType.drawLegs() (L1827-1901)
 */
public class MixedLegUnitType extends UnitType {
    private static final Vec2 legOffsetTmp = new Vec2();

    // ===== 小腿贴图 (toxoswarmer-leg-small-*) =====
    public TextureRegion smallLegRegion;       // 腿上段 (position → joint)
    public TextureRegion smallLegBaseRegion;    // 腿下段 (joint → base)
    public TextureRegion smallFootRegion;      // 脚
    public TextureRegion smallJointRegion;      // 膝关节

    // ===== 大腿贴图 (toxoswarmer-leg-large-*) =====
    public TextureRegion largeLegRegion;       // 腿上段 (position → joint)
    public TextureRegion largeLegBaseRegion;    // 腿下段 (joint → base)
    public TextureRegion largeFootRegion;       // 脚
    public TextureRegion largeJointRegion;      // 膝关节

    // 前多少条是小腿 (剩余的是大腿)
    public int smallLegCount = 6;

    public MixedLegUnitType(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        // 加载小腿贴图 (PU132 BasicLegType.load: name + "-base/-end/-foot/-joint")
        smallLegRegion = Core.atlas.find(name + "-leg-small-base");   // 上段 (对应 PU132 baseRegion)
        smallLegBaseRegion = Core.atlas.find(name + "-leg-small-end"); // 下段 (对应 PU132 endRegion)
        smallFootRegion = Core.atlas.find(name + "-leg-small-foot");
        smallJointRegion = Core.atlas.find(name + "-leg-small-joint");

        // 加载大腿贴图
        largeLegRegion = Core.atlas.find(name + "-leg-large-base");
        largeLegBaseRegion = Core.atlas.find(name + "-leg-large-end");
        largeFootRegion = Core.atlas.find(name + "-leg-large-foot");
        largeJointRegion = Core.atlas.find(name + "-leg-large-joint");
    }

    @Override
    public <T extends Unit & mindustry.gen.Legsc> void drawLegs(T unit) {
        applyColor(unit);
        Tmp.c3.set(Draw.getMixColor());

        Leg[] legs = unit.legs();
        float rotation = unit.baseRotation();
        float invDrown = 1f - unit.drownTime;

        // ===== 画脚部阴影 =====
        for (int i = 0; i < legs.length; i++) {
            Leg leg = legs[i];
            TextureRegion foot = isSmallLeg(i) ? smallFootRegion : largeFootRegion;
            if (foot.found()) {
                float ssize = foot.width * foot.scl() * 1.5f;
                Drawf.shadow(leg.base.x, leg.base.y, ssize, invDrown);
            }
        }

        // ===== 渲染腿 (从前到后) =====
        // v158 原生 drawLegs 用 (j % 2 == 0 ? j/2 : legs.length - 1 - j/2) 交替绘制左右腿
        for (int j = legs.length - 1; j >= 0; j--) {
            int i = (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2);
            Leg leg = legs[i];
            boolean flip = i >= legs.length / 2f;
            int flips = Mathf.sign(flip);

            // 选择对应腿类型的贴图
            TextureRegion legReg = isSmallLeg(i) ? smallLegRegion : largeLegRegion;
            TextureRegion legBaseReg = isSmallLeg(i) ? smallLegBaseRegion : largeLegBaseRegion;
            TextureRegion footReg = isSmallLeg(i) ? smallFootRegion : largeFootRegion;
            TextureRegion jointReg = isSmallLeg(i) ? smallJointRegion : largeJointRegion;

            if (!legReg.found() && !legBaseReg.found()) {
                // 两种贴图都没有, 退化为原版腿贴图
                legReg = legRegion;
                legBaseReg = legBaseRegion;
                footReg = footRegion;
                jointReg = jointRegion;
            }

            Vec2 position = unit.legOffset(legOffsetTmp, i).add(unit);
            Tmp.v1.set(leg.base).sub(leg.joint).inv().setLength(legExtension);

            // 移动时脚部阴影
            if (footReg.found() && leg.moving && shadowElevation > 0) {
                float scl = shadowElevation * invDrown;
                float elev = Mathf.slope(1f - leg.stage) * scl;
                Draw.color(Pal.shadow);
                Draw.rect(footReg, leg.base.x + shadowTX * elev, leg.base.y + shadowTY * elev, position.angleTo(leg.base));
                Draw.color();
            }

            Draw.mixcol(Tmp.c3, Tmp.c3.a);

            // 画脚
            if (footReg.found()) {
                Draw.rect(footReg, leg.base.x, leg.base.y, position.angleTo(leg.base));
            }

            // 画腿 (两段: 上段 position→joint, 下段 joint→base)
            // PU132 BasicLeg.draw: base→joint 用 baseRegion, joint→foot 用 endRegion
            // v158 drawLegs: position→joint 用 legRegion, joint→base 用 legBaseRegion
            if (legBaseUnder) {
                // 下段先画 (在身体下方)
                if (legBaseReg.found()) {
                    Lines.stroke(legBaseReg.height * legReg.scl() * flips);
                    Lines.line(legBaseReg, leg.joint.x + Tmp.v1.x, leg.joint.y + Tmp.v1.y, leg.base.x, leg.base.y, false);
                }
                if (legReg.found()) {
                    Lines.stroke(legReg.height * legReg.scl() * flips);
                    Lines.line(legReg, position.x, position.y, leg.joint.x, leg.joint.y, false);
                }
            } else {
                // 上段先画
                if (legReg.found()) {
                    Lines.stroke(legReg.height * legReg.scl() * flips);
                    Lines.line(legReg, position.x, position.y, leg.joint.x, leg.joint.y, false);
                }
                if (legBaseReg.found()) {
                    Lines.stroke(legBaseReg.height * legReg.scl() * flips);
                    Lines.line(legBaseReg, leg.joint.x + Tmp.v1.x, leg.joint.y + Tmp.v1.y, leg.base.x, leg.base.y, false);
                }
            }

            // 膝关节
            if (jointReg.found()) {
                Draw.rect(jointReg, leg.joint.x, leg.joint.y);
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

    /** 判断第 i 条腿是否为小腿 (左右对称分布: 每侧3小腿+2大腿) */
    private boolean isSmallLeg(int i) {
        // legCount=10, 左侧 i<5, 右侧 i>=5
        // 镜像到左侧: pos = i < half ? i : (legCount - 1 - i)
        // 小腿在 pos 0,1,3; 大腿在 pos 2,4
        int half = legCount / 2;
        int pos = i < half ? i : (legCount - 1 - i);
        return pos == 0 || pos == 1 || pos == 3;
    }
}
