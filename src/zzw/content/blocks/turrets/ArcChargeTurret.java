package zzw.content.blocks.turrets;

import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.bullet.BulletType;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/**
 * 蓄力电弧炮台 (arc-caster / arc-storm 专用)。
 *
 * <p>PU132 原版在发射前有 {@code chargeTime=51} 的蓄力阶段, 期间会以
 * {@code chargeMaxDelay} 为上限的随机间隔播放 {@code chargeEffects} 次
 * {@code chargeEffect} (arcCharge) 六边形群特效。</p>
 *
 * <p>v158 的 {@code Turret} 只在 {@code shoot.firstShotDelay > 0} 时播放一次
 * {@link BulletType#chargeEffect}, 无法复刻这段循环蓄力。本类通过覆写
 * {@code shoot()} 在蓄力窗口内手动调度多次充能特效来还原原版表现。</p>
 *
 * @author zzw
 */
public class ArcChargeTurret extends PowerTurret {

    /** 蓄力期间播放的充能特效次数 (PU132 chargeEffects) */
    public int chargeEffects = 5;
    /** 充能特效随机延迟上限 (PU132 chargeMaxDelay) */
    public float chargeMaxDelay = 24f;

    public ArcChargeTurret(String name) {
        super(name);
    }

    public class ArcChargeTurretBuild extends PowerTurretBuild {

        @Override
        protected void shoot(BulletType type) {
            // ★ 蓄力阶段: 在 firstShotDelay 窗口内随机播放多次 arcCharge 六边形群
            float delay = shoot.firstShotDelay;
            if (delay > 0f && type.chargeEffect != null) {
                for (int i = 0; i < chargeEffects; i++) {
                    float at = Mathf.random(chargeMaxDelay);
                    Time.run(at, () -> {
                        if (!isValid()) return;
                        float ox = Angles.trnsx(rotation, shootY);
                        float oy = Angles.trnsy(rotation, shootY);
                        type.chargeEffect.at(x + ox, y + oy, rotation);
                    });
                }
            }
            // 父类实现: 播放一次 chargeEffect + chargeSound, 并按 firstShotDelay 延迟发射
            super.shoot(type);
        }
    }
}