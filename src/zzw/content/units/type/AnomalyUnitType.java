package zzw.content.units.type;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.graphics.Pal;
import zzw.content.type.UnityUnitType;
import zzw.content.units.bullets.AnomalyLaserBulletType;
import zzw.content.units.effects.SpecialFx;
import zzw.content.units.effects.UnityDrawf;
import zzw.content.units.entities.DecorationUnitEntity;
import zzw.content.units.type.decal.WingDecorationType;
import zzw.content.units.type.decal.WingDecorationType.Wing;
import zzw.content.units.weapons.EnergyChargeWeapon;
import zzw.content.units.weapons.EnergyChargeWeapon.ChargeMount;
import zzw.content.units.weapons.TractorBeamWeapon;

/**
 * anomaly 单位类型 (PU132 UnityUnitTypes.java L3655-3762 完整移植)
 *
 * <p>T7 飞行单位: 两门牵引光束 + 一门充能大激光, 6 片翅膀扇动装饰。</p>
 *
 * <p><b>武器构成 (逐步解释):</b>
 * <br>1. 2× {@link TractorBeamWeapon}: 将敌人持续拉向自身并造成缓慢伤害 (附减速);
 * <br>2. 1× {@link EnergyChargeWeapon} + {@link AnomalyLaserBulletType}:
 *     蓄力期间吸收范围内敌方单位能量 (死亡回能) 转化为激光充能,
 *     满充能后发射一记伤害随充能提升的大激光, 附带命中处链状闪电。</p>
 *
 * <p><b>★ 与 PU132 的差异 (v155.4 适配):</b>
 * <br>1. PU132 用注解 @EntityDef({Unitc, Decorationc}) 生成装饰实体,
 *     这里显式指定 constructor = DecorationUnitEntity::create;
 * <br>2. 充能武器镜像关闭 (mirror=false), 贴图经 {@link UnityUnitType#init()} 处理。</p>
 */
public class AnomalyUnitType extends UnityUnitType{

    /**
     * @param name 单位注册名 (如 "anomaly", ContentTransformer 会自动加 mod 前缀)
     */
    public AnomalyUnitType(String name){
        super(name);

        // ★ 装饰实体: 持有 decors[] 驱动翅膀动画, 没有它翅膀不会显示!
        constructor = DecorationUnitEntity::create;

        health = 25000f;
        speed = 2.1f;
        rotateSpeed = 1f;
        accel = 0.08f;
        drag = 0.07f;
        hitSize = 137.5f;
        armor = 18f;  // ★ 按用户设定 (原缺失)
        engineSize = -1f;
        flying = true;
        lowAltitude = true;
        outlineColor = Color.valueOf("464a61");

        // 6 片翅膀装饰 (贴图 load 时自动尝试 create- 前缀)
        decorations.add(new WingDecorationType(name + "-wing", 2){{
            flapScl = 90f;
            flapAnimation = new Interp.ExpOut(2, 2.5f);
            wings.addAll(new Wing(0, 7.5f, -61f, 0f, 20f),
            new Wing(0, 10.5f, -48.25f, 0.1666f, 20f),
            new Wing(0, 13.5f, -35.5f, 0.3333f, 20f),
            new Wing(1, 13.5f, -22.75f, 0.5f, 20f),
            new Wing(1, 17.5f, -10f, 0.6666f, 20f),
            new Wing(1, 21.25f, 2.75f, 0.8333f, 20f));
        }});

        // 牵引光束的目标状态: 减速 (PU132 原版)
        BulletType t = new BulletType(0f, 45f){{
            status = StatusEffects.slow;
            statusDuration = 20f;
            maxRange = 290f;
        }};

        // 2 门牵引光束 (左/右) 贴图名需带 create- 前缀 (武器贴图不被 ContentTransformer 转换)
        weapons.add(new TractorBeamWeapon("create-" + name + "-mount"){{
            x = 18.75f;
            y = 22.5f;
            shootY = 8.75f + 2f;
            pullStrength = 40f;
            scaledForce = 50f;
            includeDead = true;
            bullet = t;
        }}, new TractorBeamWeapon("create-" + name + "-mount"){{
            x = 19.75f;
            y = 63f;
            shootY = 8.75f + 2f;
            pullStrength = 40f;
            scaledForce = 50f;
            includeDead = true;
            bullet = t;
        }});

        // 1 门充能大激光 (核心武器)
        weapons.add(new EnergyChargeWeapon(""){{
            x = 0f;
            y = 39.75f;
            shootY = 0f;
            mirror = false;
            reload = 2f * 60f;

            bullet = new AnomalyLaserBulletType(800f){{
                lightningColor = Pal.lancerLaser;
            }};

            float rad = 70f; // 蓄力回能范围
            // 蓄力特效: 武器口的旋转光环 + 虚线圆圈
            drawCharge = (unit, mount, charge) -> {
                float rotation = unit.rotation - 90f,
                wx = unit.x + Angles.trnsx(rotation, x, y),
                wy = unit.y + Angles.trnsy(rotation, x, y),
                scl = Math.max(1f - (mount.reload / reload), 0f) / 2f;

                Draw.color(Pal.lancerLaser);
                UnityDrawf.shiningCircle(unit.id, Time.time, wx, wy, 10f * scl, 5, 70f, 15f, 4f * scl, 90f);
                Draw.color(Color.white);
                UnityDrawf.shiningCircle(unit.id, Time.time, wx, wy, 5f * scl, 5, 70f, 15f, 3f * scl, 90f);

                Lines.stroke(2f);
                Draw.color(Pal.lancerLaser);
                UnityDrawf.dashCircleAngle(wx, wy, rad, Mathf.sin(Time.time + Mathf.randomSeed(unit.id, 0f, 6f), 90f, 30f));
                Draw.reset();
            };
            // 蓄力逻辑: 范围内敌人持续受 800 伤害 (身体碰撞伤害), 死亡时存入 charge 化为激光充能
            chargeCondition = (unit, mount) -> {
                ChargeMount m = (ChargeMount)mount;
                if(mount.reload > 0f){
                    mount.reload = Math.max(mount.reload - Time.delta * unit.reloadMultiplier, 0);
                }
                if((m.timer += Time.delta) >= 5f){
                    float rotation = unit.rotation - 90f,
                    wx = unit.x + Angles.trnsx(rotation, x, y),
                    wy = unit.y + Angles.trnsy(rotation, x, y);

                    Units.nearbyEnemies(unit.team, wx, wy, rad, u -> {
                        u.damage(500f);
                        if(u.dead){
                            m.charge += Mathf.sqrt(u.maxHealth) * (u.isFlying() ? Mathf.clamp(u.type.fallSpeed * 5f) : 1f);
                            for(int i = 0; i < 4; i++){
                                Time.run(i * 5f, () ->
                                SpecialFx.chargeTransfer.at(u.x, u.y, 0f, Pal.lancerLaser, unit));
                            }
                        }
                    });

                    m.timer = 0f;
                }
                if(m.charge > 0f){
                    float v = Math.min(m.charge, Time.delta * 2f);
                    m.charge -= v;
                    mount.reload -= v;
                }
                if(mount.reload < -250f){
                    mount.reload = -250f;
                    m.charge = 0f;
                }
            };
        }});
    }
}