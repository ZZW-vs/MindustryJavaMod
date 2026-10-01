package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
import zzw.content.exp.ExpTurret;

/**
 * PU132 DistFieldBulletType 移植版 (扭曲力场).
 *
 * <p>瞬间生成的圆形力场: 持续对半径内的敌方单位施加 {@code distStatus} 与
 * {@code distDamage}, 并减速半径内敌方子弹; 每 1/4 生命周期播放一次
 * {@link #distSplashFx} 扩散波纹。</p>
 *
 * <p>参考: PU132 {@code unity/entities/bullet/exp/DistFieldBulletType.java}。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class DistFieldBulletType extends BulletType {
    /** 力场中心颜色 (通常全透明) */
    public Color centerColor = Color.clear;
    /** 力场边缘颜色 */
    public Color edgeColor = Pal.place;
    /** 每 1/4 生命周期播放的扩散波纹, 数据为 Float[]{半径, 生命周期} */
    public Effect distSplashFx;
    /** 力场生成特效, 数据为 Float (半径) */
    public Effect distStart;
    /** 力场内单位受到的减速状态 */
    public StatusEffect distStatus;

    /** 基础半径 */
    public float radius = 24f;
    /** 每级增加的半径 */
    public float radiusInc = 0.8f;

    /** 可以被减速的敌方子弹的伤害上限 */
    public float damageLimit = 100f;
    /** 力场每帧对敌方单位造成的伤害 */
    public float distDamage = 0.1f;
    /** 基础子弹减速比例 */
    public float bulletSlow = 0.1f;
    /** 每级增加的子弹减速比例 */
    public float bulletSlowInc = 0.025f;

    public DistFieldBulletType(float speed, float damage){
        super(speed, damage);
    }

    /** 力场半径随炮台等级增长 */
    public float getRadius(Bullet b){
        return radius + radiusInc * getLevel(b) * b.damageMultiplier();
    }

    /** 敌方子弹的减速比例随炮台等级增长 */
    public float getBulletSlow(Bullet b){
        return bulletSlow + bulletSlowInc * getLevel(b) * b.damageMultiplier();
    }

    /** 取发射者的炮台等级; 非经验炮台则为 0 */
    public int getLevel(Bullet b){
        if(b.owner instanceof ExpTurret.ExpTurretBuild exp){
            return exp.level();
        }
        return 0;
    }

    @Override
    public void draw(Bullet b){
        float radius = getRadius(b);

        Draw.color(Pal.lancerLaser);
        Lines.stroke(1f);
        Lines.circle(b.x, b.y, Mathf.clamp((1f - b.fin()) * 20f) * radius);

        float centerf = centerColor.toFloatBits();
        float edgef = edgeColor.cpy().a(0.3f + 0.25f * Mathf.sin(b.time() * 0.05f)).toFloatBits();
        float sides = Mathf.ceil(Lines.circleVertices(radius) / 2f) * 2f;
        float space = 360f / sides;
        float dp = 5f;
        for(int i = 0; i < sides; i += 2){
            float px = Angles.trnsx(space * i, Mathf.clamp((1f - b.fin()) * dp) * radius);
            float py = Angles.trnsy(space * i, Mathf.clamp((1f - b.fin()) * dp) * radius);
            float px2 = Angles.trnsx(space * (i + 1), Mathf.clamp((1f - b.fin()) * dp) * radius);
            float py2 = Angles.trnsy(space * (i + 1), Mathf.clamp((1f - b.fin()) * dp) * radius);
            float px3 = Angles.trnsx(space * (i + 2), Mathf.clamp((1f - b.fin()) * dp) * radius);
            float py3 = Angles.trnsy(space * (i + 2), Mathf.clamp((1f - b.fin()) * dp) * radius);
            Fill.quad(b.x, b.y, centerf, b.x + px, b.y + py, edgef, b.x + px2, b.y + py2, edgef, b.x + px3, b.y + py3, edgef);
        }

        Draw.color();
    }

    @Override
    public void hit(Bullet b, float x, float y){
        // 力场不参与碰撞, 不做任何事
    }

    @Override
    public void despawned(Bullet b){
        // 力场消失时不做任何事
    }

    @Override
    public void update(Bullet b){
        float temp = b.lifetime / 4f;
        float radius = getRadius(b);

        if(b.time() % temp <= 1f && b.lifetime() - b.time() > 100f && distSplashFx != null){
            distSplashFx.at(b.x, b.y, 0f, new Float[]{radius, temp});
        }

        // 力场内敌方单位: 施加状态与持续伤害
        Units.nearbyEnemies(b.team, b.x, b.y, radius, e -> {
            if(distStatus != null){
                e.apply(distStatus, 2f);
            }
            e.damage(distDamage);
        });

        // 力场内敌方子弹: 减速
        Groups.bullet.intersect(b.x - radius, b.y - radius, radius * 2f, radius * 2f, e -> {
            if(e.team != b.team && e.damage() <= damageLimit){
                e.vel.scl(getBulletSlow(b));
            }
        });
    }

    @Override
    public void init(Bullet b){
        if(b == null) return;
        float radius = getRadius(b);

        if(distStart != null){
            distStart.at(b.x, b.y, 0f, radius);
        }
    }
}
