package zzw.content.blocks.exp;

import arc.Core;
import arc.math.Mathf;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.ui.Bar;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import zzw.content.entities.ExpHolder;
import zzw.content.entities.ExpOrbs;
import zzw.content.graphics.UnityPal;

/**
 * 经验工厂 (PU132 unity.world.blocks.exp.KoruhCrafter 移植)
 * <p>继承 GenericCrafter (非 StemGenericCrafter)。消耗经验值 (exp) 进行生产。
 * 当经验不足时 (ignoreExp=true), 工厂仍可运作但会受到伤害 (lackExp)。
 * onDestroyed() 时散播经验球。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>unity.entities.ExpHolder → zzw.content.entities.ExpHolder</li>
 *   <li>unity.entities.ExpOrbs → zzw.content.entities.ExpOrbs</li>
 *   <li>unity.graphics.UnityPal → zzw.content.graphics.UnityPal</li>
 *   <li>bundle key explib.expAmount 不存在主 bundle, 用硬编码字符串兜底</li>
 * </ul></p>
 */
public class KoruhCrafter extends GenericCrafter{
    /** 每次生产消耗的经验值 */
    public int expUse = 2;
    /** 经验容量 */
    public int expCapacity = 24;
    /** 若为 true, 经验不足时仍可运作但受到伤害 */
    public boolean ignoreExp = true;

    /** 经验不足时, 每点缺失经验造成的伤害 */
    public float craftDamage = 3.5f;
    /** 经验不足时触发的特效 */
    public Effect craftDamageEffect = Fx.explosion;

    public KoruhCrafter(String name){
        super(name);
        sync = true;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.itemCapacity, "@", Core.bundle.format("exp.expAmount", expCapacity));
        if(expUse > 0){
            // bundle key "explib.expAmount" 在主 bundle 不存在, 用硬编码字符串兜底
            float expPerSec = (expUse / craftTime) * 60;
            String expLabel = Core.bundle.has("explib.expAmount")
                ? Core.bundle.format("explib.expAmount", expPerSec)
                : (int)expPerSec + " exp/s";
            stats.add(Stat.input, "@ [lightgray]@[]", expLabel, StatUnit.perSecond.localized());
        }
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("exp", (KoruhCrafterBuild entity) -> new Bar(
            () -> Core.bundle.get("bar.exp"),
            () -> UnityPal.exp,
            entity::expf
        ));
    }

    public class KoruhCrafterBuild extends GenericCrafterBuild implements ExpHolder{
        public int exp;

        /** 经验不足时调用, 造成伤害 (延迟到 tick 末尾, 避免在销毁时影响依赖此方块类型的代码) */
        public void lackingExp(int missing){
            Core.app.post(() -> {
                damage(craftDamage * missing * Mathf.random(0.5f, 1f));
            });
        }

        @Override
        public boolean shouldConsume(){
            return super.shouldConsume() && (ignoreExp || exp >= expUse);
        }

        @Override
        public void consume(){
            super.consume();
            int a = Math.min(expUse, exp);
            exp -= a;
            if(a < expUse){
                lackingExp(expUse - a);
                craftDamageEffect.at(this);
            }
        }

        @Override
        public int getExp(){
            return exp;
        }

        @Override
        public int handleExp(int amount){
            if(amount > 0){
                int e = Math.min(expCapacity - exp, amount);
                exp += e;
                return e;
            }else{
                int e = Math.min(-amount, exp);
                exp -= e;
                return -e;
            }
        }

        public float expf(){
            return exp / (float)expCapacity;
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

        @Override
        public void drawSelect(){
            super.drawSelect();
            drawPlaceText(exp + "/" + expCapacity, tile.x, tile.y, exp >= expUse);
        }

        @Override
        public void onDestroyed(){
            // 散播经验球
            ExpOrbs.spreadExp(x, y, exp * 0.3f, 3 * size);
            super.onDestroyed();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(exp);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            exp = read.i();
        }
    }
}
