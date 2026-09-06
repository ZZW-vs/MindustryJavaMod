package zzw.content;

import mindustry.content.Items;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitFactory;

import static mindustry.content.Blocks.additiveReconstructor;
import static mindustry.content.Blocks.airFactory;
import static mindustry.content.Blocks.exponentialReconstructor;
import static mindustry.content.Blocks.groundFactory;
import static mindustry.content.Blocks.multiplicativeReconstructor;
import static mindustry.content.Blocks.tetrativeReconstructor;
import static zzw.content.units.Z_Units.*;
import static zzw.content.units.Z_MonolithUnits.*;

/**
 * 原版方块覆盖器 (PU132 unity.content.Overwriter 手动移植)
 *
 * <p>向原版单位工厂/重构器注入 PU 移植单位的配方, 使其可通过原版生产线生产与升级。
 * 参照 PU132 Overwriter.load() 的写入方式 (f.plans.add / r.upgrades.add)。</p>
 *
 * <p>★ 配方来源说明:
 * <ul>
 *   <li>caelifera: PU132 原版配方 (空军工厂, 硅15+钛25, 25秒)</li>
 *   <li>schistocerca / stele / discharge: PU132 原版 <b>没有</b> 直接生产配方
 *       (schistocerca 由 caelifera 在加法重构器升级而来, stele 只在波次中出现,
 *       EMP 系列未完成生产链)。此处按用户要求添加为工厂直接生产配方,
 *       材料参照 caelifera 的原版配方 (硅15+钛25, 25秒)。</li>
 *   <li>升级链: 按系列完整写入原版四个重构器 (T1→T2→T3→T4→T5),
 *       升级消耗由原版重构器自身的 ConsumeItems 决定, 与 PU132 一致。</li>
 * </ul></p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class Z_Overwriter{

    public static void load(){
        //region 单位工厂配方

        // 空军工厂: 直升机系列基础单位 + EMP 系列基础单位
        // caelifera 为 PU132 原版配方; schistocerca/discharge 参照其配方添加
        // ★ Blocks 中的字段声明为 Block 类型, 需强转为具体工厂类
        ((UnitFactory)airFactory).plans.add(
            new UnitFactory.UnitPlan(caelifera, 60f * 25f, ItemStack.with(Items.silicon, 15, Items.titanium, 25)),
            new UnitFactory.UnitPlan(discharge, 60f * 25f, ItemStack.with(Items.silicon, 15, Items.titanium, 25))
        );

        // 陆军工厂: 巨石系列基础单位 (PU132 原版无配方, 参照 caelifera 材料添加)
        ((UnitFactory)groundFactory).plans.add(
            new UnitFactory.UnitPlan(stele, 60f * 25f, ItemStack.with(Items.silicon, 15, Items.titanium, 25))
        );

        //endregion
        //region 重构器升级链

        // 加法重构器 (T1→T2): 直升机/巨石/EMP 系列第一步升级
        // PU132 原版: caelifera→schistocerca, stele→pedestal
        ((Reconstructor)additiveReconstructor).upgrades.add(
            new UnitType[]{caelifera,schistocerca},
            new UnitType[]{stele, pedestal},
            new UnitType[]{discharge, pulse}
        );

        // 乘法重构器 (T2→T3): PU132 原版: schistocerca→anthophila, pedestal→pilaster
        ((Reconstructor)multiplicativeReconstructor).upgrades.add(
            new UnitType[]{schistocerca, anthophila},
            new UnitType[]{pedestal, pilaster},
            new UnitType[]{pulse, emission}
        );

        // 指数重构器 (T3→T4): PU132 原版: anthophila→vespula, pilaster→pylon
        ((Reconstructor)exponentialReconstructor).upgrades.add(
            new UnitType[]{anthophila, vespula},
            new UnitType[]{pilaster, pylon},
            new UnitType[]{emission, waveform}
        );

        // 四次重构器 (T4→T5): PU132 原版: vespula→lepidoptera, pylon→monument
        ((Reconstructor)tetrativeReconstructor).upgrades.add(
            new UnitType[]{vespula, lepidoptera},
            new UnitType[]{pylon, monument},
            new UnitType[]{waveform, ultraviolet}
        );
    }
}
