package zzw.content;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
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
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Fires;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

import zzw.content.graphics.UnityPal;
import zzw.content.units.bullets.KamiBulletType;

import static mindustry.Vars.tilesize;

/**
 * PU_V8 自定义子弹类型 (v158 移植版)
 * 包含: SmokeBulletType, RoundLaserBulletType, ArcBulletType, AcceleratingLaserBulletType,
 *       DecayBasicBulletType, TriangleBulletType, BeamBulletType, ShieldBulletType,
 *       VelocityLaserBoltBulletType, EphemeronBulletType, EphemeronPairBulletType,
 *       SparkingContinuousLaserBulletType, SingularityBulletType
 *       + kami 弹幕子弹 (kamiBullet2, kamiBullet3)
 *
 * 简化策略:
 * - 移除 UnityFx / UnityPal / HitFx / ChargeFx / ShootFx 依赖, 用 v158 Fx / Pal 替代
 * - 移除 ExtraEffect / Utils 等复杂工具类依赖
 * - 保留核心机制 (伤害/范围/碰撞/闪电/frag)
 */
public class Z_Bullets {

    // ===== kami 弹幕子弹 (PU132 移植) =====
    public static BulletType kamiBullet2 = new KamiBulletType();
    public static BulletType kamiBullet3 = new KamiBulletType();

    /**
     * Scar 方向护盾爆炸反射破片弹 (PU132 UnityBullets.scarShrapnel)。
     *
     * <p>DirectionShieldAbility 在护盾反弹高伤害 (>= explosiveDamageThreshold) 子弹时,
     * 向偏转角两侧各 20 度共发射 3 发此破片弹, 伤害 = 造成的护盾伤害 x 0.7 倍率。</p>
     */
    public static BulletType scarShrapnel = new ShrapnelBulletType(){{
        fromColor = UnityPal.endColor;
        toColor = UnityPal.scarColor;
        damage = 1f;
        length = 110f;
    }};

    /**
     * Scar 通用导弹 (PU132 UnityBullets.scarMissile)。
     *
     * <p>sundown/rex/excelsus 的 scar-large-launcher 武器弹体:
     * 微织运动 (weave) + 溅射 + 可贯穿 3 个建筑。</p>
     */
    public static BulletType scarMissile = new MissileBulletType(6f, 12f){{
        lifetime = 70f;
        speed = 5f;
        width = 7f;
        height = 12f;
        shrinkY = 0f;
        backColor = trailColor = UnityPal.scarColor;
        frontColor = UnityPal.endColor;
        splashDamage = 36f;
        splashDamageRadius = 20f;
        weaveMag = 3f;
        weaveScale = 6f;
        pierceBuilding = true;
        pierceCap = 3;
    }};

    static {
        // kamiBullet2 有拖尾 (PU132 trailLength=12), kamiBullet3 无拖尾
        ((KamiBulletType) kamiBullet2).hasTrail = true;
    }

    /** ===== SmokeBulletType (PU_V8 celsius/kelvin) ===== */
    public static class SmokeBulletType extends BasicBulletType {
        public float baseSize = 3f;
        public float growAmount = 4.1f;
        /** ★ PU132: 拖尾生成随机偏移 */
        public float trailRand = 0.6f;
        /** ★ PU132: 烟雾生成随机偏移 */
        public float smokeRand = 1.7f;

        public SmokeBulletType(float speed, float damage) {
            super(speed, damage);
        }

        public SmokeBulletType() {
            this(1f, 1f);
        }

