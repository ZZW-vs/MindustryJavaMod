package zzw.content.units.abilities;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import zzw.content.Z_Bullets;
import zzw.content.type.UnityUnitType;
import zzw.content.units.AbilityTextures;
import zzw.util.UnityUtils;

/**
 * 方向护盾能力
 * PU132 unity.entities.abilities.DirectionShieldAbility 移植版
 *
 * <p>创建围绕单位的定向护盾, 可以反弹子弹:</p>
 * <ul>
 *   <li>射击时护盾朝向敌人, 非射击时护盾朝向后方</li>
 *   <li>命中判定用护盾线段与子弹碰撞盒的 raycast 相交测试</li>
 *   <li>高伤害 (>= explosiveDamageThreshold) 子弹被反弹时额外向偏转角两侧发射 3 发破片弹</li>
 *   <li>护盾有独立生命值, 击碎后以 disableRegen 慢速恢复, 满血自动重新展开</li>
 * </ul>
 *
 * <p>★ v158 适配: Bullet 移至 mindustry.gen; Tmp.v1/v2/v3 + Tmp.r1/r2/r3 复用替代
 * 手写 float 数组; PU132 Utils.getBulletDamage / UnityBullets.scarShrapnel
 * 分别移植到 {@link UnityUtils} 与 {@link Z_Bullets#scarShrapnel}。</p>
 */
public class DirectionShieldAbility extends Ability {
    /** 护盾宽度 */
    protected final float shieldWidth = 7f;
    /** 闪烁时间 */
    protected final float blinkTime = 5f;

    /** 护盾数量 */
    public int shields;
    /** 护盾角度数组 */
    public float[] shieldAngles;
    /** 护盾生命值数组 */
    public float[] healths;
    /** 护盾命中时间数组 */
    public float[] hitTimes;
    /** 护盾可用状态数组 */
    public boolean[] available;
    /** 最大生命值 */
    public float maxHealth;
    /** 禁用恢复速度 */
    public float disableRegen;
    /** 护盾恢复速度 */
    public float shieldRegen;
    /** 距离半径 */
    public float distanceRadius;
    /** 护盾大小 */
    public float shieldSize;
    /** 护盾旋转速度 */
    public float shieldSpeed;
    /** 计时器 */
    public Interval timer = new Interval();

    /** 爆炸反射伤害倍数 */
    public float explosiveReflectDamageMultiplier = 0.7f;
    /** 爆炸伤害阈值 */
    public float explosiveDamageThreshold = 90f;
    /** 生命条偏移 */
    public float healthBarOffset = 4f;
    /** 生命条颜色 */
    public Color healthBarColor = Color.white;

    /**
     * 构造函数
     * @param shields 护盾数量
     * @param speed 护盾旋转速度
     * @param size 护盾大小
     * @param health 护盾生命值
     * @param regen 护盾恢复速度
     * @param disableRegen 禁用恢复速度
     * @param distance 护盾距离
     */
    public DirectionShieldAbility(int shields, float speed, float size, float health, float regen, float disableRegen, float distance) {
        shieldSpeed = speed;
        shieldSize = size;
        maxHealth = health;
        shieldRegen = regen;
        distanceRadius = distance;
        shieldAngles = new float[shields];
        healths = new float[shields];
        hitTimes = new float[shields];
        available = new boolean[shields];
        this.disableRegen = disableRegen;
        this.shields = shields;

        for (int i = 0; i < shields; i++) {
            shieldAngles[i] = 0f;
            hitTimes[i] = 0f;
            healths[i] = health;
            available[i] = true;
        }
    }

    @Override
    public Ability copy() {
        DirectionShieldAbility instance = new DirectionShieldAbility(shields, shieldSpeed, shieldSize, maxHealth, shieldRegen, disableRegen, distanceRadius);
        instance.explosiveReflectDamageMultiplier = explosiveReflectDamageMultiplier;
        instance.explosiveDamageThreshold = explosiveDamageThreshold;
        instance.healthBarOffset = healthBarOffset;
        instance.healthBarColor = healthBarColor;
        return instance;
    }

