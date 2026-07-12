package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.gen.Bullet;

/**
 * 虚空弹丸 (移植自 PU132 VoidPelletBulletType)
 * - 黑色方块子弹, 软跟踪目标
 * - lifetime=90f
 * - homingPower=0.01f
 */
public class VoidPelletBulletType extends AntiCheatBulletTypeBase {
    public VoidPelletBulletType(float speed, float damage) {
        super(speed, damage);
        lifetime = 90f;
        trailColor = Color.black;
        trailLength = 16;
        trailWidth = 2f;
        despawnEffect = Fx.hitLancer;
        hitEffect = Fx.hitLancer;
        homingPower = 0.01f;
        homingRange = 50f;
        homingDelay = 20f;
        hitSize = 3f;
        keepVelocity = false;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        // 记录初始角度, 用于平滑转向 (PU132: b.fdata = b.rotation())
        b.fdata(b.rotation());
        b.rotation(b.rotation() + Mathf.range(120f));
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        // PU132 原版平滑转向: 计算当前角度与初始角度的带符号差, 旋转 vel 回到初始方向
        // ang = angleToSigned(b.rotation, b.fdata) = b.rotation - b.fdata (signed)
        // rotate(-ang * factor) → 向 fdata 方向旋转
        if (b.fdata() != -361f) {
            float ang = angleDistSigned(b.rotation(), b.fdata());
            b.vel().rotate(-ang * Mathf.clamp(0.2f * arc.util.Time.delta));
            if (Math.abs(ang) <= 0.06f) {
                b.fdata(-361f);
            }
        }
    }

    /** 带符号角度差 (PU132 Utils.angleToSigned): 返回 a 相对 b 的角度差, 范围 -180~180 */
    private static float angleDistSigned(float a, float b) {
        a += 360f;
        a %= 360f;
        b += 360f;
        b %= 360f;
        float d = Math.abs(a - b) % 360f;
        int sign = (a - b >= 0f && a - b <= 180f) || (a - b <= -180f && a - b >= -360f) ? 1 : -1;
        return (d > 180f ? 360f - d : d) * sign;
    }

    @Override
    public void draw(Bullet b) {
        drawTrail(b);
        Draw.color(Color.black);
        Fill.square(b.x(), b.y(), 2f, b.rotation() + 45f);
    }

    @Override
    public void drawLight(Bullet b) {
    }
}
