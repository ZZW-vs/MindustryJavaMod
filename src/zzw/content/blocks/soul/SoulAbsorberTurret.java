package zzw.content.blocks.soul;

import arc.math.Mathf;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Unit;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * 灵魂吸收炮台 (v158 移植版, 替代 PU_V8 SoulAbsorberTurret)
 *
 * = TractorBeamTurret (牵引光束伤害) + ISoulTurret (灵魂系统) + 产电机制
 *
 * 机制 (简化版, 与 PU_V8 部分一致):
 * - 持续激光伤害目标 (父类 TractorBeamTurret 已处理)
 * - ★产电公式: 基于目标单位的威胁度 (dpsEstimate * speed / scale)
 * - outputsPower=true, 是电力源
 *
 * ★ v158 简化说明:
 *   - PU_V8 GenericTractorBeamTurret 支持 Bullet/Unit/Building 三类目标
 *   - v158 TractorBeamTurret.target 是 Unit 类型, 不支持 Bullet/Building 目标
 *   - 因此 SoulAbsorberTurret 简化为仅吸收 Unit, 产电公式基于 Unit.dpsEstimate
 *
 * 参考: PU_V8 unity/world/blocks/defense/turrets/AbsorberTurret.java
 */
public class SoulAbsorberTurret extends SoulTractorBeamTurret {
    /** 电力产量基础值 */
    public float powerProduction = 2.5f;
    /** 抵抗强度 (影响产电效率, 灵魂数量会缩放) */
    public float resistance = 0.8f;
    /** 伤害缩放 (用于产电公式) */
    public float damageScale = 18f;
    /** 速度缩放 (用于产电公式) */
    public float speedScale = 3.5f;

    public SoulAbsorberTurret(String name) {
        super(name);
        outputsPower = true;  // ★ 作为电力源
        // ★ 不调用 consumePower(0f), 让 outputsPower=true 生效
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.basePowerGeneration, powerProduction * 60f, StatUnit.powerSecond);
    }

    public class SoulAbsorberTurretBuild extends SoulTractorBeamTurretBuild {
        /** 当前吸收强度 (基于 efficiency 平滑插值) */
        public float curStrength = 0f;

        @Override
        public void updateTile() {
            // 平滑更新吸收强度
            curStrength = Mathf.lerpDelta(curStrength, efficiency > 0 ? soulEfficiency() : 0f, 0.1f);
            // 调用父类更新 (含目标查找、激光渲染、单位伤害)
            super.updateTile();
        }

        @Override
        public float getPowerProduction() {
            // ★ 产电公式 (与 PU_V8 AbsorberTurret.getPowerProduction 一致):
            //   (unit.type.dpsEstimate / damageScale) * (unit.vel.len() / speedScale) * powerProduction * strength
            if (target == null || curStrength < 0.01f || target.dead()) {
                return 0f;
            }
            Unit u = target;
            float speed = u.vel().len() / speedScale;
            return (u.type.dpsEstimate / damageScale) * speed * powerProduction * curStrength;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(curStrength);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            curStrength = read.f();
        }
    }
}
