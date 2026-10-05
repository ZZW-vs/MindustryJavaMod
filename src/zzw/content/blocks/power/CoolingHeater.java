package zzw.content.blocks.power;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import mindustry.content.Liquids;
import zzw.content.graphics.UnityDrawf;

import static arc.Core.atlas;

/**
 * 制冷机 (主动冷却方块)
 * <p>
 * 照抄 {@link CombustionHeater 燃烧加热器} 的结构: 4 方向底座 + 热色叠加渲染,
 * 区别在于它是<b>反向</b>工作 —— 消耗冷冻液, 把所在热量网络的温度持续拉低,
 * 最低降至 {@code minTemp} (默认 -200℃ = 73.15K)。
 * <p>
 * 由于项目中没有独立的制冷机贴图, 这里复用燃烧加热器的贴图
 * ({@code combustion-heater} 系列), 通过重写 {@code load()} 指定。
 */
public class CoolingHeater extends HeatGenerator{
    /** 4 方向底座贴图 (复用燃烧加热器) */
    public final TextureRegion[] baseRegions = new TextureRegion[4];
    /** 最低温度 (K), 默认 -200℃ */
    protected float minTemp = 73.15f;
    /** 每帧降温比例 (相对 当前温度-最低温度 的差值) */
    protected float coolingCoeff = 0.1f;

    public CoolingHeater(String name){
        super(name);

        rotate = true;
        hasLiquids = true;
        liquidCapacity = 30f;
        // 消耗冷冻液 (每 tick 0.1 = 6/秒)
        consumeLiquid(Liquids.cryofluid, 0.1f);
    }

    @Override
    public void load(){
        super.load();

        // 复用燃烧加热器贴图 (本项目暂无独立制冷机贴图)
        region = atlas.find("combustion-heater");
        heatRegion = atlas.find("combustion-heater-heat");
        for(int i = 0; i < 4; i++){
            baseRegions[i] = atlas.find("combustion-heater-base" + (i + 1));
        }
    }

    public class CoolingHeaterBuild extends HeatGeneratorBuild{
        @Override
        public boolean shouldConsume(){
            // 仅在温度高于最低温度时才消耗冷冻液
            return super.shouldConsume() && heat() != null && heat().getTemp() > minTemp;
        }

        @Override
        public void updatePost(){
            var h = heat();
            if(h == null) return;

            float temp = h.getTemp();
            if(temp <= minTemp) return;

            if(canConsume()){
                consume();
                // 按 (当前温度 - 最低温度) 的比例降温, 渐近逼近 minTemp, 不会越界
                h.heat -= (temp - minTemp) * coolingCoeff;
            }
        }

        @Override
        public void draw(){
            Draw.rect(baseRegions[rotation], x, y);
            UnityDrawf.drawHeat(heatRegion, x, y, rotdeg(), heat() == null ? 0f : heat().getTemp());

            drawTeamTop();
        }
    }
}