    /**
     * 更新护盾状态 (PU132 updateShields)
     *
     * <p>执行步骤:</p>
     * <ol>
     *   <li>以单位为中心构造包围矩形 r1, 对每面护盾: v1 = 单位位置 + 角度方向的
     *       distanceRadius 偏移 (护盾线段中点), v2 = 垂直方向半长 (护盾线段半宽),
     *       两个端点 nodeA/nodeB = 中点 ± v2; 同时把端点周围 r2 合并进 r1;</li>
     *   <li>每 1.5tick 遍历 r1 范围内的敌方子弹 (排除激光类与 scaleVelocity 弹),
     *       对每面可用护盾: 用 raycastRect 检测子弹碰撞盒 (原位/半速前移两份)
     *       与护盾线段是否相交;</li>
     *   <li>相交则: 护盾扣血 (按子弹综合伤害), 子弹自身伤害减 1/3, 子弹转向
     *       反射角 angC 并转换阵营; 若护盾承受伤害超过阈值, 附加 3 发破片反射;</li>
     *   <li>护盾恢复: 可用时按 shieldRegen 回血, 击碎时按 disableRegen 慢速回血
     *       并冒烟, 回满自动重新展开。</li>
     * </ol>
     *
     * @param unit 单位实例
     */
    protected void updateShields(Unit unit) {
        Tmp.r1.setCentered(unit.x, unit.y, shieldSize);
        Seq<ShieldNode> nodes = new Seq<>();

        for (int i = 0; i < shields; i++) {
            Tmp.v1.trns(shieldAngles[i], distanceRadius - (shieldWidth / 2f));
            Tmp.v1.add(unit);
            Tmp.v2.trns(shieldAngles[i] + 90f, (shieldSize / 2f) - (shieldWidth / 2f));

            ShieldNode ts = new ShieldNode();
            ts.id = i;
            for (int s : Mathf.signs) {
                ts.getNodes(s).set(Tmp.v1.x + (Tmp.v2.x * s), Tmp.v1.y + (Tmp.v2.y * s));
                Tmp.r2.setCentered(Tmp.v1.x + (Tmp.v2.x * s), Tmp.v1.y + (Tmp.v2.y * s), shieldSize / 2f);
                Tmp.r1.merge(Tmp.r2);
            }
            nodes.add(ts);
        }

        if (timer.get(1.5f)) {
            Groups.bullet.intersect(Tmp.r1.x, Tmp.r1.y, Tmp.r1.width, Tmp.r1.height, b -> {
                if (b.team != unit.team &&
                    !(b.type instanceof ContinuousLaserBulletType || b.type instanceof LaserBulletType) &&
                    // v158.1 已移除 scaleVelocity 机制, 该排除条件不再需要
                    b.vel().len() > 0.1f) {

                    // 子弹碰撞盒原位一份 (r2), 沿半速前移一份 (r3), 都外扩护盾宽度
                    b.hitbox(Tmp.r2);
                    Tmp.r3.set(Tmp.r2).grow(shieldWidth).move(b.vel.x / 2f, b.vel.y / 2f);
                    Tmp.r2.grow(shieldWidth);

                    nodes.each(n -> {
                        if (!available[n.id]) return;

                        // 线段与两份子弹碰撞盒任一相交即视为命中
                        if (Geometry.raycastRect(n.nodeA.x, n.nodeA.y, n.nodeB.x, n.nodeB.y, Tmp.r2) != null ||
                            Geometry.raycastRect(n.nodeA.x, n.nodeA.y, n.nodeB.x, n.nodeB.y, Tmp.r3) != null) {

                            float d = UnityUtils.getBulletDamage(b.type) * (b.damage() / (b.type.damage * b.damageMultiplier()));
                            healths[n.id] -= d;
                            b.damage(b.damage() / 1.5f);
                            float angC = (((shieldAngles[n.id] + 90f) * 2f) - b.rotation()) + Mathf.range(15f);

                            // 爆炸反射: 高伤子弹在偏转角两侧各 20 度共弹射 3 发破片
                            if (explosiveReflectDamageMultiplier > 0f && d >= explosiveDamageThreshold) {
                                for (int i = 0; i < 3; i++) {
                                    float off = (i * 20f - (3 - 1) * 20f / 2f);
                                    Z_Bullets.scarShrapnel.create(unit, unit.team, b.x, b.y, angC + off, d * explosiveReflectDamageMultiplier, 1f, 1f, null);
                                }
                            }

                            hitTimes[n.id] = blinkTime;
                            b.team(unit.team());
                            b.rotation(angC);

                            if (healths[n.id] < 0) {
                                available[n.id] = false;
                            }
                        }
                    });
                }
            });
        }

        // 第4步: 护盾生命值恢复 (可用=快回, 击碎=慢回+冒烟, 回满重新展开)
        for (int i = 0; i < shields; i++) {
            if (available[i]) {
                healths[i] = Math.min(healths[i] + (shieldRegen * Time.delta), maxHealth);
            } else {
                if (Mathf.chanceDelta(0.32 * (1f - Mathf.clamp(healths[i] / maxHealth)))) {
                    Tmp.v1.trns(shieldAngles[i], distanceRadius);
                    Tmp.v1.add(unit);
                    Tmp.v2.trns(shieldAngles[i] + 90, Mathf.range(shieldSize / 2f), Mathf.range(2f));
                    Tmp.v1.add(Tmp.v2);
                    Fx.smoke.at(Tmp.v1.x, Tmp.v1.y);
                }
                healths[i] = Math.min(healths[i] + (disableRegen * Time.delta), maxHealth);
                if (healths[i] >= maxHealth) {
                    available[i] = true;
                    hitTimes[i] = blinkTime;
                }
            }
        }
    }

