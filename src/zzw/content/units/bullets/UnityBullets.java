package zzw.content.units.bullets;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.PointBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Sounds;
import zzw.content.units.effects.MonolithFx;

/**
 * Monolith 系列单位专用子弹 (PU132 unity.content.UnityBullets 节选移植)。
 *
 * <p>只移植 MonolithUnitTypes 直接引用的四种子弹:</p>
 * <ul>
 *   <li>{@link #pylonLightning} — pylon 主激光的伴随闪电;</li>
 *   <li>{@link #pylonLaser} — pylon 主激光 (2000 伤害, 生成时 24 波闪电);</li>
 *   <li>{@link #pylonLaserSmall} — pylon 副激光 (192 伤害);</li>
 *   <li>{@link #monumentRailBullet} — monument 电磁炮弹 (6000 伤害)。</li>
 * </ul>
 *
 * <p>★ v155.4 适配: Sounds.spark → Sounds.shootArc。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class UnityBullets{
    /** pylon 主激光的伴随闪电。 */
    public static LightningBulletType pylonLightning;

    /** pylon 主激光 (init 时 24 波闪电)。 */
    public static LaserBulletType pylonLaser;

    /** pylon 副激光。 */
    public static LaserBulletType pylonLaserSmall;

    /** monument 电磁炮弹 (点到点瞬发型)。 */
    public static PointBulletType monumentRailBullet;

    public static void load(){
        pylonLightning = new LightningBulletType(){{
            lightningLength = 32;
            lightningLengthRand = 12;
            damage = 56f;
        }};

        pylonLaser = new LaserBulletType(2000f){
            {
                length = 520f;
                width = 60f;
                lifetime = 72f;
                largeHit = true;
                sideLength = sideWidth = 0f;
                shootEffect = MonolithFx.pylonLaserCharge;
            }

            /**
             * 生成时在激光原点连续放出 24 波闪电 (每 2 tick 一波),
             * 视觉上像激光 "辐射" 出链电。
             */
            @Override
            public void init(Bullet b){
                super.init(b);

                for(int i = 0; i < 24; i++){
                    Time.run(2f * i, () -> {
                        pylonLightning.create(b, b.x, b.y, b.vel().angle());
                        // ★ v155.4: Sounds.spark → Sounds.shootArc
                        Sounds.shootArc.at(b.x, b.y, Mathf.random(0.6f, 0.9f));
                    });
                }
            }
        };

        pylonLaserSmall = new LaserBulletType(192f){{
            lifetime = 24f;
            length = 180f;
            width = 24f;
            sideAngle = 60f;
            shootEffect = MonolithFx.phantasmalLaserShoot;
        }};

        monumentRailBullet = new PointBulletType(){{
            damage = 6000f;
            buildingDamageMultiplier = 0.8f;
            speed = maxRange = 540f;
            lifetime = 1f;
            hitShake = 6f;
            trailSpacing = 35f;
            shootEffect = MonolithFx.monumentShoot;
            despawnEffect = MonolithFx.monumentDespawn;
            smokeEffect = Fx.blastExplosion;
            trailEffect = MonolithFx.monumentTrail;
        }};
    }

    private UnityBullets(){
        throw new AssertionError();
    }
}
