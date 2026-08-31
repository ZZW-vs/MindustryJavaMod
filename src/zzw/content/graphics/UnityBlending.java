package zzw.content.graphics;

import arc.graphics.Blending;
import arc.graphics.Gl;

/**
 * Unity 系列自定义混合模式 (PU132 unity.graphics.UnityBlending 移植)。
 *
 * <p>供 End 系列特效使用:</p>
 * <ul>
 *   <li>{@link #invert} 反色混合 (时停特效的外圈);</li>
 *   <li>{@link #multiply} 正片叠底混合 (时停特效的红色内圈);</li>
 *   <li>{@link #shadowRealm} 影界混合。</li>
 * </ul>
 *
 * <p>★ v132 → v155 适配: {@link Blending} 与 {@link Gl} 常量在 v155 无变化,
 * 原样移植。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class UnityBlending{
    /** 影界混合: 源饱和度 / 1-源透明度。 */
    public static Blending shadowRealm = new Blending(Gl.srcAlphaSaturate, Gl.oneMinusSrcAlpha),
    /** 反色混合: 1-目标色 / 1-源色, 画上去的区域颜色反转。 */
    invert = new Blending(Gl.oneMinusDstColor, Gl.oneMinusSrcColor),
    /** 正片叠底混合: 目标色 / 1-源透明度。 */
    multiply = new Blending(Gl.dstColor, Gl.oneMinusSrcAlpha);
}
