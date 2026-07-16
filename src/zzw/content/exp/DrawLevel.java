package zzw.content.exp;

import mindustry.gen.Building;
import mindustry.world.Block;

/**
 * PU_V8 DrawLevel (基类, 可被ExpTurret.draw引用绘制附加层级)
 * 参考: PU_V8 main/src/unity/world/draw/DrawLevel.java
 */
public class DrawLevel {
    /** Draws before the block's draw. */
    public <T extends Building & LevelHolder> void draw(T build){
    }

    /** Draws any extra light for the block. */
    public <T extends Building & LevelHolder> void drawLight(T build){
    }

    /** Load any relevant texture regions. */
    public void load(Block block){
    }
}
