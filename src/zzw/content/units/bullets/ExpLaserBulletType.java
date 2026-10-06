package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.entities.Damage;
import mindustry.entities.Lightning;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.graphics.Drawf;
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
    /** PU_V8 原版三层描线的各层线宽系数 (实际线宽 = width × strokes[i]) */
    public float[] strokes = {2.9f, 1.8f, 1f};
    /** PU_V8 blip: 命中点处绘制扩散光圈 (仅 puLaser 模式下生效) */
    public boolean blip = false;
    /**
     * 是否使用 PU_V8 原版渲染与命中行为 {@link ExpLaserBulletType}
     * (细三层描线 + 命中点扩散光圈; 激光束止于首个命中目标, 而不是永远画到最大长度).
     */
    public boolean puLaser = false;

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

    /**
     * 命中回调, 默认空实现, 供子类覆写.
     * <p>当激光射线沿途命中单位或建筑时, 由 {@link #init(Bullet)} 在命中点调用,
     * 典型用途是 PU132 frostLaser 的冻结圈 (freezePos)。</p>
     *
     * @param b 当前激光子弹
     * @param x 命中点 x 坐标
     * @param y 命中点 y 坐标
     */
    public void onHit(Bullet b, float x, float y){
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

        // 命中检测: 沿射线找到第一个可命中目标后触发 onHit (不施加额外伤害, 仅作为回调)
        Healthc target = Damage.linecast(b, b.x, b.y, rot, getLength(b));
        if(target != null){
            // ★ PU_V8 还原: 激光束止于首个命中目标 (而不是永远画到最大长度)
            if(puLaser){
                float dist = Mathf.dst(b.x, b.y, target.getX(), target.getY());
                if(dist > 0f) b.fdata = Math.min(resultLength, dist);
            }
            onHit(b, target.getX(), target.getY());
        }

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

    /**
     * PU_V8 原版渲染: 细三层描线激光 + 命中点扩散光圈 (blip)。
     *
     * <p>非 {@link #puLaser} 模式退回 v158 原生 {@link LaserBulletType} 渲染, 不影响其他经验激光炮台。</p>
     */
    @Override
    public void draw(Bullet b){
        if(!puLaser){
            super.draw(b);
            return;
        }

        float len = Math.max(b.fdata, 0f);
        Tmp.v1.trns(b.rotation(), len).add(b.x, b.y);

        Draw.color(getColor(b));
        Draw.alpha(0.4f);
        Lines.stroke(b.fout() * width * strokes[0]);
        Lines.line(b.x, b.y, Tmp.v1.x, Tmp.v1.y);

        Draw.alpha(1f);
        Lines.stroke(b.fout() * width * strokes[1]);
        Lines.line(b.x, b.y, Tmp.v1.x, Tmp.v1.y);

        Draw.color(Color.white);
        Lines.stroke(b.fout() * width * strokes[2]);
        Lines.line(b.x, b.y, Tmp.v1.x, Tmp.v1.y);

        if(blip){
            Draw.color(Color.white, getColor(b), b.fin());
            Lines.circle(Tmp.v1.x, Tmp.v1.y, b.fin() * width * 5f);
        }
        Draw.reset();

        Drawf.light(b.x, b.y, Tmp.v1.x, Tmp.v1.y, width * 10f * b.fout(), Color.white, 0.6f);
    }
}
