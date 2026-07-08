package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
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
     * 参考: PU132 main/src/unity/graphics/UnityDrawf.java L216-223
     */
    public static void diamond(float x, float y, float width, float length, float backLengthScl, float rotation) {
        float w2 = width / 2f;
        float l2 = length / 2f;
        float cos = Mathf.cosDeg(rotation);
        float sin = Mathf.sinDeg(rotation);

        // 4个顶点投影: 前/后(沿rotation方向) + 左/右(垂直方向)
        float ox = cos * l2, oy = sin * l2;
        float px = -sin * w2, py = cos * w2;

        Fill.quad(
            x + ox, y + oy,
            x + px, y + py,
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
     * 绘制带尖刺的闪光圆环
     */
    public static void shiningCircle(long seed, float time, float x, float y,
                                      float scale, int spikes, float spikeLen,
                                      float width, float spikeIn) {
        if (scale <= 0f) return;

        Lines.stroke(width);
        Lines.circle(x, y, scale * 8f);

        float rotation = (seed % 360) + time * 2f;
        for (int i = 0; i < spikes; i++) {
            float angle = rotation + (360f / spikes) * i;
            float cos = Mathf.cosDeg(angle);
            float sin = Mathf.sinDeg(angle);
            float innerR = scale * 8f;
            float outerR = innerR + spikeLen * spikeIn;

            Lines.line(x + cos * innerR, y + sin * innerR,
                       x + cos * outerR, y + sin * outerR, false);

            Drawf.tri(x + cos * outerR, y + sin * outerR, width * 0.5f, spikeLen * 0.3f, angle);
        }
    }
}
