package zzw.content.blocks.exp;

import arc.Core;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.gen.Sounds;
import mindustry.ui.Bar;
import mindustry.world.blocks.power.ImpactReactor;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import zzw.content.entities.ExpHolder;
import zzw.content.entities.ExpOrbs;
import zzw.content.graphics.UnityFx;
import zzw.content.graphics.UnityPal;

/**
 * 经验反应堆 (PU132 unity.world.blocks.exp.KoruhReactor 移植)
 * <p>继承 ImpactReactor。消耗经验 (exp) 维持反应。</p>
 *
 * <p>★ 红温爆炸机制 (替代 PU132 的持续扣血, 与原版钍反应堆一致):
 * 经验不足时反应堆继续运行但堆芯热量累积 (heat), 冒烟警告,
 * 热量满时熔毁爆炸 (PowerGenerator 内置爆炸系统: 范围伤害 + 反应堆爆炸特效);
 * 经验充足时热量缓慢冷却。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>PU132 原版经验不足时每 tick 扣 1 血缓慢致死 → 改为钍反应堆式 heat 累积爆炸</li>
 *   <li>爆炸参数走 PowerGenerator 内置字段 (explosionRadius/Damage/effect),
 *       onDestroyed → createExplosion 自动触发</li>
 *   <li>bundle key "explib.expAmount" 不存在主 bundle, 用硬编码字符串兜底</li>
 * </ul></p>
 */
public class KoruhReactor extends ImpactReactor{
    /** 每次消耗的经验值 */
    public int expUse = 2;
    /** 经验容量 */
    public int expCapacity = 24;
    /** 经验不足时热量累积速度 (1/heating tick 到满) */
    public float heating = 0.004f;
    /** 经验充足时热量冷却速度 (每秒) */
    public float cooling = 0.05f;
    /** 冒烟警告的热量阈值 */
    public float smokeThreshold = 0.4f;

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
        addBar("heat", (KoruhReactorBuild entity) -> new Bar(
            () -> Core.bundle.get("bar.heat", "Heat"),
            () -> mindustry.graphics.Pal.lightOrange,
            () -> entity.heat
        ));
    }

    public class KoruhReactorBuild extends ImpactReactorBuild implements ExpHolder{
        public int exp;
        /** 堆芯热量 (0~1, 满时熔毁爆炸) */
        public float heat;

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
            if(shouldConsume()){
                if(exp >= expUse){
                    // 经验充足: 热量冷却
                    heat = Mathf.approachDelta(heat, 0f, cooling);

                    // 高效率时随机喷射经验球
                    if(productionEfficiency >= 0.8f && Mathf.randomBoolean(0.001f)){
                        dumpExpOrb();
                    }
                }else{
                    // ★ 红温: 经验不足继续运行, 热量累积 (钍反应堆式熔毁前兆)
                    heat += heating * Math.min(Time.delta, 4f);

                    // 热量警告冒烟 (原版钍反应堆 smoke 阶段)
                    if(heat > smokeThreshold){
                        float smoke = 1f + (heat - smokeThreshold) / (1f - smokeThreshold);
                        if(Mathf.chance(smoke / 20f * Time.delta)){
                            Fx.reactorsmoke.at(x + Mathf.range(size * Vars.tilesize / 2f), y + Mathf.range(size * Vars.tilesize / 2f));
                        }
                    }

                    // 热量满: 熔毁爆炸 (kill → onDestroyed → PowerGenerator.createExplosion)
                    if(heat >= 0.999f){
                        kill();
                        return;
                    }
                }
            }else{
                // 停机: 缓慢冷却
                heat = Mathf.approachDelta(heat, 0f, cooling * 0.5f);
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

        /** 爆炸时喷出全部经验球 (原版死亡喷出行为) */
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
            write.f(heat);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            exp = read.i();
            heat = read.f();
        }
    }
}