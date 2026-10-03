package zzw.content.units.abilities;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.world.blocks.environment.Floor;

/**
 * 自定义腿系统 Ability (完整移植 PU132 CLegComp + CLegGroup + BasicLeg)
 *
 * 支持:
 * - 多组腿 (小腿组 + 大腿组), 每组独立 baseRotation 和步态
 * - 每条腿独立配置 (baseLength/endLength/targetX/targetY/legTrns)
 * - PU132 原版 IK (用真实 baseLength/endLength, 支持不等长)
 * - PU132 原版渲染 (Lines.line + 两段不同贴图 + 膝关节/脚)
 * - PU132 原版落地效果 (CLeg.step): 脚部扬尘/水波/震屏, 与腿贴图落点一致
 *
 * ★ 使用本 Ability 的单位必须 legCount = 0 (不使用原生腿).
 *   若保留原生腿, 其落地效果会先于本 Ability 在"原生腿几何位置"播放, 与贴图错位.
 *
 * toxoswarmer 配置:
 *   小腿组: 2条定义×2镜像=4条, baseLength=endLength=32, total=64, legTrns=0.8
 *   大腿组: 2条定义×2镜像=4条, baseLength=55, endLength=71, total=126, legTrns=0.7
 */
public class CustomLegsAbility extends Ability {
    private static final Vec2 v1 = new Vec2(), v2 = new Vec2(), v3 = new Vec2();

    public Seq<LegGroupType> legGroups = new Seq<>();
    private transient Seq<LegGroupData> groupData;
    private transient boolean inited = false;

    public CustomLegsAbility() {}

    @Override
    public String localized(){
        return "自定义腿";
    }

    @Override
    public void init(UnitType type) {
        super.init(type);
        for (LegGroupType gt : legGroups) {
            gt.load();
        }
    }

    @Override
    public void update(Unit unit) {
        super.update(unit);
        if (!inited) {
            initLegs(unit);
            inited = true;
        }

        for (LegGroupData gd : groupData) {
            gd.update(unit, unit.type.legSpeed);
        }

        // ★ 不依赖原生腿系统: 挂载本 Ability 的单位 legCount 必须为 0 (原生腿为空).
        //   原因: v160 的合并 update() 中 LegsComp 的落地效果代码先于 Ability 执行,
        //   原生腿会按自己的几何 (defaultLegAngle + legLength) 在"错误位置"播放扬尘/水波,
        //   之后本 Ability 才把位置同步过去 —— 贴图与效果必然错位.
        //   PU132 原版同样不使用原生腿, 落地效果由本 Ability 的 LegData.step() 在
        //   真实脚部坐标 (foot) 播放 (见 LegData.update 中的 group 切换判定).
    }

    /** Ability.draw 为空操作, 腿渲染由 UnitType.drawLegs() 调用 drawLegs() 完成 (确保在 body 之前) */
    @Override
    public void draw(Unit unit) {
    }

    /** 绘制腿 (由 UnitType.drawLegs() 调用, 在 drawBody 之前) */
    public void drawLegs(Unit unit) {
        if (!inited || groupData == null) return;

        unit.type.applyColor(unit);
        Tmp.c3.set(Draw.getMixColor());

        // 画脚部阴影
        for (LegGroupData gd : groupData) {
            for (LegData leg : gd.legs) {
                LegType lt = leg.type;
                float ssize = lt.footRegion.width * Draw.scl * 1.5f;
                float invDrown = 1f - unit.drownTime;
                Drawf.shadow(leg.foot.x, leg.foot.y, ssize, invDrown);

                // 移动时脚部偏移阴影
                if (leg.moving && unit.type.shadowElevation > 0f) {
                    float scl = unit.type.shadowElevation * invDrown;
                    float elev = Mathf.slope(1f - Mathf.clamp(leg.stage)) * scl;
                    Draw.color(Pal.shadow);
                    Draw.rect(lt.footRegion, leg.foot.x + UnitType.shadowTX * elev, leg.foot.y + UnitType.shadowTY * elev, gd.setBase(unit, leg).angleTo(leg.foot));
                    Draw.color();
                }
            }
        }

        // 画腿 (从后到前)
        for (LegGroupData gd : groupData) {
            for (int j = gd.legs.length - 1; j >= 0; j--) {
                LegData leg = gd.legs[j];
                leg.draw(unit, gd);
            }
            // baseRegion
            if (gd.type.baseRegion.found()) {
                Draw.rect(gd.type.baseRegion, unit.x, unit.y, gd.baseRotation - 90f);
            }
        }

        Draw.reset();
    }

