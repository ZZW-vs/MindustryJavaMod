package zzw.content;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.entities.Lightning;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.GenericCrafter.GenericCrafterBuild;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquids;
import zzw.content.blocks.soul.SoulInfuser;
import zzw.content.mechanics.FactoryBoost;
import zzw.content.blocks.production.BurnerSmelter;
import zzw.content.blocks.production.LiquidsSmelter;
import zzw.content.blocks.production.Press;
import zzw.content.blocks.production.SporeFarm;
import zzw.content.blocks.production.SporePyrolyser;
import zzw.content.blocks.production.HoldingCrucible;
import zzw.content.blocks.production.StemGenericCrafter;
import zzw.content.blocks.exp.KoruhCrafter;
import zzw.content.blocks.exp.MeltingCrafter;
import zzw.content.blocks.draw.DrawGlow;
import zzw.content.graphics.UnityFx;
import zzw.content.graphics.UnityPal;

import static mindustry.Vars.tilesize;

/**
 * 自定义工厂方块注册 - 板材制造厂、南瓜钻井、灵魂注入器 + PU132 全部工厂移植
 *
 * <p>PU132 工厂移植 (配方/数值/特效均按 UnityBlocks.java 原版配置):
 * <ul>
 *   <li>辐照器 irradiator (Press) - 产出辐照电涌</li>
 *   <li>暗合金/火花合金锻造厂 (StemGenericCrafter + DrawSmelter)</li>
 *   <li>固化器/钢冶炼厂/熔化器 (LiquidsSmelter/BurnerSmelter + DrawGlow)</li>
 *   <li>致密冶炼厂/岩浆冶炼厂/迪尔坩埚/煤提取器 (KoruhCrafter/MeltingCrafter + DrawExp)</li>
 *   <li>孢子农场/孢子热解器/坩埚容器 (SporeFarm/SporePyrolyser/HoldingCrucible)</li>
 *   <li>终端坩埚/终焉锻造厂 (GenericCrafter/StemGenericCrafter + DrawGlow 覆写)</li>
 * </ul></p>
 */
public class Z_Factory {
    public static Block Plate_Maker_Iron, Plate_Maker_Gold, Plate_Maker_Copper;
    public static Block Large_Plate_Maker_Iron, Large_Plate_Maker_Gold, Large_Plate_Maker_Copper;
    public static Block Pumpkin_Drill;
    // 灵魂注入器 (从 Z_SoulTurrets 移至工厂类, 使用 Category.crafting)
    public static SoulInfuser soulInfuser;

    // ===== PU132 工厂 =====
    /** 辐照器 (Press, 3x3): 钍+钛+电涌合金 → 辐照电涌 */
    public static Press irradiator;
    /** 暗合金锻造厂 (4x4): 铅+硅+炸药+相织物+暗物质 → 暗合金 */
    public static StemGenericCrafter darkAlloyForge;
    /** 火花合金锻造厂 (4x4): 电涌+钛+硅+焰金 → 火花合金 */
    public static StemGenericCrafter sparkAlloyForge;
    /** 固化器 (LiquidsSmelter): 岩浆+水 → 石头 */
    public static LiquidsSmelter solidifier;
    /** 钢冶炼厂: 煤+石墨+致密合金 → 钢 */
    public static GenericCrafter steelSmelter;
    /** 熔化器 (BurnerSmelter): 焚烧可燃物产出岩浆 (需火点燃) */
    public static BurnerSmelter liquifier;
    /** 致密冶炼厂 (KoruhCrafter): 铜+铅+煤 → 致密合金 (耗经验) */
    public static KoruhCrafter denseSmelter;
    /** 岩浆冶炼厂 (MeltingCrafter): 石墨+致密合金+岩浆 → 钢x5 (耗经验) */
    public static MeltingCrafter lavaSmelter;
    /** 迪尔坩埚 (KoruhCrafter): 钛+铅+电涌+钢 → 迪尔 (耗经验) */
    public static KoruhCrafter diriumCrucible;
    /** 煤提取器 (KoruhCrafter): 石头+废料+水 → 煤 (耗经验) */
    public static KoruhCrafter coalExtractor;
    /** 孢子农场: 水面自然生长孢子荚 */
    public static SporeFarm sporeFarm;
    /** 孢子热解器 (3x3): 孢子荚 + 热量 → 煤x3 */
    public static SporePyrolyser sporePyrolyser;
    /** 坩埚容器 (4x4): 熔融物缓存网络 */
    public static HoldingCrucible holdingCrucible;
    /** 终端坩埚 (6x6): 七系合金 → 终极物质 */
    public static GenericCrafter terminalCrucible;
    /** 终焉锻造厂 (8x8): 终极物质+暗合金+光合金 → 终焉合金 */
    public static StemGenericCrafter endForge;

