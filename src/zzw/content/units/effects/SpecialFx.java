package zzw.content.units.effects;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;
import arc.math.Rand;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.gen.Bullet;
import mindustry.gen.EffectState;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import zzw.content.graphics.UnityBlending;
import zzw.content.graphics.UnityPal;
import zzw.content.units.bullets.KamiBulletType;
import zzw.content.units.bullets.VoidFractureBulletType;

import static arc.graphics.g2d.Draw.color;

/**
 * 特殊机制特效 (PU132 unity.content.effects.SpecialFx 移植)。
 *
 * <p>收录带专属机制的特效: kami 弹幕出场、End 拒绝处决演出、
 * 碎裂 / 汽化着色器处决、闪烁连锁闪电、充能转移、时间停止领域、
 * 虚空碎裂余晖、聚能爆破光球。</p>
 *
 * <p>★ v132 → v155 适配要点:</p>
 * <ul>
 *   <li>kami 弹幕: PU132 的 KamiBullet 自定义实体未随本项目移植,
 *       本项目 kami 为普通 {@link Bullet} + {@code float[]{宽, 长, 转向}}
 *       数据 (见 {@link KamiBulletType}), kamiBulletSpawn 相应改读
 *       bullet.data; 出场延迟 {@code delay} 字段已在
 *       {@link KamiBulletType} 补齐 (默认 -1 = 立即出场);</li>
 *   <li>fragmentation / endgameVapourize 依赖的
 *       {@link FragmentationShaderEffect} / {@link VapourizeShaderEffect}
 *       与 {@link UnityShaders} 已随本包移植;</li>
 *   <li>timeStop: PU132 依赖全局 {@code unity.mod.TimeStop} 实体注册
 *       (时停期间冻结特效), 本项目时停为简化按能力实现
 *       (见 zzw.content.units.abilities.TimeStopAbility), 无全局注册表,
 *       故状态供应商退化为普通 {@code EffectState::create} ——
 *       渲染逻辑 (反色 + 正片叠底红圈) 与原版一致;</li>
 *   <li>voidFractureEffect 复用本项目
 *       {@link VoidFractureBulletType.VoidFractureData} 数据类
 *       (与 PU132 SpecialFx.VoidFractureData 字段一致);</li>
 *   <li>{@code unity.graphics.UnityBlending} → {@link UnityBlending}。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class SpecialFx{
    private static final Rand rand = new Rand();

    public static Effect

    /**
     * kami 弹幕出场特效 (30f, 裁剪 300)。
     *
     * <p>PU132 中 kami 子弹创建后有 {@code type.delay} tick 的悬停
     * 出场期 (子弹延迟加入世界), 本特效在该期间播放:
     * 特效寿命重设为 delay, 期间绘制 "外层色相循环光晕 (放大 1+5×fout)
     * + 内层白色核心 (按 fin 展开)" 双层弹体。</p>
     *
     * <p>★ 本项目适配: 宽 / 长从 bullet.data (float[]) 读取,
     * 弹体贴图取 "circle" (与 {@link KamiBulletType#draw} 一致)。</p>
     */
    kamiBulletSpawn = new Effect(30f, 300f, e -> {
        if(!(e.data instanceof Bullet kb)) return;
        if(!(kb.type() instanceof KamiBulletType type)) return;
        if(!(kb.data instanceof float[] dims)) return;

        TextureRegion r = Core.atlas.find("circle");
        // delay<=0 (立即出场) 时按默认寿命 30f 播放, 避免负寿命
        float delay = type.delay > 0f ? type.delay : e.lifetime;
        e.lifetime = delay;
        float width = dims[0], length = dims[1];
        float time = (Time.time / 2f) + (e.time - delay) * 2f;
        float scl = 1f + (e.fout() * 5f);
        // 弹体描边厚度: 按弹体尺寸钳制 + 呼吸脉动
        float st = Mathf.clamp(Math.max(width, length) / 10f + 1.2f, 1.5f, 4f) * (1f + Mathf.absin(time, 10f, 0.33f));
        Tmp.c1.set(Color.red).shiftHue(time).a(e.fin());
        Draw.color(Tmp.c1);
        Draw.rect(r, kb.x, kb.y, ((width * 2f) + st) * scl, ((length * 2f) + st) * scl, kb.rotation());
        Draw.color(Color.white);
        Draw.rect(r, kb.x, kb.y, width * 2f * e.fin(), length * 2f * e.fin(), kb.rotation());
    }),

    /**
     * End 拒绝处决演出 (80f, 裁剪 1200) —— "处决失败, 目标被红线锁住"。
     *
     * <p>步骤:</p>
     * <ol>
     *   <li>前 40f (scaled 子时间轴): 以目标为中心, 7×透明度条随机
     *       放射线 (长度 hs×0.75~1, 内端 f1 / 外端 f2 两次 pow3Out
     *       错峰展开, 制造 "红线锁定框绽开" 效果);</li>
     *   <li>全程: 目标本体图标以 scarColor mixcol 染色 + 微抖动
     *       (±2×fin) 重绘, 透明度随 fout 淡出。</li>
     * </ol>
     */
    endDeny = new Effect(80f, 1200f, e -> {
        if(!(e.data instanceof Unit u)) return;
        Draw.blend(Blending.additive);
        float a = (e.color.a / 2f) + 0.5f;
        e.scaled(40f, s -> {
            Draw.color(UnityPal.scarColor);
            Interp in = Interp.pow3Out;
            float f1 = in.apply(Mathf.curve(s.fin(), 0f, 0.8f)),
            f2 = in.apply(Mathf.curve(s.fin(), 0.2f, 1f)),
            hs = u.hitSize / 2f;
            rand.setSeed(e.id * 99999L);
            for(int i = 0; i < (int)(7f * a); i++){
                float len = (hs * rand.random(0.75f, 1f));
                float r = rand.range(360f), scl = rand.random(0.75f, 1.5f);
                Vec2 v = Tmp.v1.trns(r, len + hs * f1 * scl).add(e.x, e.y),
                v2 = Tmp.v2.trns(r, len + hs * f2 * scl).add(e.x, e.y);
                Lines.stroke(1.5f);
                Lines.line(v.x, v.y, v2.x, v2.y);
            }
        });
        Draw.alpha(e.fout());
        Draw.mixcol(UnityPal.scarColor, a);

        Draw.rect(u.icon(), u.x + Mathf.range(e.fin() * 2f), u.y + Mathf.range(e.fin() * 2f), u.rotation - 90f);

        Draw.blend();
        Draw.reset();
    }),

    /**
     * 碎裂消散 (3.5f×60f): 目标碎片剥离 + 灼烧变色 (着色器后处理)。
     */
    fragmentation = new FragmentationShaderEffect(3.5f * 60f),

    /**
     * 快速碎裂消散 (1.5f×60f): 无碎裂/灼烧偏移, 全程立即剥离。
     */
    fragmentationFast = new FragmentationShaderEffect(1.5f * 60f){{
        fragOffset = 0f;
        heatOffset = 0f;
    }},

    /**
     * 终局汽化 (3f×60f, 裁剪 900): 目标化为红色尘埃飘散,
     * 关闭速度推进 (目标原地消散)。
     */
    endgameVapourize = new VapourizeShaderEffect(3f * 60f, 900f).updateVel(false),

    /**
     * 活跃连锁闪电 (20f, 裁剪 300) —— 原版 {@code Fx.chainLightning}
     * 的变体: 以 {@code e.rotation} 作为闪烁频率 (越小闪得越快)。
     *
     * <p>步骤: 起点到终点 (data Position) 按每 6 像素一段连线,
     * 每段中点加 range/2 的随机垂直抖动 (种子随闪烁周期刷新),
     * 一次 beginLine/linePoint.../endLine 折线绘制。</p>
     */
    chainLightningActive = new Effect(20f, 300f, e -> {
        if(!(e.data instanceof Position p)) return;

        float tx = p.getX(), ty = p.getY(), dst = Mathf.dst(e.x, e.y, tx, ty);
        Tmp.v1.set(p).sub(e.x, e.y).nor();

        float normx = Tmp.v1.x, normy = Tmp.v1.y;
        float range = 6f;
        int links = Mathf.ceil(dst / range);
        float spacing = dst / links;

        Lines.stroke(2.5f * e.fout());
        Draw.color(Color.white, e.color, e.fin());

        Lines.beginLine();
        Lines.linePoint(e.x, e.y);

        // 种子随 Time.time/rotation 跳变 → 每个闪烁周期换一套抖动形状
        rand.setSeed(e.id + (long)(Time.time / e.rotation));

        for(int i = 0; i < links; i++){
            float nx, ny;
            if(i == links - 1){
                nx = tx;
                ny = ty;
            }else{
                float len = (i + 1) * spacing;
                Tmp.v1.setToRandomDirection(rand).scl(range/2f);
                nx = e.x + normx * len + Tmp.v1.x;
                ny = e.y + normy * len + Tmp.v1.y;
            }

            Lines.linePoint(nx, ny);
        }

        Lines.endLine();
    }).followParent(false),

    /**
     * 充能转移粒子 (20f): 一个方块粒子从 (e.x, e.y) 沿 pow3 曲线
     * 飞向目标 (data Position), 侧向带 ±10×fslope 的弧线偏移
     * (种子决定方向), 尺寸 4×fslope。
     */
    chargeTransfer = new Effect(20f, e -> {
        if(!(e.data instanceof Position)) return;
        Position to = e.data();
        Tmp.v1.set(e.x, e.y).interpolate(Tmp.v2.set(to), e.fin(), Interp.pow3)
        .add(Tmp.v2.sub(e.x, e.y).nor().rotate90(1).scl(Mathf.randomSeedRange(e.id, 1f) * e.fslope() * 10f));
        float x = Tmp.v1.x, y = Tmp.v1.y, s = e.fslope() * 4f;
        Draw.color(e.color);
        Fill.square(x, y, s, 45f);
    }),

    /**
     * 时间停止领域 (3.5f×60f, 半径 500)。
     *
     * <p>渲染: 半径 pow2(fslope)×500 展开 ——
     * 外圈以 {@link UnityBlending#invert 反色混合} 反转领域内颜色,
     * 内圈再以 {@link UnityBlending#multiply 正片叠底} 叠一层红色。</p>
     *
     * <p>★ 本项目适配: 原版通过全局 TimeStop 注册让特效在时停冻结期
     * 内悬停; 本项目时停为按能力简化实现, 无全局注册表,
     * 状态直接创建 (视觉与原版一致)。</p>
     */
    timeStop = new CustomStateEffect(EffectState::create, 3.5f * 60f, 2f * 500, e -> {
        float s = Interp.pow2.apply(e.fslope()) * 500f;
        Draw.blend(UnityBlending.invert);
        Fill.poly(e.x, e.y, (int)(s / 5) + 24, s);
        Draw.blend(UnityBlending.multiply);
        Draw.color(Color.red);
        Fill.poly(e.x, e.y, (int)(s / 5) + 24, s);
        Draw.blend();
    }),

    /**
     * 虚空碎裂余晖 (30f, 裁剪 700): Phase 2 冲刺结束后, 沿冲刺路径
     * 绘制三层渐细黑色激光 + 两端收头三角, 已命中目标的位置 (spikes)
     * 生成黑色尖刺三角。图层 effect+0.03 (激光类之上)。
     *
     * <p>★ 本项目适配: 复用 {@link VoidFractureBulletType.VoidFractureData}
     * (字段与 PU132 SpecialFx.VoidFractureData 完全一致)。</p>
     */
    voidFractureEffect = new Effect(30f, 700f, e -> {
        if(!(e.data instanceof VoidFractureBulletType.VoidFractureData data)) return;
        float rot = Angles.angle(data.x, data.y, data.x2, data.y2);

        Draw.color(Color.black);
        for(int i = 0; i < 3; i++){
            float f = Mathf.lerp(data.b.width, data.b.widthTo, i / 2f);
            float a = Mathf.lerp(0.25f, 1f, (i / 2f) * (i / 2f));

            Draw.alpha(a);
            Lines.stroke(f * e.fout());
            Lines.line(data.x, data.y, data.x2, data.y2, false);
            Drawf.tri(data.x2, data.y2, f * 1.22f * e.fout(), f * 2f, rot);
            Drawf.tri(data.x, data.y, f * 1.22f * e.fout(), f * 2f, rot + 180f);
        }

        FloatSeq s = data.spikes;
        if(!s.isEmpty()){
            for(int i = 0; i < data.spikes.size; i += 4){
                float x1 = s.get(i), y1 = s.get(i + 1), x2 = s.get(i + 2), y2 = s.get(i + 3);
                Drawf.tri(x1, y1, (data.b.widthTo + 1f) * e.fout(), Mathf.dst(x1, y1, x2, y2) * 2f * Mathf.curve(e.fin(), 0f, 0.2f), Angles.angle(x1, y1, x2, y2));
                Fill.circle(x1, y1, ((data.b.widthTo + 1f) / 1.22f) * e.fout());
            }
        }
    }).layer(Layer.effect + 0.03f),

    /**
     * 聚能爆破光球 (23f, 裁剪 600): 多层颜色同心圆 (每层半径递减
     * widthReduction×i), 外加一层光照。data 需实现
     * {@link PointBlastInterface} 提供颜色表与递减量。
     */
    pointBlastLaserEffect = new Effect(23f, 600f, e -> {
        if(!(e.data instanceof PointBlastInterface data)) return;

        for(int i = 0; i < data.colors().length; i++){
            color(data.colors()[i]);
            Fill.circle(e.x, e.y, (e.rotation - (data.widthReduction() * i)) * e.fout());
        }
        Drawf.light(e.x, e.y, e.rotation * e.fout() * 3f, data.colors()[0], 0.66f);
    });

    /**
     * 聚能爆破数据接口 (PU132 原版): 由子弹类型实现, 提供同心圆
     * 颜色表与每层半径递减量。
     */
    public interface PointBlastInterface{
        /** 同心圆颜色表 (由外到内)。 */
        Color[] colors();
        /** 每层半径递减量。 */
        float widthReduction();
    }
}
