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

    /** 虚弱 (PU132 UnityStatusEffects.weaken): 伤害/生命 -25%, 移速 -50% */
    public static StatusEffect weaken;

    /** 瘫痪 (PU132 UnityStatusEffects.disabled): 移速/装填归零 + 缴械 */
    public static StatusEffect disabled;

    public static void load(){
        blueBurn = new StatusEffect("blue-burn"){{
            damage = 0.14f;
            effect = ParticleFx.blueBurnEffect;
            color = Color.valueOf("a3e3ff");

            init(() -> {
                opposite(StatusEffects.wet, StatusEffects.freezing);
            });
        }};

        // PU132 UnityStatusEffects.weaken: sedec/trigintaduo 治疗锥对敌人施加的削弱
        // (与原版 weaken 数值不同: 原版无 healthMultiplier)
        weaken = new StatusEffect("weaken"){{
            damageMultiplier = 0.75f;
            healthMultiplier = 0.75f;
            speedMultiplier = 0.5f;
        }};

        // PU132 UnityStatusEffects.disabled: trigintaduo 治疗核弹对敌人的瘫痪
        // (原版 unmoving 只锁移动, 这里的 disabled 额外归零装填并缴械)
        disabled = new StatusEffect("disabled"){{
            reloadMultiplier = 0f;
            speedMultiplier = 0f;
            disarm = true;
        }};
    }
}
