package zzw.content.units.bullets;

import arc.graphics.g2d.Draw;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;

/**
 * 迫击炮子弹 (移植自 PU_V8 unity.entities.bullet.physical.MortarBulletType)
 * - 抛物线视觉: 高度随飞行进度按 sin 曲线缩放
 * - 拖尾轨迹: sin 插值
 * - 简化 v158 适配: 继承 BasicBulletType
 */
public class MortarBulletType extends BasicBulletType {
    /** 高度缩放倍数 (抛物线峰值) */
    public float heightScl = 1.5f;

    public MortarBulletType(float speed, float damage) {
        super(speed, damage, "shell");
        collides = false;
        collidesTiles = false;
        shrinkX = 0f;
        shrinkY = 0f;
        trailLength = 15;
        trailInterp = a -> Mathf.sin(a * Mathf.PI);
    }

    @Override
    public void draw(Bullet b) {
        drawTrail(b);
        float f = Mathf.lerp(Math.max(b.fdata, 0f), 1f, 0.125f);
        float scl = 1f + (heightScl * Interp.sineOut.apply(b.fslope()) * f);
        float height = this.height * ((1f - shrinkY) + shrinkY * b.fout()) * scl;
        float width = this.width * ((1f - shrinkX) + shrinkX * b.fout()) * scl;
        float offset = -90 + (spin != 0 ? Mathf.randomSeed(b.id, 360f) + b.time * spin : 0f);

        arc.graphics.Color mix = Tmp.c1.set(mixColorFrom).lerp(mixColorTo, b.fin());

        Draw.mixcol(mix, mix.a);

        Draw.color(backColor);
        Draw.rect(backRegion, b.x, b.y, width, height, b.rotation() + offset);
        Draw.color(frontColor);
        Draw.rect(frontRegion, b.x, b.y, width, height, b.rotation() + offset);

        Draw.reset();
    }
}
