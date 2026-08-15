package zzw.content.blocks.production;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.world.blocks.production.GenericCrafter;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.GraphBlockBase;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;
import zzw.content.mechanics.torque.graphs.GraphHeat;
import zzw.content.mechanics.torque.graphs.Graphs;
import zzw.content.mechanics.torque.modules.GraphHeatModule;
import zzw.content.mechanics.torque.modules.GraphModules;

import static arc.Core.*;

/**
 * 孢子热解器 (PU132 unity.world.blocks.production.SporePyrolyser 移植)
 * <p>继承 GenericCrafter, 通过热量图系统驱动生产效率。
 * 进度增量基于温度: sqrt(clamp((temp - 370K) / 300K)),
 * 温度低于 370K 时无法生产, 高于 670K 时满效率。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>PU132 实现 GraphBlockBase 接口 → 本项目同样实现 zzw.content.mechanics.torque.blocks.GraphBlockBase (完整版),
 *       在构造函数中注册 GraphHeat 连接器, 使 heat() 可用</li>
 *   <li>unity.graphics.UnityDrawf.drawHeat → zzw.content.graphics.UnityDrawf.drawHeat</li>
 *   <li>GraphBuildBase.heat() 返回 GraphHeatModule, 调用 getTemp() 获取温度 (K)</li>
 *   <li>v155.4: efficiency 是字段, 在 updateTile() 中乘以 gms.efficiency()</li>
 * </ul></p>
 */
public class SporePyrolyser extends GenericCrafter implements GraphBlockBase{
    /** 图容器 (管理热量等图连接器) */
    protected final Graphs graphs = new Graphs();
    /** 热力叠加贴图 */
    protected TextureRegion heatRegion;

    public SporePyrolyser(String name){
        super(name);
        // 注册热量图连接器, 使 build.heat() 返回非 null
        graphs.setGraphConnectorTypes(new GraphHeat());
    }

    @Override
    public void load(){
        super.load();
        heatRegion = atlas.find(name + "-heat");
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

    public class SporePyrolyserBuild extends GenericCrafterBuild implements GraphBuildBase{
        /** 图模块容器 */
        protected GraphModules gms;

        @Override
        public void created(){
            gms = new GraphModules(this);
            graphs.injectGraphConnector(gms);
            gms.created();
        }

        @Override
        public void updateTile(){
            // v155.4: efficiency 是字段, 在 super.updateTile() 前乘以图效率
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
        public void drawSelect(){
            super.drawSelect();
            if(gms != null) gms.drawSelect();
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
        public float getProgressIncrease(float baseTime){
            // 基于温度的进度增量: 温度低于 370K 不生产, 高于 670K 满效率
            if(gms == null) return 0f;
            GraphHeatModule heat = heat();
            if(heat == null) return 0f;
            return Mathf.sqrt(Mathf.clamp((heat.getTemp() - 370f) / 300f)) / baseTime * edelta();
        }

        @Override
        public void draw(){
            // region 引用外部类 SporePyrolyser 的 block.region 字段
            Draw.rect(region, x, y);
            // 热力叠加 (温度 ×1.5 放大显示)
            GraphHeatModule heat = gms != null ? heat() : null;
            if(heat != null){
                UnityDrawf.drawHeat(heatRegion, x, y, 0f, heat.getTemp() * 1.5f);
            }
            drawTeamTop();
        }
    }
}
