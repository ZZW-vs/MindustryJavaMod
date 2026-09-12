package zzw.content.units.weapons;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.Weapon;
import zzw.content.graphics.UnityPal;
import zzw.content.units.effects.UnityDrawf;

/**
 * 能量环武器 (PU132 unity.type.weapons.monolith.EnergyRingWeapon 完整移植)。
 *
 * <p>无贴图武器: 单位身上绘制若干圈<b>旋转的能量符环</b> (Ring) + 中心"眼睛",
 * 是 stray/tendence/liminality 视觉效果的核心。</p>
 *
 * <p>渲染步骤 (逐步解释):</p>
 * <ol>
 *   <li>每帧累计 m.time (基础流速 + 攻击姿态 aggression 加成),
 *       攻击时环加速旋转, 冷却时缓慢降速 —— 产生"苏醒/休眠"的灵性感;</li>
 *   <li>每个 Ring: 用 {@link UnityDrawf#arcLine} 画 divisions 段圆弧
 *       (相邻弧段间留 divisionSeparation 缺口), 用 {@link UnityDrawf#tri}
 *       画 spikes 个径向尖刺;</li>
 *   <li>rotate=true 的环按 m.time 自旋 (奇偶单位镜像方向), rotate=false 的环
 *       跟随武器 mount 旋转角 (瞄准方向);</li>
 *   <li>中心眼睛: 在 shootX/shootY 偏移处画 eyeRadius 圆点。</li>
 * </ol>
 *
 * <p>★ PU132 适配: 原版为未完成代码 —— Ring 缺 shootY 字段声明
 * (配置 {@code shootY = radius = 2.5f} 无法编译), 此处补上该字段承载配置;
 * 眼睛位置沿用 Weapon.shootX/shootY。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class EnergyRingWeapon extends Weapon{
    /** 环列表 (按注册顺序由内向外绘制)。 */
    public final Seq<Ring> rings = new Seq<>(4);

    /** 攻击姿态对时间的加成倍率。 */
    public float aggressionScale = 3f;
    /** 攻击姿态上升速度。 */
    public float aggressionSpeed = 0.2f;
    /** 冷却姿态回落速度。 */
    public float cooldownSpeed = 0.08f;

    /** 中心眼睛颜色。 */
    public Color eyeColor = UnityPal.monolithLight;
    /** 中心眼睛半径。 */
    public float eyeRadius = 2.5f;

    public EnergyRingWeapon(){
        super("");
        mountType = weapon -> new EnergyRingMount((EnergyRingWeapon)weapon);
    }

    @Override
    public void update(Unit unit, WeaponMount mount){
        super.update(unit, mount);

        EnergyRingMount m = (EnergyRingMount)mount;
        // 步骤 1: 攻击姿态渐变 (有目标/射击时 → 1, 否则 → 0), 时间流速随之加速
        m.aggression = (m.target != null || m.shoot)
            ? Mathf.lerpDelta(m.aggression, 1f, aggressionSpeed)
            : Mathf.lerpDelta(m.aggression, 0f, cooldownSpeed);
        m.time += Time.delta + m.aggression * Time.delta * aggressionScale;
    }

    @Override
    public void draw(Unit unit, WeaponMount mount){
        float z = Draw.z();
        Draw.z(z + layerOffset);

        EnergyRingMount m = (EnergyRingMount)mount;

        // 步骤 2: 武器在单位身上的锚点
        float rot = unit.rotation - 90f;
        Tmp.v1.trns(rot, x, y).add(unit);

        for(Ring ring : rings){
            // 奇偶单位翻转旋转方向, 让同屏多个单位姿态错开
            int sign = Mathf.sign(ring.flip ^ unit.id % 2 == 0);
            float rotation = ring.angleOffset * sign + (ring.rotate ? (m.time * sign) : (rot + mount.rotation));

            Lines.stroke(ring.thickness, ring.color);
            // 步骤 3a: divisions 段圆弧 (单段 = 完整圆环)
            for(int i = 0; i < ring.divisions; i++){
                float angleStep = 360f / ring.divisions;
                UnityDrawf.arcLine(Tmp.v1.x, Tmp.v1.y, ring.radius,
                    ring.divisions == 1 ? 360f : angleStep - ring.divisionSeparation,
                    rotation + angleStep * i);
            }

            // 步骤 3b: 径向尖刺
            for(int i = 0; i < ring.spikes; i++){
                float spikeRotation = rotation + ring.spikeRotOffset + 360f / ring.spikes * i;

                Tmp.v2.trns(spikeRotation, 0f, ring.radius + ring.spikeOffset).add(Tmp.v1);
                UnityDrawf.tri(Tmp.v2.x, Tmp.v2.y, ring.spikeWidth, ring.spikeLength, spikeRotation + 90f);
            }
        }

        // 步骤 4: 中心眼睛 (跟随 mount 旋转的炮口位置)
        rot += m.rotation;
        Tmp.v1.add(Tmp.v2.trns(rot, shootX, shootY));

        Draw.color(eyeColor);
        Fill.circle(Tmp.v1.x, Tmp.v1.y, eyeRadius);

        Draw.z(z);
    }

    /** 能量环武器没有贴图轮廓。 */
    @Override
    public void drawOutline(Unit unit, WeaponMount mount){}

    /** 能量环配置。 */
    public static class Ring{
        /** 弧线颜色。 */
        public Color color = UnityPal.monolithLight;
        /** 弧线宽度。 */
        public float thickness = 1.5f;
        /** 环半径。 */
        public float radius = 4.5f;
        /** false 时跟随武器 mount 旋转 (瞄准方向), true 时按时间自旋。 */
        public boolean rotate = true;
        /** 自旋速度 (PU132 中未被渲染逻辑使用, 保留字段)。 */
        public float rotateSpeed = 2f;
        /** 初始角度偏移。 */
        public float angleOffset = 0f;
        /** 翻转旋转方向 (配合单位 id 奇偶)。 */
        public boolean flip;

        /** 弧段数 (1 = 完整圆)。 */
        public int divisions = 1;
        /** 相邻弧段间的缺口角度。 */
        public float divisionSeparation = 12f;

        /** 径向尖刺数 (0 = 无)。 */
        public int spikes = 0;
        /** 尖刺离环面的径向偏移。 */
        public float spikeOffset = 1f;
        /** 尖刺整体角度偏移。 */
        public float spikeRotOffset;
        /** 尖刺底宽。 */
        public float spikeWidth = 1.5f;
        /** 尖刺长度。 */
        public float spikeLength = 3f;

        /** 环上炮口位置 (PU132 未完成代码引用缺失字段, 此处补齐; 渲染暂未使用)。 */
        public float shootY;
    }

    /** 能量环武器挂载点 (扩展攻击姿态状态)。 */
    public static class EnergyRingMount extends WeaponMount{
        /** 环累计自旋时间 (随攻击加速)。 */
        public float time;
        /** 攻击姿态系数 (0=休眠, 1=全力攻击)。 */
        public float aggression;

        public EnergyRingMount(EnergyRingWeapon weapon){
            super(weapon);
        }
    }
}
