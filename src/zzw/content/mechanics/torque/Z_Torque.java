package zzw.content.mechanics.torque;

import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.meta.BuildVisibility;
import arc.math.geom.Point2;
import zzw.content.Z_Items;
import zzw.content.blocks.modular.Chopper;
import zzw.content.blocks.modular.PartStat;
import zzw.content.blocks.modular.PartStatType;
import zzw.content.blocks.modular.PartType;
import zzw.content.blocks.power.CombustionHeater;
import zzw.content.blocks.power.HeatPipe;
import zzw.content.blocks.power.HeatSource;
import zzw.content.blocks.power.SolarCollector;
import zzw.content.blocks.power.SolarReflector;
import zzw.content.blocks.power.ThermalHeater;
import zzw.content.blocks.production.CastingMold;
import zzw.content.blocks.production.Crucible;
import zzw.content.blocks.production.CrucibleChannel;
import zzw.content.blocks.production.CrucibleFluidLoader;
import zzw.content.blocks.production.CruciblePump;
import zzw.content.blocks.production.CrucibleSource;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.blocks.distribution.DriveShaft;
import zzw.content.mechanics.torque.blocks.distribution.InlineGearbox;
import zzw.content.mechanics.torque.blocks.distribution.DriveBelt;
import zzw.content.mechanics.torque.blocks.distribution.SimpleTransmission;
import zzw.content.mechanics.torque.blocks.power.ElectricMotor;
import zzw.content.mechanics.torque.blocks.power.FlyWheel;
import zzw.content.mechanics.torque.blocks.power.HandCrank;
import zzw.content.mechanics.torque.blocks.power.HeatRadiator;
import zzw.content.mechanics.torque.blocks.power.SeebeckGenerator;
import zzw.content.mechanics.torque.blocks.power.SteamPiston;
import zzw.content.mechanics.torque.blocks.power.TorqueGenerator;
import zzw.content.mechanics.torque.blocks.power.WaterTurbine;
import zzw.content.mechanics.torque.blocks.power.WindTurbine;
import zzw.content.mechanics.torque.blocks.production.AugerDrill;
import zzw.content.mechanics.torque.blocks.production.MechanicalExtractor;
import zzw.content.mechanics.torque.graphs.GraphCrucible;
import zzw.content.mechanics.torque.graphs.GraphFlux;
import zzw.content.mechanics.torque.blocks.power.Magnet;
import zzw.content.mechanics.torque.blocks.power.RotorBlock;
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

    // ===== PU160 蒸汽动力 (蒸汽活塞 ↔ 飞轮) =====
    /** 飞轮: 高惯量扭矩节点, 由蒸汽活塞推动 */
    public static FlyWheel flywheel;
    /** 蒸汽活塞: 消耗水+热量, 推动前方飞轮 */
    public static SteamPiston steamPiston;
    /** 塞贝克发电机: 热网温差发电 */
    public static SeebeckGenerator seebeckGenerator;

    // ===== PU160 传动带 =====
    /** 小型传动带: 跨距离传递扭矩 (最多 1 条链接, 5 格范围) */
    public static DriveBelt driveBeltSmall;
    /** 大型传动带: 3x3, 最多 6 条链接, 10 格范围 */
    public static DriveBelt driveBeltLarge;

    // ===== PU132 热力系统 =====
    /** 热管: 热量网络传输管道 */
    public static HeatPipe heatPipe;
    /** 小型散热器: 热量网络耗散端 */
    public static HeatRadiator smallRadiator;
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
    /** 坩埚通道: 连接坩埚网络的通道 */
    public static CrucibleChannel crucibleChannel;
    /** 坩埚液体装载器: 把普通液体注入坩埚网络 */
    public static CrucibleFluidLoader crucibleFluidLoader;
    /** 坩埚泵: 熔融物网络间传输 */
    public static CruciblePump cruciblePump;
    /** 铸模: 熔融物冷却铸回物品 */
    public static CastingMold castingMold;
    /** 坩埚源: 沙盒用无限原料源 */
    public static CrucibleSource crucibleSource;

    // ===== PU132 磁力系统 =====
    /** 镍定子: 永磁体 (2Wb) */
    public static Magnet nickelStator;
    /** 大型镍定子: 永磁体 (10Wb) */
    public static Magnet nickelStatorLarge;
    /** 镍电磁铁: 耗电强化磁体 (25Wb) */
    public static Magnet nickelElectromagnet;
    /** 钕定子: 沙盒永磁体 (200Wb) */
    public static Magnet neodymiumStator;
    /** 小型电力转子: 磁通→扭矩发电 (1x1) */
    public static RotorBlock electricRotorSmall;
    /** 大型电力转子: 磁通→扭矩发电 (3x3) */
    public static RotorBlock electricRotor;

    // ===== PU132 模块化系统 =====
    /** 蓝图剁刀: 模块化扭矩切割方块 (PU132 Chopper) */
    public static Chopper chopper;

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

        // ===== PU160 传动带 (YoungchaBlocks L306-332) =====

        // small-drive-belt (L306): 1x1, rotate, 单侧固定接口, 1 条链接 (范围 5 格)
        // 节点参数忠实还原 TransmissionTorqueGraphNode(0.03f, 8f, ratio=1)
        driveBeltSmall = new DriveBelt("small-drive-belt"){{
            requirements(Category.distribution, with(Z_Items.nickel, 50, Items.graphite, 20));
            health = 150;
            addGraph(new GraphTorque(0.03f, 8f).setAccept(0, 0, 1, 0));
        }};

        // large-drive-belt (L318): 3x3, rotate, 单侧固定接口, 6 条链接 (范围 10 格)
        driveBeltLarge = new DriveBelt("large-drive-belt"){{
            requirements(Category.distribution, with(Z_Items.cupronickel, 30, Items.silicon, 40, Items.graphite, 50));
            size = 3;
            health = 1750;
            maxRange = 10f;
            wheelSize = 8f;
            maxConnections = 6;
            addGraph(new GraphTorque(0.05f, 30f).setAccept(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
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

        // ===== PU160 蒸汽动力 (YoungchaBlocks L473-497) =====

        // flywheel (PU160 L473): 3x3, rotate, GraphTorque(0.05f, 1000f), 前后中间各 1 口
        flywheel = new FlyWheel("flywheel"){{
            requirements(Category.power, with(Z_Items.nickel, 50, Items.titanium, 50, Items.lead, 150));
            size = 3;
            health = 2600;
            addGraph(new GraphTorque(0.05f, 1000f).setAccept(0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
        }};

        // steam-piston (PU160 L485): 3x3, rotate, 耗水 0.1/s, GraphHeat(9f, 0.1f, 0.01f), 单侧 1 口
        steamPiston = new SteamPiston("steam-piston"){{
            requirements(Category.power, with(Items.graphite, 20, Z_Items.nickel, 30, Items.titanium, 50, Items.lead, 150));
            size = 3;
            health = 2000;
            consumeLiquid(Liquids.water, 0.1f);
            addGraph(new GraphHeat(9f, 0.1f, 0.01f).setAccept(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
        }};

        // seebeck-generator (PU160 L540): 3x3, rotate, 热网温差发电, GraphHeat(9f, 0.01f, 0.01f), 前后中间各 1 口
        seebeckGenerator = new SeebeckGenerator("seebeck-generator"){{
            requirements(Category.power, with(Z_Items.nickel, 50, Items.graphite, 30, Items.copper, 120, Items.titanium, 100, Z_Items.cupronickel, 30));
            size = 3;
            health = 2200;
            addGraph(new GraphHeat(9f, 0.01f, 0.01f).setAccept(0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
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

        // small-radiator: 散热器 (PU160 HeatRadiator L553), 2x2, rotate
        // GraphHeat(capacity 4.0, conductivity 0.15, radiativity 0.4), accept 左右两侧各 2 口
        smallRadiator = new HeatRadiator("small-radiator"){{
            requirements(Category.power, with(Z_Items.nickel, 30, Items.graphite, 30, Items.copper, 100, Z_Items.cupronickel, 30));
            size = 1;
            health = 1100;
            addGraph(new GraphHeat(4f, 0.15f, 0.4f).setAccept(0, 0, 1, 1, 0, 0, 1, 1));
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

        // crucible: 坩埚熔炉 (PU_V8 3x3), GraphCrucible + GraphHeat(75f, 0.2f, 0.006f)
        // 3x3 方块每边 3 个端口, 全部允许连接 (12 项 accept)
        crucible = new Crucible("crucible"){{
            requirements(Category.crafting, with(Z_Items.nickel, 10, Items.titanium, 15));
            size = 3;
            health = 400;
            addGraph(new GraphCrucible().setAccept(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
            addGraph(new GraphHeat(75f, 0.2f, 0.006f).setAccept(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
        }};

        // crucible-pump: 坩埚泵, PU_V8 YoungchaBlocks L463: CrucibleGraph(容量5) + TorqueGraphNode(0.1f, 10f)
        // 力矩连接 (0,1,0,1); 由扭矩转速驱动泵送, 不耗电
        cruciblePump = new CruciblePump("crucible-pump"){{
            requirements(Category.crafting, with(Z_Items.cupronickel, 50, Z_Items.nickel, 50, Items.metaglass, 15));
            size = 1;
            health = 500;
            // 1x1 方块: 端口 0=正面(目标网络 set0), 端口 2=背面(源网络 set1)
            addGraph(new GraphCrucible(10f, false).setAccept(1, 0, 2, 0).multi());
            addGraph(new GraphHeat(50f, 0.1f, 0.003f).setAccept(1, 1, 1, 1));
            addGraph(new GraphTorque(0.1f, 10f).setAccept(0, 1, 0, 1));
        }};

        // casting-mold: 铸模 (PU_V8 3x3), GraphCrucible(2f, false) + GraphHeat(55f, 0.2f, 0f)
        // 坩埚接口位于背面 (端口 index 7 = 侧 2 中间), 铸造产物向其余邻居输出
        castingMold = new CastingMold("casting-mold"){{
            requirements(Category.crafting, with(Items.titanium, 70, Z_Items.nickel, 30));
            size = 3;
            health = 700;
            addGraph(new GraphCrucible(2f, false).setAccept(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
            addGraph(new GraphHeat(55f, 0.2f, 0f).setAccept(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
        }};

        // crucible-channel: 坩埚通道 (连接坩埚网络, 四方向)
        crucibleChannel = new CrucibleChannel("crucible-channel"){{
            requirements(Category.crafting, with(Z_Items.nickel, 10, Items.graphite, 10));
            health = 300;
            underBullets = true;
            addGraph(new GraphCrucible(5f, false).setAccept(1, 1, 1, 1));
        }};

        // crucible-fluid-loader: 液体装载器 (把普通液体注入坩埚网络)
        crucibleFluidLoader = new CrucibleFluidLoader("crucible-fluid-loader"){{
            requirements(Category.crafting, with(Z_Items.nickel, 30, Items.silicon, 30, Items.metaglass, 30));
            health = 300;
            rotate = solid = true;
            liquidCapacity = 20f;
            addGraph(new GraphCrucible(15f, false).setAccept(1, 1, 1, 1));
        }};

        // crucible-source: 坩埚源 (沙盒用无限原料源)
        crucibleSource = new CrucibleSource("crucible-source"){{
            requirements(Category.crafting, BuildVisibility.sandboxOnly, with());
            addGraph(new GraphCrucible(99f, false).setAccept(1, 1, 1, 1));
        }};

        // ===== PU132 磁力系统 (UnityBlocks L3059-3108, 3181-3185 原版配置) =====

        // nickel-stator: 镍定子 (永磁体, GraphFlux(6f) 只向正面输出)
        // ★ 2026-09-05 用户要求: 磁通量 6Wb (PU132 原版 2Wb); 永磁体不耗电 (PU132 原版无 consumePower)
        nickelStator = new Magnet("nickel-stator"){{
            requirements(Category.power, with(Z_Items.nickel, 30, Items.titanium, 20));
            health = 450;
            addGraph(new GraphFlux(6f).setAccept(1, 0, 0, 0));
        }};

        // nickel-stator-large: 大型镍定子 (永磁体, GraphFlux(16f) 四面输出)
        // ★ 2026-09-05 用户要求: 磁通量 20Wb (PU132 原版 10Wb); 永磁体不耗电
        nickelStatorLarge = new Magnet("nickel-stator-large"){{
            requirements(Category.power, with(Z_Items.nickel, 250, Items.titanium, 150));
            size = 2;
            health = 1800;
            addGraph(new GraphFlux(20f).setAccept(1, 1, 0, 0, 0, 0, 0, 0));
        }};

        // nickel-electromagnet: 镍电磁铁 (耗电强化, GraphFlux(48f) 电力满意度调谐磁通)
        // ★ 2026-09-05 用户要求: 磁通量 50Wb (PU132 原版 25Wb); 耗电保持原版 1.6
        nickelElectromagnet = new Magnet("nickel-electromagnet"){{
            requirements(Category.power, with(Z_Items.nickel, 250, Items.titanium, 200, Items.copper, 100, Z_Items.cupronickel, 50));
            size = 2;
            health = 1000;
            consumePower(1.6f);
            addGraph(new GraphFlux(50f).setAccept(1, 1, 0, 0, 0, 0, 0, 0));
        }};

        // neodymium-stator: 钕定子 (沙盒, GraphFlux(250f))
        neodymiumStator = new Magnet("neodymium-stator"){{
            requirements(Category.power, BuildVisibility.sandboxOnly, with());
            health = 400;
            addGraph(new GraphFlux(250f).setAccept(1, 0, 0, 0));
        }};

        // electric-rotor-small: 小型电力转子 (磁通→扭矩发电, 1x1)
        electricRotorSmall = new RotorBlock("electric-rotor-small"){{
            requirements(Category.power, with(Z_Items.nickel, 30, Items.copper, 50, Items.titanium, 10));
            health = 120;
            powerProduction = 2f;
            fluxEfficiency = 10f;
            rotPowerEfficiency = 0.8f;
            torqueEfficiency = 0.7f;
            baseTorque = 1f;
            baseTopSpeed = 3f;
            consumePower(1f);
            addGraph(new GraphFlux(false).setAccept(0, 1, 0, 1));
            addGraph(new GraphTorque(0.08f, 20f).setAccept(1, 0, 1, 0));
        }};

        // electric-rotor: 大型电力转子 (磁通→扭矩发电, 3x3)
        electricRotor = new RotorBlock("electric-rotor"){{
            requirements(Category.power, with(Z_Items.nickel, 200, Items.copper, 200, Items.titanium, 150, Items.graphite, 100));
            size = 3;
            health = 1000;
            powerProduction = 32f;
            big = true;
            fluxEfficiency = 10f;
            rotPowerEfficiency = 0.8f;
            torqueEfficiency = 0.8f;
            baseTorque = 5f;
            baseTopSpeed = 15f;
            consumePower(16f);
            addGraph(new GraphFlux(false).setAccept(0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1));
            addGraph(new GraphTorque(0.05f, 150f).setAccept(0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
        }};

        // ===== PU132 模块化系统 (UnityBlocks L2877 原版配置) =====
        // chopper: 蓝图剁刀 (零件网格 7x1, 4 个零件: 枢轴/刀刃/锯刃/连杆)
        // 注: UnityItems.nickel → Z_Items.nickel; 零件文案走 part.unity.* 无前缀键
        chopper = new Chopper("chopper"){{
            requirements(Category.turret, with(Z_Items.nickel, 50, Items.titanium, 50, Items.lead, 30));
            health = 650;
            setGridW(9);   // 蓝图网格 7→9 格, 允许拼出更长的刀身 (枢轴仍在网格 x=0, 即方块旋转中心)
            setGridH(1);
            addPart(arc.Core.bundle.get("part.unity.pivot.name"), arc.Core.bundle.get("part.unity.pivot.info"), PartType.blade, 4, 0, 1, 1, true, true,
                new Point2(0, 0), new ItemStack[0], new byte[]{1, 0, 0, 0}, new byte[]{0, 0, 0, 0},
                new PartStat(PartStatType.mass, 1), new PartStat(PartStatType.collides, false), new PartStat(PartStatType.hp, 10));
            addPart(arc.Core.bundle.get("part.unity.blade.name"), arc.Core.bundle.get("part.unity.blade.info"), PartType.blade, 0, 0, 1, 1,
                with(Z_Items.nickel, 3, Items.titanium, 5), new byte[]{1, 0, 0, 0}, new byte[]{0, 0, 1, 0},
                new PartStat(PartStatType.mass, 2), new PartStat(PartStatType.collides, true), new PartStat(PartStatType.hp, 80), new PartStat(PartStatType.damage, 8));
            addPart(arc.Core.bundle.get("part.unity.serrated-blade.name"), arc.Core.bundle.get("part.unity.serrated-blade.info"), PartType.blade, 2, 0, 2, 1,
                with(Z_Items.nickel, 8, Items.lead, 5), new byte[]{1, 0, 0, 0, 0, 0}, new byte[]{0, 0, 0, 1, 0, 0},
                new PartStat(PartStatType.mass, 6), new PartStat(PartStatType.collides, true), new PartStat(PartStatType.hp, 120), new PartStat(PartStatType.damage, 18));
            addPart(arc.Core.bundle.get("part.unity.rod.name"), arc.Core.bundle.get("part.unity.rod.info"), PartType.blade, 1, 0, 1, 1,
                with(Items.titanium, 3), new byte[]{1, 0, 0, 0}, new byte[]{0, 0, 1, 0},
                new PartStat(PartStatType.mass, 1), new PartStat(PartStatType.collides, false), new PartStat(PartStatType.hp, 40));
            addGraph(new GraphTorque(0.03f, 5f).setAccept(1, 0, 0, 0));
        }};
    }
}
