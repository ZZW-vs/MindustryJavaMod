package zzw.content.units.bullets;

import zzw.content.units.anticheat.AntiCheatBulletModule;
import zzw.content.units.anticheat.ArmorDamageModule;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import mindustry.content.Fx;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;

/**
 * 时间停止子弹 (移植自 PU132 TimeStopBulletType, 简化版)
 * - 标准子弹, 跟踪目标, 命中后给目标一个减速/失效效果
 * - 简化为只触发 effect (不实现真正的时间停止机制, 因为没有 Unity.TimeStop 系统)
 * - lifetime=110f, pierceCap=3
 */
public class TimeStopBulletType extends AntiCheatBulletTypeBase {
    public float duration = 45f;

    public TimeStopBulletType(float speed, float damage) {
        super(speed, damage);
        despawnEffect = Fx.hitLancer;
        hitEffect = Fx.hitLancer;
        trailColor = Color.valueOf("f53036");
        trailLength = 10;
        trailWidth = 4f;
        pierce = true;
        pierceCap = 3;
        lifetime = 110f;
        // 装备削甲模块
        modules = new AntiCheatBulletModule[]{
            new ArmorDamageModule(1f / 100f, 2f, 8f, 3f)
        };
    }

    @Override
    public void draw(Bullet b) {
        drawTrail(b);
        Draw.color(trailColor);
        Drawf.tri(b.x(), b.y(), trailWidth * 2 * 1.22f, 14f, b.rotation());
        Drawf.tri(b.x(), b.y(), trailWidth * 2 * 1.22f, 7f, b.rotation() + 180f);
        Draw.color();
    }

    @Override
    public void drawLight(Bullet b) {
    }
}
