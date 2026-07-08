package zzw.content.units;

import mindustry.entities.abilities.ShieldRegenFieldAbility;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;

/**
 * PU132 护甲/护盾削减模块
 * - 削减单位护甲、护盾
 * - 削减 ShieldRegenFieldAbility 的最大护盾值
 * - 取 max(固定削减, 比例削减) 双管齐下
 * 参考: PU132 main/src/unity/entities/bullet/anticheat/modules/ArmorDamageModule.java
 */
public class ArmorDamageModule implements AntiCheatBulletModule {
    private final float armorDamage, shieldDamage, efficiencyDamage, ratioDamage;
    private float minimumArmorShield = 20f;
    private float minimumEfficiency = 2f;

    public ArmorDamageModule(float armorDamage, float shieldDamage, float efficiencyDamage) {
        this.armorDamage = armorDamage;
        this.shieldDamage = shieldDamage;
        this.efficiencyDamage = efficiencyDamage;
        this.ratioDamage = 0f;
    }

    public ArmorDamageModule(float ratioDamage, float armorDamage, float shieldDamage, float efficiencyDamage) {
        this.armorDamage = armorDamage;
        this.shieldDamage = shieldDamage;
        this.ratioDamage = ratioDamage;
        this.efficiencyDamage = efficiencyDamage;
    }

    public ArmorDamageModule set(float minAS, float minE) {
        minimumArmorShield = minAS;
        minimumEfficiency = minE;
        return this;
    }

    @Override
    public void hitUnit(Unit unit, Bullet bullet) {
        // 削甲: 取 max(固定值, 比例值), 不低于最低值
        if (unit.armor > minimumArmorShield) {
            unit.armor = Math.max(unit.armor - Math.max(armorDamage, unit.armor * ratioDamage), 0f);
            if (unit.armor < minimumArmorShield) unit.armor = minimumArmorShield;
        }
        // 削盾
        if (unit.shield > minimumArmorShield) {
            unit.shield = Math.max(unit.shield - Math.max(shieldDamage, unit.shield * ratioDamage), 0f);
            if (unit.shield < minimumArmorShield) unit.shield = minimumArmorShield;
        }
    }

    @Override
    public void handleAbility(mindustry.entities.abilities.Ability ability, Unit unit, Bullet bullet) {
        // 削减护盾再生力场的最大值
        if (ability instanceof ShieldRegenFieldAbility s) {
            if (s.max > minimumEfficiency) {
                s.max = Math.max(s.max - Math.max(efficiencyDamage, s.max * ratioDamage), 0f);
                if (s.max < minimumEfficiency) s.max = minimumEfficiency;
            }
        }
    }
}
