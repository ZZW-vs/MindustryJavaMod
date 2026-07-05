package zzw.content.mechanics;

import arc.Events;
import arc.math.Mathf;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.world.blocks.production.GenericCrafter;

/**
 * 工厂加速系统 - 事件驱动 + 周期性回退扫描
 * 
 * 逻辑:
 * - 放置传动箱/工厂: 立即扫描附近工厂 (精准, 无浪费)
 * - 每 5 秒: 所有工厂回退扫描一次 (兜底, 处理拆除/爆炸/未知变化)
 * - 相比每 0.5 秒全量扫描, 开销降低约 10 倍
 */
public class FactoryBoost {
    private static final float BOOST_RANGE = 3f;        // 加速半径 3 格
    private static final float BOOST_MULTIPLIER = 0.25f; // 每个传动箱 +25%
    private static final float MAX_BOOST = 1.5f;        // 上限 1.5 倍
    private static final float REFRESH_INTERVAL = 5f;   // 回退扫描间隔 (秒)

    // 静态初始化: 注册事件监听
    static {
        Events.on(EventType.BlockBuildEndEvent.class, e -> {
            if (e.tile == null) return;
            // 放置传动箱: 通知附近工厂
            if (!e.breaking && e.tile.build != null
                    && e.tile.build instanceof MechanicalBuilds.TransmissionBoxBuild) {
                notifyNearbyFactories(e.tile.x, e.tile.y);
            }
            // 放置工厂: 立即计算加速
            if (!e.breaking && e.tile.build != null
                    && e.tile.build instanceof BoostedGenericCrafter.BoostedGenericCrafterBuild factory) {
                factory.recalcBoost();
            }
        });
    }

    // 通知范围内工厂重算加速
    private static void notifyNearbyFactories(int tx, int ty) {
        int r = (int) BOOST_RANGE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                if (dx * dx + dy * dy > r * r) continue;
                Building b = mindustry.Vars.world.build(tx + dx, ty + dy);
                if (b instanceof BoostedGenericCrafter.BoostedGenericCrafterBuild factory) {
                    factory.recalcBoost();
                }
            }
        }
    }

    // 在指定坐标附近数传动箱
    private static int countTransmissionBoxes(int cx, int cy) {
        int r = (int) BOOST_RANGE;
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                if (dx * dx + dy * dy > r * r) continue;
                Building b = mindustry.Vars.world.build(cx + dx, cy + dy);
                if (b instanceof MechanicalBuilds.TransmissionBoxBuild) count++;
            }
        }
        return count;
    }

    // ==================== 方块定义 ====================

    public static class BoostedGenericCrafter extends GenericCrafter {
        public BoostedGenericCrafter(String name) {
            super(name);
        }

        public BoostedGenericCrafterBuild newBuild() {
            return new BoostedGenericCrafterBuild();
        }

        public class BoostedGenericCrafterBuild extends GenericCrafterBuild {
            private int cachedBoxes = 0;
            private float cachedMultiplier = 1f;
            private float nextRefresh = 0f;

            // 立即重算: 事件驱动时调用
            public void recalcBoost() {
                cachedBoxes = countTransmissionBoxes(tileX(), tileY());
                cachedMultiplier = Mathf.clamp(1f + cachedBoxes * BOOST_MULTIPLIER, 1f, MAX_BOOST);
                nextRefresh = arc.util.Time.time + REFRESH_INTERVAL;
            }

            @Override
            public void updateTile() {
                if (efficiency > 0) {
                    // 周期性回退扫描: 每 5 秒一次 (作为兜底)
                    if (arc.util.Time.time >= nextRefresh) {
                        recalcBoost();
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
