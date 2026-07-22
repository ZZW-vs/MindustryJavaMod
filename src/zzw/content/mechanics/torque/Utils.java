package zzw.content.mechanics.torque;

/**
 * PU_V8 unity.util.Utils 简化版 (仅移植扭矩系统所需的 linear 方法)
 *
 * 参考: PU_V8 main/src/unity/util/Utils.java L959-963
 */
public class Utils{
    public static float linear(float current, float target, float maxTorque, float coefficient){
        current = Math.min(target, current);

        return Math.min(coefficient * (target - current) * maxTorque / target, 99999f);
    }
}
