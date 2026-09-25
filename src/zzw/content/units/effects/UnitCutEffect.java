package zzw.content.units.effects;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Shaders;
import mindustry.type.UnitType;

/**
 * 单位被切割效果 (v155.4 完整移植版, 使用 FrameBuffer + erase blending 实现真正的切割)
 *
 * 原版机制 (PU132 unity.entities.effects.UnitCutEffect):
 * - 创建两个 EffectState, 沿切割方向将单位分为两半
 * - 每半以单位贴图渲染, 用 stencilShader + effectBuffer 切割显示一半
 * - 持续 40f + hitSize/20f, 末期爆炸 + 烟尘
 *
 * v155.4 实现:
 * - 使用 FrameBuffer (Vars.renderer.effectBuffer) + erase blending 替代 stencilShader
 * - 原理: 将单位渲染到 FrameBuffer, 再用 erase blending 擦除半平面 (切割线一侧)
 * - 用 Shaders.screenspace 将 FrameBuffer blit 到屏幕
 * - ★ Draw.stencil() 在 Mindustry GL 上下文中无效 (无 stencil buffer), 改用此方案
 *
 * 触发位置: 当 EndCutterLaserBulletType 击杀大单位时调用 createCut()
 *
 * 参考: PU132 main/src/unity/entities/effects/UnitCutEffect.java
 */
public class UnitCutEffect {
    static Vec2 tmpPoint = new Vec2(), tmpPoint2 = new Vec2();

    /** 切割特效持续时间 (与 cutEffectEntity 的 lifetime 一致, 用于延迟 remove unit) */
    public static final float CUT_DURATION = 80f;

    /** erase blending: GL_ZERO(0), GL_ONE_MINUS_SRC_ALPHA(771) — 擦除目标像素 */
    private static final Blending eraseBlending = new Blending(0, 771);

    /** 切割数据 (每次切割创建 2 个, 分别代表上下两半) */
    public static class CutData {
        public Unit unit;
        public TextureRegion region;  // ★ 保存 unit.type.region, 避免 unit 被 remove 后访问失败
        public float unitRotation;     // ★ 保存 unit.rotation, 避免 unit 被 remove 后访问失败
        public Vec2 cutDirection = new Vec2();  // 切割方向 (单位中心 → 切割线)
        public float cutRotation = 0f;            // 切割方向角度 (cutDirection.z 在 PU132)
        public float rotationVelocity = 0f;
        public float rotationOffset = 0f;
        public Vec2 vel = new Vec2();
        public Vec2 offset = new Vec2();
        public float startX, startY;
        public float lifetime;
        public boolean exploded = false;
        public float hitSize;
        public float drag;
        public float elevation;
        public UnitType type;
    }

    /**
     * 创建切割效果: 沿激光方向将单位分为两半飞出
     * PU132 UnitCutEffect.createCut 完整移植
     *
     * @param unit 被切割的单位
     * @param x 激光起点 x
     * @param y 激光起点 y
     * @param x2 激光终点 x
     * @param y2 激光终点 y
     */
    public static void createCut(Unit unit, float x, float y, float x2, float y2) {
        // ★ 移除 isValid() 检查: unit 可能已被 kill() → remove(), isValid() 返回 false
        // 但仍需创建切割效果 (使用深拷贝的 region 数据)
        if (unit == null || unit.type == null) return;

        // PU132: Intersector.nearestSegmentPoint(x, y, x2, y2, unit.x, unit.y, tmpPoint);
        // tmpPoint.sub(unit); tmpPoint.limit(unit.hitSize / 4f);
        Intersector.nearestSegmentPoint(x, y, x2, y2, unit.x, unit.y, tmpPoint);
        tmpPoint.sub(unit.x, unit.y);
        tmpPoint.limit(unit.hitSize / 4f);
        float rot = tmpPoint.angle();

        unit.hitTime = 0f;

        // ★ 从 Groups.draw 中移除单位, 防止引擎自动绘制 (避免与切割特效重叠)
        // 不调用 unit.remove() 以保持 added = true, 让 unit.draw() 仍可正常工作
        Groups.draw.remove(unit);

        // PU132: 创建两半效果 (cutDirection.z = rot + (i * 180f))
        for (int i = 0; i < 2; i++) {
            CutData d = new CutData();
            d.unit = unit;
            // ★ 深拷贝渲染数据, 避免 unit 被 remove 后访问失败
            d.region = unit.type.region;
            d.unitRotation = unit.rotation;
            d.cutDirection.set(tmpPoint);
            d.cutRotation = rot + (i * 180f);
            // PU132: l.rotationVelocity = -(Mathf.signs[i] * 1.2f) + Mathf.range(0.7f)
            d.rotationVelocity = -(Mathf.signs[i] * 1.2f) + Mathf.range(0.7f);
            d.offset.setZero();
            d.startX = unit.x;
            d.startY = unit.y;
            // PU132: l.vel.trns(rot + 180f + (i * 180f), unit.hitSize / 60f)
            d.vel.trns(rot + 180f + (i * 180f), unit.hitSize / 60f);
            // 实际持续 60 + hitSize/15, 保存到 CutData 用于判断爆炸时机 (延长停留时间)
            d.lifetime = 60f + (unit.hitSize / 15f) + Mathf.range(3f, 6f);

            d.hitSize = unit.hitSize;
            d.drag = Math.min(unit.drag, 0.07f);
            d.elevation = unit.elevation;
            d.type = unit.type;

            cutEffectEntity.at(unit.x, unit.y, 0f, d);
        }

        // PU132: UnityFx.tenmeikiriCut.at(unit.x + tmpPoint.x, unit.y + tmpPoint.y, rot + 90f, unit.hitSize * 1.5f)
        cutFlashEffect.at(unit.x + tmpPoint.x, unit.y + tmpPoint.y, rot + 90f, unit.hitSize * 1.5f);
        
        // ★ 添加金属熔化效果
        cutScarEffect.at(unit.x, unit.y, 0f, unit.hitSize);
    }

