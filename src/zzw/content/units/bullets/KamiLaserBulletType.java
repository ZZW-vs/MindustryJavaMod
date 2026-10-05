package zzw.content.units.bullets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Ellipse;
import arc.math.geom.Intersector;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.gen.Entityc;
import zzw.content.units.kami.KamiLaserData;

/**
 * kami 激光弹体 (PU132 {@code KamiLaserComp} + {@code NewKamiLaserBulletType} 的移植)。
 *
 * <p>表示一段两端点 ({@code x/y} 与 {@code x2/y2}) 的激光。v160 不使用自定义实体,
 * 端点与碰撞参数保存在 {@link KamiLaserData} 中 (挂在 {@code b.data})。默认每 5 帧做
 * 一次线段/椭圆碰撞, 命中走 {@link BulletType#hitEntity} / {@link BulletType#hit}。</p>
 *
 * <p>参考: PU132 {@code unity/entities/bullet/kami/NewKamiLaserBulletType.java} 与
 * {@code unity/entities/comp/KamiLaserComp.java}。</p>
 */
public class KamiLaserBulletType extends BulletType {
    static TextureRegion hcircle;

    private static final Vec2 tv = new Vec2();
    private static final Ellipse ep = new Ellipse();
    private static final Rect tr1 = new Rect(), tr2 = new Rect();

    public KamiLaserBulletType(){
        speed = 0f;
        damage = 200f;
        absorbable = false;
        hittable = false;
        collidesTiles = false;
        collides = false;
        pierce = true;
        keepVelocity = false;
        lifetime = 60f;
        drawSize = 10000f;
        despawnEffect = Fx.none;
        hitEffect = Fx.none;
    }

    @Override
    public void load(){
        if(hcircle == null) hcircle = Core.atlas.find("hcircle");
    }

    /** 取子弹上的激光数据 */
    public static KamiLaserData data(Bullet b){
        return (KamiLaserData) b.data;
    }

    /** 创建一段从 (x,y) 到 (x2,y2) 的激光 */
    public Bullet createL(Entityc owner, Team team, float x, float y, float x2, float y2, Object data){
        Bullet b = create(owner, team, x, y, 0f);
        KamiLaserData d = data(b);
        d.x2 = x2;
        d.y2 = y2;
        d.data = data;
        return b;
    }

    @Override
    public void init(Bullet b){
        super.init(b);
        b.data = new KamiLaserData();
    }

    @Override
    public void update(Bullet b){
        super.update(b);
        KamiLaserData d = data(b);
        if(d == null) return;
        if(d.behavior != null) d.behavior.update(b, d);
        if(!d.intervalCollision || b.timer(1, 5f)){
            updateCollision(b, d);
        }
        if(b.timer(2, 60f)){
            d.collided.clear();
        }
    }

    /** 沿线段的碰撞结算 (PU132 KamiLaserComp.updateCollision) */
    void updateCollision(Bullet b, KamiLaserData d){
        float mx = (b.x + d.x2) / 2f, my = (b.y + d.y2) / 2f;
        float ang = Angles.angle(b.x, b.y, d.x2, d.y2);
        tr1.setCentered(b.x, b.y, d.width * 2f);
        tr2.setCentered(d.x2, d.y2, d.width * 2f);
        tr1.merge(tr2);

        Units.nearby(tr1, e -> {
            if(e.team == b.team || d.collided.contains(e.id)) return;
            float size = e.hitSize / 2f;
            if(d.ellipseCollision){
                tv.set(e.x - mx, e.y - my).rotate(-ang);
                ep.set(0f, 0f, Mathf.dst(b.x, b.y, d.x2, d.y2) * 2f + size, d.width * 2f + size);
                if(ep.contains(tv)){
                    b.type.hitEntity(b, e, e.health);
                    b.type.hit(b, e.x, e.y);
                    d.collided.add(e.id);
                }
            }else{
                float dst = Intersector.distanceSegmentPoint(b.x, b.y, d.x2, d.y2, e.x, e.y);
                if(dst < d.width + size){
                    b.type.hitEntity(b, e, e.health);
                    b.type.hit(b, e.x, e.y);
                    d.collided.add(e.id);
                }
            }
        });
    }

    @Override
    public void draw(Bullet b){
        KamiLaserData d = data(b);
        if(d == null) return;
        TextureRegion r = Core.atlas.find("circle");
        float time = (b.time * 2f) + (Time.time / 2f);
        Tmp.c1.set(Color.red).shiftHue(time);
        if(d.ellipseCollision){
            Vec2 v = Tmp.v1.set(b.x, b.y).sub(d.x2, d.y2).setLength(3f);
            Lines.stroke((d.width + 3.5f) * 2f);
            Draw.color(Tmp.c1);
            Lines.line(r, b.x + v.x, b.y + v.y, d.x2 - v.x, d.y2 - v.y, false);
            Draw.color();
            Lines.stroke(d.width * 2f);
            Lines.line(r, b.x, b.y, d.x2, d.y2, false);
            Draw.reset();
        }else{
            float ang = Angles.angle(b.x, b.y, d.x2, d.y2);
            Draw.blend(Blending.additive);
            Draw.color(Tmp.c1);
            Lines.stroke(d.width * 2f);
            Lines.line(b.x, b.y, d.x2, d.y2, false);
            Draw.rect(hcircle, b.x, b.y, d.width * 2f, d.width * 2f, ang + 180f);
            Draw.rect(hcircle, d.x2, d.y2, d.width * 2f, d.width * 2f, ang);
            Draw.blend();
        }
    }

    @Override
    public void drawLight(Bullet b){
        // PU132 原版此激光不绘制光源
    }
}
