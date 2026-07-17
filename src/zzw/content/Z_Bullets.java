package zzw.content;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Fires;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

import static mindustry.Vars.tilesize;

/**
 * PU_V8 自定义子弹类型 (v158 移植版)
 * 包含: SmokeBulletType, RoundLaserBulletType, ArcBulletType, AcceleratingLaserBulletType,
 *       DecayBasicBulletType, TriangleBulletType, BeamBulletType, ShieldBulletType,
 *       VelocityLaserBoltBulletType, EphemeronBulletType, EphemeronPairBulletType,
 *       SparkingContinuousLaserBulletType, SingularityBulletType
 *
 * 简化策略:
 * - 移除 UnityFx / UnityPal / HitFx / ChargeFx / ShootFx 依赖, 用 v158 Fx / Pal 替代
 * - 移除 ExtraEffect / Utils 等复杂工具类依赖
 * - 保留核心机制 (伤害/范围/碰撞/闪电/frag)
 */
public class Z_Bullets {

    /** ===== SmokeBulletType (PU_V8 celsius/kelvin) ===== */
    public static class SmokeBulletType extends BasicBulletType {
        public float baseSize = 3f;
        public float growAmount = 4.1f;

        public SmokeBulletType(float speed, float damage) {
            super(speed, damage);
        }

        public SmokeBulletType() {
            this(1f, 1f);
        }

        @Override
        public void draw(Bullet b) {
            Draw.color(Pal.lancerLaser, Color.valueOf("4f72e1"), b.fin());
            Fill.poly(b.x, b.y, 6, baseSize + b.fin() * growAmount, b.rotation() + b.fin() * 270f);
            Draw.reset();
        }
    }

    /** ===== RoundLaserBulletType (PU_V8 muon/higgsBoson) ===== */
    public static class RoundLaserBulletType extends LaserBulletType {
        public float lightStroke = 40f;
        public float spaceMag = 45f;
        public float[] tscales = {1f, 0.7f, 0.5f, 0.24f};
        public float[] strokes = {2.8f, 2.4f, 1.9f, 1.3f};
        public float[] lenscales = {1f, 1.13f, 1.16f, 1.17f};

        public RoundLaserBulletType(float damage) {
            super(damage);
            lifetime = 14f;
            colors = new Color[]{Color.valueOf("4787ff55"), Color.valueOf("4787ffaa"), Pal.lancerLaser, Color.white};
        }

        @Override
        public void draw(Bullet b) {
            float realLength = b.fdata;
            float baseLen = realLength * b.fout();

            Lines.lineAngle(b.x, b.y, b.rotation(), baseLen);
            for (int s = 0; s < colors.length; s++) {
                Draw.color(Tmp.c1.set(colors[s]));
                for (int i = 0; i < tscales.length; i++) {
                    Tmp.v1.trns(b.rotation() + 180f, (lenscales[i] - 1f) * spaceMag);
                    Lines.stroke(width * b.fout() * strokes[s] * tscales[i]);
                    Lines.lineAngle(b.x + Tmp.v1.x, b.y + Tmp.v1.y, b.rotation(), baseLen * lenscales[i], false);
                }
            }
            Tmp.v1.trns(b.rotation(), baseLen * 1.1f);
            Drawf.light(b.x, b.y, b.x + Tmp.v1.x, b.y + Tmp.v1.y, lightStroke, lightColor, 0.7f);
            Draw.reset();
        }
    }

    /** ===== ArcBulletType (PU_V8 caster/storm) ===== */
    public static class ArcBulletType extends BulletType {
        public Color fromColor = Color.valueOf("6c8fc7"), toColor = Color.valueOf("606571");
        public Color lightningC1 = Pal.lancerLaser, lightningC2 = Color.valueOf("8494b3");
        public int length1, length2 = 8, lengthRand1, lengthRand2 = 4;
        public float lightningDamage1, lightningDamage2;
        public float lightningInaccuracy1, lightningInaccuracy2 = 180f;
        public float radius = 12f;
        public float lightningChance1, lightningChance2;

