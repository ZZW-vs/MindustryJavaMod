package zzw.content.blocks;

import arc.util.Timer;
import arc.struct.ObjectSet;
import mindustry.Vars;
import mindustry.world.Tile;
import mindustry.world.Block;
import mindustry.game.Team;
import mindustry.content.Fx;

public class BlockMerger {
    // 添加一个延迟标记，防止立即重复合并
    private static final boolean MERGE_IN_PROGRESS = false;

    // 记录正在处理合并的位置，防止重复处理
    private static final ObjectSet<String> mergingPositions = new ObjectSet<>();
    
    // 延迟检查任务，用于快速建造情况
    private static Timer.Task delayedCheckTask = null;
    
    public static void checkAndReplace(Tile tile, Block smallBlock, Block largeBlock) {
        // 如果正在处理合并，则跳过
        if (MERGE_IN_PROGRESS) return;
        
        // 取消之前的延迟检查任务（如果有）
        if (delayedCheckTask != null) {
            delayedCheckTask.cancel();
        }
        
        // 立即检查当前方块
        checkAllPatterns(tile, smallBlock, largeBlock);
        
        // 设置延迟检查，以处理快速建造的情况
        delayedCheckTask = Timer.schedule(() -> {
            // 再次检查所有可能的2x2组合
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
        // 创建位置键，用于检查是否正在处理此位置的合并
        String positionKey = startX + "," + startY;
        
        // 如果此位置正在处理合并，则跳过
        if (mergingPositions.contains(positionKey)) return false;
        
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
            // 标记此位置正在处理合并
            mergingPositions.add(positionKey);
            
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
                // 再次检查方块是否仍然存在且未被其他操作改变
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
                
                // 只有当仍然有效时才执行合并
                if(stillValid) {
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
                }
                
                // 从处理集合中移除此位置
                mergingPositions.remove(positionKey);
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
                            Fx.pulverize.at(other.drawx(), other.drawy());
                        }
                    }
                }
            }
        }
        
        return false;
    }
}