    public static void load() {
        createPlateMakers();
        createLargePlateMakers();
        createDrills();
        createSoulInfuser();

        // ===== PU132 工厂移植 (原版 UnityBlocks 配置) =====
        createIrradiator();
        createDarkAlloyForge();
        createSparkAlloyForge();
        createSolidifier();
        createSteelSmelter();
        createLiquifier();
        createDenseSmelter();
        createLavaSmelter();
        createDiriumCrucible();
        createCoalExtractor();
        createSporeFarm();
        createSporePyrolyser();
        createHoldingCrucible();
        createTerminalCrucible();
        createEndForge();
    }

    private static FactoryBoost.BoostedGenericCrafter plateMaker(String name, ItemStack[] requirements, mindustry.type.Item input, mindustry.type.Item output, int size_, float craftTime_) {
        return new FactoryBoost.BoostedGenericCrafter(name) {{
            buildType = BoostedGenericCrafterBuild::new;
            requirements(Category.crafting, requirements);
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(output, 2);
            consumeItem(input, 2);
            size = size_;
            hasItems = true;
            craftTime = craftTime_;
        }};
    }

    private static FactoryBoost.BoostedGenericCrafter largePlateMaker(String name, ItemStack[] requirements, mindustry.type.Item input, mindustry.type.Item output, float craftTime_) {
        return new FactoryBoost.BoostedGenericCrafter(name) {{
            buildType = BoostedGenericCrafterBuild::new;
            requirements(Category.crafting, requirements);
            alwaysUnlocked = true;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(output, 4);
            consumeItem(input, 3);
            hasPower = true;
            consumePower(0.125f);
            size = 3;
            hasItems = true;
            craftTime = craftTime_;
        }};
    }

    private static void createPlateMakers() {
        Plate_Maker_Iron = plateMaker("plate_maker_iron",
                ItemStack.with(Items.copper, 90, Items.lead, 70, Z_Items.Iron, 30),
                Z_Items.Iron, Z_Items.Iron_Sheet, 2, 75f);

        Plate_Maker_Gold = plateMaker("plate_maker_gold",
                ItemStack.with(Items.copper, 90, Items.lead, 70, Z_Items.Gold, 30),
                Z_Items.Gold, Z_Items.Gold_Sheet, 2, 75f);

        Plate_Maker_Copper = plateMaker("plate_maker_copper",
                ItemStack.with(Items.copper, 110, Items.lead, 70),
                Items.copper, Z_Items.Copper_Sheet, 2, 75f);
    }

    private static void createLargePlateMakers() {
        Large_Plate_Maker_Iron = largePlateMaker("large_plate_maker_iron",
                ItemStack.with(Z_Items.Iron_Sheet, 23, Items.lead, 100, Z_Items.Iron, 40),
                Z_Items.Iron, Z_Items.Iron_Sheet, 60f);

        Large_Plate_Maker_Gold = largePlateMaker("large_plate_maker_gold",
                ItemStack.with(Z_Items.Gold_Sheet, 30, Items.lead, 100, Z_Items.Gold, 40),
                Z_Items.Gold, Z_Items.Gold_Sheet, 60f);

        Large_Plate_Maker_Copper = largePlateMaker("large_plate_maker_copper",
                ItemStack.with(Z_Items.Copper_Sheet, 50, Items.copper, 180, Items.lead, 100),
                Items.copper, Z_Items.Copper_Sheet, 60f);
    }

