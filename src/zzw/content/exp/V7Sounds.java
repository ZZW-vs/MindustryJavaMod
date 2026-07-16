package zzw.content.exp;

import arc.audio.Sound;
import mindustry.gen.Sounds;

/**
 * PU_V8 V7Sounds (移植, 仅保留经验系统使用的音效)
 * 参考: PU_V8 main/src/unity/v8/V7Sounds.java
 */
public class V7Sounds {
    public static Sound
        message = Sounds.uiNotify,
        spray = Sounds.loopSpray,
        laser = Sounds.shootLancer,
        plasmadrop = Sounds.shootQuad;
}
