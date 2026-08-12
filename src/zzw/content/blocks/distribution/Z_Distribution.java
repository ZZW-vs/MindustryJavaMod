package zzw.content.blocks.distribution;

import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import zzw.content.Z_Items;
import zzw.content.exp.DrawOver;

/**
 * PU_V8 物品运输方块注册 (传送器 + 3 种传送带)
 *
 * 参考: PU_V8 main/src/unity/content/UnityBlocks.java L1686-1768 + L2910-2914
 *
 * 注册内容:
 * 1. teleporter         - Teleporter 物品传送器 (12 颜色频道)
 * 2. steel-conveyor      - KoruhConveyor 钢制传送带 (absorbLasers=true 标志)
 * 3. mechanical-conveyor - ShadowedConveyor 机械传送带 (含 shadow 影子贴图)
 * 4. dirium-conveyor     - ExpKoruhConveyor 迪里姆合金传送带 (完整经验系统)
 * 5. distribution-drill  - DistributionDrill 分配式钻机 (钻机间互传产物)
 */
public class Z_Distribution {
    public static Block teleporter;
    public static KoruhConveyor steelConveyor;
    public static ShadowedConveyor mechanicalConveyor;
    public static ExpKoruhConveyor diriumConveyor;
    public static DistributionDrill distributionDrill;

    public static void load() {
        // ===== Teleporter 物品传送器 (PU_V8 L1758-1760) =====
        // 12 颜色频道 (原版 8 + 新增 cyan/magenta/olive/coral)
        // 同色同队互相传送物品, 仅传送后耗电
        teleporter = new Teleporter("teleporter") {{
            requirements(Category.distribution, ItemStack.with(
                Items.lead, 22, Items.silicon, 10,
                Items.phaseFabric, 32, Z_Items.dirium, 32
            ));
        }};

        // ===== steel-conveyor 钢制传送带 (PU_V8 L1686-1692) =====
        // absorbLasers=true 作为 koruh 阵营传送带标志
        // drawMultiplier=1.9 让动画速度比实际速度快 1.9 倍 (视觉效果)
        steelConveyor = new KoruhConveyor("steel-conveyor") {{
            requirements(Category.distribution, ItemStack.with(
                Z_Items.stone, 1, Z_Items.denseAlloy, 1, Z_Items.steel, 1
            ));
            health = 140;
            speed = 0.1f;
            displayedSpeed = 12.5f;
            drawMultiplier = 1.9f;
        }};

        // ===== mechanical-conveyor 机械传送带 (PU_V8 L2910-2914) =====
        // 继承原版 Conveyor + 额外渲染 shadowRegion 影子贴图用于视觉过渡
        mechanicalConveyor = new ShadowedConveyor("mechanical-conveyor") {{
            requirements(Category.distribution, ItemStack.with(
                Items.copper, 3, Z_Items.nickel, 2
            ));
            health = 250;
            speed = 0.1f;
            displayedSpeed = 10f;
        }};

        // ===== dirium-conveyor 迪里姆合金传送带 (PU_V8 L1694-1702) =====
        // 完整 ExpTurret 经验系统: 等级/exp/伤害减免/死亡散落 30% 经验球
        // absorbLasers=true (继承自 KoruhConveyor) + drawMultiplier=1.3
        // draw=DrawOver 按等级叠加贴图 (levelRegions, 若贴图存在)
        diriumConveyor = new ExpKoruhConveyor("dirium-conveyor") {{
            requirements(Category.distribution, ItemStack.with(
                Z_Items.steel, 1, Items.phaseFabric, 1, Z_Items.dirium, 1
            ));
            health = 150;
            speed = 0.16f;
            displayedSpeed = 20f;
            drawMultiplier = 1.3f;

            draw = new DrawOver();
        }};

        // ===== distribution-drill 分配式钻机 (PU132 L323-330) =====
        // 钻机之间可互相传递产物，避免无限循环
        distributionDrill = new DistributionDrill("distribution-drill") {{
            requirements(Category.production, ItemStack.with(
                Items.copper, 20, Items.silicon, 15, Items.titanium, 20
            ));
            tier = 3;
            drillTime = 450;
            size = 2;

            // v155.4: consumes.liquid(...) -> consumeLiquid(...)
            consumeLiquid(Liquids.water, 0.06f).boost();
        }};
    }
}