        public ArcBulletType(float speed, float damage) {
            super(speed, damage);
            despawnEffect = shootEffect = Fx.none;
            collidesTiles = false;
            hittable = false;
            pierce = true;
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            if (Mathf.chanceDelta(lightningChance1)) {
                Tmp.v1.trns(b.rotation() + Mathf.range(2f), radius);
                Lightning.create(b, lightningC1, lightningDamage1, b.x + Tmp.v1.x + Mathf.range(radius), b.y + Tmp.v1.y + Mathf.range(radius), b.rotation() + Mathf.range(lightningInaccuracy1), length1 + Mathf.range(lengthRand1));
            }
            if (Mathf.chanceDelta(lightningChance2)) {
                Tmp.v1.trns(b.rotation() + Mathf.range(2f), radius);
                Lightning.create(b, lightningC1, lightningDamage2, b.x + Tmp.v1.x + Mathf.range(radius), b.y + Tmp.v1.y + Mathf.range(radius), b.rotation() + Mathf.range(lightningInaccuracy2), length2 + Mathf.range(lengthRand2));
            }
        }

        @Override
        public void draw(Bullet b) {
            Draw.color(fromColor, toColor, b.fin());
            Fill.poly(b.x, b.y, 6, 6f + b.fout() * 6.1f, b.rotation());
            Draw.reset();
        }
    }

    /** ===== AcceleratingLaserBulletType (PU_V8 eclipse) - 简化版 ===== */
    public static class AcceleratingLaserBulletType extends BulletType {
        public float maxLength = 1000f;
        public float laserSpeed = 15f;
        public float accel = 25f;
        public float width = 12f, collisionWidth = 8f;
        public float fadeTime = 60f;
        public float fadeInTime = 8f;
        public float oscOffset = 1.4f, oscScl = 1.1f;
        public float pierceAmount = 4f;
        public Color[] colors = {Color.valueOf("ec745855"), Color.valueOf("ec7458aa"), Color.valueOf("ff9c5a"), Color.white};

        public AcceleratingLaserBulletType(float damage) {
            super(0f, damage);
            despawnEffect = Fx.none;
            collides = false;
            pierce = true;
            impact = true;
            keepVelocity = false;
            hittable = false;
            absorbable = false;
        }

        @Override
        public float continuousDamage() {
            return damage / 5f * 60f;
        }

        public float range() {
            return maxRange > 0 ? maxRange : maxLength / 1.5f;
        }

        @Override
        public void init() {
            super.init();
            drawSize = maxLength * 2f;
            despawnHit = false;
        }

        @Override
        public void init(Bullet b) {
            super.init(b);
            b.fdata = 0f;
        }

        @Override
        public void update(Bullet b) {
            if (b.timer(0, 5f)) {
                if (accel > 0.01f) {
                    b.fdata = Mathf.clamp(b.fdata + laserSpeed * Time.delta * 0.5f, 0f, maxLength);

                } else {
                    b.fdata = maxLength;
                }
                Tmp.v1.trns(b.rotation(), b.fdata).add(b);
                Damage.collideLaser(b, Math.min(b.fdata, maxLength), false, false, -1);
            }
        }

        @Override
        public void draw(Bullet b) {
            float fadeIn = fadeInTime <= 0f ? 1f : Mathf.clamp(b.time / fadeInTime);
            float fade = Mathf.clamp(b.time > b.lifetime - fadeTime ? 1f - (b.time - (lifetime - fadeTime)) / fadeTime : 1f) * fadeIn;
            float tipHeight = width / 2f;

            Lines.lineAngle(b.x, b.y, b.rotation(), b.fdata);
            for (int i = 0; i < colors.length; i++) {
                float f = ((float) (colors.length - i) / colors.length);
                float w = f * (width + Mathf.absin(arc.util.Time.time + (i * oscOffset), oscScl, width / 4)) * fade;

                Tmp.v2.trns(b.rotation(), b.fdata - tipHeight).add(b);
                Tmp.v1.trns(b.rotation(), width * 2f).add(Tmp.v2);
                Draw.color(colors[i]);
                Fill.circle(b.x, b.y, w / 2f);
                Lines.stroke(w);
                Lines.line(b.x, b.y, Tmp.v2.x, Tmp.v2.y, false);
                for (int s : Mathf.signs) {
                    Tmp.v3.trns(b.rotation(), w * -0.7f, w * s);
                    Fill.tri(Tmp.v2.x, Tmp.v2.y, Tmp.v1.x, Tmp.v1.y, Tmp.v2.x + Tmp.v3.x, Tmp.v2.y + Tmp.v3.y);
                }
            }
            Tmp.v2.trns(b.rotation(), b.fdata + tipHeight).add(b);
            Drawf.light(b.x, b.y, Tmp.v2.x, Tmp.v2.y, width * 2f, colors[0], 0.5f);
            Draw.reset();
        }

