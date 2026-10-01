package zzw.content.mechanics.torque.blocks.production;

import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.io.*;
import mindustry.graphics.*;
import mindustry.world.blocks.production.*;
import zzw.content.mechanics.torque.blocks.*;
import zzw.content.mechanics.torque.graphs.*;
import zzw.content.mechanics.torque.modules.*;

import static arc.Core.*;

/**
 * 螺旋石油钻井 (OilDerrick)
 *
 * <p>扭矩驱动的石油合成器: 本质是 {@link GenericCrafter} + 图网络管线,
 * 消耗"水 + 沙子"合成石油, 整体工作速度由扭矩转速决定 (效率 = 转速效率的平方)。</p>
 *
 * <p>注册时通过 {@code outputLiquid = oil; consumeLiquid(water); consumeItem(sand);} 指定配方,
 * 与普通工厂一致: 水按 tick 连续抽走、沙子按次消耗、石油按进度连续产出,
 * 三者都随 {@code efficiency} 同步缩放, 因此停机(缺料/无扭矩/油满)时不会空耗。</p>
 *
 * <p>绘制上做成"螺旋钻杆"样式: 底座 + 随扭矩旋转的螺旋钻杆 + 顶部井架 + 石油液位。</p>
 */
public class OilDerrick extends GenericCrafter implements GraphBlockBase{
    protected final Graphs graphs = new Graphs();

    /** 底座 (3x3) */
    public TextureRegion bottomRegion;
    /** 随扭矩旋转的螺旋钻杆 */
    public TextureRegion rotorRegion;
    /** 顶部井架 (不旋转) */
    public TextureRegion topRegion;
    /** 石油液位覆盖 */
    public TextureRegion liquidRegion;

    public OilDerrick(String name){
        super(name);

        rotate = true;
    }

    @Override
    public void load(){
        super.load();

        bottomRegion = fallback(name + "-bottom");
        rotorRegion = fallback(name + "-rotor");
        topRegion = fallback(name + "-top");
        liquidRegion = fallback(name + "-liquid");
    }

    /** 贴图缺失时回退到 error 贴图, 避免绘制/图标因 null 崩溃 (贴图补齐后自动使用真实贴图) */
    protected TextureRegion fallback(String regionName){
        TextureRegion r = atlas.find(regionName);
        return r == null ? atlas.find("error") : r;
    }

    @Override
    public void setStats(){
        super.setStats();

        graphs.setStats(stats);
        setStatsExt(stats);
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{atlas.find(name)};
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        graphs.drawPlace(x, y, size, rotation, valid);

        super.drawPlace(x, y, rotation, valid);
    }

    @Override
    public Graphs graphs(){
        return graphs;
    }

    public class OilDerrickBuild extends GenericCrafterBuild implements GraphBuildBase{
        protected GraphModules gms;

        @Override
        public void created(){
            gms = new GraphModules(this);
            graphs.injectGraphConnector(gms);
            gms.created();
        }

        // v155.4: efficiency 是字段而非方法, 不能用 @Override 重写方法
        // 改为在 updateTile() 开头乘以 gms.efficiency() 模拟 v159 惰性求值
        @Override
        public void updateTile(){
            efficiency *= gms.efficiency() * gms.efficiency();
            if(graphs.useOriginalUpdate()) super.updateTile();

            updatePre();
            gms.updateTile();
            updatePost();
            gms.prevTileRotation(rotation);
        }

        @Override
        public void onRemoved(){
            gms.updateGraphRemovals();
            onDelete();

            super.onRemoved();
            onDeletePost();
        }

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();

            gms.onProximityUpdate();
            proxUpdate();
        }

        @Override
        public void display(Table table){
            super.display(table);

            gms.display(table);
            displayExt(table);
        }

        @Override
        public void displayBars(Table table){
            super.displayBars(table);

            gms.displayBars(table);
            displayBarsExt(table);
        }

        @Override
        public void write(Writes write){
            super.write(write);

            gms.write(write);
            writeExt(write);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            gms.read(read, revision);
            readExt(read, revision);
        }

        @Override
        public GraphModules gms(){
            return gms;
        }

        @Override
        public void drawSelect(){
            super.drawSelect();

            gms.drawSelect();
        }

        @Override
        public void updatePre(){
            warmup = Math.min(1f, warmup);
        }

        @Override
        public void draw(){
            // 扭矩网络的累计转角 (决定钻杆旋转)
            float rot = torque().getRotation();

            Draw.rect(bottomRegion, x, y, rotdeg());

            // 石油液位
            if(liquids.currentAmount() > 0.001f){
                Drawf.liquid(liquidRegion, x, y, liquids.currentAmount() / liquidCapacity, liquids.current().color);
            }

            // 螺旋钻杆: 随扭矩旋转
            Draw.rect(rotorRegion, x, y, rot);

            // 顶部井架: 随放置朝向
            Draw.rect(topRegion, x, y, rotdeg());

            drawTeamTop();
        }
    }
}