package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;
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
 * - 继承 ExpLaserBulletType 支持 lengthInc/damageInc 等级缩放
 */
public class GeyserLaserBulletType extends ExpLaserBulletType {
    public BulletType geyser;
    public float[] strokes = {2.9f, 1.8f, 1f};
    public float width = 3f;
    public float widthInc = 0f;

    public GeyserLaserBulletType(float length, float damage) {
        super(length, damage);
        this.hitSize = 0f;
        this.lifetime = 18f;
        this.despawnEffect = mindustry.content.Fx.none;
        this.keepVelocity = false;
        this.collides = false;
        this.pierce = true;
        this.hittable = false;
        this.absorbable = false;
    }

    /** PU_V8: 从 b.data 读取液体类型 (bullet 创建时由 OmniLiquidTurret.bullet() 传入),
     *  回退到 b.owner 读取 (兼容旧调用方式) */
    public Liquid getLiquid(Bullet b) {
        if (b.data instanceof Liquid l) {
            return l;
        }
        if (b.owner instanceof OmniLiquidTurret.OmniLiquidTurretBuild build) {
            return build.liquids.current();
        }
        return Liquids.water;
    }

    @Override
    public void init(Bullet b) {
        // 保存液体引用 (super.init 会设置 b.fdata 但不覆盖 b.data)
        Liquid l = getLiquid(b);

        // 调用 ExpLaserBulletType.init 使用动态长度 + 伤害增量
        super.init(b);

        // ★ 激光末端位置 = b + trns(rotation, 实际长度)
        // 使用 b.fdata (collideLaser 设置的实际碰撞长度)
        float actualLength = b.fdata > 0 ? b.fdata : getLength(b);
        Vec2 dest = new Vec2().trns(b.rotation(), actualLength).add(b.x, b.y);

        // 在目标点生成 geyser 子弹 (传入液体作为 Bullet.data)
        if (geyser != null) {
            geyser.create(b.owner, b.team, dest.x, dest.y, b.rotation(), -1f, 1f, 1f, l);
        }
    }

    @Override
    public void draw(Bullet b) {
        // 激光从 b 指向末端 (使用实际碰撞长度 b.fdata)
        float actualLength = b.fdata > 0 ? b.fdata : getLength(b);
        Tmp.v1.trns(b.rotation(), actualLength).add(b);

        float w = width + widthInc * getLevel(b);
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
