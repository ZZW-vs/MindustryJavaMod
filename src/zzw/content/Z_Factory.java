package zzw.content;

import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumeLiquidBase;
import zzw.content.blocks.soul.SoulInfuser;
import zzw.content.mechanics.FactoryBoost;
import zzw.content.blocks.production.BurnerSmelter;
import zzw.content.blocks.production.LiquidsSmelter;
import zzw.content.blocks.production.Press;
import zzw.content.blocks.production.ExplosiveSeparator;
import zzw.content.blocks.production.FloorExtractor;
import zzw.content.blocks.production.SporeFarm;
import zzw.content.blocks.production.SporePyrolyser;
import zzw.content.blocks.production.HoldingCrucible;

/**
 * 自定义工厂方块注册 - 板材制造厂、南瓜钻井、灵魂注入器
 * 继承自 mindustry 工厂体系，使用 BoostedGenericCrafter 实现可升级工厂
 */
public class Z_Factory {
    public static Block Plate_Maker_Iron, Plate_Maker_Gold, Plate_Maker_Copper;
    public static Block Large_Plate_Maker_Iron, Large_Plate_Maker_Gold, Large_Plate_Maker_Copper;
    public static Block Pumpkin_Drill;
    // 灵魂注入器 (从 Z_SoulTurrets 移至工厂类, 使用 Category.crafting)
    public static SoulInfuser soulInfuser;
    
    // PU132 工厂移植
    public static BurnerSmelter burnerSmelter;
    public static LiquidsSmelter liquidsSmelter;
    public static Press press;
    public static ExplosiveSeparator explosiveSeparator;
    public static FloorExtractor floorExtractor;
    public static SporeFarm sporeFarm;
    public static SporePyrolyser sporePyrolyser;
    public static HoldingCrucible holdingCrucible;

    public static void load() {
        createPlateMakers();
        createLargePlateMakers();
        createDrills();
        createSoulInfuser();
        
        // ===== PU132 工厂移植 =====
        createBurnerSmelter();
        createLiquidsSmelter();
        createPress();
        createExplosiveSeparator();
        createFloorExtractor();
        createSporeFarm();
        createSporePyrolyser();
        createHoldingCrucible();
    }

    private static FactoryBoost.BoostedGenericCrafter plateMaker(String name,
            ItemStack[] requirements, mindustry.type.Item input, mindustry.type.Item output, int size_, float craftTime_) {
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

    private static FactoryBoost.BoostedGenericCrafter largePlateMaker(String name,
            ItemStack[] requirements, mindustry.type.Item input, mindustry.type.Item output, float craftTime_) {
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
    
    // ===== PU132 工厂移植 =====
    
    private static void createBurnerSmelter() {
        burnerSmelter = new BurnerSmelter("burner-smelter") {{
            requirements(Category.production, ItemStack.with(Items.copper, 15, Items.lead, 20));
            size = 2;
            health = 400;
            craftTime = 60f;
            outputItems = ItemStack.with(Items.copper, 10, Items.lead, 8);
            consume(new ConsumeItems(ItemStack.with(Items.coal, 1)));
        }};
    }
    
    private static void createLiquidsSmelter() {
        liquidsSmelter = new LiquidsSmelter("liquids-smelter") {{
            requirements(Category.production, ItemStack.with(Items.copper, 25, Items.lead, 30, Items.titanium, 20));
            size = 2;
            health = 500;
            craftTime = 80f;
            outputItems = ItemStack.with(Items.copper, 15, Items.lead, 12, Items.titanium, 8);
            consume(new ConsumeLiquid(Liquids.water, 0.2f));
            consume(new ConsumeItems(ItemStack.with(Items.copper, 2)));
        }};
    }
    
    private static void createPress() {
        press = new Press("press") {{
            requirements(Category.production, ItemStack.with(Items.copper, 20, Items.lead, 15, Items.silicon, 10));
            size = 2;
            health = 350;
            craftTime = 40f;
            outputItems = ItemStack.with(Items.copper, 12, Items.lead, 8, Items.silicon, 5);
            consumePower(1.5f);
            consume(new ConsumeItems(ItemStack.with(Items.copper, 1)));
        }};
    }
    
    private static void createExplosiveSeparator() {
        explosiveSeparator = new ExplosiveSeparator("explosive-separator") {{
            requirements(Category.production, ItemStack.with(Items.copper, 30, Items.titanium, 25, Items.silicon, 20));
            size = 3;
            health = 600;
            craftTime = 120f;
            results = ItemStack.with(Items.copper, 18, Items.titanium, 12, Items.silicon, 8, Items.coal, 5);
            consumePower(2.0f);
            consumeLiquid(Liquids.water, 0.3f);
            consume(new ConsumeItems(ItemStack.with(Items.coal, 3)));
        }};
    }
    
    private static void createFloorExtractor() {
        floorExtractor = new FloorExtractor("floor-extractor") {{
            requirements(Category.production, ItemStack.with(Items.copper, 40, Items.titanium, 35, Items.silicon, 30));
            size = 2;
            health = 450;
            craftTime = 100f;
            outputItems = ItemStack.with(Items.copper, 25, Items.titanium, 18, Items.silicon, 12);
            consumePower(1.8f);
        }};
    }
    
    private static void createSporeFarm() {
        sporeFarm = new SporeFarm("spore-farm") {{
            requirements(Category.production, ItemStack.with(Items.copper, 20, Items.plastanium, 15, Items.silicon, 10));
            size = 2;
            health = 300;
            consumePower(1.2f);
        }};
    }
    
    private static void createSporePyrolyser() {
        sporePyrolyser = new SporePyrolyser("spore-pyrolyser") {{
            requirements(Category.production, ItemStack.with(Items.copper, 35, Items.titanium, 30, Items.silicon, 25));
            size = 3;
            health = 500;
            consumePower(2.5f);
        }};
    }
    
    private static void createHoldingCrucible() {
        holdingCrucible = new HoldingCrucible("holding-crucible") {{
            requirements(Category.crafting, ItemStack.with(Items.copper, 50, Items.lead, 40, Items.titanium, 30));
            size = 2;
            health = 600;
            consume(new ConsumeItems(ItemStack.with(Items.lead, 5)));
        }};
    }
}
