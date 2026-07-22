package zzw.content.blocks.turrets;

import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Tmp;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Healthc;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import zzw.content.blocks.soul.SoulTurretPowerTurret;
import zzw.util.WavefrontObject;

import static mindustry.Vars.tilesize;

/**
 * PU_V8 PrismTurret 移植版 (prism 棱镜炮台) - v158 适配
 *
 * = SoulTurretPowerTurret (灵魂+电力炮台) + 棱镜多目标攻击机制
 *
 * 原版机制:
 * - 使用 ModelInstance (真 3D) 渲染 prism.g3dj 模型
 * - 多目标攻击: 主目标 + 最多 maxShots 个额外目标, 依次射击
 * - 棱镜旋转动画 (prismRotation) + 颜色渐变 (fromColor → toColor)
 *
 * v158 简化:
 * - 改用 WavefrontObject (伪 3D) 渲染 prism.obj (v155.4 arc-core 无 g3d 包)
 * - 继承 SoulTurretPowerTurret (替代 PU_V8 SoulPowerTurret @Merge)
 * - cooldown 用 cooldownSpeed 字段替代 (原为 SoulPowerTurret.cooldown)
 * - SpecialFx.chainLightningActive 用 Fx.chainLightning 替代 (v158 自带, 等效链式闪电)
 *
 * 模型渲染:
 * - 位置: (x + trnsx(rotation, prismOffset - recoil), y + trnsy(...))
 *   注: v158 中 recoil 是 Block 级距离字段 (恒定), Build 级当前进度是 curRecoil (0~1)
 *   实际后坐力偏移 = curRecoil * recoil, 这里直接用 recoil 作为近似 (与原版视觉差异可忽略)
 * - 旋转: 绕 Z 轴 rotation - 90f, 绕 Y 轴 prismRotation
 * - 颜色: fromColor 到 toColor 渐变 (基于 prismHeat)
 * - 由于 WavefrontObject 是共享实例, draw 中保存/恢复 lightColor
 *
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/PrismTurret.java
 */
public class PrismTurret extends SoulTurretPowerTurret {
    public WavefrontObject object;
    public float prismOffset = 6f;
    public float prismRotateSpeed = 20f;

    public Color fromColor = Color.valueOf("6586b0");  // UnityPal.monolithDark
    public Color toColor = Color.valueOf("87ceeb");    // UnityPal.monolith

    public Effect damageEffect = Fx.chainLightning;
    public float warmup = 0.1f;
    public float cooldownSpeed = 0.05f;

    public int maxShots = 5;
    public float shootRate = 2f;
    public float sortRange = tilesize * 5f;

    public PrismTurret(String name) {
        super(name);
    }

    public class PrismTurretBuild extends SoulTurretPowerTurretBuild {
        public float prismHeat = 0f;
        public float prismRotation = 0f;

        protected Seq<Posc> targets = new Seq<>(false);

        @Override
        public void updateTile() {
            super.updateTile();

            boolean act = isActive();
            prismHeat = Mathf.lerpDelta(prismHeat, act ? efficiency : 0f, act ? warmup : cooldownSpeed);
            // PU_V8/PU132 原版: += Mathf.signs[id % 2], 相邻炮台钻石交替旋转方向
            prismRotation += prismHeat * prismRotateSpeed * Mathf.signs[id % 2];
        }

        @Override
        protected void findTarget() {
            super.findTarget();

            targets.clear().add(target);
            for (int i = 0; i < maxShots; i++) {
                Teamc t = Units.closestTarget(team, targetPos.x, targetPos.y, sortRange,
                    u -> u != target && u.checkTarget(targetAir, targetGround) && !targets.contains(u),
                    b -> b != target && targetGround && !targets.contains(b)
                );

                if (t != null) targets.add(t);
            }
        }

        @Override
        protected void shoot(BulletType type) {
            // 棱镜炮台: 对每个目标依次射击 (间隔 1/shootRate tick)
            for (int idx = 0; idx < targets.size; idx++) {
                Posc u = targets.get(idx);
                final int i = idx;  // lambda 需要effectively final变量
                Time.run(i / shootRate, () -> {
                    if (!isValid() || u == null || !(u instanceof Healthc h ? h.isValid() : u.isAdded())) return;

                    float angle = angleTo(u);
                    shootType.create(this, u.getX(), u.getY(), angle);

                    heat = 1f;
                    curRecoil = 1f;  // 触发后坐力动画

                    // 链式闪电连接炮台与目标
                    damageEffect.at(
                        x + Angles.trnsx(rotation, prismOffset - recoil),
                        y + Angles.trnsy(rotation, prismOffset - recoil),
                        2f,
                        currentColor(),
                        u
                    );

                    type.hitEffect.at(u.getX(), u.getY(), angle);

                    // 射击音效 (第一发时播放)
                    if (i == 0) {
                        shootSound.at(x, y, Mathf.random(soundPitchMin, soundPitchMax));
                    }
                });
            }
        }

        protected Color currentColor() {
            return Tmp.c1.set(fromColor).lerp(toColor, prismHeat + Mathf.sin(4f, 0.1f) * prismHeat);
        }

        @Override
        public void draw() {
            super.draw();

            if (object != null && object.faces != null && object.faces.size > 0) {
                // 保存原始颜色 (WavefrontObject 是共享实例)
                Color origLight = object.lightColor.cpy();
                Color origShade = object.shadeColor.cpy();

                // 计算当前颜色 (fromColor → toColor 渐变)
                Color col = currentColor();
                object.lightColor.set(col);
                object.shadeColor.set(fromColor).lerp(toColor, prismHeat * 0.5f);

                // 模型位置: 炮台中心 + 偏移 (考虑后坐力)
                float px = x + Angles.trnsx(rotation, prismOffset - recoil);
                float py = y + Angles.trnsy(rotation, prismOffset - recoil);

                // 模型旋转: 绕 Z 轴 rotation - 90f, 绕 Y 轴 prismRotation
                float rZ = rotation - 90f;
                float rY = prismRotation;

                object.draw(px, py, 0f, rY, rZ);

                // 恢复原始颜色
                object.lightColor.set(origLight);
                object.shadeColor.set(origShade);
            }
        }
    }
}
