package zzw.content.units.bullets;

import arc.math.geom.Position;
import arc.math.geom.Vec2;
import mindustry.entities.Predict;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Entityc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Teamc;
import mindustry.graphics.Trail;

/**
 * 弹射子弹类型 (PU132 unity.entities.bullet.monolith.energy.RicochetBulletType 移植)
 *
 * <p>核心机制: 命中敌人后不消失, 而是 "弹射" 到附近另一个敌人:
 * 命中时清除已碰撞列表 (b.collided.clear) 使子弹可以再次造成伤害,
 * 并把飞行方向转向 Predict.intercept 预判的下一个目标。
 * 弹射次数上限 = pierceCap (默认 3, 可在单位定义里覆盖)。
 * 找不到下一个目标时立即消散 (despawned)。</p>
 *
 * <p>渲染: 自带一条 Trail 拖尾 (存在 RicochetBulletData 里,
 * 与 v155.4 内建 b.trail 系统无关 — 本类 trailLength 字段遮蔽了父类同名字段,
 * 父类 trailLength 保持默认 -1 因此内建拖尾不启用)。</p>
 *
 * <p>★ v155.4 适配: hitTile 签名多出 x/y 参数;
 * Predict.intercept 7 参重载与 Bullet.collided IntSeq 均存在。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class RicochetBulletType extends BasicBulletType{
    /** 弹射拖尾长度 (遮蔽父类 trailLength, 仅供 data.trail 使用)。 */
    public int trailLength = 6;

    public RicochetBulletType(float speed, float damage){
        this(speed, damage, "bullet");
    }

    public RicochetBulletType(float speed, float damage, String spriteName){
        super(speed, damage, spriteName);
        pierce = true;
        pierceBuilding = true;
        pierceCap = 3;
        trailChance = 1f;
    }

    @Override
    public void init(Bullet b){
        super.init(b);
        b.data = new RicochetBulletData();
    }

    @Override
    public void hitEntity(Bullet b, Hitboxc other, float initialHealth){
        ricochet(b, (Position)other);
    }

    @Override
    public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        super.hitTile(b, build, x, y, initialHealth, direct);
        if(direct){
            ricochet(b, build);
        }
    }

    @Override
    public void update(Bullet b){
        super.update(b);

        if(b.data instanceof RicochetBulletData data && data.trail != null){
            data.trail.update(b.x, b.y);
        }
    }

    @Override
    public void draw(Bullet b){
        if(b.data instanceof RicochetBulletData data && data.trail != null){
            data.trail.draw(backColor, width * 0.18f);
        }

        super.draw(b);
    }

    /**
     * 弹射逻辑: 命中实体时调用。
     *
     * <p>步骤:</p>
     * <ol>
     *   <li>同一实体只弹射一次 (data.hit 记录上次命中 id);</li>
     *   <li>清空 b.collided — 否则 pierce 系统认为已碰撞过不会再伤害新目标;</li>
     *   <li>未达上限时寻找射程内的新敌人 (跳过刚命中的那个),
     *       用预截获算法转向; 找不到则消散。</li>
     * </ol>
     */
    public void ricochet(Bullet b, Position entity){
        if(!(b.data instanceof RicochetBulletData data)) return;

        int id = entity instanceof Entityc e ? e.id() : entity.hashCode();
        if(data.hit == id) return;

        data.hit = id;
        b.collided.clear();

        if(data.ricochet < pierceCap){
            data.ricochet++;
            data.findEnemy(b);
            if(data.target != null){
                if(data.target instanceof Hitboxc v){
                    Vec2 out = Predict.intercept(b.x, b.y, v.getX(), v.getY(), v.deltaX(), v.deltaY(), b.vel.len());
                    float rot = out.sub(b.x, b.y).angle();
                    b.vel.setAngle(rot);
                }else{
                    b.vel.setAngle(b.angleTo(data.target));
                }
            }else{
                despawned(b);
            }
        }
    }

    /**
     * 弹射子弹的运行数据。
     */
    public class RicochetBulletData{
        /** 已弹射次数。 */
        protected int ricochet;

        /** 下一个弹射目标。 */
        protected Teamc target;
        /** 上一次命中的实体 id。 */
        protected int hit = -1;

        /** 弹射拖尾。 */
        protected Trail trail = new Trail(trailLength);

        /**
         * 在子弹射程内寻找下一个弹射目标 (单位优先于建筑)。
         * <p>搜索半径随剩余寿命衰减 (range * fout)。
         * ★ v155.4: PU132 的 range() 方法在此版本为 public 字段 range。</p>
         */
        protected void findEnemy(Bullet b){
            target = Units.closestTarget(b.team, b.x, b.y, range * b.fout(),
                u -> u.isValid() && u.id != hit && ((u.isFlying() && collidesAir) || (u.isGrounded() && collidesGround)),
                t -> t.isValid() && t.id != hit && collidesGround
            );
        }
    }
}
