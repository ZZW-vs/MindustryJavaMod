package zzw.content.mechanics.torque;

import arc.graphics.Color;
import arc.math.Mathf;
import mindustry.graphics.Pal;
import zzw.content.graphics.UnityPal;

/**
 * PU_V8 unity.util.Utils 简化版
 * <p>
 * 包含扭矩系统所需的 linear 方法和坩埚系统所需的 tempColor 方法。
 */
public class Utils{
    public static float linear(float current, float target, float maxTorque, float coefficient){
        current = Math.min(target, current);

        return Math.min(coefficient * (target - current) * maxTorque / target, 99999f);
    }

    /**
     * 根据温度 (K) 返回对应的颜色 (PU132 unity.util.Utils.tempColor 移植)
     * <p>
     * 高温 (>273.15K): 返回红橙色 (Pal.turretHeat), 透明度随温度升高
     * 低温 (<=273.15K): 返回冷蓝色 (UnityPal.coldColor), 透明度随温度降低
     */
    public static Color tempColor(float temp){
        float a;
        if(temp > 273.15f){
            a = Math.max(0f, (temp - 498f) * 0.001f);
            if(a < 0.01f) return Color.clear.cpy();
            Color fcol = Pal.turretHeat.cpy().a(a);
            if(a > 1f){
                fcol.b += 0.01f * a;
                fcol.mul(a);
            }
            return fcol;
        }else{
            a = 1f - Mathf.clamp(temp / 273.15f);
            if(a < 0.01f) return Color.clear.cpy();
            return UnityPal.coldColor.cpy().a(a);
        }
    }
}
