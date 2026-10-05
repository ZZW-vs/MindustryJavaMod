package zzw.content.graphics;

import arc.ApplicationCore;
import arc.ApplicationListener;
import arc.Core;
import arc.graphics.Color;
import arc.graphics.gl.FrameBuffer;
import arc.struct.FloatSeq;
import mindustry.Vars;
import mindustry.graphics.Layer;

import static arc.Core.graphics;
import static arc.graphics.g2d.Draw.draw;

public class BlackHoleSFX implements ApplicationListener {
    public static BlackHoleSFX inst;

    public FrameBuffer buffer;
    public FloatSeq blackHoleQueue = new FloatSeq();

    /** 上一次 resize 时的屏幕宽高, 用于避免每帧重复 resize 帧缓冲。 */
    private int lastWidth = -1, lastHeight = -1;

    public BlackHoleSFX() {
        if(Vars.platform instanceof ApplicationCore core){
            core.add(this);
        }
        inst = this;
    }

    public void load() {
        // 着色器由 ShaderLib 统一创建, 这里只负责本系统自己的帧缓冲
        buffer = new FrameBuffer(2, 2);
    }

    public void blackHole(float x, float y, float intensity, float swirl) {
        blackHoleQueue.add(x, y, intensity, swirl);
    }

    @Override
    public void update() {
        // 每帧调用，但真正的绘制在 draw 事件里
    }

    @Override
    public void dispose() {
        // 本类已注册为 ApplicationListener, 在此释放自身帧缓冲与 ShaderLib 的 GL 资源
        if(buffer != null){ buffer.dispose(); buffer = null; }
        ShaderLib.dispose();
    }

    public void render() {
        if(buffer == null || ShaderLib.blackHole == null) return;
        if(blackHoleQueue.isEmpty()) return;

        // ★ 性能: 仅在窗口尺寸变化时重建帧缓冲 (原实现每帧 resize,
        //   会反复触发 GL 纹理重建); 尺寸不变时直接复用。
        int w = graphics.getWidth(), h = graphics.getHeight();
        if(w != lastWidth || h != lastHeight){
            buffer.resize(w, h);
            lastWidth = w;
            lastHeight = h;
        }

        ShaderLib.blackHole.holes.addAll(blackHoleQueue);

        draw(Layer.floor - 1f, () -> buffer.begin(Color.clear));
        draw(Layer.blockOver + 0.1f, () -> {
            buffer.end();
            buffer.blit(ShaderLib.blackHole);
            ShaderLib.blackHole.holes.clear();
        });

        blackHoleQueue.clear();
    }
}