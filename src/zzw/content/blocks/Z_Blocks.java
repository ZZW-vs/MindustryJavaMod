package zzw.content.blocks;

import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.UnitTypes;
import mindustry.gen.Sounds;
import mindustry.game.EventType;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OverlayFloor;
import mindustry.world.blocks.environment.StaticWall;
import mindustry.world.meta.Stat;
import zzw.content.Z_Items;
import zzw.content.blocks.units.MechPad;
import zzw.content.blocks.units.ModularConstructor;
import zzw.content.blocks.units.ModularConstructorPart;
import zzw.content.blocks.units.TerraCore;
import zzw.content.exp.EField;
import zzw.content.units.Z_KoruhUnits;
import zzw.content.units.Z_Units;

import arc.Events;

/**
 * 自定义方块注册 - 铜/铁方块、南瓜、3D展示台、各类墙体与地板
 * 
 * 主要功能:
 * 1. 基础方块: 铜/铁方块、南瓜等装饰性方块
 * 2. 3D展示系统: 支持.obj和.pmx模型展示的专用方块
 * 3. PU_V8移植: 完整的墙体系统，包括：
 *    - LimitWall: 限制最大伤害的特殊墙体
 *    - LevelLimitWall: 基于经验等级成长的墙体
 *    - ShieldWall: 带护盾的强化墙体
 *    - StaticWall: 环境装饰墙体
 * 4. 地板系统: 包括发光地板、覆盖层地板等
 * 
 * 技术特点:
 * - 使用Builder模式简化方块创建
 * - 支持方块合并机制（小块→大块）
 * - 集成经验系统（LevelLimitWall）
 * - 3D模型渲染支持（WavefrontObject/PMXLoader）
 * 
 * 加载顺序:
 * 1. 3D展示方块（确保模型加载器可用）
 * 2. 防御性方块（墙体）
 * 3. 装饰性方块
 * 4. PU_V8移植内容
 * 5. 事件监听器注册
 */
public class Z_Blocks {
    // 铜方块
    public static Block Copper_Block, Large_Copper_Block;
    // 铁方块
    public static Block Iron_Block, Large_Iron_Block;
    // 其他方块
    public static Block Pumpkin, Carved_Pumpkin;
    // ===== 3D 模型展示方块 =====
    public static Block flywheelDisplay;
    public static Block wavefrontDisplay;
    public static Block mmdDisplay;
    // ===== 2D图片展示方块 ===== (废弃)
    //public static Block imageDisplay;

    // ===== PU_V8 移植: 墙体 =====
    // dark-wall (umbrium 暗色墙)
    public static Wall darkWall, darkWallLarge;
    // ustone-wall (石头墙, LimitWall maxDamage)
    public static Wall stoneWall;
    // dense-wall (致密合金墙, LimitWall maxDamage)
    public static Wall denseWall;
    // steel-wall (钢墙, LevelLimitWall 经验等级墙)
    public static Wall steelWall, steelWallLarge;
    // dirium-wall (迪里姆合金墙, LevelLimitWall 经验等级墙)
    public static Wall diriumWall, diriumWallLarge;
    // shielded-wall (护盾墙, ShieldWall 护盾+经验)
    public static Wall shieldedWall, shieldedWallLarge;
    // metaglass-wall (玻璃墙)
    public static Wall metaglassWall, metaglassWallLarge;
    // electrophobic-wall (单极子墙)
    public static Wall electrophobicWall, electrophobicWallLarge;
    // cupronickel-wall (铜镍合金墙)
    public static Wall cupronickelWall, cupronickelWallLarge;
    // sharpslate-wall (锐板岩墙, StaticWall 环境墙)
    public static Block sharpslateWall, infusedSharpslateWall;

    // ===== PU_V8 移植: 单位传送器 (TeleUnit) =====
    public static Block teleunit;

    // ===== PU_V8 移植: 单位工厂 (MechPad) =====
    public static MechPad bufferPad, omegaPad, cachePad;

    // ===== PU132 移植: 模块化构造器 (ModularConstructor) =====
    public static ModularConstructor advanceConstructor;
    public static ModularConstructorPart advanceConstructorModule;

