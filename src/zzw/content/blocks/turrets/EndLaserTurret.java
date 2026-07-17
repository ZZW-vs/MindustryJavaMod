package zzw.content.blocks.turrets;

import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;
import mindustry.world.draw.DrawTurret;
import zzw.content.units.effects.ChargeEffect;

/**
 * PU_V8 EndLaserTurret 移植版 (tenmeikiri) - v158/v155.4 适配
 *
 * 完全按原版复制: 主底座用 tenmeikiri-base, tenmeikiri.png 作为旋转炮塔,
 * tenmeikiri-base-outline 作为叠加层, 7 层灯光叠加渲染.
 *
 * 机制:
 * - 充能后发射持续激光 (chargeTime, chargeEffects, chargeMaxDelay)
 * - 激光子弹锁定到炮台旋转方向, 持续到 lifetime 结束
 * - 激光激活期间炮台不旋转, 保持在 heat/recoil 状态
 * - 防作弊伤害系统: 受击超阈值增加抗性, 30 帧无敌
 * - lastHealth 追踪: 防止外部代码修改 health
 * - 7 层灯光贴图叠加 (加法混合 + 颜色循环), alpha 随 power.status 渐变
 *
 * v155.4 适配:
 * - 用 DrawTurret 子类作为 drawer, super.draw() 画 base + turret + heat
 * - 再画 tenmeikiri-base-outline 叠加层 (在 recoilOffset 位置, drawrot 旋转)
 * - 再画 7 层灯光 (在 recoilOffset 位置, 加法混合)
 * - canConsume() 替代 consValid()
 * - curRecoil = 1f 替代 recoil = recoilAmount (v155.4 curRecoil 是 0~1 计数器)
 * - charging 用 isCharging 标志替代
 * - Drawf.light 无 Team 参数版本
 *
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/EndLaserTurret.java
 */
public class EndLaserTurret extends PowerTurret {
    public float minDamage = 170f, minDamageTaken = 600f;
    public float resistScl = 0.25f;
    public TextureRegion[] lightRegions;
    public TextureRegion baseOutline;

    // 自定义充能参数 (v155.4 PowerTurret 无这些字段)
    public float shootLength = 8f;
    public float chargeTime = 0f;
    public int chargeEffects = 0;
    public float chargeMaxDelay = 0f;
    // 充能特效 (默认使用 PU_V8 tenmeikiri 专用特效)
    public Effect chargeBeginEffect = ChargeEffect.tenmeikiriChargeBegin;
    public Effect chargeEffect = ChargeEffect.tenmeikiriChargeEffect;

    protected static float turretRotation = 0f;

    public EndLaserTurret(String name) {
        super(name);
        // drawer: 继承 DrawTurret, super.draw() 画 base + turret + heat,
        // 再画 tenmeikiri-base-outline 叠加层 + 7 层灯光
        drawer = new DrawTurret() {
            @Override
            public void draw(Building build) {
                // 1. 画 base (tenmeikiri-base) + turret (tenmeikiri.png) + heat
                super.draw(build);

                TurretBuild tb = (TurretBuild) build;
                float ox = build.x + tb.recoilOffset.x;
                float oy = build.y + tb.recoilOffset.y;
                float rot = tb.drawrot();

                // 2. 画 tenmeikiri-base-outline 叠加层 (关键修复: 之前缺失此层)
                if (baseOutline != null && baseOutline.found()) {
                    Draw.rect(baseOutline, ox, oy, rot);
                }

                // 3. 画 7 层灯光 (加法混合 + 颜色循环)
                if (lightRegions == null || lightRegions.length == 0) return;
                float alpha = build instanceof EndLaserTurretBuild ? ((EndLaserTurretBuild) build).lightsAlpha : 0f;
                if (alpha <= 0.001f) return;
                Draw.blend(Blending.additive);
                for (int i = 0; i < lightRegions.length; i++) {
                    TextureRegion r = lightRegions[i];
                    if (r == null || !r.found()) continue;
                    float offset = Time.time + ((360f / lightRegions.length) * i);
                    // PU_V8 原版颜色: 90f * Mathf.radDeg 作为蓝通道相位偏移
                    Draw.color(1f,
                        Mathf.absin(offset, 5f, 0.5f) + 0.5f,
                        Mathf.absin(offset + (90f * Mathf.radDeg), 5f, 0.5f) + 0.5f,
                        alpha);
                    Draw.rect(r, ox, oy, rot);
                }
                Draw.blend();
                Draw.color();
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
            lightRegions[i] = arc.Core.atlas.find(name + "-lights-" + i);
        }
        // 加载底座叠加层 (tenmeikiri-base-outline)
        baseOutline = arc.Core.atlas.find(name + "-base-outline");
    }

    public class EndLaserTurretBuild extends PowerTurretBuild {
        float resistance = 1f;
        float lastHealth;
        float lightsAlpha = 0f;
        boolean rotate = true;
        boolean isCharging = false;
        Bullet bullet;
        private float invFrame = 0f;
        // 自定义 Vec2 (v155.4 TurretBuild 无 tr 字段)
        private final arc.math.geom.Vec2 tr = new arc.math.geom.Vec2();

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
                    // v155.4: curRecoil 是 0~1 计数器, 设为 1f 触发后坐力
                    // 视觉后坐距离由 Turret.recoil 字段控制 (Z_AdvTurrets 中设置)
                    curRecoil = 1f;
                    heat = 1f;
                    // 直接创建子弹并跟踪
                    bullet = type.create(this, team, x + tr.x, y + tr.y, rotation + Mathf.range(inaccuracy));
                    // 手动触发发射特效 (v155.4 无 effects() 方法)
                    (shootEffect == null ? type.shootEffect : shootEffect).at(x + tr.x, y + tr.y, rotation, type.hitColor);
                    (smokeEffect == null ? type.smokeEffect : smokeEffect).at(x + tr.x, y + tr.y, rotation, type.hitColor);
                    (type.shootSound != mindustry.gen.Sounds.none ? type.shootSound : shootSound)
                        .at(x + tr.x, y + tr.y, 1f, shootSoundVolume);
                    if (shake > 0) mindustry.entities.Effect.shake(shake, shake, this);
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
