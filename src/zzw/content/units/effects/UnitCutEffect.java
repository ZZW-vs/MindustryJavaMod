package zzw.content.units.effects;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Unit;

/**
 * 单位被切割效果 (PU_V8 UnitCutEffect 简化版, 无 shader)
 *
 * 原版机制 (PU_V8 unity.entities.effects.UnitCutEffect):
 * - 创建两个 EffectState, 沿切割方向将单位分为两半
 * - 每半以单位贴图渲染, 用 stencilShader 切割显示一半
 * - 持续 40f + hitSize/20f, 末期爆炸 + 烟尘
 *
 * 简化策略 (v158 EffectState 是注解生成的 pooled entity, 不能继承):
 * - 用普通 Effect + data (CutData) 实现
 * - CutData 保存: unit 引用 + 切割方向 + offset/vel/rotationOffset/rotationVelocity
 * - 每帧 update 计算偏移, draw 绘制两半椭圆 + 切线
 * - 末期触发 Fx.dynamicExplosion + Fx.explosion + scorch
 *
 * 触发位置: 当 EndCutterLaserBulletType 击杀大单位时调用 createCut()
 *
 * 参考: PU_V8 main/src/unity/entities/effects/UnitCutEffect.java
 */
public class UnitCutEffect {
    static Vec2 tmpPoint = new Vec2(), tmpPoint2 = new Vec2();

    /** 切割数据 (每次切割创建 2 个, 分别代表上下两半) */
    public static class CutData {
        public Unit unit;
        public Vec2 cutDirection = new Vec2();
        public float cutRotation = 0f;
        public float rotationVelocity = 0f;
        public float rotationOffset = 0f;
        public Vec2 vel = new Vec2();
        public Vec2 offset = new Vec2();
        public float startX, startY;
        public float lifetime;
        public boolean exploded = false;
    }

    /**
     * 创建切割效果: 沿激光方向将单位分为两半飞出
     * @param unit 被切割的单位
     * @param x 激光起点 x
     * @param y 激光起点 y
     * @param x2 激光终点 x
     * @param y2 激光终点 y
     */
    public static void createCut(Unit unit, float x, float y, float x2, float y2) {
        if (unit == null || !unit.isValid()) return;

        // 找到激光线段上离单位最近的点
        Intersector.nearestSegmentPoint(x, y, x2, y2, unit.x, unit.y, tmpPoint);
        tmpPoint.sub(unit.x, unit.y);
        tmpPoint.limit(unit.hitSize / 4f);
        float rot = tmpPoint.angle();

        unit.hitTime = 0f;

        // 创建两半效果 (沿垂直切割方向飞开)
        for (int i = 0; i < 2; i++) {
            CutData d = new CutData();
            d.unit = unit;
            d.cutDirection.set(tmpPoint);
            d.cutRotation = rot + (i * 180f);
            d.rotationVelocity = -(Mathf.signs[i] * 1.2f) + Mathf.range(0.7f);
            d.offset.setZero();
            d.startX = unit.x;
            d.startY = unit.y;
            // 两半沿垂直切割方向飞出
            d.vel.trns(rot + 180f + (i * 180f), unit.hitSize / 60f);
            // 实际持续 40 + hitSize/20, 保存到 CutData 用于判断爆炸时机
            d.lifetime = 40f + (unit.hitSize / 20f) + Mathf.range(2f, 5f);

            cutEffectEntity.at(unit.x, unit.y, 0f, d);
        }

        // 切割瞬间红色闪光 (PU_V8: UnityFx.tenmeikiriCut)
        cutFlashEffect.at(unit.x + tmpPoint.x, unit.y + tmpPoint.y, rot + 90f, unit.hitSize * 1.5f);
    }

    /** 切割瞬间闪光特效 (PU_V8 UnityFx.tenmeikiriCut 简化版) */
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

    /** 切割实体效果 (替代 PU_V8 UnitCutEffect extends EffectState) */
    public static final Effect cutEffectEntity = new Effect(80f, 400f, e -> {
        if (!(e.data instanceof CutData)) return;
        CutData d = (CutData) e.data;

        // 更新逻辑 (因 Effect 无 update 钩子, 在 draw 中同步更新)
        if (d.unit != null && d.unit.isValid()) {
            d.unit.hitTime = 0f;
        }
        d.offset.add(d.vel.x * Time.delta, d.vel.y * Time.delta);
        d.rotationOffset += Time.delta * d.rotationVelocity;
        float drag = d.unit != null ? Math.min(d.unit.drag, 0.07f) : 0.07f;
        d.vel.scl(1f - drag);
        d.rotationVelocity *= 1f - drag;

        // 持续烟尘 (PU_V8: Fx.fallSmoke, chanceDelta(0.4f * hitSize/45))
        if (d.unit != null && Mathf.chanceDelta(0.4f * (d.unit.hitSize / 45f))) {
            tmpPoint2.trns(d.cutRotation + d.rotationOffset, 0f, Mathf.range(d.unit.hitSize / 2f))
                .add(d.cutDirection.x + d.offset.x, d.cutDirection.y + d.offset.y)
                .add(d.startX, d.startY);
            Fx.fallSmoke.at(tmpPoint2.x, tmpPoint2.y);
        }

        // 末期爆炸 (lifetime 快结束时触发一次)
        if (!d.exploded && e.time >= d.lifetime - 1f) {
            d.exploded = true;
            if (d.unit != null) {
                float ex = d.startX + d.cutDirection.x + d.offset.x;
                float ey = d.startY + d.cutDirection.y + d.offset.y;
                Effect.shake(d.unit.hitSize / 3f, d.unit.hitSize / 3f, ex, ey);
                Fx.dynamicExplosion.at(ex, ey, (d.unit.bounds() / 2f) / 8f);
                Effect.scorch(ex, ey, (int) (d.unit.hitSize / 5));
                Fx.explosion.at(ex, ey);
                if (d.unit.type != null) d.unit.type.deathSound.at(ex, ey);
            }
        }

        // 绘制 (两半椭圆 + 切线)
        if (d.unit == null) return;
        float s = d.unit.hitSize;
        float drawX = d.startX + d.cutDirection.x + d.offset.x;
        float drawY = d.startY + d.cutDirection.y + d.offset.y;
        float drawRot = d.cutRotation + d.rotationOffset;

        Draw.blend(Blending.additive);

        // 上半 (椭圆)
        Draw.color(Color.valueOf("f53036"), Color.white, e.fout() * 0.5f);
        Fill.poly(drawX, drawY, 16, s * 0.5f * e.fout(), drawRot);

        // 下半 (反方向偏移)
        Vec2 v2 = Tmp.v1.trns(drawRot + 180f, s * 0.3f * e.fout()).add(drawX, drawY);
        Draw.color(Color.valueOf("ff786e"), Color.white, e.fout() * 0.5f);
        Fill.poly(v2.x, v2.y, 16, s * 0.4f * e.fout(), drawRot + 180f);

        // 中心红色切线 (强化切割感)
        Draw.color(Color.white, e.fout());
        Lines.stroke(2f * e.fout());
        tmpPoint2.trns(drawRot, s * 0.6f * e.fout()).add(drawX, drawY);
        Vec2 v3 = Tmp.v2.trns(drawRot + 180f, s * 0.6f * e.fout()).add(drawX, drawY);
        Lines.line(tmpPoint2.x, tmpPoint2.y, v3.x, v3.y);

        Draw.blend();
        Draw.color();
    });
}
