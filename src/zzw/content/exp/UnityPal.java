package zzw.content.exp;

import arc.graphics.Color;
import mindustry.graphics.Pal;

/**
 * PU_V8 UnityPal 简化版 (仅保留经验系统所需颜色)
 * 参考: PU_V8 main/src/unity/graphics/UnityPal.java
 */
public class UnityPal {
    public static final Color
        exp = Color.valueOf("84ff00"),
        expMax = Color.valueOf("90ff00"),
        expBack = Color.valueOf("4d8f07"),
        expLaser = Color.valueOf("F9DBB1"),
        passive = Color.valueOf("61caff"),
        armor = Color.valueOf("e09e75"),
        // ===== TeleUnit 传送器所需颜色 (PU132 UnityPal) =====
        dirium = Color.valueOf("96f7c3"),
        diriumLight = Color.valueOf("ccffe4");

    public static final Color lancerLaser = Pal.lancerLaser;

    // ===== 经验激光炮台色阶 (PU132 UnityPal.lancerSap1..5) =====
    // 由 lancerLaser 向 sapBullet 逐级插值, 用于激光炮台的等级配色 (effectColors)
    public static final Color
        lancerSap1 = Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.167f),
        lancerSap2 = Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.333f),
        lancerSap3 = Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.5f),
        lancerSap4 = Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.667f),
        lancerSap5 = Pal.lancerLaser.cpy().lerp(Pal.sapBullet, 0.833f);
}
