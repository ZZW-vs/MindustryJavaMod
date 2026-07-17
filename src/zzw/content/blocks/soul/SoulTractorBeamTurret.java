package zzw.content.blocks.soul;

import arc.Core;
import arc.func.Func;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.turrets.TractorBeamTurret;
import mindustry.world.meta.Stat;

/**
 * 灵魂牵引光束炮台 (v158 简化版, 替代 PU_V8 SoulLifeStealerTurret/SoulAbsorberTurret/SoulHeatRayTurret)
 *
 * = TractorBeamTurret (持续激光, 拉拽/伤害) + ISoulTurret (灵魂系统)
 *
 * - 继承 v158 TractorBeamTurret, 实现 ISoulTurret 接口
 * - 灵魂数量影响 efficiency (0.7~1.8倍) 和 damage
 * - laserAlpha 回调: 控制激光透明度 (基于 power.status 和 soulf)
 * - 加载时若无自定义激光贴图, 使用 parallax 的激光贴图作为占位
 *
 * 参考: PU_V8 unity/content/UnityBlocks.java L2462-2732
 *       PU_V8 SoulLifeStealerTurretBuild/SoulAbsorberTurretBuild/SoulHeatRayTurretBuild (注解生成)
 */
public class SoulTractorBeamTurret extends TractorBeamTurret implements ISoulTurret {
    public int maxSouls = 3;
    public boolean requireSoul = false;
    public float efficiencyFrom = 0.7f;
    public float efficiencyTo = 1.5f;

    /** 基础伤害 (会按灵魂比例缩放) */
    public float baseDamage = 0f;

    /** 激光透明度回调 (基于 power.status 和 soulf) */
    public transient Func<SoulTractorBeamTurretBuild, Float> laserAlphaFunc = null;

    public SoulTractorBeamTurret(String name) {
        super(name);
    }

    /** 设置激光透明度回调 (PU_V8 laserAlpha) */
    public void laserAlpha(Func<SoulTractorBeamTurretBuild, Float> func) {
        this.laserAlphaFunc = func;
    }

    @Override
    public void load() {
        super.load();
        // 若未加载到自定义激光贴图, 使用 parallax 的激光贴图作为占位
        if (laser == null || !laser.found()) laser = Core.atlas.find("parallax-laser");
        if (laserStart == null || !laserStart.found()) laserStart = Core.atlas.find("parallax-laser-start");
        if (laserEnd == null || !laserEnd.found()) laserEnd = Core.atlas.find("parallax-laser-end");
    }

    @Override
    public void init() {
        super.init();
        if (baseDamage == 0f) baseDamage = damage;
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

    public class SoulTractorBeamTurretBuild extends TractorBeamBuild implements ISoulTurret {
        public int souls = 0;

        @Override
        public int souls() { return souls; }

        @Override
        public int maxSouls() { return SoulTractorBeamTurret.this.maxSouls; }

        @Override
        public boolean joinSoul() {
            if (souls < maxSouls) {
                souls++;
                return true;
            }
            return false;
        }

        @Override
        public boolean unjoinSoul() {
            if (souls > 0) {
                souls--;
                return true;
            }
            return false;
        }

        @Override
        public boolean requireSoul() { return SoulTractorBeamTurret.this.requireSoul; }

        @Override
        public float efficiencyFrom() { return SoulTractorBeamTurret.this.efficiencyFrom; }

        @Override
        public float efficiencyTo() { return SoulTractorBeamTurret.this.efficiencyTo; }

        /** 根据灵魂更新伤害 (复刻 PU_V8 progression.linear) */
        public void updateSoulDamage() {
            damage = baseDamage * soulEfficiency();
        }

        @Override
        public void updateTile() {
            // 灵魂影响伤害
            if (baseDamage > 0f) {
                damage = baseDamage * soulEfficiency();
            }
            super.updateTile();
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
