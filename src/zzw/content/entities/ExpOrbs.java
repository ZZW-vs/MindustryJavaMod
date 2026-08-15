package zzw.content.entities;

import mindustry.gen.Groups;

/** 经验球工具 (PU132 unity.entities.ExpOrbs 简化移植) */
public class ExpOrbs{
    /** 在指定位置散播经验球，附近的经验持有者会接收 */
    public static void spreadExp(float x, float y, float amount, float range){
        if(amount <= 0) return;
        // 简化版: 直接把经验分配给范围内的 ExpHolder
        float[] remaining = {amount};
        Groups.build.intersect(x - range, y - range, range * 2f, range * 2f, b -> {
            if(remaining[0] > 0 && b instanceof ExpHolder holder && holder.acceptOrb()){
                int gave = holder.handleExp((int)remaining[0]);
                remaining[0] -= gave;
            }
        });
    }
}
