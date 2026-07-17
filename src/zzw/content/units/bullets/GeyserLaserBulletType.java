package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.content.Liquids;
import zzw.content.exp.OmniLiquidTurret;

/**
 * PU_V8 GeyserLaserBulletType 移植版 (液体激光)
 * 参考: PU_V8 main/src/unity/entities/bullet/exp/GeyserLaserBulletType.java
 *
 * 功能:
 * - 激光子弹, 命中目标后生成 GeyserBulletType (喷泉子弹)
 * - 颜色和光效根据当前液体变化
 * - 从 b.owner (OmniLiquidTurretBuild) 读取当前液体类型
 *
 * 简化:
 * - 不继承 ExpLaserBulletType (项目未移植), 改用 v158 原生 LaserBulletType
 * - 移除 lengthInc/widthInc/damageInc 等经验等级增量 (炮台等级在 ExpTurret.EField 中处理 reload/range)
 * - hitMissed 行为简化: 始终在激光末端生成 geyser
 */
public class GeyserLaserBulletType extends LaserBulletType {
    public BulletType geyser;
    public float[] strokes = {2.9f, 1.8f, 1f};
    public float width = 3f;

    public GeyserLaserBulletType(float length, float damage) {
        super(damage);
        this.length = length;
        this.drawSize = length * 2f;
        this.hitSize = 0f;
        this.lifetime = 18f;
        this.despawnEffect = mindustry.content.Fx.none;
        this.keepVelocity = false;
        this.collides = false;
        this.pierce = true;
        this.hittable = false;
        this.absorbable = false;
    }

    /** 从 b.owner (OmniLiquidTurretBuild) 读取当前液体类型 */
    public Liquid getLiquid(Bullet b) {
        if (b.owner instanceof OmniLiquidTurret.OmniLiquidTurretBuild build) {
            return build.liquids.current();
        }
        return Liquids.water;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        Liquid l = getLiquid(b);

        // ★ 激光末端位置 = b + trns(rotation, length)
        Vec2 dest = new Vec2().trns(b.rotation(), length).add(b.x, b.y);

        // 在目标点生成 geyser 子弹 (传入液体作为 Bullet.data)
        if (geyser != null) {
            geyser.create(b.owner, b.team, dest.x, dest.y, b.rotation(), -1f, 1f, 1f, l);
        }
    }

    @Override
    public void draw(Bullet b) {
        // 激光从 b 指向末端
        Tmp.v1.trns(b.rotation(), length).add(b);

        float w = width;
        Liquid l = getLiquid(b);
        Draw.color(l.color, 1f);

        // 外圈半透明
        Draw.alpha(0.4f);
        Lines.stroke(b.fout() * w * strokes[0]);
        Lines.line(b.x, b.y, Tmp.v1.x, Tmp.v1.y);
        Fill.circle(b.x, b.y, b.fout() * w * 0.9f * strokes[0]);

        // 中圈
        Draw.alpha(1f);
        Lines.stroke(b.fout() * w * strokes[1]);
        Lines.line(b.x, b.y, Tmp.v1.x, Tmp.v1.y);
        Fill.circle(b.x, b.y, b.fout() * w * 0.9f * strokes[1]);

        // 内核白
        Draw.color(l.color, Color.white, 0.6f);
        Lines.stroke(b.fout() * w * strokes[2]);
        Lines.line(b.x, b.y, Tmp.v1.x, Tmp.v1.y);
        Fill.circle(b.x, b.y, b.fout() * w * 0.9f * strokes[2]);
        Draw.reset();

        // 光源 (v158 Drawf.light 无 team 参数)
        Drawf.light(b.x, b.y, Tmp.v1.x, Tmp.v1.y, w * 10 * b.fout(), l.lightColor, l.lightColor.a);
    }
}

