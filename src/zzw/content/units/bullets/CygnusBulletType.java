package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.EmpBulletType;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;
import mindustry.type.StatusEffect;

/**
 * 天鹅座 EMP 弹 (PU_V8 CygnusBulletType 移植版)
 * - extends EmpBulletType (v158 只有无参构造器)
 * - hit() 给友军治疗 + overclock 状态
 * - 自定义绘制: 双三角形 + 旋转光环 (simplified)
 * 简化: 移除 UnityDrawf.shiningCircle, 用 Fill.circle 替代
 * 参考: PU_V8 main/src/unity/entities/bullet/energy/CygnusBulletType.java
 */
public class CygnusBulletType extends EmpBulletType {
    public float size = 8f;
    public float allyStatusDuration = 60f * 2f;
    public StatusEffect allyStatus = StatusEffects.overclock;

    public CygnusBulletType() {
        super();
    }

    @Override
    public void hit(Bullet b, float x, float y) {
        super.hit(b, x, y);

        if (hitUnits) {
            Units.nearby(b.team, x, y, radius, other -> {
                if (other.team == b.team && other != b.owner) {
                    other.heal(healPercent / 100f * other.maxHealth);
                    other.apply(allyStatus, allyStatusDuration);
                }
            });
        }
    }

    @Override
    public void drawLight(Bullet b) {
        Drawf.light(b.x, b.y, size * 3f, backColor, 0.3f);
    }

    @Override
    public void draw(Bullet b) {
        drawTrail(b);
        Draw.color(backColor);
        for (int i = 0; i < 2; i++) {
            float r = b.rotation() + (180f * i);
            Drawf.tri(b.x + Angles.trnsx(r, size - 2f), b.y + Angles.trnsy(r, size - 2f),
                    size, (size * 1.5f) + Mathf.sin(Time.time, 15f, size / 2f), r);
        }
        // 简化版旋转光环 (替代 UnityDrawf.shiningCircle)
        Draw.color(Color.white);
        for (int i = 0; i < 7; i++) {
            float ang = (Time.time * 30f + i * (360f / 7f)) % 360f;
            float r = size + Mathf.sin(Time.time + i, 17f, 4f);
            Fill.circle(b.x + Angles.trnsx(ang, r), b.y + Angles.trnsy(ang, r), 1.5f);
        }
        Draw.color(Color.white);
        for (int i = 0; i < 7; i++) {
            float ang = (Time.time * 23f + i * (360f / 7f) + 180f) % 360f;
            float r = size * 0.65f + Mathf.sin(Time.time + i, 11f, 3f);
            Fill.circle(b.x + Angles.trnsx(ang, r), b.y + Angles.trnsy(ang, r), 1f);
        }
    }
}
