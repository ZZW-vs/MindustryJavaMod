package zzw.content.units.anticheat;

import mindustry.entities.abilities.Ability;
import mindustry.entities.abilities.ShieldRegenFieldAbility;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;

/**
 * 装甲/盾削弱模块 (移植自 PU132 ArmorDamageModule)
 * - 削甲 (armor): 护甲降到 minimumArmorShield
 * - 削盾 (shield): 护盾降到 minimumArmorShield
 */
public class ArmorDamageModule implements AntiCheatBulletModule {
    private final float armorDamage;
    private final float shieldDamage;
    private final float efficiencyDamage;
    private final float ratioDamage;

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
        this.minimumArmorShield = minAS;
        this.minimumEfficiency = minE;
        return this;
    }

    @Override
    public void hitUnit(Unit unit, Bullet bullet) {
        if (unit.armor() > minimumArmorShield) {
            unit.armor(Math.max(unit.armor() - Math.max(armorDamage, unit.armor() * ratioDamage), 0f));
            if (unit.armor() < minimumArmorShield) unit.armor(minimumArmorShield);
        }
        if (unit.shield() > minimumArmorShield) {
            unit.shield(Math.max(unit.shield() - Math.max(shieldDamage, unit.shield() * ratioDamage), 0f));
            if (unit.shield() < minimumArmorShield) unit.shield(minimumArmorShield);
        }
    }

    @Override
    public void handleAbility(Ability ability, Unit unit, Bullet bullet) {
        if (ability instanceof ShieldRegenFieldAbility s) {
            if (s.max > minimumEfficiency) {
                s.max = Math.max(minimumEfficiency,
                    s.max - Math.max(efficiencyDamage, s.max * ratioDamage));
                if (s.max < minimumEfficiency) s.max = minimumEfficiency;
            }
        }
    }
}
