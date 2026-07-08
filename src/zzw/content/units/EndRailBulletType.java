package zzw.content.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.geom.Vec2;
import arc.math.geom.Intersector;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

/**
 * PU132 EndRailBulletType 轨道穿透激光移植版
 * - pierceDamageFactor=0.001 几乎不衰减穿透
 * - 一次性遍历所有目标 (init 时计算)
 * - 4色叠加渲染 + 两端三角形收口
 * - 防作弊伤害 (继承 AntiCheatBulletTypeBase)
 * 参考: PU132 main/src/unity/entities/bullet/anticheat/EndRailBulletType.java
 * 简化: 用 v154.3 的 Units.nearbyEnemies + Intersector 替代 Utils.collideLineRawEnemy
 */
public class EndRailBulletType extends AntiCheatBulletTypeBase {
    public Color[] colors = new Color[]{
        Color.valueOf("f5303690"),
        Color.valueOf("f53036"),
        Color.valueOf("ff786e"),
        Color.black
    };
    public float length = 340f;
    public float collisionWidth = 4f;
    public Effect updateEffect = mindustry.content.Fx.none;
    public float updateEffectSeg = 20f;
    /** 穿透伤害衰减因子 (0.001 = 几乎不衰减) */
    public float pierceDamageFactor = 1f;

    public EndRailBulletType() {
        speed = 0f;
        pierceBuilding = true;
        pierce = true;
        reflectable = false;
        absorbable = false;
        hittable = false;
        hitEffect = mindustry.content.Fx.none;
        despawnEffect = mindustry.content.Fx.none;
        collides = false;
        lifetime = 20f;
    }

    @Override
    public void init() {
        super.init();
        drawSize = length * 2f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);

        float dam = damage;
        float len = length;

        // 终点坐标
        Tmp.v1.trns(b.rotation(), len).add(b);
        float endX = Tmp.v1.x, endY = Tmp.v1.y;

        // 一次性遍历所有敌方单位 (替代 PU132 Utils.collideLineRawEnemy)
        // 按 distance 从近到远排序处理, 实现 pierceDamageFactor 衰减
        java.util.List<Unit> hitUnits = new java.util.ArrayList<>();
        Units.nearbyEnemies(b.team, b.x, b.y, len + 50f, unit -> {
            if (!unit.hittable()) return;
            if (!unit.checkTarget(collidesAir, collidesGround)) return;
            if (Intersector.distanceSegmentPoint(b.x, b.y, endX, endY, unit.x, unit.y) > collisionWidth + unit.hitSize / 2f) return;
            hitUnits.add(unit);
        });
        // 按距离排序
        hitUnits.sort((u1, u2) -> Float.compare(b.dst(u1), b.dst(u2)));

        for (Unit unit : hitUnits) {
            if (dam <= 0f) break;
            float lh = unit.health;
            hitUnitAntiCheat(b, unit, dam - damage);
            dam -= lh * pierceDamageFactor;
            if (dam <= 0f) len = b.dst(unit);
        }

        // 遍历建筑
        java.util.List<Building> hitBuilds = new java.util.ArrayList<>();
        Vars.indexer.eachBlock(null, b.x, b.y, len + 50f,
                build -> build.team != b.team && build.health > 0,
                build -> {
                    if (Intersector.distanceSegmentPoint(b.x, b.y, endX, endY, build.x, build.y) > collisionWidth + build.hitSize() / 2f) return;
                    hitBuilds.add(build);
                });
        hitBuilds.sort((b1, b2) -> Float.compare(b.dst(b1), b.dst(b2)));

        for (Building build : hitBuilds) {
            if (dam <= 0f) break;
            float lh = build.health;
            hitBuildingAntiCheat(b, build, dam - damage);
            dam -= lh * pierceDamageFactor;
            if (dam <= 0f) len = b.dst(build);
        }

        // 沿激光线生成轨迹特效
        if (updateEffect != mindustry.content.Fx.none) {
            Vec2 nor = Tmp.v2.trns(b.rotation(), 1f).nor();
            for (float i = 0; i <= len; i += updateEffectSeg) {
                updateEffect.at(b.x + nor.x * i, b.y + nor.y * i, b.rotation());
            }
        }

        b.fdata = len;
    }

    @Override
    public void drawLight(Bullet b) {
        // 光源在 draw() 中处理
    }

    @Override
    public void draw(Bullet b) {
        float stroke = 2f * 1.5f * b.fout();
        Vec2 v = Tmp.v1.trns(b.rotation(), b.fdata).add(b);

        for (Color c : colors) {
            Draw.color(c);
            Drawf.tri(b.x, b.y, stroke * collisionWidth, stroke * 1.22f * length * 0.02f, b.rotation() + 180f);
            Lines.stroke(stroke * collisionWidth);
            Lines.line(b.x, b.y, v.x, v.y);
            Drawf.tri(v.x, v.y, stroke * collisionWidth, stroke * 1.22f * length * 0.07f, b.rotation());
            stroke /= 1.5f;
        }
        Draw.reset();
    }
}
