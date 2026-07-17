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
        // ★ v155.4 bullet(type, xOffset, yOffset, ...) 期望 LOCAL 局部坐标 (rotation-90 坐标系)
        // 默认 shootY = size*tilesize/2 (前向半身高偏移), 我们自定义每个炮管的前向偏移
        // 所以将默认值清零, 这样 yOffset 就是相对于炮台中心的前向距离
        shootY = 0f;
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
                // ★ v155.4 bullet() 期望 LOCAL 局部坐标 (rotation-90 坐标系), 不是世界坐标
                // 之前错误地传入了已旋转的 tr3.x/tr3.y 导致双重旋转, 子弹位置偏移
                float xOff = spread * i;
                float yOff = size * tilesize / 2f;
                // 用 tr3 (世界坐标) 仅用于角度计算 (从炮口位置到目标点的角度)
                tr3.trns(rotation - 90f, xOff, yOff);
                Vec2 targetVec = new Vec2();
                targetVec.trns(rotation, Math.max(Mathf.dst(x, y, targetPos.x, targetPos.y), size * tilesize));
                float rot = Angles.angle(tr3.x, tr3.y, targetVec.x, targetVec.y);
                // v155.4 bullet 5 参签名: 传入 LOCAL 偏移, bullet() 内部会旋转+加 (x,y)
                // bullet() 自动处理 useAmmo/effects/recoil/heat, 无需重复调用
                bullet(type, xOff, yOff, rot - rotation + Mathf.range(inaccuracy), null);
                barrelCounter++;
            } else {
                super.shoot(type);
            }
        }

        protected void shootBarrel(BulletType type, int index) {
            curRecoil = Mathf.clamp(curRecoil + 0.5f, 0f, 1f);
            float i = barrelShotCounters[index] % 2 - 0.5f;
            // ★ v155.4 bullet() 期望 LOCAL 局部坐标 (rotation-90 坐标系)
            float xOff = barrels.get(index).x * i;
            float yOff = barrels.get(index).y;
            float rot = rotation;
            if (focus) {
                // 用 tr3 (世界坐标) 仅用于角度计算
                tr3.trns(rotation - 90f, xOff, yOff);
                Vec2 targetVec = new Vec2();
                targetVec.trns(rotation, Math.max(Mathf.dst(x, y, targetPos.x, targetPos.y), size * tilesize));
                rot = Angles.angle(tr3.x, tr3.y, targetVec.x, targetVec.y);
            }
            // v155.4 bullet 5 参签名, 自动处理声音/特效/反冲
            bullet(type, xOff, yOff, rot - rotation + Mathf.range(inaccuracy), null);
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
