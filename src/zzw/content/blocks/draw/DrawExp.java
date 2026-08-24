package zzw.content.blocks.draw;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Eachable;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.draw.DrawBlock;
import mindustry.world.blocks.production.GenericCrafter.GenericCrafterBuild;
import zzw.content.blocks.exp.KoruhCrafter;
import zzw.content.graphics.UnityPal;

import static arc.Core.atlas;

/**
 * 经验工厂绘制器 (PU132 unity.world.draw.DrawExp 移植)
 *
 * <p>经验条叠加层 (经验占比的呼吸发光) + 火焰顶图。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>unity.graphics.UnityPal → zzw.content.graphics.UnityPal</li>
 *   <li>KoruhCrafter.KoruhCrafterBuild → zzw.content.blocks.exp.KoruhCrafter (本项目包路径)</li>
 *   <li>v155.4: draw(GenericCrafterBuild) → draw(Building) (DrawBlock 签名变更)</li>
 * </ul></p>
 */
public class DrawExp extends DrawBlock{
    /** 经验叠加贴图 ({@code 方块名-exp}) */
    public TextureRegion exp, top;
    /** 发光幅度与闪烁频率 */
    public float glowAmount = 0.9f, glowScale = 8f;
    /** 火焰颜色 */
    public Color flame = Color.yellow;

    @Override
    public void draw(Building build){
        // v155.4: warmup/totalProgress 是 GenericCrafterBuild 字段 (Building 基类为方法)
        GenericCrafterBuild b = (GenericCrafterBuild)build;
        Draw.rect(build.block.region, build.x, build.y);
        if(exp.found() && build instanceof KoruhCrafter.KoruhCrafterBuild kr){
            Draw.color(UnityPal.exp, Color.white, Mathf.absin(20, 0.6f));
            Draw.alpha(kr.expf());
            Draw.rect(exp, build.x, build.y);
        }

        if(top.found()){
            Draw.color(flame);
            Draw.alpha(Mathf.absin(b.totalProgress, glowScale, glowAmount) * b.warmup);
            Draw.rect(top, build.x, build.y);
        }
        Draw.reset();
    }

    @Override
    public void load(Block block){
        exp = atlas.find(block.name + "-exp");
        top = atlas.find(block.name + "-top");
    }

    @Override
    public TextureRegion[] icons(Block block){
        // v155.4: DrawBlock.icons 默认返回空数组 → finalIcons 填充 error 贴图 →
        // 图标生成阶段把 error 写进 atlas 的 "block-<名>-full" → 物品栏图标变错误贴图。
        // 按 v132 原版默认行为返回 {region}。
        return new TextureRegion[]{block.region};
    }

    @Override
    public void drawPlan(Block block, BuildPlan plan, Eachable<BuildPlan> list){
        // v158: GenericCrafter.drawPlanRegion 委托给 drawer.drawPlan, DrawBlock 默认为空
        // → 放置时无跟随鼠标的方块预览; 委托回默认预览 (与 DrawDefault 一致)
        block.drawDefaultPlanRegion(plan, list);
    }
}
