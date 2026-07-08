package zzw.content.units;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import mindustry.gen.Unit;

/**
 * PU132 WormDecal 液压装饰移植版
 * - 头部到段身之间的液压管装饰
 * - 两侧对称绘制 (Mathf.signs)
 * - 包含: 基线 + 端点贴图 + 中间段贴图
 * 参考: PU132 main/src/unity/type/WormDecal.java
 */
public class WormDecal {
    private static final Vec2 v1 = new Vec2();

    public float baseX, baseY, endX, endY;
    public float baseOffset;
    public int segments = 1;
    public Color lineColor = Color.white;
    public float lineWidth = 2f;
    String name;
    public TextureRegion baseRegion, endRegion;
    public TextureRegion[] segmentRegions;
    /** 是否已加载贴图 (延迟加载, 在第一次 draw 时调用) */
    private boolean loaded = false;

    public WormDecal(String name) {
        this.name = name;
    }

    public void load() {
        // ★ mod 贴图在 atlas 中会带 modname- 前缀 (如 "create-oppression-hydraulics-base")
        // 同时尝试带前缀和不带前缀的查找, 优先用带前缀的
        String modName = "create-" + name;
        baseRegion = findRegion(modName + "-base", name + "-base");
        endRegion = findRegion(modName + "-end", name + "-end");
        segmentRegions = new TextureRegion[segments];
        for (int i = 0; i < segmentRegions.length; i++) {
            segmentRegions[i] = findRegion(modName + "-" + i, name + "-" + i);
        }
        loaded = true;
    }

    /** 先试 prefixed, 找不到再试不带前缀 */
    private static TextureRegion findRegion(String prefixed, String unprefixed) {
        TextureRegion r = Core.atlas.find(prefixed);
        if (r.found()) return r;
        return Core.atlas.find(unprefixed);
    }

    /**
     * 在 base (段身) 和 other (头部 parent) 之间绘制液压装饰
     * PU132 调用: wormDecal.draw(unit, unit.parent()) → base=段身, other=头部
     */
    public void draw(Unit base, Unit other) {
        if (other == null) return;
        // 延迟加载贴图 (atlas 在 Z_Units.load() 时未加载, 需要等第一次 draw 时加载)
        if (!loaded) load();
        for (int s : Mathf.signs) {
            // 计算头部锚点
            v1.trns(base.rotation - 90f, baseX * s, baseY).add(base);
            float bx = v1.x, by = v1.y;
            // 计算段身锚点
            v1.trns(other.rotation - 90f, endX * s, endY).add(other);
            float ex = v1.x, ey = v1.y;
            float angle = Angles.angle(bx, by, ex, ey);

            Draw.mixcol();
            Draw.color(lineColor);
            // 端点圆
            Fill.circle(bx, by, lineWidth / 2f);
            Fill.circle(ex, ey, lineWidth / 2f);
            // 基线
            Lines.stroke(lineWidth);
            Lines.line(bx, by, ex, ey, false);

            // 调整端点位置 (考虑贴图宽度)
            base.type.applyColor(base);
            v1.trns(angle + 180f, (endRegion.width * Draw.scl * 0.5f) - baseOffset).add(ex, ey);
            ex = v1.x;
            ey = v1.y;
            v1.trns(angle, (baseRegion.width * Draw.scl * 0.5f) - baseOffset).add(bx, by);
            bx = v1.x;
            by = v1.y;

            // 中间段贴图
            for (int i = segmentRegions.length - 1; i >= 0; i--) {
                TextureRegion r = segmentRegions[i];
                float p = (i + 1f) / (segments + 1f);
                v1.set(bx, by).lerp(ex, ey, p);
                Draw.rect(r, v1.x, v1.y, angle);
            }

            // 端点贴图
            Draw.rect(endRegion, ex, ey, angle + 180f);
            Draw.rect(baseRegion, bx, by, angle);
        }
        Draw.reset();
    }
}
