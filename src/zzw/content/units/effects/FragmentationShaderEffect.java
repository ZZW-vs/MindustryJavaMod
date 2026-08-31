package zzw.content.units.effects;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mathf;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.gen.Drawc;
import mindustry.gen.EffectState;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;

/**
 * 碎裂消散特效 (PU132 unity.entities.effects.FragmentationShaderEffect 移植)。
 *
 * <p>把目标实体 ({@link Drawc}, 通常是单位) 先绘制到离屏帧缓冲,
 * 再用 {@link UnityShaders.FragmentationShader} 以噪声纹理做
 * "碎片剥离 + 灼烧变色" 的屏幕空间后处理 —— 用于 End 系列处决演出。</p>
 *
 * <p>★ v132 → v155 适配要点:</p>
 * <ul>
 *   <li>{@code unity.assets.list.UnityShaders.fragmentShader} →
 *       {@link UnityShaders#fragmentShader} / {@link UnityShaders#bufferAlt}
 *       (本包内精简版);</li>
 *   <li>{@link EffectState} 组件化后 x / y / rotation / lifetime / data
 *       仍为 public 字段, {@code add()} 加入世界的方式不变;</li>
 *   <li>{@code Draw.blit(buffer, shader)} 在 v155 仍存在。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class FragmentationShaderEffect extends Effect{
    /** 碎裂开始进度偏移 (0~1, 越大碎裂开始越晚)。 */
    public float fragOffset = 0.2f;
    /** 灼烧开始进度偏移 (0~1, 越大灼烧开始越晚)。 */
    public float heatOffset = 0.3f;
    /** 风力 (>=0 固定风力, <0 按 hitSize/14 自适应)。 */
    public float windPower = -1f;

    public FragmentationShaderEffect(float lifetime){
        this.lifetime = lifetime;
    }

    /**
     * 直接触发: 绕过原版 {@code at()} 的颜色参数, 自建
     * {@link FragEffectState} 并按 data 是否为 Drawc 决定裁剪尺寸。
     */
    @Override
    public void at(float x, float y, float rotation, Object data){
        if(!Vars.headless){
            FragEffectState e = Pools.obtain(FragEffectState.class, FragEffectState::new);
            e.x = x;
            e.y = y;
            e.rotation = rotation;
            e.lifetime = lifetime;
            e.type = this;
            e.data = data;
            if(data instanceof Drawc){
                e.clipSize = ((Drawc)data).clipSize();
            }else{
                e.clipSize = clip;
            }
            e.add();
        }
    }

    /**
     * 碎裂特效状态: 在实体自身图层把目标重绘进帧缓冲并套用碎裂着色器。
     */
    static class FragEffectState extends EffectState{
        FragmentationShaderEffect type;
        float clipSize;

        @Override
        public void draw(){
            if(data instanceof Drawc){
                Drawc draw = (Drawc)data;
                Unit unit = draw instanceof Unit ? (Unit)draw : null;

                // 步骤 1: 计算目标应处的图层 (飞行单位 / 地面单位分层)
                float z = Layer.flyingUnitLow;
                if(unit != null){
                    UnitType t = unit.type;
                    z = unit.elevation > 0.5f ? (t.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : t.groundLayer + Mathf.clamp(t.hitSize / 4000f, 0, 0.01f);
                }

                // 步骤 2: 在目标图层做屏幕空间后处理
                Draw.draw(z, () -> {
                    UnityShaders.FragmentationShader s = UnityShaders.fragmentShader;
                    if(unit != null){
                        unit.hitTime = 0f;
                        s.direction.trns(rotation, type.windPower >= 0f ? type.windPower : unit.hitSize / 14f);
                        s.source.set(unit);
                        s.size = unit.hitSize / 4f;
                    }else{
                        s.source.set(x, y);
                        s.direction.trns(rotation, type.windPower >= 0f ? type.windPower : clipSize / 14f);
                        s.size = 0f;
                    }

                    // 步骤 3: 灼烧颜色 lightFlame → darkFlame 随热度加深
                    float heat = type.heatOffset > 0f ? Mathf.curve(fin(), 0f, type.heatOffset) : 1f;
                    s.heatColor.set(Pal.lightFlame).lerp(Pal.darkFlame, heat);
                    s.fragProgress = Mathf.curve(fin(), type.fragOffset, 1f);
                    s.heatProgress = heat;

                    // 步骤 4: 重绘目标到帧缓冲, 再把缓冲以碎裂着色器回投到屏幕
                    FrameBuffer buffer = UnityShaders.bufferAlt;
                    buffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
                    buffer.begin(Color.clear);
                    draw.draw();
                    buffer.end();
                    Draw.blit(buffer, s);
                });
            }
        }

        @Override
        public float clipSize(){
            return clipSize * 2f;
        }
    }
}
