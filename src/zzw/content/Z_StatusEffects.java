package zzw.content;

import arc.graphics.Color;
import mindustry.content.StatusEffects;
import mindustry.type.StatusEffect;
import zzw.content.units.effects.ParticleFx;

/**
 * PU132 自定义状态效果注册 (unity.content.UnityStatusEffects 按需移植)。
 *
 * <p>收录炮台武器弹所需的状态效果; 特效本体在 {@link ParticleFx}。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class Z_StatusEffects{

    /**
     * 蓝色燃烧 (PU132 UnityStatusEffects.blueBurn)。
     *
     * <p>celsius/kelvin 等 advance 派系低温炮…… 不对 —— advance 的"蓝焰"其实是
     * 冷色调的高温等离子燃烧: 0.14/s 持续伤害 + advance 色光尘, 与 wet/freezing 互斥。</p>
     */
    public static StatusEffect blueBurn;

    public static void load(){
        blueBurn = new StatusEffect("blue-burn"){{
            damage = 0.14f;
            effect = ParticleFx.blueBurnEffect;
            color = Color.valueOf("a3e3ff");

            init(() -> {
                opposite(StatusEffects.wet, StatusEffects.freezing);
            });
        }};
    }
}