        @Override
        public void drawLight(Bullet b) {
        }
    }

    /** ===== DecayBasicBulletType (PU_V8 wBoson) ===== */
    public static class DecayBasicBulletType extends BasicBulletType {
        public float backMinRadius = 3f, frontMinRadius = 1.75f;
        public float backRadius = 6f, frontRadius = 5.75f;
        public float minInterval = 0.75f, maxInterval = 1.75f;
        public float decayMinVel = 0.9f, decayMaxVel = 1.1f;
        public float decayMinLife = 0.3f, decayMaxLife = 1.3f;
        public BulletType decayBullet;

        public DecayBasicBulletType(float speed, float damage) {
            super(speed, damage);
        }

        @Override
        public void draw(Bullet b) {
            Draw.color(backColor);
            Fill.circle(b.x, b.y, backMinRadius + b.fout() * backRadius);
            Draw.color(frontColor);
            Fill.circle(b.x, b.y, frontMinRadius + b.fout() * frontRadius);
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            if (decayBullet != null && b.timer(1, Mathf.lerp(maxInterval, minInterval, b.fin()))) {
                decayBullet.create(b, b.team, b.x, b.y, b.rotation() + Mathf.range(180f), Mathf.random(decayMinVel, decayMaxVel), Mathf.lerp(decayMaxLife, decayMinLife, b.fin()));
            }
        }
    }

    /** ===== TriangleBulletType (PU_V8 plasma) ===== */
    public static class TriangleBulletType extends BulletType {
        public float lifetimeRand = 0f;
        public boolean castsLightning = false;
        public int castInterval = 12;
        public float castRadius = 8f;
        public float length, width;
        public Color color = Pal.surge;

        public TriangleBulletType(float length, float width, float speed, float damage) {
            super(speed, damage);
            this.length = length;
            this.width = width;
            trailColor = lightningColor = Pal.surge;
            hitColor = Color.valueOf("f2e87b");
        }

        public TriangleBulletType(float speed, float damage) {
            this(1f, 1f, speed, damage);
        }

        public TriangleBulletType() {
            this(1f, 1f, 1f, 1f);
        }

        @Override
        public void init(Bullet b) {
            super.init(b);
            b.lifetime = b.lifetime + Mathf.random(lifetimeRand);
        }

        @Override
        public void draw(Bullet b) {
            drawTrail(b);
            Draw.color(lightningColor);
            Drawf.tri(b.x, b.y, width, length, b.rotation());
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            if (castsLightning && b.timer.get(1, castInterval)) {
                mindustry.gen.Teamc target = mindustry.entities.Units.closestTarget(b.team, b.x, b.y, castRadius * tilesize);
                if (target != null) {
                    Lightning.create(b.team, lightningColor, damage, b.x, b.y, b.angleTo(target), (int) (b.dst(target) / tilesize + 2));
                }
            }
        }
    }

    /** ===== BeamBulletType (PU_V8 shockwire) ===== */
    public static class BeamBulletType extends BulletType {
        public Color color = Pal.heal;
        public float beamWidth = 0.6f;
        public float lightWidth = 15f;
        public float length;
        public boolean castsLightning;
        public float castInterval = 5f;
        public float minLightningDamage, maxLightningDamage;

        public BeamBulletType(float length, float damage) {
            super(0.01f, damage);
            this.length = length;
            // v158 BulletType.range 是字段而非方法, 直接赋值
            range = length;
            keepVelocity = false;
            collides = false;
            pierce = true;
            hittable = false;
            absorbable = false;
            lifetime = 16f;
            shootEffect = Fx.none;
            despawnEffect = Fx.none;
            hitSize = 0f;
        }

