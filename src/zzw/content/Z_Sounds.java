package zzw.content;

import arc.audio.Sound;
import mindustry.Vars;

/**
 * 自定义音效注册 (模仿 PU132 UnitySounds)
 *
 * 音效文件在 assets/sounds/end/ 目录下
 * v158 加载规则: assets/sounds/ 下的 .ogg 文件通过 Vars.tree.loadSound() 加载
 */
public class Z_Sounds {
    public static Sound
        spaceFracture,    // 虚空碎裂弹武器射击音效 + spikes 命中音效
        fractureShoot,    // VoidFractureBulletType Phase 2 冲刺启动音效
        stopTime,         // 时间停止触发音效
        continueTime,    // 时间继续音效
        ravagerNightmareShoot,  // ravager 噩梦激光射击音效
        endBasicLarge,    // ravager 炮弹射击音效
        endMissile,       // ravager 小型炮台导弹射击音效
        endBasicSmall,    // 基础小型子弹射击音效
        endBasic,         // 基础子弹射击音效
        devourerMainLaser, // 噬界虫主激光射击音效
        xenoBeam,         // 西诺腐蚀者激光循环音效
        energyBolt,       // fmonolith 能量弹射击音效
        heatRay,          // fmonolith heat-ray 持续激光音效
        supernovaShoot,   // fmonolith supernova 射击音效
        supernovaActive;  // fmonolith supernova 激活循环音效

    public static void load() {
        spaceFracture = loadSound("end/space-fracture");
        fractureShoot = loadSound("end/fracture-shoot");
        stopTime = loadSound("end/stop-time");
        continueTime = loadSound("end/continue-time");
        ravagerNightmareShoot = loadSound("end/ravager-nightmare-shoot");
        endBasicLarge = loadSound("end/end-basic-large");
        endMissile = loadSound("end/end-missile");
        endBasicSmall = loadSound("end/end-basic-small");
        endBasic = loadSound("end/end-basic");
        devourerMainLaser = loadSound("end/devourer-main-laser");
        xenoBeam = loadSound("advance/xeno-beam");
        energyBolt = loadSound("energy-bolt");
        heatRay = loadSound("heat-ray");
        supernovaShoot = loadSound("monolith/supernova-shoot");
        supernovaActive = loadSound("monolith/supernova-active");
    }

    /**
     * 加载音效 (v158: Vars.tree.loadSound)
     * 音效文件路径: assets/sounds/{name}.ogg
     */
    private static Sound loadSound(String name) {
        return Vars.tree.loadSound(name);
    }
}
