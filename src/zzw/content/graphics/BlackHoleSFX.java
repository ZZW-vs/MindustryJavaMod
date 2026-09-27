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
    public BlackHoleShader blackholeShader;
    public FloatSeq blackHoleQueue = new FloatSeq();

    public BlackHoleSFX() {
        if(Vars.platform instanceof ApplicationCore core){
            core.add(this);
        }
        inst = this;
    }

    public void load() {
        buffer = new FrameBuffer(2, 2);
        blackholeShader = new BlackHoleShader();
    }

    public void blackHole(float x, float y, float intensity, float swirl) {
        blackHoleQueue.add(x, y, intensity, swirl);
    }

    @Override
    public void update() {
        // 每帧调用，但真正的绘制在 draw 事件里
    }

    public void render() {
        if(buffer == null || blackholeShader == null) return;
        if(blackHoleQueue.isEmpty()) return;

        buffer.resize(graphics.getWidth(), graphics.getHeight());

        blackholeShader.holes.addAll(blackHoleQueue);

        draw(Layer.floor - 1f, () -> buffer.begin(Color.clear));
        draw(Layer.blockOver + 0.1f, () -> {
            buffer.end();
            buffer.blit(blackholeShader);
            blackholeShader.holes.clear();
        });

        blackHoleQueue.clear();
    }
}