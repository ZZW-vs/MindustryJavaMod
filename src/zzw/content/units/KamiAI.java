package zzw.content.units;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Units;
import mindustry.entities.units.UnitController;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import zzw.content.units.kami.KamiPattern;
import zzw.content.units.kami.KamiPattern.PatternData;
import zzw.content.units.kami.KamiPatterns;

import java.util.Arrays;

/**
 * kami 弹幕 AI 控制器 (PU132 {@code unity/ai/kami/KamiAI.java} 的 1:1 移植)。
 *
 * <p>核心机制:
 * <ul>
 *   <li>一次性把所有模式 {@link KamiPattern#all} 洗牌并按 priority 排序, 每次进入新
 *       模式时顺序抽取 ({@link #reset()});</li>
 *   <li>模式生命周期 update / init / end, 期间 {@link #shoot} 计时发射,
 *       {@link #burst} 连发, {@link #run} 延迟回调;</li>
 *   <li>按 {@code pattern.followTarget} 决定是否跟随目标 (默认只维持 minRange 距离);</li>
 *   <li>{@link #updateBarrier()} 把越界玩家拉回 800 半径屏障内。</li>
 * </ul>
 *
 * <p>v160 差异 (与 PU132 原版的取舍):</p>
 * <ul>
 *   <li>使用标准 {@link UnitController}, 不引入注解实体系统 (KamiComp/KamiBulletComp 等);</li>
 *   <li>kami 单位 {@code rotateSpeed=0}, 原版 {@code unit.lookAt()} 会失效, 因此这里
 *       直接设置 {@code unit.rotation = unit.angleTo(target)}, 视觉效果与原版一致;</li>
 *   <li>{@link #difficulty} 与原版一致恒为 0 (原版未做难度递增)。</li>
 * </ul>
 *
 * <p>参考: PU132 {@code unity/ai/kami/KamiAI.java}。</p>
 */
public class KamiAI implements UnitController {
    public static final float minRange = 350f, barrierRange = 800f;
    protected static boolean allPatterns = true;
    private static final Vec2 vec = new Vec2();
    private static final int[] limit = new int[KamiPattern.PatternType.values().length];

    public Unit unit;
    public Unit target;
    public KamiPattern pattern;
    public PatternData patternData;
    public float[] reloads = new float[16];
    public int difficulty = 0, stages = 0;
    public float x, y;
    public float patternTime, waitTime = 2f * 60f;
    public Rand rand = new Rand();

    protected Seq<KamiDelay> delays = new Seq<>();
    protected Seq<KamiPattern> patterns = new Seq<>();

    static {
        KamiPatterns.load();
    }

    /** 绘制屏障圆环并交由当前模式绘制特效 (由单位 Ability 每帧调用) */
    public void draw(){
        float z = Draw.z();
        Draw.z(Layer.flyingUnit);
        Lines.stroke(3f + Mathf.absin(12f, 1f));
        Draw.color(Tmp.c1.set(Color.red).shiftHue(Time.time));
        Draw.blend(Blending.additive);
        Lines.circle(x, y, barrierRange);
        if(pattern != null && waitTime <= 0f){
            pattern.draw(this);
        }
        Draw.blend();
        Draw.reset();
        Draw.z(z);
    }

    /** 只跟随目标 (不发射): hyperSpeedPattern 的跑道等待阶段使用 */
    public void updateFollowing(){
        float range = pattern != null && pattern.followTarget ? pattern.followRange : minRange;
        vec.trns(target.angleTo(unit), range).add(target).sub(unit).scl(0.05f * Time.delta);
        unit.move(vec);
        unit.rotation = unit.angleTo(target);
    }

