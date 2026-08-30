package zzw.content.util;

/**
 * 两个 float 的打包结构 (PU132 unity.gen.Float2 精简移植)
 * <p>Light.child 的回调返回值: x=rotation, y=strengthMult。</p>
 */
public final class Float2 {

    /** 读取 x 分量 (低 32 位) */
    public static float x(long f2) {
        return Float.intBitsToFloat((int)(f2 & 0xFFFFFFFFL));
    }

    /** 读取 y 分量 (高 32 位) */
    public static float y(long f2) {
        return Float.intBitsToFloat((int)((f2 >>> 32) & 0xFFFFFFFFL));
    }

    /** 打包两个 float */
    public static long construct(float x, float y) {
        return ((long)Float.floatToIntBits(x)) | (((long)Float.floatToIntBits(y)) << 32);
    }
}