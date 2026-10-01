package zzw.content.blocks.production;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.OrderedMap;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.type.Item;
import mindustry.ui.Styles;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.graph.CrucibleGraph;
import zzw.content.mechanics.torque.graph.CrucibleGraph.CrucibleFluid;
import zzw.content.mechanics.torque.meta.CrucibleRecipes;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleIngredient;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;
import zzw.content.util.GraphicUtils;
import zzw.content.util.SVec2;

import static arc.Core.atlas;

/**
 * 坩埚熔炉 (PU_V8 unity.world.blocks.production.CrucibleBlock 移植)
 *
 * <p>接收可熔物品 (在 {@link CrucibleRecipes} 注册过的), 在热量图中加热熔化,
 * 多种熔融物按 {@link CrucibleRecipes.CrucibleRecipe} 合成合金。
 * 点击眼睛按钮可切换开盖视角 (查看内容物)。</p>
 *
 * <p>内容物以"固/液两态"表示, 见 {@link CrucibleGraph}。</p>
 */
public class Crucible extends GraphBlock{
    /** 当前查看视角的网络 (null = 正常屋顶显示) */
    CrucibleGraph viewPos;

    /** 固体物品随机摆放位置表 (打包坐标) */
    private static final long[] randomPos = new long[]{
        SVec2.construct(0f, 0f),
        SVec2.construct(-1.6f, 1.6f),
        SVec2.construct(-1.6f, -1.6f),
        SVec2.construct(1.6f, -1.6f),
        SVec2.construct(-1.6f, -1.6f),
        SVec2.construct(0f, 0f)
    };

    /** 液体 / 底座 / 屋顶 / 固体条 / 热量贴图 (12x4 切片) */
    public TextureRegion[] liquidRegions, baseRegions, roofRegions, solidItemStrips, heatRegions;
    /** 笼底 / 固体物品贴图 */
    public TextureRegion floorRegion, solidItem;

    public Crucible(String name){
        super(name);

        configurable = solid = true;
    }

    @Override
    public void load(){
        super.load();

        liquidRegions = GraphicUtils.getRegions(liquidRegion, 12, 4);
        baseRegions = GraphicUtils.getRegions(atlas.find(name + "-base"), 12, 4);
        floorRegion = atlas.find(name + "-floor");
        roofRegions = GraphicUtils.getRegions(atlas.find(name + "-roof"), 12, 4);

        solidItem = atlas.find(name + "-solid");
        solidItemStrips = GraphicUtils.getRegions(atlas.find(name + "-solidstrip"), 6, 1);
        heatRegions = GraphicUtils.getRegions(heatRegion, 12, 4);
    }

    public class CrucibleBuild extends GraphBuild{
        /** 内容物混合颜色缓存 */
        final Color color = Color.clear.cpy();

        @Override
        public void buildConfiguration(Table table){
            table.button(Icon.eye, Styles.clearNonei, () -> configure(0)).size(50f);
        }

        @Override
        public void configured(mindustry.gen.Unit builder, Object value){
            CrucibleGraph thisG = crucible().getNetwork();
            viewPos = viewPos == thisG ? null : thisG;
        }

        @Override
        public void drawConfigure(){}

        @Override
        public void draw(){
            GraphCrucibleModule dex = crucible();
            byte tileIndex = UnityDrawf.tileMap[dex.tilingIndex];

            if(viewPos == dex.getNetwork()){
                Draw.rect(floorRegion, x, y, 8f, 8f);
                drawContents(dex, tileIndex);

                Draw.rect(baseRegions[tileIndex], x, y, 8f, 8f, 4f, 4f, 0f);
                UnityDrawf.drawHeat(heatRegions[tileIndex], x, y, 0f, heat().getTemp());
            }else{
                Draw.rect(roofRegions[tileIndex], x, y, 8f, 8f, 4f, 4f, 0f);
            }

            drawTeamTop();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return crucible().canContainMore(1f) && CrucibleRecipes.items.containsKey(item);
        }

        @Override
        public void handleItem(Building source, Item item){
            crucible().addItem(item);
        }

        /**
         * 绘制坩埚内容物: 熔融部分按熔融量加权混合颜色铺液体贴图;
         * 未熔部分画固体物品贴图 (数量多时叠加固体条)。
         */
        protected void drawContents(GraphCrucibleModule crucGraph, int tIndex){
            OrderedMap<CrucibleIngredient, CrucibleFluid> cc = crucGraph.getContained();
            if(cc.isEmpty()) return;

            float fraction = crucGraph.getTotalLiquidCapacity() > 0f
                ? crucGraph.liquidCap / crucGraph.getTotalLiquidCapacity() : 0f;

            // 熔融液
            color.set(0f, 0f, 0f);
            float tLiquid = 0f;
            for(var f : cc){
                float lv = f.value.melted;
                tLiquid += lv;
                color.r += f.key.color.r * lv;
                color.g += f.key.color.g * lv;
                color.b += f.key.color.b * lv;
            }
            if(tLiquid > 0f){
                float invt = 1f / tLiquid;
                Draw.color(color.mul(invt), Mathf.clamp(tLiquid * fraction * 2f));
                Draw.rect(liquidRegions[tIndex], x, y, 8f, 8f);
            }

            // 未熔固体
            for(var f : cc){
                float ddd = f.value.solid * fraction;
                if(ddd <= 0.1f) continue;

                Draw.color(f.key.color);
                if(ddd > 1f){
                    int stripIndex = Mathf.clamp(Mathf.floor(ddd) - 1, 0, solidItemStrips.length - 1);
                    Draw.rect(solidItemStrips[stripIndex], x, y, 8f, 8f);
                }

                float siz = 8f * (ddd % 1f);
                long pos = randomPos[Mathf.clamp(Mathf.floor(ddd), 0, randomPos.length - 1)];

                Draw.rect(solidItem, SVec2.x(pos) + x, SVec2.y(pos) + y, siz, siz);
            }

            Draw.color();
        }
    }
}
