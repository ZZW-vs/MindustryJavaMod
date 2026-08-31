package zzw.content.units.effects;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.math.Rand;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.pooling.Pools;
import mindustry.content.Fx;
import mindustry.graphics.Pal;
import mindustry.entities.Effect;
import mindustry.gen.EffectState;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import zzw.content.graphics.UnityPal;
import zzw.content.units.graphics.MultiTrail.TrailHold;
import zzw.content.units.graphics.TexturedTrail;
import zzw.content.units.graphics.Trails;
import zzw.content.units.util.UnityUtils;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.circle;
import static arc.graphics.g2d.Lines.lineAngle;
import static arc.graphics.g2d.Lines.stroke;
import static arc.math.Angles.randLenVectors;
import static mindustry.Vars.state;

/**
 * 充能前摇特效 (PU132 unity.content.effects.ChargeFx 移植)。
 *
 * <p>包含 Monolith 系列武器 / End 系列 Boss 的充能特效,
 * 大量使用 {@link ParentEffect} 让特效挂在发射者身上跟随旋转。</p>
 *
 * <p>★ v132 → v155 适配要点:</p>
 * <ul>
 *   <li>{@code unity.util.Utils / MathU} 的工具方法并入
 *       {@link UnityUtils} (seedr / with / slope / randLenVectors);</li>
 *   <li>{@code unity.graphics.UnityPal} → {@link UnityPal};</li>
 *   <li>{@code unity.graphics.MultiTrail / TexturedTrail / Trails} →
 *       zzw.content.units.graphics 包下同名类;</li>
 *   <li>{@code ParentEffect / CustomStateEffect} 随本类一并移植到本包。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class ChargeFx{
    private static final Color tmpCol = new Color();

    public static Effect

    /**
     * 小型绿色激光充能 (40f): 治疗色圆环从 50 半径收缩到 0。
     */
    greenLaserChargeSmallParent = new ParentEffect(40f, 100f, e -> {
        color(Pal.heal);
        stroke(e.fin() * 2f);
        Lines.circle(e.x, e.y, e.fout() * 50f);
    }),

    /**
     * 大型绿色激光充能 (80f):
     * 收缩圆环 + 渐大实心圆 + 20 个随机辐射粒子 (带光照), 最后白芯收束。
     */
    greenLaserChargeParent = new ParentEffect(80f, 100f, e -> {
        color(Pal.heal);
        stroke(e.fin() * 2f);
        Lines.circle(e.x, e.y, 4f + e.fout() * 100f);

        Fill.circle(e.x, e.y, e.fin() * 20);

        randLenVectors(e.id, 20, 40f * e.fout(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fin() * 5f);
            Drawf.light(e.x + x, e.y + y, e.fin() * 15f, Pal.heal, 0.7f);
        });

        color();

        Fill.circle(e.x, e.y, e.fin() * 10);
        Drawf.light(e.x, e.y, e.fin() * 20f, Pal.heal, 0.7f);
    }),

    /**
     * Sagittarius 充能 (2f*60f):
     * <ol>
     *   <li>中心渐大的治疗色实心圆;</li>
     *   <li>15 个粒子按 {@link UnityUtils#randLenVectors} 以三次方曲线
     *       (f³×90) 向外辐射, 个体进度按 1-fin 反向收小 —— 形成 "吸入" 感;</li>
     *   <li>后半程 (fin>0.4) 两侧 ±90° 生成一对渐长三角, 预示箭矢形状。</li>
     * </ol>
     */
    sagittariusCharge = new Effect(2f * 60f, e -> {
        float size = e.fin() * 15f;
        color(Pal.heal);
        Fill.circle(e.x, e.y, size);
        UnityUtils.randLenVectors(e.id * 9999L, 15, e.fout(), 0.5f, 0.6f, 0.2f,
        f -> f * f * f * 90f, (ex, ey, fin) -> {
            float fout = 1f - fin;
            if(fin < 0.9999) Fill.circle(ex + e.x, ey + e.y, fout * 11f);
        });
        float f = Mathf.curve(e.fin(), 0.4f);

        if(f > 0.0001f){
            for(int s : Mathf.signs){
                Drawf.tri(e.x, e.y, Interp.pow2Out.apply(f) * 15f * 1.22f, f * f * 80f, e.rotation + 90f * s);
            }
        }

        color(Color.white);
        Fill.circle(e.x, e.y, size * 0.5f);
    }).followParent(true).rotWithParent(true),

    /**
     * Tenmeikiri 充能粒子 (40f): 2 条随机短线 (长度 10~100, 5 参版 randLenVectors),
     * 颜色 scarColor→endColor 渐变, 线段随 fout 外移并按 fslope 缩放长度。
     */
    tenmeikiriChargeEffect = new ParentEffect(40f, e -> {
        randLenVectors(e.id, 2, 10f, 90f, (x, y) -> {
            float angle = Mathf.angle(x, y);
            color(UnityPal.scarColor, UnityPal.endColor, e.fin());
            Lines.stroke(1.5f);
            Lines.lineAngleCenter(e.x + (x * e.fout()), e.y + (y * e.fout()), angle, e.fslope() * 13f);
        });
    }),

    /**
     * Tenmeikiri 充能主体 (158f): 3 层 (scarColor/endColor/white) 十字三角。
     *
     * <p>每层宽度按 {@code clamp(time/80)} 缓入并叠加 absin 脉动,
     * 长度随 fin 增长; 侧向 ±90° 两个短三角 + 正前方一个长三角
     * (×1.25) 构成充能剑形。</p>
     */
    tenmeikiriChargeBegin = new ParentEffect(158f, e -> {
        Color[] colors = {UnityPal.scarColor, UnityPal.endColor, Color.white};
        for(int ii = 0; ii < 3; ii++){
            float s = (3 - ii) / 3f;
            float width = Mathf.clamp(e.time / 80f) * (20f + Mathf.absin(Time.time + (ii * 1.4f), 1.1f, 7f)) * s;
            float length = e.fin() * (100f + Mathf.absin(Time.time + (ii * 1.4f), 1.1f, 11f)) * s;
            color(colors[ii]);
            for(int i : Mathf.signs){
                float rotation = e.rotation + (i * 90f);
                Drawf.tri(e.x, e.y, width, length * 0.5f, rotation);
            }
            Drawf.tri(e.x, e.y, width, length * 1.25f, e.rotation);
        }
    }),

    /**
     * Devourer 充能 (41f): 3 层颜色的闪光圆环。
     *
     * <p>每层: 半径 scl×fin 递减, 尖刺高度按 fslope×scl×1.5 脉冲,
     * 通过 {@link UnityDrawf#shiningCircle} 的周期随机尖刺实现
     * "荆棘状充能光环"。</p>
     */
    devourerChargeEffect = new ParentEffect(41f, e -> {
        Color[] colors = {UnityPal.scarColor, UnityPal.endColor, Color.white};

        for(int i = 0; i < colors.length; i++){
            color(colors[i]);
            float scl = (colors.length - (i / 1.25f)) * (17f / colors.length);
            float width = (35f / (1f + (i / Mathf.pi))) * e.fin();
            float spikeIn = e.fslope() * scl * 1.5f;

            UnityDrawf.shiningCircle(e.id * 241, Time.time + (i * 3f), e.x, e.y, scl * e.fin(), 9, 12f, width, spikeIn);
        }
    }),

    /**
     * Oppression 终极激光充能 (5f*60f, 裁剪 5060) —— 全特效最复杂的一段。
     *
     * <p>时间轴 (帧): 0~150 内圈粒子渐入, 60 后方块粒子满幅,
     * 145+ 尖刺菱形与中心菱形登场, 3.75f*60 后主束与沿线菱形链爆发。</p>
     *
     * <p>渲染分 6 段:</p>
     * <ol>
     *   <li><b>11 个菱形辐射粒子</b>: 每个粒子有自己的 curve 窗口 (off2 错开),
     *       半径按 pow3In(cfo) 由外向内倒吸, 长度 pow2Out(slope) 先伸后缩;</li>
     *   <li><b>尖刺菱形 + 中心菱形</b> (145f+): 以 (time%周期) 为相位的确定性随机,
     *       timeSeed 每周期刷新保证形状变化; 中心菱形长 (160+absin) 随 fin4 pow2 展开;</li>
     *   <li><b>35 个方块粒子</b>: 双径向合成 —— 环绕半径 trns 随 trv 收拢 +
     *       轴向偏移 trns2 随 fo 淡出, 颜色随进度黑化;</li>
     *   <li><b>22 条沿线短线</b>: 错峰 curve 窗口 + pow2 衰减位移,
     *       制造 "束流边缘絮状电弧";</li>
     *   <li><b>主束线</b>: 长度 pow3(clamp(time/20))×2530, 末段 30f 混入黑色;</li>
     *   <li><b>沿线菱形链 / 方块粒子</b> (3.75f*60f 前后两种形态):
     *       9 段×9 粒子的分批渐入 (partm 为段内余量系数)。</li>
     * </ol>
     */
    oppressionCharge = new Effect(5f * 60f, 2530f * 2f, e -> {
        Rand r = UnityUtils.seedr, r2 = UnityUtils.seedr2, r3 = UnityUtils.seedr3;
        r.setSeed(e.id * 9999L);

        float off = 140f / e.lifetime;
        float off2 = 70f / e.lifetime;

        float fin1 = e.time >= 150f ? 1f : e.time / 150f;
        float fin2 = e.time >= 60f ? 1f : e.time / 60f;

        float time = Time.time;
        color(UnityPal.scarColor);
        for(int i = 0; i < 11; i++){
            float f = (i / 10f) * off2;
            float cf = Mathf.curve(e.fin(), f, (1f - off2) + f);
            float cfo = 1f - cf;
            float rot = e.rotation + (r.nextFloat() - r.nextFloat()) * 6f;
            float len = r.random(75f, 210f) * Interp.pow2Out.apply(UnityUtils.slope(cf, 0.75f));
            float wid = (len / 15f) * cf * 2f * r.random(0.8f, 1.2f);
            float trns = r.random(2530f - len * 2f) + len;
            if(cf <= 0f || cf >= 1f) continue;
            Vec2 v = Tmp.v1.trns(rot, trns * Interp.pow3In.apply(cfo)).add(e.x, e.y);
            UnityDrawf.diamond(v.x + Mathf.range(4f) * cf, v.y + Mathf.range(4f) * cf, wid, len, rot);
        }
        if(e.time > 145f){
            float fin3 = e.time - 145f >= 140f ? 1f : (e.time - 145f) / 140f;
            r3.setSeed(e.id * 9999L + 781);
            float spikef = Mathf.clamp((e.time - 145f) / 20f, 0f, 13f);
            int spikei = Mathf.ceil(spikef);
            for(int i = 0; i < spikei; i++){
                float spikem = spikef >= 13f || i < spikei - 1 ? 1f : (spikef % 1f);
                float d = r3.random(25f, 45f);
                float timeOffset = r3.random(d);
                float f = ((time + timeOffset) % d) / d;
                float fo = 1f - f;
                int timeSeed = Mathf.floor((time + timeOffset) / d) + r3.nextInt();
                float offs = 0.33f;
                float lt = f < offs ? Interp.pow2In.apply(f / offs) : 1f - (f - offs) / (1f - offs);

                r2.setSeed(timeSeed);
                float rot = r2.random(360f) + r2.range(5f) * f;
                float trns = (r2.random(8f, 13f) + r2.random(5f, 10f) * e.fin());
                float w = r2.random(17f, 30f) + r2.random(8f) * fin3 * Mathf.curve(fo, 0f, 0.5f);
                float l = r2.random(75f, 180f) * lt * spikem;
                Tmp.v1.trns(rot, trns).add(e.x, e.y);
                UnityDrawf.diamond(Tmp.v1.x, Tmp.v1.y, w, l, 0.4f, rot);
            }
            float fin4 = (e.time - 145f) / (e.lifetime - 145f);
            UnityDrawf.diamond(e.x, e.y, 17f * Interp.pow2Out.apply(Mathf.curve(fin4, 0f, 0.2f)), (160f + Mathf.absin(8f, 6f)) * Interp.pow2.apply(fin4), e.rotation + 90f);
        }
        for(int i = 0; i < 35; i++){
            float d = r.random(10f, 30f);
            float timeOffset = r.random(d);
            int timeSeed = Mathf.floor((time + timeOffset) / d) + r.nextInt();
            float f = ((time + timeOffset) % d) / d;
            float fo = 1f - f;
            float trv = 1f - (f < 0.75f ? Interp.pow3Out.apply(f / 0.75f) * 0.75f : Interp.pow2In.apply((f - 0.75f) / 0.25f) * 0.25f + 0.75f);

            r2.setSeed(timeSeed);
            float rot = r2.random(360f);
            float trns = (r2.random(15f, 65f) + r2.random(15f, 75f) * e.fin()) * trv;
            float trns2 = r2.random(200f, 900f) * fo * (1f - fin1);
            float rad = (r2.random(10f, 22f) + 11f * e.fin()) * fin2 * Interp.pow2Out.apply(UnityUtils.slope(f, 0.75f));
            if(trns2 > 0){
                Tmp.v1.trns(e.rotation + r2.range(4f), trns2).add(e.x, e.y);
            }else{
                Tmp.v1.set(e.x, e.y);
            }
            color(UnityPal.scarColor, Color.black, Mathf.curve(f, 0.35f, 0.75f));
            Vec2 v = Tmp.v2.trns(rot, trns).add(Tmp.v1);
            Fill.square(v.x, v.y, rad, 45f);
        }

        color(UnityPal.scarColor);
        for(int i = 0; i < 22; i++){
            float f = (i / 21f) * off;
            float cf = Mathf.curve(e.fin(), f, (1f - off) + f);
            float cfo = 1f - cf;
            float rot = e.rotation + (r.nextFloat() - r.nextFloat()) * 20f;
            float len = r.random(300f, 800f);
            float trns = r.random(2530f - len) * cfo * cfo;
            if(cf <= 0f || cf >= 1f) continue;
            Vec2 v = Tmp.v1.trns(rot, trns).add(e.x, e.y);
            stroke(3f);
            lineAngle(v.x, v.y, rot, len * Mathf.slope(cfo * cfo), false);
        }
        float t = e.time < 3.75f * 60f ? 0f : Mathf.clamp((e.time - 3.75f * 60f) / 30f);
        float length = Interp.pow3.apply(Mathf.clamp(e.time / 20f)) * 2530f;
        color(UnityPal.scarColor, Color.black, t);
        stroke(5f);
        lineAngle(e.x, e.y, e.rotation, length);
        if(t > 0f){
            r3.setSeed(e.id * 9999L + 613);
            float dr = 3.75f * 60f;
            float partf = Mathf.clamp((e.time - dr) / (e.lifetime - dr)) * 9f;
            int parti = Mathf.ceil(partf);

            for(int j = 0; j < parti; j++){
                float partm = partf >= 9f || j < parti - 1 ? 1f : (partf % 1f);

                for(int i = 0; i < 9; i++){
                    float d = r3.random(7f, 11f);
                    float timeOffset = r3.random(d);
                    int timeSeed = Mathf.floor((time + timeOffset) / d) + r3.nextInt();
                    float f = ((time + timeOffset) % d) / d;

                    r2.setSeed(timeSeed);
                    float l = r2.random(100f, 200f) * Interp.pow2Out.apply(Mathf.curve(f, 0f, 0.5f)) * partm;
                    float w = r2.random(9f, 19f) * UnityUtils.slope(f, 0.8f) * partm * t;

                    float trns = r2.random(2530f - l * 2f) + l + r2.range(3f) * f;
                    float of = (r2.nextFloat() - r2.nextFloat()) * 35f * Interp.pow3Out.apply(1f - f) * (0.5f + t * 0.5f);
                    //float scl = r2.random(5f, 10f) * t * partm * UnityUtils.slope(f, 0.8f);
                    Tmp.v1.trns(e.rotation, trns, of).add(e.x, e.y);
                    color(UnityPal.scarColor, Color.black, Mathf.curve(f, 0.2f, 0.75f));
                    UnityDrawf.diamond(Tmp.v1.x, Tmp.v1.y, w, l, e.rotation);
                    //Fill.square(Tmp.v1.x, Tmp.v1.y, scl, 45f);
                }
            }
        }
        if(e.time < 3.75f * 60f){
            float t2 = Mathf.clamp((3.75f * 60f - e.time) / 30f);

            r3.setSeed(e.id * 9999L + 613);
            color(UnityPal.scarColor);
            for(int i = 0; i < 30; i++){
                float d = r3.random(18f, 24f);
                float timeOffset = r3.random(d);
                int timeSeed = Mathf.floor((time + timeOffset) / d) + r3.nextInt();
                float f = ((time + timeOffset) % d) / d;

                r2.setSeed(timeSeed);
                float trns = r2.random(length) + r2.range(2f) * f;
                float of = (r2.nextFloat() - r2.nextFloat()) * 65f * Interp.pow3In.apply(f) * (0.5f + t2 * 0.5f);
                float scl = r2.random(3f, 8f) * t2 * UnityUtils.slope(f, 0.25f);
                Tmp.v1.trns(e.rotation, trns, of).add(e.x, e.y);
                Fill.square(Tmp.v1.x, Tmp.v1.y, scl, 45f);
            }
        }
    }).followParent(true).rotWithParent(true),

    /**
     * W-Boson 充能起始 (38f): lightEffect→lancerLaser 渐变圆 + 白芯。
     */
    wBosonChargeBeginEffect = new Effect(38f, e -> {
        color(UnityPal.lightEffect, Pal.lancerLaser, e.fin());
        Fill.circle(e.x, e.y, 3f + e.fin() * 6f);
        color(Color.white);
        Fill.circle(e.x, e.y, 1.75f + e.fin() * 5.75f);
    }),

    /**
     * W-Boson 充能粒子 (24f): 2 个粒子按 (1-finpow)×50 半径收缩,
     * 线段长度 sin(finpow×3) 脉动。
     */
    wBosonChargeEffect = new Effect(24f, e -> {
        color(UnityPal.lightEffect, Pal.lancerLaser, e.fin());
        stroke(1.5f);

        randLenVectors(e.id, 2, (1f - e.finpow()) * 50f, (x, y) -> {
            float a = Mathf.angle(x, y);
            lineAngle(e.x + x, e.y + y, a, Mathf.sin(e.finpow() * 3f, 1f, 8f) + 1.5f);
            Fill.circle(e.x + x, e.y + y, 2f + e.fin() * 1.75f);
        });
    }),

    /**
     * Ephmeron 充能 (80f): 双层 (lancerLaser + white) 闪光圆环,
     * 尖刺高度 3f/2.5f×fin 渐长。
     */
    ephmeronCharge = new Effect(80f, e -> {
        color(Pal.lancerLaser);
        UnityDrawf.shiningCircle(e.id, Time.time, e.x, e.y, e.fin() * 9.5f, 6, 25f, 20f, 3f * e.fin());
        color(Color.white);
        UnityDrawf.shiningCircle(e.id, Time.time, e.x, e.y, e.fin() * 7.5f, 6, 25f, 20f, 2.5f * e.fin());
    }),

    /**
     * Tendence 充能 (40f) —— 带内部状态的特效。
     *
     * <p>状态: 12 条灵魂拖尾 (Trails.soul(26)), 每条有自己的极坐标偏移
     * (角度随机, 半径 24~64) 与宽度 1~2。渲染步骤:</p>
     * <ol>
     *   <li>先画 monolith→monolithLight 渐变的 8 个吸入粒子;</li>
     *   <li>每条拖尾的锚点 = 偏移×foutpowdown (向中心收拢) +
     *       垂直方向正弦摆动 (sin(width×2.6, width×8×fslope)), 逐帧 update;</li>
     *   <li>颜色 tmpCol 按 finpowdown 向 monolithLight 过渡, 画拖尾主体与端帽;</li>
     *   <li>remove() 时把所有拖尾 copy 一份交给 Fx.trailFade 渐隐,
     *       避免特效结束时拖尾瞬间消失 (State 子类负责此清理)。</li>
     * </ol>
     */
    tendenceCharge = new CustomStateEffect(() -> {
        class State extends EffectState{
            @Override
            public void remove(){
                if(data instanceof TrailHold[] data) for(TrailHold trail : data) Fx.trailFade.at(x, y, trail.width, UnityPal.monolithLight, trail.trail.copy());
                super.remove();
            }
        } return Pools.obtain(State.class, State::new);
    }, 40f, e -> {
        if(!(e.data instanceof TrailHold[] data)) return;

        color(UnityPal.monolith, UnityPal.monolithLight, e.fin());
        randLenVectors(e.id, 8, 8f + e.foutpow() * 32f, (x, y) ->
            Fill.circle(e.x + x, e.y + y, 0.5f + e.fin() * 2.5f)
        );

        color();
        for(TrailHold hold : data){
            Tmp.v1.set(hold.x, hold.y);
            Tmp.v2.trns(Tmp.v1.angle() - 90f, Mathf.sin(hold.width * 2.6f, hold.width * 8f * Interp.pow2Out.apply(e.fslope())));
            Tmp.v1.scl(e.foutpowdown()).add(Tmp.v2).add(e.x, e.y);

            float w = hold.width * e.fin();
            if(!state.isPaused()) hold.trail.update(Tmp.v1.x, Tmp.v1.y, w);

            tmpCol.set(UnityPal.monolith).lerp(UnityPal.monolithLight, e.finpowdown());
            hold.trail.drawCap(tmpCol, w);
            hold.trail.draw(tmpCol, w);
        }

        stroke(Mathf.curve(e.fin(), 0.5f) * 1.4f, UnityPal.monolithLight);
        circle(e.x, e.y, e.fout() * 64f);
    }){
        @Override
        protected EffectState inst(float x, float y, float rotation, Color color, Object data){
            TrailHold[] trails = new TrailHold[12];
            for(int i = 0; i < trails.length; i++){
                Tmp.v1.trns(Mathf.random(360f), Mathf.random(24f, 64f));
                trails[i] = new TrailHold(UnityUtils.with(Trails.soul(26), t -> {
                    if(t.trails[t.trails.length - 1].trail instanceof TexturedTrail tr){
                        tr.trailChance = 0.1f;
                    }
                }), Tmp.v1.x, Tmp.v1.y, Mathf.random(1f, 2f));
            }

            EffectState state = super.inst(x, y, rotation, color, data);
            state.data = trails;
            return state;
        }
    }.followParent(true);
}
