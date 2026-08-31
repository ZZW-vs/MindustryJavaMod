package zzw.content.units.effects;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.pooling.Pools;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.EffectState;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.graphics.Trail;
import zzw.content.graphics.UnityPal;
import zzw.content.units.graphics.Trails;

import static arc.graphics.g2d.Draw.alpha;
import static arc.graphics.g2d.Draw.blend;
import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Draw.z;
import static arc.graphics.g2d.Fill.square;
import static arc.graphics.g2d.Lines.lineAngle;
import static arc.graphics.g2d.Lines.stroke;
import static arc.math.Angles.randLenVectors;
import static mindustry.Vars.state;

/**
 * Monolith 系列单位专属特效 (PU132 HitFx / ShootFx / UnityFx 中被
 * MonolithUnitTypes 直接引用的特效集合, 移植到单一类便于管理)。
 *
 * <p>收录清单 (来源 → 本类字段):</p>
 * <ul>
 *   <li>HitFx.hitMonolithLaser / tendenceHit / monolithHitSmall /
 *       monolithHitBig / soulConcentrateHit — 命中特效;</li>
 *   <li>ShootFx.monumentShoot / soulConcentrateShoot / tendenceShoot /
 *       pedestalShootAdd / phantasmalLaserShoot — 射击特效;</li>
 *   <li>UnityFx.ricochetTrailBig / monolithRingEffect / pylonLaserCharge /
 *       monumentDespawn / monumentTrail — 通用/子弹特效。</li>
 * </ul>
 *
 * <p>★ v132 → v158 适配要点:</p>
 * <ul>
 *   <li>PU132 {@code Float2.x/y(data)} (Long 打包双 float) →
 *       {@code float[]} 数组 (JoiningBulletType 已按此约定传参);</li>
 *   <li>PU132 Quat 旋转 ({@code Utils.q1.set(Vec3.Z, a).mul(Utils.q2.set(Vec3.X, 75f))})
 *       → 本项目 {@link UnityDrawf#panningCircle} 的 Vec3 单轴版本
 *       (绕 X 轴倾斜 75°, 绕 Z 轴的相位旋转由 arcRotation 参数承担);</li>
 *   <li>贴图名去掉 "unity-" 前缀 ("unity-monolith-chain" → "monolith-chain");</li>
 *   <li>特效静态导入 (color/stroke/z/blend/circle/randLenVectors) 与项目其它特效类一致。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class MonolithFx{
    public static Effect

    /**
     * Monolith 激光命中特效 (8f) —— HitFx.hitMonolithLaser。
     *
     * <p>monolithLight → monolithDark 渐变色的细圆环, 半径随进度扩张到 5。</p>
     */
    hitMonolithLaser = new Effect(8f, e -> {
        color(UnityPal.monolithLight, UnityPal.monolithDark, e.finpow());
        stroke(0.2f + e.fout() * 1.3f);
        Lines.circle(e.x, e.y, e.fin() * 5f);
    }),

    /**
     * tendence 命中特效 (52f) —— HitFx.tendenceHit。
     *
     * <p>两侧 (sign ±) 各 3 个粒子, 粒子距离随 pow5Out 进度推进到 32f,
     * 方向以命中朝向 (e.rotation) 为中心 ±30° 扇形展开, 间距随机 16f;
     * 每个粒子是旋转的方块, 旋转速度随 sign 相反形成对称散花。</p>
     */
    tendenceHit = new Effect(52f, e -> {
        color(UnityPal.monolithLight, UnityPal.monolith, UnityPal.monolithDark, e.fin());
        for(int sign : Mathf.signs){
            randLenVectors(e.id + sign, 3, e.fin(Interp.pow5Out) * 32f, e.rotation, 30f, 16f, (x, y) ->
                square(e.x + x, e.y + y, e.foutpowdown() * 2.5f, e.id * 30f + e.finpow() * 90f * sign)
            );
        }
    }),

    /**
     * Monolith 小型命中特效 (14f) —— HitFx.monolithHitSmall。
     *
     * <p>前半段 (scaled 7f): monolith 色 45° 方框描边, 边长扩到 10;
     * 全程: monolithLight 色 5 个小方块粒子散开。</p>
     */
    monolithHitSmall = new Effect(14f, e -> {
        color(UnityPal.monolith);
        e.scaled(7f, s -> {
            stroke(s.fout());
            square(s.x, s.y, 10f * s.fin(), 45f);
        });

        color(UnityPal.monolithLight);
        randLenVectors(e.id, 5, e.fin() * 15f, (x, y) -> square(e.x + x, e.y + y, 2f * e.fout(), 45f));
    }),

    /**
     * Monolith 大型命中特效 (13f) —— HitFx.monolithHitBig。
     *
     * <p>步骤:</p>
     * <ol>
     *   <li>monolithLight 色 10 个 5f 方块粒子, 随进度散开 20f;</li>
     *   <li>monolith 色的加色发光圆盘 (Fill.light), 透明度随 pow3In 淡出,
     *       位于 effect 层之上避免被地面遮挡。</li>
     * </ol>
     */
    monolithHitBig = new Effect(13f, e -> {
        color(UnityPal.monolithLight);
        randLenVectors(e.id, 10, e.fin() * 20f, (x, y) -> square(e.x + x, e.y + y, 5f * e.fout(), 45f));

        Tmp.c1.set(UnityPal.monolith).a(e.fout(Interp.pow3In));

        z(Layer.effect + 1f);
        blend(Blending.additive);
        Fill.light(e.x, e.y, 4, 25f * e.fin(Interp.pow5Out), Color.clear, Tmp.c1);
        blend();
    }),

    /**
     * 灵魂聚合弹命中特效 (30f) —— HitFx.soulConcentrateHit。
     *
     * <p>data 为 {@code float[]{initRad, scl}} (对应 PU132 Float2):
     * initRad 为聚合弹初始半径, scl 为聚合缩放系数, 实际半径 = initRad * scl。</p>
     *
     * <p>绘制: monolithGreen 渐变光球 + 描边圆 + 8×scl 条径向短线,
     * 寿命随 scl 延长 (30f * scl)。</p>
     */
    soulConcentrateHit = new Effect(30f, e -> {
        if(!(e.data instanceof float[] arr) || arr.length < 2) return;
        float initRad = arr[0], scl = arr[1], radius = initRad * scl;

        e.lifetime = 30f * scl;

        color(UnityPal.monolithGreen, e.fout(Interp.pow5In));
        Fill.circle(e.x, e.y, radius + e.fin(Interp.pow10Out) * 6f * scl);

        stroke(e.fout() * 2f * scl, UnityPal.monolithGreenLight);
        Lines.circle(e.x, e.y, radius + e.fin(Interp.pow10Out) * 6f * scl);

        randLenVectors(e.id, (int)(8f * scl), e.finpow() * 15f * scl, (x, y) ->
            lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout(Interp.pow4Out) * 8f * scl)
        );
    }),

    /**
     * monument 电磁炮射击特效 (48f) —— ShootFx.monumentShoot。
     *
     * <p>步骤:</p>
     * <ol>
     *   <li>monolithLight 主三角光束 (10f 宽, 175f 长, 随射击衰减);</li>
     *   <li>两侧 ±45°+30° 偏转的副光束;</li>
     *   <li>15 个方块粒子扇形散开 + 中心 45° 旋转方块 (白芯);</li>
     *   <li>后半段 (scaled 15f): 加色发光圆盘渐隐。</li>
     * </ol>
     */
    monumentShoot = new Effect(48f, e -> {
        color(UnityPal.monolithLight);
        Drawf.tri(e.x, e.y, 10f * e.fout(), 175f - (20f * e.fin()), e.rotation);

        for(int i = 0; i < 2; i++){
            Drawf.tri(e.x, e.y, 10f * e.fout(), 50f, e.rotation + (45f + (e.fin(Interp.pow3Out) * 30f)) * Mathf.signs[i]);
        }

        randLenVectors(e.id, 15, e.fin(Interp.pow2Out) * 80f, e.rotation, 20f, (x, y) ->
            square(e.x + x, e.y + y, 3f * e.fout(), 45f));

        square(e.x, e.y, 5f * e.fout(Interp.pow3Out), e.rotation + 45f);
        color();
        square(e.x, e.y, 2f * e.fout(Interp.pow3Out), e.rotation + 45f);

        e.scaled(15f, s -> {
            z(Layer.effect + 1f);
            blend(Blending.additive);
            Tmp.c1.set(UnityPal.monolithLight).a(s.fout(Interp.pow5In));

            Fill.light(s.x, s.y, 4, 40f * s.fin(Interp.pow5Out), Color.clear, Tmp.c1);
            blend();
        });
    }),

    /**
     * 灵魂聚合弹射击特效 (60f) —— ShootFx.soulConcentrateShoot。
     *
     * <p>双层粒子: 内层 monolithGreen 系列 2 个粒子 (垂直 ±90° 展开),
     * 外层 monolithGreenLight 系列 3 个粒子 (沿射击方向 ±45° 展开),
     * 全部为旋转渐隐的方块, 旋转方向由 sign 对称。</p>
     */
    soulConcentrateShoot = new Effect(60f, e -> {
        int id = e.id;
        for(int sign : Mathf.signs){
            float r = e.foutpow() * 2f;

            color(UnityPal.monolithGreen, UnityPal.monolithGreenDark, e.finpowdown());
            for(int rsign : Mathf.signs){
                randLenVectors(id++, 2, e.finpow() * 20f, e.rotation + sign * 90f, 30f, (x, y) ->
                    Fill.rect(e.x + x, e.y + y, r, r, e.foutpow() * 135f * rsign)
                );
            }
        }

        float r = e.fout(Interp.pow5Out) * 2.4f;

        color(UnityPal.monolithGreenLight, UnityPal.monolithGreen, e.fin(Interp.pow5In));
        for(int rsign : Mathf.signs){
            randLenVectors(id++, 3, e.fin(Interp.pow5Out) * 32f, e.rotation, 45f, (x, y) ->
                Fill.rect(e.x + x, e.y + y, r, r, e.foutpow() * 180f * rsign)
            );
        }
    }),

    /**
     * tendence 能量环射击特效 (32f) —— ShootFx.tendenceShoot。
     *
     * <p>绘制一个 3D 倾斜 (绕 X 轴 75°) 的 "链环" (monolith-chain 贴图)
     * 绕特效中心旋转, 外加 additive 辉光环 (line-shade)。
     * 环半径 9→17 随进度扩张。</p>
     *
     * <p>★ v158 适配: PU132 Quat(Z 轴相位, X 轴 75°) → Vec3.X 轴 75° 单轴,
     * Z 轴相位由 arcRotation 参数承担。</p>
     */
    tendenceShoot = new Effect(32f, e -> {
        TextureRegion reg = Core.atlas.find("monolith-chain");
        float t = e.finpow(), w = reg.width * 0.4f * t, h = reg.height * 0.4f * t, rad = 9f + t * 8f;

        color(UnityPal.monolithLight);
        alpha(e.foutpowdown());

        UnityDrawf.panningCircle(reg,
            e.x, e.y, w, h,
            rad, 360f, e.fin(Interp.pow2Out) * 90f * Mathf.sign(e.id % 2 == 0) + e.id * 30f,
            Vec3.X, 75f, Layer.flyingUnitLow - 0.01f, Layer.flyingUnit
        );

        color(Color.black, UnityPal.monolithDark, 0.67f);
        alpha(e.foutpowdown());

        blend(Blending.additive);
        UnityDrawf.panningCircle(Core.atlas.find("line-shade"),
            e.x, e.y, w + 6f, h + 6f,
            rad, 360f, 0f,
            Vec3.X, 75f, Layer.flyingUnitLow - 0.01f, Layer.flyingUnit
        );

        blend();
    }).layer(Layer.flyingUnit),

    /**
     * pedestal 蓄力霰弹装填特效 (25f) —— ShootFx.pedestalShootAdd。
     *
     * <p>每次装填一发子弹时触发, data 为 5 条灵魂拖尾数组
     * (由 {@link #inst} 覆写注入), 拖尾锚点绕特效中心持续公转,
     * 半径 4 + foutpowdown×16 收拢; remove() 时残留拖尾交给
     * Fx.trailFade 渐隐消失。</p>
     */
    pedestalShootAdd = new CustomStateEffect(() -> {
        class State extends EffectState{
            @Override
            public void remove(){
                if(data instanceof Trail[] data) for(Trail trail : data) Fx.trailFade.at(x, y, 1f, UnityPal.monolithLight, trail.copy());
                super.remove();
            }
        } return Pools.obtain(State.class, State::new);
    }, 25f, e -> {
        if(!(e.data instanceof Trail[] data)) return;

        float initAngle = Mathf.randomSeed(e.id, 360f);
        for(int i = 0; i < data.length; i++){
            Trail trail = data[i];
            if(!state.isPaused()){
                Tmp.v1
                    .trns(initAngle + 360f / data.length * i + Time.time * 6f, 4f + e.foutpowdown() * 16f)
                    .add(e.x, e.y);

                trail.update(Tmp.v1.x, Tmp.v1.y, e.fin() * 1.4f);
            }

            trail.drawCap(UnityPal.monolithLight, 1f);
            trail.draw(UnityPal.monolithLight, 1f);
        }

        color(UnityPal.monolithDark, UnityPal.monolith, e.fin());
        randLenVectors(e.id + 1, 3, e.foutpow() * 8f, 360f, 0f, 4f, (x, y) ->
            Fill.circle(e.x + x, e.y + y, 0.4f + e.fin() * 1.6f)
        );
    }){
        @Override
        protected EffectState inst(float x, float y, float rotation, Color color, Object data){
            Trail[] trails = new Trail[5];
            for(int i = 0; i < trails.length; i++){
                trails[i] = Trails.soul(24);
            }

            EffectState state = super.inst(x, y, rotation, color, data);
            state.data = trails;
            return state;
        }
    }.followParent(true).rotWithParent(true),

    /**
     * 幻影激光射击特效 (36f) —— ShootFx.phantasmalLaserShoot。
     *
     * <p>三个沿射击方向逐层推进的 3D 倾斜圆环 (白色贴图 + Vec3.X 75°),
     * 颜色 monolithLight → monolith → monolithDark, 半径依次 1 / 0.75 / 0.5 倍;
     * data 为 Float 时用作半径 (默认 9f)。</p>
     */
    phantasmalLaserShoot = new Effect(36f, e -> {
        float
            radius = e.data instanceof Float data ? data : 9f,

            fin = e.fin(),
            f1 = Mathf.curve(fin, 0f, 0.76f),
            f2 = Mathf.curve(fin, 0.12f, 0.88f),
            f3 = Mathf.curve(fin, 0.24f, 1f);

        TextureRegion reg = Core.atlas.white();

        stroke(2f);

        color(UnityPal.monolithLight, Interp.pow3Out.apply(f1) * Interp.pow10Out.apply(1f - f1));
        Tmp.v1.trns(e.rotation, -8f + Interp.bounceOut.apply(f1) * 8f - Interp.pow3In.apply(Mathf.curve(f1, 0.67f, 1f)) * 4f).add(e.x, e.y);

        UnityDrawf.panningCircle(reg,
            Tmp.v1.x, Tmp.v1.y, 1f, 1f,
            radius, 360f, 0f, Vec3.X, 75f,
            Layer.bullet - 0.001f, Layer.bullet + 0.001f
        );

        color(UnityPal.monolith, Interp.pow3Out.apply(f2) * Interp.pow10Out.apply(1f - f2));
        Tmp.v1.trns(e.rotation, -2f + Interp.bounceOut.apply(f2) * 8f - Interp.pow3In.apply(Mathf.curve(f2, 0.67f, 1f)) * 4f).add(e.x, e.y);

        UnityDrawf.panningCircle(reg,
            Tmp.v1.x, Tmp.v1.y, 1f, 1f,
            radius * 0.75f, 360f, 0f, Vec3.X, 75f,
            Layer.bullet - 0.001f, Layer.bullet + 0.001f
        );

        color(UnityPal.monolithDark, Interp.pow3Out.apply(f3) * Interp.pow10Out.apply(1f - f3));
        Tmp.v1.trns(e.rotation, 4f + Interp.bounceOut.apply(f3) * 8f - Interp.pow3In.apply(Mathf.curve(f3, 0.67f, 1f)) * 4f).add(e.x, e.y);

        UnityDrawf.panningCircle(reg,
            Tmp.v1.x, Tmp.v1.y, 1f, 1f,
            radius * 0.5f, 360f, 0f, Vec3.X, 75f,
            Layer.bullet - 0.001f, Layer.bullet + 0.001f
        );
    }),

    /**
     * 弹射弹大型尾迹粒子 (20f) —— UnityFx.ricochetTrailBig。
     *
     * <p>6 个 45° 旋转小方块, 颜色 monolith → monolithDark,
     * 散布半径随 fout 收缩到 6.5f。</p>
     */
    ricochetTrailBig = new Effect(20f, e -> randLenVectors(e.id, 6, e.fout() * 6.5f, (x, y) -> {
        float w = 0.3f + e.fout() * 1.7f;

        color(UnityPal.monolith, UnityPal.monolithDark, e.fin());
        Fill.rect(e.x + x, e.y + y, w, w, 45f);
    })),

    /**
     * Monolith 环形波特效 (60f) —— UnityFx.monolithRingEffect。
     *
     * <p>data 为 Float 时作为缩放系数: lancerLaser 色圆环描边,
     * 半径 pow 曲线扩张到 24×scl。</p>
     */
    monolithRingEffect = new Effect(60f, e -> {
        if(e.data instanceof Float data){
            color(Pal.lancerLaser);

            stroke(e.fout() * 3f * data);
            Lines.circle(e.x, e.y, e.finpow() * 24f * data);
        }
    }),

    /**
     * pylon 主激光充能特效 (200f, clip 180f) —— UnityFx.pylonLaserCharge。
     *
     * <p>两阶段:</p>
     * <ol>
     *   <li>前半 (scaled 100f): 中心光球 (15f) + 加色辐射渐变盘,
     *       脉冲斜率随 3 倍周期循环 (80f 环闪);</li>
     *   <li>后半 (fin > 0.5): 双层正方形框旋转 (200f 外框 / 100f 内框 +45°),
     *       48 粒子光尘 + 发光圆盘, 视觉为 "聚能方阵"。</li>
     * </ol>
     */
    pylonLaserCharge = new Effect(200f, 180f, e -> {
        e.scaled(100f, c -> {
            float slope = Interp.pow3Out.apply(Mathf.mod(c.fout() * 3f, 1f));

            color(UnityPal.monolithLight);
            Fill.circle(c.x, c.y, 15f * c.fin());

            z(Layer.effect + 1f);
            blend(Blending.additive);

            Tmp.c1.set(UnityPal.monolithLight).a(c.fin(Interp.pow3Out));
            Fill.light(c.x, c.y, 27, 40f * c.fout(Interp.pow10Out), Tmp.c1, Color.clear);

            Tmp.c1.a((1f - slope) * 0.5f);
            Fill.light(c.x, c.y, 4, 80f * slope, Color.clear, Tmp.c1);

            blend();
        });

        if(e.fin() >= 0.5f){
            float fin = Mathf.curve(e.fin(), 0.5f, 1f);
            float finscaled = Mathf.curve(fin, 0f, 0.8f);
            float fin5 = Interp.pow5Out.apply(fin);
            float fin3 = Interp.pow3Out.apply(fin);
            float fin2 = Interp.pow2Out.apply(fin);
            float fout = 1f - fin;

            float rot = 370f * fin5;
            float rad = 160f * Interp.pow5Out.apply(finscaled);

            stroke(3 * fout);
            for(int i = 0; i < 2; i++){
                color(UnityPal.monolithLight, UnityPal.monolith, fin);
                Lines.square(e.x, e.y, 200f * fin3, rot * Mathf.signs[i]);

                color(UnityPal.monolith);
                Lines.square(e.x, e.y, 100f * fin5, rot * Mathf.signs[i] + 45f);
            }

            color(UnityPal.monolithLight, UnityPal.monolithDark, fin);
            randLenVectors(e.id, 48, fin3 * 180f, (x, y) ->
                Fill.circle(e.x + x, e.y + y, 5f * fout)
            );

            z(Layer.effect + 1f);
            blend(Blending.additive);

            Tmp.c1.set(UnityPal.monolithLight).a(1f - fin3);
            Fill.light(e.x, e.y, 27, 40f, Tmp.c1, Color.clear);

            Tmp.c1.set(UnityPal.monolithDark).a((1f - fin2) * 0.8f);
            Fill.light(e.x, e.y, 4, rad, Color.clear, Tmp.c1);
            blend();
        }
    }),

    /**
     * monument 电磁炮消散特效 (32f) —— UnityFx.monumentDespawn。
     *
     * <p>lancerLaser 色圆环 (半径扩到 30f) + 25 个光尘粒子
     * 沿命中朝向 ±60° 扇形飞散。</p>
     */
    monumentDespawn = new Effect(32f, e -> {
        e.scaled(15f, i -> {
            color(Pal.lancerLaser);
            stroke(i.fout() * 5f);
            Lines.circle(e.x, e.y, 4f + i.finpow() * 26f);
        });
        randLenVectors(e.id, 25, 5f + e.fin() * 80f, e.rotation, 60f, (x, y) -> Fill.circle(e.x + x, e.y + y, e.fout() * 3f));
    }),

    /**
     * monument 电磁炮尾迹特效 (32f) —— UnityFx.monumentTrail。
     *
     * <p>沿弹道方向的粗线段 (白 + lancerLaser 双层),
     * 长度固定 23f (对应 PU132 trailSpacing 35f - 12f, 硬编码避免
     * 与 UnityBullets 循环依赖), 两端三角收尾。</p>
     */
    monumentTrail = new Effect(32f, e -> {
        float len = 23f; // monumentRailBullet.trailSpacing(35f) - 12f
        float rot = e.rotation;
        Tmp.v1.trns(rot, len);
        for(int i = 0; i < 2; i++){
            color(i < 1 ? Color.white : Pal.lancerLaser);
            float scl = i < 1 ? 1f : 0.5f;
            stroke(e.fout() * 10f * scl);
            lineAngle(e.x, e.y, rot, len, false);
            Drawf.tri(e.x + Tmp.v1.x, e.y + Tmp.v1.y, Lines.getStroke() * 1.22f, 12f * scl, rot);
            Drawf.tri(e.x, e.y, Lines.getStroke() * 1.22f, 12f * scl, rot + 180f);
        }
    });
}