        public BeamBulletType() {
            this(1f, 1f);
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            Healthc target = Damage.linecast(b, b.x, b.y, b.rotation(), this.length);
            b.data = target;

            if (target instanceof Hitboxc hit) {
                if (b.timer.get(1, castInterval)) {
                    hit.collision(b, target.getX(), target.getY());
                    b.collision(hit, target.getX(), target.getY());
                    if (castsLightning) {
                        Lightning.create(b.team, color, Mathf.random(minLightningDamage, maxLightningDamage), b.x, b.y, b.angleTo(target), Mathf.floorPositive(b.dst(target) / tilesize + 3));
                    }
                }
            } else if (target instanceof mindustry.gen.Building build) {
                if (b.timer.get(1, castInterval)) {
                    if (build.collide(b)) {
                        build.collision(b);
                        hit(b, target.getX(), target.getY());
                    }
                    if (castsLightning) {
                        Lightning.create(b.team, color, Mathf.random(minLightningDamage, maxLightningDamage), b.x, b.y, b.angleTo(target), Mathf.floorPositive(b.dst(target) / tilesize + 3));
                    }
                }
            } else {
                b.data = new Vec2().trns(b.rotation(), this.length).add(b.x, b.y);
                if (b.timer.get(1, castInterval) && castsLightning) {
                    Vec2 point = (Vec2) b.data;
                    Lightning.create(b.team, color, Mathf.random(minLightningDamage, maxLightningDamage), b.x, b.y, b.angleTo(point.x, point.y), Mathf.floorPositive(b.dst(point.x, point.y) / tilesize + 3));
                }
            }
        }

        // v158 range 为字段, 已在构造函数中赋值, 无需重写方法

        @Override
        public void draw(Bullet b) {
            if (b.data instanceof arc.math.geom.Position data) {
                Tmp.v1.set(data);
                Draw.color(color);
                Drawf.light(b.x, b.y, Tmp.v1.x, Tmp.v1.y, lightWidth * b.fout(), color, 0.6f);
                Lines.stroke(beamWidth * b.fout() * 3f);
                Lines.line(b.x, b.y, Tmp.v1.x, Tmp.v1.y, false);
                Draw.reset();
            }
        }
    }

    /** ===== ShieldBulletType (PU_V8 shielder) - 简化版 ===== */
    public static class ShieldBulletType extends BasicBulletType {
        public float shieldHealth = 3000f;
        public float maxRadius = 10f;

        public ShieldBulletType(float speed) {
            super(speed, 0);
            drag = 0.3f;
            lifetime = 20000f;
            shootEffect = Fx.none;
            despawnEffect = Fx.none;
            collides = false;
            hitSize = 0;
            hittable = false;
            hitEffect = Fx.none;
        }

        @Override
        public void update(Bullet b) {
            if (b.data == null) {
                float[] data = new float[2];
                data[0] = shieldHealth;
                data[1] = 0f;
                b.data = data;
            }

            float radius = (((speed - b.vel.len()) * maxRadius) + 1) * 0.8f;
            float[] temp = (float[]) b.data;
            mindustry.gen.Groups.bullet.intersect(b.x - radius, b.y - radius, radius * 2, radius * 2, e -> {
                if (e != null && e.team != b.team) {
                    float health = temp[0] - e.damage;
                    temp[0] = health;
                    temp[1] = 1;
                    e.remove();
                }
            });

            if (temp[0] <= 0) {
                b.remove();
            }

            if (temp[0] > 0) {
                float hit = temp[1] - 1f - 0.2f * ((float) arc.util.Time.delta);
                temp[1] = hit;
            }
        }

        @Override
        public void draw(Bullet b) {
            Draw.z(Layer.shields);
            if (b.data == null) return;
            float[] temp = (float[]) b.data;
            Draw.color(b.team.color, Color.white, Mathf.clamp(temp[1]));
            float radius = ((speed - b.vel.len()) * maxRadius) + 1;
            if (arc.Core.settings.getBool("animatedshields")) {
                Fill.poly(b.x, b.y, 6, radius);
            } else {
                Lines.stroke(1.5f);
                Draw.alpha(0.09f + Mathf.clamp(0.08f * temp[1]));
                Fill.poly(b.x, b.y, 6, radius);
                Draw.alpha(1);
                Lines.poly(b.x, b.y, 6, radius);
                Draw.reset();
            }
            Draw.z(Layer.block);
            Draw.color();
        }
    }

