package zzw.content.units.bullets;

import arc.graphics.Color;
import zzw.content.units.bullets.AcceleratingLaserBulletType;

/**
 * WavefrontLaser - wavefront 炮台武器子弹 (PU_V8 移植)
 *
 * 配置:
 * - lifetime = 90f
 * - collisionWidth = 22f, width = 50f (粗激光)
 * - maxLength = 450f (长激光)
 * - accel = 40f, laserSpeed = 40f (加速)
 * - 颜色: 浅蓝 (advance) 渐变到白色
 *
 * 参考: PU_V8 main/src/unity/entities/bullet/laser/WavefrontLaser.java
 */
public class WavefrontLaserBulletType extends AcceleratingLaserBulletType {
    // PU_V8 UnityPal 颜色
    private static final Color ADVANCE = Color.valueOf("a3e3ff");
    private static final Color ADVANCE_DARK = Color.valueOf("59a7ff");

    public WavefrontLaserBulletType(float damage) {
        super(damage);
        lifetime = 90f;
        collisionWidth = 22f;
        width = 50f;
        maxLength = 450f;
        accel = 40f;
        laserSpeed = 40f;
        colors = new Color[]{
            ADVANCE_DARK.cpy().mul(0.9f, 1f, 1f, 0.4f),
            ADVANCE_DARK.cpy(),
            ADVANCE.cpy(),
            Color.white
        };
    }
}
