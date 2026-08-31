package zzw.content.units.graphics;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import zzw.content.graphics.UnityPal;
import zzw.content.units.graphics.MultiTrail.RotationHandler;
import zzw.content.units.graphics.MultiTrail.TrailHold;

/**
 * PU132 拖尾工厂 (unity.content.Trails 移植)
 *
 * <p>提供 Monolith 系列单位用的两种拖尾:</p>
 * <ul>
 *   <li>{@link #phantasmal} 风格 — 幽蓝"丝带"拖尾, 2 条缠绕丝带 + 主带 + 排气带。</li>
 *   <li>{@link #soul} 风格 — 灵魂拖尾, 3 条正弦扩散丝带 + 主带, 随速度加快旋转。</li>
 * </ul>
 *
 * <p>原版依赖 unity.util.Utils.with (lambda 配置), 此处内联为私有 with 方法,
 * 功能等价: 对对象应用一段配置代码后返回原对象。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public final class Trails{

    /**
     * 单条幻影拖尾 (unity-phantasmal-trail 贴图)。
     *
     * @param length 拖尾长度 (点数)
     */
    public static TexturedTrail singlePhantasmal(int length){
        // ★ 贴图名适配: PU132 "unity-phantasmal-trail" → 本项目实际贴图 "phantasmal-trail"
        return new TexturedTrail(Core.atlas.find("phantasmal-trail"), length){{
            blend = Blending.additive;
            fadeInterp = Interp.pow2In;
            sideFadeInterp = Interp.pow3In;
            mixInterp = Interp.pow10In;
            gradientInterp = Interp.pow10Out;
            fadeColor = new Color(0.3f, 0.5f, 1f);
            shrink = 0f;
            fadeAlpha = 1f;
            mixAlpha = 1f;
            trailChance = 0.4f;
            trailWidth = 1.6f;
            trailColor = UnityPal.monolithLight;
        }};
    }

    /**
     * 幻影排气拖尾 (无粒子、带收缩的短拖尾)。
     */
    public static TexturedTrail phantasmalExhaust(int length){
        return with(singlePhantasmal(length), t -> {
            t.capRegion = Core.atlas.find("clear");
            t.minDst = 0.4f;
            // v158 arc 无 Interp.pow25In, 用 pow(e, 2.5) 等效实现
            t.fadeInterp = e -> (1f - (float)Math.pow(e, 2.5)) * Interp.pow3In.apply(e);
            t.sideFadeInterp = Interp.pow5In;
            t.mixAlpha = 0f;
            t.trailChance = 0f;
            t.shrink = -3.6f;
        });
    }

    public static MultiTrail phantasmal(int length){
        return phantasmal(length, 3.6f, 3.5f, -1f, 0f);
    }

    public static MultiTrail phantasmal(RotationHandler rot, int length){
        return phantasmal(rot, length, 3.6f, 3.5f, -1f, 0f);
    }

    public static MultiTrail phantasmal(int length, float scale, float magnitude, float speedThreshold, float offsetY){
        return phantasmal(MultiTrail::calcRot, length, scale, magnitude, speedThreshold, offsetY);
    }

    /**
     * 幻影多重拖尾: 2 条正弦缠绕丝带 + 1 条主带 + 1 条排气带。
     *
     * @param rot 旋转处理器 (决定丝带朝向)
     * @param length 拖尾长度
     * @param scale 正弦周期缩放
     * @param magnitude 丝带摆动幅度 (乘宽度)
     * @param speedThreshold 速度阈值 (-1 = 恒速全幅)
     * @param offsetY 丝带纵向偏移
     */
    public static MultiTrail phantasmal(RotationHandler rot, int length, float scale, float magnitude, float speedThreshold, float offsetY){
        int strandsAmount = 2;

        TrailHold[] trails = new TrailHold[strandsAmount + 2];
        // 步骤 1: 两条缠绕丝带, 宽度 4.8f, 各占 0.16f 宽度比例
        for(int i = 0; i < strandsAmount; i++) trails[i] = new TrailHold(with(singlePhantasmal(Mathf.round(length * 1.5f)), t -> t.trailWidth = 4.8f), 0f, 0f, 0.16f);
        // 步骤 2: 主带 (1.0f 宽度比例)
        trails[strandsAmount] = new TrailHold(singlePhantasmal(length));
        // 步骤 3: 排气带 (位于后方 1.6f 处)
        trails[strandsAmount + 1] = new TrailHold(phantasmalExhaust(Mathf.round(length * 0.5f)), 0f, 1.6f);

        float offset = Mathf.random(Mathf.PI2 * scale);
        return new MultiTrail(rot, trails){
            @Override
            public void update(float x, float y, float width){
                // 步骤 4: 速度越快丝带摆幅越大 (speedThreshold=-1 时恒为 1)
                float scl = speedThreshold == -1f ? 1f : Mathf.clamp(Mathf.dst(x, y, lastX, lastY) / Time.delta / speedThreshold);
                float angle = rotation.get(this, x, y) - 90f;

                // 步骤 5: 两条丝带按正弦相位差缠绕
                for(int i = 0; i < strandsAmount; i++){
                    Tmp.v1.trns(angle, Mathf.sin(Time.time + offset + (Mathf.PI2 * scale) * ((float)i / strandsAmount), scale, magnitude * width * scl), offsetY);

                    TrailHold trail = trails[i];
                    trail.trail.update(x + Tmp.v1.x, y + Tmp.v1.y, width * trail.width);
                }

                // 步骤 6: 主带和排气带按固定偏移跟随
                for(int i = strandsAmount; i < trails.length; i++){
                    TrailHold trail = trails[i];
                    Tmp.v1.trns(angle, trail.x, trail.y);

                    trail.trail.update(x + Tmp.v1.x, y + Tmp.v1.y, width * trail.width);
                }

                lastX = x;
                lastY = y;
            }
        };
    }

    /**
     * 单条灵魂拖尾 (unity-soul-trail 贴图)。
     */
    public static TexturedTrail singleSoul(int length){
        // ★ 贴图名适配: PU132 "unity-soul-trail" → 本项目实际贴图 "soul-trail"
        return new TexturedTrail(Core.atlas.find("soul-trail"), length){{
            blend = Blending.additive;
            fadeInterp = Interp.pow5In;
            sideFadeInterp = Interp.pow10In;
            mixInterp = Interp.pow5In;
            gradientInterp = Interp.pow5Out;
            fadeColor = new Color(0.1f, 0.2f, 1f);
            shrink = 1f;
            mixAlpha = 0.8f;
            fadeAlpha = 0.5f;
            trailChance = 0f;
        }};
    }

    public static MultiTrail soul(int length){
        return soul(length, 6f, 2.2f, -1f);
    }

    public static MultiTrail soul(RotationHandler rot, int length){
        return soul(rot, length, 6f, 2.2f, -1f);
    }

    public static MultiTrail soul(int length, float speedThreshold){
        return soul(length, 6f, 2.2f, speedThreshold);
    }

    public static MultiTrail soul(RotationHandler rot, int length, float speedThreshold){
        return soul(rot, length, 6f, 2.2f, speedThreshold);
    }

    public static MultiTrail soul(int length, float scale, float magnitude, float speedThreshold){
        return soul(MultiTrail::calcRot, length, scale, magnitude, speedThreshold);
    }

    /**
     * 灵魂多重拖尾: 3 条正弦扩散丝带 (宽度随正弦波动) + 1 条幻影主带。
     *
     * <p>与 phantasmal 的区别: 丝带以自身时间轴旋转 (随移动速度加速),
     * 且宽度会随正弦值在 0.2~1 之间脉动, 产生"能量波纹"感。</p>
     */
    public static MultiTrail soul(RotationHandler rot, int length, float scale, float magnitude, float speedThreshold){
        int strandsAmount = 3;

        TrailHold[] trails = new TrailHold[strandsAmount + 1];
        // 步骤 1: 三条灵魂丝带, 关闭混色
        for(int i = 0; i < strandsAmount; i++) trails[i] = new TrailHold(with(singleSoul(Mathf.round(length * 1.5f)), t -> t.mixAlpha = 0f), 0f, 0f, 0.56f);
        // 步骤 2: 幻影主带 (颜色覆盖为 monolith 蓝)
        trails[strandsAmount] = new TrailHold(singlePhantasmal(length), UnityPal.monolith);

        float dir = Mathf.sign(Mathf.chance(0.5f));
        return new MultiTrail(rot, trails){
            float time = Time.time + Mathf.random(Mathf.PI2 * scale);

            @Override
            public void update(float x, float y, float width){
                float angle = rotation.get(this, x, y) - 90f;

                // 步骤 3: 时间轴随速度加速 (speedThreshold=-1 时恒速)
                time += (speedThreshold == -1f ? 1f : Mathf.clamp(Mathf.dst(x, y, lastX, lastY) / Time.delta / speedThreshold)) * Time.delta;
                // 步骤 4: 三条丝带相位差 2π/3, 宽度按正弦在 0.2~1 脉动
                for(int i = 0; i < strandsAmount; i++){
                    float rad = (time + (Mathf.PI2 * scale) * ((float)i / strandsAmount)) * dir;
                    float scl = Mathf.map(Mathf.sin(rad, scale, 1f), -1f, 1f, 0.2f, 1f);
                    Tmp.v1.trns(angle, Mathf.cos(rad, scale, magnitude * width));

                    TrailHold trail = trails[i];
                    trail.trail.update(x + Tmp.v1.x, y + Tmp.v1.y, width * trail.width * scl);
                }

                // 步骤 5: 主带跟随
                TrailHold main = trails[trails.length - 1];
                Tmp.v1.trns(angle, main.x, main.y);

                main.trail.update(x + Tmp.v1.x, y + Tmp.v1.y, width * main.width);
                lastX = x;
                lastY = y;
            }
        };
    }

    /** 对对象应用配置后返回原对象 (等价 PU Utils.with)。 */
    private static <T> T with(T obj, Cons<T> cons){
        cons.get(obj);
        return obj;
    }

    private Trails(){
        throw new AssertionError();
    }
}
