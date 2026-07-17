package zzw.content.blocks.soul;

import arc.func.Boolf;
import arc.math.Mathf;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.meta.Stat;

/**
 * 灵魂电力炮台基类 (v158 简化版, 替代 PU_V8 SoulTurretPowerTurret @Merge 注解生成类)
 *
 * - 继承 PowerTurret, 添加 souls/maxSouls 字段
 * - SoulInfuser 建筑会扫描附近炮台, 调用 joinSoul() 添加灵魂
 * - 灵魂数量影响 efficiency (0.7~1.8倍) 和 shootType.damage
 *
 * 参考: PU_V8 unity/entities/merge/SoulComp.java + unity/content/UnityBlocks.java L2412-2710
 */
public class SoulTurretPowerTurret extends PowerTurret implements ISoulTurret {
    /** 最大灵魂数量 (默认3, 部分炮台5/7/12) */
    public int maxSouls = 3;
    /** 是否需要灵魂才能工作 (默认false, 即无灵魂也能以基础效率运行) */
    public boolean requireSoul = false;
    /** 灵魂效率起始值 */
    public float efficiencyFrom = 0.7f;
    /** 灵魂效率终止值 */
    public float efficiencyTo = 1.5f;

    /** 子弹伤害随灵魂线性变化: damage = baseDamage * (efficiencyFrom + soulf * (efficiencyTo - efficiencyFrom)) */
    public Boolf<Building> damageScalesWithSoul = b -> true;

    public SoulTurretPowerTurret(String name) {
        super(name);
    }

    @Override
    public int souls() { return 0; }

    @Override
    public int maxSouls() { return maxSouls; }

    @Override
    public boolean joinSoul() { return false; }

    @Override
    public boolean unjoinSoul() { return false; }

    @Override
    public boolean requireSoul() { return requireSoul; }

    @Override
    public float efficiencyFrom() { return efficiencyFrom; }

    @Override
    public float efficiencyTo() { return efficiencyTo; }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.abilities, (table) -> {
            table.row().table(bt -> {
                bt.left().defaults().padRight(3).left();
                bt.row();
                bt.add(requireSoul ? "@soul.require" : "@soul.optional");
                if (maxSouls > 0) {
                    bt.row();
                    bt.add("[lightgray]Max souls: [accent]" + maxSouls);
                }
            });
        });
    }

    public class SoulTurretPowerTurretBuild extends PowerTurretBuild implements ISoulTurret {
        public int souls = 0;
        /** baseDamage 缓存, 在 created 时记录 shootType.damage */
        public float baseDamage = 0f;

        @Override
        public int souls() { return souls; }

        @Override
        public int maxSouls() { return SoulTurretPowerTurret.this.maxSouls; }

        @Override
        public boolean joinSoul() {
            if (souls < maxSouls) {
                souls++;
                updateSoulDamage();
                return true;
            }
            return false;
        }

        @Override
        public boolean unjoinSoul() {
            if (souls > 0) {
                souls--;
                updateSoulDamage();
                return true;
            }
            return false;
        }

        @Override
        public boolean requireSoul() { return SoulTurretPowerTurret.this.requireSoul; }

        @Override
        public float efficiencyFrom() { return SoulTurretPowerTurret.this.efficiencyFrom; }

        @Override
        public float efficiencyTo() { return SoulTurretPowerTurret.this.efficiencyTo; }

        /** 根据灵魂更新子弹伤害 */
        public void updateSoulDamage() {
            if (baseDamage > 0f && shootType != null) {
                float scale = efficiencyFrom() + soulf() * (efficiencyTo() - efficiencyFrom());
                shootType.damage = baseDamage * scale;
            }
        }

        @Override
        public void updateTile() {
            super.updateTile();
            // v158 中 efficiency 是字段, 通过乘以灵魂倍率调整
            efficiency *= soulEfficiency();
        }

        @Override
        public void created() {
            super.created();
            if (shootType != null) {
                baseDamage = shootType.damage;
                updateSoulDamage();
            }
        }
    }
}
