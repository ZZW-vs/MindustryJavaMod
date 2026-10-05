package zzw.content.units.effects;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.LegsUnit;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import zzw.content.graphics.ShaderLib;

/**
 * 单位被切割效果 —— PU132 {@code unity.entities.effects.UnitCutEffect} 的完整移植版。
 *
 * <p>原版机制 (PU132): tenmeikiri 激光击杀大型单位时, 单位不是"消失", 而是被激光
 * 沿切割线切成两半, 两半分别向两侧飞出、翻转并冒烟, 最后炸成碎片。实现方式:</p>
 *
 * <ol>
 *   <li>为单位创建两个 {@link CutData} (分别代表"切割线上方的一半"和"下方的一半"),
 *       每个都有自己的偏移量 {@code offset}、旋转增量 {@code rotationOffset} 与飞行速度
 *       {@code vel};</li>
 *   <li>绘制时把整个单位 (真实贴图/腿/武器) 用摄像机偏移渲染进
 *       {@code Vars.renderer.effectBuffer};</li>
 *   <li>再在缓冲里铺一块 <b>纯绿色 quad</b> 盖住"要切掉的那一半";</li>
 *   <li>用 {@link ShaderLib.StencilShader} 把缓冲 blit 回屏幕 —— 着色器把绿色像素
 *       变成透明 (擦除), 并给紧贴擦除边界的像素叠加灼烧高光, 于是一半单位被干净地
 *       "切掉"且切口发亮;</li>
 *   <li>持续期间冒烟, 到期后爆炸 + 焦痕 + 死亡音效。</li>
 * </ol>
 *
 * <p>与 PU132 的差异 (仅实现方式, 观感与机制一致):</p>
 * <ul>
 *   <li>PU132 用池化的 {@code EffectState} 子类; 本项目改用 {@link Effect} +
 *       {@link CutData} 承载实例数据 (Effect 无 update 钩子, 故更新逻辑写在绘制回调开头);</li>
 *   <li>单位由 {@link zzw.util.AntiCheat#annihilateEntity} 剥离出实体组后仍保留对象引用,
 *       供本特效持续绘制 (与原版一致)。</li>
 * </ul>
 */
public class UnitCutEffect {
    /** 复用的临时向量 (静态, 避免每帧分配)。 */
    private static final Vec2 tmpPoint = new Vec2(), tmpPoint2 = new Vec2();

    /** 切割数据的存活时长上限 (Effect 自身的 lifetime, 保证覆盖所有 CutData)。 */
    private static final float EFFECT_LIFETIME = 120f;

    /** 切割数据 (每次切割创建 2 个, 分别代表两半)。 */
    public static class CutData {
        /** 被切割的单位对象 (已脱离实体组, 但仍可绘制)。 */
        public Unit unit;
        /** 切割方向向量 (单位中心 → 激光最近点), cutDirection.z 在原版中用 cutRotation 表示。 */
        public final Vec2 cutDirection = new Vec2();
        /** 切割方向角度 (PU132 cutDirection.z)。 */
        public float cutRotation = 0f;
        /** 每帧旋转速度 (另一半反向)。 */
        public float rotationVelocity = 0f;
        /** 累计旋转增量。 */
        public float rotationOffset = 0f;
        /** 飞出速度。 */
        public final Vec2 vel = new Vec2();
        /** 累计位移。 */
        public final Vec2 offset = new Vec2();
        /** 起始位置 (单位被切成两半时所在处)。 */
        public float startX, startY;
        /** 本半的存活时长 (到期爆炸)。 */
        public float lifetime;
        /** 是否已爆炸。 */
        public boolean exploded = false;
        /** 空气阻力 (取单位 drag 与 0.07 的较小值)。 */
        public float drag = 0f;
        /** 单位命中尺寸 / 海拔 / 图层高度。 */
        public float size = 0f, elevation = 0f;
    }

    /**
     * 创建切割效果: 沿激光方向把单位切成两半飞出。
     *
     * @param unit 被切割的单位
     * @param x    激光起点 x
     * @param y    激光起点 y
     * @param x2   激光终点 x (用于计算单位中心到激光的最近点)
     * @param y2   激光终点 y
     */
    public static void createCut(Unit unit, float x, float y, float x2, float y2) {
        if (unit == null || unit.type == null) return;

        // PU132: 取单位中心到激光线段的最近点, 限制在 hitSize/4 内 → 切割方向
        Intersector.nearestSegmentPoint(x, y, x2, y2, unit.x, unit.y, tmpPoint);
        tmpPoint.sub(unit.x, unit.y);
        tmpPoint.limit(unit.hitSize / 4f);
        float rot = tmpPoint.angle();

        unit.hitTime = 0f;

        // 从绘制组移除, 防止引擎自动绘制与切割特效重叠
        // (单位通常已被 AntiCheat.annihilateEntity 剥离)
        mindustry.gen.Groups.draw.remove(unit);

        float size = unit instanceof LegsUnit ? unit.hitSize + (unit.type.legLength * 2f) : unit.hitSize;

        for (int i = 0; i < 2; i++) {
            CutData d = new CutData();
            d.unit = unit;
            d.cutDirection.set(tmpPoint);
            d.cutRotation = rot + (i * 180f);
            d.rotationVelocity = -(Mathf.signs[i] * 1.2f) + Mathf.range(0.7f);
            d.offset.setZero();
            d.startX = unit.x;
            d.startY = unit.y;
            d.vel.trns(rot + 180f + (i * 180f), unit.hitSize / 60f);
            // PU132: lifetime = 40f + hitSize/20f + range(2,5)
            d.lifetime = 40f + (unit.hitSize / 20f) + Mathf.range(2f, 5f);
            d.drag = Math.min(unit.drag, 0.07f);
            d.size = size;
            d.elevation = unit.elevation;

            cutEffectEntity.at(unit.x, unit.y, 0f, d);
        }

        // PU132: UnityFx.tenmeikiriCut.at(unit.x + tmpPoint.x, unit.y + tmpPoint.y, rot + 90f, unit.hitSize * 1.5f)
        cutFlashEffect.at(unit.x + tmpPoint.x, unit.y + tmpPoint.y, rot + 90f, unit.hitSize * 1.5f);
    }

