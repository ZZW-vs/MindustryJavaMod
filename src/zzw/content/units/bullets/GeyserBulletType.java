package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.Fires;
import mindustry.entities.Lightning;
import mindustry.entities.Puddles;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.world.Tile;

import static mindustry.Vars.world;
import static zzw.content.exp.OmniLiquidTurret.friendly;

/**
 * PU_V8 GeyserBulletType 移植版 (液体喷泉子弹)
 * 参考: PU_V8 main/src/unity/entities/bullet/exp/GeyserBulletType.java
 *
 * 功能:
 * - 静止子弹 (speed≈0), 在目标点生成持续喷泉效果
 * - 根据液体类型调整伤害/击退/特效:
 *   * 友方液体 (friendly): 治疗附近友军单位
 *   * 敌方液体: 伤害+击退附近敌军单位
 *   * 高温液体: 点燃地面方块
 *   * 高热容液体 (如冷却液): 触发闪电攻击
 * - 渲染: 双层扩散圆环 + 喷泉粒子
 *
 * 简化:
 * - 不继承 ExpBulletType (项目未移植), 改用 v158 原生 BulletType
 * - 移除 radiusInc 等经验等级增量
 * - spawnEffect/hotSmokeEffect/coldSmokeEffect 用 v158 原生 Fx 替代 (PU_V8 UnityFx 未移植)
 */
public class GeyserBulletType extends BulletType {
    protected static final Rand rand = new Rand();
    public float radius = 25f;

    // v158 原生特效替代 PU_V8 UnityFx (未移植)
    public Effect spawnEffect = Fx.blastsmoke;
    public Effect hotSmokeEffect = Fx.steam;
    public Effect coldSmokeEffect = Fx.smoke;
    public float puddleSpeed = 60f;
    public float smallEffectChance = 0.5f;
    public float smokeChance = 0.08f;

    public float a = 1f;
    public int particles = 25;
    public float particleLife = 50f, particleLen = 9f;
    public float shake = 1f;

    public GeyserBulletType(float lifetime, float damage) {
        super(0.0001f, damage);
        this.lifetime = lifetime;
        collides = false;
        collidesAir = collidesGround = collidesTiles = false;
        absorbable = hittable = false;
        knockback = 10f;
        statusDuration = 60f;
        despawnEffect = Fx.none;
        hitEffect = Fx.none;
    }

    public GeyserBulletType() {
        this(400f, 10f);
    }

    @Override
    public void init() {
        super.init();
        drawSize = radius * 2f + 8f;
        despawnHit = false;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        Effect.shake(shake, b.lifetime, b);
        spawnEffect.at(b.x, b.y, 0, getLiquid(b).color);
    }

    /** 从 b.data 读取液体 (由 GeyserLaserBulletType.init() 传入) */
    public Liquid getLiquid(Bullet b) {
        return b.data instanceof Liquid l ? l : Liquids.water;
    }

    public float getRad(Bullet b) {
        return radius * Mathf.clamp(15f * b.fout());
    }

    /** 液体伤害缩放 (PU_V8 原版公式) */
    public static float damageScale(Liquid l) {
        return 0.2f + (l.explosiveness + Math.abs(l.temperature - 0.5f)) * 1.8f;
    }

    /** 液体击退缩放 (PU_V8 原版公式) */
    public static float knockbackScale(Liquid l) {
        return Math.max(0.03f, (0.3f - damageScale(l)) * 5f) * l.viscosity * 3f;
    }

    /** 是否触发闪电 (PU_V8 原版条件) */
    public static boolean hasLightning(Liquid l) {
        if (l.effect == StatusEffects.none) return false;
        return (l.heatCapacity > 1f && l.temperature > 0.25f)
                || l.effect.buildSpeedMultiplier > 1.1f
                || l.effect.speedMultiplier > 1.3f
                || l.effect.dragMultiplier < 0.1f;
    }

