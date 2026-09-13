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
import arc.util.Time;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

/**
 * 鞭毛尾巴装饰 (多节单位同款动态: SegmentWormEntity.updateSegmentsLocal 的完整移植,
 * thalassophobia 的 4 段贴图长尾)
 *
 * <p>★ 动态原理 = 多节虫段身跟随 (一节传一节):</p>
 * <p>1. <b>理想位置</b>: 每节的理想点 = 上一节正后方 spacing/2 处;
 * <br>2. <b>angTo</b>: 该节当前位置 → 理想点的角度;
 * <br>3. <b>角度限幅+平滑</b> (PU132 原版公式):
 *    rotation = angTo - angleDistSigned(angTo, 上节朝向, angleLimit) × (1 - anglePhysicsSmooth)
 *    — 相对上节最多转 angleLimit 度, 且差值按 smooth 系数衰减 (柔顺不生硬);
 * <br>4. <b>随动</b>: 每节沿自身朝向移动"上一节的位移量" (deltaLen 传递);
 * <br>5. <b>关节拉回力</b> (jointStrength): 把该节拉回理想位置, 力沿链条向后传播
 *    segmentCast 节 (越靠后力越小) — 产生甩尾回弹;
 * <br>6. <b>轻微摆动</b> (wobble): 目标角叠加小幅 sin 波 (多节虫 wobble 同款),
 *    静止时尾巴也有微弱摆动。</p>
 *
 * <p>与之前版本的区别: 不再有任何"全体锁相"的 sin 波, 摆动只作为小幅度扰动,
 * 主动态完全由多节虫式的跟随物理驱动。</p>
 */
public class FlagellaDecorationType extends UnitDecorationType{
    /** 尾巴根部的挂载偏移 (单位局部坐标) */
    public float x, y;
    /** 相对上一节的最大转角 (度) — PU132 worm angleLimit */
    public float angleLimit = 30f;
    /** 角度平滑系数 (0-1, 越大越柔顺) — PU132 worm anglePhysicsSmooth */
    public float anglePhysicsSmooth = 0.5f;
    /** 关节拉回强度 (0-1) — PU132 worm jointStrength */
    public float jointStrength = 0.6f;
    /** 拉回力向后传播的节数 — PU132 worm segmentCast */
    public int segmentCast = 6;
    /** 摆动周期缩放 (越大摆得越慢) 与相位偏移 */
    public float swayScl = 80f, swayOffset = 15f;
    /** 摆动幅度 (度, 多节虫 wobble 同款轻微晃动) 与相位速率 */
    public float wobbleAmp = 5f, phaseSpeed = 1f;
    /** 贴图名 (含 create- 前缀) */
    public String name;
    /** 每节长度 / 总节数 */
    float segmentLength;
    int segments;
    TextureRegion[] regions;
    TextureRegion end;

    // 摆动角临时数组 (自备, 避免与其它系统互相踩踏; 64 > 最大节数)
    private static final float[] tmpWobble = new float[64];

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

    /** 每帧: 多节虫式跟随物理求解 (updateSegmentsLocal 逐步移植) */
    @Override
    public void update(Unit unit, UnitDecoration deco){
        FlagellaDecoration d = (FlagellaDecoration)deco;
        d.progress += Time.delta * phaseSpeed;

        // 首帧初始化: 各节排成直线在单位正后方
        if(!d.inited){
            d.inited = true;
            d.xs = new float[segments + 1];
            d.ys = new float[segments + 1];
            d.rot = new float[segments];
            float rx = unit.x + Angles.trnsx(unit.rotation - 90f, x, y),
            ry = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);
            for(int i = 0; i <= segments; i++){
                d.xs[i] = rx + Angles.trnsx(unit.rotation + 180f, i * segmentLength);
                d.ys[i] = ry + Angles.trnsy(unit.rotation + 180f, i * segmentLength);
            }
            for(int i = 0; i < segments; i++){
                d.rot[i] = unit.rotation + 180f;
            }
        }