    @Override
    public void updateUnit(){
        // ★ 目标失效 (死亡/离开) 时只清空目标, 下一帧重新索敌, 不自杀
        if(target != null && Units.invalidateTarget(target, unit.team, unit.x, unit.y)){
            target = null;
        }
        if(target == null){
            // ★ 从所有玩家中选最近的 (原版 Utils.bestEntity(Groups.player, ...))
            Player best = null;
            float bestDst = Float.MAX_VALUE;
            for(Player p : Groups.player){
                Unit pu = p.unit();
                if(pu != null && pu.isValid()){
                    float dst = unit.dst(pu);
                    if(dst < bestDst){
                        bestDst = dst;
                        best = p;
                    }
                }
            }
            target = best != null ? best.unit() : null;
        }

        // ★ 移动 (原版: waitTime>0 或 followTarget 时贴近目标)
        if((waitTime > 0f || (pattern != null && pattern.followTarget)) && target != null){
            float speed = patternTime <= 0f ? Mathf.clamp(waitTime / 40f) : 1f;
            float range = pattern != null && pattern.followTarget ? pattern.followRange : minRange;
            vec.trns(target.angleTo(unit), range).add(target).sub(unit).scl(0.05f * speed * Time.delta);
            unit.move(vec);
            unit.rotation = unit.angleTo(target);
            if(patternTime <= 0f){
                vec.set(x, y).lerpDelta(target.x, target.y, 0.1f * speed);
                x = vec.x;
                y = vec.y;
            }
        }

        // ★ 模式运行 (原版: 有目标且等待结束时)
        if(target != null && waitTime <= 0f){
            if(pattern == null){
                reset();
            }
            if(pattern != null){
                if(pattern.lootAtTarget){
                    unit.rotation = unit.angleTo(target);
                }
                pattern.update(this);

                // 延迟回调队列 (原版 delays)
                delays.removeAll(k -> {
                    k.delay -= Time.delta;
                    boolean done = k.delay <= 0f;
                    if(done){
                        k.run.run();
                    }
                    return done;
                });

                patternTime -= Time.delta;
                if(patternTime <= 0f){
                    waitTime = pattern.waitTime;
                    pattern.end(this);
                    pattern = null;
                    patternData = null;

                    // ★ 波次通过提示 (项目既有功能, 全局持久化最高记录)
                    int highestWave = Core.settings.getInt("kami-highest-wave", 0);
                    if(stages > highestWave){
                        highestWave = stages;
                        Core.settings.put("kami-highest-wave", highestWave);
                        Core.settings.forceSave();
                    }
                    Call.announce("第 " + stages + " 波已通过！\n最高记录: " + highestWave);
                }
            }
        }

        waitTime = Math.max(0f, waitTime - Time.delta);
        updateBarrier();
    }

    /** 自模式开始经过的时长 */
    public float pTime(){
        return pattern == null ? 0f : pattern.time - patternTime;
    }

    /** 抽取下一个模式: 洗牌 + 按 priority 排序, 并初始化其运行时数据 */
    void reset(){
        Arrays.fill(reloads, 0f);
        delays.clear();

        if(patterns.isEmpty()){
            Arrays.fill(limit, 0);
            for(KamiPattern p : KamiPattern.all){
                if(allPatterns || p.type.able.get(this)) patterns.add(p);
            }
            patterns.shuffle();
            if(!allPatterns) patterns.removeAll(p -> limit[p.type.ordinal()]++ >= p.type.limit);
            patterns.sort((a, b) -> Integer.compare(a.type.priority, b.type.priority));
        }

        pattern = patterns.first();
        patterns.remove(0);
        if(pattern.data != null) patternData = pattern.data.get();
        pattern.init(this);
        patternTime = pattern.time;

        stages++;
    }

    /** 把离开屏障范围的玩家拉回边界 (原版 updateBarrier) */
    void updateBarrier(){
        for(Player p : Groups.player){
            Unit u = p.unit();
            if(u != null && u.isValid() && !Mathf.within(x, y, u.x, u.y, barrierRange)){
                vec.set(u.x - x, u.y - y).setLength(barrierRange).add(x, y);
                u.set(vec);
            }
        }
    }

    /** 连发: 每隔 burstSpacing 发一轮, 共 bursts 轮; 一轮开始时先执行 begin */
    public boolean burst(int i, float time, int bursts, float burstSpacing, Runnable begin){
        boolean s = shoot(i, burstSpacing);
        if(s){
            if(reloads[i + 1] <= 0f){
                begin.run();
            }
            reloads[i + 1] += 1f;
            if(reloads[i + 1] >= bursts){
                reloads[i] += time;
                reloads[i + 1] = 0f;
            }
        }
        return s;
    }

    /** 连发 (无 begin 回调) */
    public boolean burst(int i, float time, int bursts, float burstSpacing){
        boolean s = shoot(i, burstSpacing);
        if(s){
            reloads[i + 1] += 1f;
            if(reloads[i + 1] >= bursts){
                reloads[i] += time;
            }
        }
        return s;
    }

    /** 射击计时器: 冷却结束时返回 true 并开始计时 */
    public boolean shoot(int i, float time){
        boolean s = reloads[i] <= 0f;
        if(s) reloads[i] += time;
        reloads[i] -= Time.delta;
        return s;
    }

    /** 单位到目标的朝向角 */
    public float targetAngle(){
        return unit.angleTo(target);
    }

    /** 延迟执行 (对应原版 run(delay, run)) */
    public void run(float delay, Runnable run){
        if(delay <= 0f){
            run.run();
            return;
        }
        KamiDelay k = new KamiDelay();
        k.delay = delay;
        k.run = run;
        delays.add(k);
    }

    @Override
    public void unit(Unit unit){
        this.unit = unit;
        x = unit.x;
        y = unit.y;
        rand.setSeed(unit.id * 9999L);
    }

    @Override
    public Unit unit(){
        return unit;
    }

    /** 延迟回调条目 */
    static class KamiDelay {
        Runnable run;
        float delay;
    }
}
