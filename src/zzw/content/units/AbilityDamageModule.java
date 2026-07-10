package zzw.content.units;

import mindustry.entities.abilities.Ability;
import mindustry.entities.abilities.RepairFieldAbility;
import mindustry.entities.abilities.StatusFieldAbility;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;

/**
 * 能力削弱模块 (移植自 PU132 AbilityDamageModule)
 * - 削弱 StatusFieldAbility (状态场)
 * - 削弱 RepairFieldAbility (修复场)
 * - 削弱 ForceFieldAbility (力场, 部分由 ForceFieldDamageModule 处理)
 */
public class AbilityDamageModule implements AntiCheatBulletModule {
    private final float minimumEfficiency;
    private final float maximumReload;
    private final float efficiencyDamage;
    private final float ratioDamage;
    private final float reloadDamage;

    public AbilityDamageModule(float minimumEfficiency, float maximumReload,
                               float efficiencyDamage, float ratioDamage, float reloadDamage) {
        this.minimumEfficiency = minimumEfficiency;
        this.maximumReload = maximumReload;
        this.efficiencyDamage = efficiencyDamage;
        this.ratioDamage = ratioDamage;
        this.reloadDamage = reloadDamage;
    }

    @Override
    public void handleAbility(Ability ability, Unit unit, Bullet bullet) {
        if (ability instanceof StatusFieldAbility s) {
            // 削弱状态场: 持续时间降到 minimumEfficiency
            if (s.duration > minimumEfficiency) {
                s.duration = Math.max(minimumEfficiency,
                    s.duration - Math.max(efficiencyDamage, s.duration * ratioDamage));
            }
            // 削长间隔: reload 增加到 maximumReload
            if (s.reload < maximumReload) {
                s.reload = Math.min(maximumReload,
                    s.reload + Math.max(reloadDamage, ratioDamage * s.reload));
            }
        } else if (ability instanceof RepairFieldAbility r) {
            if (r.amount > minimumEfficiency) {
                r.amount = Math.max(minimumEfficiency,
                    r.amount - Math.max(efficiencyDamage, r.amount * ratioDamage));
            }
            if (r.reload < maximumReload) {
                r.reload = Math.min(maximumReload,
                    r.reload + Math.max(reloadDamage, ratioDamage * r.reload));
            }
        }
    }
}
