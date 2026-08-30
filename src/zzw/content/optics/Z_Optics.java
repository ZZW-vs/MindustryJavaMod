package zzw.content.optics;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

import static mindustry.Vars.tilesize;

/**
 * PU132 光学系统方块注册 (UnityBlocks L1062-1176 原版配置)
 *
 * <p>光学系统: 光源发射光束 → 反射镜/分光镜转拆光路 → 光工厂受光生产。
 * 光强随距离衰减 (strength=1 传 50 格), 多光合并 (QuadTree 终点查重)。</p>
 */
public class Z_Optics {
    /** 灯: 基础光源 */
    public static LightSource lightLamp;
    /** 油灯: 大型光源 (耗油) */
    public static LightSource oilLamp;
    /** 无限灯: 沙盒光源 */
    public static LightSource lightLampInfi;
    /** 反射镜: 反射光束 */
    public static LightReflector lightReflector;
    /** 分光镜: 透射一半 + 反射一半 */
    public static LightReflector lightDivisor;
    /** 光锻造厂: 受光生产光合金 */
    public static LightHoldBlock lightForge;

    /** light-forge 四角受光贴图 (top1-4) */
    static TextureRegion[] forgeTopRegions = new TextureRegion[4];

    public static void load() {
        // light-lamp (PU132 L1062-1069): 基础灯, 0.6 光强
        lightLamp = new LightSource("light-lamp") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 5, Items.metaglass, 10));

            lightProduction = 0.6f;
            consumePower(1f);

            drawer = new DrawLightBlock();
        }};

        // oil-lamp (PU132 L1071-1082): 油灯 3x3, 2.0 光强 (耗油)
        oilLamp = new LightSource("oil-lamp") {{
            requirements(Category.logic, ItemStack.with(Items.lead, 20, Items.metaglass, 20, Items.titanium, 15));

            size = 3;
            health = 240;
            lightProduction = 2f;

            consumePower(1.8f);
            consumeLiquid(Liquids.oil, 0.1f);

            drawer = new DrawLightBlock();
        }};

        // light-lamp-infi (PU132 L1084-1089): 沙盒无限灯
        lightLampInfi = new LightSource("light-lamp-infi") {{
            requirements(Category.logic, BuildVisibility.sandboxOnly, ItemStack.with());

            lightProduction = 600000f;
            drawer = new DrawLightBlock();
        }};

        // light-reflector (PU132 L1091-1093): 反射镜
        lightReflector = new LightReflector("light-reflector") {{
            requirements(Category.logic, ItemStack.with(Items.metaglass, 10, Items.silicon, 5));
        }};

        // light-divisor (PU132 L1095-1100): 分光镜 (透射 50%)
        lightDivisor = new LightReflector("light-divisor") {{
            requirements(Category.logic, ItemStack.with(Items.metaglass, 10, Items.titanium, 2));

            health = 80;
            fallthrough = 0.5f;
        }};

        // light-forge (PU132 L1114-1176): 光锻造厂 4x4, 四角受光
        lightForge = new LightHoldBlock("light-forge") {{
            requirements(Category.crafting, ItemStack.with(Items.copper, 1));

            size = 4;
            outputItem = new ItemStack(zzw.content.Z_Items.lightAlloy, 3);

            consumeItem(Items.copper, 2);
            consumeItem(Items.silicon, 5);
            consumeItem(Items.plastanium, 2);
            consumeItem(zzw.content.Z_Items.luminum, 2);
            consumePower(3.5f);

            drawer = new zzw.content.blocks.draw.DrawSmelter(zzw.content.graphics.UnityPal.lightDark) {{
                flameRadius = 7f;
                flameRadiusIn = 3.5f;
                flameRadiusMag = 3f;
                flameRadiusInMag = 1.8f;
            }};

            // 四角受光槽 (每角 required=1.0, update 热度动画 + draw 加色贴图) — 不用局部变量声明
            acceptors.add(
                new LightAcceptorType(0, 0, 1f)
                    .update((LightHoldBlock.LightHoldBuild b, LightAcceptor s) ->
                        s.dataFloat = Mathf.lerpDelta(s.dataFloat, Mathf.clamp(s.status()), 0.05f))
                    .draw((LightHoldBlock.LightHoldBuild b, LightAcceptor s) -> {
                        Draw.z(Layer.block + 0.01f);
                        Draw.alpha(s.dataFloat);
                        Draw.blend(Blending.additive);
                        Draw.rect(forgeTopRegions[0], b.x, b.y);
                        Draw.blend();
                    }),

                new LightAcceptorType(size - 1, 0, 1f)
                    .update((LightHoldBlock.LightHoldBuild b, LightAcceptor s) ->
                        s.dataFloat = Mathf.lerpDelta(s.dataFloat, Mathf.clamp(s.status()), 0.05f))
                    .draw((LightHoldBlock.LightHoldBuild b, LightAcceptor s) -> {
                        Draw.z(Layer.block + 0.01f);
                        Draw.alpha(s.dataFloat);
                        Draw.blend(Blending.additive);
                        Draw.rect(forgeTopRegions[1], b.x, b.y);
                        Draw.blend();
                    }),

                new LightAcceptorType(size - 1, size - 1, 1f)
                    .update((LightHoldBlock.LightHoldBuild b, LightAcceptor s) ->
                        s.dataFloat = Mathf.lerpDelta(s.dataFloat, Mathf.clamp(s.status()), 0.05f))
                    .draw((LightHoldBlock.LightHoldBuild b, LightAcceptor s) -> {
                        Draw.z(Layer.block + 0.01f);
                        Draw.alpha(s.dataFloat);
                        Draw.blend(Blending.additive);
                        Draw.rect(forgeTopRegions[2], b.x, b.y);
                        Draw.blend();
                    }),

                new LightAcceptorType(0, size - 1, 1f)
                    .update((LightHoldBlock.LightHoldBuild b, LightAcceptor s) ->
                        s.dataFloat = Mathf.lerpDelta(s.dataFloat, Mathf.clamp(s.status()), 0.05f))
                    .draw((LightHoldBlock.LightHoldBuild b, LightAcceptor s) -> {
                        Draw.z(Layer.block + 0.01f);
                        Draw.alpha(s.dataFloat);
                        Draw.blend(Blending.additive);
                        Draw.rect(forgeTopRegions[3], b.x, b.y);
                        Draw.blend();
                    })
            );
        }};

        // 四角受光贴图 (light-forge-top1~4): 延迟到 ClientLoad 时 atlas 就绪后加载
        // (LightProcess.register 同期注册, 见 TestMod)
    }
}