    /** ===== VelocityLaserBoltBulletType (PU_V8 zBoson) ===== */
    public static class VelocityLaserBoltBulletType extends BasicBulletType {
        public VelocityLaserBoltBulletType(float speed, float damage) {
            super(speed, damage);
            backColor = Color.valueOf("a9d8ff");
            frontColor = Color.valueOf("ffffff");
            width = 4.75f;
            height = 4f;
            hitEffect = Fx.hitLancer;
            despawnEffect = Fx.hitLancer;
            shootEffect = Fx.none;
            smokeEffect = Fx.none;
        }

        @Override
        public void draw(Bullet b) {
            float vel = b.vel().len() * 4f;
            Draw.color(backColor);
            Fill.circle(b.x, b.y, width / 2f);
            Draw.color(frontColor);
            Fill.circle(b.x, b.y, width / 3f);
        }
    }

    /** ===== EphemeronPairBulletType (PU_V8 ephemeron) ===== */
    public static class EphemeronPairBulletType extends BasicBulletType {
        public boolean positive;

        public EphemeronPairBulletType(float damage) {
            super(0.001f, damage);
            // ★ 用户反馈小球存在时间过短, 将 lifetime 从 360f 延长至 720f
            lifetime = 720f;
            hitEffect = Fx.hitLancer;
            despawnEffect = Fx.none;
            hitSize = 8f;
            drag = 0.015f;
            pierce = true;
            hittable = false;
            absorbable = false;
            collidesTiles = false;
        }

        @Override
        public void draw(Bullet b) {
            Draw.color(frontColor);
            Fill.circle(b.x, b.y, 4f + (b.fout() * 1.5f));
            Draw.color(backColor);
            Fill.circle(b.x, b.y, 2.5f + (b.fout()));
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            if (b.data instanceof Bullet n && n.isAdded()) {
                float dst = hitSize / Math.max(b.dst(n) / 2f, hitSize);
                Tmp.v1.set(n).sub(b).nor().scl(dst);
                b.vel.add(Tmp.v1);

                if (!positive) return;

                b.hitbox(Tmp.r1);
                n.hitbox(Tmp.r2);
                if (Tmp.r1.overlaps(Tmp.r2)) {
                    b.remove();
                    n.remove();
                    Tmp.v1.set((b.x + n.x) / 2f, (b.y + n.y) / 2f);
                    Damage.damage(b.team, Tmp.v1.x, Tmp.v1.y, 40f, 80f);
                }
            }
        }
    }

    /** ===== EphemeronBulletType (PU_V8 ephemeron) ===== */
    public static class EphemeronBulletType extends BasicBulletType {
        public Color midColor = Pal.lancerLaser;
        public float[] baseRadius = {11f, 8f, 6.5f}, extraRadius = {2.5f, 1.5f, 1f};
        public float maxRadius = 80f;
        public int pairs = 15;
        public BulletType positive, negative;

        public EphemeronBulletType(float speed, float damage) {
            super(speed, damage);
            hittable = false;
            backColor = Color.valueOf("a9d8ff60");
            frontColor = Color.white;
        }

        @Override
        public void draw(Bullet b) {
            Draw.color(backColor);
            Fill.circle(b.x, b.y, baseRadius[0] + (b.fout() * extraRadius[0]));
            Draw.color(midColor);
            Fill.circle(b.x, b.y, baseRadius[1] + (b.fout() * extraRadius[1]));
            Draw.color(frontColor);
            Fill.circle(b.x, b.y, baseRadius[2] + (b.fout() * extraRadius[2]));
        }

        @Override
        public void despawned(Bullet b) {
            super.despawned(b);
            if (positive == null || negative == null) return;
            for (int i = 0; i < pairs; i++) {
                Tmp.v1.rnd(Mathf.range(maxRadius)).add(b);
                float randomSign = Mathf.random(180f);
                float angleRandom = Mathf.range(360f);
                float rangeRandom = Mathf.range(40f, 70f);
                Tmp.v2.trns(angleRandom, rangeRandom);
                Bullet pos = positive.create(b, Tmp.v1.x + Tmp.v2.x, Tmp.v1.y + Tmp.v2.y, angleRandom + randomSign);
                Tmp.v2.rotate(180f);
                Bullet neg = negative.create(b, Tmp.v1.x + Tmp.v2.x, Tmp.v1.y + Tmp.v2.y, angleRandom + randomSign + 180f);
                pos.data = neg;
                neg.data = pos;
            }
        }
    }

