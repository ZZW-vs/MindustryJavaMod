package zzw.content.mechanics;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.world.blocks.production.GenericCrafter;


/**
 * 工厂加速功能
 * 当工厂旁边有传动箱时，加快工厂的生产速度
 */
public class FactoryBoost {
    // 传动箱加速范围
    private static final float BOOST_RANGE = 3f;
    // 每个传动箱提供的加速倍数
    private static final float BOOST_MULTIPLIER = 0.25f;
    // 最大加速倍数
    private static final float MAX_BOOST = 1.5f;

    /**
     * 获取工厂的加速倍数
     * @param building 工厂建筑
     * @return 加速倍数
     */
    public static float getBoostMultiplier(Building building) {
        if (!(building.block instanceof GenericCrafter)) {
            return 1f;
        }

        int transmissionBoxes = 0;

        // 检查工厂周围是否有传动箱
        for (int x = (int) (building.x - BOOST_RANGE); x <= building.x + BOOST_RANGE; x++) {
            for (int y = (int) (building.y - BOOST_RANGE); y <= building.y + BOOST_RANGE; y++) {
                if (Mathf.dst(x, y, building.x, building.y) <= BOOST_RANGE) {
                    Building other = Vars.world.tile(x, y).build;
                    if (other != null && other.team == building.team && 
                        other.block.name.equals("transmission_box")) {
                        transmissionBoxes++;
                    }
                }
            }
        }

        // 计算加速倍数
        float boost = 1f + (transmissionBoxes * BOOST_MULTIPLIER);
        return Mathf.clamp(boost, 1f, MAX_BOOST);
    }

    /**
     * 重写GenericCrafter的Build类，添加加速功能
     */
    public static class BoostedGenericCrafter extends GenericCrafter {
        public BoostedGenericCrafter(String name) {
            super(name);
        }

        public BoostedGenericCrafterBuild newBuild() {
            return new BoostedGenericCrafterBuild();
        }

        public class BoostedGenericCrafterBuild extends GenericCrafterBuild {
            @Override
            public void updateTile() {
                // 更新生产进度时应用加速倍数
                if (efficiency > 0) {
                    progress += edelta() * getSpeedMultiplier() * efficiency;
                    warmup = Mathf.lerpDelta(warmup, efficiency > 0 ? 1f : 0f, 0.02f);
                } else {
                    warmup = Mathf.lerpDelta(warmup, 0f, 0.02f);
                }
                
                if (progress >= 1f) {
                    consume();
                    craft();
                    progress %= 1f;
                }
                
                dumpOutputs();
            }
            
            /**
             * 获取加速倍数
             * @return 加速倍数
             */
            private float getSpeedMultiplier() {
                return FactoryBoost.getBoostMultiplier(this);
            }
        }
    }
}
