package zzw.content.blocks;

import mindustry.Vars;
import mindustry.world.Tile;
import mindustry.world.Block;

public class BlockMerger {
    public static void checkAndReplace(Tile tile, Block smallBlock, Block largeBlock) {
        int x = tile.x;
        int y = tile.y;

        // 检查是否形成2x2的墙
        boolean[][] pattern = new boolean[2][2];
        for(int dx = 0; dx < 2; dx++) {
            for(int dy = 0; dy < 2; dy++) {
                Tile other = Vars.world.tile(x + dx, y + dy);
                pattern[dx][dy] = other != null && other.block() == smallBlock && other.team() == tile.team();
            }
        }

        // 如果形成2x2，替换为大墙
        if(pattern[0][0] && pattern[0][1] && pattern[1][0] && pattern[1][1]) {
            // 移除原有的4个小墙
            for(int dx = 0; dx < 2; dx++) {
                for(int dy = 0; dy < 2; dy++) {
                    Tile other = Vars.world.tile(x + dx, y + dy);
                    if(other != null) {
                        other.remove();
                    }
                }
            }
            // 放置大墙
            Vars.world.tile(x, y).setBlock(largeBlock, tile.team());
        }
    }
}
