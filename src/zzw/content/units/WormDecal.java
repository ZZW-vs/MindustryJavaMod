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
     * 
     * ★ 手机端兼容性: 添加贴图空检查, 防止贴图加载失败导致闪退
     */
    public void draw(Unit base, Unit other) {
        if (other == null) return;
        if (!loaded) load();
        
        // ★ 手机端防御: 贴图未加载成功时只画线条, 不访问贴图
        boolean hasTextures = baseRegion.found() && endRegion.found();
        if (!hasTextures && segmentRegions != null) {
            for (TextureRegion r : segmentRegions) {
                if (r.found()) { hasTextures = true; break; }
            }
        }

        for (int s : Mathf.signs) {
            v1.trns(base.rotation - 90f, baseX * s, baseY).add(base);
            float bx = v1.x, by = v1.y;
            v1.trns(other.rotation - 90f, endX * s, endY).add(other);
            float ex = v1.x, ey = v1.y;
            float angle = Angles.angle(bx, by, ex, ey);

            Draw.mixcol();
            Draw.color(lineColor);
            Fill.circle(bx, by, lineWidth / 2f);
            Fill.circle(ex, ey, lineWidth / 2f);
            Lines.stroke(lineWidth);
            Lines.line(bx, by, ex, ey, false);

            // ★ 贴图绘制部分只在贴图有效时执行
            if (hasTextures) {
                try {
                    base.type.applyColor(base);
                    
                    // 安全检查: 确保贴图宽度不为0
                    float endW = endRegion.found() ? (endRegion.width * Draw.scl * 0.5f) - baseOffset : 0f;
                    float baseW = baseRegion.found() ? (baseRegion.width * Draw.scl * 0.5f) - baseOffset : 0f;
                    
                    v1.trns(angle + 180f, endW).add(ex, ey);
                    ex = v1.x;
                    ey = v1.y;
                    v1.trns(angle, baseW).add(bx, by);
                    bx = v1.x;
                    by = v1.y;

                    if (segmentRegions != null) {
                        for (int i = segmentRegions.length - 1; i >= 0; i--) {
                            TextureRegion r = segmentRegions[i];
                            if (r.found()) {
                                float p = (i + 1f) / (segments + 1f);
                                v1.set(bx, by).lerp(ex, ey, p);
                                Draw.rect(r, v1.x, v1.y, angle);
                            }
                        }
                    }

                    if (endRegion.found()) {
                        Draw.rect(endRegion, ex, ey, angle + 180f);
                    }
                    if (baseRegion.found()) {
                        Draw.rect(baseRegion, bx, by, angle);
                    }
                } catch (Throwable t) {
                    // 手机端防御: 贴图绘制失败时继续, 不闪退
                }
            }
        }
        Draw.reset();
    }

    /**
     * ★ 分层绘制液压装饰: 前段(base端)在单位贴图下方, 后段(end端)在上方
     * - base端(当前段身) → 在更低的z层级绘制
     * - end端(父段) → 在更高的z层级绘制
     * - 中间段 → 两端之间插值
     */
    public void drawBelow(Unit base, Unit other) {
        if (other == null) return;
        if (!loaded) load();
        
        boolean hasTextures = baseRegion.found() && endRegion.found();
        if (!hasTextures && segmentRegions != null) {
            for (TextureRegion r : segmentRegions) {
                if (r.found()) { hasTextures = true; break; }
            }
        }

        float oldZ = Draw.z();

        for (int s : Mathf.signs) {
            v1.trns(base.rotation - 90f, baseX * s, baseY).add(base);
            float bx = v1.x, by = v1.y;
            v1.trns(other.rotation - 90f, endX * s, endY).add(other);
            float ex = v1.x, ey = v1.y;
            float angle = Angles.angle(bx, by, ex, ey);

            Draw.mixcol();
            Draw.color(lineColor);
            Fill.circle(bx, by, lineWidth / 2f);
            Fill.circle(ex, ey, lineWidth / 2f);
            Lines.stroke(lineWidth);
            Lines.line(bx, by, ex, ey, false);

            if (hasTextures) {
                try {
                    base.type.applyColor(base);
                    
                    float endW = endRegion.found() ? (endRegion.width * Draw.scl * 0.5f) - baseOffset : 0f;
                    float baseW = baseRegion.found() ? (baseRegion.width * Draw.scl * 0.5f) - baseOffset : 0f;
                    
                    v1.trns(angle + 180f, endW).add(ex, ey);
                    ex = v1.x;
                    ey = v1.y;
                    v1.trns(angle, baseW).add(bx, by);
                    bx = v1.x;
                    by = v1.y;

                    // ★ 中间段: 按距离插值z层级 (base端低, end端高)
                    if (segmentRegions != null) {
                        for (int i = segmentRegions.length - 1; i >= 0; i--) {
                            TextureRegion r = segmentRegions[i];
                            if (r.found()) {
                                float p = (i + 1f) / (segments + 1f);
                                v1.set(bx, by).lerp(ex, ey, p);
                                // z插值: base端(0) → oldZ - 0.5/10000, end端(1) → oldZ + 0.5/10000
                                float zInterp = Mathf.lerp(oldZ - 0.5f/10000f, oldZ + 0.5f/10000f, p);
                                Draw.z(zInterp);
                                Draw.rect(r, v1.x, v1.y, angle);
                            }
                        }
                    }

                    // ★ end端(父段): 在单位贴图上方
                    Draw.z(oldZ + 0.5f/10000f);
                    if (endRegion.found()) {
                        Draw.rect(endRegion, ex, ey, angle + 180f);
                    }

                    // ★ base端(当前段身): 在单位贴图下方
                    Draw.z(oldZ - 0.5f/10000f);
                    if (baseRegion.found()) {
                        Draw.rect(baseRegion, bx, by, angle);
                    }
                } catch (Throwable t) {}
            }
        }
        
        Draw.z(oldZ);
        Draw.reset();
    }
}
