package zzw.content.units.bullets;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.FloatSeq;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;

/**
 * kami 弹幕子弹类型 (PU132 移植版)
 *
 * 特征:
 * - 色相循环的红色弹幕 (hue-shifting)
 * - 外层彩色光晕 + 内层白色核心
 * - 拖尾效果 (沿速度反方向渐小渐淡的圆)
 * - 支持转向 (turn), 改变子弹飞行角度
 * - pierce=true, 不被防御塔拦截
 *
 * 简化: 使用 b.data (float[]) 存储 width/length/turn, 不使用自定义 Entity
 *
 * 参考: PU132 unity/entities/bullet/kami/KamiBulletType.java
 */
public class KamiBulletType extends BulletType {
    public static float bulletWidth = 6f;
    public static float bulletLength = 6f;
    public static float turn = 0f;
    /** 是否显示拖尾 (kamiBullet2=true, kamiBullet3=false) */
    public boolean hasTrail = false;
    /**
     * 出场延迟 (tick): PU132 原版 KamiBulletType.delay。
     * <p>&gt;0 时子弹延迟加入世界, 期间由
     * {@code SpecialFx.kamiBulletSpawn} 播放出場演出;
     * -1 (默认) = 立即出场。</p>
     */
    public float delay = -1f;

    public KamiBulletType() {
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

    @Override
    public void init(Bullet b) {
        super.init(b);
        // 使用 data 存储 width, length, turn
        if (b.data == null) {
            b.data = new float[]{bulletWidth, bulletLength, turn};
        }
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        // 转向: 每帧旋转子弹角度 (PU132 KamiBulletComp: rotation += turn * delta)
        float[] data = (float[]) b.data;
        if (data != null && data.length > 2 && data[2] != 0f) {
            b.rotation(b.rotation() + data[2] * Time.delta);
        }
    }

    @Override
    public void draw(Bullet b) {
        float[] data = (float[]) b.data;
        if (data == null) return;
        float width = data[0];
        float length = data[1];

        // 色相循环
        float time = (b.time * 2f) + (Time.time / 2f);
        float st = Mathf.clamp(Math.max(width, length) / 10f + 1.2f, 1.5f, 4f) * (1f + Mathf.absin(time, 10f, 0.33f));

        Draw.blend(Blending.additive);

        // ★ 拖尾效果: 沿速度反方向画 3 个渐小渐淡的圆 (PU132 trailLength=12)
        if (hasTrail) {
            float trailLen = 12f;
            for (int i = 1; i <= 3; i++) {
                float t = i / 3f;
                float tx = b.x - Mathf.cosDeg(b.rotation()) * trailLen * t;
                float ty = b.y - Mathf.sinDeg(b.rotation()) * trailLen * t;
                Tmp.c1.set(Color.red).shiftHue(time - i * 5f).a(1f - t * 0.7f);
                Draw.color(Tmp.c1);
                Draw.rect("circle", tx, ty, (width * 2f) * (1f - t * 0.4f), (length * 2f) * (1f - t * 0.4f), b.rotation());
            }
        }

        // 外层彩色光晕
        Tmp.c1.set(Color.red).shiftHue(time);
        Draw.color(Tmp.c1);
        Draw.rect("circle", b.x, b.y, (width * 2f) + st, (length * 2f) + st, b.rotation());

        // 内层白色核心
        Draw.color(Color.white);
        Draw.rect("circle", b.x, b.y, width * 2f, length * 2f, b.rotation());
        Draw.blend();
        Draw.reset();
    }
}
