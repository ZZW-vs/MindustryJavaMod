package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
import mindustry.ui.Bar;
import mindustry.world.blocks.defense.turrets.TractorBeamTurret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * 吸收者炮台 (PU_V8 AbsorberTurret 移植版)
 * absorber: 牵引光束吸收敌方子弹/单位/建筑, 根据吸收对象产生电力
 * 简化: 基于 v158 TractorBeamTurret (v158 无 GenericTractorBeamTurret), 重写 findTarget/apply/getPowerProduction;
 *       targets 改为 Teamc 通用类型, 保留子弹/单位/建筑三种目标模式
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/AbsorberTurret.java
 */
public class AbsorberTurret extends TractorBeamTurret {
    public float powerProduction = 2.5f;
    public float resistance = 0.4f;
    public float damageScale = 18f;
    public float damage = 0f;
    public float speedScale = 3.5f;

    public StatusEffect status;

    public boolean targetBullets, targetUnits, targetBuildings = false;

    private Seq<Building> buildings = new Seq<>();

    public AbsorberTurret(String name) {
        super(name);
        outputsPower = true;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.basePowerGeneration, powerProduction * 60f, StatUnit.powerSecond);
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("power", (AbsorberTurretBuild entity) -> new Bar(() ->
            Core.bundle.format("bar.poweroutput",
            Strings.fixed(entity.getPowerProduction() * 60f * entity.timeScale(), 1)),
            () -> Pal.powerBar,
            () -> entity.getPowerProduction() / powerProduction)
        );
    }

    public class AbsorberTurretBuild extends TractorBeamBuild {
        public Teamc target2;

        // v158 TractorBeamBuild 无 findTarget 方法, 此处为自定义方法
        protected void findTarget() {
            findTarget(x, y, range);
        }

        protected void findTarget(float x, float y, float r) {
            Teamc tempTarget = null;
            target = null;
            float distance = Float.MAX_VALUE;

            if (targetBullets) {
                Bullet b = Groups.bullet
                    .intersect(x - r, y - r, r * 2f, r * 2f)
                    .min(e -> e.team != team && e.type.hittable, e -> e.dst2(x, y));
                if (b != null) {
                    tempTarget = b;
                    distance = Mathf.dst(x, y, b.x, b.y);
                }
            }

            if (targetUnits) {
                Unit u = Groups.unit
                    .intersect(x - r, y - r, r * 2f, r * 2f)
                    .min(e -> e.team != team && !e.dead, e -> e.dst2(x, y));
                if (u != null) {
                    float d = Mathf.dst(x, y, u.x, u.y);
                    if (d < distance) {
                        distance = d;
                        tempTarget = u;
                    }
                }
            }

            if (targetBuildings) {
                buildings.clear();
                Vars.indexer.eachBlock(null, x, y, r, b -> b.team != team && !b.dead, buildings::add);
                Building b = buildings.min(e -> e.dst2(x, y));
                if (b != null) {
                    float d = Mathf.dst(x, y, b.x, b.y);
                    if (d < distance) tempTarget = b;
                }
            }

            // 将通用目标转为 Unit (TractorBeamBuild.target 为 Unit 类型)
            if (tempTarget instanceof Unit u) {
                target = u;
            } else {
                target = null;
            }
            target2 = tempTarget;
        }

        @Override
        public void updateTile() {
            // 重写: 不使用 super.updateTile() 的单一单位目标逻辑, 改用通用目标
            if (activationTimer > 0) {
                activationTimer -= Time.delta;
                return;
            }

            float eff = efficiency * coolantMultiplier, edelta = eff * delta();

            if (timer(timerTarget, retargetTime)) {
                findTarget();
            }

            // 冷却液消耗 (与原版一致)
            if (target2 != null && coolant != null) {
                float maxUsed = coolant.amount;
                mindustry.type.Liquid liquid = liquids.current();
                float used = Math.min(Math.min(liquids.get(liquid), maxUsed * Time.delta), Math.max(0, (1f / coolantMultiplier) / liquid.heatCapacity));
                liquids.remove(liquid, used);
                if (Mathf.chance(0.06 * used)) {
                    coolEffect.at(x + Mathf.range(size * Vars.tilesize / 2f), y + Mathf.range(size * Vars.tilesize / 2f));
                }
                coolantMultiplier = 1f + (used * liquid.heatCapacity * coolantMultiplier);
            }

            any = false;

            if (target2 != null && target2.within(this, range) && target2.team() != team && efficiency > 0.02f) {
                if (!Vars.headless) {
                    Vars.control.sound.loop(shootSound, this, shootSoundVolume);
                }

                float dest = angleTo(target2);
                rotation = Angles.moveToward(rotation, dest, rotateSpeed * edelta);
                lastX = target2.x();
                lastY = target2.y();
                strength = Mathf.lerpDelta(strength, 1f, 0.1f);

                if (Mathf.within(rotation, dest, shootCone)) {
                    apply();
                    any = true;
                    // 单位减速 (牵引效果)
                    if (target2 instanceof Unit unit) {
                        unit.impulseNet(Tmp.v1.set(this).sub(unit).limit((force + (1f - unit.dst(this) / range) * scaledForce) * edelta));
                    }
                }
            } else {
                strength = Mathf.lerpDelta(strength, 0, 0.1f);
            }
        }

        protected void apply() {
            if (target2 instanceof Bullet bullet) {
                bullet.vel.setLength(Math.max(bullet.vel.len() - resistance * strength, 0f));
                bullet.damage = Math.max(bullet.damage - (resistance / 2f) * strength * Time.delta, 0f);
                if (bullet.vel.isZero(0.01f) || bullet.damage <= 0f) {
                    bullet.remove();
                }
            }

            if (target2 instanceof Unit unit && damage > 0f) {
                if (status != null) unit.apply(status, statusDuration);
                unit.damageContinuousPierce(damage * efficiency * coolantMultiplier);
            }

            if (target2 instanceof Building building && damage > 0f) {
                building.damage(damage);
            }
        }

        @Override
        public float getPowerProduction() {
            if (target2 == null) return 0f;

            if (target2 instanceof Bullet bullet) {
                if (bullet.type == null) return 0f;
                return (bullet.type.damage / damageScale) * (bullet.vel.len() / speedScale) * powerProduction;
            }

            if (target2 instanceof Unit unit) {
                if (unit.type == null) return 0f;
                return (unit.type.dpsEstimate / damageScale) * (unit.vel.len() / speedScale) * powerProduction;
            }

            return 0f;
        }
    }
}
