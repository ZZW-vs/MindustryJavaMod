package zzw.content.exp;

import arc.graphics.g2d.TextureRegion;
import arc.graphics.g2d.Draw;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.world.Block;

import static arc.Core.atlas;

/**
 * DrawOver 在方块默认 draw 之上叠加按等级选择的贴图 (移植自 PU_V8 unity.world.draw.DrawOver)
 *
 * 机制:
 * - load 时遍历查找 {block.name}1, {block.name}2, ... 贴图, 直到找不到为止
 * - draw 时按 levelf() (0~1) 在 levelRegions 数组中选一张贴图
 * - 若选中的就是 block.region 则跳过 (避免重绘)
 *
 * 用于 dirium-conveyor 等需要按等级显示不同贴图的方块
 */
public class DrawOver extends DrawLevel {
    public TextureRegion[] levelRegions;
    public float layer = Layer.blockOver;

    @Override
    public void load(Block block) {
        int n = 1;
        while (n <= 100) { // worst-case scenario
            TextureRegion t = atlas.find(block.name + n);
            if (!t.found()) break;
            n++;
        }
        if (n > 1) {
            levelRegions = new TextureRegion[n];
            levelRegions[0] = block.region;
            for (int i = 1; i < n; i++) {
                levelRegions[i] = atlas.find(block.name + i);
            }
        }
    }

    @Override
    public <T extends Building & LevelHolder> void draw(T build) {
        TextureRegion r = levelRegion(build);
        if (r != build.block.region) {
            Draw.z(layer);
            Draw.rect(r, build.x, build.y, build.block.rotate ? build.rotdeg() : 0);
        }
    }

    public <T extends Building & LevelHolder> TextureRegion levelRegion(T build) {
        if (levelRegions == null) return build.block.region;
        return levelRegions[Math.min((int) (build.levelf() * levelRegions.length), levelRegions.length - 1)];
    }
}
