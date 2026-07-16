package zzw.content.units.rotor;

/**
 * 旋翼运行时数据 (移植自 PU_V8 unity.entities.RotorMount)
 */
public class RotorMount {
    public final Rotor rotor;
    public float rotorRot;
    public float rotorShadeRot;

    public RotorMount(Rotor rotor) {
        this.rotor = rotor;
    }
}
