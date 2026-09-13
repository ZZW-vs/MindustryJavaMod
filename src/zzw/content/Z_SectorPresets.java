package zzw.content;

import mindustry.type.SectorPreset;

/**
 * PU132 区块预设移植版 (unity.content.UnitySectorPresets)。
 *
 * <p>megalith 行星上的两个脚本化战役区块, 地图文件 (msav) 已拷贝到本 mod
 * assets/maps/ 目录, 由 v158 的 {@code SectorPreset(name, mapName, planet, sector)}
 * 构造自动挂载 FileMapGenerator 读取。</p>
 *
 * <p>★ v158 适配: PU132 使用自研 {@code ScriptedSector}(unity.map 包, 带脚本化
 * 进攻波次/事件), 本移植降级为原生 SectorPreset —— 区块地图、难度、波次上限
 * 与 PU132 一致, 脚本化事件(如 accretion 的专属开场演出)暂缺。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class Z_SectorPresets{
    public static SectorPreset accretion, salvagedLab;

    public static void load(){
        // accretion — 吸积盘区块 (megalith 第 200 区): monolith 战役起点, 15 波
        accretion = new SectorPreset("accretion", "accretion", Z_Planets.megalith, 200){{
            alwaysUnlocked = true;
            addStartingItems = true;
            difficulty = 3f;
            captureWave = 15;
        }};

        // salvagedLab — 回收实验室 (megalith 第 100 区): 30 波高难
        salvagedLab = new SectorPreset("salvaged-laboratory", "salvaged-laboratory", Z_Planets.megalith, 100){{
            difficulty = 4f;
            captureWave = 30;
        }};
    }
}