        // 点 0 = 尾根锚点 (单位后方偏移), "上一节"从锚点开始
        float lastX = unit.x + Angles.trnsx(unit.rotation - 90f, x, y),
        lastY = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);
        float lastRot = unit.rotation;
        float lastDelta = unit.deltaLen();
        d.xs[0] = lastX;
        d.ys[0] = lastY;

        // 摆动扰动 (wobble): 小幅 sin 波, 相位按节传播
        for(int i = 0; i < segments; i++){
            tmpWobble[i] = Mathf.sin(d.progress - (i * swayOffset), swayScl, wobbleAmp);
        }

        for(int i = 0; i < segments; i++){
            // ★ Step 1: 理想位置 = 上一节正后方 spacing/2 处 (PU132 updateSegmentsLocal Step1)
            float idealX = lastX + Angles.trnsx(lastRot + 180f, segmentLength / 2f),
            idealY = lastY + Angles.trnsy(lastRot + 180f, segmentLength / 2f);

            // ★ Step 2: angTo = 该节当前位置 → 理想位置
            float angTo = Angles.angle(d.xs[i + 1], d.ys[i + 1], idealX, idealY);

            // ★ Step 3: 角度限幅 + 平滑 (PU132 原版公式)
            //   rotation = angTo - angleDistSigned(angTo, 上节朝向, angleLimit) × (1 - smooth)
            float angleDiff = angleDistSigned(angTo, lastRot, angleLimit);
            d.rot[i] = angTo - angleDiff * (1f - anglePhysicsSmooth) + tmpWobble[i];

            // ★ Step 4: 该节沿自身朝向移动"上一节的位移量" (deltaLen 传递)
            d.xs[i + 1] += Angles.trnsx(d.rot[i], lastDelta);
            d.ys[i + 1] += Angles.trnsy(d.rot[i], lastDelta);

            // ★ Step 5-6: 关节拉回力 (jointStrength), 沿链条向后传播 segmentCast 节
            float pullX = Angles.trnsx(d.rot[i], segmentLength) + d.xs[i + 1] - idealX,
            pullY = Angles.trnsy(d.rot[i], segmentLength) + d.ys[i + 1] - idealY;
            pullX *= Mathf.clamp(jointStrength * Time.delta);
            pullY *= Mathf.clamp(jointStrength * Time.delta);
            int cast = segmentCast;
            int idx = i + 1;
            while(cast > 0 && idx <= segments){
                float scl = cast / (float)segmentCast;
                d.xs[idx] -= pullX * scl;
                d.ys[idx] -= pullY * scl;
                idx++;
                cast--;
            }

            // ★ Step 8: 下一节的"上一节" = 当前节
            lastX = d.xs[i + 1];
            lastY = d.ys[i + 1];
            lastRot = d.rot[i];
            lastDelta = segmentLength * 0.1f; // 尾部无实体位移, 用固定微量维持链条活跃
        }
    }

    @Override
    public void added(Unit unit, UnitDecoration deco){}

    /**
     * 逐节绘制: 相邻节点之间用 Lines.line 沿贴图拉伸
     * (贴图从尾根 tail-0 过渡到尾尖 tail-end), 每节独立阴影。
     */
    @Override
    public void draw(Unit unit, UnitDecoration deco){
        FlagellaDecoration d = (FlagellaDecoration)deco;
        if(!d.inited) return;

        int regL = regions.length - 1;
        UnitType t = unit.type;
        float z = Draw.z();
        // 绘制层级: 跟随单位所处图层, 并压到单位本体之下 (Layer.bullet - 1f 上限)
        float sz = unit.elevation > 0.5f ? (t.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : t.groundLayer + Mathf.clamp(t.hitSize / 4000f, 0, 0.01f);
        sz = Math.min(sz - 0.01f, Layer.bullet - 1f);

        for(int idx = 0; idx < segments; idx++){
            // 贴图按节数比例从 tail-0 (根部) 过渡到 tail-end (尾尖)
            TextureRegion region = idx == segments - 1 ? end
                : regions[Mathf.clamp(Mathf.round(idx / (float)(segments - 1) * regL), 0, regL)];
            float ssize = Math.max(region.width, region.height) * Draw.scl * 1.6f;

            unit.type.applyColor(unit);
            Lines.stroke(region.height * Draw.scl);

            Draw.z(sz);
            Drawf.shadow((d.xs[idx] + d.xs[idx + 1]) / 2f, (d.ys[idx] + d.ys[idx + 1]) / 2f, ssize, 0.6f);
            Draw.z(z);

            Lines.line(region, d.xs[idx], d.ys[idx], d.xs[idx + 1], d.ys[idx + 1], false);
        }
        Draw.reset();
    }

    /** 带符号角度差, 超过 start 才返回差值 (PU132 Utils.angleDistSigned L187, 虫用限幅版) */
    private static float angleDistSigned(float a, float b, float start){
        a += 360f;
        a %= 360f;
        b += 360f;
        b %= 360f;
        float d = Math.abs(a - b) % 360f;
        int sign = (a - b >= 0f && a - b <= 180f) || (a - b <= -180f && a - b >= -360f) ? 1 : -1;
        float dst = (d > 180f ? 360f - d : d) * sign;
        if(Math.abs(dst) > start){
            return dst > 0 ? dst - start : dst + start;
        }
        return 0f;
    }

    /** 图标合成: 把所有尾节贴图送入描边器 (PU132 drawIcon) */
    @Override
    public void drawIcon(Func<TextureRegion, Pixmap> prov, Pixmap icon, Func<TextureRegion, TextureRegion> outliner){
        for(TextureRegion region : regions){
            outliner.get(region);
        }
        outliner.get(end);
    }

    /**
     * 鞭毛尾巴状态实例 (每单位独立):
     * progress = 摆动相位; rot = 每节旋转; xs/ys = 各节点位置 (点0=尾根锚点, 共 segments+1 点)。
     */
    static class FlagellaDecoration extends UnitDecoration{
        float progress;
        float[] xs, ys, rot;
        boolean inited;

        public FlagellaDecoration(UnitDecorationType type){
            super(type);
        }
    }
}
