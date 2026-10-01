package zzw.content.exp;

import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;

/**
 * PU132 BurstChargePowerTurret 移植版 (连发蓄力经验能量炮台).
 *
 * <p>与普通蓄力炮台不同: 一次装填会按 {@link #burstSpacing} 的间隔依次蓄力发射
 * {@link #shots} 发子弹, 每发都独立播放 {@link #chargeBeginEffect} / {@link #chargeEffect}
 * 并在 {@link #chargeTime} 后真正打出。</p>
 *
 * <p>v160 适配:
 * <ul>
 *     <li>PU132 的 {@code effects()} 在 v160 不存在, 相关后坐/热量/特效由
 *         {@link ExpTurret.ExpTurretBuild#bullet} 统一处理, 这里不再重复。</li>
 *     <li>PU132 的 {@code tr} 字段在 v160 不存在, 改用 {@link Tmp#v1}。</li>
 *     <li>PU132 的 {@code charging} 标记改为: {@code shoot.firstShotDelay = chargeTime} 配合
 *         {@code queuedBullets}, 使 v160 的 {@code charging()} 成立, 从而在蓄力期间暂停装填。</li>
 * </ul></p>
 *
 * <p>参考: PU132 {@code unity/world/blocks/exp/turrets/BurstChargePowerTurret.java}。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class BurstChargePowerTurret extends ExpPowerTurret {
    /** 每发子弹的蓄力时间 (tick) */
    public float chargeTime = 50f;
    /** 蓄力期间播放蓄力特效的最大随机延迟 (tick) */
    public float chargeMaxDelay = 30f;
    /** 每发子弹蓄力期间播放蓄力特效的次数 */
    public int chargeEffects = 4;
    /** 蓄力过程中反复播放的特效 */
    public Effect chargeEffect;
    /** 蓄力开始瞬间播放的特效 */
    public Effect chargeBeginEffect;
    /** 连发间隔 (tick) */
    public float burstSpacing = 20f;
    /** 一次连发发射的子弹数 */
    public int shots = 4;
    /** 连发各发之间的角度间隔 (度) */
    public float spread = 0f;
    /** 是否左右交替发射 */
    public boolean alternate = false;
    /** 炮口长度, 用于蓄力特效与子弹的生成位置 */
    public float shootLength = 0f;

    public BurstChargePowerTurret(String name){
        super(name);
    }

    @Override
    public void init(){
        // 复刻 PU132 的 charging 标记:
        // v160 的 charging() = queuedBullets > 0 && shoot.firstShotDelay > 0。
        // 本类覆写了 shoot(), 因此 firstShotDelay 不会被引擎用于结算开火,
        // 仅用于在蓄力途中阻止换弹, 并驱动炮管的充能动画。
        if(shoot.firstShotDelay <= 0f){
            shoot.firstShotDelay = chargeTime;
        }
        super.init();
    }

    public class BurstChargeTurretBuild extends ExpPowerTurretBuild {

        /**
         * 单发蓄力发射.
         *
         * @param type        弹药类型
         * @param angleOffset 角度偏移 (连发散布)
         * @param lateral     横向偏移 (对应 PU132 的 xRand 随机量)
         */
        protected void shootCharge(BulletType type, float angleOffset, float lateral){
            Tmp.v1.trns(rotation, shootLength, lateral);
            float px = x + Tmp.v1.x, py = y + Tmp.v1.y;

            if(chargeBeginEffect != null){
                chargeBeginEffect.at(px, py, rotation);
            }
            chargeSound.at(px, py, 1f);

            for(int i = 0; i < chargeEffects; i++){
                Time.run(Mathf.random(chargeMaxDelay), () -> {
                    if(dead) return;
                    Tmp.v1.trns(rotation, shootLength, lateral);
                    if(chargeEffect != null){
                        chargeEffect.at(x + Tmp.v1.x, y + Tmp.v1.y, rotation);
                    }
                });
            }

            Time.run(chargeTime, () -> {
                if(dead) return;
                // ExpTurretBuild.bullet() 内部已处理 后坐/热量/特效/音效/弹药消耗 以及 xRand 横向散布
                // ★ 修复: v160 的 bullet() 会把 yOffset 叠加到默认的 shootY (size * tilesize / 2) 之上,
                //   若直接传 shootLength, 子弹实际生成在 shootY + shootLength 处 (比炮口偏前约 1 格),
                //   与上方蓄力特效 (按 shootLength 定位) 不一致。
                //   减去 shootY 后, 子弹与特效同在 shootLength 处, 与 PU132 原版一致。
                bullet(type, 0f, shootLength - shootY, angleOffset, null);
            });
        }

        @Override
        protected void shoot(BulletType type){
            if(chargeTime <= 0f){
                super.shoot(type);
                return;
            }

            if(burstSpacing > 0.0001f){
                for(int i = 0; i < shots; i++){
                    int ii = i;
                    queuedBullets++; // 供 charging() 判定, 蓄力期间暂停装填
                    Time.run(burstSpacing * i, () -> {
                        if(dead || !hasAmmo()) return;
                        shootCharge(peekAmmo(), (ii - (int)(shots / 2f)) * spread, Mathf.range(xRand));
                    });
                }
            }else{
                if(alternate){
                    float i = (barrelCounter % shots) - (shots - 1) / 2f;
                    queuedBullets++;
                    shootCharge(type, i * spread, Mathf.range(xRand));
                }else{
                    for(int i = 0; i < shots; i++){
                        queuedBullets++;
                        shootCharge(type, (i - (int)(shots / 2f)) * spread, Mathf.range(xRand));
                    }
                }
                barrelCounter++;
            }
        }
    }
}
