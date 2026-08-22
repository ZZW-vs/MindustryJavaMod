package zzw.content.blocks.production;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.ui.Styles;
import zzw.content.graphics.UnityDrawf;
import zzw.content.graphics.UnityPal;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.meta.CrucibleData;
import zzw.content.mechanics.torque.meta.MeltInfo;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;
import zzw.content.mechanics.torque.graph.CrucibleGraph;
import zzw.content.mechanics.torque.ui.dialogs.CrucibleDialog;
import zzw.content.util.GraphicUtils;
import zzw.content.util.SVec2;

import static arc.Core.atlas;

/**
 * 坩埚熔炉 (PU132 unity.world.blocks.production.Crucible 移植)
 *
 * <p>接收可熔物品 (MeltInfo 注册过的), 在热量图中加热熔化, 多种熔融物按
 * CrucibleRecipe 合成合金。点击可打开内容物图表 / 切换开盖视角。</p>
 *
 * <p>贴图: 底座/栅栏/液体/固体条均为 12x4 切片 (邻居连接变体),
 * 屋顶模式 (非视角模式) 显示 roofRegions。</p>
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
            // v155.4: button(Drawable, TextButtonStyle, float, Runnable) 不存在,
            // 改用 button(Drawable, ImageButtonStyle, Runnable) + size 指定尺寸
            table.button(Icon.chartBar, Styles.clearNonei, new CrucibleDialog(this)::show).size(50f);
            table.button(Icon.eye, Styles.clearNonei, () -> configure(0)).size(50f);
        }

        @Override
        public void configured(Unit builder, Object value){
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
            return crucible().canContainMore(1f) && MeltInfo.map.containsKey(item);
        }

        @Override
        public void handleItem(Building source, Item item){
            crucible().addItem(item);
        }

        /**
         * 绘制坩埚内容物: 熔融部分按体积加权混合颜色铺满液体贴图;
         * 未熔部分画固体物品贴图 (数量多时叠加固体条)。
         */
        protected void drawContents(GraphCrucibleModule crucGraph, int tIndex){
            color.set(0f, 0f, 0f);
            Seq<CrucibleData> cc = crucGraph.getContained();

            if(cc.isEmpty()) return;

            float tLiquid = 0f;
            float fraction = crucGraph.liquidCap / crucGraph.getTotalLiquidCapacity();

            for(var i : cc){
                if(i.meltedRatio > 0f){
                    float liquidVol = i.meltedRatio * i.volume;
                    tLiquid += liquidVol;
                    Color itemCol = UnityPal.youngchaGray;

                    if(i.item != null) itemCol = i.item.color;

                    color.r += itemCol.r * liquidVol;
                    color.g += itemCol.g * liquidVol;
                    color.b += itemCol.b * liquidVol;
                }
            }

            if(tLiquid > 0f){
                float invt = 1f / tLiquid;

                Draw.color(color.mul(invt), Mathf.clamp(tLiquid * fraction * 2f));
                Draw.rect(liquidRegions[tIndex], x, y, 8f, 8f);
            }

            for(var i : cc){
                if(i.meltedRatio < 1f && i.volume * fraction > 0.1f){
                    Color itemCol = UnityPal.youngchaGray;

                    if(i.item != null) itemCol = i.item.color;

                    float ddd = (1f - i.meltedRatio) * i.volume * fraction;

                    if(ddd > 0.1f){
                        Draw.color(itemCol);
                        if(ddd > 1f) Draw.rect(solidItemStrips[Mathf.floor(ddd) - 1], x, y);

                        float siz = 8f * (ddd % 1f);
                        long pos = randomPos[Math.max(Mathf.floor(ddd), 5)];

                        Draw.rect(solidItem, SVec2.x(pos) + x, SVec2.y(pos) + y, siz, siz);
                    }
                }
            }

            Draw.color();
        }
    }
}
