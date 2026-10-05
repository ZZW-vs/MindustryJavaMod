package zzw.content.graphics;

import arc.Core;
import arc.func.Cons;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;

/**
 * 分段弯曲贴图 (借鉴 Vanilla-Expansion 的 SegmentedRegion 思路改写)。
 *
 * <p>普通 {@code Draw.rect} 只能把一张矩形贴图平移/旋转/缩放, 无法沿曲线弯曲。
 * 本类把贴图沿"长边"切成 {@code subDiv} 段, 每段生成一个四边形, 调用方通过
 * {@code mover} 回调逐段移动四个角点, 就能把贴图掰成任意弧线 / 波浪形 ——
 * 常用于弯曲光束、能量弧、护盾弧面、闪电等。</p>
 *
 * <p>与 VE 原版的差异 (均为本项目适配):</p>
 * <ul>
 *   <li>顶点格式对齐本项目 / 游戏内 {@link Draw#vert} 的 6 float 布局
 *       (x, y, color, u, v, mixColor), 可直接喂给批处理器;</li>
 *   <li>顶点缓冲改为实例字段并按需扩容, 不再每帧分配;</li>
 *   <li>整张贴图的所有分段<b>一次性提交</b> (原版每段提交一次),
 *       绘制调用数从 {@code subDiv} 降到 1。</li>
 * </ul>
 *
 * <p>用法示例:</p>
 * <pre>{@code
 * SegmentedRegion beam = new SegmentedRegion(Core.atlas.find("my-beam"));
 * ...
 * beam.render(x, y, 6f, 64f, p -> p.x += Mathf.sin(p.y * 0.08f + Time.time) * 3f);
 * }</pre>
 *
 * @author GlennFolker (原作思路), 改写: zzw
 */
public class SegmentedRegion{
    /** 贴图 (通常是一条细长的渐变带)。 */
    public TextureRegion region;
    /** 沿长边 (这里对应 height) 的分段数, 越大越平滑、顶点越多。 */
    public int subDiv = 16;

    /** 顶点缓冲: 每段 4 顶点 × 6 float = 24; 复用以避免每帧分配。 */
    private float[] verts;

    public SegmentedRegion(String name){
        this(Core.atlas.find(name));
    }

    public SegmentedRegion(TextureRegion region){
        this.region = region;
    }

    /**
     * 绘制一条弯曲的贴图带。
     *
     * <p>几何约定: {@code (x, y)} 为带子中心, {@code width} 为短边 (横向) 宽度,
     * {@code height} 为长边 (纵向) 长度; 贴图的 u 轴映射到宽边、v 轴映射到长边。</p>
     *
     * @param x      中心 x
     * @param y      中心 y
     * @param width  短边宽度
     * @param height 长边长度
     * @param mover  角点变形回调: 传入该角点的初始坐标, 就地修改即可
     */
    public void render(float x, float y, float width, float height, Cons<Vec2> mover){
        if(region == null || region.texture == null) return;

        int need = subDiv * 24;
        if(verts == null || verts.length < need) verts = new float[need];

        TextureRegion r = region;
        float offx = width / 2f, offy = height / 2f;
        float col = Draw.getColor().toFloatBits();
        float mcol = Draw.getMixColor().toFloatBits();
        Vec2 t = Tmp.v1;
        int idx = 0;

        for(int i = 0; i < subDiv; i++){
            float f1 = i / (float)subDiv, f2 = (i + 1f) / subDiv;

            // 该段的矩形范围 (x1/x2 为左右边, y1/y2 为前后边)
            float x1 = x - offx, x2 = x + offx;
            float y1 = (height * f2 + y) - offy, y2 = (height * f1 + y) - offy;

            float u = r.u, u2 = r.u2;
            float v = Mathf.lerp(r.v2, r.v, f1);
            float v2 = Mathf.lerp(r.v2, r.v, f2);

            // 四个角点依次交给 mover 变形后写入缓冲 (顺序构成一个完整四边形)
            idx = put(idx, t.set(x1, y2), mover, col, u, v, mcol);
            idx = put(idx, t.set(x1, y1), mover, col, u, v2, mcol);
            idx = put(idx, t.set(x2, y1), mover, col, u2, v2, mcol);
            idx = put(idx, t.set(x2, y2), mover, col, u2, v, mcol);
        }

        // 一次性提交 (顶点数恰为 4 的倍数, 批处理器按四边形解释)
        Draw.vert(r.texture, verts, 0, idx);
    }

    /** 把一个角点交给 mover 变形后写入缓冲, 返回新的写入位置。 */
    private int put(int idx, Vec2 p, Cons<Vec2> mover, float col, float u, float v, float mcol){
        mover.get(p);
        verts[idx] = p.x;
        verts[idx + 1] = p.y;
        verts[idx + 2] = col;
        verts[idx + 3] = u;
        verts[idx + 4] = v;
        verts[idx + 5] = mcol;
        return idx + 6;
    }
}
