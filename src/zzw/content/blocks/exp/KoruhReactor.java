package zzw.content.blocks.exp;

import arc.Core;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.ui.Bar;
import mindustry.world.blocks.power.ImpactReactor;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import zzw.content.exp.ExpHolder;
import zzw.content.exp.ExpOrbs;
import zzw.content.graphics.UnityFx;
import zzw.content.graphics.UnityPal;

/**
 * 经验反应堆 (PU132 unity.world.blocks.exp.KoruhReactor 移植)
 * <p>继承 ImpactReactor。消耗经验 (exp) 维持反应。</p>
 *
 * <p>★ 机制对齐 PU132 原版 (updateTile):
 * 只有"正在工作时"才检查经验 —— 这里的"工作"= 必要消耗 (铀/水/电) 全部满足,
 * 即与 ImpactReactor 自身升温条件一致的 {@code efficiency >= 0.9999f && power.status >= 0.99f}。
 * 经验充足则高效运转时随机外喷经验球; 经验不足则每 tick 扣 1 点血持续掉血,
 * 同时周期性播放爆炸特效, 生命归零的同一刻喷出全部经验球。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>PU132 原版用 {@code consValid()} 判定工作时间; v160 已移除该方法,
 *       改为与父类 ImpactReactor 升温判定完全相同的
 *       {@code efficiency >= 0.9999f && power.status >= 0.99f}
 *       (v160 的 {@code shouldConsume()} 只等价于 {@code enabled}, 恒为真,
 *        绝不能拿来当"工作中"用, 否则待机时也会掉血)</li>
 *   <li>经验球喷出只在"经验不足致死"时内联执行 (与 PU132 一致), onDestroyed 为空</li>
 *   <li>bundle key "explib.expAmount" 不存在主 bundle, 用硬编码字符串兜底</li>
 * </ul></p>
 */
public class KoruhReactor extends ImpactReactor{
    /** 每次消耗的经验值 */
    public int expUse = 2;
    /** 经验容量 */
    public int expCapacity = 24;
    /** 经验不足时播放的爆炸特效 (对齐 KoruhCrafter.craftDamageEffect) */
    public Effect damageEffect = Fx.explosion;
    /** 经验不足时爆炸特效的播放间隔 (tick), 避免每帧刷屏 */
    public final int timerDamageEffect = timers++;
    public float damageEffectInterval = 30f;

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
        // ★ 之前误实现 zzw.content.entities.ExpHolder (简化接口), 经验球
        //   (zzw.content.exp.ExpOrbs.ExpOrb) 只识别 zzw.content.exp.ExpHolder,
        //   导致反应堆无法吸收经验球 —— 已改为正确接口
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
            // ★ 对齐 PU132 原版的 consValid() 判定 (v160 已移除该方法):
            //   这里必须用"必要消耗全部满足"作为"工作中"的条件, 也就是父类 ImpactReactor
            //   自己用来决定是否升温的同一个条件。绝不能使用 shouldConsume() ——
            //   它在 v160 只等价于 enabled (放置后恒为 true), 会导致方块一放下、
            //   还没通电通水通铀就开始掉血 (此前的 bug)。
            if(efficiency >= 0.9999f && power.status >= 0.99f){
                if(exp >= expUse){
                    // 经验充足: 高效运转时随机外喷经验球
                    if(productionEfficiency >= 0.8f && Mathf.randomBoolean(0.001f)){
                        dumpExpOrb();
                    }
                }else{
                    // ★ 经验不足: 每 tick 扣 1 点血, 持续掉血直至生命归零
                    damage(1);
                    // ★ 爆炸: 经验不足时周期性播放爆炸特效 (对齐 KoruhCrafter 的 craftDamageEffect),
                    //   按间隔节流, 避免每 tick 刷屏
                    if(timer(timerDamageEffect, damageEffectInterval)){
                        damageEffect.at(x, y);
                    }
                    // ★ 生命归零的同一刻喷出全部经验球 (PU132 原版在 damage 后判 health<=0 内联执行)
                    if(health <= 0f){
                        for(int i = 0, m = Mathf.ceilPositive(exp * 1.5f); i < m; i++){
                            Time.run(i * 10, this::dumpExpOrb);
                        }
                    }
                }
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

        /** ★ 对齐 PU132 原版: onDestroyed 为空, 经验球喷出只在"经验不足致死"时内联执行 (见 updateTile) */
        @Override
        public void onDestroyed(){
            super.onDestroyed();
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