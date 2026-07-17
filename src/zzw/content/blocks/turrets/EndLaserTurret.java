package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.geom.Vec2;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Sounds;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.draw.DrawBlock;

/**
 * PU132 EndLaserTurret 移植版 (tenmeikiri) - v158 适配
 *
 * 机制:
 * - 充能后发射持续激光 (chargeTime, chargeEffects, chargeMaxDelay)
 * - 激光子弹锁定到炮台旋转方向, 持续到 lifetime 结束
 * - 激光激活期间炮台不旋转, 保持在 heat/recoil 状态
 * - 防作弊伤害系统: 受击超阈值增加抗性, 30 帧无敌
 * - lastHealth 追踪: 防止外部代码修改 health
 * - 7 层灯光贴图叠加 (加法混合 + 颜色循环)
 *
 * v158 适配:
 * - DrawBlock.draw(Building) 签名 (非 DrawTurret.draw(Turret, TurretBuild))
 * - 自定义字段 chargeTime/chargeEffects/chargeMaxDelay/shootLength/recoilAmount
 * - charging 为方法 charging(), 自定义 isCharging 标志替代
 * - 不依赖父类 bullet() 方法, 直接调用 type.create 创建跟踪子弹
 * - consValid() 不存在, 用 canConsume() 替代
 * - effects() 不存在, 手动触发 shootEffect/smokeEffect/sound
 *
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/EndLaserTurret.java
 */
public class EndLaserTurret extends PowerTurret {
    public float minDamage = 170f;
    public float minDamageTaken = 600f;
    public float resistScl = 0.25f;
    public TextureRegion[] lightRegions;
    public TextureRegion baseOutlineRegion;

    // 自定义充能参数 (v158 PowerTurret 无这些字段)
    public float shootLength = 8f;
    public float chargeTime = 0f;
    public int chargeEffects = 0;
    public float chargeMaxDelay = 0f;
    public float recoilAmount = 1f;
    public Effect chargeEffect = mindustry.content.Fx.lancerLaserCharge;
    public Effect chargeBeginEffect = mindustry.content.Fx.lancerLaserChargeBegin;

    protected static float turretRotation = 0f;

    public EndLaserTurret(String name) {
        super(name);
        // 自定义 drawer: 渲染外轮廓 + 7 层灯光 (加法混合 + 颜色循环)
        drawer = new DrawBlock() {
            @Override
            public void draw(mindustry.gen.Building build) {
                if (!(build instanceof EndLaserTurretBuild)) return;
                EndLaserTurretBuild tb = (EndLaserTurretBuild) build;

                // 绘制外轮廓
                if (baseOutlineRegion != null && baseOutlineRegion.found()) {
                    Draw.rect(baseOutlineRegion, tb.x, tb.y, tb.drawrot());
                }

                // 7 层灯光叠加 (颜色循环 + 加法混合)
                if (lightRegions != null && lightRegions.length > 0) {
                    Draw.blend(Blending.additive);
                    float alpha = tb.lightsAlpha;
                    for (int i = 0; i < lightRegions.length; i++) {
                        TextureRegion r = lightRegions[i];
                        if (r == null || !r.found()) continue;
                        float offset = Time.time + ((360f / lightRegions.length) * i);
                        Draw.color(1f,
                            Mathf.absin(offset, 5f, 0.5f) + 0.5f,
                            Mathf.absin(offset + (90f * Mathf.radDeg), 5f, 0.5f) + 0.5f,
                            alpha);
                        Draw.rect(r, tb.x, tb.y, tb.drawrot());
                    }
                    Draw.blend();
                    Draw.color();
                }
            }
        };
        // unitSort: 优先选择朝向接近炮台旋转方向的单位
        unitSort = (e, x, y) -> e.dst2(x, y) + (float) Math.pow(Angles.angleDist(e.rotation, turretRotation), 2);
    }

    @Override
    public void load() {
        super.load();
        // 加载 7 个灯光贴图 (tenmeikiri-lights-0 ~ 6)
        lightRegions = new TextureRegion[7];
        for (int i = 0; i < 7; i++) {
            lightRegions[i] = Core.atlas.find(name + "-lights-" + i);
        }
        baseOutlineRegion = Core.atlas.find(name + "-base-outline");
    }

