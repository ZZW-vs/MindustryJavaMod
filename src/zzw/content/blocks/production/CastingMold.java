package zzw.content.blocks.production;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.OrderedSet;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.type.Item;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;
import zzw.content.mechanics.torque.meta.CrucibleData;
import zzw.content.mechanics.torque.meta.GraphData;
import zzw.content.mechanics.torque.meta.MeltInfo;
import zzw.content.mechanics.torque.modules.GraphCrucibleModule;

import static mindustry.Vars.iconMed;

/**
 * 铸模 (PU132 unity.world.blocks.production.CastingMold 移植)
 *
 * <p>从坩埚网络抽出熔融物 (按 MeltInfo.priority 优先级), 浇注 (pourProgress)
 * 后冷却凝固 (castProgress 与温度负相关), 产出原物品。
 * 温度过高时无法冷却 ("Too hot to cast!")。</p>
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
        /** 当前铸造的熔融物 */
        MeltInfo castingMelt;

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

                if(castingMelt != null){
                    sub.image(castingMelt.item.uiIcon).size(iconMed);
                    sub.label(() -> {
                        // ★ 汉化: 过热提示走 bundle (stat.unity.casting.toohot)
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

            // 选料: 取可抽出量 > 1 且优先级最高的熔融物
            if(castingMelt == null){
                pourProgress = 0f;
                castProgress = 0f;

                Seq<CrucibleData> cc = dex.getContained();
                MeltInfo[] melts = MeltInfo.all;

                if(cc.isEmpty()) return;

                CrucibleData hpMelt = null;
                MeltInfo hpMeltType = null;

                for(var i : cc){
                    MeltInfo meltType = melts[i.id];
                    if(i.meltedRatio * i.volume > 1f && (hpMelt == null || meltType.priority > hpMeltType.priority) && meltType.item != null){
                        hpMelt = i;
                        hpMeltType = meltType;
                    }
                }
                if(hpMelt != null){
                    dex.getNetwork().addLiquidToSlot(hpMelt, -1f);
                    castingMelt = hpMeltType;
                }
            }else{
                // 浇注 → 冷却 → 产出
                if(pourProgress < 1f){
                    pourProgress += edelta() * 0.05f;
                    if(pourProgress > 1f) pourProgress = 1f;
                }else if(castProgress < 1f){
                    // 冷却速度: 温度超过 75K + 熔点后归零 (Too hot to cast)
                    castSpeed = Math.max(0f, (1f - (heat().getTemp() - 75f) / castingMelt.meltPoint) * castingMelt.meltSpeed * 1.5f);
                    castProgress += castSpeed;

                    if(castProgress > 1f) castProgress = 1f;
                }else{
                    items.add(castingMelt.item, 1);
                    castingMelt = null;
                }
            }
        }

        @Override
        public void draw(){
            Draw.rect(baseRegions[rotation], x, y);
            if(castingMelt != null){
                if(pourProgress > 0f){
                    Draw.color(castingMelt.item.color, 1f - Math.abs(pourProgress - 0.5f) * 2f);
                    Draw.rect(liquidRegion, x, y, rotdeg());

                    Draw.color();
                    Draw.rect(castingMelt.item.fullIcon, x, y, pourProgress * 8f, pourProgress * 8f);
                }
                if(castProgress < 1f && pourProgress > 0f){
                    UnityDrawf.drawHeat(castingMelt.item.fullIcon, x, y, 0f, Mathf.map(castProgress, 0f, 1f, castingMelt.meltPoint, 275f));
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
            castingMelt = MeltInfo.all[read.i()];
            pourProgress = read.f();
            castProgress = read.f();
        }
    }
}
