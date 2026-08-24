package zzw.content.blocks.power;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.modules.GraphHeatModule;

import static arc.Core.atlas;

/**
 * 无限热源/冷源 (PU132 unity.world.blocks.sandbox.HeatSource 移植)
 *
 * <p>沙盒专用热量源方块: 热源每帧向热量网络注入热量直至 maxTemp,
 * 冷源 (isVoid=true) 则将网络热量归零。基座贴图 + 热色叠加渲染。</p>
 *
 * <p>注册配置 (PU132 UnityBlocks.java L3160-3171):
 * <pre>{@code
 * infiHeater = new HeatSource("infi-heater"){{
 *     requirements(Category.power, BuildVisibility.sandboxOnly, with());
 *     health = 200;
 *     addGraph(new GraphHeat(1000f, 1f, 0f).setAccept(1, 1, 1, 1));
 * }};
 *
 * infiCooler = new HeatSource("infi-cooler"){{
 *     requirements(Category.power, BuildVisibility.sandboxOnly, with());
 *     health = 200;
 *     isVoid = true;
 *     addGraph(new GraphHeat(1000f, 1f, 0f).setAccept(1, 1, 1, 1));
 * }};
 * }</pre></p>
 *
 * <p>适配说明: unity.graphics.UnityDrawf → zzw.content.graphics.UnityDrawf;
 * heat() 增加 null 检查 (与项目 GraphBlock.draw() 一致的防护风格)。</p>
 */
public class HeatSource extends HeatGenerator{
    /** true = 冷源 (热量归零), false = 热源 (持续注入热量) */
    protected boolean isVoid;
    /** 基座贴图 ({@code 方块名-base}) */
    TextureRegion baseRegion;

    public HeatSource(String name){
        super(name);
    }

    @Override
    public void load(){
        super.load();
        baseRegion = atlas.find(name + "-base");
    }

    public class HeatSourceBuild extends HeatGeneratorBuild{
        @Override
        public void updatePost(){
            GraphHeatModule heat = heat();
            if(heat == null) return;
            if(isVoid){
                // 冷源: 热量归零
                heat.heat = 0f;
            }else{
                // 热源: 每帧注入热量 (渐近逼近 maxTemp)
                generateHeat(1f);
            }
        }

        @Override
        public void draw(){
            GraphHeatModule heat = heat();
            float temp = heat == null ? 0f : heat.getTemp();
            Draw.rect(baseRegion, x, y);
            UnityDrawf.drawHeat(heatRegion, x, y, rotdeg(), temp);
            drawTeamTop();
        }
    }
}
