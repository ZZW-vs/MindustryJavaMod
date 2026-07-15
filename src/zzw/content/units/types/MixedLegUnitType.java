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
 * - 小腿组 (3条定义×2镜像=6条): baseLength=endLength=32, 总长64, legTrns=0.8
 * - 大腿组 (2条定义×2镜像=4条): baseLength=55, endLength=71, 总长126, legTrns=0.7
 *
 * v158 原生腿系统只支持一种腿型 (单一 legRegion/legBaseRegion/footRegion/jointRegion).
 * 本类重写 drawLegs, 在渲染时:
 * 1. 区分小腿/大腿用不同贴图
 * 2. 按 PU132 原版比例缩放腿长度 (小腿64/大腿126)
 *
 * 腿的位置和移动逻辑由 v158 原生 LegsComp 处理 (legCount=10, legLength=折中值95),
 * 渲染时根据腿类型缩放到实际长度.
 *
 * 参考: PU132 UnityUnitTypes.java L3126-3229 (toxoswarmer 配置)
 *       PU132 BasicLeg.draw() (腿渲染逻辑)
 *       v158 UnitType.drawLegs() (L1827-1901)
 */
public class MixedLegUnitType extends UnitType {
    private static final Vec2 legOffsetTmp = new Vec2();
    private static final Vec2 scaledBase = new Vec2();
    private static final Vec2 scaledJoint = new Vec2();

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

    // 腿长度 (PU132 原版: 小腿总长64, 大腿总长126)
    public float smallLegLength = 64f;
    public float largeLegLength = 126f;

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
            boolean small = isSmallLeg(i);
            TextureRegion foot = small ? smallFootRegion : largeFootRegion;
            if (foot.found()) {
                // ★ 大腿不缩放, 小腿缩放到 64
                Vec2 pos = unit.legOffset(Tmp.v3, i).add(unit);
                float scl = small ? (smallLegLength / legLength) : 1f;
                Tmp.v2.set(leg.base).sub(pos.x, pos.y).scl(scl).add(pos.x, pos.y);
                float ssize = foot.width * foot.scl() * 1.5f;
                Drawf.shadow(Tmp.v2.x, Tmp.v2.y, ssize, invDrown);
            }
        }

        // ===== 渲染腿 (从前到后) =====
        // v158 原生 drawLegs 用 (j % 2 == 0 ? j/2 : legs.length - 1 - j/2) 交替绘制左右腿
        for (int j = legs.length - 1; j >= 0; j--) {
            int i = (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2);
            Leg leg = legs[i];
            boolean small = isSmallLeg(i);
            boolean flip = i >= legs.length / 2f;
            int flips = Mathf.sign(flip);

            // 选择对应腿类型的贴图
            TextureRegion legReg = small ? smallLegRegion : largeLegRegion;
            TextureRegion legBaseReg = small ? smallLegBaseRegion : largeLegBaseRegion;
            TextureRegion footReg = small ? smallFootRegion : largeFootRegion;
            TextureRegion jointReg = small ? smallJointRegion : largeJointRegion;

            if (!legReg.found() && !legBaseReg.found()) {
                // 两种贴图都没有, 退化为原版腿贴图
                legReg = legRegion;
                legBaseReg = legBaseRegion;
                footReg = footRegion;
                jointReg = jointRegion;
            }

            Vec2 position = unit.legOffset(legOffsetTmp, i).add(unit);

            // ★ 大腿不缩放 (用 legLength 原值), 小腿缩放到 64
            // 原版大腿通过 IK 关节转动伸缩, v158 简化为固定长度避免过度延伸
            float scl = small ? (smallLegLength / legLength) : 1f;
            scaledBase.set(leg.base).sub(position.x, position.y).scl(scl).add(position.x, position.y);
            scaledJoint.set(leg.joint).sub(position.x, position.y).scl(scl).add(position.x, position.y);

            Tmp.v1.set(scaledBase).sub(scaledJoint).inv().setLength(legExtension);

            // 移动时脚部阴影
            if (footReg.found() && leg.moving && shadowElevation > 0) {
                float shadowScl = shadowElevation * invDrown;
                float elev = Mathf.slope(1f - leg.stage) * shadowScl;
                Draw.color(Pal.shadow);
                Draw.rect(footReg, scaledBase.x + shadowTX * elev, scaledBase.y + shadowTY * elev, position.angleTo(scaledBase));
                Draw.color();
            }

            Draw.mixcol(Tmp.c3, Tmp.c3.a);

            // 画脚 (在缩放后的位置)
            if (footReg.found()) {
                Draw.rect(footReg, scaledBase.x, scaledBase.y, position.angleTo(scaledBase));
            }

            // 画腿 (两段: 上段 position→joint, 下段 joint→base)
            if (legBaseUnder) {
                // 下段先画 (在身体下方)
                if (legBaseReg.found()) {
                    Lines.stroke(legBaseReg.height * legReg.scl() * flips);
                    Lines.line(legBaseReg, scaledJoint.x + Tmp.v1.x, scaledJoint.y + Tmp.v1.y, scaledBase.x, scaledBase.y, false);
                }
                if (legReg.found()) {
                    Lines.stroke(legReg.height * legReg.scl() * flips);
                    Lines.line(legReg, position.x, position.y, scaledJoint.x, scaledJoint.y, false);
                }
            } else {
                // 上段先画
                if (legReg.found()) {
                    Lines.stroke(legReg.height * legReg.scl() * flips);
                    Lines.line(legReg, position.x, position.y, scaledJoint.x, scaledJoint.y, false);
                }
                if (legBaseReg.found()) {
                    Lines.stroke(legBaseReg.height * legReg.scl() * flips);
                    Lines.line(legBaseReg, scaledJoint.x + Tmp.v1.x, scaledJoint.y + Tmp.v1.y, scaledBase.x, scaledBase.y, false);
                }
            }

            // 膝关节
            if (jointReg.found()) {
                Draw.rect(jointReg, scaledJoint.x, scaledJoint.y);
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
