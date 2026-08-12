package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.EnumSet;
import arc.util.Tmp;
import mindustry.entities.Units;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.BlockGroup;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.blocks.defense.turrets.ReloadTurret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import zzw.content.exp.ExpHolder;
import zzw.content.exp.UnityPal;

import static mindustry.Vars.tilesize;

/**
 * 区域过载炮台 (PU_V8 BlockOverdriveTurret 移植版)
 * buffTurret: 瞄准附近可加速的方块, 应用 applyBoost (加速) + heal (治疗)
 * upgradeTurret: 瞄准附近经验方块, 为其增加经验
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/BlockOverdriveTurret.java
 */
public class BlockOverdriveTurret extends ReloadTurret {
    public float buffRange = 50f;
    public float buffReload = 180f;
    public float phaseRangeBoost = 1.5f;
    /** 加速强度 (buffTurret 模式) */
    public float boostStrength = 2f;
    /** 经验增量 (upgradeTurret 模式, 每秒) */
    public float expPerSec = 5f;
    /** true=经验模式 (upgradeTurret), false=加速模式 (buffTurret) */
    public boolean upgrade = false;

    public TextureRegion baseRegion, laserRegion, laserEndRegion;

    public BlockOverdriveTurret(String name) {
        super(name);

        hasPower = hasItems = update = solid = outlineIcon = true;
        flags = EnumSet.of(BlockFlag.turret);
        group = BlockGroup.projectors;
        canOverdrive = false;
    }

    @Override
    public void load() {
        super.load();
        baseRegion = Core.atlas.find(name + "-base", Core.atlas.find("block-" + size));
        if(!region.found()){
            region = Core.atlas.find("overdrive-projector");
        }
        laserRegion = Core.atlas.find("exp-laser");
        laserEndRegion = Core.atlas.find("exp-laser-end");
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.range, buffRange / tilesize, StatUnit.blocks);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        Drawf.dashCircle(x * tilesize, y * tilesize, buffRange, Pal.accent);
        Draw.reset();
    }

    public class BlockOverdriveTurretBuild extends ReloadTurretBuild {
        public Building target;
        public float buffingTime, phaseHeat, targetTime;
        public boolean buffing, isExp;

        @Override
        public void drawSelect() {
            Drawf.circles(x, y, buffRange, Pal.accent);
            if (buffing) Drawf.selected(target, isExp ? UnityPal.exp.a(Mathf.absin(6f, 1f)) : Tmp.c1.set(Pal.heal).lerp(Color.valueOf("feb380"), Mathf.absin(9f, 1f)).a(Mathf.absin(6f, 1f)));
        }

        @Override
        public void draw() {
            Draw.rect(baseRegion, x, y);
            Draw.z(Layer.turret);
            Drawf.shadow(region, x - (size / 2f), y - (size / 2f), rotation - 90);
            Draw.rect(region, x, y, rotation - 90);

            if (buffing) {
                float angle = angleTo(target);
                float len = 5;
                Draw.color(isExp ? UnityPal.exp : Tmp.c2.set(Color.valueOf("feb380")).lerp(Pal.heal, Mathf.absin(10f, 1f)));
                Draw.alpha(1f);
                Draw.z(Layer.block + 1);
                Drawf.laser(laserRegion, laserEndRegion, x + Angles.trnsx(angle, len), y + Angles.trnsy(angle, len), target.x, target.y, 0.25f);
                Draw.color();
            }
        }

        @Override
        public void updateTile() {
            phaseHeat = Mathf.lerpDelta(phaseHeat, Mathf.num(hasItems && !items.empty()), 0.1f);
            float radius = buffRange + phaseHeat * phaseRangeBoost;
            buffing = false;

            if (target != null) {
                isExp = target instanceof ExpHolder;
                if (!targetValid(target)) {
                    target = null;
                } else if (canConsume() && enabled) {
                    // ★ 实际应用效果 (PU_V8 BlockStatusEffectBulletType.update 等效)
                    applyEffect(target, edelta());
                    rotation = Mathf.slerpDelta(rotation, angleTo(target), 0.5f);
                    buffing = true;
                }
                targetTime = 0f;
            }

            if (optionalEfficiency > 0) {
                buffingTime += edelta();
                if (buffingTime >= buffReload) {
                    consume();
                    buffingTime = 0f;
                }
            }

            if (canConsume()) {
                targetTime += edelta();
                if (targetTime >= buffReload) {
                    target = Units.closestBuilding(team, x, y, radius, this::targetValid);
                    targetTime = 0f;
                }
            }
        }

        /** 应用加速/经验效果到目标 */
        protected void applyEffect(Building b, float delta) {
            if (upgrade) {
                // upgradeTurret: 给经验方块加经验
                if (b instanceof ExpHolder exp) {
                    exp.handleExp(Mathf.round(expPerSec * delta / 60f));
                }
            } else {
                // buffTurret: 加速 + 治疗
                float strength = boostStrength + phaseHeat * boostStrength;
                b.applyBoost(strength, 180f);
                if (b.health < b.maxHealth) {
                    b.heal(strength * delta / 60f);
                }
            }
        }

        @Override
        public boolean shouldConsume() {
            return target != null && enabled;
        }

        public boolean targetValid(Building b) {
            return b.isValid() && b.block.canOverdrive && b != this && !proximity.contains(b) && b.enabled
                    // upgrade 模式只瞄准经验方块; buff 模式瞄准非经验方块 (避免重复)
                    && (upgrade ? (b instanceof ExpHolder) : !(b instanceof ExpHolder));
        }
    }
}
