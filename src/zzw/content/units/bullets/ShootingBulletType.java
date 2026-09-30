package zzw.content.units.bullets;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.entities.Sized;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Sounds;
import zzw.content.units.effects.SpecialFx;

/**
 * 巡航射手弹 (PU132 {@code unity.entities.bullet.misc.ShootingBulletType} 移植)。
 *
 * <p>这是一种 "会开火的子弹": 自身不直接命中目标, 而是缓慢飞向最近敌人,
 * 在目标周围 {@link #minTargetRange} 处悬停绕飞, 并每隔 {@link #reloadTime}
 * 帧朝目标发射一发 {@link #shootBullet} (toxoswarmer 用火焰弹)。</p>
 *
 * <p>飞行逻辑:</p>
 * <ol>
 *   <li>每 5 帧用 {@link Units#closestTarget} 刷新最近目标 (存入 {@code b.data});</li>
 *   <li>目标失效 (死亡 / 超出 {@code targetRange * 1.1}) 时清空目标;</li>
 *   <li>有目标时: 朝向角 {@code b.fdata} 以 3°/帧 靠拢目标方向, 同时每帧
 *       在目标外侧 {@code minTargetRange + hitSize/2} 处取一个点, 让速度
 *       以 {@code 1/smoothness} 的比例平滑靠拢 —— 形成 "贴近但不撞上" 的绕飞;</li>
 *   <li>朝向与目标方向相差 2° 以内, 且距离在 {@link #shootBullet} 射程内时,
 *       按 {@link #reloadTime} 周期发射子子弹;</li>
 *   <li>无目标时弹体缓慢回正 (角度 slerp 到速度方向)。</li>
 * </ol>
 *
 * <p>★ v132 → v160 适配要点:</p>
 * <ul>
 *   <li>贴图: 原版 {@code load()} 用 {@code Core.atlas.find(name)}, 本项目继续沿用
 *       (名字带 {@code create-} 前缀, 与其它贴图引用方式一致);</li>
 *   <li>特效: {@code ShootFx.plagueShootSmokeLarge} / {@code HitFx.plagueLargeHit}
 *       在本项目收录于 {@link SpecialFx};</li>
 *   <li>其余 API (Units.closestTarget / Angles.moveToward / Tmp / Mathf) 在 v160 无变化。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class ShootingBulletType extends BulletType{
    /** 索敌半径 (超出该距离的敌人不再被选为目标)。 */
    public float targetRange = 220f;
    /** 绕飞悬停距离 (弹体与目标保持的最小距离)。 */
    public float minTargetRange = 90f;
    /** 速度平滑系数 (越大越"迟钝", 越小越贴近目标)。 */
    public float smoothness = 35f;
    /** 子子弹发射间隔 (帧, 即 bullet.timer 单位)。 */
    public float reloadTime = 20f, shootInaccuracy = 0f;
    /** 周期发射的子子弹 (toxoswarmer 用火焰弹)。
     * <p>★ v160 适配: 原版默认值 {@code Bullets.standardCopper} 在 v160 已随
     * 预设子弹内联改动被移除, 这里改用等效的铜弹参数 (speed 2.5 / damage 9)。</p> */
    public BulletType shootBullet = new BasicBulletType(2.5f, 9f);
    /** 发射子子弹时播放的音效。 */
    public Sound shootSound = Sounds.none;
    /** 弹体贴图名 (由构造传入)。 */
    public String name;
    protected TextureRegion region;

    public ShootingBulletType(String name, float speed, float damage){
        super(speed, damage);
        this.name = name;
        // 可被攻击 (会被敌方子弹/单位摧毁), 但不可被吸收 (不参与护盾拦截)
        hittable = true;
        absorbable = false;
        // 不撞击任何东西: 靠绕飞 + 子子弹输出
        collides = collidesTiles = false;
        drag = 0.05f;
        trailLength = 4;
        trailChance = 0.2f;

        shootEffect = SpecialFx.plagueShootSmokeLarge;
        hitEffect = SpecialFx.plagueLargeHit;

        splashDamage = 30f;
        splashDamageRadius = 40f;
    }

    @Override
    public void load(){
        region = Core.atlas.find(name);
    }

    @Override
    public float estimateDPS(){
        // 主要输出为溅射 + 子子弹 (子子弹按一半命中率折算)
        float sum = splashDamage * 0.75f;
        if(fragBullet != null && fragBullet != this){
            sum += fragBullet.estimateDPS() * fragBullets / 2f;
        }
        return sum;
    }

    @Override
    public void init(Bullet b){
        super.init(b);
        // fdata 保存 "当前朝向角" (与速度方向解耦, 用于绕飞时保持对准目标)
        b.fdata = b.rotation();
    }

    @Override
    public void update(Bullet b){
        super.update(b);

        // 每 5 帧刷新一次最近目标
        if(b.timer(1, 5f)){
            b.data = Units.closestTarget(b.team, b.x, b.y, targetRange);
        }

        // 目标失效 (死亡或跑出索敌范围) 则清空
        if(b.data instanceof Healthc && Units.invalidateTarget((Healthc)b.data, b.team, b.x, b.y, targetRange * 1.1f)){
            b.data = null;
        }

        if(b.data instanceof Sized){
            Sized t = (Sized)b.data;
            float angTo = b.angleTo(t);
            // 左右绕飞的随机侧向偏移, 每 90 帧 (或每次换弹周期) 变化一次
            int side = Mathf.randomSeed(b.id * 913L + (int)(b.time / 90f), 0, 1) == 0 ? -1 : 1;
            // 朝向以 3°/帧 靠拢目标
            b.fdata = Angles.moveToward(b.fdata, angTo, 3f);

            // 目标外侧的悬停点: 目标背后 180° + 侧偏, 距离 = minTargetRange + 目标半径/2
            Tmp.v2.trns(angTo + 180f + (side * speed / 2f), minTargetRange + (t.hitSize() / 2f)).add(t);
            Tmp.v1.set(Tmp.v2).sub(b).limit(speed).scl(1f / smoothness);
            b.vel.add(Tmp.v1).limit(speed);

            // 对准目标且进入子子弹射程时, 周期发射
            if(Angles.within(b.fdata, angTo, 2f) && b.within(t, shootBullet.range) && b.timer(2, reloadTime)){
                shootBullet.shootEffect.at(b.x, b.y, b.fdata);
                shootSound.at(b);
                shootBullet.create(b, b.team, b.x, b.y, b.fdata + Mathf.range(shootInaccuracy));
            }
        }else{
            // 无目标: 朝向缓慢回正到速度方向
            b.fdata = Mathf.slerpDelta(b.fdata, b.rotation(), 0.1f);
        }
    }

    @Override
    public void draw(Bullet b){
        super.draw(b);
        // 弹体贴图默认朝右, 而 fdata 是 "目标朝向", 故减 90° 校正
        Draw.rect(region, b.x, b.y, b.fdata - 90f);
    }
}