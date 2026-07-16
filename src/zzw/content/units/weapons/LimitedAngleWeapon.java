package zzw.content.units.weapons;

import arc.math.Angles;
import arc.math.Mathf;
import arc.func.Boolf;
import arc.util.Tmp;
import mindustry.entities.Units;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.Weapon;

/**
 * 限制角度武器 (PU_V8 LimitedAngleWeapon 移植版)
 * - 只能在 angleCone 范围内 (相对 angleOffset) 射击
 * - findTarget 只搜索锥形范围内的敌方
 * 简化: 用 v158 baseRotation + rotationLimit 替代手动 clamp
 *       用 v158 Predict.intercept (接受 BulletType 而非 speed) 替代 PU_V8
 * 参考: PU_V8 main/src/unity/type/weapons/LimitedAngleWeapon.java
 */
public class LimitedAngleWeapon extends Weapon {
    public float angleCone = 45f;
    public float angleOffset = 0f;
    public float defaultAngle = 0f;

    public LimitedAngleWeapon(String name) {
        super(name);
        mountType = weapon -> {
            WeaponMount mount = new WeaponMount(weapon);
            mount.rotation = defaultAngle * Mathf.sign(flipSprite);
            return mount;
        };
    }

    public LimitedAngleWeapon() {
        this("");
    }

    @Override
    public void init() {
        super.init();
        // 用 v158 的 rotationLimit + baseRotation 实现角度限制
        // rotationLimit 是总角度, clamp 到 ±rotationLimit/2
        baseRotation = angleOffset * Mathf.sign(flipSprite);
        rotationLimit = angleCone * 2f;
    }

    @Override
    protected Teamc findTarget(Unit unit, float x, float y, float range, boolean air, boolean ground) {
        // 只搜索锥形范围内的敌方
        Boolf<Posc> angBool = e -> angleDist(unit.rotation + (angleOffset * Mathf.sign(flipSprite)), unit.angleTo(e)) <= angleCone;
        return Units.closestTarget(unit.team, x, y, range + Math.abs(shootY),
                u -> u.checkTarget(air, ground) && angBool.get(u),
                t -> ground && angBool.get(t));
    }

    @Override
    protected boolean checkTarget(Unit unit, Teamc target, float x, float y, float range) {
        return super.checkTarget(unit, target, x, y, range)
                || angleDist(unit.rotation + (angleOffset * Mathf.sign(flipSprite)), unit.angleTo(target)) > angleCone;
    }

    /** 计算角度差的绝对值 (0-180) */
    private float angleDist(float a, float b) {
        return Angles.angleDist(a, b);
    }
}
