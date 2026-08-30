package zzw.content.blocks.exp;

import arc.Core;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.Stat;
import zzw.content.exp.ExpHolder;
import zzw.content.graphics.UnityPal;

/**
 * 经验保险库 (PU132 unity.world.blocks.exp.KoruhVault 移植)
 * <p>继承 StorageBlock, 提供简单的经验值储存。
 * 修复 PU132 原版中 setBars 引用错误类 (KoruhCrafterBuild) 以及 ExpHolder 接口方法缺失的问题。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>unity.entities.ExpHolder → zzw.content.exp.ExpHolder (真实经验球识别的接口)</li>
 *   <li>unity.graphics.UnityPal → zzw.content.graphics.UnityPal</li>
 *   <li>bars.add → addBar (v155.4 barMap API)</li>
 *   <li>setBars 中的 bar 函数引用修正为本类的 KoruhVaultBuild</li>
 * </ul></p>
 */
public class KoruhVault extends StorageBlock{
    /** 经验容量 */
    public int expCap = 500;

    public KoruhVault(String name){
        super(name);
        update = sync = true;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.itemCapacity, "@", Core.bundle.format("exp.expAmount", expCap));
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("exp", (KoruhVaultBuild entity) -> new Bar(
            () -> Core.bundle.get("bar.exp"),
            () -> UnityPal.exp,
            entity::expf
        ));
    }

    public class KoruhVaultBuild extends StorageBuild implements ExpHolder{
        public int exp;

        @Override
        public int getExp(){
            return exp;
        }

        @Override
        public int handleExp(int amount){
            if(amount > 0){
                int e = Math.min(expCap - exp, amount);
                exp += e;
                return e;
            }else{
                int e = Math.min(-amount, exp);
                exp -= e;
                return -e;
            }
        }

        @Override
        public int unloadExp(int amount){
            int e = Math.min(amount, exp);
            exp -= e;
            return e;
        }

        @Override
        public boolean acceptOrb(){
            return true;
        }

        @Override
        public boolean handleOrb(int orbExp){
            return handleExp(orbExp) > 0;
        }

        /** 经验比例 [0, 1], 用于 bar 显示 */
        public float expf(){
            return exp / (float)expCap;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return items.total() < itemCapacity;
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            drawPlaceText(exp + "/" + expCap, tile.x, tile.y, exp > 0);
        }

        @Override
        public void onDestroyed(){
            // 销毁时散播经验球 (真实经验球实体, 与 PU132 原版一致)
            zzw.content.exp.ExpOrbs.spreadExp(x, y, exp * 0.5f, 3 * size);
            super.onDestroyed();
        }

        @Override
        public void write(arc.util.io.Writes write){
            super.write(write);
            write.i(exp);
        }

        @Override
        public void read(arc.util.io.Reads read, byte revision){
            super.read(read, revision);
            exp = read.i();
        }
    }
}
