package zzw.content.blocks.soul;

import arc.Core;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * 灵魂吸收炮台 (v158 移植版, 替代 PU_V8 SoulAbsorberTurret)
 *
 * = SoulTractorBeamTurret (灵魂系统+牵引光束) + 产电机制 + Bullet/Unit/Building 三类目标支持
 *
 * 机制 (与 PU_V8 AbsorberTurret 一致):
 * - 持续激光伤害 Unit 目标 (父类 TractorBeamTurret 已处理)
 * - 额外支持 Bullet 目标 (targetBullets=true):
 *   - 减速子弹: bullet.vel.setLength(max(vel.len() - resistance*curStrength, 0))
 *   - 削减伤害: bullet.damage -= resistance/2 * curStrength * delta
 *   - 若 vel=0 或 damage<=0, 移除子弹
 * - 产电公式 (getPowerProduction):
 *   - Bullet: (bullet.type.damage / damageScale) * (bullet.vel.len() / speedScale) * powerProduction * curStrength
 *   - Unit:   (unit.type.dpsEstimate / damageScale) * (unit.vel.len() / speedScale) * powerProduction * curStrength
 *   - Building: 未实现 (PU_V8 TODO)
 * - outputsPower=true, 是电力源 (不调用 consumePower)
 *
 * ★ v158 适配:
 *   - v158 TractorBeamTurret.target 是 Unit 类型, 不支持 Bullet/Building 目标
 *   - 用 bulletTarget 字段单独存储 Bullet 目标
 *   - 父类 TractorBeamTurret 自动查找 Unit 目标, 本类额外查找 Bullet 目标 (当 targetBullets=true 且无 Unit 目标时)
 *
 * 参考: PU_V8 unity/world/blocks/defense/turrets/AbsorberTurret.java
 */
public class SoulAbsorberTurret extends SoulTractorBeamTurret {
    /** 电力产量基础值 */
    public float powerProduction = 2.5f;
    /** 抵抗强度 (减速子弹/削减伤害, 灵魂数量会缩放) */
    public float resistance = 0.4f;
    /** 伤害缩放 (用于产电公式) */
    public float damageScale = 18f;
    /** 速度缩放 (用于产电公式) */
    public float speedScale = 3.5f;

    /** 施加给 Unit 目标的状态 (PU_V8 status, 可为 null) */
    public StatusEffect status;

    /** 启用 Bullet 目标查找 (PU_V8 targetBullets) */
    public boolean targetBullets = false;
    /** 启用 Unit 目标查找 (PU_V8 targetUnits, 父类默认已查 Unit) */
    public boolean targetUnits = false;
    /** 启用 Building 目标查找 (PU_V8 targetBuildings) */
    public boolean targetBuildings = false;

    /** Building 目标候选列表 (PU_V8 buildings 字段) */
    private Seq<mindustry.gen.Building> buildings = new Seq<>();

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

    @Override
    public void setBars() {
        super.setBars();
        // PU_V8 AbsorberTurret.setBars: 添加 power bar 显示当前产电
        addBar("power", (SoulAbsorberTurretBuild entity) -> new Bar(() ->
            Core.bundle.format("bar.poweroutput",
            Strings.fixed(entity.getPowerProduction() * 60f * entity.timeScale(), 1)),
            () -> Pal.powerBar,
            () -> entity.getPowerProduction() / powerProduction)
        );
    }

    public class SoulAbsorberTurretBuild extends SoulTractorBeamTurretBuild {
        /** 当前吸收强度 (基于 efficiency 平滑插值, PU_V8 strength) */
        public float curStrength = 0f;
        /** Bullet 目标 (v158 TractorBeamTurret.target 是 Unit 类型, 需单独字段存 Bullet) */
        public Bullet bulletTarget = null;
        /** Building 目标 (PU_V8 targetBuildings 分支) */
        public mindustry.gen.Building buildingTarget = null;

        @Override
        public void updateTile() {
            // ★ 平滑更新 curStrength (PU_V8 GenericTractorBeamTurret.updateTile strength lerp 逻辑)
            // 用 soulEfficiency() 替代 efficiency 以体现灵魂影响
            curStrength = Mathf.lerpDelta(curStrength, efficiency > 0 ? soulEfficiency() : 0f, 0.1f);

            // 调用父类更新 (含 Unit 目标查找、激光渲染、单位伤害)
            // 父类会自动设置 any/strength/lastX/lastY 并调用 target.damageContinuous
            super.updateTile();

            // ★ 若启用 Bullet 目标且父类未找到 Unit 目标, 查找并处理 Bullet 目标
            if (targetBullets && target == null) {
                findBulletTarget();
                applyBullet();
            } else if (bulletTarget != null) {
                // 已有 Unit 目标时清空 Bullet 目标
                bulletTarget = null;
            }

            // ★ 若启用 Building 目标且无 Unit/Bullet 目标, 查找 Building 目标
            if (targetBuildings && target == null && bulletTarget == null) {
                findBuildingTarget();
                applyBuilding();
            } else if (buildingTarget != null) {
                buildingTarget = null;
            }
        }