        @Override
        public void update(Bullet b) {
            super.update(b);

            // ★ PU132 原版: 每帧生成 advance 火焰拖尾 + 约 70% 概率生成烟雾
            if (b.timer.get(0, 1)) {
                zzw.content.units.effects.ParticleFx.advanceFlameTrail.at(
                        b.x + Mathf.range(trailRand), b.y + Mathf.range(trailRand), b.rotation());
            }
            if (Mathf.chanceDelta(0.7f)) {
                zzw.content.units.effects.ParticleFx.advanceFlameSmoke.at(
                        b.x + Mathf.range(smokeRand), b.y + Mathf.range(smokeRand), b.rotation());
            }
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
        public float lightningInaccuracy1 = 45f, lightningInaccuracy2 = 180f;
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
            // ★ 严格对齐 PU132 ArcBulletType.update:
            //   闪电位置从弹体沿朝向偏移 radius 再叠加随机抖动, 两道闪电都用 lightningC1 颜色
            if (Mathf.chanceDelta(lightningChance1)) {
                Tmp.v1.trns(b.rotation() + Mathf.range(2f), radius);
                Lightning.create(b, lightningC1, lightningDamage1,
                    b.x + Tmp.v1.x + Mathf.range(radius), b.y + Tmp.v1.y + Mathf.range(radius),
                    b.rotation() + Mathf.range(lightningInaccuracy1), length1 + Mathf.range(lengthRand1));
            }

            if (Mathf.chanceDelta(lightningChance2)) {
                Tmp.v1.trns(b.rotation() + Mathf.range(2f), radius);
                Lightning.create(b, lightningC1, lightningDamage2,
                    b.x + Tmp.v1.x + Mathf.range(radius), b.y + Tmp.v1.y + Mathf.range(radius),
                    b.rotation() + Mathf.range(lightningInaccuracy2), length2 + Mathf.range(lengthRand2));
            }
        }

