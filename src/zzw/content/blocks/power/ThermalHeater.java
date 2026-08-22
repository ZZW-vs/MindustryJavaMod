package zzw.content.blocks.power;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import mindustry.game.Team;
import mindustry.world.Tile;
import mindustry.world.meta.Attribute;
import zzw.content.graphics.UnityDrawf;

import static arc.Core.atlas;

/**
 * 地热加热器 (PU132 unity.world.blocks.power.ThermalHeater 移植)
 *
 * <p>放置在热液地板上, 产热量与脚下地板的热属性总和成正比。
 * 贴图 4 方向 (thermal-heater1~4), 随方块朝向选择。</p>
 */
public class ThermalHeater extends HeatGenerator{
    /** 4 方向贴图 */
    public final TextureRegion[] regions = new TextureRegion[4];
    /** 热属性 (热液地板) */
    public final Attribute attri = Attribute.heat;

    public ThermalHeater(String name){
        super(name);

        rotate = true;
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < 4; i++) regions[i] = atlas.find(name + (i + 1));
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation){
        return tile.getLinkedTilesAs(this, tempTiles).sumf(other -> other.floor().attributes.get(attri)) > 0.01f;
    }

    public class ThermalHeaterBuild extends HeatGeneratorBuild{
        /** 脚下地板的热属性总和 */
        public float sum;

        @Override
        public void updatePost(){
            generateHeat(sum + attri.env());
        }

        @Override
        public void draw(){
            Draw.rect(regions[rotation], x, y);
            UnityDrawf.drawHeat(heatRegion, x, y, rotdeg(), heat().getTemp());

            drawTeamTop();
        }

        @Override
        public void onProximityAdded(){
            super.onProximityAdded();

            sum = sumAttribute(attri, tileX(), tileY());
        }
    }
}