    /**
     * 切割瞬间闪光特效 (PU132 UnityFx.tenmeikiriCut 简化版):
     * 沿切割线方向的十字闪光 + 环形粒子。
     */
    public static final Effect cutFlashEffect = new Effect(20f, 200f, e -> {
        float size = e.rotation;
        Draw.color(Color.valueOf("f53036"), Color.white, e.fout());
        Draw.blend(arc.graphics.Blending.additive);
        Fill.circle(e.x, e.y, size * 0.3f * e.fout());
        Lines.stroke(3f * e.fout());
        Lines.lineAngleCenter(e.x, e.y, e.rotation, size * e.fout());
        Lines.lineAngleCenter(e.x, e.y, e.rotation + 90f, size * 0.5f * e.fout());
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
     * 切割实体效果 —— PU132 {@code UnitCutEffect.update/draw} 完整移植。
     *
     * <p>Effect 没有独立的 update 钩子, 因此把原版的 update 逻辑 (位移 / 旋转 /
     * 冒烟 / 到期爆炸) 放在绘制回调开头执行, 再执行 draw 部分。</p>
     */
    public static final Effect cutEffectEntity = new Effect(EFFECT_LIFETIME, 400f, e -> {
        if (!(e.data instanceof CutData d)) return;
        if (d.unit == null || d.unit.type == null) return;

        Unit unit = d.unit;
        float drag = d.drag;

        // ---- 到期: 爆炸并停止绘制 (PU132 update: time >= lifetime) ----
        if (e.time >= d.lifetime) {
            if (!d.exploded) {
                d.exploded = true;
                float ex = d.startX + d.offset.x;
                float ey = d.startY + d.offset.y;
                Effect.shake(unit.hitSize / 3f, unit.hitSize / 3f, ex, ey);
                Fx.dynamicExplosion.at(ex, ey, (unit.bounds() / 2f) / 8f);
                Effect.scorch(ex, ey, (int) (unit.hitSize / 5f));
                Fx.explosion.at(ex, ey);
                unit.type.deathSound.at(ex, ey);
            }
            return;
        }

        // ---- 运动更新 ----
        d.offset.add(d.vel.x * Time.delta, d.vel.y * Time.delta);
        d.rotationOffset += Time.delta * d.rotationVelocity;
        d.vel.scl(1f - drag);
        d.rotationVelocity *= 1f - drag;

        // ---- 持续烟尘 ----
        if (Mathf.chanceDelta(0.4f * (unit.hitSize / 45f))) {
            tmpPoint2.trns(d.cutRotation + d.rotationOffset, 0f, Mathf.range(unit.hitSize / 2f))
                .add(d.cutDirection.x + d.offset.x, d.cutDirection.y + d.offset.y)
                .add(d.startX, d.startY);
            Fx.fallSmoke.at(tmpPoint2.x, tmpPoint2.y);
        }

        // ---- 绘制 (PU132 UnitCutEffect.draw) ----
        float z = d.elevation > 0.5f
            ? (unit.type.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit)
            : unit.type.groundLayer + Mathf.clamp(unit.type.hitSize / 4000f, 0f, 0.01f);

        Draw.draw(z, () -> {
            // 1. 用摄像机偏移把"飞出位移"体现到画面 (原版做法)
            tmpPoint.set(Core.camera.position);
            Core.camera.position.set(tmpPoint).sub(d.offset);
            Core.camera.update();
            Draw.proj(Core.camera);

            // 2. 准备模板着色器: 绿色标记被擦除区域, 灼烧色随进度 lightFlame → darkFlame
            ShaderLib.StencilShader shader = ShaderLib.stencilShader();
            shader.stencilColor.set(Color.green);
            shader.heatColor.set(Pal.lightFlame).lerp(Pal.darkFlame, e.fin());

            Vars.renderer.effectBuffer.begin(Color.clear);

            // 3. 把整个单位渲染进缓冲 (真实贴图/腿/武器), 临时叠加旋转增量
            float lastRotation = unit.rotation;
            unit.rotation = lastRotation + d.rotationOffset;
            unit.draw();
            unit.rotation = lastRotation;
            Draw.reset();

            // 4. 铺绿色 quad 盖住要切掉的一半
            float[] verts = new float[8];
            int[] dx = {-1, -1, 1, 1};
            int[] dy = {0, 1, 1, 0};
            for (int i = 0; i < 4; i++) {
                tmpPoint2.trns(d.cutRotation + d.rotationOffset, dy[i] * d.size * 1.5f, dx[i] * d.size * 1.5f)
                    .add(d.cutDirection.x, d.cutDirection.y)
                    .add(unit.x, unit.y);
                verts[(i * 2)] = tmpPoint2.x;
                verts[(i * 2) + 1] = tmpPoint2.y;
            }
            Draw.color(Color.green);
            Fill.quad(verts[0], verts[1], verts[2], verts[3], verts[4], verts[5], verts[6], verts[7]);

            Vars.renderer.effectBuffer.end();

            // 5. blit 回屏幕: 着色器擦除绿色区域 + 描亮切口
            Draw.blit(Vars.renderer.effectBuffer, shader);

            // 6. 恢复摄像机
            Core.camera.position.set(tmpPoint);
            Core.camera.update();
            Draw.proj(Core.camera);
        });
    });
}