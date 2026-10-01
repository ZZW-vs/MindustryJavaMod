package zzw.content.blocks.production;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Vec2;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.type.Item;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;
import zzw.content.mechanics.torque.graph.CrucibleGraph;
import zzw.content.mechanics.torque.graph.CrucibleGraph.CrucibleFluid;
import zzw.content.mechanics.torque.meta.CrucibleRecipes;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleIngredient;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;

import static arc.Core.atlas;

/**
 * 坩埚熔炉 (PU_V8 unity.world.blocks.production.CrucibleBlock 移植)
 *
 * <p>3x3 方块。接收可熔物品 (在 {@link CrucibleRecipes} 注册过的), 在热量图中加热熔化,
 * 多种熔融物按 {@link CrucibleRecipes.CrucibleRecipe} 合成合金。</p>
 *
 * <p>绘制方式忠实还原 PU_V8: 地板 → 熔融液 → 未熔固体碎块 → 四方向底座 (锥口) → 热量叠加。
 * 四方向底座根据相邻坩埚是否连通选择"闭合"或"开口"贴图。</p>
 */
public class Crucible extends GraphBlock{
    /** 地板 / 熔融液 / 热量 / 固体碎块贴图 */
    TextureRegion floor, liquid, heat, chunks;
    /** 闭合底座贴图 [0]=南北向 [1]=东西向 */
    TextureRegion[] base = new TextureRegion[2];
    /** 开口底座贴图 (与连通方向接通) */
    TextureRegion[] baseopen = new TextureRegion[2];
    /** 固体碎块在方块内的随机散布位置 */
    Vec2[] pos;

    public Crucible(String name){
        super(name);

        solid = true;
    }

    @Override
    public void load(){
        super.load();

        floor = atlas.find(name + "-floor");
        heat = atlas.find(name + "-heat");
        liquid = atlas.find(name + "-liquid");
        chunks = atlas.find(name + "-solid");

        base[0] = atlas.find(name + "-base1");
        base[1] = atlas.find(name + "-base2");
        baseopen[0] = atlas.find(name + "-base-open1");
        baseopen[1] = atlas.find(name + "-base-open2");

        // PU_V8: 30 个随机碎块位置, 散布范围与方块尺寸相关
        pos = new Vec2[30];
        for(int i = 0; i < pos.length; i++){
            pos[i] = new Vec2(Mathf.range(size * 8f * 0.5f * 0.25f), Mathf.range(size * 8f * 0.5f * 0.25f));
        }
    }

    public class CrucibleBuild extends GraphBuild{
        /** 四个正交方向的相邻坩埚连通状态 (东/北/西/南), 决定底座是否开口 */
        final boolean[] connection = new boolean[4];

        /**
         * 计算四方向相邻坩埚是否连通 (等价 PU_V8 CrucibleBlock.onConnectionChanged)。
         *
         * <p>★ 修复"自动开口方向奇怪": 原实现用 {{size,size/2},{size/2,size},{-1,size/2},{size/2,-1}}
         * 这类不对称偏移。对 3x3 而言, 西侧 (-1,1) 与南侧 (1,-1) 的取样点落在方块自身范围内,
         * nearby() 会返回自己, 于是被判定为"已连通", 导致西/南底座永远显示开口贴图。
         * 现按 PU_V8 写法: 从中心格沿 d4 方向偏移 size/2+1 格 (3x3 即 2 格, 正好是方块边外一格)。
         * Mindustry 的 {@link Geometry#d4} 顺序为 [东,北,西,南], 与绘制循环的 connection 下标一致。</p>
         */
        void updateConnections(){
            int off = size / 2 + 1;
            for(int i = 0; i < 4; i++){
                Building b = nearby(Geometry.d4x(i) * off, Geometry.d4y(i) * off);
                connection[i] = b != this && b instanceof GraphBuildBase g && g.crucible() != null;
            }
        }

        @Override
        public void draw(){
            GraphCrucibleModule dex = crucible();
            if(dex == null) return;

            updateConnections();

            Draw.rect(floor, x, y);

            // 熔融液: 使用网络加权颜色铺满, 叠加一层慢速呼吸高光
            CrucibleGraph net = dex.getNetwork();
            Color liquidColor = net == null ? Color.clear : net.color;
            if(liquidColor.a > 0.01f){
                Draw.color(liquidColor);
                Draw.rect(liquid, x, y);
                Draw.alpha(Mathf.absin(Time.time * 0.3f, 1f, 1f) * 0.35f);
                Draw.rect(liquid, x, y);
                Draw.color();
            }

            // 未熔固体碎块 (按各原料 solid 量比例分配位置)
            float total = 0f;
            for(var f : dex.getContained()) total += f.value.solid;
            if(total > 0f){
                int am = (int)Math.min(pos.length, total);
                int idx = 0;

                for(var f : dex.getContained()){
                    float pieces = am * f.value.solid / total;
                    Draw.color(f.key.color);
                    for(int a = 0; a < (int)pieces; a++){
                        if(idx >= pos.length) break;
                        Draw.rect(chunks, x + pos[idx].x, y + pos[idx].y, Vars.itemSize, Vars.itemSize);
                        idx++;
                    }
                }
                Draw.color();
            }

            // 四方向底座 (锥口): 连通侧画开口贴图, 否则闭合
            for(int i = 0; i < 4; i++){
                Draw.rect(connection[i] ? baseopen[i == 2 || i == 3 ? 0 : 1] : base[i == 2 || i == 3 ? 0 : 1],
                    x, y, 180f + i * 90f);
            }

            if(heat() != null) UnityDrawf.drawHeat(heat, x, y, 0f, heat().getTemp());

            drawTeamTop();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return crucible() != null && crucible().canContainMore(1f) && CrucibleRecipes.items.containsKey(item);
        }

        @Override
        public void handleItem(Building source, Item item){
            crucible().addItem(item);
        }
    }
}
