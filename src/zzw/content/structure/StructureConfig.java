package zzw.content.structure;

import arc.util.Log;
import mindustry.content.UnitTypes;
import mindustry.type.UnitType;
import zzw.content.Blocks;

import java.util.HashMap;
import java.util.Map;

public class StructureConfig {
    public static void registerDefaultStructures() {
        // 注册铜墙十字结构 - 检测到时生成爆炸效果 registerCopperCross();

        // 注册铁墙T形结构 - 检测到时显示消息 registerIronT();

        // 注册大型铜墙方块结构 - 检测到时替换为铁墙 registerCopperSquare();

        // 注册混合墙结构 - 检测到时生成单位（如果可用）
        registerMixedStructure();

        Log.info("Default structures registered successfully");
    }

    private static void registerMixedStructure() {
        StructureDetector.StructurePattern pattern = new StructureDetector.StructurePattern(
                "mixed_structure", "spawn_unit", UnitTypes.beta);
        pattern.addBlock("iron_wall",0,0); // 中心
        pattern.addBlock("copper_wall", -1, -1); // 左下
        pattern.addBlock("copper_wall",1, -1); // 右下
        pattern.addBlock("iron_wall", -1,1); // 左上
        pattern.addBlock("iron_wall",1,1); // 右上
        StructureDetector.registerStructurePattern(pattern);
    }

    private static void registerCopperCross() {
        StructureDetector.StructurePattern pattern = new StructureDetector.StructurePattern(
                "copper_cross", "explosion",2.0f);
        pattern.addBlock("copper_wall", 0, 0); // 中心
        pattern.addBlock("copper_wall", -1, 0); // 左
        pattern.addBlock("copper_wall", 1, 0); // 右
        pattern.addBlock("copper_wall", 0, 1); // 上
        pattern.addBlock("copper_wall", 0, -1); // 下
        StructureDetector.registerStructurePattern(pattern);
    }

    private static void registerIronT() {
        StructureDetector.StructurePattern pattern = new StructureDetector.StructurePattern(
                "iron_t", "message", "检测到铁墙T形结构！");
        pattern.addBlock("iron_wall", 0, 0); // 中心
        pattern.addBlock("iron_wall", -1, 0); // 左
        pattern.addBlock("iron_wall", 1, 0); // 右
        pattern.addBlock("iron_wall", 0, 1); // 上
        StructureDetector.registerStructurePattern(pattern);
    }

    private static void registerCopperSquare() {
        Map<String, String> copperToIron = new HashMap<>();
        copperToIron.put("large_copper_wall", "large_iron_wall");

        StructureDetector.StructurePattern pattern = new StructureDetector.StructurePattern(
                "copper_square", "replace_blocks", copperToIron);
        pattern.addBlock("large_copper_wall",0,0);
        pattern.addBlock("large_copper_wall",2,0);
        pattern.addBlock("large_copper_wall",0,2);
        pattern.addBlock("large_copper_wall",2,2);
        StructureDetector.registerStructurePattern(pattern);
    }


}
