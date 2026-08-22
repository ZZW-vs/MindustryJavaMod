package zzw.content.blocks.draw;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.draw.DrawBlock;
import mindustry.world.blocks.production.GenericCrafter.GenericCrafterBuild;

/**
 * 发光绘制器 (Mindustry v132 mindustry.world.draw.DrawGlow 移植)
 *
 * <p>v155.4 已移除该类 (合并为 DrawGlowRegion 等), 但 PU132 工厂大量使用
 * 其行为 (顶图随 warmup 呼吸闪烁), 这里按 v132 原版源码原样移植,
 * 供暗合金锻造厂 / 固化器 / 钢冶炼厂等匿名子类覆写使用。</p>
 *
 * <p>v155.4 适配: draw(GenericCrafterBuild) → draw(Building) (DrawBlock 签名变更)。</p>
 */
public class DrawGlow extends DrawBlock{
    /** 闪烁幅度 (0~1) */
    public float glowAmount = 0.9f, glowScale = 3f;
    /** 顶部发光贴图 ({@code 方块名-top}) */
    public TextureRegion top;

    @Override
    public void draw(Building build){
        // v155.4: warmup/totalProgress 是 GenericCrafterBuild 字段 (Building 基类为方法)
        GenericCrafterBuild b = (GenericCrafterBuild)build;
        Draw.rect(build.block.region, build.x, build.y);
        Draw.alpha(Mathf.absin(b.totalProgress, glowScale, glowAmount) * b.warmup);
        Draw.rect(top, build.x, build.y);
        Draw.reset();
    }

    @Override
    public void load(Block block){
        top = Core.atlas.find(block.name + "-top");
    }
}
