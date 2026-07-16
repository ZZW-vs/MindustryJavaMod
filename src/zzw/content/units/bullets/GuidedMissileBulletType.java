package zzw.content.units.bullets;

import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Bullet;

/**
 * 制导导弹子弹 (移植自 PU132 unity.entities.bullet.physical.GuidedMissileBulletType)
 * - 读取 b.data (WeaponMount) 的 aimX/aimY 作为目标位置
 * - 用 homingPower 转向目标
 * - 到达阈值后清除 data, 让导弹自由飞行
 * - targetingInaccuracy 添加随机散布避免导弹聚集
 * 注意: 需要武器在 handleBullet 中设置 b.data = mount
 */
public class GuidedMissileBulletType extends MissileBulletType {
    public float threshold = 1f;
    public float targetingInaccuracy = 17f;

    public GuidedMissileBulletType(float speed, float damage) {
        super(speed, damage);
    }

    @Override
    public void update(Bullet b) {
        if (b.data instanceof WeaponMount mount && homingPower > 0) {
            if (targetingInaccuracy > 0.001f) {
                Tmp.v1.trns(Mathf.randomSeed(b.id, 360f), Mathf.randomSeed(((long) b.id << 2L) + 351L, targetingInaccuracy));
            } else {
                Tmp.v1.setZero();
            }
            float ang = b.angleTo(mount.aimX + Tmp.v1.x, mount.aimY + Tmp.v1.y);
            b.rotation(Angles.moveToward(b.rotation(), ang, homingPower * Time.delta * 50f));
            if (Angles.within(b.rotation(), ang, threshold)) {
                b.data = null;
            }
        }
        super.update(b);
    }
}
