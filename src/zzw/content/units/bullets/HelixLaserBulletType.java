package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import zzw.content.graphics.UnityPal;
import zzw.content.units.effects.UnityDrawf;

/**
 * 螺旋激光子弹 (PU132 unity.entities.bullet.monolith.laser.HelixLaserBulletType 完整移植)。
 *
 * <p>liminality 主炮弹: 在 {@link LaserBulletType} 的多层光束主体之上,
 * 叠加 {@link #swirlAmount} 条<b>绕光束旋转的螺旋丝带</b> (3D 深度分层渲染,
 * 丝带转到激光杆后面时画在下层, 转到前面时画在上层, 产生"绕杆穿插"视觉),
 * 外加两侧对称的斜向冲刺线。</p>
 *
 * <p>渲染步骤 (逐步解释):</p>
 * <ol>
 *   <li>光束主体: 沿 colors 逐层绘制伸缩线段 (生长 laserExtTime 阶段 /
 *       收缩 laserShrinkTime 阶段), 端点三角 + 圆点 + 两侧翼三角;</li>
 *   <li>冲刺线: 两侧 ±dashWidth/2 处各一条沿光束方向加速延伸的斜线
 *       (dashInterp1/dashInterp2 分别控制起止进度) + 两端小三角;</li>
 *   <li>螺旋丝带: 每条丝带沿光束长度取 iterations 个采样点, 角度 =
     *   随机初相 + 2π·swirlScale·(i/swirlAmount) + 2π·swirlScale·采样进度,
     *   横向偏移 = cos(rad, swirlScale, swirlMagnitude), 深度 = sin 的符号;
     *   丝带沿长度方向按 swirlIn/swirlStay/swirlOut 三段淡入-保持-淡出,
     *   颜色由 swirlColor→swirlColorDark 随透明度反向渐变。</li>
 * </ol>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class HelixLaserBulletType extends LaserBulletType{
    /** 螺旋丝带条数。 */
    public int swirlAmount = 3;
    /** 丝带双色 (正面/背面)。 */
    public Color swirlColor = UnityPal.monolith, swirlColorDark = UnityPal.monolithDark;
    /** 冲刺线双色。 */
    public Color dashColor = UnityPal.monolithLight, dashColorDark = UnityPal.monolith;

    /** 螺旋参数: 角频率 / 横向摆幅 / 线宽, 以及沿长度方向的三段进度 (淡入/保持/淡出)。 */
    public float
        swirlScale = 12f, swirlMagnitude = 6f, swirlThickness = 1f,
        swirlIn = 0.1f, swirlStay = 0.2f, swirlOut = 0.5f, swirlFrom = 0.1f, swirlTo = 0.96f,
        laserExtTime = 0.2f, laserShrinkTime = 0.3f, laserTo = 1f,
        dashWidth = 8f,
        dashFrom = 0.2f, dashTo = 0.92f, dashThickness = 1.5f;

    /** 各阶段插值函数。 */
    public Interp
        laserGrowInterp = Interp.pow2Out, laserShrinkInterp = Interp.pow2Out, laserThickInterp = Interp.pow4Out,
        swirlInInterp = Interp.pow2In, swirlOutInterp = Interp.pow3In, swirlFadeInterp = Interp.pow10Out,
        dashInterp1 = Interp.pow2In, dashInterp2 = Interp.pow2Out, dashColorInterp = Interp.pow5In;

    public HelixLaserBulletType(float damage){
        super(damage);
        lifetime = 32f;
    }

    @Override
    public void draw(Bullet b){
        float
            z = Draw.z(),
            // b.fdata = 实际激光长度 (LaserBulletType.init 的射线检测结果)
            realLength = b.fdata, scl = realLength / length,
            fin = b.fin(), rot = b.rotation(),

            lfin = Mathf.curve(fin, 0f, laserTo), lfout = 1f - lfin,
            laserLenf = Mathf.curve(lfin, 0f, laserExtTime * scl), laserLen = laserGrowInterp.apply(laserLenf) * realLength,
            laserShrinkf = Mathf.curve(lfin, 1f - laserShrinkTime * scl, 1f), laserShrink = laserShrinkInterp.apply(laserShrinkf) * realLength,
            cwidth = width,
            compound = 1f,

            sfin = Mathf.curve(fin, swirlFrom, swirlTo),
            slife = swirlIn + swirlStay + swirlOut, soffset = 1f - slife,

            dfin = Mathf.curve(fin, dashFrom * scl, dashTo);

        // 第1步: 光束主体 (多层渐变色)
        for(Color color : colors){
            Tmp.v1.trns(rot, laserShrink);

            Draw.color(color);
            Lines.stroke((cwidth *= lengthFalloff) * laserThickInterp.apply(lfout));
            Lines.lineAngle(b.x + Tmp.v1.x, b.y + Tmp.v1.y, rot, laserLen - laserShrink, false);

            UnityDrawf.tri(b.x + Tmp.v1.x, b.y + Tmp.v1.y, Lines.getStroke(), Lines.getStroke() / 2f, rot + 180f);
            Tmp.v1.trns(rot, laserLen);
            UnityDrawf.tri(b.x + Tmp.v1.x, b.y + Tmp.v1.y, Lines.getStroke(), cwidth * 2f + width / 2f, rot);

            Fill.circle(b.x, b.y, 1f * cwidth * lfout);
            for(int i : Mathf.signs){
                UnityDrawf.tri(b.x, b.y, sideWidth * lfout * cwidth, sideLength * compound, rot + sideAngle * i);
            }

            compound *= lengthFalloff;
        }

        // 第2步: 两侧冲刺线 (加速延伸 + 两端三角收尾)
        Lines.stroke(dashThickness, Tmp.c1.set(dashColor).lerp(dashColorDark, dashColorInterp.apply(dfin)));
        for(int sign : Mathf.signs){
            float x = dashWidth * sign * 0.5f, cy = Lines.getStroke() * 2.5f;
            Tmp.v1.trns(rot - 90f, x, dashInterp1.apply(dfin) * realLength + cy).add(b);
            Tmp.v2.trns(rot - 90f, x, dashInterp2.apply(dfin) * realLength + cy).add(b);

            Lines.line(Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y, false);
            UnityDrawf.tri(Tmp.v1.x, Tmp.v1.y, Lines.getStroke(), cy, rot + 180f);
            UnityDrawf.tri(Tmp.v2.x, Tmp.v2.y, Lines.getStroke(), cy, rot);
        }

        // 第3步: 螺旋丝带 (3D 深度分层多段线)
        Lines.stroke(swirlThickness);

        int iterations = Math.max(Mathf.round(realLength), 2);
        float
            seg = realLength / iterations,
            rand =
                Mathf.randomSeed(b.id, Mathf.PI2 * swirlScale) +
                (Mathf.randomSeed(b.id + 1, 0, 1) * 2f - 1f);
        for(int i = 0; i < swirlAmount; i++){
            UnityDrawf.beginLine();

            float angleOffset = rand + (Mathf.PI2 * swirlScale) * ((float)i / swirlAmount);
            for(int it = 0; it < iterations; it++){
                float
                    in = it / (iterations - 1f),
                    off = soffset * in,
                    prog = (
                        swirlInInterp.apply(Mathf.curve(sfin, off, swirlIn + off)) -
                        swirlOutInterp.apply(Mathf.curve(sfin, swirlStay + off, swirlOut + off))
                    ) * swirlFadeInterp.apply(1f - in),

                    rad = it * seg + angleOffset,
                    x = Mathf.cos(rad, swirlScale, swirlMagnitude),
                    tz = Mathf.sin(rad, swirlScale, 1f) >= 0f ? z : (z - 0.01f);

                Tmp.v1.trns(rot - 90f, x, it * seg).add(b);
                UnityDrawf.linePoint(Tmp.v1.x, Tmp.v1.y, Tmp.c1.set(swirlColor).lerp(swirlColorDark, 1f - prog).a(prog).toFloatBits(), tz);
            }

            UnityDrawf.endLine(false);
        }

        Draw.z(z);
        Draw.reset();
    }
}
