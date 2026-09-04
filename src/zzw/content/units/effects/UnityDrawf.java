package zzw.content.units.effects;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.util.Tmp;
import mindustry.graphics.Drawf;

/**
 * PU132 UnityDrawf 辅助渲染工具 (简化版)
 * - diamond: 绘制菱形 (4顶点对称图形)
 * - shiningCircle: 绘制带尖刺的闪光圆环
 * 参考: PU132 main/src/unity/util/UnityDrawf.java
 */
public class UnityDrawf {

    /**
     * 绘制菱形 (4顶点对称图形, 旋转的正方形)
     */
    public static void diamond(float x, float y, float width, float length, float rotation) {
        diamond(x, y, width, length, 1f, rotation);
    }

    /**
     * 绘制非对称菱形 (PU132 UnityDrawf.diamond 6参数版)
     * backLengthScl 控制尾部(back顶点)长度缩放, <1时形成前长后短的尖刺菱形
     * ★ PU132 的 width/length 是中心到顶点的偏移量(半宽/半长), 不是全宽/全长
     *   之前误除以2导致菱形缩小一半, 现对齐原版
     * 参考: PU132 main/src/unity/graphics/UnityDrawf.java L216-223
     */
    public static void diamond(float x, float y, float width, float length, float backLengthScl, float rotation) {
        float cos = Mathf.cosDeg(rotation);
        float sin = Mathf.sinDeg(rotation);

        // 4个顶点投影: 前/后(沿rotation方向, 距离=length) + 左/右(垂直方向, 距离=width)
        float ox = cos * length, oy = sin * length;
        float px = -sin * width, py = cos * width;

        Fill.quad(
            x + px, y + py,
            x + ox, y + oy,
            x - ox * backLengthScl, y - oy * backLengthScl,
            x - px, y - py
        );
    }

    /**
     * 绘制三角形 (PU132 UnityDrawf.tri, 代理到 Drawf.tri)
     * 参数: x, y, width, length, rotation
     */
    public static void tri(float x, float y, float width, float length, float rotation) {
        Drawf.tri(x, y, width, length, rotation);
    }

    /**
     * 绘制带尖刺的闪光圆环 (PU132 UnityDrawf.shiningCircle 9参数版)
     * 参数: seed, time, x, y, radius, spikes, spikeDuration, spikeWidth, spikeHeight
     * 参考: PU132 main/src/unity/graphics/UnityDrawf.java L234-236
     */
    public static void shiningCircle(int seed, float time, float x, float y,
                                      float radius, int spikes, float spikeDuration,
                                      float spikeWidth, float spikeHeight) {
        shiningCircle(seed, time, x, y, radius, spikes, spikeDuration, spikeWidth, spikeHeight, 0f);
    }

    /**
     * 绘制带尖刺的闪光圆环 (PU132 UnityDrawf.shiningCircle 10参数版, 含 angleDrift)
     * - 中心实心圆 + 周围动画尖刺 (三角形)
     * - 每个尖刺按 spikeDuration 周期闪烁, fslope 控制高度 (0→1→0)
     * - angleDrift > 0 时尖刺角度随时间漂移
     * 参考: PU132 main/src/unity/graphics/UnityDrawf.java L238-266
     */
    public static void shiningCircle(int seed, float time, float x, float y,
                                      float radius, int spikes, float spikeDuration,
                                      float spikeWidth, float spikeHeight, float angleDrift) {
        shiningCircle(seed, time, x, y, radius, spikes, spikeDuration, 0f, spikeWidth, spikeHeight, angleDrift);
    }

