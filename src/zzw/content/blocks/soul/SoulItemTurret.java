package zzw.content.blocks.soul;

import arc.struct.ObjectMap;
import arc.struct.OrderedMap;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.entities.bullet.BulletType;
import mindustry.type.Item;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;

/**
 * 灵魂物品炮台 (v158 简化版, 替代 PU_V8 SoulTurretItemTurret @Merge 注解生成类)
 *
 * = ItemTurret (物品炮台) + ISoulTurret (灵魂系统)
 *
 * - 继承 v158 ItemTurret, 实现 ISoulTurret 接口
 * - 灵魂数量影响 efficiency (0.7~1.8倍) 和子弹 damage
 * - 在 created() 时记录每种 ammo 的 baseDamage, 在 joinSoul 时按比例缩放
 *
 * 参考: PU_V8 unity/content/UnityBlocks.java L2477-2503 (recluse)
 */
public class SoulItemTurret extends ItemTurret implements ISoulTurret {
    public int maxSouls = 3;
    public boolean requireSoul = false;
    public float efficiencyFrom = 0.7f;
    public float efficiencyTo = 1.5f;

    public SoulItemTurret(String name) {
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

    public class SoulItemTurretBuild extends ItemTurretBuild implements ISoulTurret {
        public int souls = 0;
        /** 缓存每种 ammo 的基础伤害 (按 item 区分) */
        public ObjectMap<Item, Float> baseDamages = new ObjectMap<>();

        @Override
        public int souls() { return souls; }

        @Override
        public int maxSouls() { return SoulItemTurret.this.maxSouls; }

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
        public boolean requireSoul() { return SoulItemTurret.this.requireSoul; }

        @Override
        public float efficiencyFrom() { return SoulItemTurret.this.efficiencyFrom; }

        @Override
        public float efficiencyTo() { return SoulItemTurret.this.efficiencyTo; }

        /** 根据灵魂更新所有 ammo 的伤害 (复刻 PU_V8 progression.linear) */
        public void updateSoulDamage() {
            for (var e : baseDamages.entries()) {
                BulletType b = ammoTypes.get(e.key);
                if (b != null) {
                    b.damage = e.value * soulEfficiency();
                }
            }
        }

        @Override
        public void created() {
            super.created();
            // 缓存所有 ammo 的基础伤害
            for (var e : ammoTypes.entries()) {
                if (e.value != null && e.value.damage > 0f) {
                    baseDamages.put(e.key, e.value.damage);
                }
            }
            updateSoulDamage();
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
