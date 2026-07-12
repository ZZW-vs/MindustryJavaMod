package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;

/**
 * 虚空碎裂弹 (移植自 PU132 VoidFractureBulletType, 简化版)
 * - 黑色方块子弹, 悬停 30 tick 后直线冲刺
 * - 悬停时跟踪目标, 冲刺时穿透
 * - 简化: 不做"冲刺中"vs"悬停中"两种状态机, 统一为跟踪
 */
public class VoidFractureBulletType extends AntiCheatBulletTypeBase {
    public float length = 28f;
    public float width = 12f;
    public float widthTo = 3f;
    public float targetingRange = 320f;
    public float trueSpeed;
    public float nextLifetime = 10f;
    public int maxTargets = 15;
    public float spikesRange = 100f;
    public float spikesDamage = 200f;

    public VoidFractureBulletType(float speed, float damage) {
        super(4.3f, damage);
        drag = 0.11f;
        trueSpeed = speed;
        collides = false;
        collidesTiles = false;
        keepVelocity = false;
        pierce = true;
        despawnEffect = Fx.none;
        smokeEffect = Fx.none;
        hitEffect = Fx.hitLancer;
        lifetime = 60f;
    }

    @Override
    public void init() {
        super.init();
        drawSize = targetingRange * 2f + 30f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        // 跟踪最近目标
        final float[] bestAngle = {b.rotation()};
        final float[] bestDist = {Float.MAX_VALUE};
        Units.nearbyEnemies(b.team, b.x(), b.y(), targetingRange, u -> {
            if (!u.hittable() || !u.checkTarget(collidesAir, collidesGround)) return;
            if (!Angles.within(b.rotation(), b.angleTo(u), 90f)) return;
            float d = u.dst(b);
            if (d < bestDist[0]) {
                bestDist[0] = d;
                bestAngle[0] = b.angleTo(u);
            }
        });
        b.rotation(Mathf.slerpDelta(b.rotation(), bestAngle[0], 0.1f));
        // 命中检测
        if (b.timer(1, 5f)) {
            Units.nearbyEnemies(b.team, b.x(), b.y(), spikesRange, u -> {
                if (!u.hittable() || !u.checkTarget(collidesAir, collidesGround)) return;
                if (u.dst(b) > spikesRange) return;
                hitUnitAntiCheat(b, u);
            });
            Vars.indexer.allBuildings(b.x(), b.y(), spikesRange, build -> {
                if (build.team == b.team || build.health <= 0f) return;
                if (build.dst(b) > spikesRange) return;
                hitBuildingAntiCheat(b, build);
            });
        }
    }

    @Override
    public void draw(Bullet b) {
        Draw.color(Color.black);
        float in = Mathf.clamp(b.time / 30f);
        Drawf.tri(b.x(), b.y(), width * in, length, b.rotation());
        Drawf.tri(b.x(), b.y(), width * in, length, b.rotation() + 180f);
        // 后拖尾线
        if (b.time > 30f) {
            for (int i = 0; i < 3; i++) {
                float f = Mathf.lerp(width, widthTo, i / 2f);
                float a = Mathf.lerp(0.25f, 1f, (i / 2f) * (i / 2f));
                Draw.alpha(a);
                Lines.stroke(f);
                float tailX = b.x() - Mathf.cosDeg(b.rotation()) * 12f * (i + 1);
                float tailY = b.y() - Mathf.sinDeg(b.rotation()) * 12f * (i + 1);
                Lines.line(tailX, tailY, b.x(), b.y(), false);
            }
        }
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b) {
    }
}
