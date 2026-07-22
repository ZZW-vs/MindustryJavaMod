package zzw.content.mechanics.torque;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import zzw.content.Z_Items;
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

    public static void load(){
        // ===== 生产方块 (扭矩消耗) =====
        // auger-drill (PU_V8 L2881): 3x3, GraphTorqueConsume(45f, 8f, 0.03f, 0.15f)
        augerDrill = new AugerDrill("auger-drill"){{
            requirements(Category.production, with(Items.lead, 100, Items.copper, 75));
            size = 3;
            health = 1000;
            tier = 3;
            drillTime = 400f;
            addGraph(new GraphTorqueConsume(45f, 8f, 0.03f, 0.15f).setAccept(0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
        }};

        // mechanical-extractor (PU_V8 L2890): 3x3, GraphTorqueConsume(45f, 8f, 0.06f, 0.3f)
        mechanicalExtractor = new MechanicalExtractor("mechanical-extractor"){{
            requirements(Category.production, with(Items.lead, 100, Items.copper, 75));
            hasPower = false;
            size = 3;
            health = 1000;
            pumpAmount = 0.4f;

            addGraph(new GraphTorqueConsume(45f, 8f, 0.06f, 0.3f).setAccept(0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0));
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

        // infi-torque (PU_V8 L3148): sandbox, GraphTorqueGenerate(0.001f, 1f, 999999f, 9999f) accept(1,1,1,1)
        infiTorque = new TorqueGenerator("infi-torque"){{
            requirements(Category.power, BuildVisibility.sandboxOnly, with());
            health = 200;
            preserveDraw = true;
            rotate = false;
            addGraph(new GraphTorqueGenerate(0.001f, 1f, 999999f, 9999f).setAccept(1, 1, 1, 1));
        }};
    }
}