    private void initLegs(Unit unit) {
        groupData = new Seq<>(legGroups.size);
        for (LegGroupType gt : legGroups) {
            LegGroupData gd = new LegGroupData();
            gd.init(gt);
            gd.reset(unit);
            groupData.add(gd);
        }
    }

    // ===== 腿组类型配置 =====
    public static class LegGroupType {
        public String name;
        public LegType[] legs;
        public float moveSpacing = 1f, maxStretch = 1.7f, baseRotateSpeed = 5f;
        public int legGroupSize = 2;
        public TextureRegion baseRegion;

        public LegGroupType(String name, LegType... legs) {
            this.name = name;
            this.legs = legs;
        }

        public void load() {
            baseRegion = Core.atlas.find(name + "-base");
            for (LegType leg : legs) {
                leg.load();
            }
        }
    }

    // ===== 腿类型配置 =====
    public static class LegType {
        public String name;
        public float x, y, targetX, targetY;
        public float baseLength = 10f, endLength = 10f;
        public float legTrns = 1f;
        public boolean flipped = false;

        public TextureRegion footRegion, baseRegion, endRegion, kneeJoint, baseJoint;

        public LegType(String name) {
            this.name = name;
        }

        public void load() {
            footRegion = Core.atlas.find(name + "-foot");
            baseRegion = Core.atlas.find(name + "-base");
            endRegion = Core.atlas.find(name + "-end");
            kneeJoint = Core.atlas.find(name + "-joint");
            baseJoint = Core.atlas.find(name + "-base-joint");
        }

        public float length() {
            return baseLength + endLength;
        }
    }

    // ===== 腿组运行时数据 =====
    public static class LegGroupData {
        public LegGroupType type;
        public LegData[] legs;
        public float baseRotation, totalLength, moveSpace;

        public void init(LegGroupType type) {
            this.type = type;

            float l = 9000f;
            for (LegType leg : type.legs) {
                l = Math.min(l, leg.length());
            }
            int div = Math.max((type.legs.length * 2) / type.legGroupSize, 2);
            moveSpace = l / 1.6f / div * type.moveSpacing;

            int len = type.legs.length;
            legs = new LegData[len * 2];
            for (int i = 0; i < len * 2; i++) {
                int s = i / 2;
                boolean flip = i % 2 == 0;
                LegData leg = new LegData();
                leg.type = type.legs[s];
                leg.side = flip;
                leg.id = (i % 2 == 0) ? s : ((len * 2) - 1) - s;
                legs[i] = leg;
            }
        }

        public void reset(Unit unit) {
            baseRotation = unit.rotation;
            for (LegData leg : legs) {
                leg.reset(this, unit);
            }
        }

        public void update(Unit unit, float legSpeed) {
            if (unit.deltaLen() > 0.001f) {
                baseRotation = Angles.moveToward(baseRotation, Mathf.angle(unit.deltaX(), unit.deltaY()), type.baseRotateSpeed);
            }
            totalLength += unit.deltaLen() / moveSpace;

            for (LegData leg : legs) {
                leg.update(unit, this, legSpeed);
            }
        }

        public Vec2 setBase(Unit unit, LegData leg) {
            int side = Mathf.sign(leg.side);
            return v1.trns(baseRotation - 90f, leg.type.x * side, leg.type.y).add(unit);
        }
    }

    // ===== 单条腿运行时数据 =====
    public static class LegData {
        public LegType type;
        public boolean side;
        public int id, group;
        public boolean moving;
        public float stage;
        public Vec2 foot = new Vec2();
        public float jointX, jointY;

        public void reset(LegGroupData g, Unit unit) {
            int side = Mathf.sign(this.side);
            v1.trns(g.baseRotation - 90f, (type.x + type.targetX) * side, type.y + type.targetY).add(unit);
            foot.set(v1);

            float scl = type.baseLength / (type.baseLength + type.endLength);
            v1.trns(g.baseRotation - 90f, (type.targetX + type.x) * side, (type.targetY + type.y)).scl(scl).add(unit);
            jointX = v1.x;
            jointY = v1.y;
        }

