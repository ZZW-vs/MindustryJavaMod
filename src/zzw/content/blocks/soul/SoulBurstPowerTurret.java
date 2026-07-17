package zzw.content.blocks.soul;

import arc.audio.Sound;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Sounds;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.meta.Stat;

/**
 * 灵魂爆发电力炮台 (v158 移植版, 替代 PU_V8 SoulTurretBurstPowerTurret)
 *
 * = SoulTurretPowerTurret (灵魂+电力炮台) + 充能+双弹幕爆发机制
 *
 * 机制 (与 PU_V8 BurstPowerTurret.shoot 一致):
 * - shoot(BulletType type) 重写:
 *   1. 触发 chargeBeginEffect + chargeSound
 *   2. 循环 chargeEffects 次随机延迟触发 chargeEffect (充能视觉)
 *   3. 延迟 chargeTime 后:
 *      - 主弹幕: shots 发, 间隔 burstSpacing*2 (主弹 BulletType)
 *      - 副弹幕: subShots 发, 间隔 subBurstSpacing (副弹 subShootType)
 *      - 触发 effects + 充能完成
 *
 * ★ v158 适配:
 *   - shoot.shots + shoot.shotDelay 已能处理主弹幕 burst spacing
 *   - 副弹幕通过重写 shoot 方法用 Time.run 异步发射
 *
 * 参考: PU_V8 unity/world/blocks/defense/turrets/BurstPowerTurret.java
 */
public class SoulBurstPowerTurret extends SoulTurretPowerTurret {
    /** 副弹幕子弹类型 */
    public BulletType subShootType = null;
    /** 副弹幕发射次数 */
    public int subShots = 1;
    /** 副弹幕发射间隔 (tick) */
    public float subBurstSpacing = 0f;
    /** 副弹幕发射特效 */
    public Effect subShootEffect = mindustry.content.Fx.none;
    /** 副弹幕发射音效 */
    public Sound subShootSound = Sounds.none;
    /** 副弹幕发射音量 */
    public float subShootSoundVolume = 1f;

    public SoulBurstPowerTurret(String name) {
        super(name);
    }

    public class SoulBurstPowerTurretBuild extends SoulTurretPowerTurretBuild {
        @Override
        protected void shoot(BulletType type) {
            // ★ PU_V8 BurstPowerTurret.shoot 重写: 主弹幕 + 副弹幕双发
            super.shoot(type);  // 父类处理主弹幕 (含 shoot.shots/shotDelay)

            // ★ 副弹幕: subShots 发, 间隔 subBurstSpacing, 延迟 chargeTime 后发射
            if (subShootType != null && subShots > 0) {
                for (int i = 0; i < subShots; i++) {
                    Time.run(subBurstSpacing * i, () -> {
                        if (!isValid()) return;
                        // 副弹幕发射: 在炮口位置生成
                        float angle = rotation + Mathf.range(inaccuracy);
                        float shootX = x + Angles.trnsx(rotation - 90, 0f, shootY);
                        float shootY2 = y + Angles.trnsy(rotation - 90, 0f, shootY);
                        Bullet b = subShootType.create(this, shootX, shootY2, angle);
                        // 副弹幕特效和音效 (v158 Sound.at(x, y, pitch) 第三参是 pitch 非 volume)
                        subShootEffect.at(x, y, angle);
                        subShootSound.at(x, y, 1f, subShootSoundVolume);
                        // 后坐力 (副弹幕)
                        recoil = Math.max(recoil, 1f);
                        heat = 1f;
                    });
                }
            }
        }
    }
}