    @Override
    public void update(Unit unit) {
        if (unit.isShooting()) {
            // 射击时护盾朝向敌人 (以 unit.rotation 为中心扇形排布)
            float size = ((shieldSize * Mathf.PI2) / Mathf.sqrt(distanceRadius / 1.5f));
            for (int i = 0; i < shields; i++) {
                float ang = Mathf.mod((i * size - (shields - 1f) * size / 2f) + unit.rotation, 360f);
                shieldAngles[i] = Mathf.slerpDelta(shieldAngles[i], ang, shieldSpeed);
                hitTimes[i] = Math.max(hitTimes[i] - Time.delta, 0f);
            }
        } else {
            // 非射击时护盾朝向后方 (均匀圆周分布 + 180 度偏移)
            float offset = (360f / shields) / 2f;
            for (int i = 0; i < shields; i++) {
                float ang = Mathf.mod(((i * 360f / shields) + offset) + unit.rotation + 180f, 360f);
                shieldAngles[i] = Mathf.slerpDelta(shieldAngles[i], ang, shieldSpeed);
                hitTimes[i] = Math.max(hitTimes[i] - Time.delta, 0f);
            }
        }
        updateShields(unit);
    }

    @Override
    public void draw(Unit unit) {
        float z = Draw.z();

        // 检查是否为UnityUnitType
        if (!(unit.type instanceof UnityUnitType)) {
            return;
        }
        UnityUnitType type = (UnityUnitType) unit.type;

        // 获取护盾贴图
        var region = type.abilityRegions[AbilityTextures.shield.ordinal()];
        if (region == null) return;

        float size = (Math.max(region.width, region.height) * Draw.scl) * 1.3f;
        Lines.stroke(1.5f);

        for (int i = 0; i < shields; i++) {
            Draw.z(z - 0.0098f);

            Tmp.v3.trns(shieldAngles[i], distanceRadius);
            Tmp.v3.add(unit);

            float offset = available[i] ? 2f : 1.5f;
            Draw.mixcol(Color.white, hitTimes[i] / blinkTime);
            Draw.color(Color.white, Color.black, (1f - (Mathf.clamp(healths[i] / maxHealth))) / offset);
            Draw.rect(region, Tmp.v3.x, Tmp.v3.y, shieldAngles[i]);

            if (available[i]) {
                // 绘制生命条
                Tmp.v3.trns(shieldAngles[i], distanceRadius + healthBarOffset);
                Tmp.v3.add(unit);
                Draw.color(healthBarColor);
                Lines.lineAngleCenter(Tmp.v3.x, Tmp.v3.y, shieldAngles[i] + 90f,
                    Mathf.clamp(healths[i] / maxHealth) * shieldSize);
            }

            // 绘制阴影
            Draw.z(Math.min(Layer.darkness, z - 1f));
            Draw.mixcol();
            Draw.color(Pal.shadow);
            Draw.rect(type.softShadowRegion, Tmp.v3.x, Tmp.v3.y, size, size);

            // 绘制能量核心
            Draw.z(z - 0.0099f);
            float engScl = shieldSize / 6f;
            float liveScl = (engScl - (engScl / 4f)) + Mathf.absin(Time.time, 2f, engScl / 4f);
            Tmp.v3.trns(shieldAngles[i], distanceRadius - engScl);
            Tmp.v3.add(unit);
            Draw.color(unit.team.color);
            Fill.circle(Tmp.v3.x, Tmp.v3.y, liveScl);
            Draw.color(Color.white);
            Fill.circle(Tmp.v3.x, Tmp.v3.y, liveScl / 2f);
            Draw.z(z);
        }

        Draw.reset();
    }

    /**
     * 护盾节点类
     */
    public static class ShieldNode {
        public arc.math.geom.Vec2 nodeA = new arc.math.geom.Vec2();
        public arc.math.geom.Vec2 nodeB = new arc.math.geom.Vec2();
        public int id;

        public ShieldNode() {
        }

        public arc.math.geom.Vec2 getNodes(int sign) {
            return sign <= 0 ? nodeA : nodeB;
        }
    }
}
