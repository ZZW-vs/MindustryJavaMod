package zzw.content.util;

import arc.graphics.Color;

/**
 * 打包颜色工具 (PU132 unity.gen.SColor 精简移植)
 * <p>PU132 的 SColor 是注解生成的 struct 工具; 这里基于 arc Color 的
 * ABGR8888 打包格式 (Color.toFloatBits 同序) 实现分量读取。</p>
 */
public final class SColor {
    private static final Color tmp = new Color();

    /** 读取 ABGR 打包颜色的 r 分量 [0..1] */
    public static synchronized float r(int color) {
        tmp.abgr8888(color);
        return tmp.r;
    }

    /** 读取 g 分量 */
    public static synchronized float g(int color) {
        tmp.abgr8888(color);
        return tmp.g;
    }

    /** 读取 b 分量 */
    public static synchronized float b(int color) {
        tmp.abgr8888(color);
        return tmp.b;
    }

    /** 读取 a 分量 */
    public static synchronized float a(int color) {
        tmp.abgr8888(color);
        return tmp.a;
    }

    /** 修改 alpha 通道 */
    public static int a(int color, float alpha) {
        return (color & 0xFFFFFF00) | ((int)(alpha * 255f) & 0xFF);
    }
}