    /** ===== SparkingContinuousLaserBulletType (PU_V8 fallout/catastrophe/calamity/extinction) =====
     * ★移植 PU_V8 完整机制:
     *  - fromBlockChance/fromBlockAmount: 在炮台位置生成定向闪电
     *  - fromLaserChance/fromLaserAmount: 在激光线上随机点生成闪电
     *  - incendChance/incendSpread/incendAmount: 在激光线上生成火焰 (v158继承自BulletType)
     *  - extinction=true: 锥形扫描区域点燃地面 (Fires.create) + 损伤敌方建筑
     */
    public static class SparkingContinuousLaserBulletType extends ContinuousLaserBulletType {
        public float fromBlockChance = 0.4f, fromBlockDamage = 23f;
        public float fromLaserChance = 0.9f, fromLaserDamage = 23f;
        public float incendStart = 2.9f;
        public float coneRange = 1.1f;
        public int fromLaserLen = 4, fromLaserLenRand = 5, fromLaserAmount = 1;
        public int fromBlockLen = 2, fromBlockLenRand = 5, fromBlockAmount = 1;
        public boolean extinction = false;
        public Color sparkColor = Color.valueOf("ff9c5a");

        public SparkingContinuousLaserBulletType(float damage) {
            super(damage);
            lightningColor = Color.valueOf("ff9c5a");
        }

        public SparkingContinuousLaserBulletType() {
            this(0f);
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            float realLength = Damage.findLaserLength(b, length);

            // 炮台位置闪电 (fromBlock)
            for (int i = 0; i < fromBlockAmount; i++) {
                if (Mathf.chanceDelta(fromBlockChance)) {
                    Lightning.create(b.team, lightningColor, fromBlockDamage, b.x, b.y, b.rotation(),
                            Mathf.round(length / 8f) + fromBlockLen + Mathf.random(fromBlockLenRand));
                }
            }
            // 激光线上的闪电 (fromLaser)
            for (int i = 0; i < fromLaserAmount; i++) {
                if (Mathf.chanceDelta(fromLaserChance)) {
                    int lLength = fromLaserLen + Mathf.random(fromLaserLenRand);
                    Tmp.v1.trns(b.rotation(), Mathf.random(0, Math.max(realLength - lLength * 8f, 4f)));
                    Lightning.create(b.team, sparkColor, fromLaserDamage, b.x + Tmp.v1.x, b.y + Tmp.v1.y, b.rotation(), lLength);
                }
            }
            // 激光线上点燃火灾
            if (incendChance > 0 && Mathf.chance(incendChance)) {
                Tmp.v1.trns(b.rotation(), Mathf.random(incendStart, realLength));
                Damage.createIncend(b.x + Tmp.v1.x, b.y + Tmp.v1.y, incendSpread, incendAmount);
            }

            // extinction 锥形扫描: 在激光路径前方锥形区域内点燃地面+伤害敌方建筑
            if (extinction && b.timer(2, 15f)) {
                float coneLen = length * coneRange;
                float coneHalfAngle = 70f;
                // 遍历锥形区域内的所有 tile
                int tx = mindustry.Vars.world.toTile(b.x);
                int ty = mindustry.Vars.world.toTile(b.y);
                int range = Mathf.ceilPositive(coneLen / mindustry.Vars.tilesize);
                for (int dx = -range; dx <= range; dx++) {
                    for (int dy = -range; dy <= range; dy++) {
                        mindustry.world.Tile tile = mindustry.Vars.world.tile(tx + dx, ty + dy);
                        if (tile == null) continue;
                        float wx = tile.worldx(), wy = tile.worldy();
                        float ang = Angles.angle(wx - b.x, wy - b.y);
                        float angDiff = Math.abs(Angles.angleDist(ang, b.rotation()));
                        if (angDiff > coneHalfAngle) continue;
                        float dst = Mathf.dst(wx - b.x, wy - b.y);
                        if (dst > coneLen) continue;
                        float angD = Mathf.clamp(1f - angDiff / coneHalfAngle);
                        float dstC = Mathf.clamp(1f - dst / coneLen);
                        // 锥形点燃地面
                        if (Mathf.chance(arc.math.Interp.smooth.apply(angD) * 0.32f * Mathf.clamp(dstC * 1.7f))) {
                            Fires.create(tile);
                        }
                        // 锥形伤害敌方建筑
                        mindustry.gen.Building build = tile.build;
                        if (build != null && build.team != b.team) {
                            build.damage(arc.math.Interp.smooth.apply(angD) * 23.3f * Mathf.clamp(dstC * 1.7f));
                        }
                    }
                }
            }
        }
    }

