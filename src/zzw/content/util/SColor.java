package zzw.content.util;

import arc.graphics.Color;

/**
 * 打包颜色工具 (PU132 unity.gen.SColor 精简移植)
 *
 * <p>PU132 的 SColor 是注解生成的 struct 工具, 操作 {@link Color#rgba()} 打包
 * (RGBA8888: r 高位 → a 低位, 与 Color.set(int)/rgba8888 一致) 的整数颜色。
 * Light 光束的颜色管道全程使用该格式 (whiteRgba / tmpCol.rgba() / Tmp.c1.set)。</p>
 */
public final class SColor {
    private static final Color tmp = new Color();

    /** 读取 RGBA 打包颜色的 r 分量 [0..1] */
    public static synchronized float r(int color) {
        tmp.rgba8888(color);
        return tmp.r;
    }

    /** 读取 g 分量 */
    public static synchronized float g(int color) {
        tmp.rgba8888(color);
        return tmp.g;
    }

    /** 读取 b 分量 */
    public static synchronized float b(int color) {
        tmp.rgba8888(color);
        return tmp.b;
    }

    /** 读取 a 分量 */
    public static synchronized float a(int color) {
        tmp.rgba8888(color);
        return tmp.a;
    }

    /** 修改 alpha 通道 (RGBA 打包: a 在最低字节) */
    public static int a(int color, float alpha) {
        return (color & 0xFFFFFF00) | ((int)(alpha * 255f) & 0xFF);
    }
}