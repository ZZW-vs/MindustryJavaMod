package zzw.content.graphics;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.FloatSeq;

/**
 * 批量折线拖尾 / 光带 (借鉴 Vanilla-Expansion PointTrail 思路重写)。
 *
 * <p>把一串中心点连成一条可变宽的带子, 整条带子一次性提交给批处理器绘制 ——
 * 适合子弹拖尾、光带、能量轨迹等。与项目已有的
 * {@link zzw.content.units.graphics.TexturedTrail} 相比:</p>
 * <ul>
 *   <li>不继承 Trail, 只持有一个中心点列表, 更轻量, 可直接当普通字段用;</li>
 *   <li>每个点带独立宽度, 天然支持"头粗尾细";</li>
 *   <li>相邻分段共享边点 (法线取前后差分), 拐弯处不会裂缝;</li>
 *   <li>整条带子一次 {@link Draw#vert} 提交, 顶点数相同时绘制调用更少。</li>
 * </ul>
 *
 * <p>用法: 每帧 {@link #update(float, float, float)} 追加当前中心点, 再
 * {@link #draw(TextureRegion)} 画一次; 不再追加时带子保留, 需要时 {@link #clear()}。
 * 追加顺序即带子的时间顺序: <b>索引 0 为最旧点, 末尾为最新点</b>, 贴图 v 轴
 * 由旧点到新点采样 (v2 → v)。</p>
 *
 * @author GlennFolker (原作思路), 重写: zzw
 */
public class PointTrail{
    /** 中心点列表: 每 3 个 float 表示一点 (x, y, 宽度)。 */
    public final FloatSeq points = new FloatSeq();
    /** 最大点数, 超过后丢弃队头 (最旧的点)。 */
    public int length = 24;

    /** 每条边的顶点缓冲: 每段 4 顶点 × 6 float = 24; 复用以避免每帧分配。 */
    private float[] verts;
    /** 每个点的左右边点缓存: 每点 4 float (左x, 左y, 右x, 右y)。 */
    private float[] edges;

    /** 追加以 (x, y) 为中心、指定宽度的一点。 */
    public void update(float x, float y, float width){
        if(points.size >= length * 3) points.removeRange(0, 2);
        points.add(x, y, width);
    }

    /** 追加一点, 宽度沿用上一点 (尚无上一点时取 1)。 */
    public void update(float x, float y){
        float w = points.size >= 3 ? points.items[points.size - 1] : 1f;
        update(x, y, w);
    }

    /** 清空所有点。 */
    public void clear(){
        points.clear();
    }

    /**
     * 沿当前点列表绘制整条光带 (一次提交)。
     *
     * @param region 光带贴图 (通常为纵向渐变条); 其 u..u2 为横向, v..v2 为纵向
     */
    public void draw(TextureRegion region){
        int n = points.size / 3;
        if(n < 2 || region == null || region.texture == null) return;

        if(edges == null || edges.length < n * 4) edges = new float[n * 4];
        int segs = n - 1;
        if(verts == null || verts.length < segs * 24) verts = new float[segs * 24];

        float[] items = points.items;

        // 步骤 1: 为每个点求左右边点。用前后差分求切线, 保证相邻段共享边点、拐弯不出缝
        for(int i = 0; i < n; i++){
            float px = items[i * 3], py = items[i * 3 + 1], hw = items[i * 3 + 2] * 0.5f;

            float dx, dy;
            if(i == 0){
                dx = items[3] - items[0];
                dy = items[4] - items[1];
            }else if(i == n - 1){
                dx = items[(n - 1) * 3] - items[(n - 2) * 3];
                dy = items[(n - 1) * 3 + 1] - items[(n - 2) * 3 + 1];
            }else{
                dx = items[(i + 1) * 3] - items[(i - 1) * 3];
                dy = items[(i + 1) * 3 + 1] - items[(i - 1) * 3 + 1];
            }

            float len = Mathf.len(dx, dy);
            if(len < 0.0001f){ dx = 0f; dy = 0f; len = 1f; } // 重合点: 用退化方向, 避免除零

            float nx = -dy / len * hw, ny = dx / len * hw;
            edges[i * 4] = px - nx;
            edges[i * 4 + 1] = py - ny;
            edges[i * 4 + 2] = px + nx;
            edges[i * 4 + 3] = py + ny;
        }

        // 步骤 2: 逐段生成四边形 (左i, 右i, 右i+1, 左i+1), 全部写进同一缓冲
        float col = Draw.getColor().toFloatBits();
        float mcol = Draw.getMixColor().toFloatBits();
        int idx = 0;

        for(int i = 0; i < segs; i++){
            float f1 = i / (float)segs, f2 = (i + 1f) / segs;
            float v1 = Mathf.lerp(region.v2, region.v, f1);
            float v2 = Mathf.lerp(region.v2, region.v, f2);

            idx = put(idx, edges[i * 4], edges[i * 4 + 1], col, region.u, v1, mcol);
            idx = put(idx, edges[i * 4 + 2], edges[i * 4 + 3], col, region.u2, v1, mcol);
            idx = put(idx, edges[(i + 1) * 4 + 2], edges[(i + 1) * 4 + 3], col, region.u2, v2, mcol);
            idx = put(idx, edges[(i + 1) * 4], edges[(i + 1) * 4 + 1], col, region.u, v2, mcol);
        }

        Draw.vert(region.texture, verts, 0, idx);
    }

    /** 写入一个顶点 (x, y, color, u, v, mixColor), 返回新的写入位置。 */
    private int put(int idx, float x, float y, float col, float u, float v, float mcol){
        verts[idx] = x;
        verts[idx + 1] = y;
        verts[idx + 2] = col;
        verts[idx + 3] = u;
        verts[idx + 4] = v;
        verts[idx + 5] = mcol;
        return idx + 6;
    }
}
