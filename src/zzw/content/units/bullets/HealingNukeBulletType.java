package zzw.content.units.bullets;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
import zzw.content.units.util.UnityUtils;

/**
 * 治疗核弹 (PU_V8 HealingNukeBulletType 完整移植版)
 * 参考: PU_V8 main/src/unity/entities/bullet/energy/HealingNukeBulletType.java
 *
 * 功能 (与 PU_V8 一致):
 * - 圆形范围: 友军治疗+allyStatus, 敌方伤害+status
 * - 多射线检测 absorbLasers 建筑 (遮挡)
 * - 自定义绘制: 多三角形组成圆形
 *
 * v158 适配:
 * - 用 zzw.content.units.util.UnityUtils.castCircle 替代 Utils.castCircle
 * - 用 Units.nearby(rect, cons) 替代 v158 中无对应方法的 Group 查询 (与 PU_V8 行为一致)
 * - ★ v158 BuildingComp.heal(amount) 不修改 health 字段, 直接修改 build.health + healthChanged()
 */
public class HealingNukeBulletType extends BulletType {
    public float radius = 650f;
    public int rays = 180;
    public StatusEffect allyStatus = StatusEffects.none;
    public float allyStatusDuration = 2f * 60f;

    public HealingNukeBulletType() {
        super(0f, 20f);
        hitEffect = Fx.none;
        despawnEffect = Fx.none;
        shootEffect = Fx.none;
        smokeEffect = Fx.none;
        buildingDamageMultiplier = 10f;

        collides = collidesTiles = false;
        hittable = absorbable = false;
    }

    @Override
    protected float calculateRange() {
        return radius;
    }

    @Override
    public void init() {
        super.init();
        drawSize = radius * 2f;
    }

    @Override
    public void init(Bullet b) {
        // ★ 与 PU_V8 一致: 用 castCircle 一次性扫描所有建筑 + 射线遮挡
        float[] data = UnityUtils.castCircle(b.x, b.y, radius, rays, bd -> true, building -> {
            if (building.team == b.team) {
                Fx.healBlockFull.at(building.x, building.y, building.block.size, Pal.heal);
                // ★ v158 修复: BuildingComp.heal(amount) 只调用 healthChanged(), 不修改 health 字段!
                //   直接修改 health 字段, 然后调用 healthChanged() 通知显示更新
                float healAmount = (healPercent / 100f) * building.maxHealth;
                building.health = Math.min(building.maxHealth, building.health + healAmount);
                building.healthChanged();
            } else {
                building.damage(damage * b.damageMultiplier() * buildingDamageMultiplier);
            }
        }, tile -> tile.block() != null && tile.block().absorbLasers && tile.team() != b.team);

        // ★ 与 PU_V8 一致: 用 Units.nearby(rect, cons) 扫描圆内所有单位 (包括友军和敌军)
        Units.nearby(Tmp.r1.setCentered(b.x, b.y, radius * 2f), u -> {
            float ang = b.angleTo(u);
            float dst = u.dst2(b) - ((u.hitSize * u.hitSize) / 2f);
            int idx = Mathf.mod(Mathf.round((ang % 360f) / (360f / data.length)), data.length);
            float d = data[idx];

            if (b.within(u, radius + (u.hitSize / 2f)) && dst <= d * d) {
                if (u.team == b.team) {
                    u.heal((healPercent / 100f) * u.maxHealth);
                    u.apply(allyStatus, allyStatusDuration);
                } else {
                    u.damage(damage * b.damageMultiplier());
                    u.apply(status, statusDuration);
                }
            }
        });

        b.data = data;
    }

    @Override
    public void draw(Bullet b) {
        if (b.data instanceof float[] data) {
            Draw.color(Pal.heal);
            Draw.alpha(0.3f * b.fout());
            for (int i = 0; i < data.length; i++) {
                float ang1 = i * (360f / data.length),
                        ang2 = (i + 1f) * (360f / data.length),
                        len1 = data[i], len2 = data[(i + 1) % data.length];
                Vec2 v1 = Tmp.v1.trns(ang1, len1).add(b),
                        v2 = Tmp.v2.trns(ang2, len2).add(b);

                Fill.tri(b.x, b.y, v1.x, v1.y, v2.x, v2.y);
            }
            Draw.reset();
        }
    }
}
