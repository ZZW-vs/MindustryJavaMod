package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.Lightning;
import mindustry.entities.bullet.BulletType;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.draw.DrawTurret;

/**
 * 蓄速电力炮台 (PU_V8 RampupPowerTurret 移植版)
 * z-boson: 持续射击时射速不断提升 (speed 倍率), 停火后衰减; speed 低于阈值时额外触发闪电
 * 简化: 移除 shoot() 重写 (v158 ShootPattern 与 PU_V8 差异大), 通过 baseReloadSpeed() 乘以 speed 实现射速加成;
 *       闪电在 updateTile 中触发; 自定义顶贴图 + 速度条渲染保留
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/RampupPowerTurret.java
 */
public class RampupPowerTurret extends PowerTurret {
    public float barBaseY, barLength, barStroke = 1.5f;
    public Color[] barColors = {Color.valueOf("00d9ff"), Color.valueOf("ccffff")};
    public float maxSpeedMul = 13f, speedInc = 0.2f, speedDec = 0.05f, accInc = 4f;
    public boolean lightning;
    public Color lightningColor = Color.valueOf("a9d8ff");
    public int baseLightningLength, lightningLengthDec;
    public float lightningThreshold, baseLightningDamage, lightningDamageDec;

    public TextureRegion topRegion;

    public RampupPowerTurret(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        topRegion = Core.atlas.find(name + "-top");
    }

    public class RampupPowerTurretBuild extends PowerTurretBuild {
        public float speed = 1f;
        public boolean wasShootingLast = false;

        @Override
        public void updateTile() {
            // 射速提升/衰减 (PU_V8 原版逻辑)
            if (!isShooting() || !canConsume()) {
                changeSpeed(-speedDec * Time.delta);
            } else {
                // 射击中: 通过 shoot 后回调累计 (在 baseReloadSpeed 被调用时累加)
                wasShootingLast = true;
            }
            super.updateTile();

            // 闪电触发 (speed 低于阈值时): 当正在射击且朝向目标
            if (lightning && wasShootingLast && speed < lightningThreshold && efficiency > 0.02f) {
                if (Mathf.chanceDelta(0.15f)) {
                    Lightning.create(team, lightningColor, baseLightningDamage - lightningDamageDec * speed,
                        x + arc.math.Angles.trnsx(rotation, shootY), y + arc.math.Angles.trnsy(rotation, shootY),
                        rotation, baseLightningLength - (int) ((speed - 1) * lightningLengthDec));
                }
            }
            if (!isShooting()) wasShootingLast = false;
        }

        @Override
        public void draw() {
            Draw.rect(((DrawTurret)drawer).base, x, y);

            Draw.z(Layer.turret);

            Drawf.shadow(region, x + recoilOffset.x - elevation, y + recoilOffset.y - elevation, rotation - 90);
            Draw.rect(region, x + recoilOffset.x, y + recoilOffset.y, rotation - 90);

            if (speed > 1.001f) {
                arc.math.geom.Vec2 tr3 = new arc.math.geom.Vec2();
                tr3.trns(rotation, -curRecoil * recoil + barBaseY);
                Draw.color(barColors[0], barColors[1], heat);
                Lines.stroke(barStroke);
                Lines.lineAngle(x + tr3.x, y + tr3.y, rotation, speedf() * barLength, false);
                Draw.reset();
            }

            if (topRegion.found()) {
                Draw.rect(topRegion, x + recoilOffset.x, y + recoilOffset.y, rotation - 90);
            }

            // 过热贴图渲染 (委托给 drawer)
            ((DrawTurret)drawer).drawHeat(RampupPowerTurret.this, this);
        }

        @Override
        protected float baseReloadSpeed() {
            // 射速乘以 speed 倍率 (核心加速机制)
            float s = efficiency * speed;
            // 每次成功 reload 累计 speed
            if (isShooting() && canConsume()) {
                changeSpeed(speedInc * Time.delta / accInc);
            }
            return s;
        }

        public void changeSpeed(float amount) {
            speed = Mathf.clamp(speed + amount, 1f, maxSpeedMul);
        }

        public float speedf() {
            return (speed - 1f) / (maxSpeedMul - 1f);
        }
    }
}