    // ===== PU132 移植: 大地核心 (TerraCore, 世界单位召唤方块) =====
    public static TerraCore terraCore;

    // ===== PU_V8 移植: 地板 =====
    public static Floor electroTile;
    public static Floor sharpslate, infusedSharpslate, archaicSharpslate;
    public static OverlayFloor archaicEnergy;
    public static Floor concreteBlank, concreteFill, concreteNumber, concreteStripe, concrete;
    public static Floor stoneFullTiles, stoneFull, stoneHalf, stoneTiles;

    public static void load() {
        // ★ 3D展示方块最先创建, 避免前面方法异常导致无法注册
        create3DDisplayBlocks();
        createDefenseBlocks();
        createDecorativeBlocks();
        createPUFloors();
        createPUWalls();
        createTeleUnit();
        createMechPads();
        createModularConstructors();
        createTerraCore();
        registerEventListeners();
    }

    private static Wall wall(String name, ItemStack[] requirements, int size_, int health_) {
        return new Wall(name) {{
            requirements(Category.defense, requirements);
            size = size_;
            health = health_;
        }};
    }

    private static void createDefenseBlocks() {
        Copper_Block = wall("copper_block",
                ItemStack.with(Z_Items.Copper_Sheet, 4, Items.copper, 3), 1, 380);
        Large_Copper_Block = wall("large_copper_block",
                ItemStack.with(Z_Items.Copper_Sheet, 16, Items.copper, 12), 2, 1520);

        Iron_Block = wall("iron_block",
                ItemStack.with(Z_Items.Iron_Sheet, 4, Items.copper, 3), 1, 400);
        Large_Iron_Block = wall("large_iron_block",
                ItemStack.with(Z_Items.Iron_Sheet, 16, Z_Items.Iron, 12), 2, 1600);
    }

    private static Block decorative(String name, ItemStack[] requirements, int health_) {
        return new Block(name) {{
            requirements(Category.effect, requirements);
            size = 1;
            health = health_;
            destructible = true;
            allowDiagonal = true;
            solid = true;
        }};
    }

    private static void createDecorativeBlocks() {
        Pumpkin = decorative("pumpkin",
                ItemStack.with(Z_Items.Copper_Sheet, 4, Items.copper, 3), 380);
        Carved_Pumpkin = decorative("carved_pumpkin",
                ItemStack.with(Z_Items.Copper_Sheet, 4, Items.copper, 3), 350);
    }

    // ===== 3D 模型展示方块 (伪3D, 使用 WavefrontObject 渲染 .obj 文件) =====
    private static void create3DDisplayBlocks() {
        // 万能模型展示台: 3x3 尺寸, 支持4种内置模型切换
        // flywheelDisplay 和 wavefrontDisplay 保留作为兼容, 但实际使用 universalDisplay
        flywheelDisplay = new ObjDisplayBlock("universal-display") {{
            requirements(Category.effect, ItemStack.with(Items.copper, 50, Items.lead, 30, Items.silicon, 20), true);
            health = 800;
            buildVisibility = BuildVisibility.shown;
        }};
        
        // 为了保持兼容性, 给旧名字起别名 (在Mindustry中通过ContentLoader处理)
        // 实际上我们只注册一个 "universal-display"
        wavefrontDisplay = flywheelDisplay;

        // MMD 模型专用展示台: 直接渲染 PMX 文件, 专用渲染设置
        mmdDisplay = new MmdDisplayBlock("mmd-display") {{
            requirements(Category.effect, ItemStack.with(Items.copper, 50, Items.lead, 30, Items.silicon, 20), true);
            health = 800;
            buildVisibility = BuildVisibility.shown;
        }};
        //2D图片展示台: 支持6张内置图片的展示，丰富的参数调节 (废弃但保留图片)
        //imageDisplay = new ImageDisplayBlock("image-display") {{
        //   requirements(Category.effect, ItemStack.with(Items.copper, 80, Items.lead, 50, Items.graphite, 30), true);
        //    health = 600;
        //    buildVisibility = BuildVisibility.shown;
        //}};
    }

