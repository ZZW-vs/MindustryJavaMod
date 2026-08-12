package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.entities.Damage;
import mindustry.entities.Lightning;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Pal;
import zzw.content.exp.ExpTurret;
import zzw.content.exp.UnityPal;

/**
 * PU_V8 ExpLaserBulletType 移植版 (经验激光子弹)
 * 参考: PU_V8 main/src/unity/entities/bullet/exp/ExpLaserBulletType.java
 *
 * 功能:
 * - 激光长度随炮台等级增长 (length + lengthInc * level)
 * - 伤害随炮台等级增长 (damage + damageInc * level)
 * - 颜色随炮台等级变化 (fromColor → toColor)
 *
 * 与 PU_V8 区别:
 * - 继承 v158 原生 LaserBulletType (而非 PU_V8 完全自定义渲染)
 * - 仅覆写 init(Bullet) 使用动态长度 + 应用伤害增量
 * - draw() 仍使用 LaserBulletType 原生渲染 (基于 b.fdata 长度)
 */
public class ExpLaserBulletType extends LaserBulletType {
    /** Length increase per owner level */
    public float lengthInc = 0f;
    /** Damage increase per owner level */
    public float damageInc = 0f;
    /** Color at level 0 */
    public Color fromColor = Pal.lancerLaser;
    /** Color at max level */
    public Color toColor = UnityPal.expLaser;

    public ExpLaserBulletType(float length, float damage){
        super(damage);
        this.length = length;
        this.drawSize = length * 2f;
    }

    public ExpLaserBulletType(){
        this(160f, 1f);
    }

    /** Get owner turret's level */
    public int getLevel(Bullet b){
        if(b.owner instanceof ExpTurret.ExpTurretBuild exp){
            return exp.level();
        }
        return 0;
    }

    /** Get owner turret's level fraction (0..1) */
    public float getLevelf(Bullet b){
        if(b.owner instanceof ExpTurret.ExpTurretBuild exp){
            return exp.levelf();
        }
        return 0f;
    }

    /** Dynamic length based on owner's level */
    public float getLength(Bullet b){
        return length + lengthInc * getLevel(b);
    }

    /** Color based on owner's level (returns Tmp.c2, do not store) */
    public Color getColor(Bullet b){
        return Tmp.c2.set(fromColor).lerp(toColor, getLevelf(b));
    }

    @Override
    public void init(Bullet b){
        // Apply damage increase based on level (PU_V8 setDamage)
        if(damageInc != 0f){
            b.damage += damageInc * getLevel(b) * b.damageMultiplier();
        }

        // Use dynamic length for collision (PU_V8 getLength)
        float resultLength = Damage.collideLaser(b, getLength(b), largeHit, laserAbsorb, pierceCap);
        float rot = b.rotation();

        laserEffect.at(b.x, b.y, rot, resultLength * 0.75f);

        if(lightningSpacing > 0){
            int idx = 0;
            for(float i = 0; i <= resultLength; i += lightningSpacing){
                float cx = b.x + Angles.trnsx(rot, i),
                    cy = b.y + Angles.trnsy(rot, i);

                int f = idx++;

                for(int s : Mathf.signs){
                    Time.run(f * lightningDelay, () -> {
                        if(b.isAdded() && b.type == this){
                            Lightning.create(b, lightningColor,
                                lightningDamage < 0 ? damage : lightningDamage,
                                cx, cy, rot + 90 * s + Mathf.range(lightningAngleRand),
                                lightningLength + Mathf.random(lightningLengthRand));
                        }
                    });
                }
            }
        }
    }
}
