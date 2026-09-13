package zzw.content;

import arc.graphics.Blending;
import arc.graphics.Color;
import mindustry.content.Planets;
import mindustry.graphics.Pal;
import mindustry.graphics.g3d.HexMesh;
import mindustry.type.Planet;
import zzw.content.graphics.UnityPal;

/**
 * PU132 行星移植版 (unity.content.UnityPlanets)。
 *
 * <p>包含 monolith 派系母星 megalith 与 imber 派系行星 electrode / inert 卫星。</p>
 *
 * <p>★ v158 适配: PU132 的 megalith 使用自研 {@code CompositeMesh}(含 3D 星环模型
 * UnityModels.megalithring + 自定义星环 shader megalithRingShader), 这套渲染栈
 * (自定义 g3d 网格 + shader) 无法在 v158 原生行星管线中使用, 因此三个行星统一
 * 降级为原生 {@link HexMesh} 六边形网格; 氛围色 / 起始区块 / 可进入性等游戏属性
 * 与 PU132 一致。megalith 的星环视觉暂缺 (TODO: 如需还原需自研 PlanetMesh shader)。</p>
 *
 * <p>★ generator 说明: PU132 使用自研地形生成器 (unity.map.planets.MegalithPlanetGenerator 等),
 * 本移植未移植地形生成器; 两颗行星的战役区块由 {@link Z_SectorPresets} 的
 * msav 地图文件 (FileMapGenerator) 提供, 因此行星本体不带程序化生成器。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class Z_Planets{
    public static Planet megalith, electrode, inert;

    public static void load(){
        // megalith — monolith 母星 (起始区块 200, 可进入)
        megalith = new Planet("megalith", Planets.sun, 1f, 3){{
            // PU132: CompositeMesh + 星环 → v158 用原生六边形网格替代
            meshLoader = () -> new HexMesh(this, 6);
            accessible = true;
            atmosphereColor = UnityPal.monolithAtmosphere;
            startSector = 200;
            atmosphereRadIn = 0.04f;
            atmosphereRadOut = 0.35f;
        }};

        // electrode — imber 行星 (起始区块 30, 可进入)
        electrode = new Planet("electrode", Planets.sun, 1f, 3){{
            meshLoader = () -> new HexMesh(this, 6);
            accessible = true;
            atmosphereColor = Pal.surge;
            startSector = 30;
        }};

        // inert — electrode 的卫星 (半径减半, 不可进入, 深灰单色外观)
        inert = new Planet("inert", electrode, 0.5f){{
            atmosphereColor = Color.white.cpy();
            accessible = false;
            // PU132: ColorMesh 多面单色网格 → v158 用低细分 HexMesh 替代
            meshLoader = () -> new HexMesh(this, 3);
        }};
    }
}
