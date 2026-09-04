package zzw.content.units.graphics;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.FloatSeq;
import arc.util.Time;
import mindustry.Vars;

/**
 * 定长拖尾 (PU132 unity.graphics.FixedTrail 移植)。
 *
 * <p>与原版 {@link mindustry.graphics.Trail} 的区别: 本类每个点记录
 * <b>(x, y, 宽度, 弧度朝向)</b> 四个 float, 拖尾段按各点自身朝向展开成
 * 四边形条带, 而不是简单直线插值 —— 适合绘制 "飘带状" 拖尾
 * (Monolith 灵魂转移 / 披风轨迹等)。</p>
 *
 * <p>渲染算法 (见 {@link #draw}):</p>
 * <ol>
 *   <li>相邻两点 (x1,y1)-(x2,y2) 组成一段;</li>
 *   <li>每点按其记录的弧度角 a 算出垂直于朝向的展开向量
 *       (sin·w, cos·w), 其中 w = 序号 × 单段宽度 × 点宽系数;</li>
 *   <li>两个展开向量构成一个四边形条带段, 逐段 {@code Fill.quad} 填充。</li>
 * </ol>
 *
 * <p>点的增删按 ~1 tick 节奏进行 (counter 累计 Time.delta 到 0.99),
 * 保证拖尾长度与游戏帧率无关。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class FixedTrail{
    /** 拖尾最大点数 (每点 4 个 float: x, y, 宽度, 弧度朝向)。 */
    public int length;

    /** 点序列, 按 (x, y, width, angleRad) 交错存储。 */
    private final FloatSeq points;
    private float lastX = -1, lastY = -1, counter = 0f;

    public FixedTrail(int length){
        this.length = length;
        points = new FloatSeq(length * 4);
    }

    /** 深拷贝 (转移给渐隐特效时使用)。 */
    public FixedTrail copy(){
        FixedTrail out = new FixedTrail(length);
        out.points.addAll(points);
        out.lastX = lastX;
        out.lastY = lastY;
        return out;
    }

    public void clear(){
        points.clear();
    }

    public int size(){
        return points.size / 4;
    }

    /**
     * 绘制头部封口: 取最后一个点, 用 "hcircle" 半圆贴图沿该点朝向
     * 盖住拖尾末端, 宽度按点在序列中的位置比例缩放。
     */
    public void drawCap(Color color, float width){
        if(points.size > 0){
            Draw.color(color);
            float[] items = points.items;
            int i = points.size - 4;
            float x1 = items[i], y1 = items[i + 1], w1 = items[i + 2], ai = items[i + 3], w = w1 * width / (points.size / 4) * i / 4f * 2f;
            if(w1 <= 0.001f) return;
            // ★ hcircle 为 mod 贴图, atlas 中带 "create-" 前缀
            Draw.rect("create-hcircle", x1, y1, w, w, -Mathf.radDeg * ai + 180f);
            Draw.reset();
        }
    }

    /**
     * 绘制拖尾主体: 逐段展开四边形条带。
     *
     * <p>每段步骤: 取相邻两点 (x1..a1) 与 (x2..a2), 按各自弧度角与
     * "序号 × 单段宽度 × 点宽" 算出垂直展开向量 (cx,cy)/(nx,ny),
     * 四个顶点 (x1-cx, x1+cx, x2+nx, x2-nx) 组成 Fill.quad。</p>
     */
    public void draw(Color color, float width){
        Draw.color(color);
        float[] items = points.items;

        for(int i = 0; i < points.size - 4; i+= 4){
            float x1 = items[i], y1 = items[i + 1], w1 = items[i + 2], a1 = items[i + 3],
                x2 = items[i + 4], y2 = items[i + 5], w2 = items[i + 6], a2 = items[i + 7];
            float size = width / (points.size / 4);
            if(w1 <= 0.001f || w2 <= 0.001f) continue;

            float cx = Mathf.sin(a1) * i / 4f * size * w1, cy = Mathf.cos(a1) * i / 4f * size * w1,
                nx = Mathf.sin(a2) * (i / 4f + 1) * size * w2, ny = Mathf.cos(a2) * (i / 4f + 1) * size * w2;
            Fill.quad(x1 - cx, y1 - cy, x1 + cx, y1 + cy, x2 + nx, y2 + ny, x2 - nx, y2 - ny);
        }

        Draw.reset();
    }

    /**
     * 按固定间隔从队头移除一个点, 让拖尾逐渐缩短 (渐隐阶段使用)。
     */
    public void shorten(){
        if(Vars.state.isPlaying() && (counter += Time.delta) >= 0.99f){
            if(points.size >= 4){
                points.removeRange(0, 3);
            }

            counter = 0f;
        }
    }

    /**
     * 追加新点, 朝向自动取上一点到当前点的连线角。
     */
    public void update(float x, float y){
        update(x, y, Angles.angle(x, y, lastX, lastY));
    }

    /**
     * 追加新点并指定朝向 (宽度 1f)。
     */
    public void update(float x, float y, float rotation){
        update(x, y, 1f, rotation);
    }

    /**
     * 追加新点: 每 ~1 tick 记录一次, 超过 length 后丢弃队头,
     * 点内记录 (x, y, 宽度, -rotation 弧度)。
     */
    public void update(float x, float y, float width, float rotation){
        if(Vars.state.isPlaying() && (counter += Time.delta) >= 0.99f){
            if(points.size > length * 4){
                points.removeRange(0, 3);
            }

            points.add(x, y, width, -rotation * Mathf.degRad);

            counter = 0f;

            lastX = x;
            lastY = y;
        }
    }
}
