package zzw.content.units.bullets;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Damage;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;
import zzw.content.exp.UnityFx;

/**
 * PU132 ExpLaserFieldBulletType 移植版 (裂缝激光 + 力场).
 *
 * <p>激光命中后不直接结束: 在命中点生成一个 {@link #distField} 力场,
 * 并沿炮台到命中点播放 {@link UnityFx#chainLightning}; 随后按 {@link #fields}
 * 的数量, 在命中点周围随机偏移处依次播放小闪电链并生成 {@link #smallDistField}。</p>
 *
 * <p>参考: PU132 {@code unity/entities/bullet/exp/ExpLaserFieldBulletType.java}。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class ExpLaserFieldBulletType extends ExpLaserBulletType {
    /** 命中时生成的主力场 */
    public BulletType distField;
    /** 主命中点周围随机生成的小力场 */
    public BulletType smallDistField;
    /** 基础小力场数量 */
    public int fields = 2;
    /** 每级增加的小力场数量 */
    public float fieldInc = 0.15f;

    public ExpLaserFieldBulletType(float length, float damage){
        super(length, damage);
    }

    /** 小力场数量随炮台等级增长 */
    public int getFields(Bullet b){
        return fields + Mathf.floor(fieldInc * getLevel(b) * b.damageMultiplier());
    }

    @Override
    public void init(Bullet b){
        super.init(b);

        // 沿激光方向寻找第一个可命中目标, 命中点即力场生成位置
        Healthc target = Damage.linecast(b, b.x, b.y, b.rotation(), getLength(b));

        float vx = b.x + Angles.trnsx(b.rotation(), getLength(b));
        float vy = b.y + Angles.trnsy(b.rotation(), getLength(b));

        if(target instanceof Unit unit){
            vx = unit.x;
            vy = unit.y;
            unit.collision(b, vx, vy);
            b.collision(unit, vx, vy);
        }else if(target instanceof Building tile && tile.collide(b)){
            vx = tile.x;
            vy = tile.y;
            tile.collision(b);
            hit(b, vx, vy);
        }

        // 命中单位/建筑 → 大范围力场; 落空 → 小力场
        if(target != null){
            distField.create(b.owner, b.team, vx, vy, 0f);
        }else{
            smallDistField.create(b.owner, b.team, vx, vy, 0f);
        }

        UnityFx.chainLightning.at(b.x, b.y, 0f, getColor(b), new Vec2(vx, vy));

        final float fx = vx, fy = vy;
        for(int i = 0; i < getFields(b); i++){
            Time.run(0.1f * 60f * i + 1f + UnityFx.smallChainLightning.lifetime * 0.5f, () -> {
                float tx = fx + Mathf.range(8f) * Vars.tilesize;
                float ty = fy + Mathf.range(8f) * Vars.tilesize;
                UnityFx.smallChainLightning.at(fx, fy, 0f, getColor(b), new Vec2(tx, ty));
                smallDistField.create(b.owner, b.team, tx, ty, 0f);
            });
        }
    }
}
