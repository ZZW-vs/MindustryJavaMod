package zzw.content.exp;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;

/**
 * 经验子弹类型 (PU_V8 ExpBulletType 移植版)。
 * <p>
 * 参考: PU_V8 main/src/unity/entities/bullet/exp/ExpBulletType.java
 * <p>
 * 与原生 {@link BulletType} 的差别:
 * <ul>
 *   <li>{@link #expOnHit} 为 true 时, 子弹命中会给发射它的经验炮台增加经验;</li>
 *   <li>{@link #drawTrail} / {@link #drawLight} 会用随等级渐变的颜色绘制, 让子弹颜色跟着炮台等级变化。</li>
 * </ul>
 * <p>
 * 说明: Mindustry v154+ 的 {@link BulletType} 已不再绘制弹体精灵,
 * 子弹的可视部分只有尾迹 (trail)、部件 (parts) 和光源 (light),
 * 因此本类只覆写这三处中的尾迹与光源, 与 PU 原版行为一致。
 */
public class ExpBulletType extends BulletType {
    /** 等级 0 时的颜色 (尾迹/光源) */
    public Color fromColor = Pal.lancerLaser;
    /** 满级时的颜色 (尾迹/光源) */
    public Color toColor = UnityPal.expLaser;

    /** 命中时给炮台增加的经验值 */
    public int expGain = 1;
    /** 是否在命中时给发射者加经验 */
    public boolean expOnHit = false;
    /** 触发加经验的概率 (0~1) */
    public float expChance = 1f;

    /** 尾迹是否使用随等级渐变的颜色 */
    public boolean overrideTrail = true;
    /** 光源是否使用随等级渐变的颜色 */
    public boolean overrideLight = true;

    public ExpBulletType(float speed, float damage){
        super(speed, damage);
    }

    /**
     * 命中处理: 先结算经验, 再走原生命中逻辑。
     */
    @Override
    public void hit(Bullet b, float x, float y){
        if(expOnHit) handleExp(b, x, y, expGain);

        super.hit(b, x, y);
    }

    @Override
    public void drawTrail(Bullet b){
        if(trailLength > 0 && b.trail != null){
            float z = Draw.z();
            Draw.z(z - 0.0001f);
            b.trail.draw(overrideTrail ? getColor(b).mul(trailColor) : trailColor, trailWidth);
            Draw.z(z);
        }
    }

    @Override
    public void drawLight(Bullet b){
        if(lightOpacity <= 0f || lightRadius <= 0f) return;
        Drawf.light(b, lightRadius, overrideLight ? getColor(b).mul(lightColor) : lightColor, lightOpacity);
    }

    /**
     * 按概率给子弹的发射者 (经验炮台) 增加经验。
     * <p>
     * 只有发射者是 {@link ExpTurret.ExpTurretBuild} 时才会生效,
     * 即普通炮台/单位打出的同类型子弹不会获得经验。
     */
    public void handleExp(Bullet b, float x, float y, int amount){
        if(!Mathf.chance(expChance)) return;
        if(b.owner instanceof ExpTurret.ExpTurretBuild exp){
            exp.handleExp(amount);
        }
    }

    /** @return 发射者的经验等级, 非经验炮台时返回 0 */
    public int getLevel(Bullet b){
        if(b.owner instanceof ExpTurret.ExpTurretBuild exp){
            return exp.level();
        }
        return 0;
    }

    /** @return 发射者的等级百分比 (0~1), 非经验炮台时返回 0 */
    public float getLevelf(Bullet b){
        if(b.owner instanceof ExpTurret.ExpTurretBuild exp){
            return exp.levelf();
        }
        return 0f;
    }

    /** @return Tmp.c2, 按等级在 {@link #fromColor} 与 {@link #toColor} 之间插值 (不可长期持有) */
    public Color getColor(Bullet b){
        return Tmp.c2.set(fromColor).lerp(toColor, getLevelf(b));
    }
}
