package zzw.content.blocks.production;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.OrderedMap;
import arc.struct.OrderedSet;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.type.Item;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;
import zzw.content.mechanics.torque.graph.CrucibleGraph;
import zzw.content.mechanics.torque.graph.CrucibleGraph.CrucibleFluid;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleIngredient;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleItem;
import zzw.content.mechanics.torque.meta.GraphData;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;

import static mindustry.Vars.iconMed;

/**
 * 铸模 (PU_V8 CrucibleCaster 思路移植到项目框架)
 *
 * <p>从坩埚网络抽出熔融物 (取第一种熔融量足够的物品原料), 浇注 (pourProgress)
 * 后冷却凝固 (castProgress 与温度负相关), 产出原物品。
 * 温度过高时无法冷却 ("温度过高，无法铸造！")。</p>
 */
public class CastingMold extends GraphBlock{
    /** 4 方向底座/顶盖贴图 */
    final TextureRegion[] baseRegions = new TextureRegion[4], topRegions = new TextureRegion[4];

    public CastingMold(String name){
        super(name);

        rotate = solid = hasItems = true;
        itemCapacity = 1;
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < 4; i++){
            baseRegions[i] = Core.atlas.find(name + "-base" + (i + 1));
            topRegions[i] = Core.atlas.find(name + "-top" + (i + 1));
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
            Draw.rect(baseRegions[rotation], x, y);
            if(outputItem != null){
                if(pourProgress > 0f){
                    Draw.color(outputItem.color, 1f - Math.abs(pourProgress - 0.5f) * 2f);
                    Draw.rect(liquidRegion, x, y, rotdeg());

                    Draw.color();
                    Draw.rect(outputItem.fullIcon, x, y, pourProgress * 8f, pourProgress * 8f);
                }
                if(castProgress < 1f && pourProgress > 0f){
                    UnityDrawf.drawHeat(outputItem.fullIcon, x, y, 0f, Mathf.map(castProgress, 0f, 1f, castingMelt == null ? 1073f : castingMelt.meltingpoint, 275f));
                }
            }

            Draw.rect(topRegions[rotation], x, y);
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
            castingMelt = id < 0 ? null : (CrucibleItem)CrucibleRecipesGet(id);
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
