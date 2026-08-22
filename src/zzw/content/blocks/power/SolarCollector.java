package zzw.content.blocks.power;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.OrderedSet;
import arc.struct.Seq;
import mindustry.graphics.Layer;
import zzw.content.graphics.UnityDrawf;
import zzw.content.blocks.power.SolarReflector.SolarReflectorBuild;

import static arc.Core.atlas;

/**
 * 太阳能集热器 (PU132 unity.world.blocks.power.SolarCollector 移植)
 *
 * <p>朝向的锥形范围内每面反射镜按对准程度贡献热量
 * (方向点积 × 1.5 截断), 需配合 {@link SolarReflector} 使用。</p>
 */
public class SolarCollector extends HeatGenerator{
    /** 4 方向贴图 + 光锥贴图 */
    public final TextureRegion[] regions = new TextureRegion[4];
    public TextureRegion lightRegion;

    public SolarCollector(String name){
        super(name);

        rotate = solid = true;
    }

    @Override
    public void load(){
        super.load();

        lightRegion = atlas.find(name + "-light");
        for(int i = 0; i < 4; i++) regions[i] = atlas.find(name + (i + 1));
    }

    public class SolarCollectorBuild extends HeatGeneratorBuild{
        /** 链接的反射镜集合 */
        final OrderedSet<SolarReflectorBuild> linkedReflect = new OrderedSet<>(8);
        /** 当前热功率系数 (所有反射镜贡献之和) */
        float thermalPwr;

        /** 单面反射镜的热功率贡献: 朝向方向与反射镜方向夹角的余弦 × 1.5 */
        float getThermalPowerCoeff(SolarReflectorBuild ref){
            float dst = Mathf.dst(ref.x, ref.y, x, y);

            Point2 dir = Geometry.d4(rotation);

            return Mathf.clamp((dir.x * (ref.x - x) / dst + dir.y * (ref.y - y) / dst) * 1.5f);
        }

        void recalcThermalPwr(){
            thermalPwr = 0f;

            if(linkedReflect.isEmpty()) return;
            for(var i : linkedReflect) thermalPwr += getThermalPowerCoeff(i);
        }

        public void appendSolarReflector(SolarReflectorBuild ref){
            linkedReflect.add(ref);
            recalcThermalPwr();
        }

        public void removeReflector(SolarReflectorBuild ref){
            if(linkedReflect.remove(ref)) recalcThermalPwr();
        }

        @Override
        public void onDelete(){
            Seq<SolarReflectorBuild> items = linkedReflect.orderedItems();

            while(!items.isEmpty()) items.first().setLink(-1);
        }

        @Override
        public void updatePost(){
            generateHeat(thermalPwr, thermalPwr);
        }

        @Override
        public void draw(){
            Draw.rect(regions[rotation], x, y);
            UnityDrawf.drawHeat(heatRegion, x, y, rotdeg(), heat().getTemp());

            if(thermalPwr > 0f){
                Draw.z(Layer.effect);
                Draw.color(thermalPwr, thermalPwr, thermalPwr);
                Draw.rect(lightRegion, x, y, rotdeg());
                Draw.z();
            }

            drawTeamTop();
        }
    }
}
