package zzw.content.units.type.decal;

import arc.Core;
import arc.func.Func;
import arc.graphics.Pixmap;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

/**
 * 鞭毛尾巴装饰 (移植自 PU132 type/decal/FlagellaDecorationType 原版,
 * thalassophobia 的 4 段贴图 × 15 节长尾)
 *
 * <p>动画原理 (对照 PU132 原版, 未做任何改动):</p>
 * <p>1. 尾巴由 segments 节组成, 每节用 Lines.line 沿贴图拉伸绘制
 *    (贴图从尾根 tail-0 过渡到尾尖 tail-end);
 * <br>2. 两遍模拟:
 *    <br>- 第一遍 (update): 目标位置约束 — 每节朝上一节方向转动 (angleLimit 限幅),
 *      节间距 = cosDeg(swayAngle) × segmentLength (摆动时自然压缩);
 *    <br>- 第二遍: 实际绘制位置 = 加上 swayAngle 摆动角后的固定间距,
 *      摆动角 = sin(progress - idx×swayOffset, swayScl, 强度渐变)。
 *      强度从根部 startIntensity 渐变到末端 endIntensity (末端甩得最厉害);
 * <br>3. progress 随单位移动距离累加 (deltaLen, PU132 原版设计);
 * <br>4. 每节独立绘制阴影 (Drawf.shadow)。</p>
 */
public class FlagellaDecorationType extends UnitDecorationType{
    /** 尾巴根部的挂载偏移 (单位局部坐标) */
    public float x, y;
    /** 根部/末端摆动强度 (角度) */
    public float startIntensity = 15f, endIntensity = 40f;
    /** 摆动周期缩放 (越大摆得越慢) 与相位偏移 */
    public float swayScl = 40f, swayOffset;
    /** 每节相对上一节的最大转角 (限幅, 防止尾巴打折) */
    public float angleLimit = 25f;
    /** 贴图名 (含 create- 前缀) */
    public String name;
    /** 每节长度 / 总节数 */
    float segmentLength;
    int segments;
    TextureRegion[] regions;
    TextureRegion end;

    public FlagellaDecorationType(String name, int textures, int segments, float length){
        this.name = name;
        this.segments = segments;
        segmentLength = length;
        regions = new TextureRegion[textures];

        decalType = FlagellaDecoration::new;
    }

    @Override
    public void load(){
        for(int i = 0; i < regions.length; i++){
            regions[i] = Core.atlas.find(name + "-" + i);
        }
        end = Core.atlas.find(name + "-end");
    }

    /**
     * 第一遍模拟: 目标位置 (tx/ty/tr) 约束求解 (PU132 update 逐步移植)。
     *
     * <p>逐步解释:</p>
     * <p>1. progress += 单位本帧移动距离 (deltaLen), 作为摆动时间源;
     * <br>2. 根节方向 = 指向目标位置的角度, 相对"单位正后方"限制在 ±angleLimit;
     * <br>3. 后续节 = 指向自身目标点的角度, 相对上一节限制在 ±angleLimit;
     * <br>4. 节间距用 cosDeg(swayAngle)×segmentLength: 摆动角越大, 沿轴距离越短。</p>
     */
    @Override
    public void update(Unit unit, UnitDecoration deco){
        FlagellaDecoration d = (FlagellaDecoration)deco;
        float dLen = unit.deltaLen();
        d.progress += dLen;
        Tmp.v1.trns(unit.rotation - 90f, x, y).add(unit);

        FlagellaSegment c = d.root;
        int idx = 0;
        while(c != null){
            // 目标点跟随单位移动
            Tmp.v2.trns(c.tr, dLen);
            c.tx += Tmp.v2.x;
            c.ty += Tmp.v2.y;
            c.length = offset(d, idx);
            if(c.prev == null){
                // 根节: 相对"单位正后方"限幅
                c.tr = clampedAngle(Tmp.v1.angleTo(c.tx, c.ty), unit.rotation + 180f, angleLimit);
                Tmp.v2.trns(c.tr, c.length).add(Tmp.v1);
            }else{
                FlagellaSegment p = c.prev;
                c.tr = clampedAngle(Angles.angle(p.tx, p.ty, c.tx, c.ty), p.tr, angleLimit);
                Tmp.v2.trns(c.tr, c.length).add(p.tx, p.ty);
            }
            c.tx = Tmp.v2.x;
            c.ty = Tmp.v2.y;

            c = c.next;
            idx++;
        }
        // 第二遍: 从目标位置 + 摆动角推算实际绘制位置 (x/y)
        idx = 0;
        c = d.root;
        while(c != null){
            float rot = c.tr + swayAngle(d, idx);

            if(c.prev == null){
                Tmp.v2.trns(rot, segmentLength).add(Tmp.v1);
            }else{
                FlagellaSegment p = c.prev;
                Tmp.v2.trns(rot, segmentLength).add(p.x, p.y);
            }
            c.x = Tmp.v2.x;
            c.y = Tmp.v2.y;

            c = c.next;
            idx++;
        }
    }

