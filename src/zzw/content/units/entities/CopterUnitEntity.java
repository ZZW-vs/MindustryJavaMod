package zzw.content.units.entities;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.UnitEntity;
import zzw.content.units.ZEntityRegister;
import zzw.content.units.rotor.RotorMount;

/**
 * 直升机单位实体 (移植自 PU_V8 CopterComp)
 *
 * 替代 PU_V8 的 @EntityComponent Copterc 接口,
 * 直接继承 UnitEntity 并内嵌旋翼状态字段 (rotors/rotorSpeedScl).
 *
 * 由 CopterUnitType.create() 创建, 在 update() 中驱动旋翼旋转,
 * 在 CopterUnitType.draw() 中渲染旋翼贴图.
 *
 * 死亡时旋翼减速并随机旋转方向, 模拟坠机效果.
 */
public class CopterUnitEntity extends UnitEntity {
    public RotorMount[] rotors = new RotorMount[0];
    public float rotorSpeedScl = 1f;

    public static CopterUnitEntity create() {
        return new CopterUnitEntity();
    }

    @Override
    public int classId() {
        return ZEntityRegister.classId(CopterUnitEntity.class);
    }

    @Override
    public void update() {
        super.update();

        // PU_V8 CopterComp.update: 死亡时旋翼减速并随机旋转方向, 存活时恢复
        if (dead || health < 0f) {
            rotation += 2.5f * Mathf.signs[id % 2] * Time.delta;
            rotorSpeedScl = Mathf.lerpDelta(rotorSpeedScl, 0f, 0.01f);
        } else {
            rotorSpeedScl = Mathf.lerpDelta(rotorSpeedScl, 1f, 0.01f);
        }

        // 驱动旋翼旋转
        for (RotorMount rotor : rotors) {
            rotor.rotorRot += rotor.rotor.speed * rotorSpeedScl * Time.delta;
            rotor.rotorRot %= 360f;

            rotor.rotorShadeRot += rotor.rotor.shadeSpeed * Time.delta;
            rotor.rotorShadeRot %= 360f;
        }
    }
}
