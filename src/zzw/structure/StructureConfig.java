package zzw.structure;

public class StructureConfig {
    public static void registerDefaultStructures() {
        StructureDetector.registerStructurePattern("copper_structure",
                0, 0,   // 中心
                -1, 0,  // 左
                1, 0,   // 右
                0, 1    // 上
        );

        // 可以继续添加其他结构模式
        StructureDetector.registerStructurePattern("iron_structure",
                0, 0,   // 中心
                -1, 0,  // 左
                1, 0,   // 右
                0, 1,   // 上
                0, -1   // 下
        );
    }
}
