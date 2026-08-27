package zzw.content.mechanics.torque;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.world.meta.BuildVisibility;
import zzw.content.Z_Items;
import zzw.content.blocks.power.CombustionHeater;
import zzw.content.blocks.power.HeatPipe;
import zzw.content.blocks.power.HeatSource;
import zzw.content.blocks.power.SolarCollector;
import zzw.content.blocks.power.SolarReflector;
import zzw.content.blocks.power.ThermalHeater;
import zzw.content.blocks.production.CastingMold;
import zzw.content.blocks.production.Crucible;
import zzw.content.blocks.production.CruciblePump;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.blocks.distribution.DriveShaft;
import zzw.content.mechanics.torque.blocks.distribution.InlineGearbox;
import zzw.content.mechanics.torque.blocks.distribution.SimpleTransmission;
import zzw.content.mechanics.torque.blocks.power.ElectricMotor;
import zzw.content.mechanics.torque.blocks.power.HandCrank;
import zzw.content.mechanics.torque.blocks.power.TorqueGenerator;
import zzw.content.mechanics.torque.blocks.power.WaterTurbine;
import zzw.content.mechanics.torque.blocks.power.WindTurbine;
import zzw.content.mechanics.torque.blocks.production.AugerDrill;
import zzw.content.mechanics.torque.blocks.production.MechanicalExtractor;
import zzw.content.mechanics.torque.graphs.GraphCrucible;
import zzw.content.mechanics.torque.graphs.GraphHeat;
import zzw.content.mechanics.torque.graphs.GraphTorque;
import zzw.content.mechanics.torque.graphs.GraphTorqueConsume;
import zzw.content.mechanics.torque.graphs.GraphTorqueGenerate;
import zzw.content.mechanics.torque.graphs.GraphTorqueTrans;

import static mindustry.type.ItemStack.with;

/**
 * PU_V8 扭矩系统方块注册
 *
 * 参考: PU_V8 main/src/unity/content/UnityBlocks.java L2881-3154
 * 注: UnityItems.* 已替换为 Z_Items.*
 * 注: v155.4 适配: consumes.power(...) -> consumePower(...)
 */
public class Z_Torque{
    // 生产 (扭矩消耗)
    public static AugerDrill augerDrill;
    public static MechanicalExtractor mechanicalExtractor;

    // 分配 (扭矩传输)
    public static DriveShaft driveShaft;
    public static InlineGearbox inlineGearbox;
    public static GraphBlock shaftRouter;
    public static SimpleTransmission simpleTransmission;

    // 动力 (扭矩产生)
    public static HandCrank handCrank;
    public static WindTurbine windTurbine;
    public static WaterTurbine waterTurbine;
    public static ElectricMotor electricMotor;
    public static TorqueGenerator infiTorque;

    // ===== PU132 热力系统 =====
    /** 热管: 热量网络传输管道 */
    public static HeatPipe heatPipe;
    /** 小型散热器: 热量网络耗散端 */
    public static GraphBlock smallRadiator;
    /** 地热加热器: 热液地板产热 */
    public static ThermalHeater thermalHeater;
    /** 燃烧加热器: 焚烧可燃物产热 */
    public static CombustionHeater combustionHeater;
    /** 太阳能集热器: 配合反射镜聚焦产热 */
    public static SolarCollector solarCollector;
    /** 太阳反射镜: 为集热器聚焦光线 */
    public static SolarReflector solarReflector;
    /** 无限热源: 沙盒热量源 (持续注入热量) */
    public static HeatSource infiHeater;
    /** 无限冷源: 沙盒冷源 (热量归零) */
    public static HeatSource infiCooler;

    // ===== PU132 坩埚系统 =====
    /** 坩埚熔炉: 熔化物品/合成合金 */
    public static Crucible crucible;
    /** 坩埚容器: 熔融物网络缓存 (Z_Factory 注册 holdingCrucible) */
    /** 坩埚泵: 熔融物网络间传输 */
    public static CruciblePump cruciblePump;
    /** 铸模: 熔融物冷却铸回物品 */
    public static CastingMold castingMold;

