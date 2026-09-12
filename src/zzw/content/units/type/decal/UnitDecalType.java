package zzw.content.units.type.decal;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import mindustry.gen.Unit;
import zzw.content.units.util.UnityUtils;

/**
 * 贴图装饰类型 (PU132 UnitDecal 移植版)。
 *
 * <p>★ 说明: PU132 源码中的 UnitDecal 类属于未完成代码 (源码/编译产物中均不存在),
 * 本移植根据 MonolithUnitTypes 中的用法
 * {@code new UnitDecal(name + "-top", 0f, 0f, 0f, Layer.bullet - 0.02f, Color.white)}
 * 反推语义实现:</p>
 * <ul>
 *   <li>regionName: 装饰贴图名 (单位名 + "-middle"/"-top", 已含 create- 前缀);</li>
 *   <li>x, y, rotation: 相对单位中心的偏移与附加旋转;</li>
 *   <li>layer: 独立渲染层级 (如 Layer.bullet - 0.02f 贴在身体上方、子弹下方,
 *       Layer.effect + 0.0199f 悬在最上层), 绘制时临时切换 Draw.z;</li>
 *   <li>color: 混合色 (Color.white = 原色)。</li>
 * </ul>
 *
 * <p>贴图随单位旋转 (rotation - 90 基准), 顶层 (top=true) 装饰由
 * {@link zzw.content.type.UnityUnitType#drawBody} 绘制。</p>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
public class UnitDecalType extends UnitDecorationType{
    /** 装饰贴图名 (load 时查找, 自动尝试 create- 前缀)。 */
    public String regionName;
    /** 相对单位中心的横向/纵向偏移。 */
    public float x, y;
    /** 附加旋转角 (叠加在单位朝向上)。 */
    public float rotation;
    /** 独立渲染层级。 */
    public float layer;
    /** 混合色。 */
    public Color color = Color.white;

    /** load 时填充的贴图。 */
    public TextureRegion region;

    public UnitDecalType(String regionName, float x, float y, float rotation, float layer, Color color){
        this.regionName = regionName;
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.layer = layer;
        this.color = color;
        // 装饰画在身体贴图之后 (drawBody 阶段), 保证覆盖在单位主体上
        this.top = true;
    }

    @Override
    public void load(){
        region = UnityUtils.findRegion(regionName);
    }

    @Override
    public void draw(Unit unit, UnitDecoration deco){
        if(region == null || !region.found()) return;

        float z = Draw.z();
        Draw.z(layer);
        Draw.color(color);
        Draw.rect(region, unit.x + x, unit.y + y, unit.rotation - 90f + rotation);
        Draw.color();
        Draw.z(z);
    }
}