    // ===== PU_V8 移植: 墙体 (简化版, 用 vanilla Wall 或 LimitWall) =====
    private static void createPUWalls() {
        // dark-wall: 暗色墙 (umbrium, 简化为 vanilla Wall, 移除光照交互)
        darkWall = new Wall("dark-wall") {{
            requirements(Category.defense, ItemStack.with(Z_Items.umbrium, 6));
            health = 120 * 4;
        }};
        darkWallLarge = new Wall("dark-wall-large") {{
            requirements(Category.defense, ItemStack.with(Z_Items.umbrium, 24));
            health = 120 * 4 * 4;
            size = 2;
        }};

        // ustone-wall: 石头墙 (LimitWall maxDamage=40)
        stoneWall = new LimitWall("ustone-wall") {{
            requirements(Category.defense, ItemStack.with(Z_Items.stone, 6));
            maxDamage = 40f;
            health = 200;
        }};

        // dense-wall: 致密合金墙 (LimitWall maxDamage=32)
        denseWall = new LimitWall("dense-wall") {{
            requirements(Category.defense, ItemStack.with(Z_Items.denseAlloy, 6));
            maxDamage = 32f;
            health = 560;
        }};

        // steel-wall: 钢墙 (LevelLimitWall 经验等级墙)
        // PU_V8: maxLevel=6, expFields=[ERational(maxDamage 48→24, axis=-3)]
        steelWall = new LevelLimitWall("steel-wall") {{
            requirements(Category.defense, ItemStack.with(Z_Items.steel, 6));
            maxDamage = 24f;
            health = 810;
            maxLevel = 6;
            expFields = new EField[]{
                new EField.ERational(v -> maxDamage = v, 48f, 24f, -3f, Stat.abilities, v -> arc.Core.bundle.format("stat.unity.maxdamage", v)).formatAll(false)
            };
        }};
        steelWallLarge = new LevelLimitWall("steel-wall-large") {{
            requirements(Category.defense, ItemStack.with(Z_Items.steel, 24));
            maxDamage = 48f;
            health = 3240;
            size = 2;
            maxLevel = 12;
            expFields = new EField[]{
                new EField.ERational(v -> maxDamage = v, 72f, 24f, -3f, Stat.abilities, v -> arc.Core.bundle.format("stat.unity.maxdamage", v)).formatAll(false)
            };
        }};

        // dirium-wall: 迪里姆合金墙 (LevelLimitWall 经验等级墙)
        // PU_V8: maxLevel=6, blinkFrame=30, expFields=[ERational(maxDamage 152→50, axis=-3), ELinearCap(blinkFrame 10→10, cap=2)]
        diriumWall = new LevelLimitWall("dirium-wall") {{
            requirements(Category.defense, ItemStack.with(Z_Items.dirium, 6));
            maxDamage = 76f;
            blinkFrame = 30f;
            health = 760;
            maxLevel = 6;
            expFields = new EField[]{
                new EField.ERational(v -> maxDamage = v, 152f, 50f, -3f, Stat.abilities, v -> arc.Core.bundle.format("stat.unity.maxdamage", v)).formatAll(false),
                new EField.ELinearCap(v -> blinkFrame = v, 10f, 10f, 2, Stat.abilities, v -> arc.Core.bundle.format("stat.unity.blinkframe", v)).formatAll(false)
            };
        }};
        diriumWallLarge = new LevelLimitWall("dirium-wall-large") {{
            requirements(Category.defense, ItemStack.with(Z_Items.dirium, 24));
            maxDamage = 152f;
            blinkFrame = 30f;
            health = 3040;
            size = 2;
            maxLevel = 12;
            expFields = new EField[]{
                new EField.ERational(v -> maxDamage = v, 304f, 50f, -2f, Stat.abilities, v -> arc.Core.bundle.format("stat.unity.maxdamage", v)).formatAll(false),
                new EField.ELinearCap(v -> blinkFrame = v, 10f, 5f, 4, Stat.abilities, v -> arc.Core.bundle.format("stat.unity.blinkframe", v)).formatAll(false)
            };
        }};

        // shielded-wall: 护盾墙 (ShieldWall 护盾+经验等级)
        // PU_V8 shieldWall: maxLevel=10, shieldHealth=500, expFields=[ERational(maxDamage 100→25), ELinear(repair 50→10), ELinear(shieldHealth 500→25)]
        // 贴图: shielded-wall.png + shielded-wall-top.png (PU132/PU_V8 原版)
        shieldedWall = new ShieldWall("shielded-wall") {{
            requirements(Category.defense, ItemStack.with(Z_Items.dirium, 8, Z_Items.steel, 6, Items.silicon, 4));
            health = 500;
            shieldHealth = 500;
            maxDamage = 50f;
            maxLevel = 10;
            expFields = new EField[]{
                new EField.ERational(v -> maxDamage = v, 100f, 25f, -3f, Stat.abilities, v -> arc.Core.bundle.format("stat.unity.maxdamage", v)).formatAll(false),
                new EField.ELinear(v -> repair = v, 50f, 10f, Stat.repairSpeed, v -> arc.Core.bundle.format("stat.unity.repairspeed", v)).formatAll(false),
                new EField.ELinear(v -> shieldHealth = v, 500, 25, Stat.shieldHealth)
            };
        }};
        shieldedWallLarge = new ShieldWall("shielded-wall-large") {{
            requirements(Category.defense, ItemStack.with(Z_Items.dirium, 32, Z_Items.steel, 24, Items.silicon, 16));
            health = 2000;
            maxDamage = 100f;
            shieldHealth = 2000;
            size = 2;
            maxLevel = 20;
            expFields = new EField[]{
                new EField.ERational(v -> maxDamage = v, 200f, 50f, -3f, Stat.abilities, v -> arc.Core.bundle.format("stat.unity.maxdamage", v)).formatAll(false),
                new EField.ELinear(v -> repair = v, 200f, 20f, Stat.repairSpeed, v -> arc.Core.bundle.format("stat.unity.repairspeed", v)).formatAll(false),
                new EField.ELinear(v -> shieldHealth = v, 2000, 50, Stat.shieldHealth)
            };
        }};

        // metaglass-wall: 玻璃墙 (简化为 vanilla Wall, 移除光照交互)
        metaglassWall = new Wall("metaglass-wall") {{
            requirements(Category.defense, ItemStack.with(Items.lead, 6, Items.metaglass, 6));
            health = 350;
        }};
        metaglassWallLarge = new Wall("metaglass-wall-large") {{
            requirements(Category.defense, ItemStack.with(Items.lead, 24, Items.metaglass, 24));
            health = 1400;
            size = 2;
        }};

        // electrophobic-wall: 单极子墙 (简化为 vanilla Wall, 移除热图/能量倍率)
        electrophobicWall = new Wall("electrophobic-wall") {{
            requirements(Category.defense, ItemStack.with(Z_Items.monolite, 4, Items.silicon, 2));
            health = 400;
        }};
        electrophobicWallLarge = new Wall("electrophobic-wall-large") {{
            requirements(Category.defense, ItemStack.with(Z_Items.monolite, 16, Items.silicon, 8));
            health = 1600;
            size = 2;
        }};

        // cupronickel-wall: 铜镍合金墙 (简化为 vanilla Wall, 移除热图)
        cupronickelWall = new Wall("cupronickel-wall") {{
            requirements(Category.defense, ItemStack.with(Z_Items.cupronickel, 8, Z_Items.nickel, 5));
            health = 500;
        }};
        cupronickelWallLarge = new Wall("cupronickel-wall-large") {{
            requirements(Category.defense, ItemStack.with(Z_Items.cupronickel, 36, Z_Items.nickel, 20));
            health = 2000;
            size = 2;
        }};

        // sharpslate-wall: 锐板岩墙 (StaticWall 环境墙, vanilla)
        sharpslateWall = new StaticWall("sharpslate-wall") {{
            variants = 2;
            sharpslate.asFloor().wall = this;
        }};
        infusedSharpslateWall = new StaticWall("infused-sharpslate-wall") {{
            variants = 2;
            infusedSharpslate.asFloor().wall = this;
            archaicSharpslate.asFloor().wall = this;
        }};
    }

