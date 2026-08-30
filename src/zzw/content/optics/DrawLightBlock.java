package zzw.content.optics;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.util.Nullable;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.draw.DrawBlock;

import static arc.Core.atlas;

/**
 * 灯方块 drawer (PU132 unity.world.draw.DrawLightBlock 移植)
 * <p>画 base + 液体条 + 按光朝向旋转的本体。</p>
 *
 * <p>★ v155.4 修复: DrawBlock.draw(Building) 是空默认方法 (非抽象),
 * 之前只覆写了 draw(GenericCrafterBuild) 重载 —— 没有任何调用方走它,
 * 导致灯放置后整体透明 (图标走 icons() 所以正常);
 * 必须覆写 draw(Building)。drawPlan 同理 (默认空 → 放置预览透明)。</p>
 */
public class DrawLightBlock extends DrawBlock {
    public TextureRegion baseRegion, liquidRegion;

    @Override
    public void draw(Building build) {
        Draw.rect(baseRegion, build.x, build.y);

        // 液体条 (油灯): 有液体容量且有液体时画
        if (build.liquids != null && build.block.hasLiquids && liquidRegion.found()) {
            Draw.color(build.liquids.current().color);
            Draw.alpha(build.liquids.currentAmount() / build.block.liquidCapacity);
            Draw.rect(liquidRegion, build.x, build.y);
            Draw.color();
        }

        Draw.rect(build.block.region, build.x, build.y,
            build.block instanceof LightHoldBlock hold ? (hold.getRotation(build) - 90f) : 0f);
    }

    @Override
    public void drawPlan(Block block, BuildPlan plan, arc.util.Eachable<BuildPlan> list) {
        // 放置预览 (同项目 DrawSmelter 方案: 委托默认预览)
        block.drawDefaultPlanRegion(plan, list);
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