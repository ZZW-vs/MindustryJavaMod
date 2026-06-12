package zzw.content.mechanics;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.world.blocks.production.GenericCrafter;

public class FactoryBoost {
    private static final float BOOST_RANGE = 3f;
    private static final float BOOST_MULTIPLIER = 0.25f;
    private static final float MAX_BOOST = 1.5f;
    // 缓存刷新间隔（秒），避免每帧扫描 49 格
    private static final float REFRESH_INTERVAL = 0.5f;

    private static int transmissionBoxesAround(Building building) {
        int count = 0;
        int r = (int) BOOST_RANGE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                if (dx * dx + dy * dy > r * r) continue;
                Building other = Vars.world.tile(building.tileX() + dx, building.tileY() + dy).build;
                if (other != null && other.team == building.team
                        && "transmission_box".equals(other.block.name)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static float getBoostMultiplier(Building building) {
        if (!(building.block instanceof GenericCrafter)) return 1f;
        return Mathf.clamp(1f + transmissionBoxesAround(building) * BOOST_MULTIPLIER, 1f, MAX_BOOST);
    }

    public static class BoostedGenericCrafter extends GenericCrafter {
        public BoostedGenericCrafter(String name) {
            super(name);
        }

        public BoostedGenericCrafterBuild newBuild() {
            return new BoostedGenericCrafterBuild();
        }

        public class BoostedGenericCrafterBuild extends GenericCrafterBuild {
            private float nextRefresh = 0f;
            private float cachedMultiplier = 1f;

            @Override
            public void updateTile() {
                if (efficiency > 0) {
                    if (arc.util.Time.time >= nextRefresh) {
                        cachedMultiplier = getBoostMultiplier(this);
                        nextRefresh = arc.util.Time.time + REFRESH_INTERVAL;
                    }
                    progress += edelta() * cachedMultiplier * efficiency;
                    warmup = Mathf.lerpDelta(warmup, 1f, 0.02f);
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
        }
    }
}
