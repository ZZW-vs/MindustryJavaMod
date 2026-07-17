package zzw.content.blocks.turrets;

import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.math.Interp;
import arc.util.Tmp;
import mindustry.world.blocks.defense.turrets.LaserTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.draw.DrawTurret;

/**
 * 大型激光炮台 (PU_V8 BigLaserTurret 移植版)
 * 继承 LaserTurret, 自定义过热贴图渲染 (加法混合 + 自定义颜色曲线)
 * 简化: 用 Interp.pow5In 替代 PU_V8 Utils.pow6In (v158 无 Utils 类)
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/BigLaserTurret.java
 */
public class BigLaserTurret extends LaserTurret {

    public BigLaserTurret(String name) {
        super(name);
        // v158 无 heatDrawer 字段, 通过自定义 DrawTurret 的 drawHeat 方法实现
        drawer = new DrawTurret() {
            @Override
            public void drawHeat(Turret block, TurretBuild build) {
                if (build.heat <= 0.00001f) return;

                float r = Interp.pow2Out.apply(build.heat);
                float g = Interp.pow3In.apply(build.heat) + ((1 - Interp.pow3In.apply(build.heat)) * 0.12f);
                float b = Interp.pow5In.apply(build.heat);
                float a = Interp.pow2Out.apply(build.heat);

                Tmp.c1.set(r, g, b, a);
                Draw.color(Tmp.c1);
                Draw.blend(Blending.additive);
                Draw.rect(heat, build.x + build.recoilOffset.x, build.y + build.recoilOffset.y, build.drawrot());
                Draw.color();
                Draw.blend();
            }
        };
    }
}