    /** ===== GravitonLaserBulletType (PU132 graviton) - 重力子牵引激光 =====
     * ★完整移植 PU132 GravitonLaserBulletType:
     *  - 较高可见度的连续激光 (彩色 alpha 较高, 较粗的描边)
     *  - 负值 knockback 实现吸引效果
     *  - 自定义 draw 方法使用 strokes[] 数组渲染
     */
    public static class GravitonLaserBulletType extends ContinuousLaserBulletType {
        public int max = 6;
        public float[] strokes = {2.4f, 1.8f};
        public float[] tscales = {1f, 0.7f};
        public float[] lenscales = {1f, 1.13f};
        public float spaceMag = 45f;
        public float oscMag = 1.5f;
        public float oscScl = 0.8f;
        public float widthMul = 1f;

        public GravitonLaserBulletType(float damage) {
            super(damage);
            fadeTime = 16f;
        }

        @Override
        public void init(Bullet b) {
            super.init(b);
            b.fdata = length;
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            // ★ 补充: 对范围内敌方单位施加持续吸引力 (每5tick)
            if (b.timer(2, 5f)) {
                mindustry.entities.Units.nearbyEnemies(b.team, b.x, b.y, length, u -> {
                    if (u != null && u.isValid()) {
                        // 仅影响激光前方锥形范围内单位
                        float ang = b.angleTo(u);
                        if (Math.abs(Angles.angleDist(ang, b.rotation())) > 30f) return;
                        float dst = b.dst(u);
                        if (dst > length) return;
                        // 越远拉力越大
                        Tmp.v1.set(b).sub(u).nor().scl(Math.abs(knockback) * 80f * (1f - dst / length) * Time.delta * 5f);
                        u.impulse(Tmp.v1);
                    }
                });
            }
        }

        @Override
        public void draw(Bullet b) {
            float realLength = b.fdata;
            float fout = Mathf.clamp(b.time > b.lifetime - fadeTime ? 1f - (b.time - (lifetime - fadeTime)) / fadeTime : 1f);
            float baseLen = realLength * fout;

            Lines.lineAngle(b.x, b.y, b.rotation(), baseLen);
            for (int s = 0; s < colors.length; s++) {
                Draw.color(Tmp.c1.set(colors[s]).mul(1f + Mathf.absin(Time.time, 1f, 0.1f)));
                for (int i = 0; i < tscales.length; i++) {
                    Tmp.v1.trns(b.rotation() + 180f, (lenscales[i] - 1f) * spaceMag);
                    Lines.stroke((width + Mathf.absin(Time.time, oscScl, oscMag)) * fout * strokes[s] * tscales[i] * widthMul);
                    Lines.lineAngle(b.x + Tmp.v1.x, b.y + Tmp.v1.y, b.rotation(), baseLen * lenscales[i], false);
                }
            }

            Tmp.v1.trns(b.rotation(), baseLen * 1.1f);
            Drawf.light(b.x, b.y, b.x + Tmp.v1.x, b.y + Tmp.v1.y, lightStroke, lightColor, 0.7f);
            Draw.reset();
        }
    }

    /** ===== SingularityBulletType (PU_V8 singularity) - 黑洞子弹 =====
     * ★完整移植 PU_V8 黑洞机制:
     *  - 吸引锥形范围内敌方单位 (force + scaledForce)
     *  - 范围内敌方建筑受持续伤害, 接近中心的建筑被瞬间摧毁
     *  - 中心范围内的单位受到瞬间伤害
     *  - 多层同心圆+旋转尖刺光球渲染 (shiningCircle 风格)
     *  - GluonOrbData 单位列表管理 (timer 2f 间隔收集单位, 每帧吸引)
     */
    public static class SingularityBulletType extends BasicBulletType {
        public float force = 8f, scaledForce = 5f;
        public float tileDamage = 150f;
        public float radius = 230f;
        public float size = 5f;
        public float buildingDamageMultiplier = 1f;
        public float[] scales = {8.6f, 7f, 5.5f, 4.2f, 3.9f};
        public Color[] colors = new Color[]{Color.valueOf("4787ff80"), Pal.lancerLaser, Color.white, Pal.lancerLaser, Color.black};

