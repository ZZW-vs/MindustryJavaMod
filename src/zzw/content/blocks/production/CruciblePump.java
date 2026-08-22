package zzw.content.blocks.production;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.meta.StatUnit;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.graph.CrucibleGraph;
import zzw.content.mechanics.torque.meta.CrucibleData;
import zzw.content.mechanics.torque.meta.MeltInfo;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;

import static mindustry.Vars.content;
import static mindustry.Vars.iconMed;

/**
 * 坩埚泵 (PU132 unity.world.blocks.distribution.CruciblePump 移植)
 *
 * <p>从背面网络 (set1) 向正面网络 (set0) 泵送指定物品的熔融液,
 * 泵送目标按填充模式 (满/50%/25%) 截止。可配置过滤物品和填充模式。</p>
 */
public class CruciblePump extends GraphBlock{
    /** 填充模式对应的目标容量比例 */
    public static final float[] fillAm = new float[]{1f, 0.5f, 0.25f};
    /** 4 方向顶盖贴图 */
    public final TextureRegion[] topRegions = new TextureRegion[4];
    /** 底座贴图 */
    public TextureRegion bottomRegion;

    public CruciblePump(String name){
        super(name);

        rotate = solid = configurable = true;
        config(Item.class, (CruciblePumpBuild build, Item item) -> build.filterItem = item);
        config(Integer.class, (CruciblePumpBuild build, Integer value) -> {
            build.pumpMode = value & 3;
            if(value > 2) build.filterItem = content.item(value >>> 2);
        });
        configClear((CruciblePumpBuild build) -> build.filterItem = null);
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < 4; i++) topRegions[i] = Core.atlas.find(name + "-top" + (i + 1));
        bottomRegion = Core.atlas.find(name + "-bottom");
    }

    public class CruciblePumpBuild extends GraphBuild{
        /** 泵送过滤物品 (null = 不泵送) */
        Item filterItem;
        /** 最近泵送量 (动画/显示用, 每帧减半) */
        float flowRate, flowAnimation;
        /** 填充模式 (0=满, 1=50%, 2=25%) */
        int pumpMode;

        @Override
        public void buildConfiguration(Table table){
            // v155.4: Styles.clearPartialt 不存在, 用 cleart (透明文本按钮样式)
            table.labelWrap("Fill until:").growX().pad(5f).center().row();
            table.table(bt -> {
                bt.button("Full", Styles.cleart, () -> configure(0)).left().size(50f).disabled(b -> pumpMode == 0);
                bt.button("50%", Styles.cleart, () -> configure(1)).left().size(50f).disabled(b -> pumpMode == 1);
                bt.button("25%", Styles.cleart, () -> configure(2)).left().size(50f).disabled(b -> pumpMode == 2);
            }).row();

            table.labelWrap("Pump:").growX().pad(5f).center().row();
            ItemSelection.buildTable(table, content.items(), () -> filterItem, this::configure);
            table.setBackground(Styles.black5);
        }

        @Override
        public void displayExt(Table table){
            String ps = " " + StatUnit.perSecond.localized();
            table.row();
            table.table(sub -> {
                sub.clearChildren();
                sub.left();
                if(filterItem != null){
                    sub.image(filterItem.uiIcon).size(iconMed);
                    sub.label(() -> Strings.fixed(flowRate * 10f, 2) + "units" + ps).color(Color.lightGray);
                }else{
                    sub.labelWrap("No filter selected").color(Color.lightGray);
                }
            }).left();
        }

        @Override
        public void updatePost(){
            float rate = 0.08f;
            GraphCrucibleModule dex = crucible();
            flowRate /= 2f;

            if(filterItem != null){
                CrucibleGraph fromNet = dex.getNetworkFromSet(1);
                CrucibleGraph toNet = dex.getNetworkFromSet(0);

                if(fromNet != null && toNet != null){
                    for(var fnc : fromNet.contains()){
                        if(fnc.item != filterItem) continue;

                        float transfer = Math.min(toNet.getRemainingSpace(), Math.min(rate * edelta(), fnc.volume * fnc.meltedRatio));
                        CrucibleData toG = toNet.getMeltFromID(fnc.id);

                        if(toG != null) transfer = Math.min(toNet.totalCapacity() * fillAm[pumpMode] - toG.volume, transfer);
                        if(transfer <= 0f) break;

                        fromNet.addLiquidToSlot(fnc, -transfer);
                        toNet.addMeltItem(MeltInfo.all[fnc.id], transfer, true);
                        flowRate = transfer;

                        break;
                    }
                }
            }
            flowAnimation += flowRate * 0.4f;
        }

        @Override
        public void draw(){
            Draw.rect(bottomRegion, x, y);
            if(filterItem != null){
                Draw.color(filterItem.color, Mathf.clamp(flowRate * 60f));
                UnityDrawf.drawSlideRect(liquidRegion, x, y, 16f, 16f, 32f, 16f, rotdeg() + 180f, 16, flowAnimation);

                Draw.color();
            }

            UnityDrawf.drawHeat(heatRegion, x, y, rotdeg(), heat().getTemp());
            Draw.rect(topRegions[rotation], x, y);

            drawTeamTop();
        }

        @Override
        public void writeExt(Writes write){
            write.s(filterItem == null ? -1 : filterItem.id);
            write.b(pumpMode);
        }

        @Override
        public void readExt(Reads read, byte revision){
            filterItem = content.item(read.s());
            pumpMode = read.b();
        }

        @Override
        public Integer config(){
            return pumpMode + (filterItem != null ? (filterItem.id + 1) << 2 : 0);
        }
    }
}
