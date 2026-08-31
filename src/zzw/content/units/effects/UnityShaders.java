package zzw.content.units.effects;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.geom.Vec2;

import static mindustry.Vars.headless;
import static mindustry.Vars.tree;

/**
 * Unity 系列着色器 (PU132 unity.assets.list.UnityShaders 精简移植)。
 *
 * <p>本类只移植 {@link SpecialFx} 所需的部分:</p>
 * <ul>
 *   <li>{@link FragmentationShader} 碎裂消散着色器 (fragmentation /
 *       fragmentationFast 特效);</li>
 *   <li>{@link VapourizeShader} 汽化消散着色器 (endgameVapourize 特效);</li>
 *   <li>{@link #bufferAlt} 公用帧缓冲 (begin/end 在同一函数内时使用)。</li>
 * </ul>
 *
 * <p>★ v132 → v155 适配要点:</p>
 * <ul>
 *   <li>原版还包含 StencilShader / MegalithRingShader / 3D 着色器提供器,
 *       与特效无关, 未随本次移植;</li>
 *   <li>{@code tree.get("shaders/xxx")} 的 shader 资源
 *       (fragmentation.frag / fragmentnoise.png / vapourize.frag /
 *       vapourizenoise.png) 已随项目 assets/shaders/ 提供;</li>
 *   <li>需在客户端加载阶段调用 {@link #load()} (mod init 接入,
 *       单位移植阶段统一挂接)。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class UnityShaders{
    /** 汽化消散着色器 (End 系列 "单位化为尘埃")。 */
    public static VapourizeShader vapourizeShader;
    /** 碎裂消散着色器 (End 系列 "单位碎裂剥离")。 */
    public static FragmentationShader fragmentShader;

    /** 公用帧缓冲: 仅当 begin() 与 end() 在同一函数内时使用。 */
    public static FrameBuffer bufferAlt;

    /**
     * 加载着色器 (仅客户端)。重复调用时先释放旧实例防泄漏。
     */
    public static void load(){
        if(headless) return;

        dispose();
        bufferAlt = new FrameBuffer();

        vapourizeShader = new VapourizeShader();
        fragmentShader = new FragmentationShader();
    }

    /** 释放已创建的着色器与帧缓冲。 */
    public static void dispose(){
        if(vapourizeShader != null){
            vapourizeShader.dispose();
            vapourizeShader = null;
        }
        if(fragmentShader != null){
            fragmentShader.dispose();
            fragmentShader = null;
        }
        if(bufferAlt != null){
            bufferAlt.dispose();
            bufferAlt = null;
        }
    }

    /**
     * 碎裂消散着色器 (PU132 UnityShaders.FragmentationShader 移植)。
     *
     * <p>uniform 含义:</p>
     * <ul>
     *   <li>u_noise: 碎裂噪声纹理 (fragmentnoise.png, 重复平铺);</li>
     *   <li>u_blastpos / u_blastforce: 碎裂爆心与风向 (风把碎片吹离);</li>
     *   <li>heatcolor / heatprogress: 灼烧颜色与进度 (lightFlame → darkFlame);</li>
     *   <li>fragprogress: 碎裂进度 (0 → 1 全部剥离);</li>
     *   <li>size: 碎片尺寸基准。</li>
     * </ul>
     */
    public static class FragmentationShader extends Shader{
        /** 碎裂噪声纹理。 */
        public Texture noise;
        /** 碎裂爆心 (世界坐标)。 */
        public Vec2 source = new Vec2(), direction = new Vec2();
        /** 灼烧颜色。 */
        public Color heatColor = new Color();
        /** 灼烧进度 / 碎裂进度 / 碎片尺寸。 */
        public float heatProgress, fragProgress, size;

        public FragmentationShader(){
            super(
                Core.files.internal("shaders/screenspace.vert"),
                tree.get("shaders/fragmentation.frag")
            );
            if(noise == null){
                noise = new Texture(tree.get("shaders/fragmentnoise.png"));
                noise.setFilter(Texture.TextureFilter.linear);
                noise.setWrap(Texture.TextureWrap.repeat);
            }
        }

        @Override
        public void apply(){
            // 步骤 1: 绑定噪声纹理到纹理单元 1, 屏幕缓冲到单元 0
            noise.bind(1);
            bufferAlt.getTexture().bind(0);

            // 步骤 2: 传递相机与屏幕参数
            setUniformi("u_noise", 1);

            setUniformf("u_texsize", Core.camera.width, Core.camera.height);
            setUniformf("u_invsize", 1f / Core.camera.width, 1f / Core.camera.height);
            setUniformf("u_campos",
                Core.camera.position.x - Core.camera.width / 2,
                Core.camera.position.y - Core.camera.height / 2);

            // 步骤 3: 传递碎裂参数
            setUniformf("u_blastpos", source);
            setUniformf("u_blastforce", direction);
            setUniformf("heatcolor", heatColor);

            setUniformf("heatprogress", heatProgress);
            setUniformf("fragprogress", fragProgress);
            setUniformf("size", size);
        }
    }

    /**
     * 汽化消散着色器 (PU132 UnityShaders.VapourizeShader 移植)。
     *
     * <p>uniform 含义:</p>
     * <ul>
     *   <li>u_noise: 汽化噪声纹理 (vapourizenoise.png, 镜像平铺);</li>
     *   <li>position: 风源位置 (碎片向远离风源方向飘散);</li>
     *   <li>progress / fragprogress: 整体进度与碎片剥离进度;</li>
     *   <li>tocolor / colorprog: 汽化颜色过渡 (→ Pal.rubble) 及进度;</li>
     *   <li>size: 碎片尺寸基准。</li>
     * </ul>
     */
    public static class VapourizeShader extends Shader{
        /** 汽化噪声纹理。 */
        public Texture noise;
        /** 风源位置。 */
        public Vec2 windSource = new Vec2();
        /** 汽化目标颜色。 */
        public Color toColor = new Color();
        /** 整体进度 / 颜色进度 / 碎片进度 / 尺寸。 */
        public float progress, colorProgress, fragProgress, size;

        public VapourizeShader(){
            super(
                Core.files.internal("shaders/screenspace.vert"),
                tree.get("shaders/vapourize.frag")
            );
            if(noise == null){
                noise = new Texture(tree.get("shaders/vapourizenoise.png"));
                noise.setWrap(Texture.TextureWrap.mirroredRepeat);
            }
        }

        @Override
        public void apply(){
            // 步骤 1: 绑定噪声纹理到纹理单元 1, 屏幕缓冲到单元 0
            noise.bind(1);
            bufferAlt.getTexture().bind(0);

            setUniformi("u_noise", 1);

            // 步骤 2: 传递风源与进度参数
            setUniformf("position", windSource);

            setUniformf("progress", progress);
            setUniformf("fragprogress", fragProgress);

            setUniformf("tocolor", toColor);
            setUniformf("colorprog", colorProgress);
            setUniformf("size", size);

            // 步骤 3: 传递相机与屏幕参数
            setUniformf("u_texsize", Core.camera.width, Core.camera.height);
            setUniformf("u_invsize", 1f / Core.camera.width, 1f / Core.camera.height);
            setUniformf("u_offset",
                Core.camera.position.x - Core.camera.width / 2,
                Core.camera.position.y - Core.camera.height / 2);
        }
    }
}
