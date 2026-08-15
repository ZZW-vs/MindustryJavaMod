package zzw.content.blocks.production;

import arc.Core;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.blocks.production.*;
import zzw.content.Z_Sounds;
import zzw.content.graphics.UnityFx;

/**
 * 压力机 (PU132 unity.world.blocks.production.Press 移植)
 * <p>通用制造机, 制作时左右压板向中心挤压, 中心发光。
 * 制作完成时播放金属碰撞音效并产生火花特效。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>unity.content.UnitySounds.clang → zzw.content.Z_Sounds.clang</li>
 *   <li>unity.graphics.UnityFx.sparkBoi → zzw.content.graphics.UnityFx.sparkBoi</li>
 * </ul></p>
 */
public class Press extends GenericCrafter{
    /** 左侧压板区域 */
    public TextureRegion leftRegion, rightRegion;
    /** 中心发光区域 */
    public TextureRegion glowRegion;
    /** 压板对数 (默认 2 对) */
    public int presses = 2;
    /** 压板间距 */
    public float spread = 4f;
    /** 压板移动速度 */
    public float pressSpeed = 0.05f;
    /** 发光强度 */
    public float glowMagnitude = 0.5f;
    /** 发光缩放频率 */
    public float glowScl = 4f;

    public Press(String name){
        super(name);
    }

    @Override
    public void load(){
        super.load();
        leftRegion = Core.atlas.find(name + "-left");
        rightRegion = Core.atlas.find(name + "-right");
        glowRegion = Core.atlas.find(name + "-glow");
    }

    public class PressBuild extends GenericCrafterBuild{
        @Override
        public void draw(){
            super.draw();
            // 压板偏移量, 基于 progress 正弦波动画
            float o = Mathf.sin(progress * pressSpeed, 1, spread / 2f);
            for(int i = 0; i < presses; i++){
                float off = (i - (presses - 1) / 2f) * spread;
                Draw.rect(leftRegion, x + off + o, y);
                Draw.rect(rightRegion, x + off - o, y);
            }
            // 中心发光, 随预热和正弦波闪烁
            Draw.color(Pal.accent);
            Draw.alpha((0.5f + Mathf.absin(glowScl, glowMagnitude)) * warmup);
            Draw.rect(glowRegion, x, y);
            Draw.color();
        }

        @Override
        public void consume(){
            super.consume();
            // 制作完成时播放碰撞音效并产生火花
            Z_Sounds.clang.at(this);
            UnityFx.sparkBoi.at(x, y, size);
        }
    }
}