    // ===== PU_V8 移植: 单位传送器 (PU132 UnityBlocks L1787-1793 完整还原) =====
    private static void createTeleUnit() {
        teleunit = new TeleUnit("teleunit") {{
            requirements(Category.units, ItemStack.with(Items.lead, 180, Items.titanium, 80, Items.silicon, 90, Items.phaseFabric, 64, Z_Items.dirium, 48));
            size = 3;
            ambientSound = Sounds.loopTech;  // v155.4: techloop -> loopTech
            ambientSoundVolume = 0.02f;
            consumePower(3f);
        }};
    }

    // ===== PU_V8 移植: 单位工厂 (PU132 UnityBlocks L1729-1764 完整还原) =====
    private static void createMechPads() {
        bufferPad = new MechPad("buffer-pad") {{
            requirements(Category.units, ItemStack.with(Z_Items.stone, 120, Items.copper, 170, Items.lead, 150, Items.titanium, 150, Items.silicon, 180));
            size = 2;
            craftTime = 100;
            consumePower(0.7f);
            unitType = Z_KoruhUnits.buffer;
        }};

        omegaPad = new MechPad("omega-pad") {{
            requirements(Category.units, ItemStack.with(Z_Items.stone, 220, Items.lead, 200, Items.silicon, 230, Items.thorium, 260, Items.surgeAlloy, 100));
            size = 3;
            craftTime = 300f;
            consumePower(1.2f);
            unitType = Z_KoruhUnits.omega;
        }};

        cachePad = new MechPad("cache-pad") {{
            requirements(Category.units, ItemStack.with(Z_Items.stone, 150, Items.lead, 160, Items.silicon, 100, Items.titanium, 60, Items.plastanium, 120, Items.phaseFabric, 60));
            size = 2;
            craftTime = 130f;
            consumePower(0.8f);
            unitType = Z_KoruhUnits.cache;
        }};
    }

