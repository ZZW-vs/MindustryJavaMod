package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.gen.Bullet;

/**
 * 虚空弹丸 (完全复刻 PU132 VoidPelletBulletType)
 *
 * PU132 原版逻辑:
 * - 黑色方块子弹, 软跟踪目标
 * - init 时: 记录初始角度到 fdata, 然后 rotation 随机偏转±120°
 * - update 时: 用 angleDistSigned(rotation, fdata) 计算带符号角度差, 平滑转向回初始角度
 * - 当角度差<=0.06° 时停止转向 (fdata=-361f 标记完成)
 *
 * ★ 之前 bug: 用 Tmp.v1.trns + angleTo 计算角度差是错的 (算的是两点向量角度, 不是角度差)
 *   正确做法: 直接 angleDistSigned(rotation, fdata)
 */
public class VoidPelletBulletType extends AntiCheatBulletTypeBase {
    public VoidPelletBulletType(float speed, float damage) {
        super(speed, damage);
        lifetime = 90f;
        trailColor = Color.black;
        trailLength = 16;
        trailWidth = 3f;
        despawnEffect = Fx.hitLancer;
        hitEffect = Fx.hitLancer;
        homingPower = 0.01f;
        homingRange = 50f;
        homingDelay = 20f;
        hitSize = 5f;
        keepVelocity = false;
        // ★ PU132 原版没有 drag, 移除之前误加的 drag=0.05f
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        // 记录初始角度, 然后随机偏转±120° (PU132 L27-30)
        b.fdata(b.rotation());
        b.rotation(b.rotation() + Mathf.range(120f));
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        // 平滑转向回初始角度 (PU132 L34-42)
        // fdata=-361f 表示转向完成, 不再处理
        if (b.fdata() != -361f) {
            float ang = angleDistSigned(b.rotation(), b.fdata());
            b.vel().rotate(-ang * Mathf.clamp(0.2f * Time.delta));
            if (Math.abs(ang) <= 0.06f) {
                b.fdata(-361f);
            }
        }
    }

    @Override
    public void draw(Bullet b) {
        drawTrail(b);
        // ★ 白色描边 (让黑色子弹在任何背景上可见, PU132 原版纯黑在暗色背景上看不见)
        Draw.color(Color.white);
        Fill.square(b.x(), b.y(), 4f, b.rotation() + 45f);
        // 黑色核心
        Draw.color(Color.black);
        Fill.square(b.x(), b.y(), 3f, b.rotation() + 45f);
    }

    @Override
    public void drawLight(Bullet b) {
    }

    /**
     * 带符号角度差: 从 b 到 a 需要旋转的角度, 归一化到 [-180, 180]
     * 正值=a 在 b 逆时针方向, 负值=a 在 b 顺时针方向
     * (复刻 PU132 Utils.angleDistSigned)
     */
    private static float angleDistSigned(float a, float b) {
        float d = (a - b) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }
}
