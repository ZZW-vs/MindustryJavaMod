package zzw;

import arc.Events;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;

import zzw.content.blocks.Z_Blocks;
import zzw.content.Z_Items;
import zzw.content.Z_Factory;
import zzw.content.mechanics.Z_Mechanics;


public class TestMod extends Mod{
    private static final float WELCOME_DIALOG_DELAY = 10f;
    private static final float CLOSE_BUTTON_DELAY = 100f;
    
    public TestMod(){
        Events.on(EventType.ClientLoadEvent.class, e -> {
            Time.run(WELCOME_DIALOG_DELAY, this::showWelcomeDialog);
        });
    }


    @Override
    public void loadContent(){
        // 加载自定义物品（基础资源）
        Z_Items.load();
        
        // 加载自定义工厂（需要物品）
        Z_Factory.load();

        // 加载机械系统（需要物品和工厂）
        Z_Mechanics.load();
        
        // 加载自定义方块（可能需要物品和工厂）
        Z_Blocks.load();
    }
    
    /**
     * 显示欢迎对话框
     */
    private void showWelcomeDialog() {
        BaseDialog dialog = new BaseDialog("欢迎来玩我的模组！");
        dialog.cont.add("我是b站up“郑zip”，感谢您游玩我的模组");
        
        // 延迟添加关闭按钮，让玩家有时间阅读消息
        Time.run(CLOSE_BUTTON_DELAY, dialog::addCloseButton);
        
        // 显示对话框
        dialog.show();
    }
}
