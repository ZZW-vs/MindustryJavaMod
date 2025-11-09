package zzw.content.blocks;

import arc.util.Timer;
import mindustry.Vars;
import mindustry.world.Tile;
import mindustry.world.Block;
import mindustry.game.Team;
import mindustry.content.Fx;
import mindustry.entities.Effect;

public class BlockMerger {
    // 添加一个延迟标记，防止立即重复合并
    private static boolean mergeInProgress = false;
    
    public static void checkAndReplace(Tile tile, Block smallBlock, Block largeBlock) {
        // 如果正在处理合并，则跳过
        if (mergeInProgress) return;
        
        // 检查所有可能的2x2组合
        // 检查以当前方块为左上角的2x2
        if (check2x2Pattern(tile.x, tile.y, smallBlock, largeBlock, tile.team())) return;
        // 检查以当前方块为右上角的2x2
        if (check2x2Pattern(tile.x - 1, tile.y, smallBlock, largeBlock, tile.team())) return;
        // 检查以当前方块为左下角的2x2
        if (check2x2Pattern(tile.x, tile.y - 1, smallBlock, largeBlock, tile.team())) return;
        // 检查以当前方块为右下角的2x2
        if (check2x2Pattern(tile.x - 1, tile.y - 1, smallBlock, largeBlock, tile.team())) return;
    }
    
    private static boolean check2x2Pattern(int startX, int startY, Block smallBlock, Block largeBlock, Team team) {
        // 检查是否形成2x2的墙
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

        // 如果形成2x2，替换为大墙
        if(pattern[0][0] && pattern[0][1] && pattern[1][0] && pattern[1][1]) {
            // 设置合并标记，防止重复合并
            mergeInProgress = true;
            
            // 添加视觉效果，使合并过程更明显
            for(int dx = 0; dx < 2; dx++) {
                for(int dy = 0; dy < 2; dy++) {
                    Tile other = Vars.world.tile(startX + dx, startY + dy);
                    if(other != null) {
                        // 创建合并特效
                        Fx.smoke.at(other.drawx(), other.drawy());
                    }
                }
            }
            
            // 延迟执行合并，让特效有时间显示
            Timer.schedule(() -> {
                // 移除原有的4个小墙
                for(int dx = 0; dx < 2; dx++) {
                    for(int dy = 0; dy < 2; dy++) {
                        Tile other = Vars.world.tile(startX + dx, startY + dy);
                        if(other != null) {
                            other.remove();
                        }
                    }
                }
                
                // 放置大墙，并设置团队
                Tile largeTile = Vars.world.tile(startX, startY);
                largeTile.setBlock(largeBlock, team);
                
                // 添加放置特效
                Fx.placeBlock.at(largeTile.drawx(), largeTile.drawy());
                
                // 重置合并标记
                mergeInProgress = false;
            }, 0.3f);
            
            return true;
        }
        
        // 如果有3个方块，检查剩余位置是否为空，如果是则给玩家视觉提示
        if (validBlocks == 3) {
            // 找出缺失的方块位置
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
            
            // 检查缺失位置是否为空或可以放置方块
            if (missingX != -1) {
                Tile missingTile = Vars.world.tile(missingX, missingY);
                // 只有当缺失位置为空或可以放置方块时才提示
                if (missingTile != null && (missingTile.block() == null || missingTile.block().replaceable)) {
                    canMerge = true;
                }
            }
            
            // 只有当可以合并时才显示高亮效果
            if (canMerge) {
                for(int dx = 0; dx < 2; dx++) {
                    for(int dy = 0; dy < 2; dy++) {
                        Tile other = Vars.world.tile(startX + dx, startY + dy);
                        if(other != null && pattern[dx][dy]) {
                            // 给已有方块添加微光效果，提示玩家差一个就能合并
                            Fx.spark.at(other.drawx(), other.drawy());
                        }
                    }
                }
            }
        }
        
        return false;
    }
}
