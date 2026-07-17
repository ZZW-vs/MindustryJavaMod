package zzw.content.units.bullets;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
import mindustry.world.Tile;
import zzw.content.units.util.UnityUtils;

/**
 * 锥形扫描治疗 (PU_V8 HealingConeBulletType 完整移植版)
 * 参考: PU_V8 main/src/unity/entities/bullet/energy/HealingConeBulletType.java
 *
 * 功能 (与 PU_V8 一致):
 * - 锥形范围内扫描, 友军治疗+allyStatus, 敌方伤害
 * - 多射线检测 absorbLasers 建筑 (遮挡)
 * - 自定义绘制: 三角形扇形
 *
 * v158 适配:
 * - 用 zzw.content.units.util.UnityUtils 提供 shotgunRange / castConeTile / angleDistSigned
 * - ★ v158 BuildingComp.heal(amount) 不修改 health 字段, 直接修改 build.health + healthChanged()
 * - 用 Groups.unit.intersect(rect, cons) 同时扫描友军和敌军 (PU_V8 行为, 比项目原 Units.nearby(team,...) 更准确)
 *   旧版项目用 Units.nearby(b.team, ...) 只扫描友军, 导致锥内敌方单位永远不会受伤
 */
public class HealingConeBulletType extends BulletType {
    public float cone = 45f;
    public float length = 250f;
    public int scanAccuracy = 30;
    public StatusEffect allyStatus = StatusEffects.none;
    public float allyStatusDuration = 0f;
    public arc.graphics.Color color = Pal.heal;

    private int idx = 0;

    public HealingConeBulletType(float damage) {
        this.damage = damage;
        speed = 0.001f;
        hitEffect = Fx.none;
        despawnEffect = Fx.none;
        keepVelocity = false;
        collides = false;
        pierce = true;
        hittable = false;
        absorbable = false;
    }

    @Override
    protected float calculateRange() {
        return length;
    }

    @Override
    public float continuousDamage() {
        return damage / 30f * 60f;
    }

    @Override
    public float estimateDPS() {
        return damage * 100f / 30f * 3f;
    }

    @Override
    public void init() {
        super.init();
        drawSize = Math.max(drawSize, length * 2f);
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = new float[scanAccuracy];
    }

    @Override
    public void update(Bullet b) {
        if (!(b.data instanceof float[] data)) return;

        // ★ 与 PU_V8 一致: 用 shotgunRange 遍历角度, world.raycastEachWorld 检测 absorbLasers 建筑
        idx = 0;
        UnityUtils.shotgunRange(scanAccuracy, cone, b.rotation(), ang -> {
            Tmp.v1.trns(ang, length).add(b);
            final int fi = idx;
            Vars.world.raycastEachWorld(b.x, b.y, Tmp.v1.x, Tmp.v1.y, (cx, cy) -> {
                Tile tile = Vars.world.tile(cx, cy);
                boolean bl = tile != null && tile.build != null && tile.team() != b.team
                        && tile.block() != null && tile.block().absorbLasers;
                if (bl) {
                    float dst = Math.min(b.dst(cx * Vars.tilesize, cy * Vars.tilesize), length);
                    data[fi] = dst * dst;
                } else {
                    data[fi] = length * length;
                }
                return bl;
            });
            idx++;
        });

        // 每 30 tick 检测单位和建筑伤害/治疗 (与 PU_V8 一致)
        if (b.timer(1, 30f)) {
            Tmp.r1.setCentered(b.x, b.y, 1f);
            UnityUtils.shotgunRange(3, cone, b.rotation(), ang -> {
                Tmp.v1.trns(ang, length).add(b);
                Tmp.r1.merge(Tmp.v1);
            });

            // ★ 与 PU_V8 一致: 用 Groups.unit.intersect 同时扫描友军和敌军 (旧版只扫友军, 敌方不受伤)
            Groups.unit.intersect(Tmp.r1.x, Tmp.r1.y, Tmp.r1.width, Tmp.r1.height, unit -> {
                if (b.within(unit, length + (unit.hitSize / 2f)) && Angles.within(b.rotation(), b.angleTo(unit), cone)) {
                    int index = Mathf.clamp(Mathf.round(((UnityUtils.angleDistSigned(b.angleTo(unit), b.rotation()) + cone) / (cone * 2f)) * (data.length - 1)), 0, data.length - 1);
                    if ((b.dst2(unit) + (unit.hitSize / 2f)) < data[index]) {
                        if (unit.team != b.team) {
                            unit.damage(b.damage);
                            unit.apply(status, statusDuration);
                        } else {
                            unit.heal((unit.maxHealth / 100f) * healPercent);
                            unit.apply(allyStatus, allyStatusDuration);
                        }
                    }
                }
            });

            // ★ 与 PU_V8 一致: 用 castConeTile 扫描锥形 tile 范围内的建筑
            // (旧版用 Groups.build.forEach 全局遍历, 性能差且依赖手动 dst/angle 检查)
            UnityUtils.castConeTile(b.x, b.y, length, b.rotation(), cone, (building, tile) -> {
                if (building != null) {
                    if (building.team == b.team) {
                        if (building.damaged()) {
                            // ★ v158 修复: BuildingComp.heal(amount) 只调用 healthChanged(), 不修改 health 字段!
                            //   直接修改 health 字段, 然后调用 healthChanged() 通知显示更新
                            float healAmount = building.maxHealth / 100f * healPercent;
                            building.health = Math.min(building.maxHealth, building.health + healAmount);
                            building.healthChanged();
                            Fx.healBlockFull.at(building.x, building.y, building.block.size, Pal.heal);
                        }
                    } else {
                        building.damage(b.damage * buildingDamageMultiplier);
                    }
                }
            }, tile -> tile.block() != null && tile.block().absorbLasers && tile.team() != b.team, data);
        }
    }

    @Override
    public void draw(Bullet b) {
        if (!(b.data instanceof float[] data)) return;
        float z = Draw.z();
        Draw.z(Layer.buildBeam);
        float fout = Mathf.clamp(b.time > b.lifetime - 16f ? 1f - (b.time - (b.lifetime - 16f)) / 16f : 1f) * Mathf.clamp(b.time / 16f) * length;

        Tmp.v1.trns(b.rotation() - cone, Math.min(Mathf.sqrt(data[0]), fout)).add(b);
        Draw.color(color);
        if (!Vars.renderer.animateShields) Draw.alpha(0.3f);
        for (int i = 1; i < scanAccuracy; i++) {
            float ang = Mathf.lerp(-cone, cone, i / (scanAccuracy - 1f)) + b.rotation();
            Tmp.v2.trns(ang, Math.min(Mathf.sqrt(data[i]), fout)).add(b);
            Fill.tri(b.x, b.y, Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y);
            Tmp.v1.set(Tmp.v2);
        }
        Draw.color();
        Draw.z(z);
    }

    @Override
    public void drawLight(Bullet b) {
        // 不绘制光源 (与 PU_V8 一致)
    }
}
