package zzw.content.blocks.turrets;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.entities.bullet.BulletType;
import mindustry.world.blocks.defense.turrets.ItemTurret;

import static mindustry.Vars.tilesize;

/**
 * 多管物品炮台 (PU_V8 BarrelsItemTurret 移植版)
 * ghost/banshee: 支持多炮管独立装填, focus 模式下所有炮管集中瞄准目标点
 * 简化: 保留多炮管系统 (addBarrel + barrelReloads), focus 模式逻辑;
 *       移除 baseRegion 自定义查找 (v158 用 @Load 注解)
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/BarrelsItemTurret.java
 */
public class BarrelsItemTurret extends ItemTurret {
    protected final Seq<Barrel> barrels = new Seq<>(1);
    protected boolean focus;
    protected Vec2 tr3 = new Vec2();

    public BarrelsItemTurret(String name) {
        super(name);
    }

    public void addBarrel(float x, float y, float reloadTime) {
        barrels.add(new Barrel(x, y, reloadTime));
    }

    protected class Barrel {
        public final float x, y, reloadTime;

        public Barrel(float x, float y, float reloadTime) {
            this.x = x;
            this.y = y;
            this.reloadTime = reloadTime;
        }
    }

    public class BarrelsItemTurretBuild extends ItemTurretBuild {
        protected float[] barrelReloads;
        protected int[] barrelShotCounters;

        @Override
        public void placed() {
            super.placed();
            barrelReloads = new float[barrels.size];
            barrelShotCounters = new int[barrels.size];
        }

        @Override
        protected void shoot(BulletType type) {
            if (focus) {
                // focus 模式: 集中瞄准目标点 (原版逻辑)
                curRecoil = 1f;
                heat = 1f;

                // 使用 shootX/shootY (v158 字段) 作为炮口位置
                float bx = x + Angles.trnsx(rotation - 90, shootX, shootY);
                float by = y + Angles.trnsy(rotation - 90, shootX, shootY);
                tr3.trns(rotation, Math.max(Mathf.dst(x, y, targetPos.x, targetPos.y), size * tilesize));

                float rot = Angles.angle(bx, by, tr3.x + x, tr3.y + y);

                bullet(type, 0f, 0f, rot - rotation, null);
                barrelCounter++;
                useAmmo();
            } else {
                super.shoot(type);
            }
        }

        protected void shootBarrel(BulletType type, int index) {
            curRecoil = Mathf.clamp(curRecoil + 0.5f, 0f, 1f);

            float i = barrelShotCounters[index] % 2 - 0.5f;
            float bx = x + Angles.trnsx(rotation - 90, barrels.get(index).x * i, barrels.get(index).y);
            float by = y + Angles.trnsy(rotation - 90, barrels.get(index).x * i, barrels.get(index).y);
            float rot = rotation;

            if (focus) {
                tr3.trns(rotation, Math.max(Mathf.dst(x, y, targetPos.x, targetPos.y), size * tilesize));
                rot = Angles.angle(bx, by, tr3.x + x, tr3.y + y);
            }

            // 直接创建子弹 (绕过 v158 pattern 系统, 实现多管独立射击)
            type.create(this, team, bx, by, rot + Mathf.range(inaccuracy), 1f, 1f);
            barrelShotCounters[index]++;
            useAmmo();
            (shootEffect == null ? type.shootEffect : shootEffect).at(bx, by, rot, type.hitColor);
        }

        @Override
        protected void updateShooting() {
            super.updateShooting();
            if (barrelReloads == null) {
                barrelReloads = new float[barrels.size];
                barrelShotCounters = new int[barrels.size];
            }
            for (int i = 0, len = barrels.size; i < len; i++) {
                if (hasAmmo()) {
                    if (barrelReloads[i] >= barrels.get(i).reloadTime) {
                        shootBarrel(peekAmmo(), i);
                        barrelReloads[i] = 0f;
                    } else {
                        barrelReloads[i] += delta() * peekAmmo().reloadMultiplier * baseReloadSpeed();
                    }
                }
            }
        }
    }
}
