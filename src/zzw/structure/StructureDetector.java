package zzw.structure;

import mindustry.core.World;
import mindustry.world.Tile;
import java.util.HashMap;
import java.util.Map;

public class StructureDetector {
    private static final Map<String, int[]> STRUCTURE_PATTERNS = new HashMap<>();

    public static void registerStructurePattern(String structureName, int... positions) {
        STRUCTURE_PATTERNS.put(structureName, positions);
    }

    public static boolean checkStructure(World world, int pos) {
        Tile tile = world.tile(pos);
        if(tile == null) return false;

        for(Map.Entry<String, int[]> entry : STRUCTURE_PATTERNS.entrySet()) {
            if(checkPattern(world, tile, entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkPattern(World world, Tile center, int[] pattern) {
        for(int i = 0; i < pattern.length; i += 2) {
            Tile checkTile = world.tile(center.x + pattern[i], center.y + pattern[i + 1]);
            if(checkTile == null || checkTile.block() == null) {
                return false;
            }
        }
        return true;
    }

    public static void triggerStructureEffect(World world, int pos, String structureName) {
        // 实现结构触发的效果
        // 比如生成特殊单位、播放特效等
    }
}
