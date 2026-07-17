package zzw.content.units.bullets;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
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

/**
 * 锥形扫描治疗 (PU_V8 HealingConeBulletType 移植版)
 * - 锥形范围内扫描, 友军治疗+allyStatus, 敌方伤害
 * - 多个射线检测 absorbLasers 建筑
 * - 自定义绘制: 三角形扇形
 * 简化: 用 v158 world.raycastEachWorld + Groups.unit.intersect 替代 Utils.shotgunRange/castConeTile
 *       移除 UnityStatusEffects.weaken → StatusEffects.sapped
 * 参考: PU_V8 main/src/unity/entities/bullet/energy/HealingConeBulletType.java
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
        range = length;
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

        // 扫描每个角度, 检测 absorbLasers 建筑
        idx = 0;
        for (int i = 0; i < scanAccuracy; i++) {
            float ang = b.rotation() + Mathf.lerp(-cone, cone, i / (scanAccuracy - 1f));
            Tmp.v1.trns(ang, length).add(b);
            final int fi = i;
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
        }

        // 每 30 tick 检测单位和建筑伤害/治疗
        if (b.timer(1, 30f)) {
            // 检测锥形范围内单位
            mindustry.entities.Units.nearby(b.team, b.x, b.y, length + 50f, unit -> {
                if (!b.within(unit, length + (unit.hitSize / 2f))) return;
                if (!Angles.within(b.rotation(), b.angleTo(unit), cone)) return;
                int index = Mathf.clamp(Mathf.round(((angleDistSigned(b.angleTo(unit), b.rotation()) + cone) / (cone * 2f)) * (data.length - 1)), 0, data.length - 1);
                if ((b.dst2(unit) + (unit.hitSize / 2f)) < data[index]) {
                    if (unit.team != b.team) {
                        unit.damage(b.damage);
                        unit.apply(status, statusDuration);
                    } else {
                        unit.heal((unit.maxHealth / 100f) * healPercent);
                        unit.apply(allyStatus, allyStatusDuration);
                    }
                }
            });

            // 遍历锥形范围内所有建筑 (修复 PU_V8 Utils.castConeTile 的功能)
            // 用 Groups.build 遍历, 检查是否在锥形范围内且未被 absorbLasers 建筑遮挡
            Groups.build.forEach(build -> {
                if (!b.within(build, length + (build.block.size * Vars.tilesize / 2f))) return;
                if (!Angles.within(b.rotation(), b.angleTo(build), cone)) return;
                int index = Mathf.clamp(Mathf.round(((angleDistSigned(b.angleTo(build), b.rotation()) + cone) / (cone * 2f)) * (data.length - 1)), 0, data.length - 1);
                if ((b.dst2(build) + (build.block.size * Vars.tilesize / 2f)) < data[index]) {
                    if (build.team == b.team) {
                        if (build.damaged()) {
                            build.heal(build.maxHealth / 100f * healPercent);
                            Fx.healBlockFull.at(build.x, build.y, build.block.size, Pal.heal);
                        }
                    } else {
                        build.damage(b.damage * buildingDamageMultiplier);
                    }
                }
            });
        }
    }

    /** 计算带符号的角度差 (-180 ~ 180) */
    private float angleDistSigned(float a, float b) {
        float d = (a - b + 360f) % 360f;
        return d > 180f ? d - 360f : d;
    }

    @Override
    public void draw(Bullet b) {
        if (!(b.data instanceof float[] data)) return;
        float z = Draw.z();
        Draw.z(Layer.buildBeam);
        float fout = Mathf.clamp(b.time > b.lifetime - 16f ? 1f - (b.time - (b.lifetime - 16f)) / 16f : 1f)
                * Mathf.clamp(b.time / 16f) * length;

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
        // 不绘制光源
    }
}
