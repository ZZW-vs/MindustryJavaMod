package zzw.content.blocks.production;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.world.blocks.production.GenericCrafter;
import zzw.content.Z_Sounds;
import zzw.content.graphics.UnityFx;

import static mindustry.Vars.tilesize;

/**
 * 压力机 (PU132 unity.world.blocks.production.Press 移植)
 *
 * <p>通用制造机: 左右压板随制作进度向中心挤压, 制作完成时播放金属碰撞音效
 * 并向四周溅射火花 (电涌合金色)。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>unity.content.UnitySounds.clang → zzw.content.Z_Sounds.clang</li>
 *   <li>unity.graphics.UnityFx.sparkBoi → zzw.content.graphics.UnityFx.sparkBoi</li>
 * </ul></p>
 */
public class Press extends GenericCrafter{
    /** 压板最大移动距离 (世界单位) */
    public float movementSize = 10f;
    /** 火花特效 Y 轴随机偏移 (单位: tile) */
    public float fxYVariation = 15f / tilesize;
    /** 压合音效 */
    public Sound clangSound = Z_Sounds.clang;
    /** 火花特效 */
    public Effect sparkEffect = UnityFx.sparkBoi;
    /** 左压板 / 右压板 / 底座贴图 */
    public TextureRegion leftRegion, rightRegion, baseRegion;

    public Press(String name){
        super(name);
        update = true;
        updateEffectChance = 0f;
    }

    @Override
    public void load(){
        super.load();
        leftRegion = Core.atlas.find(name + "-left");
        rightRegion = Core.atlas.find(name + "-right");
        baseRegion = Core.atlas.find(name + "-base");
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region, leftRegion, rightRegion};
    }

    public class PressBuild extends GenericCrafterBuild{
        /** 压板移动距离 (movementSize 换算为 tile 单位) */
        public float realMovementSize = movementSize / tilesize;
        /** 红色能量圈最大透明度 */
        public float alphaValueMax = 0.4f;
        /** 红色能量圈当前透明度 (工作时渐入, 停止时渐出) */
        public float alphaValue = 0f;

        @Override
        public void draw(){
            Draw.rect(baseRegion, x, y);
            Draw.color(Team.crux.color);
            if(alphaValue > 0f){
                Draw.alpha(alphaValue);
                for(int i = 0; i < 10; i++){
                    Fill.circle(x, y, i * 0.6f + Mathf.sin((totalProgress + Time.time) / 16f) / 3f);
                }
            }
            Draw.color();
            Draw.rect(leftRegion,
                x - Math.abs(Mathf.sin(Mathf.clamp(progress * 1.2f - 0.2f, 0, 1) / 2 * 360 * Mathf.degreesToRadians)) * realMovementSize, y);
            Draw.rect(rightRegion,
                x + Math.abs(Mathf.sin(Mathf.clamp(progress * 1.2f - 0.2f, 0, 1) / 2 * 360 * Mathf.degreesToRadians)) * realMovementSize, y);
            Draw.rect(region, x, y);
        }

        @Override
        public void updateTile(){
            super.updateTile();

            if(efficiency > 0.001f){
                alphaValue += 0.01f;
            }else{
                alphaValue -= 0.01f;
            }
            alphaValue = Mathf.clamp(alphaValue, 0f, alphaValueMax);
        }

        @Override
        public void consume(){
            super.consume();

            clangSound.at(x, y, Mathf.random(0.6f, 0.8f));

            for(int i = 0; i < 8; i++){
                sparkEffect.at(x, y + Mathf.range(fxYVariation), Mathf.random() * 360, Items.surgeAlloy.color,
                    Mathf.random() + 0.5f);
            }
        }
    }
}
