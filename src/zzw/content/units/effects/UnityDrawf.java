package zzw.content.units.effects;

import arc.graphics.g2d.Fill;
import arc.math.Mathf;
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
}
