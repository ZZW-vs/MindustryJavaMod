package zzw.content.units.graphics;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.g2d.TextureAtlas.AtlasRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.struct.FloatSeq;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.graphics.Pal;
import mindustry.graphics.Trail;

import static mindustry.Vars.headless;

/**
 * 贴图化拖尾 (PU132 unity.graphics.TexturedTrail 移植)
 *
 * <p>原版 {@link Trail} 只能画渐变色的粗线; 本类用一张贴图沿轨迹拉伸绘制,
 * 支持收缩 (shrink)、渐隐 (fadeAlpha)、混色 (mixColor)、自定义混合模式等,
 * 是 Monolith 幽蓝拖尾 (unity-phantasmal-trail / unity-soul-trail) 的核心。</p>
 *
 * <p>实现原理 (新手向):</p>
 * <ol>
 *   <li>拖尾点存进自己的 {@link FloatSeq} (每点 4 个 float: x, y, 宽度, 进度)。</li>
 *   <li>{@link #update} 每 ~1 tick 追加一个点, 超过 length 后移除队头。</li>
 *   <li>{@link #draw} 从尾到头逐段画两个四边形 (上下各一个, 用 UV 采样贴图)。</li>
 * </ol>
 *
 * <p>★ v158 适配: 基类改为 mindustry.graphics.Trail, 其余 arc API 兼容。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class TexturedTrail extends Trail{
    /** 拖尾主贴图。 */
    public TextureRegion region;
    /** 拖尾头部封口贴图。 */
    public TextureRegion capRegion;
    /** 拖尾从头到尾的宽度收缩比例, 1f = 三角形。 */
    public float shrink = 1f;
    /** 拖尾尾部的透明度, 1f = 尾部完全透明。 */
    public float fadeAlpha = 0f;
    /** 混色透明度 (draw 时传入的颜色)。 */
    public float mixAlpha = 0.5f;
    /** 基础宽度倍率。 */
    public float baseWidth = 1f;
    /** 渐变色 (拖尾越靠尾部越向该色过渡)。 */
    public Color fadeColor = Color.white;
    /** 渐变色插值器。 */
    public Interp gradientInterp = Interp.linear;
    /** 中心透明度插值器。 */
    public Interp fadeInterp = Interp.pow2In;
    /** 边缘透明度插值器。 */
    public Interp sideFadeInterp = Interp.pow3In;
    /** 混色插值器。 */
    public Interp mixInterp = Interp.pow5In;
    /** 混合模式。 */
    public Blending blend = Blending.normal;

    /** 强制不绘制封口。 */
    public boolean forceCap;
    /** 更新点的最小移动距离。 */
    public float minDst = -1f;

    /** 拖尾粒子特效。 */
    public Effect trailEffect = Fx.missileTrail;
    /** 粒子触发概率。 */
    public float trailChance = 0f;
    /** 粒子宽度。 */
    public float trailWidth = 1f;
    /** 粒子颜色。 */
    public Color trailColor = Pal.engine;
    /** 低于此速度时粒子概率衰减。 */
    public float trailThreshold = 3f;

    private static final float[] vertices = new float[24];
    private static final Color tmp = new Color();

    // 自维护的点序列 (绕过基类 points)
    protected final FloatSeq points;
    protected float lastX = -1f, lastY = -1f, lastAngle = -1f, lastW = 0f, counter = 0f;

    public TexturedTrail(TextureRegion region, TextureRegion capRegion, int length){
        this(length);
        this.region = region;
        this.capRegion = capRegion;
    }

    public TexturedTrail(TextureRegion region, int length){
        this(length);
        this.region = region;
        // ★ mod 贴图带 "create-" 前缀, 帽端贴图 (reg.name + "-cap") 也带前缀;
        //   回退贴图 hcircle 同为 mod 贴图, 需用前缀名查找
        if(!headless && region instanceof AtlasRegion reg) capRegion = Core.atlas.find(reg.name + "-cap", "create-hcircle");
    }

    public TexturedTrail(int length){
        super(0); // 基类不分配点数组
        this.length = length;
        points = new FloatSeq(length * 4);
    }

    @Override
    public TexturedTrail copy(){
        TexturedTrail out = new TexturedTrail(region, capRegion, length);
        out.shrink = shrink;
        out.fadeAlpha = fadeAlpha;
        out.mixAlpha = mixAlpha;
        out.baseWidth = baseWidth;
        out.fadeColor = fadeColor;
        out.gradientInterp = gradientInterp;
        out.fadeInterp = fadeInterp;
        out.sideFadeInterp = sideFadeInterp;
        out.mixInterp = mixInterp;
        out.blend = blend;
        out.forceCap = forceCap;
        out.minDst = minDst;
        out.trailEffect = trailEffect;
        out.trailChance = trailChance;
        out.trailWidth = trailWidth;
        out.trailColor = trailColor;
        out.trailThreshold = trailThreshold;
        out.points.addAll(points);
        out.lastX = lastX;
        out.lastY = lastY;
        out.lastAngle = lastAngle;
        out.lastW = lastW;
        out.counter = counter;
        return out;
    }

    @Override
    public void clear(){
        points.clear();
    }

    @Override
    public int size(){
        return points.size / 4;
    }

    @Override
    public void drawCap(Color color, float widthMultiplier){
        if(forceCap || capRegion == Core.atlas.find("clear")) return;

        float width = baseWidth * widthMultiplier;
        // ★ hcircle 为 mod 贴图, 需用 "create-" 前缀名查找
        if(capRegion == null) capRegion = Core.atlas.find("create-hcircle");

        int psize = points.size;
        if(psize > 0){
            float
                rv = psize / 4f / length,
                alpha = rv * fadeAlpha + (1f - fadeAlpha),
                w = Mathf.map(rv, 1f - shrink, 1f) * width * lastW * 2f,
                h = ((float)capRegion.height / capRegion.width) * w,

                angle = -Mathf.radDeg * lastAngle - 90f,
                u = capRegion.u, v = capRegion.v2, u2 = capRegion.u2, v2 = capRegion.v, uh = Mathf.lerp(u, u2, 0.5f),
                cx = Mathf.cosDeg(angle) * w / 2f, cy = Mathf.sinDeg(angle) * w / 2f,
                x1 = lastX, y1 = lastY,
                x2 = lastX + Mathf.cosDeg(angle + 90f) * h, y2 = lastY + Mathf.sinDeg(angle + 90f) * h,

                col1 = tmp.set(Draw.getColor()).lerp(fadeColor, gradientInterp.apply(1f - rv)).a(fadeInterp.apply(alpha)).clamp().toFloatBits(),
                col1h = tmp.set(Draw.getColor()).lerp(fadeColor, gradientInterp.apply(1f - rv)).a(sideFadeInterp.apply(alpha)).clamp().toFloatBits(),
                col2 = tmp.set(Draw.getColor()).lerp(fadeColor, gradientInterp.apply(1f - rv)).a(fadeInterp.apply(alpha)).clamp().toFloatBits(),
                col2h = tmp.set(Draw.getColor()).lerp(fadeColor, gradientInterp.apply(1f - rv)).a(sideFadeInterp.apply(alpha)).clamp().toFloatBits(),
                mix1 = tmp.set(color).a(mixInterp.apply(rv * mixAlpha)).clamp().toFloatBits(),
                mix2 = tmp.set(color).a(mixInterp.apply(rv * mixAlpha)).clamp().toFloatBits();

            Draw.blend(blend);
            vertices[0] = x1 - cx;
            vertices[1] = y1 - cy;
            vertices[2] = col1h;
            vertices[3] = u;
            vertices[4] = v;
            vertices[5] = mix1;

            vertices[6] = x1;
            vertices[7] = y1;
            vertices[8] = col1;
            vertices[9] = uh;
            vertices[10] = v;
            vertices[11] = mix1;

            vertices[12] = x2;
            vertices[13] = y2;
            vertices[14] = col2;
            vertices[15] = uh;
            vertices[16] = v2;
            vertices[17] = mix2;

            vertices[18] = x2 - cx;
            vertices[19] = y2 - cy;
            vertices[20] = col2h;
            vertices[21] = u;
            vertices[22] = v2;
            vertices[23] = mix2;

            Draw.vert(region.texture, vertices, 0, 24);

            vertices[6] = x1 + cx;
            vertices[7] = y1 + cy;
            vertices[8] = col1h;
            vertices[9] = u2;
            vertices[10] = v;
            vertices[11] = mix1;

            vertices[0] = x1;
            vertices[1] = y1;
            vertices[2] = col1;
            vertices[3] = uh;
            vertices[4] = v;
            vertices[5] = mix1;

            vertices[18] = x2;
            vertices[19] = y2;
            vertices[20] = col2;
            vertices[21] = uh;
            vertices[22] = v2;
            vertices[23] = mix2;

            vertices[12] = x2 + cx;
            vertices[13] = y2 + cy;
            vertices[14] = col2h;
            vertices[15] = u2;
            vertices[16] = v2;
            vertices[17] = mix2;

            Draw.vert(region.texture, vertices, 0, 24);
            Draw.blend();
        }
    }

    @Override
    public void draw(Color color, float widthMultiplier){
        if(forceCap) drawCap(color, widthMultiplier);
        float width = baseWidth * widthMultiplier;

        if(region == null) region = Core.atlas.find("white");
        if(points.isEmpty()) return;

        float[] items = points.items;
        int psize = points.size;

        float
            endAngle = this.lastAngle, lastAngle = endAngle,
            u = region.u2, v = region.v2, u2 = region.u, v2 = region.v, uh = Mathf.lerp(u, u2, 0.5f);

        Draw.blend(blend);
        for(int i = 0; i < psize; i += 4){ // 从尾到头绘制
            float
                x1 = items[i], y1 = items[i + 1], w1 = items[i + 2], rv1 = Mathf.clamp(items[i + 3]),
                x2, y2, w2, rv2;

            if(i < psize - 4){
                x2 = items[i + 4];
                y2 = items[i + 5];
                w2 = items[i + 6];
                rv2 = Mathf.clamp(items[i + 7]);
            }else{
                x2 = lastX;
                y2 = lastY;
                w2 = lastW;
                rv2 = psize / 4f / length;
            }

            float
                z2 = i == psize - 4 ? endAngle : -Angles.angleRad(x1, y1, x2, y2), z1 = i == 0 ? z2 : lastAngle,
                fs1 = Mathf.map(rv1, 1f - shrink, 1f) * width * w1,
                fs2 = Mathf.map(rv2, 1f - shrink, 1f) * width * w2,

                cx = Mathf.sin(z1) * fs1, cy = Mathf.cos(z1) * fs1,
                nx = Mathf.sin(z2) * fs2, ny = Mathf.cos(z2) * fs2,

                mv1 = Mathf.lerp(v, v2, rv1), mv2 = Mathf.lerp(v, v2, rv2),
                cv1 = rv1 * fadeAlpha + (1f - fadeAlpha), cv2 = rv2 * fadeAlpha + (1f - fadeAlpha),
                col1 = tmp.set(Draw.getColor()).lerp(fadeColor, gradientInterp.apply(1f - rv1)).a(fadeInterp.apply(cv1)).clamp().toFloatBits(),
                col1h = tmp.set(Draw.getColor()).lerp(fadeColor, gradientInterp.apply(1f - rv1)).a(sideFadeInterp.apply(cv1)).clamp().toFloatBits(),
                col2 = tmp.set(Draw.getColor()).lerp(fadeColor, gradientInterp.apply(1f - rv2)).a(fadeInterp.apply(cv2)).clamp().toFloatBits(),
                col2h = tmp.set(Draw.getColor()).lerp(fadeColor, gradientInterp.apply(1f - rv2)).a(sideFadeInterp.apply(cv2)).clamp().toFloatBits(),
                mix1 = tmp.set(color).a(mixInterp.apply(rv1 * mixAlpha)).clamp().toFloatBits(),
                mix2 = tmp.set(color).a(mixInterp.apply(rv2 * mixAlpha)).clamp().toFloatBits();

            vertices[0] = x1 - cx;
            vertices[1] = y1 - cy;
            vertices[2] = col1h;
            vertices[3] = u;
            vertices[4] = mv1;
            vertices[5] = mix1;

            vertices[6] = x1;
            vertices[7] = y1;
            vertices[8] = col1;
            vertices[9] = uh;
            vertices[10] = mv1;
            vertices[11] = mix1;

            vertices[12] = x2;
            vertices[13] = y2;
            vertices[14] = col2;
            vertices[15] = uh;
            vertices[16] = mv2;
            vertices[17] = mix2;

            vertices[18] = x2 - nx;
            vertices[19] = y2 - ny;
            vertices[20] = col2h;
            vertices[21] = u;
            vertices[22] = mv2;
            vertices[23] = mix2;

            Draw.vert(region.texture, vertices, 0, 24);

            vertices[6] = x1 + cx;
            vertices[7] = y1 + cy;
            vertices[8] = col1h;
            vertices[9] = u2;
            vertices[10] = mv1;
            vertices[11] = mix1;

            vertices[0] = x1;
            vertices[1] = y1;
            vertices[2] = col1;
            vertices[3] = uh;
            vertices[4] = mv1;
            vertices[5] = mix1;

            vertices[18] = x2;
            vertices[19] = y2;
            vertices[20] = col2;
            vertices[21] = uh;
            vertices[22] = mv2;
            vertices[23] = mix2;

            vertices[12] = x2 + nx;
            vertices[13] = y2 + ny;
            vertices[14] = col2h;
            vertices[15] = u2;
            vertices[16] = mv2;
            vertices[17] = mix2;

            Draw.vert(region.texture, vertices, 0, 24);
            lastAngle = z2;
        }

        Draw.blend();
    }

    @Override
    public void shorten(){
        if((counter += Time.delta) >= 0.96f){
            if(points.size >= 4) points.removeRange(0, 3);
            counter = 0f;
        }

        calcProgress();
    }

    @Override
    public void update(float x, float y, float widthMultiplier){
        float dst = Mathf.dst(lastX, lastY, x, y) / Time.delta;
        float width = baseWidth * widthMultiplier;

        if((counter += Time.delta) >= 0.96f){
            if(dst >= minDst){
                if(points.size > length * 4 - 4) points.removeRange(0, 3);
                points.add(x, y, width, 0f);
            }else if(points.size >= 4){
                points.removeRange(0, 3);
            }

            counter = 0f;
        }

        lastAngle = Mathf.zero(dst) ? lastAngle : -Angles.angleRad(lastX, lastY, x, y);
        lastX = x;
        lastY = y;
        lastW = width;
        calcProgress();

        int psize = points.size;
        if(psize > 0 && trailChance > 0f && Mathf.chanceDelta(trailChance * Mathf.clamp(dst / trailThreshold))){
            trailEffect.at(
                x, y, width * trailWidth,
                tmp.set(trailColor).a(fadeInterp.apply(Mathf.clamp((psize / 4f / length) * fadeAlpha + (1f - fadeAlpha))))
            );
        }
    }

    /**
     * 重新计算每个点的进度值 (第 4 个 float), 用于宽度/透明度沿轨迹渐变。
     */
    public void calcProgress(){
        int psize = points.size;
        if(psize > 0){
            float[] items = points.items;

            float maxDst = 0f;
            for(int i = 0; i < psize; i += 4){
                float
                    x = items[i], y = items[i + 1],
                    dst = i < psize - 4 ? Mathf.dst(x, y, items[i + 4], items[i + 5]) : Mathf.dst(x, y, lastX, lastY);

                items[i + 3] = maxDst;
                maxDst += dst;
            }

            float frac = psize / 4f / length;
            for(int i = 0; i < psize; i += 4){
                items[i + 3] = Mathf.clamp((items[i + 3] / maxDst) * frac);
            }
        }
    }
}
