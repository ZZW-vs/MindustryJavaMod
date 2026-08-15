package zzw.content.graphics;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;

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

    /** 绘制热贴图, 颜色随温度 (K) 变化 */
    public static void drawHeat(TextureRegion region, float x, float y, float rotation, float temp){
        if(region == null || !Core.settings.getBool("effects")) return;
        // 温度归一化到 0~1 (室温 293.15K ~ 1273.15K)
        float t = Mathf.clamp((temp - 293.15f) / 980f);
        // 冷 (蓝) -> 热 (红橙)
        Draw.color(1f, 0.3f * (1f - t), 0.2f * (1f - t), 0.75f * t + 0.05f);
        Draw.rect(region, x, y, rotation);
        Draw.color();
    }
}
