package zzw.content.optics;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import mindustry.graphics.Layer;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.draw.DrawBlock;

import static arc.Core.atlas;

/**
 * 灯方块 drawer (PU132 unity.world.draw.DrawLightBlock 移植)
 * <p>画 base + 液体条 + 按光朝向旋转的本体。</p>
 */
public class DrawLightBlock extends DrawBlock {
    public TextureRegion baseRegion, liquidRegion;

    public void draw(GenericCrafter.GenericCrafterBuild build) {
        Draw.rect(baseRegion, build.x, build.y);

        // 液体条 (油灯): 有液体消费者且有液体时画
        if (build.liquids != null && build.block.hasLiquids) {
            Draw.color(build.liquids.current().color);
            Draw.alpha(build.liquids.currentAmount() / build.block.liquidCapacity);
            Draw.rect(liquidRegion, build.x, build.y);
            Draw.color();
        }

        Draw.rect(build.block.region, build.x, build.y,
            build.block instanceof LightHoldBlock hold ? (hold.getRotation(build) - 90f) : 0f);
    }

    @Override
    public void load(Block block) {
        baseRegion = atlas.find(block.name + "-base");
        liquidRegion = atlas.find(block.name + "-liquid");
    }

    @Override
    public TextureRegion[] icons(Block block) {
        return new TextureRegion[]{baseRegion, block.region};
    }
}