    // ===== PU132 移植: 模块化构造器 (PU132 UnityBlocks L3200-3234 完整还原+适配) =====
    // advance-constructor-module: 6×6 部件, 链式连接到主构造器, 提升等级解锁更高 tier 单位
    // advance-constructor: 13×13 主构造器, 通过模块连接提升 tier, 减少 tier 以上单位的建造时间
    private static void createModularConstructors() {
        // advance-constructor-module: 6×6, 中等材料, 功耗120f (Block 构造器已设), 需低温液体
        // 适配: UnityItems.xenium → Z_Items.stone, UnityItems.advanceAlloy → Z_Items.advanceAlloy
        // 适配: consumes.liquid(...) → consumeLiquid(...), consumes.power(...) 已在 Block 构造器中用 consumePower(120f)
        advanceConstructorModule = new ModularConstructorPart("advance-constructor-module") {{
            requirements(Category.units, ItemStack.with(Z_Items.stone, 300, Items.silicon, 200, Items.graphite, 300, Items.thorium, 400, Items.phaseFabric, 50, Items.surgeAlloy, 100, Z_Items.advanceAlloy, 300));
            size = 6;
            liquidCapacity = 20f;
            consumeLiquid(Liquids.cryofluid, 0.7f);
            hasLiquids = true;
            hasPower = true;
        }};

        // advance-constructor: 13×13, 大量材料, 功耗13f
        // 配方调整: T0=scepter(30min), T1=reign(40min), T2=devourer(50min)
        // 适配: UnityItems.xenium → Z_Items.stone, UnityItems.advanceAlloy → Z_Items.advanceAlloy
        // 适配: UnityUnitTypes.mantle → Z_Units.devourer (虫子单位作为终极单位)
        // 适配: consumes.power(13f) → consumePower(13f)
        advanceConstructor = new ModularConstructor("advance-constructor") {{
            requirements(Category.units, ItemStack.with(Z_Items.stone, 3000, Items.silicon, 5000, Items.graphite, 2000, Items.thorium, 3000, Items.phaseFabric, 800, Items.surgeAlloy, 700, Z_Items.advanceAlloy, 1500));
            size = 13;
            efficiencyPerTier = 10f * 60f;
            moduleBlock = advanceConstructorModule;

            plans.addAll(
                // T0: UnitTypes.scepter (30分钟建造时间)
                new ModularConstructorPlan(UnitTypes.scepter, 30f * 60f, 0,
                ItemStack.with(Items.silicon, 690, Items.lead, 60, Items.graphite, 30, Items.titanium, 550, Items.metaglass, 40, Items.plastanium, 420)),

                // T1: UnitTypes.reign (40分钟建造时间)
                new ModularConstructorPlan(UnitTypes.reign, 40f * 60f, 1,
                ItemStack.with(Items.silicon, 1350, Items.lead, 160, Items.graphite, 90, Items.titanium, 550, Items.metaglass, 100, Items.plastanium, 830, Items.surgeAlloy, 330, Items.phaseFabric, 250)),

                // T2: Z_Units.devourer (50分钟建造时间, 虫子单位作为终极单位)
                new ModularConstructorPlan(Z_Units.devourer, 50f * 60f, 2,
                ItemStack.with(Items.silicon, 2050, Items.graphite, 180, Items.titanium, 830, Items.metaglass, 150, Items.plastanium, 1250, Items.surgeAlloy, 500, Items.phaseFabric, 375))
            );

            consumePower(13f);
        }};
    }

