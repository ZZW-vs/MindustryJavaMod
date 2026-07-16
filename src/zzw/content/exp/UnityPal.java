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
        armor = Color.valueOf("e09e75");

    public static final Color lancerLaser = Pal.lancerLaser;
}
