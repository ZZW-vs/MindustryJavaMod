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
 * 鞭毛尾巴装饰 (PU132 type/decal/FlagellaDecorationType 的运动学重写版,
 * thalassophobia 的 4 段贴图 × 15 节长尾)
 *
 * <p>★ 为什么重写: PU132 原版用"目标点物理模拟"(两遍约束求解),
 * 在 v158 的海军单位上问题很多 — 转向时目标点滞后导致尾巴抽搐蜷缩
 * (摆动方向与转向相反时最明显), 上岸时碰撞抖动会让尾巴完全错乱。</p>
 *
 * <p>运动学原理 (无状态、纯计算, 任何情况下都平滑):</p>
 * <p>1. 尾根锚定在单位后方 (x, y 局部偏移), 朝向 = 单位正后方 (rotation + 180°);
 * <br>2. 逐节向外延伸: 每节沿 (正后方 + 摆动角) 方向走 segmentLength,
 *    摆动角 = sin(progress - idx×swayOffset, swayScl, 强度渐变),
 *    强度从根部 startIntensity 渐变到末端 endIntensity (末端甩得最厉害);
 * <br>3. progress 纯时间驱动 (phaseSpeed/tick), 与移动速度/水深完全解耦 —
 *    水里陆上、加减速、原地转身全程匀速丝滑;
 * <br>4. 单位转向时整条尾巴跟着旋转, 不会出现方向反打。</p>
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

    /** 摆动相位推进 (纯时间驱动) */
    @Override
    public void update(Unit unit, UnitDecoration deco){
        ((FlagellaDecoration)deco).progress += Time.delta * phaseSpeed;
    }

    @Override
    public void added(Unit unit, UnitDecoration deco){}

    /**
     * 逐节绘制: 从尾根到尾尖, 每节用 Lines.line 沿贴图拉伸
     * (贴图从尾根 tail-0 过渡到尾尖 tail-end), 每节独立阴影。
     */
    @Override
    public void draw(Unit unit, UnitDecoration deco){
        FlagellaDecoration d = (FlagellaDecoration)deco;

        int regL = regions.length - 1;
        UnitType t = unit.type;
        float z = Draw.z();
        // 绘制层级: 跟随单位所处图层 (飞行/地面), 并压到单位本体之下 (Layer.bullet - 1f 上限)
        float sz = unit.elevation > 0.5f ? (t.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : t.groundLayer + Mathf.clamp(t.hitSize / 4000f, 0, 0.01f);
        sz = Math.min(sz - 0.01f, Layer.bullet - 1f);

        // 尾根位置 (单位局部偏移) + 基础朝向 (单位正后方)
        float px = unit.x + Angles.trnsx(unit.rotation - 90f, x, y),
        py = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);
        float baseRot = unit.rotation + 180f;

        for(int idx = 0; idx < segments; idx++){
            // 贴图按节数比例从 tail-0 (根部) 过渡到 tail-end (尾尖)
            TextureRegion region = idx == segments - 1 ? end
                : regions[Mathf.clamp(Mathf.round(idx / (float)(segments - 1) * regL), 0, regL)];
            float ssize = Math.max(region.width, region.height) * Draw.scl * 1.6f;

            // 本节方向 = 正后方 + 摆动角 (强度根部→末端渐变)
            float ang = baseRot + swayAngle(d, idx);
            float nx = px + Angles.trnsx(ang, segmentLength),
            ny = py + Angles.trnsy(ang, segmentLength);

            unit.type.applyColor(unit);
            Lines.stroke(region.height * Draw.scl);

            // 节段中点阴影 (尾根下方)
            Draw.z(sz);
            Drawf.shadow((px + nx) / 2f, (py + ny) / 2f, ssize, 0.6f);
            Draw.z(z);

            Lines.line(region, px, py, nx, ny, false);

            px = nx;
            py = ny;
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

    /** 鞭毛尾巴状态实例 (每单位独立, 只存摆动相位) */
    static class FlagellaDecoration extends UnitDecoration{
        float progress;

        public FlagellaDecoration(UnitDecorationType type){
            super(type);
        }
    }
}
