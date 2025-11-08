package zzw.structure;

import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Sounds;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OreBlock;
import zzw.content.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StructureDetector {
    private static final Map<String, StructurePattern> STRUCTURE_PATTERNS = new HashMap<>();
    private static boolean initialized = false;

    // 结构模式类，包含方块位置和类型要求
    public static class StructurePattern {
        public String name;
        public Map<String, List<BlockPosition>> pattern; // 使用方块类型作为键
        public String effectType; // 触发效果类型
        public Object effectParam; // 效果参数

        public StructurePattern(String name, String effectType, Object effectParam) {
            this.name = name;
            this.pattern = new HashMap<>();
            this.effectType = effectType;
            this.effectParam = effectParam;
        }

        public void addBlock(String blockType, int x, int y) {
            pattern.computeIfAbsent(blockType, k -> new ArrayList<>()).add(new BlockPosition(x, y));
        }
    }

    // 方块位置类
    public static class BlockPosition {
        public int x;
        public int y;

        public BlockPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // 初始化结构检测系统
    public static void initialize() {
        if (initialized) return;

        // 注册方块放置事件监听器
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (!event.breaking) {
                checkAllStructures(event.tile.x, event.tile.y);
            }
        });

        // 注册方块破坏事件监听器
        Events.on(EventType.BlockDestroyEvent.class, event -> {
            checkAllStructures(event.tile.x, event.tile.y);
        });

        initialized = true;
        Log.info("StructureDetector initialized");
    }

    // 注册结构模式
    public static void registerStructurePattern(StructurePattern pattern) {
        STRUCTURE_PATTERNS.put(pattern.name, pattern);
        Log.info("Registered structure pattern: " + pattern.name);
    }

    // 检查所有结构
    public static void checkAllStructures(int centerX, int centerY) {
        for (StructurePattern pattern : STRUCTURE_PATTERNS.values()) {
            if (checkPatternAt(centerX, centerY, pattern)) {
                triggerStructureEffect(centerX, centerY, pattern);
                // 移除return语句，允许多个结构同时触发
            }
        }
    }

    // 在指定位置检查特定结构模式
    private static boolean checkPatternAt(int centerX, int centerY, StructurePattern pattern) {
        // 遍历结构模式中的每种方块类型
        for (Map.Entry<String, List<BlockPosition>> entry : pattern.pattern.entrySet()) {
            String blockType = entry.getKey();
            List<BlockPosition> positions = entry.getValue();

            // 检查该类型方块的所有位置
            for (BlockPosition pos : positions) {
                int checkX = centerX + pos.x;
                int checkY = centerY + pos.y;

                // 对于大型方块（2x2），需要检查所有被占用的格子
                if (blockType.startsWith("large_")) {
                    // 检查大型方块占用的所有格子
                    for (int dx = 0; dx < 2; dx++) {
                        for (int dy = 0; dy < 2; dy++) {
                            if (!checkBlockType(checkX + dx, checkY + dy, blockType)) {
                                return false; // 如果任何一个位置不匹配，结构不成立
                            }
                        }
                    }
                } else {
                    // 普通方块只检查一个位置
                    if (!checkBlockType(checkX, checkY, blockType)) {
                        return false; // 如果任何一个位置不匹配，结构不成立
                    }
                }
            }
        }

        // 所有位置都匹配
        return true;
    }

    // 检查指定位置的方块类型
    private static boolean checkBlockType(int x, int y, String blockType) {
        Tile tile = Vars.world.tile(x, y);
        if (tile == null) return false;

        switch (blockType) {
            case "copper_wall":
                return tile.block() == Blocks.Copper_Wall;
            case "iron_wall":
                return tile.block() == Blocks.Iron_Wall;
            case "large_copper_wall":
                // 对于大型方块，需要检查是否是大型方块的一部分
                return tile.block() == Blocks.Large_Copper_Wall; 

            case "large_iron_wall":
                // 对于大型方块，需要检查是否是大型方块的一部分
                return tile.block() == Blocks.Large_Iron_Wall; 

            case "any_block":
                return tile.block() != null;
            case "empty":
                return tile.block() == null && !(tile.floor() instanceof OreBlock);
            default:
                return false;
        }
    }

    // 触发结构效果
    public static void triggerStructureEffect(int centerX, int centerY, StructurePattern pattern) {
        Log.info("Structure detected: " + pattern.name + " at (" + centerX + ", " + centerY + ")");

        switch (pattern.effectType) {
            case "spawn_unit":
                // 生成单位
                if (pattern.effectParam instanceof UnitType) {
                    UnitType unitType = (UnitType) pattern.effectParam;
                    // 确保服务器存在
                    if (Vars.netServer != null) {
                        // 确保玩家存在
                        if (Vars.player != null) {
                            Vars.netServer.unitSpawner.spawn(unitType, Vars.player.team(), centerX * 8, centerY * 8);
                        } else {
                            // 如果玩家不存在，使用默认队伍
                            Vars.netServer.unitSpawner.spawn(unitType, mindustry.content.Team.sharded, centerX * 8, centerY * 8);
                        }
                    }
                    Call.soundAt(Sounds.spawn, centerX * 8, centerY * 8, 1, 1);
                }
                break;

            case "explosion":
                // 创建爆炸效果
                if (pattern.effectParam instanceof Float) {
                    float radius = (Float) pattern.effectParam;
                    Call.effect(mindustry.content.Fx.explosion, centerX * 8, centerY * 8, radius, null);
                }
                break;

            case "message":
                // 显示消息
                if (pattern.effectParam instanceof String) {
                    if (Vars.player != null) {
                        Vars.player.sendMessage((String) pattern.effectParam);
                    } else {
                        // 如果玩家不存在，输出到日志
                        Log.info((String) pattern.effectParam);
                    }
                }
                break;

            case "replace_blocks":
                // 替换方块
                if (pattern.effectParam instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> replacements = (Map<String, String>) pattern.effectParam;
                    for (Map.Entry<String, List<BlockPosition>> entry : pattern.pattern.entrySet()) {
                        String blockType = entry.getKey();
                        if (replacements.containsKey(blockType)) {
                            String replacementType = replacements.get(blockType);
                            for (BlockPosition pos : entry.getValue()) {
                                int x = centerX + pos.x;
                                int y = centerY + pos.y;

                                // 对于大型方块，确保只在左上角替换
                                if (blockType.startsWith("large_")) {
                                    // 确保是大型方块的左上角
                                    Tile tile = Vars.world.tile(x, y);
                                    if (tile != null && tile.block() != null) {
                                        switch (replacementType) {
                                            case "copper_wall":
                                                tile.setBlock(Blocks.Copper_Wall);
                                                break;
                                            case "iron_wall":
                                                tile.setBlock(Blocks.Iron_Wall);
                                                break;
                                            case "large_copper_wall":
                                                tile.setBlock(Blocks.Large_Copper_Wall);
                                                break;
                                            case "large_iron_wall":
                                                tile.setBlock(Blocks.Large_Iron_Wall);
                                                break;
                                        }
                                    }
                                } else {
                                    // 普通方块直接替换
                                    Tile tile = Vars.world.tile(x, y);
                                    if (tile != null) {
                                        switch (replacementType) {
                                            case "copper_wall":
                                                tile.setBlock(Blocks.Copper_Wall);
                                                break;
                                            case "iron_wall":
                                                tile.setBlock(Blocks.Iron_Wall);
                                                break;
                                            case "large_copper_wall":
                                                tile.setBlock(Blocks.Large_Copper_Wall);
                                                break;
                                            case "large_iron_wall":
                                                tile.setBlock(Blocks.Large_Iron_Wall);
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                break;

            default:
                Log.warn("Unknown effect type: " + pattern.effectType);
        }
    }
}
