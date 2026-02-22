package zzw;

import arc.Events;
import arc.util.Log;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;

import zzw.content.blocks.Z_Blocks;
import zzw.content.Z_Items;
import zzw.content.Z_Factory;
import zzw.content.Z_Mine;
import zzw.content.mechanics.Z_Mechanics;


public class TestMod extends Mod{
    private static final float WELCOME_DIALOG_DELAY = 10f;
    private static final float CLOSE_BUTTON_DELAY = 100f;
    
    public TestMod(){
        Log.info("[ZZW] Loaded Java classes.");
        
        Events.on(EventType.ClientLoadEvent.class, e -> {
            Time.run(WELCOME_DIALOG_DELAY, this::showWelcomeDialog);
        });
    }

    @Override
    public void init() {
        
    }

    @Override
    public void loadContent(){
        // 物品保持在Java中加载（兼容性考虑，大量Java代码引用）
        Z_Items.load();
        
        // 加载矿物（需要物品）
        Z_Mine.load();
        
        // 加载工厂（需要物品）
        Z_Factory.load();
        
        // 高性能核心逻辑保留在Java中
        Z_Mechanics.load();
        
        // 加载方块（可能需要物品和工厂）
        Z_Blocks.load();
        
        Log.info("[ZZW] Java content loaded");
    }
    
    /**
     * 显示欢迎对话框
     */
    private void showWelcomeDialog() {
        BaseDialog dialog = new BaseDialog("欢迎来玩我的模组！");
        dialog.cont.add("我是b站up“郑zip”，感谢您游玩我的模组");
        
        Time.run(CLOSE_BUTTON_DELAY, dialog::addCloseButton);
        
        dialog.show();
    }
}
