package zzw.content.graphics;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.Shader;

/**
 * 黑洞着色器 - 用于黑洞子弹的扭曲效果
 * 
 * 使用 blackholeshader.frag 和 blackholeshader.vert
 * 加载 blackhole_noise.png 作为噪声纹理
 */
public class BlackHoleShader {
    public static Shader shader;
    public static TextureRegion noise;
    
    /**
     * 加载着色器和纹理
     * 应该在 Mod 主类的 load() 方法中调用
     */
    public static void load() {
        // 加载着色器程序
        shader = new Shader(
            Core.files.internal("shaders/blackholeshader.vert"),
            Core.files.internal("shaders/blackholeshader.frag")
        );
        
        // 加载噪声纹理
        noise = Core.atlas.find("blackhole-noise");
        
        // 预编译着色器
        shader.bind();
        shader.dispose();
    }
    
    /**
     * 应用黑洞效果的统一参数设置
     * 
     * @param radius 黑洞半径
     * @param intensity 强度 (1.0 + b.fin() * 1.5f)
     * @param swirl 漩涡强度
     */
    public static void apply(float radius, float intensity, float swirl) {
        if (shader == null || noise == null) return;
        
        shader.bind();
        
        // 绑定噪声纹理到单元 0
        noise.texture.bind(0);
        
        // 设置统一变量
        shader.setUniformf("u_texture", 0); // 纹理单元 0
        shader.setUniformf("u_texsize", radius, radius);
        shader.setUniformf("u_invsize", 1.0f / radius, 1.0f / radius);
        shader.setUniformf("u_camscl", 1.0f);
        
        // 设置黑洞参数 (最多支持 8 个，这里只用 1 个)
        shader.setUniformf("u_holes[0]", radius * 0.5f, radius * 0.5f, intensity, swirl);
        shader.setUniformi("u_holes_count", 1);
    }
    
    /**
     * 释放资源
     */
    public static void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        noise = null;
    }
}