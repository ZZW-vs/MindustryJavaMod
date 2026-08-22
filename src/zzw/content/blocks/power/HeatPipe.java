package zzw.content.blocks.power;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Eachable;
import mindustry.content.StatusEffects;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Unit;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.Utils;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.util.GraphicUtils;

import static arc.Core.atlas;
import static mindustry.Vars.tilesize;

/**
 * 热管 (PU132 unity.world.blocks.distribution.HeatPipe 移植)
 *
 * <p>连接热量网络的导热管道: 贴图按 4 邻居连接位掩码自动拼接 (8x2 切片 16 变体)。
 * 高温时发光, 单位踩上会受到灼烧伤害。管道温度低于 0°C 或高于 225°C 时显示热色。</p>
 *
 * <p>适配说明: unity.util.GraphicUtils → zzw.content.util.GraphicUtils;
 * onNeighboursChanged 为项目 GraphBlockBase 接口方法。</p>
 */
public class HeatPipe extends GraphBlock{
    final static Color baseColor = Color.valueOf("6e7080");
    /** 邻居方向到位掩码位的映射 (右→0, 上→3, 左→2, 下→1) */
    final static int[] shift = new int[]{0, 3, 2, 1};
    /** 热色贴图 16 变体 (8x2 切片) */
    TextureRegion[] heatRegions, regions;

    public HeatPipe(String name){
        super(name);
    }

    @Override
    public void load(){
        super.load();
        heatRegions = GraphicUtils.getRegions(heatRegion, 8, 2);
        regions = GraphicUtils.getRegions(atlas.find(name + "-tiles"), 8, 2);
    }

    @Override
    public void drawPlanRegion(BuildPlan req, Eachable<BuildPlan> list){
        float scl = tilesize * req.animScale;
        Draw.rect(region, req.drawx(), req.drawy(), scl, scl, req.rotation * 90f);
    }

    public class HeatPipeBuild extends GraphBuild{
        /** 邻居连接位掩码 (贴图变体索引) */
        int spriteIndex;

        @Override
        public void created(){
            rotation = 0;
            super.created();
        }

        @Override
        public void onNeighboursChanged(){
            spriteIndex = 0;
            heat().eachNeighbourValue(n -> spriteIndex += 1 << shift[n]);
        }

        @Override
        public void unitOn(Unit unit){
            if(timer(dumpTime, 20f)){
                float intensity = Mathf.clamp(Mathf.map(heat().getTemp(), 400f, 1000f, 0f, 1f));
                unit.apply(StatusEffects.burning, intensity * 20f + 5f);
                unit.damage(intensity * 10f);
            }
        }

        @Override
        public void draw(){
            float temp = heat().getTemp();
            Draw.rect(regions[spriteIndex], x, y);
            if(temp < 273f || temp > 498f){
                Draw.color(Utils.tempColor(temp).add(baseColor));
                Draw.rect(heatRegions[spriteIndex], x, y);
                Draw.color();
            }
            drawTeamTop();
        }
    }
}