    // ===== PU132 移植: 大地核心 (TerraCore, 召唤世界单位) =====
    // 点击按钮召唤 terra 世界单位, 单位携带 8x18 子世界可放置建筑物
        private static void createTerraCore () {
            terraCore = new TerraCore("terra-core") {{
                requirements(Category.units, ItemStack.with(Items.lead, 200, Items.silicon, 150, Items.thorium, 100, Items.phaseFabric, 50, Z_Items.advanceAlloy, 80));
                size = 2;
                health = 2000;
                // 绑定召唤的单位类型 (Z_Units.terra 必须已加载)
                type = Z_Units.terra;
            }};
        }

    // ===== PU_V8 移植: 地板 (vanilla Floor / OverlayFloor) =====
    private static void createPUFloors() {
        // electro-tile: 电子地板 (vanilla Floor, 默认 3 variants)
        electroTile = new Floor("electro-tile");

        // sharpslate: 锐板岩 (variants=3)
        sharpslate = new Floor("sharpslate") {{
            variants = 3;
        }};

        // infused-sharpslate: 灌注锐板岩 (variants=3, 发光)
        infusedSharpslate = new Floor("infused-sharpslate") {{
            variants = 3;
            emitLight = true;
            lightRadius = 24f;
            lightColor = mindustry.graphics.Pal.darkMetal.cpy().a(0.1f);
        }};

        // archaic-sharpslate: 远古锐板岩 (variants=3, 发光)
        archaicSharpslate = new Floor("archaic-sharpslate") {{
            variants = 3;
            emitLight = true;
            lightRadius = 24f;
            lightColor = mindustry.graphics.Pal.darkMetal.cpy().a(0.12f);
        }};

        // archaic-energy: 远古能量覆盖层 (OverlayFloor, variants=3, 发光)
        archaicEnergy = new OverlayFloor("archaic-energy") {{
            variants = 3;
            emitLight = true;
            lightRadius = 24f;
            lightColor = mindustry.graphics.Pal.darkMetal.cpy().a(0.24f);
        }};

        // concrete 系列 (vanilla Floor)
        concreteBlank = new Floor("concrete-blank");
        concreteFill = new Floor("concrete-fill") {{
            variants = 0;
        }};
        concreteNumber = new Floor("concrete-number") {{
            variants = 10;
        }};
        concreteStripe = new Floor("concrete-stripe");
        concrete = new Floor("concrete");

        // stone-* 系列 (vanilla Floor)
        stoneFullTiles = new Floor("stone-full-tiles");
        stoneFull = new Floor("stone-full");
        stoneHalf = new Floor("stone-half");
        stoneTiles = new Floor("stone-tiles");
    }

    private static void registerEventListeners() {
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking) return;
            if (event.tile.block() == Copper_Block) {
                BlockMerger.checkAndReplace(event.tile, Copper_Block, Large_Copper_Block);
            } else if (event.tile.block() == Iron_Block) {
                BlockMerger.checkAndReplace(event.tile, Iron_Block, Large_Iron_Block);
            }
        });
    }
}