    /**
     * 绘制带尖刺的闪光圆环 (PU132 UnityDrawf.shiningCircle 11参数版, 完整实现)
     * - durationRange: 尖刺持续时间随机范围 (0=无随机)
     * 参考: PU132 main/src/unity/graphics/UnityDrawf.java L242-266
     */
    public static void shiningCircle(int seed, float time, float x, float y,
                                      float radius, int spikes, float spikeDuration,
                                      float durationRange, float spikeWidth, float spikeHeight,
                                      float angleDrift) {
        if (radius <= 0f) return;
        Fill.circle(x, y, radius);
        spikeWidth = Math.min(spikeWidth, 90f);

        for (int i = 0; i < spikes; i++) {
            float d = spikeDuration * (durationRange > 0f ? Mathf.randomSeed((seed + i) * 41L, 1f - durationRange, 1f + durationRange) : 1f);
            float timeOffset = Mathf.randomSeed((seed + i) * 314L, 0f, d);
            int timeSeed = Mathf.floor((time + timeOffset) / d);
            float fin = ((time + timeOffset) % d) / d;
            float fslope = (0.5f - Math.abs(fin - 0.5f)) * 2f;
            float angle = Mathf.randomSeed(Math.max(timeSeed, 1) + ((i + seed) * 245L), 360f);
            if (fslope > 0.0001f) {
                float drift = angleDrift > 0 ? Mathf.randomSeed(Math.max(timeSeed, 1) + ((i + seed) * 162L), -angleDrift, angleDrift) * fin : 0f;
                for (int j = 0; j < 3; j++) {
                    float angB = (j * spikeWidth - (2f) * spikeWidth / 2f) + angle;
                    Tmp.v1.trns(angB + drift, radius + (j == 1 ? (spikeHeight * fslope) : 0f)).add(x, y);
                    if (j == 0) {
                        Tmp.v3.set(Tmp.v1);
                    } else if (j == 1) {
                        Tmp.v2.set(Tmp.v1);
                    } else {
                        Fill.tri(Tmp.v3.x, Tmp.v3.y,
                                 Tmp.v2.x, Tmp.v2.y,
                                 Tmp.v1.x, Tmp.v1.y);
                    }
                }
            }
        }
    }

    /**
     * 绘制带厚度的圆弧线段 (PU132 UnityDrawf.arcLine)
     * <p>
     * 与 Lines.arc 不同, 该方法绘制的是"环形扇区"(内外半径差 = 线宽),
     * 视觉上是一条有宽度的弧线。EnergyRingWeapon 能量环的核心绘制原语。
     * <p>
     * 参数:
     * - x, y: 圆心
     * - radius: 半径
     * - arcAngle: 弧线跨度的角度 (度)
     * - angle: 弧线中心角 (度)
     */
    public static void arcLine(float x, float y, float radius, float arcAngle, float angle) {
        float arc = arcAngle / 360f;
        int sides = Math.max((int) (Lines.circleVertices(radius) * arc), 1);
        float space = arcAngle / sides;
        // 半弦长修正: 保证相邻扇区拼接处无缝隙
        float hstep = Lines.getStroke() / 2f / Mathf.cosDeg(space / 2f);
        float r1 = radius - hstep, r2 = radius + hstep;

        for (int i = 0; i < sides; i++) {
            float a = angle - arcAngle / 2f + space * i,
                cos = Mathf.cosDeg(a), sin = Mathf.sinDeg(a),
                cos2 = Mathf.cosDeg(a + space), sin2 = Mathf.sinDeg(a + space);
            Fill.quad(
                x + r1 * cos, y + r1 * sin,
                x + r1 * cos2, y + r1 * sin2,
                x + r2 * cos2, y + r2 * sin2,
                x + r2 * cos, y + r2 * sin);
        }
    }

    /**
     * 计算带符号的角度差 (PU132 Utils.angleDistSigned)
     * <p>
     * 返回 a 到 b 的最短旋转角度 (-180 ~ 180), 正值表示需要顺时针转。
     */
    public static float angleDistSigned(float a, float b) {
        a = Mathf.mod(a, 360f);
        b = Mathf.mod(b, 360f);
        float diff = b - a;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        return diff;
    }

