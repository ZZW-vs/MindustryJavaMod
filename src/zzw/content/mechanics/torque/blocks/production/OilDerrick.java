package zzw.content.mechanics.torque.blocks.production;

import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.io.*;
import mindustry.graphics.*;
import mindustry.world.blocks.production.*;
import zzw.content.mechanics.torque.UnityDrawf;
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

    public final TextureRegion[] bottomRegions = new TextureRegion[2], topRegions = new TextureRegion[2], liquidRegions = new TextureRegion[2];
    public TextureRegion rotorRegion, mbaseRegion, wormDrive, gearRegion, rotateRegion, overlayRegion;

    public OilDerrick(String name){
        super(name);

        rotate = true;
    }

    @Override
    public void load(){
        super.load();

        rotorRegion = atlas.find(name + "-rotor");
        mbaseRegion = atlas.find(name + "-mbase");
        gearRegion = atlas.find(name + "-gear");

        overlayRegion = atlas.find(name + "-overlay");
        rotateRegion = atlas.find(name + "-moving");
        wormDrive = atlas.find(name + "-rotate");

        for(int i = 0; i < 2; i++){
            bottomRegions[i] = atlas.find(name + "-bottom" + (i + 1));
            topRegions[i] = atlas.find(name + "-top" + (i + 1));
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

        /**
         * 强制本方块即使在沙盒 / 无限资源规则下, 也必须真实消耗"水 + 沙子"才能产出石油.
         *
         * <p>{@code BuildingComp.updateConsumption()} 里有一条捷径:
         * 当 {@code !block.hasConsumers || cheating()} 成立时, 直接把 efficiency 置为 1,
         * 完全跳过 ConsumeItems / ConsumeLiquid 的检查. 沙盒(无限资源)模式下
         * {@code cheating()} 恒为 true, 于是钻井不加水、不加沙子照样出油.</p>
         *
         * <p>这里返回 false, 让效率计算走正常消耗分支: 水按 tick 连续抽走、沙子按次消耗,
         * 两者任一不足时 efficiency = 0 停止产出. 注意这<b>不影响</b>扭矩网络 ——
         * 摩擦/阻力只看 {@code enabled} (见 GraphTorqueConsumeModule.updateExtension),
         * 所以缺料时钻杆依旧转动, 只是"转而不工作".</p>
         */
        @Override
        public boolean cheating(){
            return false;
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
            // 扭矩网络的累计转角 (决定传动杆/齿轮旋转)
            float rot = torque() == null ? 0f : torque().getRotation();
            // 防呆: 扭矩图异常时可能算出 NaN/Inf, 会让 Draw.rect 的旋转顶点失效, 导致整层贴图不渲染
            if(!Float.isFinite(rot)) rot = 0f;

            float fixedRot = (rotdeg() + 90f) % 180f - 90f;

            int variant = rotation % 2;

            float deg = rotation == 0 || rotation == 3 ? rot : -rot;
            float rev = rotation == 0 || rotation == 3 ? 24 : -24;

            Point2 offset = Geometry.d4(rotation + 1);

            Draw.rect(bottomRegions[variant], x, y);

            // 石油液位
            if(liquids.currentAmount() > 0.001f){
                Drawf.liquid(liquidRegions[variant], x, y, liquids.currentAmount() / liquidCapacity, liquids.current().color);
            }

            // 底部转子
            Draw.rect(rotorRegion, x + offset.x * 4f, y + offset.y * 4f, rev, 24, -deg / 2);
            Draw.rect(rotorRegion, x - offset.x * 4f, y - offset.y * 4f, -rev, 24, deg / 2 + 90);

            // 主轴
            Draw.rect(mbaseRegion, x, y, fixedRot);

            UnityDrawf.drawRotRect(wormDrive, x, y, 24f, 3.5f, 3.5f, fixedRot, rot, rot + 180f);
            UnityDrawf.drawRotRect(wormDrive, x, y, 24f, 3.5f, 3.5f, fixedRot, rot + 180f, rot + 360f);
            UnityDrawf.drawRotRect(rotateRegion, x, y, 24f, 3.5f, 3.5f, fixedRot, rot, rot + 180f);

            Draw.rect(overlayRegion, x, y, fixedRot);

            // 齿轮
            Draw.rect(gearRegion, x + offset.x * 4f, y + offset.y * 4f, -deg / 2);
            Draw.rect(gearRegion, x - offset.x * 4f, y - offset.y * 4f, deg / 2);

            Draw.rect(topRegions[variant], x, y);
            drawTeamTop();
        }
    }
}