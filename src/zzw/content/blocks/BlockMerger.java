package zzw.content.blocks;

import arc.util.Timer;
import arc.struct.IntSet;
import mindustry.Vars;
import mindustry.world.Tile;
import mindustry.world.Block;
import mindustry.game.Team;
import mindustry.content.Fx;

/**
 * 方块合并器: 将 2x2 的小方块自动合并成大方块
 * 性能优化: 使用 IntSet (整数位运算) 代替字符串 key, 减少 GC
 */
public class BlockMerger {
    // 用 tile 位置编码: (x << 16) | (y & 0xFFFF) 保存正在合并的位置
    private static final IntSet mergingPositions = new IntSet();
    private static final int[][] PATTERN_OFFSETS = {{0, 0}, {-1, 0}, {0, -1}, {-1, -1}};

    /**
     * 检查并替换方块
     * @param tile 触发检查的方块
     * @param smallBlock 小方块类型
     * @param largeBlock 大方块类型
     */
    public static void checkAndReplace(Tile tile, Block smallBlock, Block largeBlock) {
        int tx = tile.x, ty = tile.y;
        Team team = tile.team();
        // 检查 4 种可能的 2x2 起点
        for (int[] offset : PATTERN_OFFSETS) {
            if (tryMerge2x2(tx + offset[0], ty + offset[1], smallBlock, largeBlock, team)) {
                return;
            }
        }
    }

    private static boolean tryMerge2x2(int startX, int startY, Block smallBlock, Block largeBlock, Team team) {
        // 编码位置 key, 避免同一位置重复触发
        int positionKey = (startX << 16) | (startY & 0xFFFF);
        if (mergingPositions.contains(positionKey)) return false;

        int validBlocks = 0;
        int missingX = -1, missingY = -1;
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                Tile t = Vars.world.tile(startX + dx, startY + dy);
                if (t != null && t.block() == smallBlock && t.team() == team) {
                    validBlocks++;
                } else {
                    missingX = startX + dx;
                    missingY = startY + dy;
                }
            }
        }

        // 完整的 2x2 模式: 延迟执行合并
        if (validBlocks == 4) {
            mergingPositions.add(positionKey);

            // 播放烟雾特效
            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    Tile t = Vars.world.tile(startX + dx, startY + dy);
                    if (t != null) Fx.smoke.at(t.drawx(), t.drawy());
                }
            }

            // 延迟替换: 给玩家视觉反馈时间
            Timer.schedule(() -> {
                // 再次检查: 延迟期间方块可能被改
                boolean stillValid = true;
                for (int dx = 0; dx < 2; dx++) {
                    for (int dy = 0; dy < 2; dy++) {
                        Tile t = Vars.world.tile(startX + dx, startY + dy);
                        if (t == null || t.block() != smallBlock || t.team() != team) {
                            stillValid = false;
                            break;
                        }
                    }
                    if (!stillValid) break;
                }

                if (stillValid) {
                    // 移除 4 个小方块
                    for (int dx = 0; dx < 2; dx++) {
                        for (int dy = 0; dy < 2; dy++) {
                            Tile t = Vars.world.tile(startX + dx, startY + dy);
                            if (t != null) t.remove();
                        }
                    }
                    // 放置大方块
                    Tile origin = Vars.world.tile(startX, startY);
                    if (origin != null) {
                        origin.setBlock(largeBlock, team);
                        Fx.placeBlock.at(origin.drawx(), origin.drawy());
                    }
                }
                mergingPositions.remove(positionKey);
            }, 0.3f);

            return true;
        }

        // 3 个方块: 在空位可建造时, 播放提示特效
        if (validBlocks == 3) {
            Tile missingTile = (missingX >= 0) ? Vars.world.tile(missingX, missingY) : null;
            if (missingTile != null && (missingTile.block() == null || missingTile.block().replaceable)) {
                for (int dx = 0; dx < 2; dx++) {
                    for (int dy = 0; dy < 2; dy++) {
                        Tile t = Vars.world.tile(startX + dx, startY + dy);
                        if (t != null && t.block() == smallBlock && t.team() == team) {
                            Fx.pulverize.at(t.drawx(), t.drawy());
                        }
                    }
                }
            }
        }

        return false;
    }
}
