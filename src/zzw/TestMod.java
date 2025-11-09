package zzw;

import arc.Events;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;

import zzw.content.blocks.Z_Blocks;
import zzw.content.Z_Items;
import zzw.content.Z_Factory;

/**
 * 主模组类
 * 处理模组的初始化和内容加载
 */
public class TestMod extends Mod{
    // 欢迎对话框显示延迟（秒）
    private static final float WELCOME_DIALOG_DELAY = 10f;
    // 关闭按钮显示延迟（秒）
    private static final float CLOSE_BUTTON_DELAY = 100f;
    
    /**
     * 构造函数，设置事件监听器
     */
    public TestMod(){
        // 监听客户端加载完成事件
        Events.on(EventType.ClientLoadEvent.class, e -> {
            // 延迟显示欢迎对话框
            Time.run(WELCOME_DIALOG_DELAY, this::showWelcomeDialog);
        });
    }

    /**
     * 加载模组内容
     * 按照依赖关系顺序加载：先物品，再工厂，最后方块
     */
    @Override
    public void loadContent(){
        // 加载自定义物品（基础资源）
        Z_Items.load();
        
        // 加载自定义工厂（需要物品）
        Z_Factory.load();
        
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
