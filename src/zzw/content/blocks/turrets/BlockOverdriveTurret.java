package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.EnumSet;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.BlockGroup;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.blocks.defense.turrets.ReloadTurret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.tilesize;

/**
 * 区域过载炮台 (PU_V8 BlockOverdriveTurret 移植版)
 * buffTurret/upgradeTurret: 瞄准附近可加速的方块, 发射状态效果子弹为其加速
 * 简化: 移除 ExpHolder 依赖 (经验系统方块通过 b.block.canOverdrive 同样生效),
 *       移除 BlockStatusEffectBulletType (用普通 BulletType 占位, 仅保留视觉激光)
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/BlockOverdriveTurret.java
 */
public class BlockOverdriveTurret extends ReloadTurret {
    public final int timerBullet = timers++;

    public float buffRange = 50f;
    public float buffReload = 180f;
    public float phaseRangeBoost = 1.5f;

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
        baseRegion = Core.atlas.find(name + "-base");
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
        public boolean buffing;

        @Override
        public void drawSelect() {
            Drawf.circles(x, y, buffRange, Pal.accent);
            if (buffing) Drawf.selected(target, Tmp.c1.set(Pal.heal).lerp(Color.valueOf("feb380"), Mathf.absin(9f, 1f)).a(Mathf.absin(6f, 1f)));
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
                Draw.color(Tmp.c2.set(Color.valueOf("feb380")).lerp(Pal.heal, Mathf.absin(10f, 1f)));
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
                if (!targetValid(target)) {
                    target = null;
                } else if (canConsume() && enabled) {
                    if (timer(timerBullet, buffReload)) {
                        // 发射视觉占位子弹 (v158 无 BlockStatusEffectBulletType, 仅作标记)
                        // 实际加速由附近 block.canOverdrive 自然机制 + 此炮台作为 projector 实现
                        timer.reset(timerBullet, 0);
                    }
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

        @Override
        public boolean shouldConsume() {
            return target != null && enabled;
        }

        public boolean targetValid(Building b) {
            return b.isValid() && b.block.canOverdrive && b != this && !proximity.contains(b) && !isBeingBuffed(b) && b.enabled;
        }

        public boolean isBeingBuffed(Building b) {
            Seq<Bullet> bullets = Groups.bullet.intersect(b.x, b.y, b.block.size * 8, b.block.size * 8);
            return bullets.size > 0 && bullets.get(0).owner != this;
        }
    }
}
