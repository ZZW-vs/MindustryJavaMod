package zzw.content.blocks.production;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Icon;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.meta.StatUnit;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.graph.CrucibleGraph;
import zzw.content.mechanics.torque.graph.CrucibleGraph.CrucibleFluid;
import zzw.content.mechanics.torque.meta.CrucibleRecipes;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleIngredient;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;

import static mindustry.Vars.iconMed;

/**
 * 坩埚泵 (PU_V8 unity.world.blocks.production.CruciblePump 移植)
 *
 * <p>把背面网络 (set1) 中指定原料的熔融液泵送到正面网络 (set0)。
 * 可配置目标原料 (物品或液体)。</p>
 *
 * <p>泵送由扭矩驱动 (PU_V8): 效率 eff = curve(lastVelocity, 0, 50) * 0.2,
 * 单帧泵送量 ∝ 源网络该原料的熔融存量 (eff * melted + 0.001), 受目标网络剩余空间限制。</p>
 */
public class CruciblePump extends GraphBlock{
    /** 4 方向顶盖贴图 */
    public final TextureRegion[] topRegions = new TextureRegion[4];
    /** 底座 / 地板贴图 */
    public TextureRegion bottomRegion, floorRegion;

    public CruciblePump(String name){
        super(name);

        rotate = solid = configurable = true;
        config(Item.class, (CruciblePumpBuild build, Item item) -> build.config = CrucibleRecipes.items.get(item));
        config(Liquid.class, (CruciblePumpBuild build, Liquid liquid) -> build.config = CrucibleRecipes.liquids.get(liquid));
        config(Integer.class, (CruciblePumpBuild build, Integer id) -> build.config = CrucibleRecipes.ingredients.get(id));
        configClear((CruciblePumpBuild build) -> build.config = null);
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < 4; i++) topRegions[i] = Core.atlas.find(name + "-top" + (i + 1));
        bottomRegion = Core.atlas.find(name + "-bottom");
        floorRegion = Core.atlas.find(name + "-floor");
    }

    public class CruciblePumpBuild extends GraphBuild{
        /** 泵送的原料 (null = 不泵送) */
        CrucibleIngredient config;
        /** 最近泵送量 (动画/显示用, 每帧减半) */
        float flowRate, flowAnimation;

        @Override
        public void buildConfiguration(Table table){
            table.labelWrap("[lightgray]" + Core.bundle.get("stat.unity.crucible.pumpfilter", "Pump Ingredient")).growX().pad(5f).center().row();
            table.table(t -> {
                int i = 0;
                for(var ing : CrucibleRecipes.ingredients.values()){
                    t.button(new TextureRegionDrawable(ing.icon()), Styles.clearNonei, () -> configure(ing.id)).size(40f).pad(2f);
                    if(++i % 8 == 0) t.row();
                }
            }).grow().pad(4f).row();
            table.button(Icon.cancel, Styles.clearNonei, () -> configure(-1)).size(40f).padTop(4f);
        }

        @Override
        public void displayExt(Table table){
            String ps = " " + StatUnit.perSecond.localized();
            table.row();
            table.table(sub -> {
                sub.clearChildren();
                sub.left();
                if(config != null){
                    sub.image(config.icon()).size(iconMed);
                    sub.label(() -> Strings.fixed(flowRate * 10f, 2) + "units" + ps).color(Color.lightGray);
                }else{
                    sub.labelWrap(Core.bundle.get("stat.unity.crucible.nofilter", "No filter selected")).color(Color.lightGray);
                }
            }).left();
        }

        @Override
        public void updatePost(){
            GraphCrucibleModule dex = crucible();
            flowRate /= 2f;

            // PU_V8: 由扭矩转速决定泵送效率 (非电力)
            var tGraph = torque();
            float eff = (tGraph == null || tGraph.getNetwork() == null) ? 0f
                : Mathf.curve(tGraph.getNetwork().lastVelocity, 0f, 50f) * 0.2f;

            if(config != null && dex != null){
                CrucibleGraph fromNet = dex.getNetworkFromSet(1);
                CrucibleGraph toNet = dex.getNetworkFromSet(0);

                if(fromNet != null && toNet != null){
                    CrucibleFluid f = fromNet.fluids.get(config);
                    if(f != null && f.melted > 0f){
                        // PU_V8 公式: 单帧移出量 ∝ 源存量 (+0.001 保证低速也缓慢输送),
                        // 受源存量与目标网络剩余空间双重限制
                        float remove = Mathf.clamp(eff > 0f ? eff * f.melted + 0.001f : 0f, 0f,
                            Math.min(f.melted, toNet.getRemainingSpace()));
                        if(remove > 0f){
                            f.melted -= remove;
                            toNet.addLiquidIngredient(config, remove);
                            flowRate = remove;
                        }
                    }
                }
            }
            flowAnimation += flowRate * 0.4f;
        }

        @Override
        public void draw(){
            Draw.rect(bottomRegion, x, y);
            if(config != null){
                Draw.color(config.color, Mathf.clamp(flowRate * 60f));
                UnityDrawf.drawSlideRect(liquidRegion, x, y, 16f, 16f, 32f, 16f, rotdeg() + 180f, 16, flowAnimation);

                Draw.color();
            }

            if(heat() != null) UnityDrawf.drawHeat(heatRegion, x, y, rotdeg(), heat().getTemp());
            Draw.rect(topRegions[rotation], x, y);

            drawTeamTop();
        }

        @Override
        public void writeExt(Writes write){
            write.s(config == null ? -1 : config.id);
        }

        @Override
        public void readExt(Reads read, byte revision){
            config = CrucibleRecipes.ingredients.get(read.s());
        }

        @Override
        public Integer config(){
            return config == null ? -1 : config.id;
        }
    }
}
