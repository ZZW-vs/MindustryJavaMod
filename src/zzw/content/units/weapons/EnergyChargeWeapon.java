package zzw.content.units.weapons;

import arc.func.Cons3;
import arc.math.Mathf;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.Weapon;

/**
 * 蓄力武器 (简化版, 完全用原版方法实现)
 *
 * 替代 PU132 EnergyChargeWeapon 的 chargeCondition/bulletC/ChargeMount 复杂模式
 * 只保留 drawCharge 蓄力视觉回调, shoot/update 走 v158 原生 Weapon 逻辑
 *
 * 核心设计 (与 PU132 一致):
 * - mount.reload 从 reload(满充能) 衰减到 0(可发射)
 * - charge = 1f - clamp(mount.reload / reload)  (0=未充能, 1=满充能)
 * - draw() 在 super.draw 前后调用 drawCharge 回调绘制蓄力特效
 *
 * ★ 与普通 Weapon 的唯一区别: 多了 drawCharge 回调
 *   shoot() 走 super.shoot(), 不重写 (用 v158 原生逻辑)
 *   update() 走 super.update(), 不重写 (用 v158 原生逻辑)
 *
 * ★ 使用方式 (参考 PU132 UnityUnitTypes.java L4999-5051 oppression VoidFracture):
 *   weapons.add(new EnergyChargeWeapon("create-oppression-void-fracture") {{
 *       ... 普通Weapon配置 ...
 *       drawCharge = (unit, mount, charge) -> {
 *           // 绘制充能特效 (charge: 0=未充能, 1=满充能)
 *       };
 *   }});
 */
public class EnergyChargeWeapon extends Weapon {
    /** 充能绘制回调 (unit, mount, charge) -> 绘制充能特效, charge: 0=未充能, 1=满充能 */
    public Cons3<Unit, WeaponMount, Float> drawCharge = (unit, mount, charge) -> {};
    /** 是否在 super.draw 之上绘制充能特效 (true=之后绘制, false=之前绘制) */
    public boolean drawTop = true;
    /** 是否绘制武器贴图 */
    public boolean drawRegion = true;

    public EnergyChargeWeapon(String name) {
        super(name);
    }

    public EnergyChargeWeapon() {
        this("");
    }

    @Override
    public void drawOutline(Unit unit, WeaponMount mount) {
        if (drawRegion) super.drawOutline(unit, mount);
    }

    /**
     * 绘制武器 + 充能特效
     * 完全复刻 PU132 EnergyChargeWeapon.draw L37-44
     * - 临时 clamp mount.reload 到 [0, reload] 避免越界
     * - drawTop=false 时在 super.draw 之前绘制充能特效
     * - drawTop=true 时在 super.draw 之后绘制充能特效
     */
    @Override
    public void draw(Unit unit, WeaponMount mount) {
        float tmp = mount.reload;
        mount.reload = Mathf.clamp(mount.reload, 0f, reload);
        if (!drawTop) drawCharge.get(unit, mount, 1f - Mathf.clamp(mount.reload / reload));
        if (drawRegion) super.draw(unit, mount);
        mount.reload = tmp;
        if (drawTop) drawCharge.get(unit, mount, 1f - Mathf.clamp(mount.reload / reload));
    }
}
