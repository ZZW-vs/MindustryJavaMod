package zzw.content.units.kami;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import zzw.content.units.KamiAI;
import zzw.content.units.types.RainbowUnitType;

/**
 * kami 弹幕模式基类 (PU132 {@code unity/ai/kami/KamiPattern.java} 的 1:1 移植)。
 *
 * <p>一个模式有持续时间 {@link #time}、结束后等待 {@link #waitTime}, 以及
 * {@link #update(KamiAI)} / {@link #init(KamiAI)} / {@link #end(KamiAI)} /
 * {@link #draw(KamiAI)} 生命周期。全部模式实例注册到静态 {@link #all},
 * 由 {@link KamiAI#reset()} 洗牌并按 {@link PatternType#priority} 排序后逐个抽取。</p>
 */
public class KamiPattern {
    /** 所有已注册模式 */
    public static final Seq<KamiPattern> all = new Seq<>();
    public int id;

    /** 模式总时长 (tick) */
    public float time;
    /** 模式结束后的等待时长 (tick) */
    public float waitTime;
    /** followTarget=true 时保持的跟随距离 */
    public float followRange = KamiAI.minRange;
    /** 是否每帧朝向目标 */
    public boolean lootAtTarget = true;
    /** 是否跟随目标移动 (默认 false) */
    public boolean followTarget;

    /** 该模式私有的运行时数据工厂 */
    public Prov<PatternData> data;
    public PatternType type = PatternType.basic;

    public KamiPattern(float time){
        this(time, 3f * 60f);
    }

    public KamiPattern(float time, float waitTime){
        this.time = time;
        this.waitTime = waitTime;
        id = all.size;
        all.add(this);
    }

    public void update(KamiAI ai){
    }

    public void init(KamiAI ai){
    }

    public void end(KamiAI ai){
    }

    /**
     * 通用绘制 (PU132 原版)。bossBasic 类型的模式会在本体周围绘制 3 个旋转的
     * trail 贴图作为出场/进行中的视觉提示。
     */
    public void draw(KamiAI ai){
        if(type == PatternType.bossBasic){
            float z = Draw.z();
            RainbowUnitType rt = (RainbowUnitType) ai.unit.type;
            TextureRegion r = rt.trailRegion;
            float fin = Mathf.clamp(ai.pTime() / 80f);
            Draw.z(z - 0.01f);
            for(int i = 0; i < 3; i++){
                float ang = i * 360f / 3f + (ai.patternTime * 2f);
                Draw.color(Tmp.c1.set(Color.red).shiftHue(Time.time + ang));
                Vec2 v = Tmp.v1.trns(ang, fin * 45f).add(ai.unit.x, ai.unit.y);
                Draw.rect(r, v.x, v.y, ai.unit.rotation - 90f);
            }
            Draw.z(z);
        }
    }

    @Override
    public String toString(){
        return "KamiPattern: " + id + " priority: " + type.priority;
    }

    /**
     * 模式分类 (PU132 原版)。
     *
     * <p>{@link #able} 判定当前是否允许出现, {@link #limit} 为每次洗牌最多出现次数,
     * {@link #priority} 为排序优先级 (越小越先被抽取)。</p>
     */
    public enum PatternType {
        permanent(ai -> true),
        basic(ai -> {
            int s = ai.stages;
            int ms = 10;
            float chance = 1f - ((s - ms) / 5f);
            return s < ms || (chance > 0f && ai.rand.chance(chance));
        }),
        bossBasic(ai -> ai.stages > 5 && ai.stages % 3 == 2, 1, 2),
        advance(ai -> ai.stages > 10, 5, 1);

        public final Boolf<KamiAI> able;
        public final int limit;
        public final int priority;

        PatternType(Boolf<KamiAI> able){
            this(able, 10, 0);
        }

        PatternType(Boolf<KamiAI> able, int limit, int priority){
            this.able = able;
            this.limit = limit;
            this.priority = priority;
        }
    }

    /** 模式私有运行时数据基类 */
    public static class PatternData {
    }

    /**
     * 多阶段模式 (PU132 {@code StagePattern})。
     *
     * <p>由若干 {@link Stage} 组成, 每个阶段有自己的时长、循环次数、每帧逻辑与进入时的
     * 初始化。构造时若传入负的 {@code time}, 则用各阶段时长之自动计算总时长。</p>
     */
    public static class StagePattern extends KamiPattern {
        Stage[] stages;

        public StagePattern(float time, Stage... stages){
            this(time, PatternType.basic, stages);
        }

        public StagePattern(float time, PatternType type, Stage... stages){
            super(time);
            this.stages = stages;
            this.type = type;
            data = StageData::new;
            if(time < 0f){
                float t = 0f;
                for(Stage s : stages){
                    t += s.time * (s.loop + 1);
                }
                this.time = t * -time;
            }
        }

        @Override
        public void init(KamiAI ai){
            initAlt(ai, (StageData) ai.patternData);
        }

        void initAlt(KamiAI ai, StageData d){
            Stage s = stages[d.index];
            if(s.init != null){
                s.init.get(ai, d);
            }
        }

        @Override
        public void update(KamiAI ai){
            StageData d = (StageData) ai.patternData;
            Stage s = stages[d.index];
            s.cons.get(ai, d);
            d.time += Time.delta;
            if(d.time > s.time){
                d.time = 0f;
                d.loops += 1;
                initAlt(ai, d);
            }
            if(d.loops > s.loop){
                d.loops = 0;
                d.index = (short) ((d.index + 1) % stages.length);
                initAlt(ai, d);
            }
        }

        /** 单个阶段 */
        public static class Stage {
            float time;
            short loop;
            Cons2<KamiAI, StageData> cons, init;

            public Stage(float time, Cons2<KamiAI, StageData> cons){
                this(time, 0, cons, null);
            }

            public Stage(float time, Cons2<KamiAI, StageData> cons, Cons2<KamiAI, StageData> init){
                this(time, 0, cons, init);
            }

            public Stage(float time, int loop, Cons2<KamiAI, StageData> cons, Cons2<KamiAI, StageData> init){
                this.time = time;
                this.loop = (short) loop;
                this.cons = cons;
                this.init = init;
            }
        }

        /** 阶段运行时数据 */
        public static class StageData extends PatternData {
            public short loops, index;
            public float time;
        }
    }
}
