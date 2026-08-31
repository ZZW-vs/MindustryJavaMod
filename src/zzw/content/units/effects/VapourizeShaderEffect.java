package zzw.content.units.effects;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.gen.Drawc;
import mindustry.gen.EffectState;
import mindustry.gen.Unit;
import mindustry.gen.Velc;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

import static arc.graphics.g2d.Draw.alpha;
import static arc.graphics.g2d.Draw.blend;
import static arc.graphics.g2d.Draw.mixcol;
import static arc.graphics.g2d.Draw.rect;
import static arc.graphics.g2d.Draw.z;

/**
 * 汽化消散特效 (PU132 unity.entities.effects.VapourizeShaderEffect 移植)。
 *
 * <p>目标实体 ({@link Drawc}) 先以 "红色加色" 叠绘一遍 (肉眼可见的
 * 汽化轮廓), 再在护盾动画开启时重绘到离屏帧缓冲, 用
 * {@link UnityShaders.VapourizeShader} 做碎片剥离后处理 ——
 * 用于 End 系列的大范围处决演出。</p>
 *
 * <p>data 支持两种形态:</p>
 * <ul>
 *   <li>单个 Drawc (单位 / 建筑);</li>
 *   <li>{@code Object[]{Drawc, Object extra, float windScl}} ——
 *       extra 为风源 (Position), windScl 为风力系数,
 *       此时裁剪半径 = rotation×2, 寿命减半;</li>
 *   <li>{@code Object[]{Building[] 批量, Position 风源, float windScl}}
 *       —— 多建筑一起汽化。</li>
 * </ul>
 *
 * <p>★ v132 → v155 适配要点:</p>
 * <ul>
 *   <li>{@code unity.assets.list.UnityShaders.vapourizeShader / bufferAlt} →
 *       {@link UnityShaders} (本包内精简版);</li>
 *   <li>PU132 在 {@code reset()} 中清理池化字段, v155 生成类的 reset
 *       挂钩不可靠, 改为在 at() 创建时显式清零 (语义等价);</li>
 *   <li>{@code Draw.rect(region, Position, rotation)} 重载 v155 存在。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class VapourizeShaderEffect extends Effect{
    /** 是否在特效期间继续推进目标的速度 (惯性飞行)。 */
    boolean updateVel = true;

    public VapourizeShaderEffect(float lifetime, float clipsize){
        super(lifetime, clipsize, e -> {});
    }

    @Override
    public void at(Position pos){
        at(pos.getX(), pos.getY());
    }

    @Override
    public void at(float x, float y){
        at(x, y, 0f);
    }

    @Override
    public void at(Position pos, float rotation){
        at(pos.getX(), pos.getY(), rotation);
    }

    @Override
    public void at(float x, float y, Color color){
        at(x, y);
    }

    @Override
    public void at(float x, float y, float rotation){
        at(x, y, rotation, null, null);
    }

    @Override
    public void at(float x, float y, float rotation, Color color){
        at(x, y, rotation, null, null);
    }

    @Override
    public void at(float x, float y, float rotation, Color color, Object data){
        at(x, y, rotation, data);
    }

    /**
     * 直接触发: 解析 data 数组形态 (可选风源 + 风力系数),
     * 创建 {@link VapourizeShaderEffectState} 并加入世界。
     */
    @Override
    public void at(float x, float y, float rotation, Object data){
        if(Vars.headless || !Core.settings.getBool("effects")) return;
        VapourizeShaderEffectState s = Pools.obtain(VapourizeShaderEffectState.class, VapourizeShaderEffectState::new);
        s.x = x;
        s.y = y;
        s.rotation = rotation;
        // ★ 池化实例可能带旧值, 显式清零 (替代 PU132 的 reset() 清理)
        s.datab = null;
        s.windScl = -1f;
        s.clipSize = 0f;

        float l = lifetime;
        if(data instanceof Object[] d){
            s.datab = d[0];
            if(d.length >= 3){
                s.clipSize = rotation * 2f;
                s.windScl = (float)d[2];
                l /= 2f;
            }
            data = d[1];
        }
        s.data = data;
        s.lifetime = l;
        s.add();
    }

    public VapourizeShaderEffect updateVel(boolean v){
        updateVel = v;
        return this;
    }

    /**
     * 汽化特效状态: 红色轮廓叠绘 + 碎片剥离后处理。
     */
    public class VapourizeShaderEffectState extends EffectState{
        /** 风力系数 (<0 表示按目标 clipSize/8 自适应)。 */
        float windScl = -1f, clipSize;
        /** 风源 / 批量建筑数组。 */
        Object datab;

        @Override
        public void update(){
            super.update();

            // 汽化期间目标保持惯性飞行 (速度按 drag 衰减)
            if(updateVel && data instanceof Velc v){
                v.move(v.vel());
                v.vel().scl(1f - (v.drag() * Time.delta));
            }
        }

        @Override
        public void draw(){
            if(data instanceof Drawc draw){
                // 步骤 1: 红色加色轮廓 (无论护盾动画开关都绘制)
                float c = windScl > 0 ? windScl : draw.clipSize() / 8f;
                z(Layer.flyingUnitLow);
                blend(Blending.additive);
                mixcol(Color.red, 1f);
                alpha(fout());

                if(data instanceof Unit u){
                    u.hitTime = 0f;
                    rect(u.type.fullIcon, u, u.rotation - 90f);
                }else if(data instanceof Building b){
                    rect(b.block.region, b.x(), b.y());
                }

                blend();

                // 步骤 2: 护盾动画开启时, 重绘目标到帧缓冲并套用汽化着色器
                if(Vars.renderer.animateShields){
                    Draw.draw(z() + 0.001f, () -> {
                        float in = Mathf.clamp(fin() * 2f);

                        UnityShaders.VapourizeShader s = UnityShaders.vapourizeShader;
                        FrameBuffer buffer = UnityShaders.bufferAlt;
                        buffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
                        s.toColor.set(Pal.rubble);
                        s.colorProgress = Interp.pow2In.apply(Mathf.clamp(in * 1.25f));
                        s.progress = Interp.pow2In.apply(in);
                        s.windSource.set(datab instanceof Position p ? p : draw);
                        s.fragProgress = Interp.pow3In.apply(in) * c;
                        s.size = c;
                        buffer.begin(Color.clear);
                        draw.draw();
                        buffer.end();
                        buffer.blit(UnityShaders.vapourizeShader);
                    });
                }
            }else if(Vars.renderer.animateShields && data instanceof Building[] drwA && datab != null){
                // 步骤 3: 批量建筑汽化 —— 逐个视锥剔除后重绘进帧缓冲
                Draw.draw(Layer.block + 0.001f, () -> {
                    float in = fin();

                    UnityShaders.VapourizeShader s = UnityShaders.vapourizeShader;
                    FrameBuffer buffer = UnityShaders.bufferAlt;
                    s.toColor.set(Pal.rubble);
                    s.colorProgress = Interp.pow2In.apply(Mathf.clamp(in * 1.25f));
                    s.progress = Interp.pow2In.apply(in);
                    s.windSource.set((Position)datab);
                    s.fragProgress = Interp.pow3In.apply(in) * windScl;
                    s.size = 0f;
                    buffer.begin(Color.clear);

                    for(Building d : drwA){
                        if(Core.camera.bounds(Tmp.r1).overlaps(Tmp.r2.setCentered(d.x(), d.y(), d.block.clipSize + s.fragProgress * 2f))){
                            d.draw();
                        }
                    }

                    buffer.end();
                    buffer.blit(UnityShaders.vapourizeShader);
                });
            }
            Draw.reset();
        }

        @Override
        public float clipSize(){
            return Math.max(clip, clipSize);
        }
    }
}
