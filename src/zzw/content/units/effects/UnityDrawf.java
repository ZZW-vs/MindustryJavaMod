package zzw.content.units.effects;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.math.geom.Vec3;
import arc.struct.FloatSeq;
import arc.util.Tmp;
import mindustry.graphics.Drawf;
import zzw.util.Quat;

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

    // ===== 多段线渲染 (PU132 UnityDrawf.beginLine/linePoint/endLine 适配版) =====
    /** 多段线构建缓冲: 每个点 4 个 float (x, y, colorBits, z)。 */
    private static final FloatSeq lineBuilder = new FloatSeq(40);
    /** 是否正在构建多段线。 */
    private static boolean buildingLine;
    /** 多段线展开用的临时颜色。 */
    private static final Color lc1 = new Color(), lc2 = new Color();

    /** 开始构建多段线 (PU132 原版接口)。 */
    public static void beginLine(){
        lineBuilder.clear();
        buildingLine = true;
    }

    /**
     * 添加多段线顶点 (PU132 原版接口)。
     *
     * @param x, y 顶点位置
     * @param col 顶点颜色 (toFloatBits, 支持逐点渐变)
     * @param z 渲染深度 (用于前后穿插分层)
     */
    public static void linePoint(float x, float y, float col, float z){
        if(!buildingLine) throw new IllegalStateException("Not building.");
        lineBuilder.add(x, y, col, z);
    }

    /**
     * 结束构建并绘制多段线 (PU132 UnityDrawf.endLine 适配版)。
     *
     * <p>★ v158 适配: PU132 用点连接 (pointy join) + 逐顶点深度排序渲染;
     * 本实现把每对相邻顶点展开为一个四边形 (线段外扩 stroke/2 宽),
     * 颜色取两端平均 (相邻点色差极小, 渐变视觉一致), z 取两端平均后
     * 用 Draw.z 分层 —— z >= 0 画在前、z < 0 画在后, 保留螺旋丝带
     * "绕杆穿插"的 3D 视觉。wrap 参数保留接口兼容 (当前未使用环绕)。</p>
     */
    public static void endLine(boolean wrap){
        if(!buildingLine) throw new IllegalStateException("Not building.");
        buildingLine = false;

        float[] items = lineBuilder.items;
        int len = lineBuilder.size;
        if(len < 8) return;

        float halfWidth = 0.5f * Lines.getStroke();

        for(int i = 4; i < len - 4; i += 4){
            float x1 = items[i - 4], y1 = items[i - 3], z1 = items[i - 1];
            float x2 = items[i], y2 = items[i + 1], z2 = items[i + 3];

            float dx = x2 - x1, dy = y2 - y1;
            float d = Mathf.len(dx, dy);
            if(d < 0.001f) continue;

            // 线段法线方向外扩半宽, 构造四边形
            float nx = -dy / d * halfWidth, ny = dx / d * halfWidth;

            // arc Color.toFloatBits 为 ABGR 位模式打包, 手动反解为 0~1 分量
            int ia = Float.floatToRawIntBits(items[i - 2]);
            int ib = Float.floatToRawIntBits(items[i + 2]);
            lc1.set((ia & 0xff) / 255f, ((ia >>> 8) & 0xff) / 255f, ((ia >>> 16) & 0xff) / 255f, ((ia >>> 24) & 0xff) / 255f);
            lc2.set((ib & 0xff) / 255f, ((ib >>> 8) & 0xff) / 255f, ((ib >>> 16) & 0xff) / 255f, ((ib >>> 24) & 0xff) / 255f);
            lc1.lerp(lc2, 0.5f);

            Draw.z((z1 + z2) / 2f);
            Draw.color(lc1);
            Fill.quad(
                x1 + nx, y1 + ny,
                x2 + nx, y2 + ny,
                x2 - nx, y2 - ny,
                x1 - nx, y1 - ny
            );
        }

        // 逐段 Draw.z 后不再恢复 —— 调用方 (如 HelixLaserBulletType.draw)
        // 自行用开头快照的 z 收尾复位
        Draw.color();
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
    
    public static void dashCircleAngle(float x, float y, float radius, float rotation){
        float scaleFactor = 0.6f;
        int sides = 10 + (int)(radius * scaleFactor);
        if(sides % 2 == 1) sides++;

        Vec2 vec1 = new Vec2();

        for(int i = 0; i < sides; i++){
            if(i % 2 == 0) continue;
            vec1.set(radius, 0).setAngle((360f / sides * i + 90) + rotation);
            float x1 = vec1.x;
            float y1 = vec1.y;

            vec1.set(radius, 0).setAngle((360f / sides * (i + 1) + 90) + rotation);

            Lines.line(x1 + x, y1 + y, vec1.x + x, vec1.y + y);
        }
    }

    /**
     * 绘制 3D 透视旋转圆环 (PU132 UnityDrawf.panningCircle Quat 完整版, 11 参数)。
     *
     * <p>与上方"轴+角度"简化版的区别: 本版本接收任意组合四元数
     * (如 PU132 tendenceShoot 的 "绕 Z 旋转 90 度再绕 X 倾斜 75 度" 两级旋转),
     * 倾斜圆环等复杂姿态只有它能表达。</p>
     */
    public static void panningCircle(TextureRegion region, float x, float y, float w, float h,
                                     float radius, float arcCone, float arcRotation,
                                     Quat rotation, float layerLow, float layerHigh){
        panningCircle(region, x, y, w, h, radius, arcCone, arcRotation, rotation, false, layerLow, layerHigh, 150f);
    }

    /** {@link #panningCircle(TextureRegion, float, float, float, float, float, float, float, Quat, boolean, float, float, float)} 的默认透视距离 (150f) 版本。 */
    public static void panningCircle(TextureRegion region, float x, float y, float w, float h,
                                     float radius, float arcCone, float arcRotation,
                                     Quat rotation, boolean useLinePrecision, float layerLow, float layerHigh){
        panningCircle(region, x, y, w, h, radius, arcCone, arcRotation, rotation, useLinePrecision, layerLow, layerHigh, 150f);
    }

    /**
     * 绘制 3D 透视旋转圆环 (PU132 UnityDrawf.panningCircle 原版逻辑)。
     *
     * <p>渲染步骤 (逐步解释):</p>
     * <ol>
     *   <li>按周长/分片宽算出分片数 sides (useLinePrecision 时按 Lines 圆顶点精度);</li>
     *   <li>每个分片取环带上 4 个顶点 (内外半径 r1/r2, 相邻角度 a/a+space);</li>
     *   <li>用四元数 rotation 旋转顶点 (v' = v + 2w(q×v) + 2q×(q×v)),
     *       再按透视公式 scl = (perspectiveDst + z) / perspectiveDst 缩放
     *       (z 越大越靠近观察者, 分片越大);</li>
     *   <li>4 顶点平均 z 决定渲染层级: z>=0 画在 layerHigh (单位前),
     *       z<0 画在 layerLow (单位后), 实现"环绕穿插"效果;</li>
     *   <li>Fill.quad 填充分片 (顶点顺序 x3,x2,x1,x4 保持贴图朝向)。</li>
     * </ol>
     *
     * @param perspectiveDst 透视距离 (越大透视越弱)
     */
    public static void panningCircle(TextureRegion region, float x, float y, float w, float h,
                                     float radius, float arcCone, float arcRotation,
                                     Quat rotation, boolean useLinePrecision, float layerLow, float layerHigh, float perspectiveDst){
        float z = Draw.z();

        float arc = arcCone / 360f;
        int sides = useLinePrecision
            ? Math.max((int)(Lines.circleVertices(radius) * arc), 1)
            : Math.max((int)((Mathf.PI2 * radius * arc) / Math.max(w, 1f)), 1);
        float space = arcCone / sides;
        float hstep = (Lines.getStroke() * h / 2f) / Mathf.cosDeg(space / 2f);
        float r1 = radius - hstep, r2 = radius + hstep;

        for(int i = 0; i < sides; i++){
            float a = arcRotation - arcCone / 2f + space * i,
                cos = Mathf.cosDeg(a), sin = Mathf.sinDeg(a),
                cos2 = Mathf.cosDeg(a + space), sin2 = Mathf.sinDeg(a + space);

            Tmp.v31.set(r1 * cos, r1 * sin, 0f);
            rotation.transform(Tmp.v31);
            Tmp.v31.scl(Math.max((perspectiveDst + Tmp.v31.z) / perspectiveDst, 0f));
            float x1 = x + Tmp.v31.x, y1 = y + Tmp.v31.y;
            float sumZ = Tmp.v31.z;

            Tmp.v31.set(r1 * cos2, r1 * sin2, 0f);
            rotation.transform(Tmp.v31);
            Tmp.v31.scl(Math.max((perspectiveDst + Tmp.v31.z) / perspectiveDst, 0f));
            float x2 = x + Tmp.v31.x, y2 = y + Tmp.v31.y;
            sumZ += Tmp.v31.z;

            Tmp.v31.set(r2 * cos2, r2 * sin2, 0f);
            rotation.transform(Tmp.v31);
            Tmp.v31.scl(Math.max((perspectiveDst + Tmp.v31.z) / perspectiveDst, 0f));
            float x3 = x + Tmp.v31.x, y3 = y + Tmp.v31.y;
            sumZ += Tmp.v31.z;

            Tmp.v31.set(r2 * cos, r2 * sin, 0f);
            rotation.transform(Tmp.v31);
            Tmp.v31.scl(Math.max((perspectiveDst + Tmp.v31.z) / perspectiveDst, 0f));
            float x4 = x + Tmp.v31.x, y4 = y + Tmp.v31.y;
            sumZ = (sumZ + Tmp.v31.z) / 4f;

            Draw.z(sumZ >= 0f ? layerHigh : layerLow);
            Fill.quad(region, x3, y3, x2, y2, x1, y1, x4, y4);
        }

        Draw.z(z);
    }
}
