package zzw.content.blocks.soul;

import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.world.blocks.defense.turrets.LaserTurret;
import mindustry.world.meta.Stat;

/**
 * 灵魂激光炮台基类 (v158 移植版)
 *
 * 等价于 PU_V8 @Merge(base = LaserTurret.class, value = Soulc.class)
 * = LaserTurret + ISoulTurret 接口实现
 *
 * - 继承 v158 LaserTurret (持续激光, 液体冷却)
 * - 实现 ISoulTurret 接口 (灵魂系统: 灵魂数量影响 efficiency 和 shootType.damage)
 * - SoulInfuser 建筑会扫描附近炮台, 调用 joinSoul() 添加灵魂
 *
 * 参考: PU_V8 unity/entities/merge/SoulComp.java + unity/world/blocks/defense/turrets/SoulLaserTurret.java
 */
public class SoulLaserTurret extends LaserTurret implements ISoulTurret {
    /** 最大灵魂数量 (默认3) */
    public int maxSouls = 3;
    /** 是否需要灵魂才能工作 (默认false, 即无灵魂也能以基础效率运行) */
    public boolean requireSoul = false;
    /** 灵魂效率起始值 */
    public float efficiencyFrom = 0.7f;
    /** 灵魂效率终止值 */
    public float efficiencyTo = 1.5f;

    public SoulLaserTurret(String name) {
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

    public class SoulLaserTurretBuild extends LaserTurretBuild implements ISoulTurret {
        public int souls = 0;
        /** baseDamage 缓存, 在 created 时记录 shootType.damage */
        public float baseDamage = 0f;

        @Override
        public int souls() { return souls; }

        @Override
        public int maxSouls() { return SoulLaserTurret.this.maxSouls; }

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
        public boolean requireSoul() { return SoulLaserTurret.this.requireSoul; }

        @Override
        public float efficiencyFrom() { return SoulLaserTurret.this.efficiencyFrom; }

        @Override
        public float efficiencyTo() { return SoulLaserTurret.this.efficiencyTo; }

        /** 根据灵魂更新子弹伤害: damage = baseDamage * (efficiencyFrom + soulf * (efficiencyTo - efficiencyFrom)) */
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

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(souls);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            souls = read.i();
        }
    }
}
