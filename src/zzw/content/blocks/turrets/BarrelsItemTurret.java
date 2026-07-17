package zzw.content.blocks.turrets;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.pattern.ShootAlternate;
import mindustry.world.blocks.defense.turrets.ItemTurret;

import static mindustry.Vars.tilesize;

/**
 * 多管物品炮台 (PU_V8 BarrelsItemTurret 完整移植)
 * ghost/banshee: 支持多炮管独立装填, focus 模式下所有炮管集中瞄准目标点
 * ★完整移植 PU_V8 原版机制 (v155.4 API 适配):
 *  - shoot(): focus 模式下使用 barrelCounter 交替 + spread + xRand 计算位置
 *  - shootBarrel(): 各炮管独立装填计数, focus 模式下瞄向目标点
 *  - bullet() 5 参方法 (v155.4): 自动处理声音/特效/反冲/弹药消耗
 *  - 使用 barrelCounter 替代 PU_V8 shotCounter (v155.4 无 shotCounter 字段)
 *  - 使用 tr3 自有 Vec2 替代 PU_V8 tr (v155.4 ItemTurretBuild 无 tr 字段)
 *  - spread 从 ShootAlternate 模式提取 (v155.4 无独立 spread 字段)
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

    /** 从 shoot 模式提取 spread (PU_V8 直接访问 spread 字段, v155.4 需从 ShootAlternate 提取) */
    protected float getSpread() {
        if (shoot instanceof ShootAlternate) {
            return ((ShootAlternate) shoot).spread;
        }
        return 0f;
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
                // ★完整移植 PU_V8: focus 模式集中瞄准目标点
                curRecoil = 1f;
                heat = 1f;
                float i = barrelCounter % 2 - 0.5f;
                float spread = getSpread();
                // tr3 既是位置偏移 (替代 PU_V8 的 tr)
                tr3.trns(rotation - 90f, spread * i + Mathf.range(xRand), size * tilesize / 2f);
                Vec2 targetVec = new Vec2();
                targetVec.trns(rotation, Math.max(Mathf.dst(x, y, targetPos.x, targetPos.y), size * tilesize));
                float rot = Angles.angle(tr3.x, tr3.y, targetVec.x, targetVec.y);
                // v155.4 bullet 5 参签名: (type, xOffset, yOffset, angleOffset, mover)
                bullet(type, tr3.x, tr3.y, rot - rotation + Mathf.range(inaccuracy), null);
                barrelCounter++;
                useAmmo();
            } else {
                super.shoot(type);
            }
        }

        protected void shootBarrel(BulletType type, int index) {
            curRecoil = Mathf.clamp(curRecoil + 0.5f, 0f, 1f);
            float i = barrelShotCounters[index] % 2 - 0.5f;
            // 炮管相对位置 (交替 ±x/2 偏移)
            float bx = barrels.get(index).x * i;
            float by = barrels.get(index).y;
            float rot = rotation;
            if (focus) {
                Vec2 targetVec = new Vec2();
                targetVec.trns(rotation, Math.max(Mathf.dst(x, y, targetPos.x, targetPos.y), size * tilesize));
                // 计算炮管世界位置
                float barrelWorldX = x + Angles.trnsx(rotation - 90f, bx, by);
                float barrelWorldY = y + Angles.trnsy(rotation - 90f, bx, by);
                rot = Angles.angle(barrelWorldX, barrelWorldY, targetVec.x + x, targetVec.y + y);
            }
            // v155.4 bullet 5 参签名, 自动处理声音/特效/反冲
            bullet(type, bx, by, rot - rotation + Mathf.range(inaccuracy), null);
            barrelShotCounters[index]++;
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
