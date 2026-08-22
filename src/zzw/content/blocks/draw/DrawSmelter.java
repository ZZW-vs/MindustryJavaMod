package zzw.content.blocks.draw;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.draw.DrawBlock;
import mindustry.world.blocks.production.GenericCrafter.GenericCrafterBuild;

/**
 * 冶炼炉火焰绘制器 (Mindustry v132 mindustry.world.draw.DrawSmelter 移植)
 *
 * <p>v155.4 已移除该类 (改为 DrawArcSmelt 等), PU132 的暗合金/火花合金锻造厂
 * 使用它绘制火焰光晕。按 v132 原版源码原样移植。</p>
 *
 * <p>v155.4 适配: draw(GenericCrafterBuild) → draw(Building) (DrawBlock 签名变更)。</p>
 */
public class DrawSmelter extends DrawBlock{
    /** 火焰颜色 */
    public Color flameColor = Color.valueOf("ffc999");
    /** 顶部贴图 ({@code 方块名-top}) */
    public TextureRegion top;
    /** 光照参数 */
    public float lightRadius = 60f, lightAlpha = 0.65f, lightSinScl = 10f, lightSinMag = 5;
    /** 火焰圆半径参数 */
    public float flameRadius = 3f, flameRadiusIn = 1.9f, flameRadiusScl = 5f, flameRadiusMag = 2f, flameRadiusInMag = 1f;

    public DrawSmelter(){
    }

    public DrawSmelter(Color flameColor){
        this.flameColor = flameColor;
    }

    @Override
    public void load(Block block){
        top = Core.atlas.find(block.name + "-top");
        block.clipSize = Math.max(block.clipSize, (lightRadius + lightSinMag) * 2f * block.size);
    }

    @Override
    public TextureRegion[] icons(Block block){
        // v155.4: DrawBlock.icons 默认返回空数组 → finalIcons 填充 error 贴图 →
        // 图标生成阶段把 error 写进 atlas 的 "block-<名>-full" → 物品栏图标变错误贴图。
        // 按 v132 原版默认行为返回 {region}。
        return new TextureRegion[]{block.region};
    }

    @Override
    public void draw(Building build){
        // v155.4: warmup 是 GenericCrafterBuild 字段 (Building 基类为方法)
        GenericCrafterBuild b = (GenericCrafterBuild)build;
        Draw.rect(build.block.region, build.x, build.y, build.block.rotate ? build.rotdeg() : 0);

        if(b.warmup > 0f && flameColor.a > 0.001f){
            float g = 0.3f;
            float r = 0.06f;
            float cr = Mathf.random(0.1f);

            Draw.z(Layer.block + 0.01f);

            Draw.alpha(b.warmup);
            Draw.rect(top, build.x, build.y);

            Draw.alpha(((1f - g) + Mathf.absin(Time.time, 8f, g) + Mathf.random(r) - r) * b.warmup);

            Draw.tint(flameColor);
            Fill.circle(build.x, build.y, flameRadius + Mathf.absin(Time.time, flameRadiusScl, flameRadiusMag) + cr);
            Draw.color(1f, 1f, 1f, b.warmup);
            Fill.circle(build.x, build.y, flameRadiusIn + Mathf.absin(Time.time, flameRadiusScl, flameRadiusInMag) + cr);

            Draw.color();
        }
    }

    @Override
    public void drawLight(Building build){
        GenericCrafterBuild b = (GenericCrafterBuild)build;
        // v155.4: Drawf.light 无 Team 重载
        Drawf.light(build.x, build.y, (lightRadius + Mathf.absin(lightSinScl, lightSinMag)) * b.warmup * build.block.size, flameColor, lightAlpha);
    }
}