    public class EndLaserTurretBuild extends PowerTurretBuild {
        float resistance = 1f;
        float lastHealth;
        float lightsAlpha = 0f;
        boolean rotate = true;
        boolean isCharging = false;
        Bullet bullet;
        private float invFrame = 0f;
        // 自定义 Vec2 (v158 TurretBuild 无 tr 字段)
        private final Vec2 tr = new Vec2();

        @Override
        protected void shoot(BulletType type) {
            // 充能射击逻辑 (chargeTime > 0 时使用充能模式)
            if (chargeTime > 0) {
                useAmmo();

                tr.trns(rotation, shootLength);
                float bx = x + tr.x, by = y + tr.y;
                chargeBeginEffect.at(bx, by, 0f, this);
                chargeSound.at(bx, by, 1f);

                for (int i = 0; i < chargeEffects; i++) {
                    Time.run(Mathf.random(chargeMaxDelay), () -> {
                        if (!isValid()) return;
                        tr.trns(rotation, shootLength);
                        chargeEffect.at(x + tr.x, y + tr.y, rotation);
                    });
                }

                isCharging = true;

                Time.run(chargeTime, () -> {
                    if (!isValid()) return;
                    tr.trns(rotation, shootLength);
                    curRecoil = 1f;
                    heat = 1f;
                    // 直接创建子弹并跟踪
                    bullet = type.create(this, team, x + tr.x, y + tr.y, rotation + Mathf.range(inaccuracy));
                    // 手动触发发射特效
                    (shootEffect == null ? type.shootEffect : shootEffect).at(x + tr.x, y + tr.y, rotation, type.hitColor);
                    (smokeEffect == null ? type.smokeEffect : smokeEffect).at(x + tr.x, y + tr.y, rotation, type.hitColor);
                    (type.shootSound != Sounds.none ? type.shootSound : shootSound).at(x + tr.x, y + tr.y, 1f, shootSoundVolume);
                    if (shake > 0) Effect.shake(shake, shake, this);
                    isCharging = false;
                });
            } else {
                super.shoot(type);
            }
        }

        @Override
        public void updateTile() {
            // 防作弊: 防止外部代码恢复 health
            if (health < lastHealth) health = lastHealth;
            if (invFrame < 30f) invFrame += Time.delta;

            super.updateTile();

            boolean powered = power.status > 0.0001f;
            lightsAlpha = Mathf.lerpDelta(lightsAlpha, powered ? power.status : 0f,
                !powered ? 0.07f : Math.max(power.status * 0.1f, 0.07f));
            resistance = Math.max(1f, resistance - (Time.delta / 20f));

            if (bullet != null) {
                rotate = false;
                tr.trns(rotation, shootLength);
                bullet.rotation(rotation);
                bullet.set(x + tr.x, y + tr.y);
                heat = 1f;
                curRecoil = 1f;
                if (bullet.time >= bullet.lifetime || bullet.owner != this) bullet = null;
            } else {
                rotate = true;
            }
        }

        @Override
        protected void findTarget() {
            turretRotation = rotation;
            super.findTarget();
        }

        @Override
        public void damage(float damage) {
            // 防作弊: 大伤害增加抗性
            if (damage > minDamage) resistance += (damage - minDamage) * resistScl;
            // 无敌帧内不受伤
            if (invFrame < 30f) return;
            float trueDamage = Mathf.clamp(damage, 0f, minDamageTaken) / resistance;
            lastHealth -= trueDamage;
            super.damage(trueDamage);
        }

        @Override
        public void add() {
            if (added) return;
            super.add();
            if (lastHealth <= 0) lastHealth = block.health;
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            lastHealth = read.f();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(lastHealth);
        }

        @Override
        public boolean shouldTurn() {
            return true;
        }

        @Override
        protected void turnToTarget(float targetRot) {
            // 激光激活期间锁定旋转
            float speed = rotate ? rotateSpeed * delta() * baseReloadSpeed() : 0f;
            rotation = Angles.moveToward(rotation, targetRot, speed);
        }

        @Override
        protected void updateCooling() {
            // 激光激活期间不进行冷却 (避免 reload 期间激光中断)
            if (bullet == null) super.updateCooling();
        }

        @Override
        protected void updateShooting() {
            if (canConsume() && !isCharging) {
                super.updateShooting();
            }
        }

        @Override
        protected float baseReloadSpeed() {
            // 激光激活期间 reload 速度为 0 (不让冷却提前结束)
            return bullet == null ? super.baseReloadSpeed() : 0f;
        }

        @Override
        public boolean shouldActiveSound() {
            return bullet != null;
        }
    }
}