    /**
     * 绘制 3D 透视旋转圆环 (PU132 UnityDrawf.panningCircle 简化移植)
     * <p>
     * 将一张贴图沿圆周排列成一个"环带", 每个分片先绕 rotationAxis 旋转 rotationAngle,
     * 再做透视缩放 (z 越靠近观察者越大), 营造 3D 旋转环效果。
     * 用于 JoiningBulletType 的能量球外壳 / monolith-soul 的链环。
     * <p>
     * ★ v158 适配: PU 用 Quat + Mat3D, 这里用 arc Vec3.rotate(axis, angle) 等效实现。
     * <p>
     * 参数:
     * - region: 贴图 (通常为白色方块或 line-shade)
     * - x, y: 圆心
     * - w, h: 每个分片的宽高
     * - radius: 环半径
     * - arcCone: 环的角度跨度 (度, 360 = 完整环)
     * - arcRotation: 环的起始角 (度)
     * - rotationAxis: 3D 旋转轴 (Vec3.X/Y/Z)
     * - rotationAngle: 绕轴旋转角 (度)
     * - layerLow, layerHigh: 分片在 z<0 / z>=0 时的渲染层级
     */
    public static void panningCircle(TextureRegion region, float x, float y, float w, float h,
                                      float radius, float arcCone, float arcRotation,
                                      Vec3 rotationAxis, float rotationAngle,
                                      float layerLow, float layerHigh) {
        float z = Draw.z();
        float perspectiveDst = 150f;

        float arc = arcCone / 360f;
        int sides = Math.max((int) ((Mathf.PI2 * radius * arc) / Math.max(w, 1f)), 1);
        float space = arcCone / sides;
        float hstep = (Lines.getStroke() * h / 2f) / Mathf.cosDeg(space / 2f);
        float r1 = radius - hstep, r2 = radius + hstep;

        for (int i = 0; i < sides; i++) {
            float a = arcRotation - arcCone / 2f + space * i,
                cos = Mathf.cosDeg(a), sin = Mathf.sinDeg(a),
                cos2 = Mathf.cosDeg(a + space), sin2 = Mathf.sinDeg(a + space);

            // 依次计算 4 个顶点: 绕轴旋转 + 透视缩放
            Tmp.v31.set(r1 * cos, r1 * sin, 0f).rotate(rotationAxis, rotationAngle)
                .scl(Math.max((perspectiveDst + Tmp.v31.z) / perspectiveDst, 0f));
            float x1 = x + Tmp.v31.x, y1 = y + Tmp.v31.y;
            float sumZ = Tmp.v31.z;

            Tmp.v31.set(r1 * cos2, r1 * sin2, 0f).rotate(rotationAxis, rotationAngle)
                .scl(Math.max((perspectiveDst + Tmp.v31.z) / perspectiveDst, 0f));
            float x2 = x + Tmp.v31.x, y2 = y + Tmp.v31.y;
            sumZ += Tmp.v31.z;

            Tmp.v31.set(r2 * cos2, r2 * sin2, 0f).rotate(rotationAxis, rotationAngle)
                .scl(Math.max((perspectiveDst + Tmp.v31.z) / perspectiveDst, 0f));
            float x3 = x + Tmp.v31.x, y3 = y + Tmp.v31.y;
            sumZ += Tmp.v31.z;

            Tmp.v31.set(r2 * cos, r2 * sin, 0f).rotate(rotationAxis, rotationAngle)
                .scl(Math.max((perspectiveDst + Tmp.v31.z) / perspectiveDst, 0f));
            float x4 = x + Tmp.v31.x, y4 = y + Tmp.v31.y;
            sumZ = (sumZ + Tmp.v31.z) / 4f;

            Draw.z(sumZ >= 0f ? layerHigh : layerLow);
            Fill.quad(region, x3, y3, x2, y2, x1, y1, x4, y4);
        }

        Draw.z(z);
    }
}
