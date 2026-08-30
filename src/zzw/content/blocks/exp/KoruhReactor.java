package zzw.content.blocks.exp;

import arc.Core;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.ui.Bar;
import mindustry.world.blocks.power.ImpactReactor;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import zzw.content.entities.ExpHolder;
import zzw.content.entities.ExpOrbs;
import zzw.content.graphics.UnityFx;
import zzw.content.graphics.UnityPal;

/**
 * 经验反应堆 (PU132 unity.world.blocks.exp.KoruhReactor 移植, 稳定发电机版)
 * <p>继承 ImpactReactor。消耗经验 (exp) 维持反应。</p>
 *
 * <p>★ 稳定化调整 (应用户要求): 移除红温热量累积与熔毁爆炸机制 ——
 * 反应堆为稳定发电机, 无经验时仅停止产出 (ImpactReactor 原版的
 * warmup/plasma 动画与电力启停逻辑不受影响), 被打爆时喷出全部经验球。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>PU132 原版经验不足时每 tick 扣血缓慢致死 → 移除 (稳定发电)</li>
 *   <li>保留: 经验经验球喷出 (高效运转时随机外喷 / 摧毁时全量喷出)</li>
 *   <li>bundle key "explib.expAmount" 不存在主 bundle, 用硬编码字符串兜底</li>
 * </ul></p>
 */
public class KoruhReactor extends ImpactReactor{
    /** 每次消耗的经验值 */
    public int expUse = 2;
    /** 经验容量 */
    public int expCapacity = 24;

    public KoruhReactor(String name){
        super(name);
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.itemCapacity, "@", Core.bundle.format("exp.expAmount", expCapacity));
        // bundle key "explib.expAmount" 在主 bundle 不存在, 用硬编码字符串兜底
        float expPerSec = (expUse / itemDuration) * 60;
        String expLabel = Core.bundle.has("explib.expAmount")
            ? Core.bundle.format("explib.expAmount", expPerSec)
            : (int)expPerSec + " exp/s";
        stats.add(Stat.input, "@ [lightgray]@[]", expLabel, StatUnit.perSecond.localized());
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("exp", (KoruhReactorBuild entity) -> new Bar(
            () -> Core.bundle.get("bar.exp"),
            () -> UnityPal.exp,
            () -> 1f * entity.exp / expCapacity
        ));
    }

    public class KoruhReactorBuild extends ImpactReactorBuild implements ExpHolder{
        public int exp;

        @Override
        public int getExp(){
            return exp;
        }

        /** 处理经验 (正数=注入, 负数=抽取), 返回实际处理量 */
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
        public void updateTile(){
            super.updateTile();
            // 稳定发电机: 无经验惩罚, 经验充足且高效运转时随机外喷经验球
            if(shouldConsume() && exp >= expUse
                && productionEfficiency >= 0.8f && Mathf.randomBoolean(0.001f)){
                dumpExpOrb();
            }
        }

        /** 向外喷射一个经验球 */
        private void dumpExpOrb(){
            float dir = Mathf.random(360f);
            Vec2 vec = new Vec2();
            vec.trns(dir, (size + Mathf.random(0.5f, 1.5f)) * Vars.tilesize).add(x, y);
            UnityFx.expDump.at(x, y, 0, vec);
            Time.run(UnityFx.expDump.lifetime, () -> ExpOrbs.spreadExp(vec.x, vec.y, 10, 0));
        }

        /** 被摧毁时喷出全部经验球 */
        @Override
        public void onDestroyed(){
            super.onDestroyed();
            for(int i = 0, m = Mathf.ceilPositive(exp * 1.5f); i < m; i++){
                Time.run(i * 10, this::dumpExpOrb);
            }
        }

        @Override
        public void consume(){
            super.consume();
            if(exp >= expUse) handleExp(-expUse);
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