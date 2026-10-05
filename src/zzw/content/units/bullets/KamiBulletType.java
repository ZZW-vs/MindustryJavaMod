package zzw.content.units.bullets;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import zzw.content.units.kami.KamiBulletData;

/**
 * kami 弹幕子弹类型 (PU132 {@code KamiBulletType} 的移植版)。
 *
 * <p>特征: 色相循环的红色弹幕、外层脉动光晕 + 内层白色核心、可选拖尾
 * (kamiBullet2 有 12 段拖尾)、支持逐帧转向 (turn) 与特殊行为 (bdata)。</p>
 *
 * <p>v160 不使用 PU132 的注解实体系统, 弹体的 width/length/turn/fdata/fdata2/bdata
 * 等字段统一存放在 {@link KamiBulletData} 中 (挂在 {@code b.data}), 由本类读写。</p>
 *
 * <p>参考: PU132 {@code unity/entities/bullet/kami/KamiBulletType.java} +
 * {@code unity/entities/comp/KamiBulletComp.java}。</p>
 */
public class KamiBulletType extends BulletType {
    /** 出场延迟 (tick): >0 时 kamiBulletSpawn 特效按该值播放悬停出场期, <=0 表示立即出场。 */
    public float delay = -1f;

    public KamiBulletType(){
        speed = 1f;
        damage = 9f;
        absorbable = false;
        hittable = false;
        collidesTiles = false;
        pierce = true;
        keepVelocity = false;
        hitSize = 6f;
        lifetime = 240f;
        // ★ collidesTeam=true: 子弹可以打自己方单位 (含 kami 自己)
        collidesTeam = true;
        despawnEffect = Fx.none;  // ★ 不能为 null, 否则 Bullet.remove 时 NPE
        hitEffect = Fx.none;
    }

    /** 取子弹上的 kami 弹幕数据 */
    public static KamiBulletData data(Bullet b){
        return (KamiBulletData) b.data;
    }

    @Override
    public void init(Bullet b){
        super.init(b);
        b.data = new KamiBulletData();
    }

    @Override
    public void update(Bullet b){
        super.update(b);
        KamiBulletData d = data(b);
        if(d == null) return;
        // ★ 特殊行为 (对应 PU132 KamiBulletData.update)
        if(d.behavior != null) d.behavior.update(b, d);
        // ★ 逐帧转向 (对应 PU132 KamiBulletComp: rotation += turn * delta)
        if(d.turn != 0f){
            b.rotation(b.rotation() + d.turn * Time.delta);
        }
    }

    @Override
    public void draw(Bullet b){
        KamiBulletData d = data(b);
        if(d == null) return;
        float time = (b.time * 2f) + (Time.time / 2f);
        float st = Mathf.clamp(Math.max(d.width, d.length) / 10f + 1.2f, 1.5f, 4f) * (1f + Mathf.absin(time, 10f, 0.33f));

        Tmp.c1.set(Color.red).shiftHue(time);
        drawTrail(b);
        // 外层彩色光晕
        Draw.color(Tmp.c1);
        Draw.rect("circle", b.x, b.y, (d.width * 2f) + st, (d.length * 2f) + st, b.rotation());
        // 内层白色核心
        Draw.color(Color.white);
        Draw.rect("circle", b.x, b.y, d.width * 2f, d.length * 2f, b.rotation());
        Draw.color();
    }

    @Override
    public void drawTrail(Bullet b){
        // PU132: 用子弹自身宽度绘制加法混合拖尾
        if(trailLength > 0 && b.trail != null){
            KamiBulletData d = data(b);
            float z = Draw.z();
            Draw.z(z - 0.0001f);
            Draw.blend(Blending.additive);
            b.trail.draw(Tmp.c1, d == null ? 6f : d.width);
            Draw.blend();
            Draw.z(z);
        }
    }
}
