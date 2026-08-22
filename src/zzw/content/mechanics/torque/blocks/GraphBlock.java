package zzw.content.mechanics.torque.blocks;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.world.*;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.graphs.*;
import zzw.content.mechanics.torque.meta.*;
import zzw.content.mechanics.torque.modules.*;

/**
 * 图结构方块 - 扭矩系统的核心方块，管理扭矩网络
 * 继承 Block 实现 GraphBlockBase，作为扭矩系统的物理节点
 */
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
        setStatsExt(stats);
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
        // 改为在 updateTile() 开头乘以 gms.efficiency() 模拟 v159 惰性求值:
        // v159 中 super.updateTile() 调用 efficiency() 方法 = super.efficiency() * gms.efficiency()(stale)
        // 此处在 super.updateTile() 之前乘 (使用上一帧的 gms 状态), 匹配原行为
        @Override
        public void updateTile(){
            efficiency *= gms.efficiency();
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
        public void draw(){
            // PU132 原版 GraphBlock.draw(): preserveDraw 走原版绘制;
            // 否则带热量图的方块画本体 + 热色叠加 (small-radiator / 热源等)
            if(preserveDraw){
                super.draw();
            }else if(graphs.hasGraph(GraphType.heat)){
                Draw.rect(region, x, y);
                if(heat() != null){
                    UnityDrawf.drawHeat(heatRegion, x, y, 0, heat().getTemp());
                }
                drawTeamTop();
            }
        }
    }
}
