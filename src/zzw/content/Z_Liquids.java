package zzw.content;

import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.game.EventType.Trigger;
import mindustry.type.Liquid;

/**
 * 自定义液体注册 (移植自 PU_V8 UnityLiquids)
 * - lava: 熔岩, 颜色在 #ff2a00(红橙) 和 #ffcc00(金黄) 之间脉动
 *   对单位施加 melting 状态 (v158 等价 PU 的 molten)
 */
public class Z_Liquids {
    /** 熔岩颜色1 (红橙) */
    public static final Color lavaColor = new Color(0xff2a00ff);
    /** 熔岩颜色2 (金黄) */
    public static final Color lavaColor2 = new Color(0xffcc00ff);

    /** 熔岩液体 */
    public static Liquid lava;

    private static final Color temp = new Color();

    public static void load() {
        lava = new Liquid("lava", lavaColor) {{
            heatCapacity = 0f;
            viscosity = 0.7f;
            temperature = 1.5f;
            // PU 用 molten, v158 用 melting (功能相似: 持续伤害+减速)
            effect = mindustry.content.StatusEffects.melting;
            lightColor = lavaColor2.cpy().mul(1f, 1f, 1f, 0.55f);
        }};

        // 动态颜色: 颜色在 lavaColor 和 lavaColor2 之间脉动 (PU_V8 原版逻辑)
        if (!mindustry.Vars.headless) {
            Events.run(Trigger.update, () -> {
                lava.color = temp.set(lavaColor).lerp(lavaColor2, Mathf.absin(Time.globalTime, 25f, 1f));
            });
        }
    }
}