        @Override
        public void draw(Bullet b) {
            // ★ PU132 原版: 只绘制旋转六边形 (无内层白圆)
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
        /** PU132 decayEffect: 主弹沿途播放的拖尾特效 (w-boson 用) */
        public Effect decayEffect = Fx.none;

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
            // PU132 decayEffect: 随衰减计时播放拖尾
            if (decayEffect != Fx.none && b.timer(2, minInterval)) {
                decayEffect.at(b.x, b.y, b.rotation() + 180f);
            }
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
        /** ★ PU132 原版用 "laser"/"laser-end" 贴图绘制光束, 而非简单线段 */
        public TextureRegion region, endRegion;

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
        public void load() {
            super.load();
            // ★ 与 PU132 一致: 使用内置 laser / laser-end 贴图
            region = arc.Core.atlas.find("laser");
            endRegion = arc.Core.atlas.find("laser-end");
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
                // ★ PU132 原版: 贴图光束 (laser / laser-end), 比 Lines 线段更粗更还原
                if (region != null && region.found()) {
                    Drawf.laser(region, endRegion, b.x, b.y, Tmp.v1.x, Tmp.v1.y, beamWidth * b.fout());
                } else {
                    Lines.stroke(beamWidth * b.fout() * 3f);
                    Lines.line(b.x, b.y, Tmp.v1.x, Tmp.v1.y, false);
                }
                Drawf.light(b.x, b.y, Tmp.v1.x, Tmp.v1.y, lightWidth * b.fout(), color, 0.6f);
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
            // ★ 160 已移除 animatedshields 设置项, 改读 renderer.animateShields (与原版一致)
            if (mindustry.Vars.renderer.animateShields) {
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

    /** ===== VelocityLaserBoltBulletType (PU_V8 zBoson) =====
     * ★ PU132 原版用 "circle" 贴图拉伸成长条型光弹 (长度随速度增长)。
     * 用户要求: 长度改为原版长度的 2/3 (lenScale = 2/3)。
     */
    public static class VelocityLaserBoltBulletType extends BasicBulletType {
        /** 长度缩放 (1 = 原版长度, 用户要求 2/3) */
        public float lenScale = 2f / 3f;

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
        public void load() {
            super.load();
            // ★ 使用内置 "circle" 贴图 (PU132 同款) 拉伸为长条
            frontRegion = arc.Core.atlas.find("circle");
        }

        @Override
        public void draw(Bullet b) {
            float vel = b.vel().len() * 4f * lenScale;

            Draw.color(backColor);
            Draw.rect(frontRegion, b.x, b.y, width, height + vel, b.rotation() - 90f);

            Draw.color(frontColor);
            Draw.rect(frontRegion, b.x, b.y, width * 0.625f, height * 0.625f + (vel / 1.2f), b.rotation() - 90f);
            Draw.reset();
        }
    }

    /** ===== EphemeronPairBulletType (PU_V8 ephemeron) ===== */
    public static class EphemeronPairBulletType extends BasicBulletType {
        public boolean positive;

        public EphemeronPairBulletType(float damage) {
            super(0.001f, damage);
            // ★ 分裂放射小球存在时间: 与 PU132 原版一致 (360f = 6 秒, 对撞后消失, 实测约 2~3 秒)
            lifetime = 360f;
            hitEffect = Fx.hitLancer;
            despawnEffect = Fx.none;
            hitSize = 8f;
            drag = 0.015f;
            pierce = true;
            hittable = false;
            absorbable = false;
            reflectable = false;
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
                    // ★ PU132 原版: 阴阳粒子对撞时播放 lightHitLarge 命中特效
                    zzw.content.units.effects.HitEffect.lightHitLarge.at(Tmp.v1);
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
                float randomB = Mathf.random(0.2f, 1.4f);
                float angleRandom = Mathf.range(360f);
                float rangeRandom = Mathf.range(40f, 70f);
                Tmp.v2.trns(angleRandom, rangeRandom);
                Bullet pos = positive.create(b, Tmp.v1.x + Tmp.v2.x, Tmp.v1.y + Tmp.v2.y, angleRandom + randomSign);
                Tmp.v2.rotate(180f);
                Bullet neg = negative.create(b, Tmp.v1.x + Tmp.v2.x, Tmp.v1.y + Tmp.v2.y, angleRandom + randomSign + 180f);
                pos.data = neg;
                neg.data = pos;
                // ★ 关键: 给这对小球一个相反方向的小初速, 让它们先分开再被吸回对撞。
                //   若不加这一步, 正负两球生成在同一坐标会立刻判定重叠而双双移除,
                //   表现为"分裂小球一闪就没" (PU132 原版 ephemEronLaser 也做了这步)。
                Tmp.v2.trns(angleRandom + randomSign, randomB);
                pos.vel.add(Tmp.v2);
                neg.vel.add(Tmp.v2.rotate(180f));
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

            // ★ 黑洞全屏后处理：每帧把本子弹位置压入扭曲队列
            //   Trigger.draw 时 BlackHoleSFX.render() 会消费该队列并 blit 背景扭曲 shader
            //   强度必须跟着生命末期的收缩一起衰减，否则子弹消失瞬间扭曲会突然断掉
            if (zzw.TestMod.blackHoleSFX != null) {
                float life = b.fin();
                float sc;
                if (life < 0.2f) {
                    sc = life / 0.2f;
                } else if (life > 0.75f) {
                    sc = (1f - life) / 0.25f;
                } else {
                    sc = 1f;
                }
                sc = Mathf.clamp(sc, 0f, 1f);
                sc = (float) Math.sin(sc * Mathf.PI * 0.5f);

                zzw.TestMod.blackHoleSFX.blackHole(b.x, b.y, (1.5f + interp * 2.5f) * sc, 5.0f);
            }
        }

        @Override
    public void draw(Bullet b){
        float life = b.fin();

        // 生命周期：前 20% 长大，稳定，最后 25% 缩小到消失
        float scaleCurve;
        if(life < 0.2f){
            scaleCurve = life / 0.2f;
        } else if(life > 0.75f){
            scaleCurve = (1f - life) / 0.25f;
        } else {
            scaleCurve = 1f;
        }
        scaleCurve = Mathf.clamp(scaleCurve, 0f, 1f);
        scaleCurve = (float)Math.sin(scaleCurve * Mathf.PI * 0.5f);
        // 不再抬到 0.55 下限: 让黑洞末端真正收缩到 0, 与 shader 扭曲强度同步淡出

        if(scaleCurve < 0.02f) return;

        float fade  = scaleCurve;
        float pulse = 0.85f + 0.15f * Mathf.sin(Time.time * 6f);

        // 核心参数
        float coreR      = (8f + 5f * life) * scaleCurve;     // 事件视界半径 (Draw 侧, 黑洞本体由 shader 绘制)
        float diskInner  = coreR * 1.5f;                      // 吸积盘内缘
        float diskOuter  = coreR * 4.2f;                      // 吸积盘外缘
        float lensR1     = coreR * 2.2f;                      // 引力透镜弧 1
        float lensR2     = coreR * 3.4f;                      // 引力透镜弧 2

        // ============================================================
        // 0. 空间扭曲弧：一组同心弧，内快外慢，模拟空间被拧动
        // ============================================================
        Draw.blend(arc.graphics.Blending.additive);
        int twistCount = 4;
        for(int i = 0; i < twistCount; i++){
            float tNorm = i / (float)(twistCount - 1);       // 0 = 内, 1 = 外
            float arcR = Mathf.lerp(coreR * 1.15f, coreR * 3.0f, tNorm);

            // 内圈转得快，外圈转得慢
            float speed = Mathf.lerp(3.5f, 0.6f, tNorm);
            float angle = Time.time * speed * 60f + i * 23f;

            // 内圈亮，外圈淡
            float alpha = Mathf.lerp(0.85f, 0.15f, tNorm) * fade * pulse;

            // 内圈暖（金橙），外圈冷（蓝白）
            float rC = Mathf.lerp(1.0f, 0.5f, tNorm);
            float gC = Mathf.lerp(0.85f, 0.75f, tNorm);
            float bC = Mathf.lerp(0.4f, 1.0f, tNorm);
            Draw.color(rC, gC, bC, alpha);

            // 弧的粗细也是内粗外细
            float stroke = Mathf.lerp(2.2f, 0.8f, tNorm);
            Lines.stroke(stroke);

            // 每层画两段错开的弧，看起来像空间被拧过
            float arcSpan = Mathf.lerp(0.30f, 0.14f, tNorm);
            Lines.arc(b.x, b.y, arcR, arcSpan, angle);
            Lines.arc(b.x, b.y, arcR, arcSpan * 0.6f, angle + 180f);
        }
        Draw.blend();

        // ============================================================
        // 1. 引力透镜背景光晕（最底层，冷色，大范围渐隐）
        // ============================================================
        Draw.blend(arc.graphics.Blending.additive);

        for(int i = 0; i < 2; i++){
            float rr = diskOuter * (1.0f + i * 0.35f);
            float a  = (0.10f - i * 0.03f) * fade;
            Draw.color(Pal.lancerLaser, a);
            Fill.circle(b.x, b.y, rr);
        }

        Draw.color(Color.valueOf("7ab8ff"), 0.35f * fade * pulse);
        Lines.stroke(1.6f);
        Lines.arc(b.x, b.y, lensR1, 0.45f, 0f);
        Lines.arc(b.x, b.y, lensR1, 0.45f, 180f);
        Draw.color(Color.valueOf("a8d4ff"), 0.25f * fade);
        Lines.stroke(1.2f);
        Lines.arc(b.x, b.y, lensR2, 0.32f, 0f);
        Lines.arc(b.x, b.y, lensR2, 0.32f, 180f);

        // ============================================================
        // 2. 水平吸积盘（暖色，橙色为主，两侧各一条弧）
        // ============================================================
        Draw.color(Color.valueOf("ff7030"), 0.50f * fade * pulse);
        Lines.stroke(3.4f);
        Lines.arc(b.x, b.y, diskOuter * 0.80f, 0.26f, 0f);
        Lines.arc(b.x, b.y, diskOuter * 0.80f, 0.26f, 180f);

        Draw.color(Color.valueOf("ffe090"), 0.85f * fade * pulse);
        Lines.stroke(2.0f);
        Lines.arc(b.x, b.y, diskInner * 1.15f, 0.34f, 12f);
        Lines.arc(b.x, b.y, diskInner * 1.15f, 0.34f, 192f);

        // ============================================================
        // 5. 内向汇聚粒子
        // ============================================================
        int particles = 8;
        for(int i = 0; i < particles; i++){
            float seed = i * 1.37f;
            float t = (Time.time * 0.4f + seed) % 1f;
            float r = Mathf.lerp(diskOuter * 1.1f, coreR * 1.3f, t);

            float baseAng = seed * 137.5f;
            float driftAng = (1f - t) * 60f * (i % 2 == 0 ? 1f : -1f);
            float ang = baseAng + driftAng;

            float px = b.x + Mathf.cosDeg(ang) * r;
            float py = b.y + Mathf.sinDeg(ang) * r * 0.55f;

            float a = Mathf.sin(t * Mathf.PI) * 0.85f * fade;
            float size = Mathf.lerp(2.2f, 0.7f, t);
            Draw.color(Color.valueOf("FFE8A0"), a);
            Fill.circle(px, py, size);
        }

        // ============================================================
        // 6. 事件视界改由 blackholeshader.frag 绘制（不透明黑核 + 光子环）
        //    Draw 侧不再画黑圆, 避免之前半透明叠加的问题
        // ============================================================
        Draw.blend();
        Draw.color();
        Draw.reset();
    }
}

    /** GluonOrbData - 黑洞单位列表管理 (PU_V8 移植) */
    public static class GluonOrbData {
        public Seq<Unit> units = new Seq<>();
    }

    /** ===== GluonWhirlBulletType (PU132 gluon 能量球消散后的漩涡) =====
     * ★完整移植 PU132 GluonWhirlBulletType:
     *  - 小漩涡: 持续吸引半径内敌方单位 (force + scaledForce)
     *  - 每 2 tick 对范围内单位造成持续伤害
     *  - 渲染双层光球 + 随机 whirl 粒子
     */
    public static class GluonWhirlBulletType extends BasicBulletType {
        public float force = 8f, scaledForce = 7f, radius = 100f;

        public GluonWhirlBulletType(float damage) {
            super(0.001f, damage);
            pierce = pierceBuilding = true;
            despawnEffect = hitEffect = Fx.none;
        }

        @Override
        public void init(Bullet b) {
            super.init(b);
            b.data = new GluonOrbData();
        }

        @Override
        public void update(Bullet b) {
            super.update(b);

            if (!(b.data instanceof GluonOrbData)) return;
            GluonOrbData data = (GluonOrbData) b.data;

            if (Mathf.chance(Time.delta * 0.7f * b.fout())) {
                zzw.content.units.effects.ParticleFx.whirl.at(b);
            }

            if (b.timer(0, 2f)) {
                data.units.clear();
                Units.nearbyEnemies(b.team, b.x - radius, b.y - radius, radius * 2f, radius * 2f, u -> {
                    if (u != null && Mathf.within(b.x, b.y, u.x, u.y, radius)) {
                        data.units.add(u);
                    }
                });
                Damage.damage(b.team, b.x, b.y, hitSize, damage);
            }

            data.units.each(u -> {
                if (!u.dead) {
                    float f = force + (1f - u.dst(b) / radius) * scaledForce * Interp.pow2In.apply(b.fout()) * (u.isFlying() ? 1.5f : 1f);
                    Tmp.v1.trns(u.angleTo(b), f).scl(20f * Time.delta);
                    u.impulse(Tmp.v1);
                }
            });
        }

        @Override
        public void draw(Bullet b) {
            Draw.color(Pal.lancerLaser);
            Fill.circle(b.x, b.y, b.fout() * 7.5f);
            Draw.color(Color.white);
            Fill.circle(b.x, b.y, b.fout() * 5.5f);
            Draw.reset();
        }
    }

    /** gluon 能量球消散后生成的漩涡实例 (PU132 UnityBullets.gluonWhirl) */
    public static GluonWhirlBulletType gluonWhirl = new GluonWhirlBulletType(4f) {{
        lifetime = 5f * 60f;
        hitSize = 12f;
        // ★ 保留项目原有伤害强度: 漩涡持续伤害
        damage = 15f;
        splashDamage = 0f;
    }};
}