        public void update(Unit unit, LegGroupData g, float legSpeed) {
            int side = Mathf.sign(this.side);
            int div = Math.max(2, g.legs.length / g.type.legGroupSize);
            v1.trns(g.baseRotation - 90f, type.x * side, type.y).add(unit);

            float stageF = g.totalLength;
            int stage = (int) stageF;
            int grp = stage % div;

            float trns = g.moveSpace * 0.85f * type.legTrns;

            moving = id % div == grp;
            // PU132: 用 uMoving (单位移动状态) 和局部 stage 变量
            this.stage = unit.moving() ? stageF % 1f : Mathf.lerpDelta(stage, 0f, 0.1f);

            v2.trns(g.baseRotation - 90f, (type.x + type.targetX) * side, type.y + type.targetY + trns).add(unit);

            updateLeg(unit, g, moving, v1.x, v1.y, v2.x, v2.y, legSpeed);

            // ★ 落地效果: 该腿刚由"摆动"落到"站稳"时, 在真实脚部坐标播放扬尘/水波
            //   (移植 PU132 CLeg.update 的 group 切换判定; 坐标用 foot 保证与贴图一致)
            Floor floor = Vars.world.floorWorld(foot.x, foot.y);
            if (this.group != grp) {
                if (!moving && id % div == this.group) {
                    step(unit, floor);
                }
                this.group = grp;
            }
        }

        /**
         * 腿落地效果 (移植 PU132 CLeg.step).
         * <p>坐标一律使用真实脚部 {@link #foot}, 因此扬尘/水波与腿贴图的落点完全一致.</p>
         */
        private void step(Unit unit, Floor floor) {
            if (Vars.headless) return;

            if (floor.isLiquid) {
                floor.walkEffect.at(foot.x, foot.y, unit.type.rippleScale, floor.mapColor);
                floor.walkSound.at(foot.x, foot.y, 1f, floor.walkSoundVolume);
            } else {
                Fx.unitLandSmall.at(foot.x, foot.y, unit.type.rippleScale, floor.mapColor);
                unit.type.stepSound.at(foot.x, foot.y,
                        unit.type.stepSoundPitch + Mathf.range(unit.type.stepSoundPitchRange),
                        unit.type.stepSoundVolume);
            }

            // 脚掌触地时的轻微震屏
            if (unit.type.stepShake > 0f) {
                Effect.shake(unit.type.stepShake, unit.type.stepShake, foot);
            }
        }

        private void updateLeg(Unit unit, LegGroupData g, boolean moving, float baseX, float baseY, float targetX, float targetY, float legSpeed) {
            // 限制 joint 离 base 的距离
            v1.set(jointX, jointY).sub(baseX, baseY).limit(type.baseLength * g.type.maxStretch).add(baseX, baseY);
            jointX = v1.x;
            jointY = v1.y;

            // 限制 foot 离 base 的距离
            foot.sub(baseX, baseY).limit(type.length() * g.type.maxStretch).add(baseX, baseY);

            // IK 解算 joint 目标位置
            v2.set(foot).sub(baseX, baseY);
            boolean flip = side == type.flipped;
            mindustry.graphics.InverseKinematics.solve(type.baseLength, type.endLength, v2, flip, v3);
            v3.add(baseX, baseY);

            if (moving) {
                float fract = g.totalLength % 1f;
                foot.lerpDelta(targetX, targetY, fract);
                v1.set(jointX, jointY).lerpDelta(v3, fract / 2f);
                jointX = v1.x;
                jointY = v1.y;
            }
            // 平滑收敛到 IK 解
            v1.set(jointX, jointY).lerpDelta(v3, legSpeed / 4f);
            jointX = v1.x;
            jointY = v1.y;
        }

        public void draw(Unit unit, LegGroupData g) {
            int flips = Mathf.sign(this.side == type.flipped);

            Vec2 base = g.setBase(unit, this);
            float ang = base.angleTo(foot);

            Draw.mixcol(Tmp.c3, Tmp.c3.a);

            // 画脚
            if (type.footRegion.found()) {
                Draw.rect(type.footRegion, foot.x, foot.y, ang);
            }

            // 画大腿段 (base → joint)
            if (type.baseRegion.found()) {
                Lines.stroke(type.baseRegion.height * Draw.scl * flips);
                Lines.line(type.baseRegion, base.x, base.y, jointX, jointY, false);
            }

            // 画小腿段 (joint → foot)
            if (type.endRegion.found()) {
                Lines.stroke(type.endRegion.height * Draw.scl * flips);
                Lines.line(type.endRegion, jointX, jointY, foot.x, foot.y, false);
            }

            // 膝关节
            if (type.kneeJoint.found()) {
                Draw.rect(type.kneeJoint, jointX, jointY);
            }

            // 根部关节 (PU132: 检查 baseJoint 但画 baseRegion)
            if (type.baseJoint.found()) {
                Draw.rect(type.baseRegion, base.x, base.y, g.baseRotation - 90f);
            }
        }
    }
}
