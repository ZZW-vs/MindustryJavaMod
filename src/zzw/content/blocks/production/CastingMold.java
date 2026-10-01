package zzw.content.blocks.production;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.OrderedMap;
import arc.struct.OrderedSet;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.type.Item;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;
import zzw.content.mechanics.torque.graph.CrucibleGraph;
import zzw.content.mechanics.torque.graph.CrucibleGraph.CrucibleFluid;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleIngredient;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleItem;
import zzw.content.mechanics.torque.meta.GraphData;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;

import static mindustry.Vars.iconMed;
import static mindustry.Vars.itemSize;

/**
 * 铸模 (PU_V8 unity.world.blocks.production.CrucibleCaster 移植)
 *
 * <p>3x3 方块。从坩埚网络抽出熔融物 (取第一种熔融量足够的物品原料), 浇注 (pourProgress)
 * 后冷却凝固 (castProgress 与温度负相关), 产出原物品。温度过高时无法冷却 ("温度过高，无法铸造！")。</p>
 *
 * <p>绘制忠实还原 PU_V8 贴图布局: 地板 → 铸盘 (含四角铸件与熔液) → 四方向底座 → 熔融网络色液体 → 队伍标。</p>
 */
public class CastingMold extends GraphBlock{
    /** 地板 / 铸盘 / 铸盘侧面 / 浇注熔液贴图 */
    TextureRegion floor, platter, platterside, castliquid;
    /** 4 方向底座贴图 */
    final TextureRegion[] base = new TextureRegion[4];
    /** 铸件在铸盘上的四角位置 */
    final Vec2[] itemPos = {
        new Vec2(0.4f * 8f, 0.4f * 8f),
        new Vec2(-0.4f * 8f, 0.4f * 8f),
        new Vec2(-0.4f * 8f, -0.4f * 8f),
        new Vec2(0.4f * 8f, -0.4f * 8f)
    };

    public CastingMold(String name){
        super(name);

        rotate = solid = hasItems = true;
        itemCapacity = 1;
    }

    @Override
    public void load(){
        super.load();

        floor = Core.atlas.find(name + "-floor");
        platter = Core.atlas.find(name + "-platter");
        platterside = Core.atlas.find(name + "-platterside");
        castliquid = Core.atlas.find(name + "-cast-liquid");

        for(int i = 0; i < 4; i++){
            base[i] = Core.atlas.find(name + "-base" + (i + 1));
        }
    }

    public class CastingMoldBuild extends GraphBuild{
        /** 输出目标建筑缓存 (非坩埚的 8 邻居) */
        final OrderedSet<Building> outputBuildings = new OrderedSet<>(8);
        /** 当前铸造的原料 (物品类) */
        CrucibleItem castingMelt;
        /** 铸造产物 */
        Item outputItem;

        /** 浇注进度 / 冷却进度 / 冷却速度 */
        float pourProgress, castProgress, castSpeed;

        @Override
        public void proxUpdate(){
            updateOutput();
        }

        @Override
        public void onRotationChanged(){
            updateOutput();
        }

        @Override
        public void displayExt(Table table){
            table.row();
            table.table(sub -> {
                sub.clearChildren();
                sub.left();

                if(outputItem != null){
                    sub.image(outputItem.uiIcon).size(iconMed);
                    sub.label(() -> {
                        if(pourProgress == 1f && castSpeed == 0f) return Core.bundle.get("stat.unity.casting.toohot", "温度过高，无法铸造！");

                        return Strings.fixed((pourProgress + castProgress) * 50f, 2) + "%";
                    }).color(Color.lightGray);
                }else{
                    sub.labelWrap(Core.bundle.get("stat.unity.casting.nothing", "暂无铸造物")).color(Color.lightGray);
                }
            }).left();
        }

        /** 重算输出目标: 8 邻居中排除坩埚类方块 (避免倒灌) */
        void updateOutput(){
            outputBuildings.clear();

            for(int i = 0; i < 8; i++){
                GraphData pos = gms.getConnectSidePos(i);
                Building b = nearby(pos.toPos.x, pos.toPos.y);

                if(b != null){
                    if(b instanceof GraphBuildBase g && g.crucible() != null) continue;
                    outputBuildings.add(b);
                }
            }
        }

