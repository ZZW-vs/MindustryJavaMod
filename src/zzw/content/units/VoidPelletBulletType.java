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
        drag = 0.05f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        // 记录初始角度, 用于平滑转向
        b.fdata(b.rotation());
        b.rotation(b.rotation() + Mathf.range(120f));
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        // 平滑转向到目标方向 (用 vel.angle() vs 初始角度差)
        if (b.fdata() != -361f) {
            Tmp.v1.trns(b.rotation(), 1f);
            Tmp.v2.trns(b.fdata(), 1f);
            float targetAngle = Tmp.v1.angleTo(Tmp.v2);
            b.vel().rotate(-targetAngle * Mathf.clamp(0.2f * arc.util.Time.delta));
            if (Math.abs(targetAngle) <= 0.06f) {
                b.fdata(-361f);
            }
        }
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
