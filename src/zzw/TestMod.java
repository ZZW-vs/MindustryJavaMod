package zzw;

import arc.Events;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;

import zzw.content.blocks.Z_Blocks;
import zzw.content.Z_Items;
import zzw.content.Z_Factory;

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
        Z_Items.load();
        Z_Factory.load();
        Z_Blocks.load();
    }
}
