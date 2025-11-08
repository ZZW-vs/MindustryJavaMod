package zzw;

import arc.Events;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;

import zzw.content.Blocks;
import zzw.content.items;
import zzw.content.Planets;
import zzw.content.structure.StructureConfig;
import zzw.content.structure.StructureDetector;

public class TestMod extends Mod{
    public TestMod(){
        Events.on(EventType.ClientLoadEvent.class, e -> {
            Time.run(10f, () -> {
                BaseDialog dialog = new BaseDialog("Welcome to use my mod!");
                dialog.cont.add("I am ZZW, Thank you for playing my mod");
                Time.run(100f, dialog::addCloseButton);
                dialog.show();
            });
        });
    }

    @Override
    public void loadContent(){
        items.load();
        Planets.load();
        Blocks.load();
        
        // 初始化结构检测系统
        StructureDetector.initialize();

        // 注册默认结构
        StructureConfig.registerDefaultStructures();
    }
}
