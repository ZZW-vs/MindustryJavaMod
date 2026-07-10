package zzw.content.units;

import mindustry.content.Fx;
import mindustry.entities.abilities.Ability;
import mindustry.entities.abilities.ForceFieldAbility;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;

/**
 * 力场削弱模块 (移植自 PU132 ForceFieldDamageModule)
 * - 削弱 ForceFieldAbility (力场): 半径/最大盾/回盾速率全部削弱
 * - 破盾时播放 shieldBreak 特效
 */
public class ForceFieldDamageModule implements AntiCheatBulletModule {
    private final float maxRadius;
    private final float maxShield;
    private final float maxRegen;
    private final float ratioDamage;
    private final float damage;
    private final float cooldown;

    public ForceFieldDamageModule(float damage, float maxRadius, float maxShield,
                                  float maxRegen, float ratioDamage, float cooldown) {
        this.maxRadius = maxRadius;
        this.maxShield = maxShield;
        this.maxRegen = maxRegen;
        this.ratioDamage = ratioDamage;
        this.damage = damage;
        this.cooldown = cooldown;
    }

    public ForceFieldDamageModule(float damage, float maxRadius, float maxShield,
                                  float maxRegen, float ratioDamage) {
        this(damage, maxRadius, maxShield, maxRegen, ratioDamage, 0f);
    }

    @Override
    public float getUnitData(Unit unit) {
        return unit.shield();
    }

    @Override
    public void handleAbility(Ability ability, Unit unit, Bullet bullet) {
        if (ability instanceof ForceFieldAbility f) {
            if (f.regen > maxRegen) {
                f.regen = Math.max(maxRegen, f.regen - Math.max(damage / 5f, f.regen * ratioDamage));
            }
            if (f.max > maxShield) {
                f.max = Math.max(maxShield, f.max - Math.max(damage, f.max * ratioDamage));
            }
            if (f.radius > maxRadius + (unit.hitSize() / 2f)) {
                f.radius = Math.max(maxRadius + (unit.hitSize() / 2f),
                    f.radius - Math.max(damage, f.radius * ratioDamage));
            }
        }
    }

    @Override
    public void handleUnitPost(Unit unit, Bullet bullet, float data) {
        // data 是命中前的 shield, 如果之前有盾现在没了, 播放破盾特效
        if (data > 0f && unit.shield() <= 0f) {
            for (Ability a : unit.abilities()) {
                if (a instanceof ForceFieldAbility f) {
                    unit.shield(unit.shield() - Math.max(f.cooldown * f.regen, cooldown * f.regen));
                    Fx.shieldBreak.at(unit.x(), unit.y(), f.radius, unit.team().color);
                    break;
                }
            }
        }
    }
}
