package zzw.content;

import arc.audio.Sound;
import mindustry.Vars;

/**
 * 自定义音效注册 (模仿 PU132 UnitySounds)
 *
 * 音效文件在 assets/sounds/ 目录下
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
        continuousLaserB, // end 系持续激光武器射击音效 (apocalypseLaser/quetzalcoatl 主炮, PU132 UnitySounds.continuousLaserB)
        thalassophobiaLaser, // thalassophobia 主炮充能+射击音效 (PU132 UnitySounds.thalassophobiaLaser)
        xenoBeam,         // 西诺腐蚀者激光循环音效
        energyBolt,       // fmonolith 能量弹射击音效
        energyBlast,      // 能量类武器射击音效 (PU132 UnitySounds.energyBlast)
        heatRay,          // fmonolith heat-ray 持续激光音效
        supernovaShoot,   // fmonolith supernova 射击音效
        supernovaActive,  // fmonolith supernova 激活循环音效
        supernovaCharge,  // fmonolith supernova 充能音效 (PU_V8 UnitySounds.supernovaCharge)
        // light/ 目录炮台音效
        gluonShoot,       // gluon 炮台射击音效
        muonShoot,        // muon 炮台射击音效
        higgsBosonShoot,  // higgsBoson 炮台射击音效
        singularityShoot, // singularity 炮台射击音效
        wbosonShoot,      // wBoson 炮台射击音效
        zbosonShoot,      // zBoson 炮台射击音效
        ephemeronShoot,   // ephemeron 炮台射击音效
        // advance/ 目录炮台音效
        eclipseBeam,      // eclipse 炮台激光循环音效
        // dark/ 目录炮台音效
        extinctionShoot,            // extinction 炮台射击音效
        beamIntenseHighpitchTone,   // extinction 炮台激光循环音效
        // end/ 目录 tenmeikiri/endgame 音效
        tenmeikiriCharge,   // tenmeikiri 充能音效
        tenmeikiriShoot,    // tenmeikiri 射击音效
        endgameActive,      // endgame 循环音效
        endgameShoot,       // endgame 主射击音效
        endgameSmallShoot,  // endgame 副射击音效
        clang,              // 金属碰撞音效 (PU132 UnitySounds.clang)
        // Monolith 系列单位音效 (PU132 UnitySounds)
        chainyShot,         // pedestal/pilaster 蓄力霰弹射击音效
        energyCharge,       // tendence 能量环充能音效
        cubeBlast,          // the-cube 炮台射击音效
        continuousLaserA,   // 持续激光 A 循环音效
        oppressionLightning, // oppression 闪电音效
        shieldBreak,        // shielder 护盾破碎音效
        shielderShoot,      // shielder 射击音效
        kamiLaser,          // kami 激光音效
        kamiMasterspark,    // kami 大师火花音效
        kamiSansLaser,      // kami sans 激光音效
        kamiSegapower,      // kami segapower 音效
        kamiShootChime,     // kami 音铃射击音效
        kamiShootSimple,    // kami 简单射击音效
        kamiShootSimpleB,   // kami 简单射击 B 音效
        laserFreeze;        // frost-laser-turret 冻结音效

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
        continuousLaserB = loadSound("continuous-laser-b");
        thalassophobiaLaser = loadSound("end/thalassophobia-laser");
        xenoBeam = loadSound("advance/xeno-beam");
        energyBolt = loadSound("energy-bolt");
        energyBlast = loadSound("energy-blast");
        heatRay = loadSound("heat-ray");
        supernovaShoot = loadSound("monolith/supernova-shoot");
        supernovaActive = loadSound("monolith/supernova-active");
        supernovaCharge = loadSound("monolith/supernova-charge");

        // light/ 目录: light/gluon-shoot.ogg 等
        gluonShoot = loadSound("light/gluon-shoot");
        muonShoot = loadSound("light/muon-shoot");
        higgsBosonShoot = loadSound("light/higgs-boson-shoot");
        singularityShoot = loadSound("light/singularity-shoot");
        wbosonShoot = loadSound("light/wboson-shoot");
        zbosonShoot = loadSound("light/zboson-shoot");
        ephemeronShoot = loadSound("light/ephemeron-shoot");

        // advance/ 目录
        eclipseBeam = loadSound("advance/eclipse-beam");

        // dark/ 目录
        extinctionShoot = loadSound("dark/extinction-shoot");
        beamIntenseHighpitchTone = loadSound("dark/beam-intense-highpitch-tone");

        // end/ 目录 tenmeikiri/endgame
        tenmeikiriCharge = loadSound("end/tenmeikiri-charge");
        tenmeikiriShoot = loadSound("end/tenmeikiri-shoot");
        endgameActive = loadSound("end/endgame-active");
        endgameShoot = loadSound("end/endgame-shoot");
        endgameSmallShoot = loadSound("end/endgame-small-shoot");
        clang = loadSound("clang");

        // Monolith 系列单位音效
        chainyShot = loadSound("chainy-shot");
        energyCharge = loadSound("energy-charge");
cubeBlast = loadSound("advance/cube-blast");
continuousLaserA = loadSound("continuous-laser-a");
oppressionLightning = loadSound("end/oppression-lightning");
shieldBreak = loadSound("imber/shield-break");
shielderShoot = loadSound("imber/shielder-shoot");
kamiLaser = loadSound("kami/kami-laser");
kamiMasterspark = loadSound("kami/kami-masterspark");
kamiSansLaser = loadSound("kami/kami-sans-laser");
kamiSegapower = loadSound("kami/kami-segapower");
kamiShootChime = loadSound("kami/kami-shoot-chime");
kamiShootSimple = loadSound("kami/kami-shoot-simple");
kamiShootSimpleB = loadSound("kami/kami-shoot-simple-b");
laserFreeze = loadSound("koruh/laser-freeze");
    }

    /**
     * 加载音效 (v158: Vars.tree.loadSound)
     * 音效文件路径: assets/sounds/{name}.ogg
     */
    private static Sound loadSound(String name) {
        return Vars.tree.loadSound(name);
    }
}
