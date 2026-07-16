package zzw.content.exp;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class ExpItemTurret extends ExpTurret {
    public ObjectMap<Item, BulletType> ammoTypes = new OrderedMap<>();

    public ExpItemTurret(String name){
        super(name);
        hasItems = true;
    }

    /** Initializes accepted ammo map. Format: [item1, bullet1, item2, bullet2...] */
    public void ammo(Object... objects){
        ammoTypes = OrderedMap.of(objects);
    }

    /** Makes copies of all bullets and limits their range. */
    public void limitRange(){
        limitRange(1f);
    }

    /** Makes copies of all bullets and limits their range. */
    public void limitRange(float margin){
        for(var entry : ammoTypes.copy().entries()){
            var copy = entry.value.copy();
            copy.lifetime = (range + margin) / copy.speed;
            ammoTypes.put(entry.key, copy);
        }
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.remove(Stat.itemCapacity);
        stats.add(Stat.ammo, StatValues.ammo(ammoTypes));
    }

    @Override
    public void init(){
        consume(new ConsumeItemFilter(i -> ammoTypes.containsKey(i)){
            @Override
            public void build(Building build, Table table){
                MultiReqImage image = new MultiReqImage();
                content.items().each(i -> filter.get(i) && i.unlockedNow(),
                    item -> image.add(new ReqImage(new Image(item.uiIcon),
                        () -> build instanceof ExpItemTurretBuild it && !it.ammo.isEmpty() && ((ItemEntry)it.ammo.peek()).item == item)));

                table.add(image).size(8 * 4);
            }

            @Override
            public float efficiency(Building build){
                //valid when it can shoot
                return build instanceof ExpItemTurretBuild it && it.ammo.size > 0 && (it.ammo.peek().amount >= ammoPerShot || it.cheating()) ? 1f : 0f;
            }

            @Override
            public void display(Stats stats){
                //don't display
            }
        });

        super.init();
    }

    public class ExpItemTurretBuild extends ExpTurretBuild {

        @Override
        public void onProximityAdded(){
            super.onProximityAdded();

            //add first ammo item to cheaty blocks so they can shoot properly
            if(!hasAmmo() && cheating() && ammoTypes.size > 0){
                handleItem(this, ammoTypes.keys().next());
            }
        }

        @Override
        public void updateTile(){
            unit.ammo((float)unit.type().ammoCapacity * totalAmmo / maxAmmo);

            super.updateTile();
        }

        @Override
        public int acceptStack(Item item, int amount, Teamc source){
            BulletType type = ammoTypes.get(item);

            if(type == null) return 0;

            return Math.min((int)((maxAmmo - totalAmmo) / ammoTypes.get(item).ammoMultiplier), amount);
        }

        @Override
        public void handleStack(Item item, int amount, Teamc source){
            for(int i = 0; i < amount; i++){
                handleItem(null, item);
            }
        }

        //currently can't remove items from turrets.
        @Override
        public int removeStack(Item item, int amount){
            return 0;
        }

        @Override
        public void handleItem(Building source, Item item){
            if(item == Items.pyratite){
                Events.fire(EventType.Trigger.flameAmmo);
            }

            BulletType type = ammoTypes.get(item);
            if(type == null) return;
            totalAmmo += type.ammoMultiplier;

            //find ammo entry by type
            for(int i = 0; i < ammo.size; i++){
                ItemEntry entry = (ItemEntry)ammo.get(i);

                //if found, put it to the right
                if(entry.item == item){
                    entry.amount += type.ammoMultiplier;
                    ammo.swap(i, ammo.size - 1);
                    return;
                }
            }

            //must not be found
            ammo.add(new ItemEntry(item, (int)type.ammoMultiplier));
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return ammoTypes.get(item) != null && totalAmmo + ammoTypes.get(item).ammoMultiplier <= maxAmmo;
        }

        @Override
        public byte version(){
            return 2;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.b(ammo.size);
            for(AmmoEntry entry : ammo){
                ItemEntry i = (ItemEntry)entry;
                write.s(i.item.id);
                write.s(i.amount);
            }
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            ammo.clear();
            totalAmmo = 0;
            int amount = read.ub();
            for(int i = 0; i < amount; i++){
                Item item = Vars.content.item(revision < 2 ? read.ub() : read.s());
                int itemAmount = Math.min(read.s(), maxAmmo);

                //only add ammo if this is a valid ammo type
                if(item != null && ammoTypes.containsKey(item)){
                    totalAmmo += itemAmount;
                    ammo.add(new ItemEntry(item, itemAmount));
                }
            }
        }
    }

    public class ItemEntry extends AmmoEntry{
        public Item item;

        ItemEntry(Item item, int amount){
            this.item = item;
            this.amount = amount;
        }

        @Override
        public BulletType type(){
            return ammoTypes.get(item);
        }
    }
}
