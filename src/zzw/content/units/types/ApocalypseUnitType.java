package zzw.content.units.types;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.gen.Unit;
import zzw.content.units.entities.EndInvisibleUnit;

/**
 * 天启单位类型 (apocalypse 专属, 继承通用隐身绘制类型 InvisibleUnitType)
 *
 * <p>在通用隐身渲染 (PU132 InvisibleUnitType 移植) 之上追加:</p>
 * <p>1. visualElevation 字段 (PU132 apocalypse = 3f, v155.4 已移除该字段);
 * <br>2. 机身后方 ±73.5 的两个小引擎喷口 (PU132 apocalypse drawEngine 覆写):
 *    位置 = 机身后方 105×offset 像素, 半径 = 3.25 + absin 呼吸,
 *    队伍色外圈 + 白色内芯, 隐身时跟随 fade 淡出。</p>
 */
public class ApocalypseUnitType extends InvisibleUnitType{
    /** 视觉悬浮高度 (PU132 UnitType.visualElevation, v155.4 已移除该字段, apocalypse=3f) */
    public float visualElevation = 3f;

    public ApocalypseUnitType(String name){
        super(name);
    }

    @Override
    public void drawEngines(Unit unit){
        if(!unit.isFlying()) return;

        super.drawEngines(unit);

        float scale = unit.elevation;

        for(int i : Mathf.signs){
            float offset = 0.5f + (0.5f * scale);
            float engineSizeB = 3.25f;
            Tmp.v1.trns(unit.rotation, -105f * offset, 73.5f * i).add(unit);
            Draw.color(unit.team.color);
            if(unit instanceof EndInvisibleUnit e) Draw.alpha(fade(e));
            Fill.circle(Tmp.v1.x, Tmp.v1.y, (engineSizeB + Mathf.absin(Time.time + 90f, 2f, engineSizeB / 2f)) * scale);
            Tmp.v1.trns(unit.rotation, (-105f * offset) + 1f, 74f * i).add(unit);
            Draw.color(Color.white);
            if(unit instanceof EndInvisibleUnit e) Draw.alpha(fade(e));
            Fill.circle(Tmp.v1.x, Tmp.v1.y, (engineSizeB + Mathf.absin(Time.time + 90f, 2f, engineSizeB / 2f)) / 2f * scale);
            Draw.color();
        }
    }
}