        @Override
        public void updatePost(){
            // 有物品时只负责输出
            if(items.total() > 0){
                pourProgress = 0f;
                castProgress = 0f;

                if(timer(timerDump, dumpTime)){
                    Item itemPass = items.first();

                    for(var i : outputBuildings){
                        if(i.team == team && i.acceptItem(this, itemPass)){
                            i.handleItem(this, itemPass);
                            items.remove(itemPass, 1);

                            return;
                        }
                    }
                }
                return;
            }
            GraphCrucibleModule dex = crucible();
            if(dex == null || dex.getNetwork() == null) return;

            // 选料: 取第一种熔融量 > 1 的物品原料
            if(castingMelt == null){
                pourProgress = 0f;
                castProgress = 0f;

                OrderedMap<CrucibleIngredient, CrucibleFluid> cc = dex.getContained();
                if(cc.isEmpty()) return;

                for(var f : cc){
                    if(f.key instanceof CrucibleItem ci && f.value.melted > 1f){
                        castingMelt = ci;
                        outputItem = ci.item;
                        f.value.melted -= 1f;
                        break;
                    }
                }
            }else if(castingMelt != null){
                // 浇注 → 冷却 → 产出
                if(pourProgress < 1f){
                    pourProgress += edelta() * 0.05f;
                    if(pourProgress > 1f) pourProgress = 1f;
                }else if(castProgress < 1f){
                    // 冷却速度: 温度超过 75K + 熔点后归零
                    castSpeed = Math.max(0f, (1f - (heat().getTemp() - 75f) / castingMelt.meltingpoint) * castingMelt.meltspeed * 1.5f);
                    castProgress += castSpeed;

                    if(castProgress > 1f) castProgress = 1f;
                }else{
                    items.add(outputItem, 1);
                    castingMelt = null;
                    outputItem = null;
                }
            }
        }

        @Override
        public void draw(){
            Draw.rect(floor, x, y);

            float prog = Mathf.curve(pourProgress, 0f, 1f);
            if(outputItem != null && pourProgress > 0f){
                // 铸盘 + 浇注熔液 + 四角逐渐成形的铸件
                Draw.rect(platter, x, y);

                Draw.color(outputItem.color, prog);
                Draw.rect(castliquid, x, y);
                Draw.color();

                float siz = itemSize * prog;
                for(int i = 0; i < itemPos.length; i++){
                    Draw.rect(outputItem.fullIcon, x + itemPos[i].x, y + itemPos[i].y, siz, siz);
                }
            }else{
                Draw.rect(platter, x, y);
            }

            Draw.rect(base[rotation], x, y);

            // 熔融网络色铺底
            GraphCrucibleModule dex = crucible();
            if(dex != null && dex.getNetwork() != null){
                Draw.color(dex.getNetwork().color);
                Draw.rect(liquidRegion, x, y, rotdeg());
                Draw.color();
            }

            drawTeamTop();
        }

        @Override
        public void writeExt(Writes write){
            write.i(castingMelt != null ? castingMelt.id : -1);
            write.f(pourProgress);
            write.f(castProgress);
        }

        @Override
        public void readExt(Reads read, byte revision){
            int id = read.i();
            // ★ 用 instanceof 判定而非强制转换: 配方表改动后同一 id 可能已指向非物品原料,
            // 直接 (CrucibleItem) 转换会抛 ClassCastException。
            CrucibleIngredient ing = id < 0 ? null : CrucibleRecipesGet(id);
            castingMelt = ing instanceof CrucibleItem ci ? ci : null;
            if(castingMelt != null) outputItem = castingMelt.item;
            pourProgress = read.f();
            castProgress = read.f();
        }
    }

    /** 通过 id 取回原料 (封装 null 处理) */
    static CrucibleIngredient CrucibleRecipesGet(int id){
        return zzw.content.mechanics.torque.meta.CrucibleRecipes.ingredients.get(id);
    }
}
