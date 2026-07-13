package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;

import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.util.Tmp;
// import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.graphics.Drawf;

/**
 * PU132 EndContinuousLaserBulletType 移植版
 * - 4色 × 4时间缩放叠加渲染 (16条线段)
 * - 振荡宽度 (oscScl/oscMag)
 * - 闪电效果 (lightningChance)
 * - 充能特效 (chargeEffect)
 * - 防作弊伤害 (继承 AntiCheatBulletTypeBase)
 * 参考: PU132 main/src/unity/entities/bullet/anticheat/EndContinuousLaserBulletType.java
 */
public class EndContinuousLaserBulletType extends AntiCheatBulletTypeBase {
    public float length = 220f;
    public float shake = 1f;
    public float fadeTime = 16f;
    public float lightStroke = 40f;
    public float spaceMag = 35f;
    public Color[] colors = {Color.valueOf("ec745855"), Color.valueOf("ec7458aa"), Color.valueOf("ff9c5a"), Color.white};
    public float[] tscales = {1f, 0.7f, 0.5f, 0.2f};
    public float[] strokes = {2f, 1.5f, 1f, 0.3f};
    public float[] lenscales = {1f, 1.12f, 1.15f, 1.17f};
    public float width = 9f, oscScl = 0.8f, oscMag = 1.5f;
    public boolean largeHit = true;

    // 闪电效果参数 (PU132 endLaser 独有)
    public float lightningChance = 0f;
    public float lightningDamage = 0f;
    public int lightningLength = 0;
    public int lightningLengthRand = 0;

    // 充能特效 (PU132 ChargeFx.devourerChargeEffect)
    public Effect chargeEffect = mindustry.content.Fx.none;

    public EndContinuousLaserBulletType(float damage) {
        this.damage = damage;
        this.speed = 0f;
        this.hitEffect = mindustry.content.Fx.hitBeam;
        this.despawnEffect = mindustry.content.Fx.none;
        this.hitSize = 4;
        this.drawSize = 420f;
        this.lifetime = 16f;
        this.hitColor = colors[2];
        this.lightColor = Color.orange;
        this.impact = true;
        this.pierce = true;
        this.hittable = false;
        this.absorbable = false;
    }

    @Override
    public float continuousDamage() {
        return damage / 5f * 60f;
    }

    @Override
    public void init() {
        super.init();
        drawSize = Math.max(drawSize, length * 2f);
    }

    @Override
    public void update(Bullet b) {
        if (b.timer(1, 5f)) {
            // ★ 原版 PU132 直接使用 length, 不调用 findLaserLength (避免被友方建筑截断)
            b.fdata = length;

            Vec2 v = Tmp.v1.trns(b.rotation(), length).add(b);
            float w = largeHit ? 15f : 3f;

            // ★ 对齐原版 Utils.collideLineRawNew 的精确碰撞框检测
            Rect rect = arc.util.Tmp.r1;
            rect.set(b.x, b.y, 0, 0).merge(v.x, v.y).grow(w * 2f + 50f);

            int[] hitCount = {0};

            // 单位检测: 使用 Groups.unit.intersect + Geometry.raycastRect 精确检测碰撞框
            mindustry.gen.Groups.unit.intersect(rect.x, rect.y, rect.width, rect.height, unit -> {
                if (unit.team == b.team) return;
                if (!unit.checkTarget(collidesAir, collidesGround)) return;
                unit.hitbox(arc.util.Tmp.r2);
                arc.util.Tmp.r2.grow(w * 2f);
                Vec2 vec = arc.math.geom.Geometry.raycastRect(b.x, b.y, v.x, v.y, arc.util.Tmp.r2);
                if (vec != null) {
                    unit.collision(b, vec.x, vec.y);
                    hitUnitAntiCheat(b, unit);
                    if (hitCount[0] < 8) hit(b, vec.x, vec.y);
                    hitCount[0]++;
                    if (b.owner instanceof Healthc h) {
                        h.heal(damage * 0.1f);
                    }
                }
            });

            // 建筑检测: 对齐原版 world.raycastEachWorld 沿激光线逐格检测
            // if (collidesGround) {
            //     Vars.world.raycastEachWorld(b.x, b.y, v.x, v.y, (cx, cy) -> {
            //         mindustry.gen.Building build = Vars.world.build(cx, cy);
            //         if (build != null && build.team != b.team) {
            //             hitBuildingAntiCheat(b, build);
            //             if (hitCount[0] < 8) hit(b, build.x, build.y);
            //             hitCount[0]++;
            //             return build.block.absorbLasers;
            //         }
            //         return false;
            //     });
            // }

            // 闪电效果 (PU132 endLaser: lightningChance=0.8f)
            if (lightningChance > 0 && lightningLength > 0 && Mathf.chanceDelta(lightningChance)) {
                mindustry.entities.Lightning.create(b, colors[2],
                    lightningDamage < 0 ? damage : lightningDamage,
                    b.x + Mathf.range(length * 0.5f), b.y + Mathf.range(length * 0.5f),
                    b.rotation() + Mathf.range(60f),
                    lightningLength + Mathf.random(lightningLengthRand));
            }

            // 震屏
            if (shake > 0) {
                Effect.shake(shake, shake, b);
            }
        }
    }

    @Override
    public void draw(Bullet b) {
        // ★ 对齐 PU132 原版 draw() (EndContinuousLaserBulletType L109-128)
        // - 用 Damage.findLaserLength 获取实际长度 (考虑碰撞截断)
        // - 先画主线, 再 4色×4tscales 叠加 (PU132 顺序)
        // - 无末端三角形 (之前的"激光箭头"是多余的 Drawf.tri)
        float realLength = mindustry.entities.Damage.findLaserLength(b, length);
        float fout = Mathf.clamp(b.time > b.lifetime - fadeTime ? 1f - (b.time - (lifetime - fadeTime)) / fadeTime : 1f);
        float baseLen = realLength * fout;

        // 主线 (PU132 L114)
        Lines.lineAngle(b.x, b.y, b.rotation(), baseLen);

        // 4色 × 4 tscales 叠加 (PU132 L115-122)
        for (int s = 0; s < colors.length; s++) {
            Draw.color(Tmp.c1.set(colors[s]).mul(1f + Mathf.absin(arc.util.Time.time, 1f, 0.1f)));
            for (int i = 0; i < tscales.length; i++) {
                Tmp.v1.trns(b.rotation() + 180f, (lenscales[i] - 1f) * spaceMag);
                Lines.stroke((width + Mathf.absin(arc.util.Time.time, oscScl, oscMag)) * fout * strokes[s] * tscales[i]);
                Lines.lineAngle(b.x + Tmp.v1.x, b.y + Tmp.v1.y, b.rotation(), baseLen * lenscales[i], false);
            }
        }

        // 光源 (PU132 L124-126, v154.3 用 7 参数版本)
        Tmp.v1.trns(b.rotation(), baseLen * 1.1f);
        Drawf.light(b.x, b.y, b.x + Tmp.v1.x, b.y + Tmp.v1.y, lightStroke, lightColor, 0.7f);
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b) {
        // 光源在 draw() 中处理
    }
}
