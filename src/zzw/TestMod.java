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
        // ★ 加载自定义音效 (必须在单位之前, 单位武器会引用)
        Z_Sounds.load();

        // 加载自定义物品（基础资源）
        Z_Items.load();

        // 加载自定义液体（需要物品之前或并行）
        Z_Liquids.load();

        // 加载矿物（需要物品）
        Z_Mine.load();

        // 加载自定义工厂（需要物品）
        Z_Factory.load();

        // 加载机械系统（需要物品和工厂）
        Z_Mechanics.load();

        // 加载 PU_V8 扭矩系统 (需要物品)
        Z_Torque.load();

        // 加载 PU_V8 koruh 阵营单位 (需要在方块之前, MechPad 引用单位类型)
        Z_KoruhUnits.load();

        // 加载自定义单位 (需要在方块之前, ModularConstructor/TerraCore 引用单位类型)
        Z_Units.load();

        // 加载自定义方块（可能需要物品和工厂）
        Z_Blocks.load();

        // 加载 PU_V8 物品运输方块 (传送器 + 3 种传送带)
        Z_Distribution.load();

        // 加载 PU 炮台 (需要物品)
        Z_Turrets.load();

        // 加载 fmonolith 灵魂炮台 (需要物品)
        zzw.content.blocks.Z_SoulTurrets.load();

        // 加载高级炮台 (TractorBeam/oracle/recluse/prism/supernova)
        zzw.content.blocks.Z_AdvTurrets.load();

        // 加载经验系统（需要物品）
        Z_Exp.load();

        // 加载光学系统 (光照传播, 需要物品和工厂)
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