        /** 查找范围内最近的敌方 hittable 子弹 (PU_V8 AbsorberTurret.findTarget Bullet 分支) */
        protected void findBulletTarget() {
            bulletTarget = Groups.bullet
                .intersect(x - range, y - range, range * 2f, range * 2f)
                .min(b -> b.team != team && b.type != null && b.type.hittable, b -> b.dst2(x, y));
        }

        /** 查找范围内最近的敌方建筑 (PU_V8 AbsorberTurret.findTarget Building 分支) */
        protected void findBuildingTarget() {
            buildings.clear();
            Vars.indexer.eachBlock(null, x, y, range, b -> b.team != team && !b.dead, buildings::add);
            buildingTarget = buildings.min(b -> b.dst2(x, y));
        }

        /**
         * 对 Bullet 目标应用效果 (PU_V8 AbsorberTurret.apply Bullet 分支):
         * - 减速: bullet.vel.setLength(max(vel.len() - resistance*curStrength, 0))
         * - 削减伤害: bullet.damage = max(damage - resistance/2 * curStrength * delta, 0)
         * - 若 vel=0 或 damage<=0, 移除子弹
         * - 同时设置激光渲染参数 (让父类的 draw 能渲染激光指向 Bullet)
         */
        protected void applyBullet() {
            if (bulletTarget == null || !bulletTarget.isAdded()) {
                bulletTarget = null;
                return;
            }

            // 设置激光渲染参数 (让 SoulTractorBeamTurret.draw 能渲染激光指向 Bullet)
            any = true;
            lastX = bulletTarget.x;
            lastY = bulletTarget.y;
            strength = Mathf.lerpDelta(strength, 1f, 0.1f);

            // PU_V8 AbsorberTurret.apply Bullet 分支
            Bullet bullet = bulletTarget;
            bullet.vel.setLength(Math.max(bullet.vel.len() - resistance * curStrength, 0f));
            bullet.damage = Math.max(bullet.damage - (resistance / 2f) * curStrength * Time.delta, 0f);

            if (bullet.vel.isZero(0.01f) || bullet.damage <= 0f) {
                bullet.remove();
                bulletTarget = null;
            }
        }

        /**
         * 对 Building 目标应用效果 (PU_V8 AbsorberTurret.apply Building 分支):
         * - 若 damage>0, building.damage(damage)
         * - 设置激光渲染参数
         */
        protected void applyBuilding() {
            if (buildingTarget == null || !buildingTarget.isValid()) {
                buildingTarget = null;
                return;
            }

            // 设置激光渲染参数
            any = true;
            lastX = buildingTarget.x;
            lastY = buildingTarget.y;
            strength = Mathf.lerpDelta(strength, 1f, 0.1f);

            // PU_V8 AbsorberTurret.apply Building 分支
            if (damage > 0f) {
                buildingTarget.damage(damage);
            }
        }

        /**
         * 产电公式 (完整复刻 PU_V8 AbsorberTurret.getPowerProduction):
         * - Bullet: (bullet.type.damage / damageScale) * (bullet.vel.len() / speedScale) * powerProduction * curStrength
         * - Unit:   (unit.type.dpsEstimate / damageScale) * (unit.vel.len() / speedScale) * powerProduction * curStrength
         * - Building: 未实现 (PU_V8 TODO)
         */
        @Override
        public float getPowerProduction() {
            // Bullet 目标优先
            if (bulletTarget != null && bulletTarget.isAdded() && bulletTarget.type != null) {
                return (bulletTarget.type.damage / damageScale) * (bulletTarget.vel.len() / speedScale) * powerProduction * curStrength;
            }

            // Unit 目标 (target 字段, 父类已查找)
            if (target != null && !target.dead() && target.type != null && curStrength > 0.01f) {
                return (target.type.dpsEstimate / damageScale) * (target.vel.len() / speedScale) * powerProduction * curStrength;
            }

            // Building 目标: PU_V8 未实现 (TODO)
            return 0f;
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