    public void effects(Bullet b, Liquid l) {
        if (Mathf.chance(smokeChance)) {
            if (l.temperature < 0.32f) {
                coldSmokeEffect.at(b.x, b.y, l.color);
            } else if (l.viscosity < l.temperature) {
                Color smokeColor = l.temperature > 1.4f ? Pal.gray : l.temperature > 0.7f ? Color.lightGray : Color.white;
                hotSmokeEffect.at(b.x, b.y, l.temperature > 1.4f ? Pal.gray : smokeColor);
            }
        }

        if (l.effect != StatusEffects.none && Mathf.chance(smallEffectChance) && l.effect.effect != Fx.none) {
            Tmp.v1.trns(Mathf.random(360f), getRad(b)).add(b);
            l.effect.effect.at(Tmp.v1.x, Tmp.v1.y, 0, l.effect.color, null);
        }

        // 高温液体点燃地面
        if (l.temperature > 0.8f && Mathf.chance(Mathf.clamp(l.temperature - 0.5f) * 0.2f)) {
            Tmp.v1.trns(Mathf.random(360f), getRad(b) * l.temperature * Mathf.random(0.3f, 1f)).add(b);
            Tile t = world.tileWorld(Tmp.v1.x, Tmp.v1.y);
            if (t != null) Fires.create(t);
        }

        // 高热容液体触发闪电 (如冷却液)
        if (hasLightning(l) && Mathf.chanceDelta(l.heatCapacity * 0.2f)) {
            Lightning.create(b.team, l.color, b.damage * 0.5f, b.x, b.y, Mathf.random(360f), Mathf.random(5, 8));
        }
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        float rad = getRad(b) * 0.6f;
        Liquid l = getLiquid(b);

        if (friendly(l)) {
            // 友方液体: 治疗友军单位
            Units.nearby(b.team, b.x, b.y, rad, unit -> {
                unit.apply(l.effect, statusDuration);
                unit.heal(Math.abs(b.damage * damageScale(l) * 0.1f * l.effect.damage));
            });
        } else {
            // 敌方液体: 伤害+击退敌军单位
            Units.nearbyEnemies(b.team, b.x, b.y, rad, unit -> {
                Tmp.v3.set(unit).sub(b).nor().scl(knockback * 80f * knockbackScale(l) * Mathf.clamp(1f - 0.9f * unit.dst2(b) / (rad * rad)));
                unit.impulse(Tmp.v3);
                unit.apply(l.effect, statusDuration);
                unit.damageContinuousPierce(b.damage * damageScale(l));
            });
        }

        // 在地面留下液体水坑
        Tile tile = world.tileWorld(b.x, b.y);
        if (tile != null) Puddles.deposit(tile, l, 25f);

        effects(b, l);
    }

    @Override
    public void draw(Bullet b) {
        float rad = getRad(b);
        Liquid l = getLiquid(b);
        float finb = Mathf.clamp(b.fout() * 9f);

        // 双层扩散圆环 (在 debris 层下方)
        Draw.z(Layer.debris - 0.1f);

        for (int i = 0; i < 2; i++) {
            float fin = ((Time.time + i * puddleSpeed / 2f) % puddleSpeed) / puddleSpeed;
            float fout = Mathf.clamp((1 - fin) * 5f);
            Draw.color(l.color, Color.white, fout * 0.5f);
            Lines.stroke(fout * 6f * finb);
            Draw.alpha(0.3f);
            Lines.circle(b.x, b.y, rad * fin * fin);
            Lines.stroke(fout * 5f * finb);
            Draw.alpha(0.3f);
            Lines.circle(b.x, b.y, rad * fin * fin);
            Lines.stroke(fout * 3f * finb);
            Draw.alpha(1f);
            Lines.circle(b.x, b.y, rad * fin * fin);
        }

        // 喷泉粒子 (bullet 层上方)
        Draw.z(Layer.bullet + 1);
        float base = (Time.time / particleLife);
        rand.setSeed(b.id);
        for (int i = 0; i < particles; i++) {
            float fin = (rand.random(1f) + base) % 1f, fout = 1f - fin;
            float angle = rand.random(360f);
            float len = rand.random(0.3f, 0.7f) * Interp.pow3Out.apply(fin) * rad;
            float roff = rand.random(0.8f, 1.1f);
            Draw.color(Tmp.c1.set(l.color).mul(0.5f + fout * 0.5f).lerp(Color.white, fout * 0.2f + rand.random(0f, 0.2f)), a);
            Fill.circle(b.x + Angles.trnsx(angle, len), b.y + Angles.trnsy(angle, len), particleLen * roff * fout * Mathf.clamp(fin * 5f) * finb + 0.01f);
        }

        Draw.reset();
    }
}
