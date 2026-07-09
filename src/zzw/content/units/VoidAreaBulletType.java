package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.graphics.Layer;

/**
 * PU132 VoidAreaBulletType 移植版 (黑色圆形虚空区域)
 * - 由 EndSweepLaser 命中时生成 (每隔 distance 距离触发一次)
 * - 半径范围内持续造成防作弊伤害 (每 5 tick 检测一次)
 * - shadowRealm 混合模式渲染黑色圆形 (渐入渐出)
 * 参考: PU132 main/src/unity/entities/bullet/anticheat/VoidAreaBulletType.java
 * 参数对齐 PU132 UnityBullets.oppressionArea (L1220-1233)
 */
public class VoidAreaBulletType extends AntiCheatBulletTypeBase {
    public float fadeInTime = 15f, fadeOutTime = 15f;
    public float radius = 150f;

    // PU132 颜色常量 (UnityPal.scarColor / UnityPal.endColor)
    private static final Color SCAR_COLOR = Color.valueOf("f53036");
    private static final Color END_COLOR = Color.valueOf("ff786e");

    public VoidAreaBulletType(float damage) {
        super(0f, damage);
        collides = false;
        collidesTiles = false;
        despawnEffect = hitEffect = mindustry.content.Fx.none;
        layer = Layer.flyingUnit + 1f;
        keepVelocity = false;
        countsAsSkill = true;
        maxActive = 5;  // 黑圆形最多5个
    }

    @Override
    public void init() {
        super.init();
        drawSize = radius * 2f;
    }

    @Override
    public void update(Bullet b) {
        if (!checkSkillLimit(b)) return;
        if (b.timer(1, 5f)) {
            float fin = getFin(b);
            // 半径范围内持续伤害 (渐入渐出期间半径随 fin 缩放)
            Units.nearbyEnemies(b.team, b.x, b.y, radius * fin, u -> hitUnitAntiCheat(b, u));
            Vars.indexer.eachBlock(null, b.x, b.y, radius * fin,
                    build -> build.team != b.team,
                    build -> hitBuildingAntiCheat(b, build));
        }
    }

    @Override
    public void drawLight(Bullet b) {
        // 光源在 draw() 中处理
    }

    @Override
    public void draw(Bullet b) {
        float fin = getFin(b);
        float osc = Mathf.absin(b.time, 8f, 1f);
        Tmp.c1.set(SCAR_COLOR).lerp(END_COLOR, osc);

        Draw.color(Tmp.c1);
        Draw.blend(VoidPortalBulletType.shadowRealm);
        Fill.circle(b.x, b.y, fin * radius);
        Draw.blend();
        Lines.stroke(4f - osc * 1.5f);
        Lines.circle(b.x, b.y, fin * radius);
        Draw.reset();
    }

    /** 渐入渐出系数 (前 fadeInTime 渐入, 后 fadeOutTime 渐出) */
    private float getFin(Bullet b) {
        return Mathf.clamp(b.time / fadeInTime) * Mathf.clamp(b.time > b.lifetime - fadeOutTime ? 1f - (b.time - (lifetime - fadeOutTime)) / fadeOutTime : 1f);
    }
}