    public static void load(){
        // ===== 生产方块 (扭矩消耗) =====
        // auger-drill (PU_V8 L2881): 3x3, GraphTorqueConsume(45f, 8f, 1.5f, 0.03f, 0.15f)
        // 效率调优: oversupplyFalloff 0.7→1.5 (衰减小一点), drillTime 400→300 (基础产矿提高)
        // 1000转速(lastVelocity)时约20矿/秒, 10000转速时约36矿/秒
        augerDrill = new AugerDrill("auger-drill"){{
            requirements(Category.production, with(Items.lead, 100, Items.copper, 75));
            size = 3;
            health = 1000;
            tier = 3;
            drillTime = 300f;
            addGraph(new GraphTorqueConsume(45f, 8f, 1.5f, 0.03f, 0.15f).setAccept(0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
        }};

        // mechanical-extractor (PU_V8 L2890): 3x3, GraphTorqueConsume(45f, 8f, 1.0f, 0.06f, 0.3f)
        // 效率调优: oversupplyFalloff 0.7→1.0 (平方关系下不宜过高)
        mechanicalExtractor = new MechanicalExtractor("mechanical-extractor"){{
            requirements(Category.production, with(Items.lead, 100, Items.copper, 75));
            hasPower = false;
            size = 3;
            health = 1000;
            pumpAmount = 0.4f;

            addGraph(new GraphTorqueConsume(45f, 8f, 1.0f, 0.06f, 0.3f).setAccept(0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
        }};

        // ===== 分配方块 (扭矩传输) =====
        // drive-shaft (PU_V8 L2922): GraphTorque(0.01f, 3f) accept(1,0,1,0)
        driveShaft = new DriveShaft("drive-shaft"){{
            requirements(Category.distribution, with(Items.copper, 10, Items.lead, 10));
            health = 150;
            addGraph(new GraphTorque(0.01f, 3f).setAccept(1, 0, 1, 0));
        }};

        // inline-gearbox (PU_V8 L2928): 2x2, GraphTorque(0.02f, 20f) accept(1,1,0,0, 1,1,0,0)
        inlineGearbox = new InlineGearbox("inline-gearbox"){{
            requirements(Category.distribution, with(Items.titanium, 20, Items.lead, 30, Items.copper, 30));
            size = 2;
            health = 700;
            addGraph(new GraphTorque(0.02f, 20f).setAccept(1, 1, 0, 0, 1, 1, 0, 0));
        }};

        // shaft-router (PU_V8 L2935): GraphTorque(0.05f, 5f) accept(1,1,1,1), preserveDraw
        shaftRouter = new GraphBlock("shaft-router"){{
            requirements(Category.distribution, with(Items.copper, 20, Items.lead, 20));
            health = 100;
            preserveDraw = true;
            addGraph(new GraphTorque(0.05f, 5f).setAccept(1, 1, 1, 1));
        }};

        // simple-transmission (PU_V8 L2942): 2x2, GraphTorqueTrans(0.05f, 25f).setRatio(1f, 2.5f)
        simpleTransmission = new SimpleTransmission("simple-transmission"){{
            requirements(Category.distribution, with(Items.titanium, 50, Items.lead, 50, Items.copper, 50));
            size = 2;
            health = 500;
            addGraph(new GraphTorqueTrans(0.05f, 25f).setRatio(1f, 2.5f).setAccept(2, 1, 0, 0, 1, 2, 0, 0));
        }};

        // ===== 动力方块 (扭矩产生) =====
        // hand-crank (PU_V8 L3085): GraphTorque(0.01f, 3f) accept(1,0,0,0)
        handCrank = new HandCrank("hand-crank"){{
            requirements(Category.power, with(Z_Items.nickel, 5, Items.lead, 20));
            health = 120;
            addGraph(new GraphTorque(0.01f, 3f).setAccept(1, 0, 0, 0));
        }};

        // wind-turbine (PU_V8 L3091): 3x3, GraphTorqueGenerate(0.03f, 20f, 5f, 5f)
        windTurbine = new WindTurbine("wind-turbine"){{
            requirements(Category.power, with(Items.titanium, 20, Items.lead, 80, Items.copper, 70));
            size = 3;
            health = 1200;
            addGraph(new GraphTorqueGenerate(0.03f, 20f, 5f, 5f).setAccept(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        }};

        // water-turbine (PU_V8 L3098): 3x3, disableOgUpdate(), GraphTorqueGenerate(0.3f, 20f, 7f, 15f)
        waterTurbine = new WaterTurbine("water-turbine"){{
            requirements(Category.power, with(Items.metaglass, 50, Z_Items.nickel, 20, Items.lead, 150, Items.copper, 100));
            size = 3;
            health = 1100;
            liquidCapacity = 250f;
            liquidPressure = 0.3f;
            disableOgUpdate();
            addGraph(new GraphTorqueGenerate(0.3f, 20f, 7f, 15f).setAccept(0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0));
        }};

        // electric-motor (PU_V8 L3108): 3x3, consumes.power(4.5f), GraphTorqueGenerate(0.1f, 25f, 10f, 16f)
        electricMotor = new ElectricMotor("electric-motor"){{
            requirements(Category.power, with(Items.silicon, 100, Items.lead, 80, Items.copper, 150, Items.titanium, 150));
            size = 3;
            health = 1300;
            // v155.4: consumes.power(...) -> consumePower(...)
            consumePower(4.5f);
            addGraph(new GraphTorqueGenerate(0.1f, 25f, 10f, 16f).setAccept(0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
        }};

        // infi-heater (PU132 L3160): 沙盒无限热源, GraphHeat(1000f, 1f, 0f) accept(1,1,1,1)
        infiHeater = new HeatSource("infi-heater"){{
            requirements(Category.power, BuildVisibility.sandboxOnly, with());
            health = 200;
            addGraph(new GraphHeat(1000f, 1f, 0f).setAccept(1, 1, 1, 1));
        }};

        // infi-cooler (PU132 L3166): 沙盒无限冷源 (isVoid=true 热量归零)
        infiCooler = new HeatSource("infi-cooler"){{
            requirements(Category.power, BuildVisibility.sandboxOnly, with());
            health = 200;
            isVoid = true;
            addGraph(new GraphHeat(1000f, 1f, 0f).setAccept(1, 1, 1, 1));
        }};

        // infi-torque (PU_V8 L3148): sandbox, GraphTorqueGenerate(0.001f, 1f, 999999f, 9999f) accept(1,1,1,1)
        infiTorque = new TorqueGenerator("infi-torque"){{
            requirements(Category.power, BuildVisibility.sandboxOnly, with());
            health = 200;
            preserveDraw = true;
            rotate = false;
            addGraph(new GraphTorqueGenerate(0.001f, 1f, 999999f, 9999f).setAccept(1, 1, 1, 1));
        }};

        // ===== PU132 热力系统 (UnityBlocks L2941/L3018-3057 原版配置) =====

        // heat-pipe: 热量网络管道, GraphHeat(5f, 0.7f, 0.008f) accept(1,1,1,1)
        // ★ rotate=true: 放置时可旋转, 预览显示方向箭头 (传动带风格, 用户需求)
        heatPipe = new HeatPipe("heat-pipe"){{
            requirements(Category.distribution, with(Items.copper, 15, Z_Items.cupronickel, 10, Z_Items.nickel, 5));
            health = 140;
            rotate = true;
            addGraph(new GraphHeat(5f, 0.7f, 0.008f).setAccept(1, 1, 1, 1));
        }};

        // small-radiator: 小型散热器, GraphHeat(10f, 0.7f, 0.05f) accept(1,1,1,1)
        smallRadiator = new GraphBlock("small-radiator"){{
            requirements(Category.power, with(Items.copper, 30, Z_Items.cupronickel, 20, Z_Items.nickel, 15));
            health = 200;
            solid = true;
            addGraph(new GraphHeat(10f, 0.7f, 0.05f).setAccept(1, 1, 1, 1));
        }};

        // thermal-heater: 地热加热器, GraphHeat(40f, 0.6f, 0.004f) accept(1,1,0,0,0,0,0,0)
        thermalHeater = new ThermalHeater("thermal-heater"){{
            requirements(Category.power, with(Items.copper, 150, Z_Items.nickel, 100, Items.titanium, 150));
            size = 2;
            health = 500;
            maxTemp = 1100f;
            mulCoeff = 0.11f;
            addGraph(new GraphHeat(40f, 0.6f, 0.004f).setAccept(1, 1, 0, 0, 0, 0, 0, 0));
        }};

        // combustion-heater: 燃烧加热器, GraphHeat(40f, 0.6f, 0.004f) accept(1,1,0,0,0,0,0,0)
        combustionHeater = new CombustionHeater("combustion-heater"){{
            requirements(Category.power, with(Items.copper, 100, Z_Items.nickel, 70, Items.graphite, 40, Items.titanium, 80));
            size = 2;
            health = 550;
            itemCapacity = 5;
            maxTemp = 1200f;
            mulCoeff = 0.45f;
            addGraph(new GraphHeat(40f, 0.6f, 0.004f).setAccept(1, 1, 0, 0, 0, 0, 0, 0));
        }};

        // solar-collector: 太阳能集热器, GraphHeat(60f, 1f, 0.02f) accept(8向仅上)
        solarCollector = new SolarCollector("solar-collector"){{
            requirements(Category.power, with(Z_Items.nickel, 80, Items.titanium, 50, Items.lead, 30));
            size = 3;
            health = 1500;
            maxTemp = 800f;
            // ★ 0.03 → 0.1: PU132 原版升温过慢 (几面反射镜对准仍需数秒升几度),
            //   同等反射镜数量下升温速度提升约 3 倍
            mulCoeff = 0.1f;
            addGraph(new GraphHeat(60f, 1f, 0.02f).setAccept(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
        }};

        // solar-reflector: 太阳反射镜 (链接集热器聚焦产热)
        solarReflector = new SolarReflector("solar-reflector"){{
            requirements(Category.power, with(Z_Items.nickel, 25, Items.copper, 50));
            size = 2;
            health = 800;
        }};

        // ===== PU132 坩埚系统 (UnityBlocks L2974-3004 原版配置) =====

        // crucible: 坩埚熔炉, GraphCrucible + GraphHeat(75f, 0.2f, 0.006f) accept(1,1,1,1)
        crucible = new Crucible("crucible"){{
            requirements(Category.crafting, with(Z_Items.nickel, 10, Items.titanium, 15));
            health = 400;
            addGraph(new GraphCrucible().setAccept(1, 1, 1, 1));
            addGraph(new GraphHeat(75f, 0.2f, 0.006f).setAccept(1, 1, 1, 1));
        }};

        // crucible-pump: 坩埚泵, GraphCrucible(10f, false) multi + GraphHeat(50f, 0.1f, 0.003f)
        cruciblePump = new CruciblePump("crucible-pump"){{
            requirements(Category.crafting, with(Z_Items.cupronickel, 50, Z_Items.nickel, 50, Items.metaglass, 15));
            size = 2;
            health = 500;
            consumePower(1f);
            addGraph(new GraphCrucible(10f, false).setAccept(1, 1, 0, 0, 2, 2, 0, 0).multi());
            addGraph(new GraphHeat(50f, 0.1f, 0.003f).setAccept(1, 1, 1, 1, 1, 1, 1, 1));
        }};

        // casting-mold: 铸模, GraphCrucible(2f, false) + GraphHeat(55f, 0.2f, 0f)
        castingMold = new CastingMold("casting-mold"){{
            requirements(Category.crafting, with(Items.titanium, 70, Z_Items.nickel, 30));
            size = 2;
            health = 700;
            addGraph(new GraphCrucible(2f, false).setAccept(0, 0, 0, 0, 1, 1, 0, 0));
            addGraph(new GraphHeat(55f, 0.2f, 0f).setAccept(1, 1, 1, 1, 1, 1, 1, 1));
        }};
    }
}
