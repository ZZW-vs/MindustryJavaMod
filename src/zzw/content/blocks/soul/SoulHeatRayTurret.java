package zzw.content.blocks.soul;

import mindustry.content.StatusEffects;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * 灵魂热射线炮台 (v158 移植版, 替代 PU_V8 SoulHeatRayTurret)
 * 用于 heatRay / incandescence 炮台
 *
 * = SoulTractorBeamTurret (灵魂系统+牵引光束) + melting 状态施加
 *
 * 机制 (与 PU_V8 HeatRayTurret 一致):
 * - 持续激光伤害目标 (父类 TractorBeamTurret 内部已调用 damageContinuous, 不需要重复实现)
 * - 对 Unit 目标施加 melting 状态 (PU_V8 status=StatusEffects.melting, statusDuration=60f)
 * - targetAir/targetGround=true (可攻击空中和地面目标)
 *
 * ★ v158 适配:
 *   - v158 TractorBeamTurret.updateTile 内部已实现 status 施加 (当 status != StatusEffects.none 时)
 *   - 本类重写 updateTile 再次施加 status 以匹配 PU_V8 HeatRayTurret.apply 结构 (幂等操作, 仅刷新持续时间)
 *
 * 参考: PU_V8 unity/world/blocks/defense/turrets/HeatRayTurret.java
 */
public class SoulHeatRayTurret extends SoulTractorBeamTurret {

    public SoulHeatRayTurret(String name) {
        super(name);
        // PU_V8 HeatRayTurret 默认值
        targetAir = true;
        targetGround = true;
        status = StatusEffects.melting;
        statusDuration = 60f;
    }

    @Override
    public void setStats() {
        super.setStats();
        // 与 PU_V8 HeatRayTurret.setStats 一致
        stats.add(Stat.damage, baseDamage / 60f, StatUnit.perSecond);
        stats.add(Stat.targetsAir, targetAir);
        stats.add(Stat.targetsGround, targetGround);
        // 显示 status 信息 (PU_V8 TODO: display status)
        if (status != StatusEffects.none) {
            stats.add(Stat.abilities, t -> t.add("[lightgray]Status: [accent]" + status.localizedName));
        }
    }

    public class SoulHeatRayTurretBuild extends SoulTractorBeamTurretBuild {
        @Override
        public void updateTile() {
            // 调用父类更新 (含目标查找、激光渲染、伤害施加、状态施加)
            // 父类 TractorBeamTurret 内部已调用:
            //   target.damageContinuous(damage * eff * timeScale * state.rules.blockDamage(team))
            //   target.apply(status, statusDuration)  (当 status != StatusEffects.none)
            super.updateTile();

            // ★ PU_V8 HeatRayTurret.apply 等效逻辑: 显式施加状态
            // (父类已施加, 此处再次施加以匹配 PU_V8 结构, 幂等操作仅刷新持续时间)
            if (target != null && status != StatusEffects.none) {
                target.apply(status, statusDuration);
            }
        }
    }
}
