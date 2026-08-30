package zzw;

import arc.Events;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;

import zzw.content.blocks.Z_Blocks;
import zzw.content.blocks.Z_Turrets;
import zzw.content.blocks.distribution.Z_Distribution;
import zzw.content.Z_Items;
import zzw.content.Z_Factory;
import zzw.content.Z_Liquids;
import zzw.content.Z_Mine;
import zzw.content.Z_Sounds;
import zzw.content.exp.Z_Exp;
import zzw.content.mechanics.Z_Mechanics;
import zzw.content.mechanics.torque.Z_Torque;
import zzw.content.units.Z_Units;
import zzw.content.units.Z_KoruhUnits;
import zzw.util.ZObjs;


/**
 * 模组主入口类 — 所有内容的加载起点
 *
 * 工作流程:
 * 1. 构造函数: 初始化静态数据 (ZObjs等), 注册一次性事件
 * 2. loadContent(): 按依赖顺序加载所有游戏内容 (物品→液体→方块→单位)
 *    ★ 加载顺序很重要! 后加载的可以引用先加载的, 反过来会报空指针
 *
 * 参考: Mindustry 模组开发指南 — https://github.com/Anuken/MindustryModding
 */
public class TestMod extends Mod{
    private static final float WELCOME_DIALOG_DELAY = 3f;

    public TestMod(){
        // 初始化 WavefrontObject 占位实例 (cube/wavefront 炮台引用)
        // 实际 .obj 文件加载在 FileTreeInitEvent 时触发
        ZObjs.init();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            Time.run(WELCOME_DIALOG_DELAY, this::showWelcomeDialog);
            // ★ 注册世界单位子世界鼠标交互 (悬停显示建筑状态 / 点击打开配置和物品界面)
            zzw.content.type.WorldUnitType.registerInteraction();
            // ★ 注册光学系统 (光照传播 + 光束渲染, PU132 LightProcess)
            zzw.content.optics.LightProcess.register();
        });
    }


    @Override
    public void loadContent(){
        // ★ 加载顺序 = 物品栏/数据库显示顺序 (block id 递增)
        //   排列原则: 自创内容(板材/南瓜/展示)在前 → PU 移植内容按类别集中在各类别末尾,
        //   同类别内 PU 移植方块自成一个组, 与 PU 原版物品栏的分组观感一致

        // ★ 加载自定义音效 (必须在单位之前, 单位武器会引用)
        Z_Sounds.load();

        // 加载自定义物品（基础资源; PU 物品在同文件内排在自创物品之后）
        Z_Items.load();

        // 加载自定义液体（需要物品之前或并行）
        Z_Liquids.load();

        // 加载矿物（需要物品）
        Z_Mine.load();

        // ===== 自创方块 (各类别前部) =====

        // 加载自定义工厂（需要物品; PU132 工厂在 Z_Factory 内部已排在自创工厂之后）
        Z_Factory.load();

        // 加载机械系统（需要物品和工厂）
        Z_Mechanics.load();

        // 加载自定义单位 (需要在方块之前, ModularConstructor/TerraCore 引用单位类型)
        Z_Units.load();

        // PU_V8 koruh 阵营单位 (Z_Blocks 的 MechPad 引用其单位类型, 必须在方块前;
        //   单位不占物品栏方块位, 放这里不影响方块排序)
        Z_KoruhUnits.load();

        // 加载自定义方块（可能需要物品和工厂; 含 ModConstructor/TerraCore/强化器/灵魂工厂）
        Z_Blocks.load();

        // ===== PU 移植内容 (各类别末尾, 依次成组) =====

        // PU_V8 扭矩/热量/磁力系统 (power/crafting/production 类别末尾)
        Z_Torque.load();

        // PU_V8 物品运输方块 (传送器 + 3 种传送带)
        Z_Distribution.load();

        // ===== PU 移植炮台 (turret 类别, 完全按 PU132 UnityBlocks 派系顺序成组排列) =====
        // ★ 物品栏顺序 = 方块注册顺序, 各派系内部顺序与 PU132 原版一致

        // dark 派系 (7): apparition, ghost, banshee, fallout, catastrophe, calamity, extinction
        Z_Turrets.loadDark();

        // light 派系 (12): photon, graviton, electron, proton, neutron, gluon,
        //                  w-boson, z-boson, higgs-boson, singularity, muon, ephemeron
        Z_Turrets.loadLight();

        // imber 派系 (7): orb, shockwire, current, plasma, electrobomb, shielder, orb-turret
        Z_Turrets.loadImber();

        // koruh 派系经验炮台 (8): laser-turret ~ inferno (PU132 顺序)
        Z_Exp.loadTurrets();

        // monolith 派系 (14, PU132 交错顺序):
        // ricochet, diviner → life-stealer, recluse, absorber-aura → mage, blackout, shellshock
        // → heat-ray, oracle → purge → incandescence, prism, supernova
        zzw.content.blocks.Z_SoulTurrets.loadPart1();
        zzw.content.blocks.Z_AdvTurrets.loadPart1();
        zzw.content.blocks.Z_SoulTurrets.loadPart2();
        zzw.content.blocks.Z_AdvTurrets.loadPart2();
        zzw.content.blocks.Z_SoulTurrets.loadPart3();
        zzw.content.blocks.Z_AdvTurrets.loadPart3();

        // advance 派系 (8): celsius, kelvin, arc-caster, arc-storm, blue-eclipse, xeno-corruptor,
        //                    the-cube, wavefront
        Z_Turrets.loadAdvance();
        zzw.content.blocks.Z_AdvTurrets.load3D();

        // end 派系 (2): tenmeikiri, endgame
        zzw.content.blocks.Z_AdvTurrets.loadEnd();

        // 经验系统 (effect 类别末尾)
        Z_Exp.load();

        // 光学系统 (光照传播, 需要物品和工厂)
        zzw.content.optics.Z_Optics.load();
    }
    
    /**
     * 显示欢迎对话框
     */
    private void showWelcomeDialog() {
        BaseDialog dialog = new BaseDialog("欢迎来玩我的模组！");
        dialog.cont.add("我是b站up“郑zip”，感谢您游玩我的模组").row();
        dialog.addCloseButton();
        dialog.show();
    }
}
