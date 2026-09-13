package zzw.content.units.type.decal;

import arc.Core;
import arc.func.Func;
import arc.graphics.Pixmap;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

/**
 * 鞭毛尾巴装饰 (PU132 FlagellaDecorationType 的"跟随链"重写版, 参考多节单位的跟随写法,
 * thalassophobia 的 4 段贴图 × 15 节长尾)
 *
 * <p>★ 演进记录: PU132 原版"目标点物理模拟"在海军单位上抽搐蜷缩 → 纯运动学直链
 * 又太硬 (相邻节折角大, 看起来像硬链接)。本版取两者折中:</p>
 *
 * <p>1. <b>跟随链</b> (多节单位 syncToHead 同思路): 每单位持久保存各节位置,
 *    每帧第 i 节 = 第 i-1 节 + 旋转方向 × 节长 — 节与节首尾相接无缝隙;
 * <br>2. <b>基础朝向滞后</b>: 尾巴基准方向用 Vec2 向单位正后方 lerp (0.12/tick),
 *    转身时尾巴平滑甩过去 (鞭子感), 且用向量插值自动处理角度环绕, 不抽搐;
 * <br>3. <b>波形平滑</b>: 摆动角先算原始 sin 波, 再做一次邻域平均
 *    (类似多节虫 anglePhysicsSmooth), 相邻节折角大幅减小;
 * <br>4. 相位纯时间驱动 (phaseSpeed/tick), 与移动/水深/陆地完全解耦。</p>
 */
public class FlagellaDecorationType extends UnitDecorationType{
    /** 尾巴根部的挂载偏移 (单位局部坐标) */
    public float x, y;
    /** 根部/末端摆动强度 (角度) */
    public float startIntensity = 15f, endIntensity = 40f;
    /** 摆动周期缩放 (越大摆得越慢) 与相位偏移 */
    public float swayScl = 40f, swayOffset;
    /** 摆动相位速率 (tick): progress += Time.delta × phaseSpeed */
    public float phaseSpeed = 3f;
    /** 基础朝向滞后系数 (0-1, 越小转身越"甩") */
    public float followLerp = 0.12f;
    /** 贴图名 (含 create- 前缀) */
    public String name;
    /** 每节长度 / 总节数 */
    float segmentLength;
    int segments;
    TextureRegion[] regions;
    TextureRegion end;

    // 摆动角临时数组 (自备, 不用 Tmp.floats 避免与其它系统互相踩踏; 64 > 最大节数)
    private static final float[] tmpAngles = new float[64], tmpSmooth = new float[64];

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

    /** 每帧: 推进相位 + 跟随链求解 (参考多节单位 syncToHead) */
    @Override
    public void update(Unit unit, UnitDecoration deco){
        FlagellaDecoration d = (FlagellaDecoration)deco;
        d.progress += Time.delta * phaseSpeed;

        // 首帧初始化: 各节排成直线在单位正后方
        if(!d.inited){
            d.inited = true;
            d.dir.set(Angles.trnsx(unit.rotation + 180f, 1f), Angles.trnsy(unit.rotation + 180f, 1f));
            d.xs = new float[segments + 1];
            d.ys = new float[segments + 1];
            d.rot = new float[segments];
            float rx = unit.x + Angles.trnsx(unit.rotation - 90f, x, y),
            ry = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);
            for(int i = 0; i <= segments; i++){
                d.xs[i] = rx + Angles.trnsx(unit.rotation + 180f, i * segmentLength);
                d.ys[i] = ry + Angles.trnsy(unit.rotation + 180f, i * segmentLength);
            }
        }

        // 1) 基础朝向滞后: 向量插值 (自动处理角度环绕), 转身时尾巴平滑甩动
        Tmp.v1.set(Angles.trnsx(unit.rotation + 180f, 1f), Angles.trnsy(unit.rotation + 180f, 1f));
        d.dir.lerp(Tmp.v1, Mathf.clamp(followLerp * Time.delta, 0f, 1f));
        if(d.dir.len() > 0.001f) d.dir.nor();
        float baseRot = d.dir.angle();

        // 2) 尾根锚定 (单位后方偏移)
        d.xs[0] = unit.x + Angles.trnsx(unit.rotation - 90f, x, y);
        d.ys[0] = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);

        // 3) 摆动角: 原始 sin 波 → 邻域平均平滑 (消除相邻节硬折角)
        for(int i = 0; i < segments; i++){
            tmpAngles[i] = swayAngle(d, i);
        }
        for(int i = 0; i < segments; i++){
            float prev = i > 0 ? tmpAngles[i - 1] : tmpAngles[0],
            next = i < segments - 1 ? tmpAngles[i + 1] : tmpAngles[i];
            tmpSmooth[i] = (prev + tmpAngles[i] * 2f + next) / 4f;
        }

        // 4) 跟随链 (一节传一节, 同多节单位): 每节旋转向目标角渐近 (slerpDelta 处理角度环绕),
        //    根部节跟得快、末端节滞后 → 摆动沿链条向外传播, 转身时甩尾自然
        for(int i = 0; i < segments; i++){
            float target = baseRot + tmpSmooth[i];
            d.rot[i] = Mathf.slerpDelta(d.rot[i], target, 0.25f);
            float a = d.rot[i];
            d.xs[i + 1] = d.xs[i] + Angles.trnsx(a, segmentLength);
            d.ys[i + 1] = d.ys[i] + Angles.trnsy(a, segmentLength);
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
        // 绘制层级: 跟随单位所处图层 (飞行/地面), 并压到单位本体之下 (Layer.bullet - 1f 上限)
        float sz = unit.elevation > 0.5f ? (t.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : t.groundLayer + Mathf.clamp(t.hitSize / 4000f, 0, 0.01f);
        sz = Math.min(sz - 0.01f, Layer.bullet - 1f);

        for(int idx = 0; idx < segments; idx++){
            // 贴图按节数比例从 tail-0 (根部) 过渡到 tail-end (尾尖)
            TextureRegion region = idx == segments - 1 ? end
                : regions[Mathf.clamp(Mathf.round(idx / (float)(segments - 1) * regL), 0, regL)];
            float ssize = Math.max(region.width, region.height) * Draw.scl * 1.6f;

            unit.type.applyColor(unit);
            Lines.stroke(region.height * Draw.scl);

            // 节段中点阴影
            Draw.z(sz);
            Drawf.shadow((d.xs[idx] + d.xs[idx + 1]) / 2f, (d.ys[idx] + d.ys[idx + 1]) / 2f, ssize, 0.6f);
            Draw.z(z);

            Lines.line(region, d.xs[idx], d.ys[idx], d.xs[idx + 1], d.ys[idx + 1], false);
        }
        Draw.reset();
    }

    /** 摆动角: sin 波形, 相位按节偏移 swayOffset, 强度从根部渐变到末端 (PU132 swayAngle) */
    float swayAngle(FlagellaDecoration d, int idx){
        return Mathf.sin(d.progress - (idx * swayOffset), swayScl, Mathf.lerp(startIntensity, endIntensity, idx / (float)(segments - 1)));
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
     * progress = 摆动相位; dir = 滞后的基础朝向;
     * rot = 每节持久旋转 (一节传一节的载体, 向目标角渐近产生传导延迟);
     * xs/ys = 各节绘制位置 (含尾根共 segments+1 点)。
     */
    static class FlagellaDecoration extends UnitDecoration{
        float progress;
        final Vec2 dir = new Vec2();
        float[] xs, ys, rot;
        boolean inited;

        public FlagellaDecoration(UnitDecorationType type){
            super(type);
        }
    }
}
