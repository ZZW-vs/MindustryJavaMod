package zzw.content.blocks;

import arc.util.Timer;
import arc.struct.ObjectSet;
import mindustry.Vars;
import mindustry.world.Tile;
import mindustry.world.Block;
import mindustry.game.Team;
import mindustry.content.Fx;

public class BlockMerger {
    private static final boolean MERGE_IN_PROGRESS = false;
    private static final ObjectSet<String> mergingPositions = new ObjectSet<>();
    private static Timer.Task delayedCheckTask = null;

    public static void checkAndReplace(Tile tile, Block smallBlock, Block largeBlock) {
        if (MERGE_IN_PROGRESS) return;

        if (delayedCheckTask != null) {
            delayedCheckTask.cancel();
        }

        checkAllPatterns(tile, smallBlock, largeBlock);

        delayedCheckTask = Timer.schedule(() -> {
            checkAllPatterns(tile, smallBlock, largeBlock);
            delayedCheckTask = null;
        }, 0.15f);
    }

    private static void checkAllPatterns(Tile tile, Block smallBlock, Block largeBlock) {
        boolean merged = false;
        merged |= check2x2Pattern(tile.x, tile.y, smallBlock, largeBlock, tile.team());
        merged |= check2x2Pattern(tile.x - 1, tile.y, smallBlock, largeBlock, tile.team());
        merged |= check2x2Pattern(tile.x, tile.y - 1, smallBlock, largeBlock, tile.team());
        merged |= check2x2Pattern(tile.x - 1, tile.y - 1, smallBlock, largeBlock, tile.team());
    }


    private static boolean check2x2Pattern(int startX, int startY, Block smallBlock, Block largeBlock, Team team) {
        String positionKey = startX + "," + startY;

        if (mergingPositions.contains(positionKey)) return false;

        boolean[][] pattern = new boolean[2][2];
        int validBlocks = 0;

        for(int dx = 0; dx < 2; dx++) {
            for(int dy = 0; dy < 2; dy++) {
                Tile other = Vars.world.tile(startX + dx, startY + dy);
                boolean isValid = other != null && other.block() == smallBlock && other.team() == team;
                pattern[dx][dy] = isValid;
                if (isValid) validBlocks++;
            }
        }

        if(pattern[0][0] && pattern[0][1] && pattern[1][0] && pattern[1][1]) {
            mergingPositions.add(positionKey);

            for(int dx = 0; dx < 2; dx++) {
                for(int dy = 0; dy < 2; dy++) {
                    Tile other = Vars.world.tile(startX + dx, startY + dy);
                    if(other != null) {
                        Fx.smoke.at(other.drawx(), other.drawy());
                    }
                }
            }

            Timer.schedule(() -> {
                boolean stillValid = true;
                for(int dx = 0; dx < 2; dx++) {
                    for(int dy = 0; dy < 2; dy++) {
                        Tile other = Vars.world.tile(startX + dx, startY + dy);
                        if(other == null || other.block() != smallBlock || other.team() != team) {
                            stillValid = false;
                            break;
                        }
                    }
                    if(!stillValid) break;
                }

                if(stillValid) {
                    for(int dx = 0; dx < 2; dx++) {
                        for(int dy = 0; dy < 2; dy++) {
                            Tile other = Vars.world.tile(startX + dx, startY + dy);
                            if(other != null) {
                                other.remove();
                            }
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

        if (validBlocks == 3) {
            int missingX = -1, missingY = -1;
            boolean canMerge = false;

            for(int dx = 0; dx < 2; dx++) {
                for(int dy = 0; dy < 2; dy++) {
                    if (!pattern[dx][dy]) {
                        missingX = startX + dx;
                        missingY = startY + dy;
                        break;
                    }
                }
                if (missingX != -1) break;
            }

            if (missingX != -1) {
                Tile missingTile = Vars.world.tile(missingX, missingY);
                if (missingTile != null && (missingTile.block() == null || missingTile.block().replaceable)) {
                    canMerge = true;
                }
            }

            if (canMerge) {
                for(int dx = 0; dx < 2; dx++) {
                    for(int dy = 0; dy < 2; dy++) {
                        Tile other = Vars.world.tile(startX + dx, startY + dy);
                        if(other != null && pattern[dx][dy]) {
                            Fx.pulverize.at(other.drawx(), other.drawy());
                        }
                    }
                }
            }
        }

        return false;
    }
}
