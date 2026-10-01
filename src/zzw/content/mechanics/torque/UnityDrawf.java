package zzw.content.mechanics.torque;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;

/**
 * PU_V8 UnityDrawf 简化版 (仅移植扭矩系统所需的方法)
 *
 * 移植方法:
 * - drawRotRect: 绘制可旋转的矩形 (用于驱动轴/齿轮等旋转视觉)
 * - arc: 绘制带厚度的圆弧 (传动带皮带绕过皮带轮用)
 *
 * 参考: PU_V8 main/src/unity/graphics/UnityDrawf.java
 */
public class UnityDrawf{
    private static final TextureRegion nRegion = new TextureRegion();

    public static void drawRotRect(TextureRegion region, float x, float y, float w, float h, float th, float rot, float ang1, float ang2){
        if(region == null || !Core.settings.getBool("effects")) return;
        float amod1 = Mathf.mod(ang1, 360f);
        float amod2 = Mathf.mod(ang2, 360f);
        if(amod1 >= 180f && amod2 >= 180f) return;

        nRegion.set(region);
        float uy1 = nRegion.v;
        float uy2 = nRegion.v2;
        float uCenter = (uy1 + uy2) / 2f;
        float uSize = (uy2 - uy1) * h / th * 0.5f;
        uy1 = uCenter - uSize;
        uy2 = uCenter + uSize;
        nRegion.v = uy1;
        nRegion.v2 = uy2;

        float s1 = -Mathf.cos(ang1 * Mathf.degreesToRadians);
        float s2 = -Mathf.cos(ang2 * Mathf.degreesToRadians);
        if(amod1 > 180f){
            nRegion.v2 = Mathf.map(0f, amod1 - 360f, amod2, uy2, uy1);
            s1 = -1f;
        }else if(amod2 > 180f){
            nRegion.v = Mathf.map(180f, amod1, amod2, uy2, uy1);
            s2 = 1f;
        }
        s1 = Mathf.map(s1, -1f, 1f, y - h / 2f, y + h / 2f);
        s2 = Mathf.map(s2, -1f, 1f, y - h / 2f, y + h / 2f);
        Draw.rect(nRegion, x, (s1 + s2) * 0.5f, w, s2 - s1, w * 0.5f, y - s1, rot);
    }

    /**
     * 绘制带厚度的圆弧 (PU_V8 unity.graphics.UnityDrawf.arc 移植)。
     * <p>以当前 {@link Lines#getStroke()} 为厚度, 沿半径 r 的圆周从 fromRadian 扫到 toRadian,
     * 每个分段用 {@link Fill#quad} 拼出一个四边形, 从而得到任意线宽的平滑圆弧。
     * 传动带绕过皮带轮的部分用它绘制。</p>
     *
     * @param x          圆心 x
     * @param y          圆心 y
     * @param r          圆弧半径
     * @param fromRadian 起始角 (弧度)
     * @param toRadian   结束角 (弧度, 可与起始角大小相反表示逆时针)
     */
    public static void arc(float x, float y, float r, float fromRadian, float toRadian){
        // 分段数按圆弧长度折算, 保证不同半径下的平滑度一致
        int seg = (int)Math.max(1, Lines.circleVertices(r) * Math.abs(toRadian - fromRadian) / (2 * Mathf.pi));
        Vec2 ptop = new Vec2(), pbottom = new Vec2();
        float c = Mathf.cos(fromRadian);
        float s = Mathf.sin(fromRadian);
        float thick = Lines.getStroke() * 0.5f;
        ptop.set(c * (r + thick) + x, s * (r + thick) + y);
        pbottom.set(c * (r - thick) + x, s * (r - thick) + y);
        for(int i = 0; i < seg; i++){
            float t = Mathf.lerp(fromRadian, toRadian, (i + 1f) / seg);
            c = Mathf.cos(t);
            s = Mathf.sin(t);
            float ctx = c * (r + thick) + x, cty = s * (r + thick) + y;
            float cbx = c * (r - thick) + x, cby = s * (r - thick) + y;
            // 上一条弧段的下/上边与当前弧段拼接成一个四边形
            Fill.quad(Core.atlas.white(), ptop.x, ptop.y, ctx, cty, cbx, cby, pbottom.x, pbottom.y);
            ptop.set(ctx, cty);
            pbottom.set(cbx, cby);
        }
    }
}