    private static void createDrills() {
        Pumpkin_Drill = new Drill("pumpkin_drill") {{
            requirements(Category.production, ItemStack.with(Items.copper, 50, Items.lead, 30));
            size = 1;
            drillTime = 300f;
            hasItems = true;
            itemCapacity = 10;
            returnItem = Z_Items.Pumpkin_Seeds;
            tier = 1;
            liquidCapacity = 10f;
            hasLiquids = true;
            drawRim = true;
            updateEffect = Fx.pulverizeSmall;
            drillEffect = Fx.pulverizeSmall;
            warmupSpeed = 0.02f;
            consumeLiquid(Liquids.water, 0.1f).boost();
        }};
    }

    // ===== SoulInfuser 灵魂注入器 (从 Z_SoulTurrets 移至工厂类) =====
    // 简化版: 消耗 monolite + 电力产生灵魂, 注入附近炮台/容器
    // PU_V8: size=3, Category.crafting (工厂类)
    private static void createSoulInfuser() {
        soulInfuser = new SoulInfuser("soul-infuser") {{
            requirements(Category.crafting, ItemStack.with(Z_Items.monolite, 200, Items.titanium, 250, Items.silicon, 420));
            size = 3;
            health = 600;
            craftTime = 60f;
            consumePower(3.2f);
            consume(new ConsumeItems(ItemStack.with(Z_Items.monolite, 2)));
            range = 15f;
            injectEffect = Fx.smokeCloud;
        }};
    }

    // ===== PU132 工厂 (原版 UnityBlocks.java 配置) =====

    /** 辐照器: 压板挤压产出辐照电涌 (UnityBlocks L362-371) */
    private static void createIrradiator() {
        irradiator = new Press("irradiator"){{
            requirements(Category.crafting, ItemStack.with(Items.lead, 120, Items.silicon, 80, Items.titanium, 30));
            outputItem = new ItemStack(Z_Items.irradiantSurge, 3);
            size = 3;
            movementSize = 29f;
            fxYVariation = 25f / tilesize;
            craftTime = 50f;
            consumePower(1.2f);
            consume(new ConsumeItems(ItemStack.with(Items.thorium, 5, Items.titanium, 5, Items.surgeAlloy, 1)));
        }};
    }

    /** 暗合金锻造厂: 高温合成暗合金 (UnityBlocks L581-599) */
    private static void createDarkAlloyForge() {
        darkAlloyForge = new StemGenericCrafter("dark-alloy-forge"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 30, Items.lead, 25));

            outputItem = new ItemStack(Z_Items.darkAlloy, 3);
            craftTime = 140f;
            size = 4;
            ambientSound = Sounds.waveSpawn;
            ambientSoundVolume = 0.6f;
            drawer = new zzw.content.blocks.draw.DrawSmelter();

            consume(new ConsumeItems(ItemStack.with(Items.lead, 2, Items.silicon, 3, Items.blastCompound, 1, Items.phaseFabric, 1, Z_Items.umbrium, 2)));
            consumePower(3.2f);

