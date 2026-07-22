package zzw.content.mechanics.torque.blocks.power;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.entities.units.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.liquid.*;
import zzw.content.mechanics.torque.blocks.*;
import zzw.content.mechanics.torque.graphs.*;
import zzw.content.mechanics.torque.meta.*;
import zzw.content.mechanics.torque.modules.*;

import static arc.Core.*;

public class WaterTurbine extends ArmoredConduit implements GraphBlockBase{
    protected final Graphs graphs = new Graphs();

    public final TextureRegion[] topRegions = new TextureRegion[4], bottomRegions = new TextureRegion[2], liquidRegions = new TextureRegion[2];
    public TextureRegion rotorRegion;

    public WaterTurbine(String name){
        super(name);

        solid = true;
        noUpdateDisabled = false;
    }

    @Override
    public void load(){
        super.load();

        rotorRegion = atlas.find(name + "-rotor");

        for(int i = 0; i < 4; i++) topRegions[i] = atlas.find(name + "-top" + (i + 1));
        for(int i = 0; i < 2; i++){
            bottomRegions[i] = atlas.find(name + "-bottom" + (i + 1));
            liquidRegions[i] = atlas.find(name + "-liquid" + (i + 1));
        }
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

    @Override
    public void drawPlanRegion(BuildPlan req, Eachable<BuildPlan> list){
        Draw.alpha(0.5f);
        Draw.rect(region, req.drawx(), req.drawy(), req.rotation * 90f);
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region};
    }

    public class WaterTurbineBuild extends ArmoredConduitBuild implements GraphBuildBase{
        protected GraphModules gms;
        float flowRate;

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

        //
        @Override
        public void updatePre(){
            float flow = flowRate * 40f;
            smoothLiquid = Mathf.lerpDelta(smoothLiquid, liquids.currentAmount() / liquidCapacity, 0.05f);
            if(liquids.currentAmount() > 0.001f && timer(timerFlow, 1f)) flowRate = moveLiquidForward(leaks, liquids.current());
            float mul = flow / 100f;
            if(mul < 0.4f) mul = 0f;
            if(mul > 1f) mul = 0.5f * Mathf.log2(mul) + 1f;
            torque().setMotorForceMult(mul);
        }

        @Override
        public void draw(){
            float rot = torque().getRotation();
            Draw.rect(bottomRegions[rotation % 2], x, y);
            if(liquids.currentAmount() > 0.001f) Drawf.liquid(liquidRegions[rotation % 2], x, y, liquids.currentAmount() / liquidCapacity, liquids.current().color);
            Drawf.shadow(rotorRegion, x - size / 2f, y - size / 2f, rot);
            Draw.rect(rotorRegion, x, y, rot);
            Draw.rect(topRegions[rotation], x, y);
            drawTeamTop();
        }

        @Override
        public float moveLiquidForward(boolean leaks, Liquid liquid){
            Point2 rPos = GraphData.getConnectSidePos(1, 3, rotation).toPos;
            Tile next = tile.nearby(rPos);
            if(next == null) return 0f;
            if(next.build != null) return moveLiquid(next.build, liquid);
            return 0f;
        }
    }
}
