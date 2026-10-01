package zzw.content.graphics;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import mindustry.graphics.Shaders;

/**
 * 绘图工具 (PU132 unity.graphics.UnityDrawf 移植, 仅图系统所需方法)
 * 提供热贴图渲染 drawHeat 和栅栏 tiling 索引 tileMap
 */
public class UnityDrawf{
    /**
     * 栅栏 tiling 索引表 (PU132 移植)。
     * <p>8 邻居位掩码 (0~255) → 栅栏贴图索引 (0~47)。
     * 用于 SporeFarm 等需要栅栏连接的方块。</p>
     */
    public static final byte[] tileMap = {
        39, 36, 39, 36, 27, 16, 27, 24, 39, 36, 39, 36, 27, 16, 27, 24,
        38, 37, 38, 37, 17, 41, 17, 43, 38, 37, 38, 37, 26, 21, 26, 25,
        39, 36, 39, 36, 27, 16, 27, 24, 39, 36, 39, 36, 27, 16, 27, 24,
        38, 37, 38, 37, 17, 41, 17, 43, 38, 37, 38, 37, 26, 21, 26, 25,
         3,  4,  3,  4, 15, 40, 15, 20,  3,  4,  3,  4, 15, 40, 15, 20,
         5, 28,  5, 28, 29, 10, 29, 23,  5, 28,  5, 28, 31, 11, 31, 32,
         3,  4,  3,  4, 15, 40, 15, 20,  3,  4,  3,  4, 15, 40, 15, 20,
         2, 30,  2, 30,  9, 47,  9, 22,  2, 30,  2, 30, 14, 44, 14,  6,
        39, 36, 39, 36, 27, 16, 27, 24, 39, 36, 39, 36, 27, 16, 27, 24,
        38, 37, 38, 37, 17, 41, 17, 43, 38, 37, 38, 37, 26, 21, 26, 25,
        39, 36, 39, 36, 27, 16, 27, 24, 39, 36, 39, 36, 27, 16, 27, 24,
        38, 37, 38, 37, 17, 41, 17, 43, 38, 37, 38, 37, 26, 21, 26, 25,
         3,  0,  3,  0, 15, 42, 15, 12,  3,  0,  3,  0, 15, 42, 15, 12,
         5,  8,  5,  8, 29, 35, 29, 33,  5,  8,  5,  8, 31, 34, 31,  7,
         3,  0,  3,  0, 15, 42, 15, 12,  3,  0,  3,  0, 15, 42, 15, 12,
         2,  1,  2,  1,  9, 45,  9, 19,  2,  1,  2,  1, 14, 18, 14, 13
    };

    /** 贴图 UV 裁剪缓冲 (drawSlideRect 复用, 避免每帧分配) */
    private static final TextureRegion nRegion = new TextureRegion();

    /**
     * 绘制热贴图 (PU132 原版逻辑): 温度 > 498K 显示热色 (additive 叠加),
     * 温度 < 273K 显示冷色; 过热 (a>1) 时颜色饱和泛白。
     */
    public static void drawHeat(TextureRegion reg, float x, float y, float rot, float temp){
        float a;
        if(temp > 273.15f){
            a = Math.max(0f, (temp - 498f) * 0.001f);
            if(a < 0.01f) return;
            if(a > 1f){
                arc.graphics.Color fCol = mindustry.graphics.Pal.turretHeat.cpy().add(0, 0, 0.01f * a);
                fCol.mul(a);
                Draw.color(fCol, a);
            }else{
                Draw.color(mindustry.graphics.Pal.turretHeat, a);
            }
        }else{
            a = 1f - Mathf.clamp(temp / 273.15f);
            if(a < 0.01f) return;
            Draw.color(UnityPal.coldColor, a);
        }
        Draw.blend(arc.graphics.Blending.additive);
        Draw.rect(reg, x, y, rot);
        Draw.blend();
        Draw.color();
    }

    /**
     * 绘制滚动条纹矩形 (PU132 原版): 通过 UV 偏移实现贴图平移流动效果,
     * 坩埚泵的液体流动动画用。
     */
    public static void drawSlideRect(TextureRegion region, float x, float y, float w, float h, float tw, float th, float rot, int step, float offset){
        if(region == null) return;
        nRegion.set(region);

        float scaleX = w / tw;
        float texW = nRegion.u2 - nRegion.u;

        nRegion.u += Mathf.map(offset % 1, 0f, 1f, 0f, texW * step / tw);
        nRegion.u2 = nRegion.u + scaleX * texW;
        Draw.rect(nRegion, x, y, w, h, w * 0.5f, h * 0.5f, rot);
    }

    /**
     * 绘制"建造进度"特效 (PU132 unity.graphics.UnityDrawf.drawConstruct 移植)。
     *
     * <p>用 build 着色器按 progress 从下往上扫出已建造的部分, 蓝图未建满时
     * 显示扫描光带效果。Chopper 未付满造价时用它绘制刀刃半成品。</p>
     *
     * @param region   待绘制贴图
     * @param progress 建造进度 (0~1)
     * @param color    扫描颜色
     * @param alpha    透明度
     * @param time     动画时间 (内部会折算为着色器 time)
     * @param layer    绘制层级
     * @param func     实际绘制回调 (负责摆放贴图)
     */
    public static void drawConstruct(TextureRegion region, float progress, Color color, float alpha, float time, float layer, Cons<TextureRegion> func){
        nRegion.set(region);
        Draw.draw(layer, () -> {
            Shaders.build.region = nRegion;
            Shaders.build.progress = progress;
            Shaders.build.color.set(color);
            Shaders.build.color.a = alpha;
            Shaders.build.time = -time / 20f;
            Draw.shader(Shaders.build);
            func.get(nRegion);
            Draw.shader();
            Draw.reset();
        });
    }
}