            update((StemGenericCrafter.StemGenericCrafterBuild e) -> {
                if(e.efficiency > 0.0001f && Mathf.chanceDelta(0.76f)){
                    UnityFx.craftingEffect.at(e.x, e.y, Mathf.random(360f));
                }
            });
        }};
    }

    /** 火花合金锻造厂: 电涌合成 + 闪电特效 (UnityBlocks L1351-1374) */
    private static void createSparkAlloyForge() {
        sparkAlloyForge = new StemGenericCrafter("spark-alloy-forge"){{
            requirements(Category.crafting, ItemStack.with(Items.lead, 160, Items.graphite, 340, Z_Items.imberium, 270, Items.silicon, 250, Items.thorium, 120, Items.surgeAlloy, 100));

            outputItem = new ItemStack(Z_Items.sparkAlloy, 4);
            size = 4;
            craftTime = 160f;
            ambientSound = Sounds.loopMachine;
            ambientSoundVolume = 0.6f;
            craftEffect = UnityFx.imberCircleSparkCraftingEffect;
            drawer = new zzw.content.blocks.draw.DrawSmelter();

            consumePower(2.6f);
            consume(new ConsumeItems(ItemStack.with(Items.surgeAlloy, 3, Items.titanium, 4, Items.silicon, 6, Z_Items.imberium, 3)));

            update((StemGenericCrafter.StemGenericCrafterBuild e) -> {
                if(e.efficiency > 0.0001f){
                    if(Mathf.chanceDelta(0.3f)){
                        UnityFx.imberSparkCraftingEffect.at(e.x, e.y, Mathf.random(360f));
                    }else if(Mathf.chanceDelta(0.02f)){
                        Lightning.create(e.team, UnityPal.imberColor, 5f, e.x, e.y, Mathf.random(360f), 5);
                    }
                }
            });
        }};
    }

    /** 固化器: 岩浆+水淬凝成石头 (UnityBlocks L1423-1445) */
    private static void createSolidifier() {
        solidifier = new LiquidsSmelter("solidifier"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 20, Z_Items.denseAlloy, 30));

            health = 150;
            hasItems = true;
            liquidCapacity = 12f;
            updateEffect = Fx.fuelburn;
            craftEffect = UnityFx.rockFx;
            craftTime = 60f;
            outputItem = new ItemStack(Z_Items.stone, 1);

            consume(new ConsumeLiquids(new LiquidStack[]{new LiquidStack(Z_Liquids.lava, 0.1f), new LiquidStack(Liquids.water, 0.1f)}));

            drawer = new DrawGlow(){
                @Override
                public void draw(Building build){
                    GenericCrafterBuild gb = (GenericCrafterBuild)build;
                    Draw.rect(build.block.region, build.x, build.y);
                    ConsumeLiquids con = (ConsumeLiquids)((GenericCrafter)build.block).findConsumer(c -> c instanceof ConsumeLiquids);
                    Draw.color(con.liquids[0].liquid.color, build.liquids.get(con.liquids[0].liquid) / build.block.liquidCapacity);
                    Draw.rect(top, build.x, build.y);
                    Draw.reset();
                }
            };
        }};
    }

    /** 钢冶炼厂: 煤+石墨还原致密合金 (UnityBlocks L1447-1468) */
    private static void createSteelSmelter() {
        steelSmelter = new GenericCrafter("steel-smelter"){
            @Override
            public void setStats(){
                super.setStats();
                // 输出物品速率四舍五入到最多 2 位小数 (原版 3 位)
                zzw.content.util.StatUtils.roundOutputStats(this);
            }

            {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 45, Items.silicon, 20, Z_Items.denseAlloy, 30));
            health = 140;
            itemCapacity = 10;
            craftEffect = UnityFx.craft;
            updateEffect = Fx.fuelburn;
            craftTime = 300f;
            outputItem = new ItemStack(Z_Items.steel, 1);

            consumePower(2f);
            consume(new ConsumeItems(ItemStack.with(Items.coal, 2, Items.graphite, 2, Z_Items.denseAlloy, 3)));

            drawer = new DrawGlow(){
                @Override
                public void draw(Building build){
                    GenericCrafterBuild gb = (GenericCrafterBuild)build;
                    Draw.rect(build.block.region, build.x, build.y);
                    Draw.color(1f, 1f, 1f, gb.warmup * Mathf.absin(8f, 0.6f));
                    Draw.rect(top, build.x, build.y);
                    Draw.reset();
                }
            };
            }}
        };
    }

    /** 熔化器: 焚烧可燃物产出岩浆, 需要火源点燃 (UnityBlocks L1491-1521) */
    private static void createLiquifier() {
        liquifier = new BurnerSmelter("liquifier"){{
            requirements(Category.crafting, ItemStack.with(Items.titanium, 30, Items.silicon, 15, Z_Items.steel, 10));
            health = 100;
            hasLiquids = true;
            updateEffect = Fx.fuelburn;
            craftTime = 30f;
            outputLiquid = new LiquidStack(Z_Liquids.lava, 0.1f);

            configClear(b -> mindustry.entities.Fires.create(b.tile));
            consumePower(3.7f);

            update((StemGenericCrafter.StemGenericCrafterBuild e) -> {
                if(e.progress == 0f && e.warmup > 0.001f && (mindustry.Vars.net.server() || !mindustry.Vars.net.active()) && Mathf.chanceDelta(0.2f)){
                    e.configureAny(null);
                }
            });

            drawer = new DrawGlow(){
                @Override
                public void draw(Building build){
                    GenericCrafterBuild gb = (GenericCrafterBuild)build;
                    Draw.rect(build.block.region, build.x, build.y);

                    mindustry.type.Liquid liquid = ((GenericCrafter)build.block).outputLiquid.liquid;
                    Draw.color(liquid.color, build.liquids.get(liquid) / build.block.liquidCapacity);
                    Draw.rect(top, build.x, build.y);
                    Draw.color();

                    Draw.reset();
                }
            };
        }};
    }

    /** 致密冶炼厂: 经验工厂, 铜+铅+煤 → 致密合金 (UnityBlocks L1403-1421) */
    private static void createDenseSmelter() {
        denseSmelter = new KoruhCrafter("dense-smelter"){{
            requirements(Category.crafting, ItemStack.with(Items.copper, 30, Items.lead, 20, Z_Items.stone, 35));

            health = 70;
            hasItems = true;
            craftTime = 46.2f;
            craftEffect = UnityFx.denseCraft;
            itemCapacity = 10;

            outputItem = new ItemStack(Z_Items.denseAlloy, 1);
            consume(new ConsumeItems(ItemStack.with(Items.copper, 1, Items.lead, 2, Items.coal, 1)));

            expUse = 2;
            expCapacity = 24;
            drawer = new zzw.content.blocks.draw.DrawExp(){{
                flame = Color.orange;
                glowAmount = 1f;
            }};
        }};
    }

    /** 岩浆冶炼厂: 岩浆参与的高级冶炼 (UnityBlocks L1470-1489) */
    private static void createLavaSmelter() {
        lavaSmelter = new MeltingCrafter("lava-smelter"){{
            requirements(Category.crafting, ItemStack.with(Items.silicon, 70, Z_Items.denseAlloy, 60, Z_Items.steel, 40));

            health = 190;
            hasLiquids = true;
            hasItems = true;
            craftTime = 70f;
            updateEffect = Fx.fuelburn;
            craftEffect = UnityFx.craft;
            itemCapacity = 21;

            outputItem = new ItemStack(Z_Items.steel, 5);
            consume(new ConsumeItems(ItemStack.with(Items.graphite, 7, Z_Items.denseAlloy, 7)));
            consumePower(2f);
            consumeLiquid(Z_Liquids.lava, 0.4f);

            expUse = 10;
            expCapacity = 60;
            drawer = new zzw.content.blocks.draw.DrawLiquid();
        }};
    }

    /** 迪尔坩埚: 高级经验工厂 (UnityBlocks L1549-1569) */
    private static void createDiriumCrucible() {
        diriumCrucible = new KoruhCrafter("dirium-crucible"){{
            requirements(Category.crafting, ItemStack.with(Items.plastanium, 60, Z_Items.stone, 90, Z_Items.denseAlloy, 90, Z_Items.steel, 150));

            health = 320;
            hasItems = true;
            craftTime = 250f;
            craftEffect = UnityFx.diriumCraft;
            itemCapacity = 40;
            ambientSound = Sounds.loopTech;
            ambientSoundVolume = 0.02f;

            outputItem = new ItemStack(Z_Items.dirium, 1);
            consume(new ConsumeItems(ItemStack.with(Items.titanium, 6, Items.pyratite, 3, Items.surgeAlloy, 3, Z_Items.steel, 9)));
            consumePower(8.28f);

            expUse = 40;
            expCapacity = 160;
            ignoreExp = false;
            craftDamage = 0;
            drawer = new zzw.content.blocks.draw.DrawExp();
        }};
    }

    /** 煤提取器: 石头+废料+水 → 煤 (UnityBlocks L1571-1588) */
    private static void createCoalExtractor() {
        coalExtractor = new KoruhCrafter("coal-extractor"){{
            requirements(Category.crafting, ItemStack.with(Items.silicon, 80, Z_Items.stone, 100, Z_Items.steel, 150));

            health = 250;
            hasItems = true;
            craftTime = 240f;
            craftEffect = UnityFx.craftFx;
            itemCapacity = 50;

            consume(new ConsumeItems(ItemStack.with(Z_Items.stone, 6, Items.scrap, 2)));
            consumeLiquid(Liquids.water, 0.5f);
            consumePower(6f);
            outputItem = new ItemStack(Items.coal, 1);

            expUse = 30;
            expCapacity = 120;
            craftDamage = 0;
            drawer = new zzw.content.blocks.draw.DrawExp();
        }};
    }

    /** 孢子农场: 极便宜的天然孢子荚生产 (UnityBlocks L2925-2933) */
    private static void createSporeFarm() {
        sporeFarm = new SporeFarm("spore-farm"){{
            requirements(Category.production, ItemStack.with(Items.lead, 5));
            health = 50;
            rebuildable = false;
            hasItems = true;
            itemCapacity = 2;
            buildCostMultiplier = 0.01f;
            breakSound = Sounds.stepWater;
        }};
    }

    /** 孢子热解器: 热解孢子荚产煤 (UnityBlocks L3006-3016) */
    private static void createSporePyrolyser() {
        sporePyrolyser = new SporePyrolyser("spore-pyrolyser"){{
            requirements(Category.crafting, ItemStack.with(Z_Items.nickel, 25, Items.titanium, 50, Items.copper, 50, Items.lead, 30));
            size = 3;
            health = 1100;
            craftTime = 50f;
            outputItem = new ItemStack(Items.coal, 3);
            ambientSound = Sounds.loopMachine;
            ambientSoundVolume = 0.6f;
            consumeItem(Items.sporePod, 1);
            addGraph(new zzw.content.mechanics.torque.graphs.GraphHeat(60f, 0.4f, 0.008f).setAccept(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
        }};
    }

    /** 坩埚容器: 熔融物网络缓存节点 (UnityBlocks L2981-2987) */
    private static void createHoldingCrucible() {
        holdingCrucible = new HoldingCrucible("holding-crucible"){{
            requirements(Category.crafting, ItemStack.with(Z_Items.nickel, 50, Z_Items.cupronickel, 150, Items.metaglass, 150, Items.titanium, 30));
            size = 4;
            health = 2400;
            addGraph(new zzw.content.mechanics.torque.graphs.GraphCrucible(50f, false).setAccept(0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0));
            addGraph(new zzw.content.mechanics.torque.graphs.GraphHeat(275f, 0.05f, 0.01f).setAccept(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
        }};
    }

    /** 终端坩埚: 七系合金合成终极物质 (UnityBlocks L3474-3504) */
    private static void createTerminalCrucible() {
        terminalCrucible = new GenericCrafter("terminal-crucible"){
            @Override
            public void setStats(){
                super.setStats();
                // 输出物品速率四舍五入到最多 2 位小数 (原版 3 位)
                zzw.content.util.StatUtils.roundOutputStats(this);
            }

            {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 810, Items.graphite, 720, Items.silicon, 520, Items.phaseFabric, 430, Items.surgeAlloy, 320, Z_Items.plagueAlloy, 120, Z_Items.darkAlloy, 120, Z_Items.lightAlloy, 120, Z_Items.advanceAlloy, 120, Z_Items.monolithAlloy, 120, Z_Items.sparkAlloy, 120, Z_Items.superAlloy, 120));
            size = 6;
            craftTime = 310f;
            ambientSound = Sounds.waveSpawn;
            ambientSoundVolume = 0.6f;
            outputItem = new ItemStack(Z_Items.terminum, 1);

            consumePower(45.2f);
            consume(new ConsumeItems(ItemStack.with(Z_Items.plagueAlloy, 3, Z_Items.darkAlloy, 3, Z_Items.lightAlloy, 3, Z_Items.advanceAlloy, 3, Z_Items.monolithAlloy, 3, Z_Items.sparkAlloy, 3, Z_Items.superAlloy, 3)));

            drawer = new DrawGlow(){
                /** 灯光贴图 (load 时预加载, 避免每帧 atlas 查询) */
                TextureRegion lights;

                @Override
                public void load(Block block){
                    super.load(block);
                    lights = Core.atlas.find(block.name + "-lights");
                }

                @Override
                public void draw(Building build){
                    GenericCrafterBuild gb = (GenericCrafterBuild)build;
                    Draw.rect(build.block.region, build.x, build.y);

                    Draw.blend(Blending.additive);

                    Draw.color(1f, Mathf.absin(5f, 0.5f) + 0.5f, Mathf.absin(Time.time + 90f * Mathf.radDeg, 5f, 0.5f) + 0.5f, gb.warmup);
                    Draw.rect(lights, build.x, build.y);

                    float b = (Mathf.absin(8f, 0.25f) + 0.75f) * gb.warmup;
                    Draw.color(1f, b, b, b);

                    Draw.rect(top, build.x, build.y);

                    Draw.reset();
                    Draw.blend();
                }
            };
            }}
        };
    }

    /** 终焉锻造厂: 终极合成设施 (UnityBlocks L3506-3530+) */
    private static void createEndForge() {
        endForge = new StemGenericCrafter("end-forge"){
            final int effectTimer = timers++;

            {
                requirements(Category.crafting, ItemStack.with(Items.silicon, 2300, Items.phaseFabric, 650, Items.surgeAlloy, 1350, Z_Items.plagueAlloy, 510, Z_Items.darkAlloy, 510, Z_Items.lightAlloy, 510, Z_Items.advanceAlloy, 510, Z_Items.monolithAlloy, 510, Z_Items.sparkAlloy, 510, Z_Items.superAlloy, 510, Z_Items.terminationFragment, 230));
                size = 8;
                craftTime = 410f;
                ambientSoundVolume = 0.6f;
                outputItem = new ItemStack(Z_Items.terminaAlloy, 2);

                consumePower(86.7f);
                consume(new ConsumeItems(ItemStack.with(Z_Items.terminum, 3, Z_Items.darkAlloy, 5, Z_Items.lightAlloy, 5)));

                update((StemGenericCrafter.StemGenericCrafterBuild e) -> {
                    if(e.efficiency > 0.0001f){
                        if(e.timer.get(effectTimer, 120f)){
                            UnityFx.forgeFlameEffect.at(e);
                            UnityFx.forgeAbsorbPulseEffect.at(e);
                        }
                        if(Mathf.chanceDelta(0.7f * e.warmup)){
                            UnityFx.forgeAbsorbEffect.at(e.x, e.y, Mathf.random(360f));
                        }
                    }
                });

                drawer = new DrawGlow(){
                    /** 灯光贴图 (load 时预加载, 避免每帧 atlas 查询) */
                    TextureRegion lights;

                    @Override
                    public void load(Block block){
                        super.load(block);
                        lights = Core.atlas.find(block.name + "-lights");
                    }

                    @Override
                public void draw(Building build){
                    GenericCrafterBuild gb = (GenericCrafterBuild)build;
                    Draw.rect(build.block.region, build.x, build.y);

                    Draw.blend(Blending.additive);
                    Draw.color(1f, Mathf.absin(5f, 0.5f) + 0.5f, Mathf.absin(Time.time + 90f * Mathf.radDeg, 5f, 0.5f) + 0.5f, gb.warmup);

                    Draw.rect(lights, build.x, build.y);
                    float b = (Mathf.absin(8f, 0.25f) + 0.75f) * gb.warmup;

                    Draw.color(1f, b, b, b);
                    Draw.rect(top, build.x, build.y);

                    // 渲染状态复位: 不重置 blend 会导致后续所有方块渲染残留 additive 叠加模式
                    // → 画面变白变糊、贴图变亮 (终端坩埚同款写法)
                    Draw.reset();
                    Draw.blend();
                }
                };
            }
        };
    }
}
