package zzw.content.mechanics.torque.blocks.power;

import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.consumers.*;
import zzw.content.graphics.UnityDrawf;
import zzw.content.mechanics.torque.blocks.*;

import static arc.Core.*;

/**
 * 散热器 (PU160 unity.world.blocks.power.HeatRadiator 移植)
 * <p>
 * 热量网络的耗散端: 把所在网络的热量辐射到环境中 (由 GraphHeat 的
 * radiativity 决定散热速率), 高温时会冒出蒸汽粒子。
 * <p>
 * <b>冷却液机制 (本项目新增)</b>
 * <ul>
 *   <li>不接冷却液: 保持原版行为 —— 向环境 (20℃) 普通散热。</li>
 *   <li>通入<b>水</b>: 消耗 8 单位/秒, 散热更快, 最低可降到 5℃。</li>
 *   <li>通入<b>冷冻液</b>: 消耗 8 单位/秒, 散热更快, 最低可降到 -100℃。</li>
 * </ul>
 * 实现方式: 把本模块热辐射的"环境温度"临时改成冷却液对应的温度池, 并乘以散热倍率,
 * 让所在热网向该温度收敛 (见 {@code GraphHeatModule.ambientTemp / radiativityMul})。
 * <p>
 * 移植说明: 原版有 2 个朝向变体贴图 (name1/name2), 本项目 small-radiator
 * 只有单张 {@code small-radiator.png}, 因此找不到变体时回退到本体贴图。
 * 参数取自 参考/PU160反编译/.../YoungchaBlocks.java L553-564。
 */
public class HeatRadiator extends GraphBlock{
    /** 朝向变体贴图 (最多 2 种) */
    final TextureRegion[] rotateregions = new TextureRegion[2];

    // ===== 冷却液机制参数 (可在 Z_Torque 中按需覆盖) =====
    /** 通入水时的最低温度 (K) = 5℃ */
    public float waterMinTemp = 278.15f;
    /** 通入冷冻液时的最低温度 (K) = -100℃ */
    public float cryoMinTemp = 173.15f;
    /** 通入水时的散热倍率 (相对 GraphHeat 的基础 radiativity) */
    public float waterRadiativityMul = 4f;
    /** 通入冷冻液时的散热倍率 */
    public float cryoRadiativityMul = 10f;

    /** 冷却液消耗器 (水或冷冻液, 取存量较多的一种); 8 单位/秒 = 8/60 每 tick */
    public ConsumeLiquidFilter coolant;

    public HeatRadiator(String name){
        super(name);

        rotate = true;
        solid = true;

        // 通入水 / 冷冻液时主动冷却 (消耗 8 单位/秒)
        hasLiquids = true;
        liquidCapacity = 30f;
        coolant = consume(new ConsumeLiquidFilter(l -> l == Liquids.water || l == Liquids.cryofluid, 8f / 60f));
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < 2; i++){
            String variant = name + (i + 1);
            rotateregions[i] = atlas.has(variant) ? atlas.find(variant) : region;
        }
    }

    public class HeatRadiatorBuild extends GraphBuild{
        @Override
        public boolean shouldConsume(){
            // 仅在通有冷却液、且温度高于该冷却液的最低温度时才消耗
            var h = heat();
            if(h == null || coolant == null) return false;
            Liquid liq = coolant.getConsumed(this);
            return super.shouldConsume() && liq != null
                && h.getTemp() > (liq == Liquids.cryofluid ? cryoMinTemp : waterMinTemp);
        }

        @Override
        public void updatePost(){
            var h = heat();
            if(h == null) return;

            Liquid liq = (coolant == null) ? null : coolant.getConsumed(this);
            if(liq == null){
                // 无冷却液: 恢复原版散热 (环境 20℃, 基础散热率)
                h.ambientTemp = 293.15f;
                h.radiativityMul = 1f;
            }else{
                boolean cryo = liq == Liquids.cryofluid;
                float floor = cryo ? cryoMinTemp : waterMinTemp;

                // 把本模块的热辐射指向冷却液的温度池, 并提高散热倍率;
                // 环境温度不高于当前温度, 保证"只降温、不反向加热"
                h.ambientTemp = Math.min(floor, h.getTemp());
                h.radiativityMul = cryo ? cryoRadiativityMul : waterRadiativityMul;

                if(canConsume()){
                    consume();
                }
            }

            // 温度越高, 冒蒸汽越频繁 (273K→0, 2073K→1%)
            float likely = Mathf.map(h.getTemp(), 273.15f, 2073.15f, 0f, 0.01f);
            if(Mathf.random() < likely){
                Fx.steam.at(x + Mathf.range(8), y + Mathf.range(8));
            }
        }

        @Override
        public void draw(){
            Draw.rect(rotateregions[rotation % 2], x, y);
            if(heatRegion != null){
                // ★ 对齐原版: 热色叠加随方块朝向旋转 (原版传 rotdeg())
                UnityDrawf.drawHeat(heatRegion, x, y, rotdeg(), heat().getTemp());
            }

            drawTeamTop();
        }
    }
}