    /**
     * 切割瞬间闪光特效 (PU132 UnityFx.tenmeikiriCut 简化版)
     * PU132 原版: Drawf.tri 双向三角闪光, scarColor→endColor 渐变
     */
    public static final Effect cutFlashEffect = new Effect(20f, 200f, e -> {
        float size = e.rotation;
        Draw.color(Color.valueOf("f53036"), Color.white, e.fout());
        Draw.blend(Blending.additive);
        // 中心十字闪光
        Fill.circle(e.x, e.y, size * 0.3f * e.fout());
        Lines.stroke(3f * e.fout());
        Lines.lineAngleCenter(e.x, e.y, e.rotation, size * e.fout());
        Lines.lineAngleCenter(e.x, e.y, e.rotation + 90f, size * 0.5f * e.fout());
        // 周围粒子
        for (int i = 0; i < 6; i++) {
            float a = (360f / 6f) * i + e.rotation;
            float d = (1f - e.fout()) * size * 0.5f;
            Vec2 v = Tmp.v1.trns(a, d).add(e.x, e.y);
            Fill.circle(v.x, v.y, 3f * e.fout());
        }
        Draw.blend();
        Draw.color();
    });

    /**
     * 切割疤痕特效 - FO风格金属熔化效果
     */
    public static final Effect cutScarEffect = new Effect(60f, 600f, e -> {
        if (!(e.data instanceof CutData)) return;
        CutData d = (CutData) e.data;
        
        // FO风格熔化金属颜色 (鞍棕色 → 橙红色 → 金色 → 白色)
        Color meltColor1 = Color.valueOf("#8B4513"); // 鞍棕色 (基础)
        Color meltColor2 = Color.valueOf("#FF4500"); // 橙红色 (熔化)
        Color meltColor3 = Color.valueOf("#FFD700"); // 金色 (高温)
        Color meltColor4 = Color.white; // 白色 (炽热)
        
        float time = e.time / e.lifetime;
        float size = d.hitSize * 1.2f; // 增加疤痕尺寸
        
        // ★ 增强FO风格效果: 多层渲染
        Draw.blend(Blending.additive);
        
        // 1. 熔化核心区域 - 增强高温效果
        float coreSize = size * 0.4f * (1f + Mathf.sin(time * 8f) * 0.3f);
        Draw.color(meltColor1, meltColor2, time);
        Fill.circle(e.x, e.y, coreSize);
        
        // 2. 高温中心 - 白色炽热点
        Draw.color(meltColor3, meltColor4, Mathf.sin(time * Mathf.PI));
        Fill.circle(e.x, e.y, coreSize * 0.3f);
        
        // 3. 熔化飞溅效果 - 增加数量和强度
        for (int i = 0; i < 12; i++) {
            float angle = (360f / 12f) * i + time * 60f;
            float dist = size * (0.6f + Mathf.random(0.4f));
            Vec2 pos = Tmp.v1.trns(angle, dist).add(e.x, e.y);
            float meltSize = size * 0.15f * (1f - time);
            
            // 随机选择熔化颜色
            Draw.color(meltColor2, meltColor3, Mathf.random());
            Fill.circle(pos.x, pos.y, meltSize * (1f + Mathf.sin(time * 12f + i) * 0.4f));
        }
        
        // 4. 熔化流痕 - 增加数量和长度
        for (int i = 0; i < 6; i++) {
            float angle = d.cutRotation + 60f + i * 60f;
            float dist = size * time * 1.2f; // 增加流痕长度
            Vec2 end = Tmp.v1.trns(angle, dist).add(e.x, e.y);
            
            // 渐变流痕颜色
            Draw.color(meltColor1, meltColor2, 1f - time);
            Lines.stroke(3f * (1f - time));
            Lines.line(e.x, e.y, end.x, end.y);
            
            // 流痕末端飞溅
            if (time > 0.7f) {
                Vec2 splash = Tmp.v1.trns(angle + 90f, 5f).add(end);
                Draw.color(meltColor3, meltColor4, (time - 0.7f) * 3.33f);
                Fill.circle(splash.x, splash.y, 2f * (1f - time));
            }
        }
        
        // 5. 火花效果 - 模拟熔化产生的火花
        for (int i = 0; i < 16; i++) {
            float angle = Mathf.random(360f);
            float dist = Mathf.random(size * 0.3f, size * 0.8f);
            Vec2 sparkPos = Tmp.v1.trns(angle, dist).add(e.x, e.y);
            float sparkSize = Mathf.random(1f, 3f) * (1f - time);
            
            Draw.color(meltColor3, meltColor4, Mathf.random());
            Fill.circle(sparkPos.x, sparkPos.y, sparkSize);
        }
        
        Draw.blend();
        Draw.color();
    });

