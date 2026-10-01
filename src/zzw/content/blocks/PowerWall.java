package zzw.content.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.Blending;
import arc.math.Mathf;
import arc.struct.ObjectFloatMap;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Bullet;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.BlockGroup;

/**
 * 单极子墙/电力墙 (PU132 unity.world.blocks.defense.PowerWall 移植)
 *
 * <p>受到能量弹 (激光/闪电等) 攻击时, 把弹种携带的能量转化为电力输出:
 * 每次被 {@code collision} 命中, 按 {@code (子弹伤害/伤害阈值) * (子弹速度/弹种速度) * 能量倍率}
 * 累加到 {@code productionEfficiency}; 效率随时间缓慢衰减到 0, 并向外产电
 * ({@code getPowerProduction() = powerProduction * productionEfficiency})。</p>
 *
 * <p>效率超过 1 时过载, 持续扣血; 被友方治疗弹命中或治疗/提升血量时效率清零。
 * 效率高于 {@code heatThreshold} 时绘制热图叠加。</p>
 */
public class PowerWall extends Wall {
    /** 弹种 → 能量转化倍率 (按 BulletType 的运行时类匹配) */
    public ObjectFloatMap<Class<?>> energyMultiplier = new ObjectFloatMap<>();
    /** 满效率时的基础发电量 */
    public float powerProduction = 2f;
    /** 伤害阈值 (低于/高于此值决定效率增速) */
    public float damageThreshold = 150f;
    /** 过载时每秒扣血量 */
    public float overloadDamage = 0.8f;

    /** 热量叠加贴图 */
    public TextureRegion heatRegion;
    /** 绘制热图的效率阈值 */
    public float heatThreshold = 0.35f;
    /** 热图颜色 */
    public Color heatColor = Color.red;

    public PowerWall(String name) {
        super(name);
        update = true;
        sync = true;
        flashHit = true;
        solid = true;
        consumesPower = false;
        outputsPower = true;
        hasPower = true;
        group = BlockGroup.walls;
    }

    @Override
    public void load() {
        super.load();
        heatRegion = Core.atlas.find(name + "-heat");
    }

    @Override
    public void setBars() {
        super.setBars();
        // 电力条: 显示当前发电量 (满效率 * powerProduction)
        addBar("power", (PowerWallBuild entity) -> new Bar(
            () -> Core.bundle.format("bar.poweroutput", Strings.fixed(entity.getPowerProduction() * 60f * entity.timeScale(), 1)),
            () -> Pal.powerBar,
            () -> entity.productionEfficiency
        ));
    }

    public class PowerWallBuild extends WallBuild {
        /** 当前能量转化效率 (累积的电力, 也是发电倍率) */
        public float productionEfficiency = 0f;
        protected boolean overloaded;

        @Override
        public void draw() {
            super.draw();

            // 效率超阈值时叠加脉动热图
            if (productionEfficiency > heatThreshold) {
                float heat = 1f + ((productionEfficiency - heatThreshold) / (1f - heatThreshold)) * 5.4f;
                heat += heat * Time.delta;

                Draw.color(heatColor, Mathf.absin(heat, 9f, 1f) * Mathf.curve(productionEfficiency, heatThreshold, 1f));
                Draw.blend(Blending.additive);
                Draw.rect(heatRegion, x, y);
                Draw.blend();
            }
        }

        @Override
        public void updateTile() {
            super.updateTile();
            productionEfficiency = Mathf.lerpDelta(productionEfficiency, 0f, 0.05f);

            overloaded = productionEfficiency > 1f;
            if (overloaded) {
                health -= overloadDamage * Time.delta;
            }
        }

        @Override
        public boolean collision(Bullet bullet) {
            // 能量转化: 伤害/阈值 * 速度比 * 弹种倍率
            productionEfficiency +=
                (bullet.damage / damageThreshold)
                * (bullet.vel.len() / bullet.type.speed)
                * energyMultiplier.get(
                    bullet.type.getClass().isAnonymousClass()
                    ?   bullet.type.getClass().getSuperclass()
                    :   bullet.type.getClass()
                , 1f);

            // 友方治疗弹命中: 效率清零
            if (bullet.team == team && bullet.type.healPercent > 0f) {
                productionEfficiency = 0f;
            }

            return super.collision(bullet);
        }

        @Override
        public float getPowerProduction() {
            return powerProduction * productionEfficiency;
        }

        @Override
        public void heal() {
            super.heal();
            productionEfficiency = 0f;
        }

        @Override
        public void heal(float amount) {
            super.heal(amount);
            productionEfficiency = 0f;
        }

        @Override
        public void health(float health) {
            if (this.health < health) productionEfficiency = 0f;
            super.health(health);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(productionEfficiency);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            productionEfficiency = read.f();
        }
    }
}