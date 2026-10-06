package zzw.content.units;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.math.geom.Vec3;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.world.Tile;
import zzw.content.graphics.UnityPal;
import zzw.content.type.UnityUnitType;
import zzw.content.units.ai.MonolithSoulAI;
import zzw.content.units.effects.LineFx;
import zzw.content.units.effects.MonolithFx;
import zzw.content.units.effects.ParticleFx;
import zzw.content.units.effects.TrailFx;
import zzw.content.units.effects.UnityDrawf;
import zzw.content.units.entities.MonolithSoulUnit;
import zzw.content.units.graphics.MultiTrail;
import zzw.content.units.graphics.MultiTrail.TrailHold;
import zzw.content.units.graphics.Trails;
import zzw.content.units.soul.MonolithWorld;
import zzw.content.units.soul.SoulDecorationUnit;
import zzw.content.units.soul.SoulLegsUnit;
import zzw.content.units.soul.SoulMechUnit;
import zzw.util.Quat;
import zzw.util.UnityUtils;

/**
 * Z_SoulUnits — 灵魂系统单位与实体注册 (PU132 移植)。
 *
 * <p>本类集中处理"灵魂系统"的单位侧闭环, 对应 PU132 的
 * {@code MonolithUnitTypes.monolithSoul} + {@code unity.entities.comp.MonolithSoulComp}
 * + {@code unity.entities.comp.CTrailComp}：</p>
 *
 * <ol>
 *   <li><b>实体注册</b>：把灵魂承载实体 ({@link SoulMechUnit} / {@link SoulLegsUnit} /
 *       {@link SoulDecorationUnit}) 与灵魂单位 {@link MonolithSoulUnit} 注册进
 *       {@link ZEntityRegister}, 分配唯一 classId (v154+ 的硬性要求);</li>
 *   <li><b>地块索引</b>：调用 {@link MonolithWorld#get()} 注册世界加载 / 地块变化事件,
 *       让灵魂 AI 能查找最近的巨石地块;</li>
 *   <li><b>灵魂单位</b>：定义 {@link #monolithSoul} 单位类型, 并移植 PU132 中
 *       定义在 UnitType 上的 {@code update(Unit)} / {@code draw(Unit)} 覆写
 *       (幽灵态 3D 符环 + 幻影拖尾, 实体态走普通单位绘制)。</li>
 * </ol>
 *
 * <p>★ v160 适配要点:</p>
 * <ul>
 *   <li>PU132 {@code defaultController} → v160 {@code aiController};</li>
 *   <li>PU132 {@code miningRange} → v160 {@code mineRange};</li>
 *   <li>PU132 {@code forceWreckRegion} 字段不存在, 省略 (wreck 贴图仍会被 UnitType.load 自动加载);</li>
 *   <li>PU132 {@code softShadowRegion} 默认取原版软阴影, 不覆写 (与 PU132 行为一致);</li>
 *   <li>贴图名去掉 "unity-" 前缀, 改用本模组 "create-" 前缀规则
 *       ("unity-monolith-chain" → "create-monolith-chain", "unity-line-shade" → "create-line-shade")。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class Z_SoulUnits{
    /** 巨石灵魂单位 (PU132 monolithSoul, 死亡拆魂后飞出的灵魂实体)。 */
    public static UnityUnitType monolithSoul;

    public static void load(){
        // ★ 1. 注册灵魂相关实体 (必须先于任何 UnitType.init, 否则 classId() 返回 -1)
        ZEntityRegister.register(MonolithSoulUnit.class, MonolithSoulUnit::create);
        ZEntityRegister.register(SoulMechUnit.class, SoulMechUnit::create);
        ZEntityRegister.register(SoulLegsUnit.class, SoulLegsUnit::create);
        ZEntityRegister.register(SoulDecorationUnit.class, SoulDecorationUnit::create);

        // ★ 2. 初始化巨石地块分块索引 (注册 WorldLoadEvent / TileChangeEvent)
        MonolithWorld.get();

        // ★ 3. 灵魂单位类型定义 (PU132 MonolithUnitTypes.monolithSoul)
        monolithSoul = new UnityUnitType("monolith-soul"){
            {
                // PU132: defaultController = MonolithSoulAI::new
                aiController = MonolithSoulAI::new;
                constructor = MonolithSoulUnit::create;

                health = 300f;
                speed = 2.4f;
                rotateSpeed = 10f;
                accel = 0.2f;
                drag = 0.08f;
                flying = true;
                lowAltitude = true;
                fallSpeed = 1f;
                range = maxRange = mineRange = 96f;
                hitSize = 12f;
                omniMovement = false;
                engineColor = UnityPal.monolithLight;
                trailLength = 24;
                deathExplosionEffect = MonolithFx.monolithSoulDeath;

                // 幻影多重拖尾: 1 条主带 + 2 条侧丝带 (PU132 trailType)
                trailType = unit -> new MultiTrail(MultiTrail.rot(unit),
                    new TrailHold(Trails.soul(MultiTrail.rot(unit), 50, speed), engineColor),
                    new TrailHold(Trails.soul(MultiTrail.rot(unit), 64, speed), -4.8f, 6f, 0.56f, engineColor),
                    new TrailHold(Trails.soul(MultiTrail.rot(unit), 64, speed), 4.8f, 6f, 0.56f, engineColor)
                );
            }

            /**
             * 每帧逻辑 (PU132 MonolithUnitType.monolithSoul.update)。
             *
             * <p>步骤:</p>
             * <ol>
             *   <li>根据当前是否实体化 (corporeal) 切换 3 条 / 1 条拖尾:
             *       旧拖尾交给 {@link TrailFx#trailFadeLow} 渐隐;</li>
             *   <li>幽灵态: 概率撒灵魂粒子; 正在成形 (forming) 时对每块地形播放火花与吸收线;
             *       正在加入容器 (joining) 时播放吸收线;</li>
             *   <li>super.update 执行原版单位更新 (含引擎侧自动拖尾推进)。</li>
             * </ol>
             */
            @Override
            public void update(Unit unit){
                if(unit instanceof MonolithSoulUnit soul){
                    if(!(soul.trail instanceof MultiTrail trail)) return;

                    float width = (engineSize + Mathf.absin(Time.time, 2f, engineSize / 4f) * soul.elevation) * trailScl;
                    if(trail.trails.length == 3 && soul.corporeal()){
                        MultiTrail copy = trail.copy();
                        copy.rotation = MultiTrail::calcRot;

                        TrailFx.trailFadeLow.at(soul.x, soul.y, width, engineColor, copy);
                        soul.trail = new MultiTrail(new TrailHold(Trails.soul(MultiTrail.rot(unit), trailLength, speed), engineColor));
                    }else if(trail.trails.length == 1 && !soul.corporeal()){
                        MultiTrail copy = trail.copy();
                        copy.rotation = MultiTrail::calcRot;

                        TrailFx.trailFadeLow.at(soul.x, soul.y, width, engineColor, copy);
                        soul.trail = trailType.get(soul);
                    }

                    if(!soul.corporeal()){
                        if(Mathf.chance(Time.delta)) ParticleFx.monolithSoul.at(soul.x, soul.y, Time.time, new Vec2(soul.vel).scl(-0.3f));
                        if(soul.forming()){
                            for(Tile form : soul.forms()){
                                if(Mathf.chanceDelta(0.17f)) ParticleFx.monolithSpark.at(form.drawx(), form.drawy(), 4f);
                                if(Mathf.chanceDelta(0.67f)) LineFx.monolithSoulAbsorb.at(form.drawx(), form.drawy(), 0f, soul);
                            }
                        }else if(soul.joining() && Mathf.chanceDelta(0.33f)){
                            LineFx.monolithSoulAbsorb.at(soul.x + Mathf.range(6f), soul.y + Mathf.range(6f), 0f, soul.joinTarget());
                        }
                    }
                }

                super.update(unit);
            }

            /**
             * 绘制 (PU132 MonolithUnitType.monolithSoul.draw)。
             *
             * <p>幽灵态 (非实体化) 完全自绘: additive 蓝圆 + 旋转弧线/三角符环 +
             * 地块残骸 (formProgress 驱动) + 3D 透视链环; 实体态回退到普通单位绘制。</p>
             */
            @Override
            public void draw(Unit unit){
                if(!(unit instanceof MonolithSoulUnit soul)) return;
                if(!soul.corporeal()){
                    float z = Draw.z();
                    Draw.z(Layer.flyingUnitLow);

                    float trailSize = (engineSize + Mathf.absin(Time.time, 2f, engineSize / 4f) * soul.elevation) * trailScl;
                    soul.trail.drawCap(engineColor, trailSize);
                    soul.trail.draw(engineColor, trailSize);

                    Draw.z(Layer.effect - 0.01f);

                    Draw.blend(Blending.additive);
                    Draw.color(UnityPal.monolith);
                    Fill.circle(soul.x, soul.y, 6f);

                    Draw.color(UnityPal.monolithDark);
                    Draw.rect(softShadowRegion, soul.x, soul.y, 10f, 10f);

                    Draw.blend();
                    Lines.stroke(1f, UnityPal.monolithDark);

                    float rotation = Time.time * 3f * Mathf.sign(soul.id % 2 == 0);
                    for(int i = 0; i < 5; i++){
                        float r = rotation + 72f * i;
                        UnityDrawf.arcLine(soul.x, soul.y, 10f, 60f, r);

                        Tmp.v1.trns(r, 10f).add(soul.x, soul.y);
                        UnityDrawf.tri(Tmp.v1.x, Tmp.v1.y, 2.5f, 6f, r);
                    }

                    Draw.z(Layer.flyingUnit);
                    Draw.reset();

                    for(int i = 0; i < wreckRegions.length; i++){
                        float off = (360f / wreckRegions.length) * i;
                        float fin = soul.formProgress(), fout = 1f - fin;

                        Tmp.v1.trns(soul.rotation + off, fout * 24f)
                            .add(Tmp.v2.trns((Time.time + off) * 4f, fout * 3f))
                            .add(soul.x, soul.y);

                        Draw.alpha(fin);
                        Draw.rect(wreckRegions[i], Tmp.v1.x, Tmp.v1.y, soul.rotation - 90f);
                    }

                    Lines.stroke(1.5f, UnityPal.monolith);

                    TextureRegion reg = Trails.region("monolith-chain");
                    Quat rot = UnityUtils.q1.set(Vec3.Z, soul.ringRotation() + 90f).mul(UnityUtils.q2.set(Vec3.X, 75f));
                    float t = Interp.pow3Out.apply(soul.joinTime()), w = reg.width * Draw.scl * 0.5f * t, h = reg.height * Draw.scl * 0.5f * t,
                        rad = t * 25f, a = Mathf.curve(t, 0.33f);

                    Draw.alpha(a);
                    UnityDrawf.panningCircle(reg,
                        soul.x, soul.y, w, h,
                        rad, 360f, Time.time * 6f * Mathf.sign(soul.id % 2 == 0) + soul.id * 30f,
                        rot, Layer.flyingUnitLow - 0.01f, Layer.flyingUnit
                    );

                    Draw.color(Color.black, UnityPal.monolithDark, 0.67f);
                    Draw.alpha(a);

                    Draw.blend(Blending.additive);
                    UnityDrawf.panningCircle(Trails.region("line-shade"),
                        soul.x, soul.y, w + 6f, h + 6f,
                        rad, 360f, 0f,
                        rot, true, Layer.flyingUnitLow - 0.01f, Layer.flyingUnit
                    );

                    Draw.blend();
                    Draw.z(z);
                }else{
                    super.draw(soul);
                }
            }
        };
    }
}
