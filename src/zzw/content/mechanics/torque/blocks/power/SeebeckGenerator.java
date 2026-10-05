package zzw.content.mechanics.torque.blocks.power;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.EnumSet;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.Stat;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.*;
import zzw.content.mechanics.torque.modules.GraphHeatModule;

import static arc.Core.*;

/**
 * 塞贝克发电机 (PU160 unity.world.blocks.power.SeebeckGenerator 移植)
 * <p>
 * 热电发电机: 利用两侧热量网络的温差发电 (塞贝克效应)。
 * 输出功率正比于 "本体温度与相邻热节点温度差的加权和":
 * {@code heatdiff = Σ |nodeTemp - neighbourTemp| * neighbourConductivity}。
 * <p>
 * 移植说明: 原版用旧框架的 2 端口 FixedGraphConnector 让方块两侧保持为
 * 两个独立热网; 本项目热量模块为单连接 (两侧会并成同一张热网),
 * 但温差公式只依赖 "自身温度" 与 "直接相邻热节点温度", 因此结果一致,
 * 这里直接遍历 {@code heat().eachNeighbour()} 取相邻温度。
 * 参数取自 参考/PU160反编译/.../YoungchaBlocks.java L540-551。
 */
public class SeebeckGenerator extends GraphBlock{
    /** 最大输出功率 (每 tick) */
    public float maxPower = 30f;
    /** 塞贝克强度 (每单位温差的发电量) */
    public float seebeckStrength = 5f;

    final TextureRegion[] rotations = new TextureRegion[4];
    TextureRegion heatLeft, heatRight, heatCenter;

    public SeebeckGenerator(String name){
        super(name);

        rotate = true;
        solid = true;
        // ★ 对齐原版: flags = EnumSet.of(BlockFlag.generator); outputsPower=true; consumesPower=false
        flags = EnumSet.of(BlockFlag.generator);
        outputsPower = true;
        consumesPower = false;
        // ★ 关键: 原版注册处显式设置了 hasPower=true; 不设置时 v160 不会把本方块
        //   纳入电力网 (power graph), 导致 getPowerProduction() 永远不被读取 → 不发电。
        hasPower = true;
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < 4; i++) rotations[i] = atlas.find(name + "-" + (i + 1));
        heatLeft = atlas.find(name + "-heat-left");
        heatRight = atlas.find(name + "-heat-right");
        heatCenter = atlas.find(name + "-heat-center");
    }

    @Override
    public void setStats(){
        super.setStats();

        // ★ 信息面板写清楚发电量: 最大发电量 (每 tick * 60 = 每秒) 与 每单位温差发电量
        stats.add(Stat.basePowerGeneration, "@", bundle.format("stat.unity.maxpoweroutput", maxPower * 60f));
        stats.add(Stat.basePowerGeneration, bundle.get("stat.unity-seebeckStrength"), seebeckStrength * 60f);
    }

    @Override
    public void setBars(){
        super.setBars();

        // ★ 对齐原版: 电力条显示当前发电量 / 最大值
        addBar("power", (SeebeckGeneratorBuild entity) -> new Bar(
            () -> bundle.format("bar.poweroutput", Strings.fixed(entity.getPowerProduction() * 60f * entity.timeScale(), 1)),
            () -> Pal.powerBar,
            () -> entity.getPowerProduction() / maxPower
        ));
    }

    public class SeebeckGeneratorBuild extends GraphBuild{
        float leftheat, rightheat;
        float heatdiff;
        float powergen;

        @Override
        public float getPowerProduction(){
            return Mathf.clamp(powergen, 0f, maxPower);
        }

        @Override
        public void updateTile(){
            super.updateTile();

            float my = heat().getTemp();
            leftheat = my;
            rightheat = my;

            // 遍历直接相邻的热节点, 累加温差贡献; 同时按端口序号区分左右 (仅用于贴图)
            float[] diff = {0f};
            heat().eachNeighbour(e -> {
                GraphHeatModule n = e.key;
                float nt = n.getTemp();
                diff[0] += Math.abs(my - nt) * n.graph.baseHeatConductivity;

                // ★ 对齐原版: 原版按端口序号 (ordinal) 区分左右 ——
                //   accept 中第 1 个非零口 (端口索引 1) = ordinal 0 → rightheat;
                //   第 2 个非零口 (端口索引 7) = ordinal 1 → leftheat。
                //   e.value = 本方块连接该邻居所用的端口索引, 会随方块旋转自动对应正确的一侧。
                if(e.value == 1){
                    rightheat = nt;
                }else{
                    leftheat = nt;
                }
            });
            heatdiff = diff[0];

            powergen += (seebeckStrength * heatdiff - powergen) * Mathf.clamp(0.1f * Time.delta);
        }

        @Override
        public void draw(){
            Draw.rect(rotations[rotation], x, y);
            UnityDrawf.drawHeat(heatLeft, x, y, rotdeg(), leftheat);
            UnityDrawf.drawHeat(heatRight, x, y, rotdeg(), rightheat);
            UnityDrawf.drawHeat(heatCenter, x, y, rotdeg(), heat().getTemp());

            drawTeamTop();
        }
    }
}
