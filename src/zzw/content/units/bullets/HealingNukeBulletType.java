package zzw.content.units.bullets;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
import mindustry.world.Tile;

/**
 * 治疗核弹 (PU_V8 HealingNukeBulletType 移植版)
 * - 圆形范围: 友军治疗+allyStatus, 敌方伤害+status
 * - 多射线检测 absorbLasers 建筑 (遮挡)
 * - 自定义绘制: 多三角形组成圆形
 * 简化: 用 v158 world.raycastEachWorld + Units.nearby 替代 Utils.castCircle
 *       移除 UnityStatusEffects.disabled → StatusEffects.unmoving
 * 参考: PU_V8 main/src/unity/entities/bullet/energy/HealingNukeBulletType.java
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
    public void init() {
        super.init();
        range = radius;
        drawSize = radius * 2f;
    }

    @Override
    public void init(Bullet b) {
        // 圆形射线检测, 记录每个角度的最大长度 (被 absorbLasers 建筑遮挡)
        float[] data = new float[rays];
        for (int i = 0; i < rays; i++) {
            float ang = i * (360f / rays);
            Tmp.v1.trns(ang, radius).add(b);
            final int fi = i;
            data[fi] = radius;
            mindustry.Vars.world.raycastEachWorld(b.x, b.y, Tmp.v1.x, Tmp.v1.y, (cx, cy) -> {
                Tile tile = mindustry.Vars.world.tile(cx, cy);
                if (tile != null && tile.block() != null && tile.block().absorbLasers && tile.team() != b.team) {
                    data[fi] = Mathf.dst(b.x, b.y, cx * mindustry.Vars.tilesize, cy * mindustry.Vars.tilesize);
                    return true;
                }
                return false;
            });
        }

        // 检测建筑伤害/治疗
        mindustry.Vars.indexer.eachBlock(null, b.x, b.y, radius,
                build -> true,
                build -> {
                    if (build.team == b.team) {
                        if (build.damaged()) {
                            Fx.healBlockFull.at(build.x, build.y, build.block.size, Pal.heal);
                            // ★ v158 修复: BuildingComp.heal(amount) 用 @MethodPriority(100) 覆盖了 HealthComp 默认实现,
                            //   但只调用 healthChanged() 更新显示, 没有真正增加 health 字段!
                            //   所以这里直接修改 health 字段, 然后调用 healthChanged() 通知显示更新
                            float healAmount = (healPercent / 100f) * build.maxHealth;
                            build.health = Math.min(build.maxHealth, build.health + healAmount);
                            build.healthChanged();
                        }
                    } else {
                        build.damage(damage * b.damageMultiplier() * buildingDamageMultiplier);
                    }
                });

        // 检测单位
        float nearbyR = radius + 50f;
        mindustry.entities.Units.nearby(b.x - nearbyR, b.y - nearbyR, nearbyR * 2f, nearbyR * 2f, u -> {
            float ang = b.angleTo(u);
            float dst = u.dst2(b) - ((u.hitSize * u.hitSize) / 2f);
            int idx = Mathf.mod(Mathf.round((ang % 360f) / (360f / data.length)), data.length);
            float d = data[idx];

            if (b.within(u, radius + (u.hitSize / 2f)) && dst <= d * d) {
                if (u.team == b.team) {
                    // 单位 heal() 不受 @MethodPriority 覆盖影响, 可以正常调用
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
