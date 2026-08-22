package zzw.content.blocks.power;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.type.Item;
import mindustry.world.consumers.ConsumeItemFilter;
import zzw.content.graphics.UnityDrawf;

import static arc.Core.atlas;

/**
 * 燃烧加热器 (PU132 unity.world.blocks.power.CombustionHeater 移植)
 *
 * <p>焚烧可燃物品 (flammability >= 0.1) 产生热量, 燃烧效率 = 物品可燃性。
 * 贴图 4 方向 (combustion-heater-base1~4)。</p>
 */
public class CombustionHeater extends HeatGenerator{
    /** 4 方向底座贴图 */
    public final TextureRegion[] baseRegions = new TextureRegion[4];

    public CombustionHeater(String name){
        super(name);
        rotate = true;
    }

    @Override
    public void load(){
        super.load();
        for(int i = 0; i < 4; i++) baseRegions[i] = atlas.find(name + "-base" + (i + 1));
    }

    @Override
    public void init(){
        consume(new ConsumeItemFilter(item -> item.flammability >= 0.1f)).update(false).optional(true, false);

        super.init();
    }

    public class CombustionHeaterBuild extends HeatGeneratorBuild{
        /** 燃烧计时 (1 个物品烧 1 单位时间, 每帧衰减 0.01) */
        float generateTime, productionEfficiency;

        @Override
        public void updatePost(){
            // v155.4: consValid() 不存在, 用 shouldConsume() 等价判断 (必要消耗满足)
            if(!shouldConsume()){
                productionEfficiency = 0f;
                return;
            }

            if(generateTime <= 0f && items.total() > 0f){
                Fx.generatespark.at(x + Mathf.range(3f), y + Mathf.range(3f));
                Item item = items.take();
                productionEfficiency = item.flammability;
                generateTime = 1f;
            }

            if(generateTime > 0f){
                generateTime -= Math.min(0.01f * delta(), generateTime);
            }else{
                productionEfficiency = 0f;
            }

            generateHeat(productionEfficiency);
        }

        @Override
        public void draw(){
            Draw.rect(baseRegions[rotation], x, y);
            UnityDrawf.drawHeat(heatRegion, x, y, rotdeg(), heat().getTemp());

            drawTeamTop();
        }

        @Override
        public void writeExt(Writes write){
            write.f(productionEfficiency);
        }

        @Override
        public void readExt(Reads read, byte revision){
            productionEfficiency = read.f();
        }
    }
}