    /**
     * 切割实体效果 (使用 FrameBuffer + erase blending 实现真正的切割)
     *
     * PU132 原版 draw():
     * 1. 摄像机偏移 (offset)
     * 2. effectBuffer.begin()
     * 3. unit.draw() (渲染单位到 FrameBuffer)
     * 4. Fill.quad (绘制 quad 作为 stencil mask)
     * 5. effectBuffer.end()
     * 6. Draw.blit(effectBuffer, stencilShader)
     *
     * v155.4 实现 (FrameBuffer + erase blending):
     * 1. effectBuffer.begin() — 清空为透明
     * 2. Draw.rect(region) — 用 normal blending 渲染单位贴图到 FrameBuffer
     * 3. Draw.blend(eraseBlending) — 切换到擦除混合
     * 4. Fill.quad — 绘制半平面 mask, 擦除切割线一侧
     * 5. Draw.blend() — 恢复 normal blending
     * 6. effectBuffer.end()
     * 7. Draw.blit(effectBuffer, Shaders.screenspace) — blit 到屏幕
     *
     * ★ 不使用 Draw.stencil(): Mindustry GL 上下文无 stencil buffer, stencil 操作无效
     * ★ 不使用摄像机偏移: 直接将单位绘制到视觉位置 (startX + offset.x), 效果相同
     */
    public static final Effect cutEffectEntity = new Effect(120f, 400f, e -> {
        if (!(e.data instanceof CutData)) return;
        CutData d = (CutData) e.data;

        // === PU132 update() 逻辑同步更新 (因 Effect 无 update 钩子) ===
        d.offset.add(d.vel.x * Time.delta, d.vel.y * Time.delta);
        d.rotationOffset += Time.delta * d.rotationVelocity;
        float drag = d.drag;
        d.vel.scl(1f - drag);
        d.rotationVelocity *= 1f - drag;

        // 持续烟尘 (PU132: Fx.fallSmoke, chanceDelta(0.4f * hitSize/45))
        if (d.hitSize > 0 && Mathf.chanceDelta(0.4f * (d.hitSize / 45f))) {
            tmpPoint2.trns(d.cutRotation + d.rotationOffset, 0f, Mathf.range(d.hitSize / 2f))
                .add(d.cutDirection.x + d.offset.x, d.cutDirection.y + d.offset.y)
                .add(d.startX, d.startY);
            Fx.fallSmoke.at(tmpPoint2.x, tmpPoint2.y);
        }

        // 末期爆炸 (PU132 update(): time >= lifetime 时触发) - 降低爆炸威力
        if (!d.exploded && e.time >= d.lifetime - 1f) {
            d.exploded = true;
            float ex = d.startX + d.cutDirection.x + d.offset.x;
            float ey = d.startY + d.cutDirection.y + d.offset.y;
            Effect.shake(d.hitSize / 6f, d.hitSize / 6f, ex, ey); // 减少震动
            Fx.dynamicExplosion.at(ex, ey, d.hitSize / 12f); // 减小爆炸尺寸
            // 移除 scorch 效果，减少爆炸强度
            if (d.type != null) d.type.deathSound.at(ex, ey);
        }

        // ★ 爆炸后或 region 无效时不再渲染
        if (d.exploded || d.region == null || !d.region.found() || d.type == null) return;

        // === PU132 draw() 完整移植 (FrameBuffer + erase blending) ===
        float size = d.hitSize;

        // PU132: z = unit.elevation > 0.5 ? (lowAltitude ? flyingUnitLow : flyingUnit) : groundLayer + clamp
        float z = d.elevation > 0.5f
            ? (d.type.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit)
            : d.type.groundLayer + Mathf.clamp(d.type.hitSize / 4000f, 0f, 0.01f);

        // 单位视觉位置 (startX + offset, 替代 PU132 的摄像机偏移)
        float ux = d.startX + d.offset.x;
        float uy = d.startY + d.offset.y;

        Draw.draw(z, () -> {
            // === 渲染到 FrameBuffer ===
            FrameBuffer buffer = Vars.renderer.effectBuffer;
            buffer.begin(Color.clear);
            Draw.proj(Core.camera);

            // 1. 绘制单位贴图 (normal blending) - 添加FO风格熔化色彩
            Draw.blend();
            
            // 为碎片添加FO风格熔化色彩效果
            if (d.type != null) {
                // FO风格金属熔化颜色 (鞍棕色 → 橙红色 → 金色渐变)
                Color baseColor = Color.valueOf("#8B4513"); // 基础鞍棕色
                Color meltColor = Color.valueOf("#FF4500"); // 熔化橙红色
                Color hotColor = Color.valueOf("#FFD700"); // 高温金色
                
                // 根据时间渐变熔化效果
                float time = e.time / e.lifetime;
                Color finalColor = baseColor.lerp(meltColor, time).lerp(hotColor, Mathf.sin(time * Mathf.PI) * 0.5f);
                
                // 添加闪烁效果模拟熔化的高温
                float flicker = 1f + Mathf.sin(Time.time * 15f) * 0.1f;
                finalColor.mul(flicker);
                
                // 应用熔化色彩
                Draw.color(finalColor);
                Draw.rect(d.region, ux, uy, d.unitRotation + d.rotationOffset - 90f);
                Draw.color(); // 重置颜色
            } else {
                // 默认熔化效果
                Color meltColor = Color.valueOf("#8B4513");
                float time = e.time / e.lifetime;
                meltColor.lerp(Color.valueOf("#FF4500"), time);
                Draw.color(meltColor);
                Draw.rect(d.region, ux, uy, d.unitRotation + d.rotationOffset - 90f);
                Draw.color();
            }
            
            Draw.flush();

            // 2. 擦除切割线一侧 (erase blending: GL_ZERO, GL_ONE_MINUS_SRC_ALPHA)
            //    PU132 原版: 绘制绿色 quad, stencilShader 将绿色像素设为透明
            //    v155.4: 用 erase blending 直接擦除
            Draw.blend(eraseBlending);
            Draw.color(Color.white);  // alpha=1 → 完全擦除

            // mask quad: 半平面, 覆盖切割线一侧 (与 PU132 相同的顶点计算)
            // PU132: dx={-1,-1,1,1}, dy={0,1,1,0}
            // tmpPoint2.trns(cutDirection.z + rotationOffset, dy[i] * size * 1.5f, dx[i] * size * 1.5f)
            //   .add(cutDirection.x, cutDirection.y).add(unit)
            float[] vx = new float[4];
            float[] vy = new float[4];
            int[] dx = {-1, -1, 1, 1};
            int[] dy = {0, 1, 1, 0};
            for (int i = 0; i < 4; i++) {
                tmpPoint2.trns(d.cutRotation + d.rotationOffset,
                        dy[i] * size * 1.5f,
                        dx[i] * size * 1.5f)
                    .add(d.cutDirection.x, d.cutDirection.y)
                    .add(ux, uy);
                vx[i] = tmpPoint2.x;
                vy[i] = tmpPoint2.y;
            }
            Fill.quad(vx[0], vy[0], vx[1], vy[1], vx[2], vy[2], vx[3], vy[3]);

            // 3. 恢复 normal blending
            Draw.flush();
            Draw.blend();
            Draw.color();

            buffer.end();

            // === blit FrameBuffer 到屏幕 ===
            Draw.blit(buffer, Shaders.screenspace);
        });
    });
}
