package zzw.content.units.abilities;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.gen.Sounds;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Healthc;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import zzw.content.graphics.UnityPal;

import static mindustry.Vars.tilesize;

/**
 * 闪电生成能力 (PU132 unity.entities.abilities.LightningSpawnAbility 移植)
 *
 * <p>colossus (巨像) / bastion (堡垒) 的环绕闪电球能力:
 * N 个能量球绕单位旋转 (相位随弹药余量收缩/展开),
 * 每个球独立索敌, 命中时直接造成伤害 + 链式闪电特效。</p>
 *
 * <p>渲染: 阴影底图 + 实心圆 + 双层旋转弧线 + 光照。</p>
 *
 * <p>★ v158 适配:
 * <ul>
 *   <li>unit.ammo/ammof() (v7 弹药系统) → 固定 1f (v158 无单位弹药)</li>
 *   <li>SpecialFx.chainLightningActive → Fx.chainLightning (近似替代)</li>
 *   <li>ParticleFx.monolithSpark → Fx.hitLancer (近似替代)</li>
 * </ul></p>
 *
 * @author GlennFolker (原版), 移植适配
 */
public class LightningSpawnAbility extends Ability {
    /** 旋转速度 (度/秒, 也用于弧线自转) */
    public float rotateSpeed;

    /** 弧线分扇数 */
    public int sectors = 6;
    /** 相位 (0~1, 控制能量球距单位的距离比例) */
    public float phase;
    public float phaseSpeed,
        lightningRange, lightningOffset, lightningDamage,
        lightningRadius = tilesize, lightningOuterRadius = tilesize * 6f,
        trailChance = 0.3f;

    protected int lightningCount;
    protected Color backColor = UnityPal.monolithDark.cpy().a(0.5f);
    protected Color frontColor = UnityPal.monolithLight.cpy().a(0.5f);
    protected mindustry.entities.Effect damageEffect = Fx.chainLightning;
    protected mindustry.entities.Effect hitEffect = Fx.hitLaserBlast;
    protected mindustry.entities.Effect trailEffect = Fx.hitLancer;

    protected float timer, reload;

    /**
     * 构造 (与 PU132 用法一致)
     * <p>
     * colossus: new LightningSpawnAbility(8, 32f, 2f, 0.05f, 180f, 56f, 200f)
     * bastion:  new LightningSpawnAbility(12, 16f, 3f, 0.05f, 300f, 96f, 640f)
     *
     * @param lightningCount   闪电球数量
     * @param reload           索敌/攻击间隔 (tick)
     * @param rotateSpeed      旋转速度
     * @param phaseSpeed       相位变化速度
     * @param lightningRange   每个球的索敌范围
     * @param lightningOffset  球距单位的旋转半径
     * @param lightningDamage  每次命中伤害
     */
    public LightningSpawnAbility(int lightningCount, float reload, float rotateSpeed, float phaseSpeed, float lightningRange, float lightningOffset, float lightningDamage) {
        this.reload = reload;
        this.rotateSpeed = rotateSpeed;
        this.phaseSpeed = phaseSpeed;
        this.lightningCount = lightningCount;
        this.lightningRange = lightningRange;
        this.lightningOffset = lightningOffset;
        this.lightningDamage = lightningDamage;
    }

    @Override
    public void update(Unit unit) {
        timer += Time.delta;
        boolean can = timer >= reload;

        for (int i = 0; i < lightningCount; i++) {
            // 每个球的位置: 绕单位旋转 (单位 id 决定旋转方向, 保证左右对称美观)
            Tmp.v1.trns(
                (Time.time + rotateSpeed + 360f * i / (float) lightningCount + Mathf.randomSeed(unit.id)) * Mathf.signs[unit.id % 2],
                lightningOffset * phase
            ).add(unit);
            float x = Tmp.v1.x, y = Tmp.v1.y;

            if (can) {
                timer = 0f;

                // 独立索敌: 每个球找最近的敌方目标
                Teamc t = Units.closestTarget(unit.team, x, y, lightningRange);
                if (t instanceof Healthc h) {
                    h.damage(lightningDamage);

                    hitEffect.at(h.x(), h.y(), unit.angleTo(h), backColor);
                    damageEffect.at(x, y, 2f, frontColor, h);
                    hitEffect.at(x, y, unit.angleTo(h), backColor);

                    Sounds.shootArc.at(x, y, Mathf.random(0.8f, 1.2f)); // v158 无 Sounds.spark, 用 shootArc 替代
                }
            }

            if (Mathf.chanceDelta(trailChance)) trailEffect.at(x, y, lightningRadius);
        }

        // 相位: v158 无单位弹药, 固定展开 (原版随弹药余量收缩)
        phase = Mathf.lerpDelta(phase, 1f, phaseSpeed);
    }

    @Override
    public void draw(Unit unit) {
        float z = Draw.z();
        Draw.z(Layer.bullet);

        TextureRegion shade = arc.Core.atlas.find("circle-shadow");
        for (int i = 0; i < lightningCount; i++) {
            Tmp.v1.trns(
                (Time.time + rotateSpeed + 360f * i / (float) lightningCount + Mathf.randomSeed(unit.id)) * Mathf.signs[unit.id % 2],
                lightningOffset * phase
            ).add(unit);

            float out = lightningOuterRadius + Mathf.absin(8f, 0.5f);
            float in = lightningRadius + Mathf.absin(6f, 0.4f);
            float bet = Mathf.lerp(in, out, 0.2f);

            float x = Tmp.v1.x, y = Tmp.v1.y;

            Draw.color(backColor);
            Draw.rect(shade, x, y, lightningOuterRadius * 2f, lightningOuterRadius * 1.8f);
            Draw.color(frontColor);
            Fill.circle(x, y, lightningRadius);

            // 双层旋转弧线 (内外层旋转方向相反)
            Lines.stroke((2f + Mathf.absin(15f, 0.7f)), Tmp.c1.set(backColor).lerp(frontColor, 0.7f));
            for (int s = 0; s < sectors; s++) {
                Lines.arc(x, y, bet - 2f, 0.1f, s * 360f / sectors + Time.time * rotateSpeed * Mathf.signs[unit.id % 2]);
            }

            Lines.stroke(Lines.getStroke() - 1f, frontColor);
            for (int s = 0; s < sectors; s++) {
                Lines.arc(x, y, bet, 0.14f, s * 360f / sectors + Time.time * rotateSpeed * Mathf.signs[(unit.id + 1) % 2]);
            }

            Drawf.light(x, y, lightningOuterRadius * 2f, frontColor, phase);
        }

        Draw.z(z);
    }

    @Override
    public String localized() {
        return arc.Core.bundle.get("ability.lightningspawn");
    }
}
