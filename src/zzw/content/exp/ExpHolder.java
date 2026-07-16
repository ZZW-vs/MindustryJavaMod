package zzw.content.exp;

import mindustry.gen.Building;

/**
 * PU_V8 ExpHolder 接口 (经验持有者)
 * 参考: PU_V8 main/src/unity/world/blocks/exp/ExpHolder.java
 */
public interface ExpHolder {
    int getExp();
    int handleExp(int amount);

    default int unloadExp(int amount){
        return 0;
    }

    default boolean handleOrb(int orbExp){
        return handleExp(orbExp) > 0;
    }

    default boolean acceptOrb(){
        return false;
    }

    default int handleTower(int amount, float angle){
        return handleExp(amount);
    }

    default boolean hubbable(){
        return false;
    }

    default boolean canHub(Building build){
        return false;
    }

    default void setHub(ExpHub.ExpHubBuild build){
    }
}
