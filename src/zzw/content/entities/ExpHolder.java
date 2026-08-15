package zzw.content.entities;

/** 经验持有者接口 (PU132 unity.entities.ExpHolder 移植) */
public interface ExpHolder{
    int getExp();
    int handleExp(int amount);
    int unloadExp(int amount);
    boolean acceptOrb();
    boolean handleOrb(int orbExp);
}
