package zzw.content.blocks.modular;

import arc.func.Cons;

/**
 * 零件属性 (PU132 younggamExperimental.PartStat 移植)。
 *
 * <p>一条 {@link PartStatType} 对应的数值/字符串/布尔属性, 可附带一个修改器。</p>
 */
public class PartStat{
    public final PartStatType category;
    final Object value;
    final Cons mod;

    public PartStat(PartStatType category, Object value, Cons mod){
        this.category = category;
        this.value = value;
        this.mod = mod;
    }

    public PartStat(PartStatType category, Object value){
        this(category, value, null);
    }

    public String asString(){
        return (String)value;
    }

    public float asFloat(){
        return (float)value;
    }

    public int asInt(){
        return (int)value;
    }

    public boolean asBool(){
        return (boolean)value;
    }
}