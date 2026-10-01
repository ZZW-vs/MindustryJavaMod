package zzw.content.blocks.modular;

/**
 * 零件属性类型 (PU132 younggamExperimental.PartStatType 移植)。
 *
 * <p>每一项对应 bundle 里的一条属性文案键, 供蓝图编辑 UI 显示零件属性。</p>
 */
public enum PartStatType{
    hp("stat.unity.hpinc"),
    mass("stat.unity.blademass"),
    collides("stat.unity.collides"),
    damage("stat.unity.bladedamage"),
    support("stat.unity.supports"),
    reload("stat.unity.reload"),
    heatAccumMult("stat.unity.heatAccumMult"),
    useTorque("stat.unity.usesTorque"),
    shaftSpd("stat.unity.shaftSpd"),
    bulletType("stat.unity.bulletType"),
    baseDmg("stat.unity.bulletDmg"),
    baseSpeed("stat.unity.bulletSpd"),
    ammoType("stat.unity.ammoType"),
    payload("stat.unity.payload"),
    magazine("stat.unity.magazine"),
    shots("stat.unity.shots"),
    reloadMultiplier("stat.unity.reloadMult"),
    spread("stat.unity.spread"),
    lifetime("stat.unity.lifetime"),
    heat("stat.unity.heatPerShot"),
    mod("stat.unity.mod"),
    radiate("stat.unity.heatRadiativity"),
    rangeinc("stat.unity.range");

    public static final PartStatType[] all = values();

    /** 属性文案键 */
    public final String name;

    PartStatType(String name){
        this.name = name;
    }
}