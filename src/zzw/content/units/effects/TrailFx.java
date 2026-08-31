package zzw.content.units.effects;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Trail;
import zzw.content.graphics.UnityPal;

import static arc.graphics.g2d.Draw.alpha;
import static arc.graphics.g2d.Draw.blend;
import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Draw.rect;
import static arc.graphics.g2d.Draw.scl;
import static mindustry.Vars.state;

/**
 * 拖尾类特效 (PU132 unity.content.effects.TrailFx 移植)。
 *
 * <p>收录各类武器的飞行拖尾: 通用渐隐长拖尾、磁轨炮双色拖尾、
 * 箭形拖尾、能量尖刺拖尾、End 系列红黑拖尾、披风拖尾。</p>
 *
 * <p>★ v132 → v155 适配要点:</p>
 * <ul>
 *   <li>{@code unity.graphics.UnityPal} → {@link UnityPal};</li>
 *   <li>{@link Trail} 在 v155 仍位于 mindustry.graphics, 无需改动;</li>
 *   <li>PU132 的 {@code unity.type.decal.CapeDecorationType.CapeEffectData}
 *       (披风装饰系统) 尚未移植, 此处以本类内嵌的
 *       {@link CapeEffectData} 占位 —— 字段结构与原版一致
 *       (type.region / type.x / type.y / alpha / sway), 披风装饰系统
 *       移植后可直接替换 data 类型。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class TrailFx{
    public static Effect

    /**
     * 长拖尾渐隐 (400f): data 携带 {@link Trail}, 特效寿命重设为
     * 拖尾长度 × 1.4, 每 tick 调用 shorten() 从队头削点,
     * 使拖尾整体平滑缩短直至消失。图层压在飞行单位之下。
     */
    trailFadeLow = new Effect(400f, e -> {
        if(!(e.data instanceof Trail trail)) return;
        e.lifetime = trail.length * 1.4f;

        if(!state.isPaused()) trail.shorten();
        trail.drawCap(e.color, e.rotation);
        trail.draw(e.color, e.rotation);
    }).layer(Layer.flyingUnitLow - 0.001f),

    /**
     * 磁轨炮拖尾 (30f): 旋转方向两侧 (±90°+90°×sign) 各一片
     * 宽 10×fout 的长三角, 颜色由 e.color 指定。
     */
    coloredRailgunTrail = new Effect(30f, e -> {
        for(int i = 0; i < 2; i++){
            int sign = Mathf.signs[i];
            color(e.color);
            Drawf.tri(e.x, e.y, 10f * e.fout(), 24f, e.rotation + 90f + 90f * sign);
        }
    }),

    /**
     * 小型磁轨炮拖尾 (30f): 同 {@link #coloredRailgunTrail} 的缩小版
     * (宽 5, 长 12)。
     */
    coloredRailgunSmallTrail = new Effect(30f, e -> {
        for(int i = 0; i < 2; i++){
            int sign = Mathf.signs[i];
            color(e.color);
            Drawf.tri(e.x, e.y, 5f * e.fout(), 12f, e.rotation + 90f + 90f * sign);
        }
    }),

    /**
     * 箭形拖尾 (40f, 裁剪 80): 旋转方向前后各一片三角拼成 "箭羽"。
     *
     * <p>步骤: 头部锚点 v1 沿朝向前移 5×fout; 两侧锚点 v2 在
     * 旋转 - 90° 方向偏移 9×sign×((fout+2)/3) 并后移 20,
     * 头 / 尾锚点与侧锚点组成 Fill.tri。</p>
     */
    coloredArrowTrail = new Effect(40f, 80f, e -> {
        Tmp.v1.trns(e.rotation, 5f * e.fout());
        color(e.color);
        for(int s : Mathf.signs){
            Tmp.v2.trns(e.rotation - 90f, 9f * s * ((e.fout() + 2f) / 3f), -20f);
            Fill.tri(Tmp.v1.x + e.x, Tmp.v1.y + e.y, -Tmp.v1.x + e.x, -Tmp.v1.y + e.y, Tmp.v2.x + e.x, Tmp.v2.y + e.y);
        }
    }),

    /**
     * 尖刺能量拖尾 (16f): 朝向 ±90° 两侧各一片宽 4、长 30×fslope
     * 的三角, 长度按 slope 曲线先伸后收。
     */
    spikedEnergyTrail = new Effect(16f, e -> {
        color(e.color);
        for(int s : Mathf.signs){
            Drawf.tri(e.x, e.y, 4f, 30f * e.fslope(), e.rotation + 90f*s);
        }
    }),

    /**
     * End 磁轨拖尾 (50f): scarColor → endColor 渐变,
     * 前后 (rot / rot+180°) 两片宽 13×fout、长 29 的对顶三角。
     */
    endRailTrail = new Effect(50f, e -> {
        color(UnityPal.scarColor, UnityPal.endColor, e.fin());
        Drawf.tri(e.x, e.y, 13f * e.fout(), 29f, e.rotation);
        Drawf.tri(e.x, e.y, 13f * e.fout(), 29f, e.rotation + 180f);
    }),

    /**
     * End 常规拖尾 (50f): 黑 → scarColor 渐变 (前 30% 保持黑),
     * 2 个随机圆粒子在 finpow×7 半径内飘散, 半径 3×fout 收缩。
     */
    endTrail = new Effect(50f, e -> {
        color(Color.black, UnityPal.scarColor, Mathf.curve(e.fin(), 0f, 0.3f));
        Angles.randLenVectors(e.id, 2, e.finpow() * 7f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 3f * e.fout());
        });
    }),

    /**
     * 披风拖尾 (30f): data 携带 {@link CapeEffectData}。
     *
     * <p>披风本体左右两片 (sign = ±1) 以加色混合绘制:
     * 位置 = 朝向 - 90° 方向按 type.x×sign / type.y 偏移,
     * 贴图宽度按 sign 翻转 (镜像), 旋转 = 特效朝向 + sway×sign - 90°
     * (sway 摆动量由装饰系统按单位仰角提供), 透明度 alpha×fout 渐隐。</p>
     */
    capeTrail = new Effect(30f, e -> {
        CapeEffectData data = e.data();
        TextureRegion reg = data.type.region;

        alpha(data.alpha * e.fout());
        blend(Blending.additive);
        for(int sign : Mathf.signs){
            Tmp.v1.trns(e.rotation - 90f, data.type.x * sign, data.type.y);
            rect(
                reg,
                e.x + Tmp.v1.x, e.y + Tmp.v1.y,
                reg.width * scl * sign,
                reg.height * scl,
                e.rotation + data.sway * sign - 90f
            );
        }

        blend(Blending.normal);
    });

    /**
     * 披风拖尾数据 (PU132 unity.type.decal.CapeDecorationType.CapeEffectData 占位移植)。
     *
     * <p>原版中由 {@code CapeDecorationType.update()} 每帧构造并传给本特效;
     * 披风装饰系统移植后, 可将本类删除并改回原版类型,
     * 字段结构与原版完全一致。</p>
     */
    public static class CapeEffectData{
        /** 披风装饰类型 (贴图与挂点偏移)。 */
        public CapeTypeHolder type;
        /** 披风透明度 (随单位仰角变化)。 */
        public float alpha;
        /** 披风摆动角 (度)。 */
        public float sway;

        public CapeEffectData(CapeTypeHolder type, float alpha, float sway){
            this.type = type;
            this.alpha = alpha;
            this.sway = sway;
        }
    }

    /**
     * 披风类型字段占位 (对应原版 CapeDecorationType 中拖尾用到的字段)。
     */
    public static class CapeTypeHolder{
        /** 披风贴图。 */
        public TextureRegion region;
        /** 挂点在 (朝向-90°) 坐标系下的偏移。 */
        public float x, y;
    }
}
