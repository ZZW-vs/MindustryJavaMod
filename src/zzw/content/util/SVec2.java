package zzw.content.util;

/**
 * 结构体 Vec2 (PU132 unity.gen.SVec2 精简移植)
 *
 * <p>把两个 float 打包进一个 long (x 低 32 位, y 高 32 位),
 * 用于在数组中零装箱地存储坐标 (坩埚固体物品随机位置表)。</p>
 *
 * <p>PU132 中由注解处理器从 {@code @Struct} 注解生成完整方法集;
 * 这里只移植 Crucible 用到的 construct/x/y 三个方法。</p>
 */
public final class SVec2{

    /** 读取打包坐标的 x 分量 */
    public static float x(long svec2){
        return Float.intBitsToFloat((int)(svec2 & 0xFFFFFFFFL));
    }

    /** 读取打包坐标的 y 分量 */
    public static float y(long svec2){
        return Float.intBitsToFloat((int)((svec2 >>> 32) & 0xFFFFFFFFL));
    }

    /** 打包两个 float 为一个 long 坐标 */
    public static long construct(float x, float y){
        return ((long)Float.floatToIntBits(x)) | (((long)Float.floatToIntBits(y)) << 32);
    }
}
