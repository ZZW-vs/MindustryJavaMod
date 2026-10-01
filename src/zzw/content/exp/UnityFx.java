package zzw.content.exp;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.entities.Effect;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

/**
 * PU_V8 UnityFx 简化版 (仅保留经验系统所需特效)
 * 参考: PU_V8 main/src/unity/content/UnityFx.java L104-146, L819-828
 */
public class UnityFx {

    /** 闪电链抖动使用的随机数发生器 (PU132 里为静态导入的 rand) */
    private static final Rand rand = new Rand();

    public static final Effect
        expPoof = new Effect(60f, e -> {
            Draw.color(Pal.accent, UnityPal.exp, e.fin());
            Angles.randLenVectors(e.id, 9, 1f + 30f * e.finpow(), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 1.7f * e.fout());
                spark(e.x + x, e.y + y, 5f, (5 + 1.5f * Mathf.sin(arc.util.Time.time * 0.12f + e.id * 4f)) * e.fout(), e.finpow() * 90f + e.id * 69f);
            });
        }),

        expShineRegion = new Effect(25f, e -> {
            Draw.color();
            Tmp.c1.set(Pal.accent).lerp(UnityPal.exp, e.fin());
            Draw.mixcol(Tmp.c1, 1f);
            Draw.alpha(1f - e.fin() * e.fin());
            if(e.data instanceof TextureRegion region){
                Draw.rect(region, e.x, e.y, e.rotation);
            }
        }),

        orbDespawn = new Effect(15f, e -> {
            Draw.color(UnityPal.exp);
            Lines.stroke(e.fout() * 1.2f + 0.01f);
            Lines.circle(e.x, e.y, 4f * e.finpow());
        }),

        expLaser = new Effect(15f, e -> {
            if(e.data instanceof mindustry.gen.Building b && !b.dead){
                Tmp.v2.set(b);
                Tmp.v1.set(Tmp.v2).sub(e.x, e.y).nor().scl(mindustry.Vars.tilesize / 2f);
                Tmp.v2.sub(Tmp.v1);
                Tmp.v1.add(e.x, e.y);
                Drawf.laser(Core.atlas.find("create-exp-laser"), Core.atlas.find("create-exp-laser-end"), Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y, 0.4f * e.fout());
            }
        }),

        placeShine = new Effect(30f, e -> {
            Draw.color(e.color);
            Lines.stroke(e.fout());
            Lines.square(e.x, e.y, e.rotation / 2f + e.fin() * 3f);
            spark(e.x, e.y, 25f, 15f * e.fout(), e.finpow() * 90f);
        }),

        expAbsorb = new Effect(15f, e -> {
            Lines.stroke(e.fout() * 1.5f);
            Draw.color(UnityPal.exp);
            Lines.circle(e.x, e.y, e.fin() * 2.5f + 1f);
        }),

        expDespawn = new Effect(15f, e -> {
            Draw.color(UnityPal.exp);
            Angles.randLenVectors(e.id, 7, 2f + 5 * e.fin(), (x, y) -> Fill.circle(e.x + x, e.y + y, e.fout()));
        }),

        // ===== Supernova 专用特效 (PU_V8 UnityFx L1130-1183 完整移植) =====
        // 充能开始特效: 数据为 Float r, 随机线段向外发散
        supernovaChargeBegin = new Effect(27f, e -> {
            if(e.data instanceof Float data){
                float r = data;
                Angles.randLenVectors(e.id, (int)(2f * r), 1f + 27f * e.fout(), (x, y) -> {
                    Draw.color(Pal.lancerLaser);
                    Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), (1f + e.fslope() * 6f) * r);
                });
            }
        }),

        // 星辰热浪特效: 双圆环扩散
        supernovaStarHeatwave = new Effect(40f, e -> {
            Draw.color(Pal.lancerLaser);
            Lines.stroke(e.fout());
            Lines.circle(e.x, e.y, 110f * e.fin());
            Lines.circle(e.x, e.y, 120f * e.finpow() * 0.6f);
        }),

        // 充能星辰特效: 数据为 Float r, 渐隐圆环
        supernovaChargeStar = new Effect(30f, e -> {
            if(e.data instanceof Float data){
                float r = data;
                Draw.color(Pal.lancerLaser);
                Draw.alpha(e.fin() * 2f * r);
                Lines.circle(e.x, e.y, 150f * Interp.pow2Out.apply(e.fout()) * Mathf.lerp(0.1f, 1f, r));
            }
        }),

        // 星辰衰减特效: 随机小方块渐隐
        supernovaStarDecay = new Effect(56f, e -> Angles.randLenVectors(e.id, 1, 36f * e.finpow(), (x, y) -> {
            Draw.color(Pal.lancerLaser);
            Fill.rect(e.x + x, e.y + y, 2.2f * e.fout(), 2.2f * e.fout(), 45f);
        })),

        // 充能星辰2特效: 数据为 Float r, 随机小圆点扩散
        supernovaChargeStar2 = new Effect(27f, e -> {
            if(e.data instanceof Float data){
                float r = data;
                Angles.randLenVectors(e.id, (int)(3f * r), e.fout() * ((90f + r * 150f) * (0.3f + Mathf.randomSeed(e.id, 0.7f))), (x, y) -> {
                    Draw.color(Pal.lancerLaser);
                    Fill.circle(e.x + x, e.y + y, 2f * e.fin());
                });
            }
        }),

        // 拉拽特效: 数据为 Vec2 (目标位置), e.rotation 为 size
        // ★ v155.4 简化: 原版用 SVec2 (Long 序列化), 这里改用 Vec2 直接传递
        supernovaPullEffect = new Effect(30f, 500f, e -> {
            if(e.data instanceof Vec2 target){
                float size = e.rotation;
                float x = e.x + Mathf.randomSeedRange(e.id, 4f);
                float y = e.y + Mathf.randomSeedRange(e.id + 1, 4f);
                // 从单位位置向目标位置插值
                float px = Mathf.lerp(x, target.x, e.fin());
                float py = Mathf.lerp(y, target.y, e.fin());
                Draw.color(Pal.lancerLaser);
                Fill.circle(px, py, size * (0.5f + e.fslope() * 0.5f));
            }
        }),

        // ===== TeleUnit 传送特效 (PU132 UnityFx L847-879 完整移植) =====
        // 传送到达特效: e.rotation 为 hitSize, 双层方框 + 随机方块扩散
        tpOut = new Effect(30f, e -> {
            Draw.color(UnityPal.dirium);
            Lines.stroke(3f * e.fout());
            Lines.square(e.x, e.y, e.finpow() * e.rotation, 45f);
            Lines.stroke(5f * e.fout());
            Lines.square(e.x, e.y, e.fin() * e.rotation, 45f);
            Angles.randLenVectors(e.id, 10, e.fin() * (e.rotation + 10f), (x, y) -> Fill.square(e.x + x, e.y + y, e.fout() * 4f, 100f * Mathf.randomSeed(e.id + 1) * e.fin()));
        }),

        // 传送离开特效: 数据为 UnitType, 绘制单位图标渐显
        tpIn = new Effect(50f, e -> {
            if(!(e.data instanceof UnitType type)) return;
            TextureRegion region = type.fullIcon;
            Draw.color();
            Draw.mixcol(UnityPal.dirium, 1f);
            Draw.rect(region, e.x, e.y, region.width * Draw.scl * e.fout(), region.height * Draw.scl * e.fout(), e.rotation);
            Draw.mixcol();
        }),

        // 传送闪光特效: 数据为 Unit, 在单位位置绘制图标渐隐 (渲染于飞行单位层之上)
        tpFlash = new Effect(30f, e -> {
            if(!(e.data instanceof Unit unit) || !unit.isValid()) return;
            TextureRegion region = unit.type.fullIcon;
            Draw.mixcol(UnityPal.diriumLight, 1f);
            Draw.alpha(e.fout());
            Draw.rect(region, unit.x, unit.y, unit.rotation - 90f);
            Draw.mixcol();
            Draw.color();
        }).layer(Layer.flyingUnit + 1f),

        // ===== LightningBurstAbility 所需特效 (PU132 UnityFx L742-780) =====
        // 充能等待进度特效: 数据为 Object[]{whenReady, Unit}
        waitFx = new Effect(30f, e -> {
            Object[] data = (Object[])e.data;
            float whenReady = (float)data[0];
            Unit u = (Unit)data[1];
            if(u == null || !u.isValid() || u.dead) return;
            Draw.color(e.color);
            Lines.stroke(e.fout() * 1.5f);
            polySeg(60, 0, (int)(60 * (1 - (e.rotation - Time.time) / whenReady)), u.x, u.y, 8f, 0f);
        }).layer(Layer.effect - 0.00001f),

        // 充能完成环形特效: 数据为 Unit
        ringFx = new Effect(25f, e -> {
            if(!(e.data instanceof Unit u)) return;
            if(!u.isValid() || u.dead) return;
            Draw.color(Color.white, e.color, e.fin());
            Lines.stroke(e.fout() * 1.5f);
            Lines.circle(u.x, u.y, 8f);
        }),

        // ===== 经验激光炮台特效 (PU132 UnityFx / ShootFx 移植) =====

        /** 蓄力火花 (PU132 UnityFx.laserCharge): 沿炮口方向随机散布的短线段 */
        laserCharge = new Effect(38f, e -> {
            Draw.color(e.color);
            Angles.randLenVectors(e.id, e.id % 3 + 1, 1f + 20f * e.fout(), e.rotation, 120f, (x, y) ->
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fslope() * 3f + 1f)
            );
        }),

        /** 短蓄力火花 (PU132 UnityFx.laserChargeShort): 单点小方块, 用于连发蓄力炮台 */
        laserChargeShort = new Effect(18f, e -> {
            Draw.color(e.color);
            Angles.randLenVectors(e.id, 1, 1f + 20f * e.fout(), e.rotation, 120f, (x, y) ->
                Fill.square(e.x + x, e.y + y, e.fslope() * 1.5f + 0.1f, 45f)
            );
        }),

        /**
         * 蓄力开始方块 (PU132 UnityFx.laserChargeBegin): 由大到小再收敛的旋转方块.
         * <p>v160 无 {@code Effect.scaled}, 这里用 {@code e.fin()} 直接复现内外两层进度.</p>
         */
        laserChargeBegin = new Effect(60f, e -> {
            Draw.color(e.color);
            Fill.square(e.x, e.y, e.fin() * 3f, 45f);

            Draw.color();
            Fill.square(e.x, e.y, e.fin() * 2f, 45f);
        }),

        /**
         * 裂缝激光蓄力 (PU132 UnityFx.laserFractalCharge): 3 条由外向内收敛的线段.
         * <p>v160 无 {@code Effect.scaled}, 用固定时长 (60/30 tick) 代替原版的嵌套子特效.</p>
         */
        laserFractalCharge = new Effect(120f, e -> {
            float radius = 10f * 8f;
            // 生成半径: 前半段维持, 后半段向外扩张 (对应原版 pow3Out(1 - fout(0.5)))
            float grow = radius / 2f + Interp.pow3Out.apply(1f - e.fout(0.5f)) * radius * 1.25f;
            // 线段亮度: 前 60 tick 内衰减
            float life = Mathf.curve(1f - Mathf.clamp(e.time / 60f), 0f, 0.5f);
            // 收敛进度: 前 30 tick 由外向内
            float conv = Interp.pow2.apply(Mathf.clamp(e.time / 30f));

            Angles.randLenVectors(e.id, 3, grow, (x, y) -> {
                Lines.stroke(life, Tmp.c1.set(Pal.lancerLaser).lerp(Pal.sapBullet, 0.5f).a(life));
                Lines.line(e.x + x, e.y + y, e.x + Mathf.lerp(x, 0f, conv), e.y + Mathf.lerp(y, 0f, conv));
            });
        }),

        /**
         * 裂缝激光蓄力起始 (PU132 UnityFx.laserFractalChargeBegin): 4 条旋转弧线.
         * <p>v160 无 {@code Effect.scaled}, 用 {@code e.time/lifetime} 直接换算各弧线半径.</p>
         */
        laserFractalChargeBegin = new Effect(90f, e -> {
            float r0 = 9f * Mathf.clamp(e.time / 60f);
            float r1 = 10f * Mathf.clamp(e.time / 40f);
            float r2 = 11f * Mathf.clamp(e.time / 40f);
            float r3 = 12f * Mathf.clamp(e.time / 60f);

            Draw.color(Tmp.c1.set(UnityPal.lancerSap3).a(0.1f + 0.55f * e.fslope()));
            Lines.arc(e.x, e.y, r0, 0.6f, Time.time * 8f - 60f);
            Lines.arc(e.x, e.y, r1, 0.6f, Time.time * 5f);

            Draw.color(Tmp.c1.set(Pal.lancerLaser).lerp(Pal.sapBullet, 0.5f + 0.5f * Mathf.sin(16f * e.fin())).a(0.25f + 0.8f * e.fslope()));
            Lines.arc(e.x, e.y, r2, 0.4f, Time.time * -6f + 121f);
            Lines.arc(e.x, e.y, r3, 0.4f, Time.time * -4f + 91f);
        }),

        /**
         * 冻结爆裂 (PU132 UnityFx.freezeEffect): 六边形扩散 + 双圈雪花.
         * <p>数据: e.rotation 为强度, e.color 为冰色.</p>
         */
        freezeEffect = new Effect(30f, e -> {
            Draw.color(Color.white, e.color, e.fin());
            Lines.stroke(e.fout() * 2f);
            Lines.poly(e.x, e.y, 6, 4f + e.rotation * 1.5f * e.finpow(), Mathf.randomSeed(e.id) * 360f);
            Draw.color();

            // ★ 原版用静态计数器 integer 派生随机种子, 这里用局部数组实现同样的效果
            int[] idx = {0};
            Angles.randLenVectors(e.id, 5, e.rotation * 1.6f * e.fin() + 16f, e.fin() * 33f, 360f, (x, y) ->
                snowFlake(e.x + x, e.y + y, e.finpow() * 60f, Mathf.randomSeed(e.id + idx[0]++) * 2f + 2f)
            );
            Angles.randLenVectors(e.id + 1, 3, e.rotation * 2.1f * e.fin() + 7f, e.fin() * -19f, 360f, (x, y) ->
                snowFlake(e.x + x, e.y + y, e.finpow() * 60f, Mathf.randomSeed(e.id + idx[0]++) * 2f + 2f)
            );
        }),

        /** 冰片射击 (PU132 UnityFx.shootFlake): 6 向冰针 */
        shootFlake = new Effect(21f, e -> {
            Draw.color(e.color, Color.white, e.fout());

            for(int i = 0; i < 6; i++){
                Drawf.tri(e.x, e.y, 3f * e.fout(), 12f, e.rotation + Mathf.randomSeed(e.id, 360f) + 60f * i);
            }
        }),

        /** 蓄力开火 (PU132 ShootFx.laserChargeShoot): 4 向旋转尖刺 */
        laserChargeShoot = new Effect(21f, e -> {
            Draw.color(e.color, Color.white, e.fout());

            for(int i = 0; i < 4; i++){
                Drawf.tri(e.x, e.y, 4f * e.fout(), 29f, e.rotation + 90f * i + e.finpow() * 112f);
            }
        }),

        /** 连发蓄力开火 (PU132 ShootFx.laserChargeShootShort): 扩散方框 */
        laserChargeShootShort = new Effect(15f, e -> {
            Draw.color(e.color, Color.white, e.fout());
            Lines.stroke(2f * e.fout());
            Lines.square(e.x, e.y, 0.1f + 20f * e.finpow(), 45f);
        }),

        /**
         * 裂缝激光开火 (PU132 ShootFx.laserFractalShoot): 4 向尖刺 + 5 组散射碎片.
         * <p>v160 无 PU 的 {@code Utils.pow25Out}, 用 {@link Interp#pow2Out} 近似.</p>
         */
        laserFractalShoot = new Effect(40f, e -> {
            Draw.color(Tmp.c1.set(e.color).lerp(Color.white, e.fout()));

            for(int i = 0; i < 4; i++){
                Drawf.tri(e.x, e.y, 4f * e.fout(), 29f, e.rotation + 90f * i + e.finpow() * 112f);
            }

            for(int h = 1; h <= 5; h++){
                float mul = h % 2;
                float rm = 1f + mul * 0.5f;
                float rot = 90f + (1f - e.finpow()) * Mathf.randomSeed(e.id + (long)(mul * 2f), 210f * rm, 360f * rm);
                for(int i = 0; i < 2; i++){
                    float m = i == 0 ? 1f : 0.5f;
                    float w = 8f * e.fout() * m;
                    float length = 8f * 3f / (2f - mul);
                    Tmp.v1.trns(rot, length - 4f);
                    float fx = Tmp.v1.x + e.x, fy = Tmp.v1.y + e.y;
                    length *= Interp.pow2Out.apply(e.fout());

                    Drawf.tri(fx, fy, w, length * m, rot + 180f);
                    Drawf.tri(fx, fy, w, length / 3f * m, rot);

                    Draw.alpha(0.5f);
                    Drawf.tri(e.x, e.y, w, length * m, rot + 360f);
                    Drawf.tri(e.x, e.y, w, length / 3f * m, rot);
                    Fill.square(fx, fy, 3f * e.fout(), rot + 45f);
                }
            }
        }),

        /** 扭曲状态特效 (PU132 UnityFx.distortFx): 数据为 Float, 表示方块旋转角 */
        distortFx = new Effect(18f, e -> {
            if(!(e.data instanceof Float)) return;
            Draw.color(Pal.lancerLaser, Pal.place, e.fin());
            Fill.square(e.x, e.y, 0.1f + e.fout() * 2.5f, (Float)e.data);
        }),

        /** 力场扩散波纹 (PU132 UnityFx.distSplashFx): 数据为 Float[]{半径, 生命周期} */
        distSplashFx = new Effect(80f, e -> {
            if(!(e.data instanceof Float[] data)) return;
            Draw.color(Pal.lancerLaser, Pal.place, e.fin());
            Lines.stroke(2f * e.fout());
            Lines.circle(e.x, e.y, data[0] * e.fin());
        }){
            @Override
            public void at(float x, float y, float rotation, Object data){
                // ★ 原版行为: 用数据里的第二项临时覆盖特效生存时间
                if(data instanceof Float[] f) lifetime = f[1];
                create(x, y, rotation, Color.white, data);
            }
        },

        /** 力场生成 (PU132 UnityFx.distStart): 数据为 Float, 表示半径 */
        distStart = new Effect(45f, e -> {
            if(!(e.data instanceof Float data)) return;

            float centerf = Color.clear.toFloatBits();
            float edgef = Tmp.c1.set(Pal.lancerLaser).a(e.fout()).toFloatBits();
            float sides = Mathf.ceil(Lines.circleVertices(data) / 2f) * 2f;
            float space = 360f / sides;

            for(int i = 0; i < sides; i += 2){
                float px = Angles.trnsx(space * i, data);
                float py = Angles.trnsy(space * i, data);
                float px2 = Angles.trnsx(space * (i + 1), data);
                float py2 = Angles.trnsy(space * (i + 1), data);
                float px3 = Angles.trnsx(space * (i + 2), data);
                float py3 = Angles.trnsy(space * (i + 2), data);
                Fill.quad(e.x, e.y, centerf, e.x + px, e.y + py, edgef, e.x + px2, e.y + py2, edgef, e.x + px3, e.y + py3, edgef);
            }
        }),

        /** 小闪电链 (PU132 UnityFx.smallChainLightning): 数据为 Position (目标点) */
        smallChainLightning = new Effect(40f, 300f, e -> {
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
            rand.setSeed(e.id);

            for(int i = 0; i < links; i++){
                float nx, ny;
                if(i == links - 1){
                    nx = tx;
                    ny = ty;
                }else{
                    float len = (i + 1) * spacing;
                    Tmp.v1.setToRandomDirection(rand).scl(range / 2f);
                    nx = e.x + normx * len + Tmp.v1.x;
                    ny = e.y + normy * len + Tmp.v1.y;
                }
                Lines.linePoint(nx, ny);
            }

            Lines.endLine();
        }),

        /** 闪电链 (PU132 UnityFx.chainLightning): 数据为 Position (目标点) */
        chainLightning = new Effect(30f, 300f, e -> {
            if(!(e.data instanceof Position p)) return;

            float tx = p.getX(), ty = p.getY(), dst = Mathf.dst(e.x, e.y, tx, ty);
            Tmp.v1.set(p).sub(e.x, e.y).nor();

            float normx = Tmp.v1.x, normy = Tmp.v1.y;
            float range = 6f;
            int links = Mathf.ceil(dst / range);
            float spacing = dst / links;

            Lines.stroke(4f * e.fout());
            Draw.color(Color.white, e.color, e.fin());

            Lines.beginLine();

            Lines.linePoint(e.x, e.y);
            rand.setSeed(e.id);

            for(int i = 0; i < links; i++){
                float nx, ny;
                if(i == links - 1){
                    nx = tx;
                    ny = ty;
                }else{
                    float len = (i + 1) * spacing;
                    Tmp.v1.setToRandomDirection(rand).scl(range / 2f);
                    nx = e.x + normx * len + Tmp.v1.x;
                    ny = e.y + normy * len + Tmp.v1.y;
                }
                Lines.linePoint(nx, ny);
            }

            Lines.endLine();
        }),

        /** 闪光 (PU132 UnityFx.sparkle): dirium-wall 的 updateEffect, 随机火花 */
        sparkle = new Effect(55f, e -> {
            Draw.color(e.color);
            // ★ 原版用静态计数器 integer 派生随机种子, 这里用局部数组复现
            int[] idx = {0};
            Angles.randLenVectors(e.id, e.id % 3 + 1, 8f, (x, y) -> {
                idx[0]++;
                spark(e.x + x, e.y + y, e.fout() * 2.5f, 0.5f + e.fout(), e.id * idx[0]);
            });
        }),

        /** 限伤命中特效 (PU132 UnityFx.maxDamageFx): 橙色扩张方框 */
        maxDamageFx = new Effect(16f, e -> {
            Draw.color(Color.orange);
            Lines.stroke(2.5f * e.fin());
            Lines.square(e.x, e.y, e.rotation * 4f);
        }),

        /** 承受限伤特效 (PU132 UnityFx.withstandFx): 橙色收缩方框 */
        withstandFx = new Effect(16f, e -> {
            Draw.color(Color.orange);
            Lines.stroke(1.2f * e.rotation * e.fout());
            Lines.square(e.x, e.y, e.rotation * 4f);
        }),

        /** 闪烁免伤特效 (PU132 UnityFx.blinkFx): 白→迪里姆色方框 */
        blinkFx = new Effect(30f, e -> {
            Draw.color(Color.white, UnityPal.dirium, e.fin());
            Lines.stroke(3f * e.rotation * e.fout());
            Lines.square(e.x, e.y, e.rotation * 4f * e.finpow());
        });

    /** 雪花 (PU132 UnityDrawf.snowFlake): 三条夹角 60° 的线段 */
    private static void snowFlake(float x, float y, float r, float s){
        for(int i = 0; i < 3; i++){
            Lines.lineAngleCenter(x, y, r + 60 * i, s);
        }
    }

    /** 绘制多边形部分弧线 (PU132 polySeg, 原版未定义自行实现) */
    private static void polySeg(int sides, int start, int end, float x, float y, float radius, float rotation){
        float step = 360f / sides;
        for(int i = start; i < end; i++){
            float a1 = (i * step + rotation) * Mathf.degRad;
            float a2 = ((i + 1) * step + rotation) * Mathf.degRad;
            Lines.line(
                x + Mathf.cos(a1) * radius, y + Mathf.sin(a1) * radius,
                x + Mathf.cos(a2) * radius, y + Mathf.sin(a2) * radius
            );
        }
    }

    /** 绘制4向三角形尖刺 (PU_V8 UnityDrawf.spark) */
    public static void spark(float x, float y, float w, float h, float r){
        for(int i = 0; i < 4; i++){
            Drawf.tri(x, y, w, h, r + 90 * i);
        }
    }
}
