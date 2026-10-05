package zzw.content.units.kami;

import arc.func.Floatc;
import arc.func.Floatc2;
import arc.math.Mathf;

import zzw.content.units.KamiAI;

/**
 * 弹幕生成预设 (PU132 {@code unity/ai/kami/KamiBulletPresets.java} 的移植)。
 *
 * <p>提供沿一条线按密度散布子弹、以及"花瓣"式延迟散射等通用工具。</p>
 */
public class KamiBulletPresets {
    /** 单元素 {0} 数组, 用于"只朝一个方向"时替代 Mathf.signs */
    public static final int[] zero = {0};

    /** 花瓣密度曲线 (入参 0~1) */
    public interface Spacing {
        float get(float fin);
    }

    /** 在 from~to 之间按 density 等距取密度值 (speed 值) */
    public static void shootLine(float from, float to, int density, Floatc c){
        for(int i = 0; i < density; i++){
            float fin = Mathf.lerp(from, to, i / ((float) density - 1f));
            if(Float.isNaN(fin)) fin = to;
            c.get(fin);
        }
    }

    /** 同上, 但回调额外携带索引 j */
    public static void shootLine(float from, float to, int density, Floatc2 c){
        for(int i = 0; i < density; i++){
            float fin = Mathf.lerp(from, to, i / ((float) density - 1f));
            if(Float.isNaN(fin)) fin = to;
            c.get(fin, i);
        }
    }

    /**
     * 生成一片"花瓣"弹幕 (PU132 {@code petal})。
     *
     * <p>把 amount 发子弹按时间均摊, 每发通过 {@code ai.run(delay, ...)} 延迟发射;
     * 首发只朝单侧, 其余发双向对称。</p>
     */
    public static void petal(KamiAI ai, float angleCone, float time, int amount, Spacing spacingF, Floatc2 cons){
        for(int i = 0; i < amount; i++){
            float fin = i / (amount - 1f);
            int[] sign = i <= 0 ? zero : Mathf.signs;
            float delay = fin * time, angle = spacingF.get(fin) * angleCone;
            ai.run(delay, () -> {
                for(int s : sign){
                    cons.get(angle * s, delay);
                }
            });
        }
    }
}