        public SingularityBulletType(float damage) {
            super(0.001f, damage);
            pierce = pierceBuilding = true;
            hitEffect = Fx.none;
            despawnEffect = Fx.blastExplosion;
            hitSize = 19f;
            lifetime = 3.5f * 60f;
        }

        @Override
        public void init(Bullet b) {
            super.init(b);
            b.data = new GluonOrbData();
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            float interp = b.fin(Interp.exp10Out);
            Effect.shake(interp, interp, b);

            // 建筑伤害 (每 7 tick 一次)
            if (b.timer(1, 7f)) {
                Vars.indexer.eachBlock(null, b.x, b.y, radius, build -> build.team != b.team, e -> {
                    if (e.isValid() && e.team != b.team) {
                        // 中心范围内或血量低于阈值的建筑直接摧毁
                        if (e.health < tileDamage || Mathf.within(b.x, b.y, e.x, e.y, (interp * size * 3.9f) + e.block.size / 2f)) {
                            e.kill();
                        }
                        float dst = Math.abs(1f - (Mathf.dst(b.x, b.y, e.x, e.y) / radius));
                        e.damage(tileDamage * buildingDamageMultiplier * dst);
                    }
                });
            }

            // 单位收集 + 中心伤害 (每 2 tick 一次)
            if (b.data instanceof GluonOrbData) {
                GluonOrbData data = (GluonOrbData) b.data;
                if (b.timer(2, 2f)) {
                    data.units.clear();
                    Units.nearbyEnemies(b.team, b.x - radius, b.y - radius, 2 * radius, 2 * radius, u -> {
                        if (u != null && Mathf.within(b.x, b.y, u.x, u.y, radius)) {
                            data.units.add(u);
                            // 中心瞬间伤害
                            if (Mathf.within(b.x, b.y, u.x, u.y, (interp * size * 3.9f) + u.hitSize / 2f)) {
                                u.damage(120f);
                            }
                        }
                    });
                    // 范围伤害
                    Damage.damage(b.team, b.x, b.y, hitSize, damage);
                }
                // 每帧吸引 (收集到的单位列表)
                data.units.each(u -> {
                    if (!u.dead) {
                        Tmp.v1.trns(u.angleTo(b), force + ((1f - u.dst(b) / radius) * scaledForce * b.fin(Interp.exp10Out) * (u.isFlying() ? 1.5f : 1f))).scl(20f * Time.delta);
                        u.impulse(Tmp.v1);
                    }
                });
            }
        }

        @Override
        public void draw(Bullet b) {
            float interp = b.fin(Interp.exp10Out);
            for (int i = 0; i < colors.length; i++) {
                Draw.color(colors[i]);
                if (i != 0) {
                    // 旋转尖刺三角形光球 (替代 UnityDrawf.shiningCircle)
                    // 中心实心圆
                    Fill.circle(b.x + Mathf.range(0.5f), b.y + Mathf.range(0.5f), interp * size * scales[i]);
                    // 旋转尖刺三角形 (按 spikeDuration 周期闪烁)
                    int spikeCount = 6;
                    float baseAngle = Time.time * 1.5f - i * 30f;
                    float radius0 = interp * size * scales[i];
                    float spikeLen = radius0 * 1.4f;
                    for (int s = 0; s < spikeCount; s++) {
                        float ang = baseAngle + s * (360f / spikeCount);
                        Tmp.v1.trns(ang, spikeLen).add(b);
                        Tmp.v2.trns(ang + 30f, radius0 * 0.5f).add(b);
                        Tmp.v3.trns(ang - 30f, radius0 * 0.5f).add(b);
                        Fill.tri(Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y, Tmp.v3.x, Tmp.v3.y);
                    }
                } else {
                    Fill.circle(b.x + Mathf.range(0.5f), b.y + Mathf.range(0.5f), interp * size * scales[i]);
                }
            }
            Draw.color();
        }
    }

    /** GluonOrbData - 黑洞单位列表管理 (PU_V8 移植) */
    public static class GluonOrbData {
        public Seq<Unit> units = new Seq<>();
    }
}
