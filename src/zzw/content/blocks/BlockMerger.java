package zzw.content.blocks;

import arc.util.Timer;
import arc.struct.ObjectSet;
import mindustry.Vars;
import mindustry.world.Tile;
import mindustry.world.Block;
import mindustry.game.Team;
import mindustry.content.Fx;

/**
 * 方块合并器
 * 负责将2x2的小方块合并成大方块
 */
public class BlockMerger {
    private static final ObjectSet<String> mergingPositions = new ObjectSet<>();
    private static Timer.Task delayedCheckTask = null;

    /**
     * 检查并替换方块
     * @param tile 触发检查的方块
     * @param smallBlock 小方块类型
     * @param largeBlock 大方块类型
     */
    public static void checkAndReplace(Tile tile, Block smallBlock, Block largeBlock) {
        if (delayedCheckTask != null) delayedCheckTask.cancel();
        checkAllPatterns(tile, smallBlock, largeBlock);

        delayedCheckTask = Timer.schedule(() -> {
            checkAllPatterns(tile, smallBlock, largeBlock);
            delayedCheckTask = null;
        }, 0.15f);
    }

    private static final int[][] PATTERN_OFFSETS = {{0, 0}, {-1, 0}, {0, -1}, {-1, -1}};

    private static void checkAllPatterns(Tile tile, Block smallBlock, Block largeBlock) {
        int tx = tile.x, ty = tile.y;
        Team team = tile.team();
        for (int[] offset : PATTERN_OFFSETS) {
            if (check2x2Pattern(tx + offset[0], ty + offset[1], smallBlock, largeBlock, team)) return;
        }
    }

    private static boolean allSmallBlocks(int startX, int startY, Block smallBlock, Team team) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                Tile t = Vars.world.tile(startX + dx, startY + dy);
                if (t == null || t.block() != smallBlock || t.team() != team) return false;
            }
        }
        return true;
    }

    private static boolean check2x2Pattern(int startX, int startY, Block smallBlock, Block largeBlock, Team team) {
        String positionKey = startX + "," + startY;
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

        // 完整的2x2模式，执行合并
        if (validBlocks == 4) {
            mergingPositions.add(positionKey);

            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    Tile t = Vars.world.tile(startX + dx, startY + dy);
                    if (t != null) Fx.smoke.at(t.drawx(), t.drawy());
                }
            }

            Timer.schedule(() -> {
                if (allSmallBlocks(startX, startY, smallBlock, team)) {
                    for (int dx = 0; dx < 2; dx++) {
                        for (int dy = 0; dy < 2; dy++) {
                            Tile t = Vars.world.tile(startX + dx, startY + dy);
                            if (t != null) t.remove();
                        }
                    }
                    Tile largeTile = Vars.world.tile(startX, startY);
                    largeTile.setBlock(largeBlock, team);
                    Fx.placeBlock.at(largeTile.drawx(), largeTile.drawy());
                }
                mergingPositions.remove(positionKey);
            }, 0.3f);

            return true;
        }

        // 3 个方块的情况：在空位可替换时显示提示
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
