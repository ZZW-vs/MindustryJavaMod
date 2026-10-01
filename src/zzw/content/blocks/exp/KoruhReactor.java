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
import zzw.content.exp.ExpHolder;
import zzw.content.exp.ExpOrbs;
import zzw.content.graphics.UnityFx;
import zzw.content.graphics.UnityPal;

/**
 * 经验反应堆 (PU132 unity.world.blocks.exp.KoruhReactor 移植)
 * <p>继承 ImpactReactor。消耗经验 (exp) 维持反应。</p>
 *
 * <p>★ 机制对齐 PU132 原版 (updateTile):
 * 只要"工作时(必要消耗满足)"就检查经验 —— 经验充足则高效运转时随机外喷经验球;
 * 经验不足则每 tick 扣 1 点血持续掉血, 生命归零的同一刻喷出全部经验球。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>PU132 原版用 {@code consValid()} 判定工作时间; v160 已移除该方法,
 *       按项目惯例用等价的 {@code shouldConsume()} (必要消耗满足) 替代</li>
 *   <li>经验球喷出只在"经验不足致死"时内联执行 (与 PU132 一致), onDestroyed 为空</li>
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
            // ★ 对齐 PU132 原版 (consValid() 在 v160 已移除, 按项目惯例用 shouldConsume() 等价替代):
            //   只要"工作时(必要消耗满足)"就检查经验:
            if(shouldConsume()){
                if(exp >= expUse){
                    // 经验充足: 高效运转时随机外喷经验球
                    if(productionEfficiency >= 0.8f && Mathf.randomBoolean(0.001f)){
                        dumpExpOrb();
                    }
                }else{
                    // ★ 经验不足: 每 tick 扣 1 点血, 持续掉血直至生命归零
                    damage(1);
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