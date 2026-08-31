package zzw.content.units.types;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/**
 * 对象化单位引擎 (PU132 unity.type.Engine 移植)
 *
 * <p>原版 UnitType 只支持一个居中引擎 (engineOffset/engineSize 标量字段);
 * 本类把引擎封装成对象, 支持:</p>
 * <ul>
 *   <li>独立颜色 (color) 与内芯颜色 (innerColor)。</li>
 *   <li>多个引擎组合 ({@link MultiEngine}: 左右各一个, 依次绘制)。</li>
 * </ul>
 *
 * <p>绘制逻辑 (新手向): 引擎画在单位"身后" (rotation+180°),
 * 大小随 elevation (抬升度) 缩放, 并带 sin 脉动闪烁。</p>
 *
 * <p>★ v158 适配: PU 的 Trailc/CTrailc 接口不存在于本移植,
 * 拖尾由 UnitType.trailType 统一维护, 此处不再重复绘制拖尾。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class Engine{
    /** 引擎离单位中心的偏移 (沿朝向反方向)。 */
    public float offset = 5f;
    /** 引擎半径。 */
    public float size = 2.5f;
    /** 拖尾缩放。 */
    public float trailScale = 1f;
    /** 引擎外圈颜色 (null = 队伍色)。 */
    public Color color = null, innerColor = Color.white;

    /** 是否绘制拖尾 (MultiEngine 会关闭子引擎的拖尾避免重复)。 */
    public boolean drawTrail = true;

    /**
     * 把本引擎参数写回 UnitType 的标量字段 (用于兼容原版渲染路径)。
     *
     * @param type 目标单位类型
     */
    public Engine apply(UnitType type){
        type.engineOffset = offset;
        type.engineSize = size;
        type.trailScl = trailScale;
        type.engineColor = color;
        type.engineColorInner = Color.white;
        return this;
    }

    /** 在单位位置绘制引擎。 */
    public void draw(Unit unit){
        draw(unit, unit.x, unit.y);
    }

    /**
     * 在指定位置绘制引擎 (供 MultiEngine 偏移调用)。
     *
     * <p>步骤:</p>
     * <ol>
     *   <li>抬升度 scale 决定引擎可见度 (地面时接近 0)。</li>
     *   <li>外圈: 队伍色/自定义色圆, 带 sin 脉动。</li>
     *   <li>内芯: 白色小圆, 偏移少 1f, 半径减半。</li>
     * </ol>
     */
    public void draw(Unit unit, float x, float y){
        float scale = unit.elevation;
        float offset = this.offset / 2f + this.offset / 2f * scale;

        // 注: 拖尾由 UnitType 统一绘制, 此处不重复 (见类 Javadoc 的 v158 适配说明)

        Draw.color(color == null ? unit.team.color : color);
        Fill.circle(
            x + Angles.trnsx(unit.rotation + 180f, offset),
            y + Angles.trnsy(unit.rotation + 180f, offset),
            (size + Mathf.absin(Time.time, 2f, size / 4f)) * scale
        );
        Draw.color(innerColor);
        Fill.circle(
            x + Angles.trnsx(unit.rotation + 180f, offset - 1f),
            y + Angles.trnsy(unit.rotation + 180f, offset - 1f),
            (size + Mathf.absin(Time.time, 2f, size / 4f)) / 2f * scale
        );
        Draw.color();
    }

    /**
     * 多引擎组合 (PU132 Engine.MultiEngine 移植)。
     *
     * <p>持有若干 {@link EngineHold} (引擎 + 侧向偏移), 绘制时
     * 逐个把子引擎画到 unit.rotation-90° 方向偏移 offsetX 处。
     * 子引擎的 drawTrail 被构造函数关闭, 避免拖尾重复。</p>
     */
    public static class MultiEngine extends Engine{
        /** 子引擎及其侧向偏移列表。 */
        public EngineHold[] engines;

        /**
         * @param engines 任意数量的 (引擎, 侧偏移) 组合
         */
        public MultiEngine(EngineHold... engines){
            this.engines = engines;
            for(EngineHold engine : this.engines){
                engine.engine.drawTrail = false; // 避免拖尾重复绘制
            }
        }

        @Override
        public void draw(Unit unit, float x, float y){
            // 步骤 1: 依次在侧偏移处绘制子引擎
            for(EngineHold engine : engines){
                float
                    ox = Angles.trnsx(unit.rotation - 90f, engine.offsetX),
                    oy = Angles.trnsy(unit.rotation - 90f, engine.offsetX);

                engine.engine.draw(unit, x + ox, y + oy);
            }
        }

        /**
         * 引擎持有者 (PU132 EngineHold 移植)。
         */
        public static class EngineHold{
            /** 子引擎。 */
            public final Engine engine;
            /** 相对单位中心的侧向偏移 (rotation-90° 方向)。 */
            public final float offsetX;

            public EngineHold(Engine engine, float offsetX){
                this.engine = engine;
                this.offsetX = offsetX;
            }
        }
    }
}
