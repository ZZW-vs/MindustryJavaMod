package zzw.content.units.weapons;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.entities.Mover;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.Weapon;

/**
 * 迫击炮武器 (移植自 PU_V8 unity.type.weapons.MortarWeapon)
 * - 炮管倾角随目标距离变化 (近距高倾角, 远距低倾角)
 * - 自定义绘制炮管 + 炮管末端 + 热量贴图
 * - 简化 v158 适配: shoot/bullet 用 v158 原生逻辑, 仅保留倾角动画和绘制
 */
public class MortarWeapon extends Weapon {
    /** 倾角起始偏移 (度) */
    public float inclineOffset = 5f;
    /** 最大倾角 (度) */
    public float maxIncline = 85f;
    /** 炮管动画速度 */
    public float barrelSpeed = 5f;
    /** 炮管偏移 */
    public float barrelOffset = 0f;

    public TextureRegion barrelRegion, barrelEndRegion, barrelEndHeatRegion;

    public MortarWeapon(String name) {
        super(name);
        mountType = MortarMount::new;
    }

    @Override
    public void load() {
        super.load();
        barrelRegion = Core.atlas.find(name + "-barrel");
        barrelEndRegion = Core.atlas.find(name + "-barrel-end");
        barrelEndHeatRegion = Core.atlas.find(name + "-barrel-end-heat");
    }

    @Override
    public void draw(Unit unit, WeaponMount mount) {
        super.draw(unit, mount);
        float z = Draw.z();
        Draw.z(z + layerOffset);

        MortarMount mMount = (MortarMount) mount;
        float incline = -Mathf.sinDeg(Mathf.lerp(inclineOffset, maxIncline, mMount.incline)) * barrelRegion.width * Draw.scl;
        float endIncline = -Mathf.cosDeg(Mathf.lerp(inclineOffset, maxIncline, mMount.incline));
        float rotation = unit.rotation - 90f,
                weaponRotation = rotation + (rotate ? mount.rotation : 0),
                recoil = -((mount.reload) / reload * this.recoil),
                wx = unit.x + Angles.trnsx(rotation, x, y) + Angles.trnsx(weaponRotation, 0, recoil),
                wy = unit.y + Angles.trnsy(rotation, x, y) + Angles.trnsy(weaponRotation, 0, recoil);

        Tmp.v1.trns(weaponRotation - 90f, incline + barrelOffset).add(wx, wy);
        Tmp.v2.trns(weaponRotation - 90f, barrelOffset).add(wx, wy);

        // 炮管
        if (barrelRegion.found()) {
            Lines.stroke(barrelRegion.width * Draw.scl * 0.5f);
            Lines.line(barrelRegion, Tmp.v2.x, Tmp.v2.y, Tmp.v1.x, Tmp.v1.y, false);
        }
        // 热量贴图
        if (heatRegion.found() && mount.heat > 0f) {
            Draw.color(heatColor, mount.heat);
            Draw.blend(arc.graphics.Blending.additive);

            Lines.stroke(heatRegion.width * Draw.scl * 0.5f);
            Lines.line(heatRegion, Tmp.v2.x, Tmp.v2.y, Tmp.v1.x, Tmp.v1.y, false);

            Draw.blend();
            Draw.color();
        }

        // 炮管末端
        if (barrelEndRegion.found()) {
            Draw.rect(barrelEndRegion, Tmp.v1,
                    barrelEndRegion.width * Draw.scl,
                    barrelEndRegion.height * endIncline * Draw.scl,
                    weaponRotation);
        }
        // 炮管末端热量
        if (barrelEndHeatRegion.found() && mount.heat > 0f) {
            Draw.color(heatColor, mount.heat);
            Draw.blend(arc.graphics.Blending.additive);

            Draw.rect(barrelEndHeatRegion, Tmp.v1,
                    barrelEndHeatRegion.width * Draw.scl,
                    barrelEndHeatRegion.height * endIncline * Draw.scl,
                    weaponRotation);

            Draw.blend();
            Draw.color();
        }

        Draw.z(z);
    }

    @Override
    public void update(Unit unit, WeaponMount mount) {
        MortarMount mMount = (MortarMount) mount;
        float r = bullet.range;
        mMount.incline = Mathf.approachDelta(mMount.incline, Mathf.clamp(unit.dst(mount.aimX, mount.aimY) / r), barrelSpeed / r);
        super.update(unit, mount);
    }

    /**
     * 覆写 bullet() (移植自 PU132 MortarWeapon.shoot + bullet)
     * 根据 incline 调整子弹发射点, 让子弹从炮管末端发射 (而非固定 shootY 位置)
     * PU132: shootX/Y = mX/Y + trns(weaponRotation - 90, -incline + barrelOffset)
     * v158 适配: 临时修改 this.shootY 实现 (shootY 沿 weaponRotation 方向, 对应 PU132 的 weaponRotation - 90 方向)
     */
    @Override
    protected void bullet(Unit unit, WeaponMount mount, float xOffset, float yOffset, float angleOffset, Mover mover) {
        MortarMount mMount = (MortarMount) mount;
        // PU132: incline = sin(lerp(inclineOffset, maxIncline, mMount.incline)) * shootY
        // 倾角越大 (远距离), incline 越大, 炮口越靠近 mount (shootY 减小)
        float originalShootY = this.shootY;
        float incline = Mathf.sinDeg(Mathf.lerp(inclineOffset, maxIncline, mMount.incline)) * this.shootY;
        this.shootY = this.shootY - incline + barrelOffset;
        super.bullet(unit, mount, xOffset, yOffset, angleOffset, mover);
        this.shootY = originalShootY;
    }

    /** 迫击炮武器挂载点 (含倾角动画状态) */
    public static class MortarMount extends WeaponMount {
        public float incline = 0f;

        public MortarMount(Weapon weapon) {
            super(weapon);
        }
    }
}