    @Override
    public void draw(Unit unit, UnitDecoration deco){
        FlagellaDecoration d = (FlagellaDecoration)deco;
        FlagellaSegment cur = d.end;

        Tmp.v1.trns(unit.rotation - 90f, x, y).add(unit);
        int regL = regions.length - 1;
        int idx = 0;
        UnitType t = unit.type;
        float z = Draw.z();
        // 绘制层级: 跟随单位所处图层 (飞行/地面), 并压到单位本体之下 (Layer.bullet - 1f 上限)
        float sz = unit.elevation > 0.5f ? (t.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : t.groundLayer + Mathf.clamp(t.hitSize / 4000f, 0, 0.01f);
        sz = Math.min(sz - 0.01f, Layer.bullet - 1f);

        // 从尾尖 (end) 往回画到尾根, 贴图按节数比例从 tail-0 过渡到 tail-end
        while(cur != null){
            int reg = Mathf.clamp(regL - Mathf.round((idx / (float)segments) * regL), 0, regL);
            TextureRegion region = cur == d.end ? end : regions[reg];
            float ssize = Math.max(region.width, region.height) * Draw.scl * 1.6f;

            unit.type.applyColor(unit);
            Lines.stroke(region.height * Draw.scl);
            if(cur.prev == null){
                Tmp.v2.set(cur.x, cur.y).sub(Tmp.v1).setLength(region.width * Draw.scl).add(Tmp.v1);

                Draw.z(sz);
                Drawf.shadow((Tmp.v1.x + Tmp.v2.x) / 2f, (Tmp.v1.y + Tmp.v2.y) / 2f, ssize, 0.6f);
                Draw.z(z);

                Lines.line(region, Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y, false);
            }else{
                FlagellaSegment pr = cur.prev;
                Tmp.v2.set(cur.x, cur.y).sub(pr.x, pr.y).setLength(region.width * Draw.scl).add(pr.x, pr.y);

                Draw.z(sz);
                Drawf.shadow((pr.x + Tmp.v2.x) / 2f, (pr.y + Tmp.v2.y) / 2f, ssize, 0.6f);
                Draw.z(z);

                Lines.line(region, pr.x, pr.y, Tmp.v2.x, Tmp.v2.y, false);
            }

            idx++;
            cur = cur.prev;
        }
        Draw.reset();
    }

    /** 沿轴节间距 = cosDeg(摆动角) × segmentLength (PU132 offset) */
    float offset(FlagellaDecoration d, int idx){
        return Mathf.cosDeg(swayAngle(d, idx)) * segmentLength;
    }

    /** 摆动角: sin 波形, 相位按节偏移 swayOffset, 强度从根部渐变到末端 (PU132 swayAngle) */
    float swayAngle(FlagellaDecoration d, int idx){
        return Mathf.sin(d.progress - (idx * swayOffset), swayScl, Mathf.lerp(startIntensity, endIntensity, idx / (segments - 1f)));
    }

    /**
     * 初始化: 在单位正后方排成一列直线 (间隔 segmentLength), 方向 = 单位正后方 (PU132 added)。
     */
    @Override
    public void added(Unit unit, UnitDecoration deco){
        FlagellaDecoration d = (FlagellaDecoration)deco;

        float ox = Angles.trnsx(unit.rotation + 180f, segmentLength),
        oy = Angles.trnsy(unit.rotation + 180f, segmentLength);

        Tmp.v1.trns(unit.rotation - 90f, x, y).add(unit);

        FlagellaSegment last = null;
        for(int i = 0; i < segments; i++){
            FlagellaSegment c = new FlagellaSegment();
            c.tx = (ox * (i + 1f)) + Tmp.v1.x;
            c.ty = (oy * (i + 1f)) + Tmp.v1.y;
            c.tr = unit.rotation + 180f;
            c.length = segmentLength;
            if(last == null){
                d.root = c;
            }else{
                c.prev = last;
                last.next = c;
            }
            d.end = c;

            last = c;
        }
    }

    /** 图标合成: 把所有尾节贴图送入描边器 (PU132 drawIcon) */
    @Override
    public void drawIcon(Func<TextureRegion, Pixmap> prov, Pixmap icon, Func<TextureRegion, TextureRegion> outliner){
        for(TextureRegion region : regions){
            outliner.get(region);
        }
        outliner.get(end);
    }

    /** PU132 clampedAngle: 将 angle 限制在 relative ± limit 范围内 (处理角度环绕) */
    static float clampedAngle(float angle, float relative, float limit){
        return Mathf.clamp(Angles.angleDist(angle, relative), -limit, limit) + relative;
    }

    /** 鞭毛尾巴状态实例 (每单位独立) */
    static class FlagellaDecoration extends UnitDecoration{
        float progress;
        FlagellaSegment root, end;

        public FlagellaDecoration(UnitDecorationType type){
            super(type);
        }
    }

    /** 单节数据: 目标位置 (tx/ty/tr) 与绘制位置 (x/y), 双向链表 */
    static class FlagellaSegment{
        float tx, ty, tr, length;
        float x, y;

        FlagellaSegment next, prev;
    }
}
