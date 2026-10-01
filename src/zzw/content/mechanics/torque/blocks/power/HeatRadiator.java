package zzw.content.mechanics.torque.blocks.power;

import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.content.*;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.*;

import static arc.Core.*;

/**
 * 散热器 (PU160 unity.world.blocks.power.HeatRadiator 移植)
 * <p>
 * 热量网络的耗散端: 把所在网络的热量辐射到环境中 (由 GraphHeat 的
 * radiativity 决定散热速率), 高温时会冒出蒸汽粒子。
 * <p>
 * 移植说明: 原版有 2 个朝向变体贴图 (name1/name2), 本项目 small-radiator
 * 只有单张 {@code small-radiator.png}, 因此找不到变体时回退到本体贴图。
 * 参数取自 参考/PU160反编译/.../YoungchaBlocks.java L553-564。
 */
public class HeatRadiator extends GraphBlock{
    /** 朝向变体贴图 (最多 2 种) */
    final TextureRegion[] rotateregions = new TextureRegion[2];

    public HeatRadiator(String name){
        super(name);

        rotate = true;
        solid = true;
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < 2; i++){
            String variant = name + (i + 1);
            rotateregions[i] = atlas.has(variant) ? atlas.find(variant) : region;
        }
    }

    public class HeatRadiatorBuild extends GraphBuild{
        @Override
        public void updatePost(){
            // 温度越高, 冒蒸汽越频繁 (273K→0, 2073K→1%)
            float likely = Mathf.map(heat().getTemp(), 273.15f, 2073.15f, 0f, 0.01f);
            if(Mathf.random() < likely){
                Fx.steam.at(x + Mathf.range(8), y + Mathf.range(8));
            }
        }

        @Override
        public void draw(){
            Draw.rect(rotateregions[rotation % 2], x, y);
            if(heatRegion != null){
                // ★ 对齐原版: 热色叠加随方块朝向旋转 (原版传 rotdeg())
                UnityDrawf.drawHeat(heatRegion, x, y, rotdeg(), heat().getTemp());
            }

            drawTeamTop();
        }
    }
}
