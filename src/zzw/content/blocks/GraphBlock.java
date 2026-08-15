package zzw.content.blocks;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.world.Block;
import zzw.content.graphics.UnityDrawf;
import zzw.content.graphs.GraphBuildBase;
import zzw.content.graphs.GraphBlockBase;
import zzw.content.graphs.Graphs;
import zzw.content.graphs.GraphType;
import zzw.content.modules.GraphModules;

/** 图方块基类 (PU132 unity.world.blocks.GraphBlock 移植, 适配 v155.4) */
public class GraphBlock extends Block implements GraphBlockBase{
    protected final Graphs graphs = new Graphs();
    protected boolean preserveDraw;
    protected TextureRegion heatRegion, liquidRegion;

    public GraphBlock(String name){
        super(name);
        update = true;
    }

    @Override
    public void load(){
        super.load();
        if(graphs.hasGraph(GraphType.crucible)) liquidRegion = Core.atlas.find(name + "-liquid");
        if(graphs.hasGraph(GraphType.heat)) heatRegion = Core.atlas.find(name + "-heat");
    }

    @Override
    public void setStats(){
        super.setStats();
        graphs.setStats(stats);
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

    public class GraphBuild extends Building implements GraphBuildBase{
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
            if(gms != null) efficiency *= gms.efficiency();
            if(graphs.useOriginalUpdate()) super.updateTile();
            if(gms != null){
                gms.updateTile();
                gms.prevTileRotation(rotation);
            }
        }

        @Override
        public void onRemoved(){
            if(gms != null) gms.updateGraphRemovals();
            super.onRemoved();
        }

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();
            if(gms != null) gms.onProximityUpdate();
        }

        @Override
        public void display(Table table){
            super.display(table);
            if(gms != null) gms.display(table);
        }

        @Override
        public void displayBars(Table table){
            super.displayBars(table);
            if(gms != null) gms.displayBars(table);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            if(gms != null) gms.write(write);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(gms != null) gms.read(read, revision);
        }

        @Override
        public GraphModules gms(){
            return gms;
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            if(gms != null) gms.drawSelect();
        }

        @Override
        public void draw(){
            if(preserveDraw){
                super.draw();
            }else if(graphs.hasGraph(GraphType.heat)){
                Draw.rect(block.region, x, y);
                if(heatRegion != null) UnityDrawf.drawHeat(heatRegion, x, y, 0f, heat().getTemp());
                drawTeamTop();
            }
        }
    }
}
