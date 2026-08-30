package zzw.content.mechanics.torque.blocks.power;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.blocks.power.PowerGenerator;
import zzw.content.mechanics.torque.blocks.GraphBlockBase;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;
import zzw.content.mechanics.torque.graphs.Graphs;
import zzw.content.mechanics.torque.graph.TorqueGraph;
import zzw.content.mechanics.torque.modules.GraphFluxModule;
import zzw.content.mechanics.torque.modules.GraphModules;
import zzw.content.mechanics.torque.modules.GraphTorqueModule;

/**
 * 电力转子 (PU132 unity.world.blocks.power.RotorBlock 完整移植)
 *
 * <p>磁力系统的做功端: 消费电力启动, 在磁通网络中感应出扭矩
 * (force = 磁通 * 基础扭矩 * (效率-转速比) * delta), 扭矩驱动传动轴;
 * 反过来传动轴转速决定发电效率 (productionEfficiency = 转速比*盈亏平衡)。</p>
 *
 * <p>物理关系:
 * <ul>
 *   <li>topSpeed = baseTopSpeed / (1 + 磁通/fluxEfficiency) —— 磁通越强限速越低</li>
 *   <li>rotNeg = clamp(网络转速/topSpeed, 0, 2/盈亏平衡) —— 转速越接近限速反电动势越强</li>
 *   <li>发电效率 = rotNeg * 盈亏平衡 * rotPowerEfficiency (上限 2)</li>
 * </ul></p>
 */
public class RotorBlock extends PowerGenerator implements GraphBlockBase{
    protected final Graphs graphs = new Graphs();
    protected float baseTopSpeed = 20f, baseTorque = 5f, torqueEfficiency = 1f, fluxEfficiency = 1f, rotPowerEfficiency = 1f;
    /** 大型(3x3)与小型(1x1)两种贴图方案 */
    protected boolean big;

    public final TextureRegion[] topRegions = new TextureRegion[4];
    public TextureRegion overlayRegion, rotorRegion, bottomRegion, topRegion, overRegion, spinRegion;

    public RotorBlock(String name){
        super(name);

        rotate = consumesPower = outputsPower = true;
    }

    @Override
    public void load(){
        super.load();

        if(big){
            for(int i = 0; i < 4; i++) topRegions[i] = Core.atlas.find(name + "-top" + (i + 1));

            overlayRegion = Core.atlas.find(name + "-overlay");
            rotorRegion = Core.atlas.find(name + "-rotor");
            bottomRegion = Core.atlas.find(name + "-bottom");
        }else{
            topRegion = Core.atlas.find(name + "-top");
            overRegion = Core.atlas.find(name + "-over");
            spinRegion = Core.atlas.find(name + "-spin");
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

    public class RotorBuild extends GeneratorBuild implements GraphBlockBase.GraphBuildBase{
        protected GraphModules gms;
        float topSpeed;

        @Override
        public void created(){
            gms = new GraphModules(this);
            graphs.injectGraphConnector(gms);
            gms.created();
        }

        @Override
        public void onRemoved(){
            gms.updateGraphRemovals();
            onDelete();

            super.onRemoved();
            onDeletePost();
        }

        @Override
        public void updateTile(){
            // 适配 v155.4: efficiency 字段化, 在 super 前乘图效率 (同 GraphBuild 方案)
            efficiency *= gms.efficiency();
            if(graphs.useOriginalUpdate()) super.updateTile();

            updatePre();
            gms.updateTile();

            updatePost();
            gms.prevTileRotation(rotation);
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
        public void displayBarsExt(Table table){
            GraphTorqueModule<?> tGraph = torque();
            float mTorque = flux().getNetwork().flux() * torqueEfficiency * baseTorque;

            // 适配 v155.4: consumes.getPower().usage → consPower.usage
            float usage = consPower != null ? consPower.usage : 0f;
            table.add(new Bar(
                () -> arc.Core.bundle.format("bar.poweroutput", Strings.fixed((getPowerProduction() - usage) * 60f * timeScale, 1)),
                () -> Pal.powerBar,
                () -> productionEfficiency
            )).growX().row();
            table.add(new Bar(
                () -> arc.Core.bundle.get("stat.unity.torque", "Torque") + ": " + Strings.fixed(tGraph.force, 1) + "/" + Strings.fixed(mTorque, 1),
                () -> Pal.darkishGray,
                () -> tGraph.force / Math.max(mTorque, 0.001f)
            )).growX().row();
            table.add(new Bar(
                () -> arc.Core.bundle.get("stat.unity.maxspeed", "Max Speed") + ":" + Strings.fixed(topSpeed / 6f, 1) + "r/s",
                () -> Pal.darkishGray,
                () -> topSpeed / baseTopSpeed
            )).growX().row();
        }

        @Override
        public void updatePre(){
            float flux = flux().getNetwork().flux();
            topSpeed = baseTopSpeed / (1f + flux / fluxEfficiency);
            float usage = consPower != null ? consPower.usage : 0f;
            float breakEven = usage / powerProduction;

            GraphTorqueModule<?> tGraph = torque();
            TorqueGraph<?> net = tGraph.getNetwork();
            float netVel = net != null ? net.lastVelocity : 0f;
            float rotNeg = Mathf.clamp(netVel / topSpeed, 0f, 2f / Math.max(breakEven, 0.0001f));

            productionEfficiency = Mathf.clamp(rotNeg * breakEven, 0f, 2f);
            productionEfficiency *= rotPowerEfficiency;

            // 感应扭矩: 磁通 * 基础扭矩 * (效率 - 转速比) * delta (PU132 原版公式)
            tGraph.force = flux * baseTorque * (efficiency - rotNeg) * delta();
        }

        @Override
        public void draw(){
            float fixedRot = (rotdeg() + 90f) % 180f - 90f;
            GraphTorqueModule<?> tGraph = torque();
            float graphRot = tGraph.getRotation();
            float shaftRot = (rotation + 1) % 4 >= 2 ? 360f - graphRot : graphRot;

            if(big){
                Draw.rect(bottomRegion, x, y, fixedRot);

                zzw.content.mechanics.torque.UnityDrawf.drawRotRect(rotorRegion, x, y, 24f, 15f, 24f, rotdeg(), shaftRot, shaftRot + 90f);
                zzw.content.mechanics.torque.UnityDrawf.drawRotRect(rotorRegion, x, y, 24f, 15f, 24f, rotdeg(), shaftRot + 120f, shaftRot + 210f);
                zzw.content.mechanics.torque.UnityDrawf.drawRotRect(rotorRegion, x, y, 24f, 15f, 24f, rotdeg(), shaftRot + 240f, shaftRot + 330f);

                Draw.rect(overlayRegion, x, y, fixedRot);
                Draw.rect(topRegions[rotation], x, y);
            }else{
                zzw.content.mechanics.torque.UnityDrawf.drawRotRect(spinRegion, x, y, 8f, 3.5f, 8f, rotdeg(), shaftRot, shaftRot + 180f);
                zzw.content.mechanics.torque.UnityDrawf.drawRotRect(spinRegion, x, y, 8f, 3.5f, 8f, rotdeg(), shaftRot + 180f, shaftRot + 360f);

                Draw.rect(overRegion, x, y, fixedRot);
                Draw.rect(topRegion, x, y, fixedRot);
            }

            drawTeamTop();
        }

        protected GraphFluxModule flux(){
            return gms().flux();
        }
    }
}