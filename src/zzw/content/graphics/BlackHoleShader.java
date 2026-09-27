package zzw.content.graphics;

import arc.Core;
import arc.files.Fi;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;

import static arc.Core.*;
import static mindustry.Vars.*;

public class BlackHoleShader extends Shader {
    static final int maxHoles = 8 * 4;
    public FloatSeq holes = new FloatSeq(maxHoles);
    public FloatSeq uniforms = new FloatSeq(maxHoles);

    public BlackHoleShader() {
        super(
            Core.files.internal("shaders/screenspace.vert"),
            tree.get("shaders/blackholeshader.frag")
        );
    }

    public void add(float x, float y, float intensity, float swirl) {
        if(holes.size >= maxHoles || intensity <= 0) return;
        holes.add(x, y, intensity, swirl);
    }

    @Override
    public void apply() {
        FrameBuffer b = BlackHoleSFX.inst.buffer;
        setUniformf("u_texsize", b.getWidth(), b.getHeight());
        setUniformf("u_invsize", 1f / Core.camera.width, 1f / Core.camera.height);
        setUniformf("u_camscl", renderer.getDisplayScale());

        uniforms.clear();
        float[] items = holes.items;
        for(int i = 0; i < holes.size; i += 4){
            Vec2 v = Core.camera.project(items[i], items[i + 1]);
            uniforms.add(v.x, v.y, items[i + 2], items[i + 3]);
        }

        setUniformi("u_holes_count", holes.size / 4);
        setUniform4fv("u_holes", uniforms.items, 0, uniforms.size);
    }
}