package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.Vars;

import mindustry.gen.Bullet;
import mindustry.gen.Groups;

/**
 * 荒芜弹 (移植自 PU132 DesolationBulletType, 简化版)
 * - 双三角扇形伤害区域
 * - 健康度衰减 (打越久伤害越低)
 * - 简化: 用矩形扫描代替三角形检测
 * - width=180-230, length=70
 */
public class DesolationBulletType extends AntiCheatBulletTypeBase {
    public float health = 1500000f;
    public float maxDamage = 5000f;
    public float length = 70f;
    public float widthFrom = 180f;
    public float widthTo = 230f;
    public float fadeInTime = 40f;
    public float fadeOutTime = 20f;
    // ★ 偏红透明渐变 (去掉白色, 改为红色半透明渐变)
    public Color[] colors = {
        new Color(0xf5303640),   // 红更透明 (外层)
        new Color(0xf5303680),   // 红半透明
        new Color(0xf53036ff),   // 红不透明
        new Color(0xff786eff)    // 橙红 (最内层)
    };

    public DesolationBulletType(float speed, float damage) {
        super(speed, damage);
        hittable = false;
        absorbable = false;
        collides = false;
        pierce = true;
        pierceShields = true;
        impact = true;
        keepVelocity = false;
        knockback = 5f;
        lifetime = 60f;
    }

    @Override
    public void init() {
        super.init();
        drawSize = Math.max(Math.max(widthTo, widthFrom), length) * 2f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.fdata(health);  // 用 fdata 存当前生命
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        if (b.fdata() <= 0f) {
            b.remove();
            return;
        }
        if (b.timer(1, 5f)) {
            // 矩形扫描单位和建筑
            float width = Mathf.lerp(widthFrom, widthTo, Mathf.clamp(b.time / fadeInTime));
            Tmp.v1.trns(b.rotation(), length / 2f);
            float cx = b.x();
            float cy = b.y();
            float halfW = width / 2f + 10f;
            float halfL = length / 2f + 10f;
            // 旋转 90 度方向
            Tmp.v2.trns(b.rotation() - 90f, 1f);

            // 单位检测
            Groups.unit.intersect(cx - halfW, cy - halfL, halfW * 2f, halfL * 2f, unit -> {
                if (unit.team() == b.team || unit.dead) return;
                // 简单距离判断: 到中心
                float dx = unit.x() - cx, dy = unit.y() - cy;
                // 旋转到子弹本地坐标
                float lx = dx * Tmp.v2.x + dy * Tmp.v2.y;
                float ly = -dx * Tmp.v2.y + dy * Tmp.v2.x;
                if (Math.abs(lx) < halfW && Math.abs(ly) < halfL) {
                    float dmg = Math.min(unit.health(), maxDamage);
                    b.fdata(b.fdata() - dmg);
                    hitUnitAntiCheat(b, unit);
                    if (b.fdata() <= 0f) return;
                }
            });

            // 建筑检测
            arc.util.Tmp.r1.setCentered(cx, cy, halfW * 2f, halfL * 2f);
            if (Vars.world != null) {
                Vars.indexer.allBuildings(cx, cy, halfW * 2f, build -> {
                    if (build.team == b.team || build.health <= 0f) return;
                    float dx = build.x - cx, dy = build.y - cy;
                    float lx = dx * Tmp.v2.x + dy * Tmp.v2.y;
                    float ly = -dx * Tmp.v2.y + dy * Tmp.v2.x;
                    if (Math.abs(lx) < halfW && Math.abs(ly) < halfL) {
                        float dmg = Math.min(build.health, maxDamage);
                        b.fdata(b.fdata() - dmg);
                        hitBuildingAntiCheat(b, build);
                    }
                });
            }
        }
    }

    @Override
    public void draw(Bullet b) {
        float width = Mathf.lerp(widthFrom, widthTo, Mathf.clamp(b.time / fadeInTime));
        float in = b.fdata() / health;
        float out = Mathf.clamp(b.time > b.lifetime - fadeOutTime ? 1f - (b.time - (lifetime - fadeOutTime)) / fadeOutTime : 1f);
        Tmp.v1.trns(b.rotation(), length / 2f);

        for (Color c : colors) {
            Draw.color(c);
            for (int s : Mathf.signs) {
                Tmp.v2.trns(b.rotation() - 90f, width * in * s * out, -(length / 2f) * 1.75f).add(b);
                float x1 = b.x() + Tmp.v1.x, x2 = b.x() - Tmp.v1.x,
                    y1 = b.y() + Tmp.v1.y, y2 = b.y() - Tmp.v1.y;
                Fill.tri(x1, y1, x2, y2, Tmp.v2.x, Tmp.v2.y);
            }
        }
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b) {
    }
}
