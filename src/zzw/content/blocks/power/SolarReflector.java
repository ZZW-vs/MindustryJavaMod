package zzw.content.blocks.power;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import zzw.content.blocks.power.SolarCollector.SolarCollectorBuild;

import static arc.Core.atlas;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * 太阳反射镜 (PU132 unity.world.blocks.power.SolarReflector 移植)
 *
 * <p>手动配置链接到太阳能集热器, 镜面缓慢转向集热器方向,
 * 为其贡献热功率 (见 SolarCollector.getThermalPowerCoeff)。</p>
 */
public class SolarReflector extends Block{
    /** 镜面 / 底座贴图 */
    public TextureRegion mirrorRegion, baseRegion;

    public SolarReflector(String name){
        super(name);

        solid = update = configurable = true;
        config(Point2.class, (SolarReflectorBuild build, Point2 point) -> build.setLink(Point2.pack(point.x + build.tileX(), point.y + build.tileY())));
        config(Integer.class, (SolarReflectorBuild build, Integer point) -> build.setLink(point));
    }

    @Override
    public void load(){
        super.load();

        mirrorRegion = atlas.find(name + "-mirror");
        baseRegion = atlas.find(name + "-base");
    }

    public class SolarReflectorBuild extends Building{
        /** 镜面当前角度 (向集热器平滑转向) */
        float mirrorRot;
        /** 链接的集热器 tile 坐标 (-1 = 未链接) */
        int link = -1;
        /** 链接变更后待通知集热器 */
        boolean hasChanged;

        public void setLink(int s){
            if(s == link) return;
            if(link != -1){
                Building build = world.build(link);
                if(build instanceof SolarCollectorBuild b) b.removeReflector(this);
            }

            if(s != -1) hasChanged = true;
            link = s;
        }

        @Override
        public void updateTile(){
            mirrorRot += 0.4f;
            Building build = world.build(link);

            if(linkValid()){
                setLink(build.pos());
                mirrorRot = Mathf.slerpDelta(mirrorRot, tile.angleTo(build.tile), 0.05f);

                if(hasChanged){
                    ((SolarCollectorBuild)build).appendSolarReflector(this);
                    hasChanged = false;
                }
            }
        }

        @Override
        public void draw(){
            Draw.rect(baseRegion, x, y);
            Drawf.shadow(mirrorRegion, x - size / 2f, y - size / 2f, mirrorRot);
            Draw.rect(mirrorRegion, x, y, mirrorRot);
        }

        @Override
        public void drawConfigure(){
            float sin = Mathf.absin(6f, 1f);

            if(linkValid()){
                Building target = world.build(link);
                Drawf.circles(target.x, target.y, (target.block.size / 2f + 1f) * tilesize + sin - 2f, Pal.place);
                Drawf.arrow(x, y, target.x, target.y, size * tilesize + sin, 4f + sin);
            }

            Drawf.dashCircle(x, y, 100f, Pal.accent);
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            // v155.4: onConfigureTileTapped 改名 onConfigureBuildTapped
            if(this == other){
                configure(-1);
                return false;
            }
            if(link == other.pos()){
                configure(-1);
                return false;
            }else if(other instanceof SolarCollectorBuild && other.dst(tile) <= 100f && other.team == team){
                configure(other.pos());
                return false;
            }

            return true;
        }

        @Override
        public Point2 config(){
            return Point2.unpack(link).sub(tileX(), tileY());
        }

        boolean linkValid(){
            if(link == -1) return false;
            Building build = world.build(link);

            if(build instanceof SolarCollectorBuild) return build.team == team && within(build, 100f);

            return false;
        }

        @Override
        public void write(Writes write){
            super.write(write);

            write.i(link);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            setLink(read.i());
        }

        @Override
        public void onRemoved(){
            Building build = world.build(link);
            if(build instanceof SolarCollectorBuild b) b.removeReflector(this);
        }
    }
}
