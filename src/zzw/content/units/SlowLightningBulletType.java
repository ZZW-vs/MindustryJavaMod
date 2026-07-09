package zzw.content.units;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Posc;
import mindustry.gen.Unit;

/**
 * PU132 SlowLightningBulletType 移植版 (适配 v150.1)
 * - 在 init(Bullet b) 时创建 SlowLightningEntity
 * - 子弹位置跟随 SlowLightningEntity
 * - 不绘制子弹本身 (由 SlowLightningEntity.draw 绘制闪电)
 * 参考: PU132 unity.entities.bullet.anticheat.SlowLightningBulletType
 *
 * PU132 原版参数: damage=120, range=870, nodeLength=80, nodeTime=7,
 *   splitChance=0.06, continuous=true, lifetime=160, lineWidth=3
 */
public class SlowLightningBulletType extends AntiCheatBulletTypeBase {
    // ★ 用 slRange 字段名, 避免隐藏 BulletType.range 字段 (v150.1 有 range 字段)
    protected float slRange = 870f, nodeLength = 80f, nodeTime = 7f, splitChance = 0.06f;
    protected SlowLightningType type;

    public SlowLightningBulletType(float damage) {
        super(0f, damage);
        lifetime = 160f;
        collides = false;
        hittable = absorbable = reflectable = false;
        keepVelocity = false;
        despawnEffect = hitEffect = Fx.none;
        // v150.1: BulletType.range 是字段, 直接设置 (PU132 用 range() 方法返回 slRange*0.8f)
        range = slRange * 0.8f;
        countsAsSkill = true;
    }

    @Override
    public void init() {
        super.init();

        SlowLightningBulletType b = this;

        type = new SlowLightningType() {{
            damage = b.damage;
            lifetime = b.lifetime;
            range = b.slRange;
            nodeLength = b.nodeLength;
            nodeTime = b.nodeTime;
            colorFrom = Color.red;
            colorTo = Color.black;
            splitChance = b.splitChance;
            continuous = true;
            lineWidth = 3f;
        }

        @Override
        public void damageUnit(SlowLightningNode s, Unit unit) {
            if (s.main.bullet != null && s.main.bullet.type == b) {
                b.hitUnitAntiCheat(s.main.bullet, unit);
            }
        }

        @Override
        public void damageBuilding(SlowLightningNode s, Building building) {
            if (s.main.bullet != null && s.main.bullet.type == b) {
                b.hitBuildingAntiCheat(s.main.bullet, building);
            }
        }

        @Override
        public void hit(SlowLightningNode s, float x, float y) {
            super.hit(s, x, y);
            if (s.main.bullet != null && s.main.bullet.type == b) {
                b.hit(s.main.bullet, x, y);
            }
        }
        };
    }

    @Override
    public void init(Bullet b) {
        b.data = type.create(b.team, b, b.x, b.y, b.rotation(), null,
            b.owner instanceof Posc ? (Posc) b.owner : null, null);
    }

    @Override
    public void update(Bullet b) {
        if (!checkSkillLimit(b)) return;
        if (b.data instanceof SlowLightningEntity) {
            SlowLightningEntity data = (SlowLightningEntity) b.data;
            b.x = data.x;
            b.y = data.y;
        }
    }

    @Override
    public void drawLight(Bullet b) {}

    @Override
    public void draw(Bullet b) {}
}
