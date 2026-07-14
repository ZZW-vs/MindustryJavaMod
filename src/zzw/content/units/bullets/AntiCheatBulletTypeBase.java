package zzw.content.units.bullets;

import zzw.content.units.anticheat.AntiCheatBulletModule;

import arc.math.Mathf;
import arc.struct.IntSet;
import arc.util.Tmp;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;

/**
 * PU132 防作弊子弹基类移植版
 * - ratioDamage: 按比例扣血 (应对百万血单位)
 * - overDamage: 超量伤害指数增长 (目标越强伤害越高)
 * - bleedDuration: 流血状态 (阻止回血)
 * - pierceShields: 穿盾
 * - ArmorDamageModule: 削甲/盾/能力效率
 * 参考: PU132 main/src/unity/entities/bullet/anticheat/AntiCheatBulletTypeBase.java
 * 简化: 移除了 Unity.antiCheat 依赖 (采样湮灭系统), 保留核心伤害计算
 */
public abstract class AntiCheatBulletTypeBase extends BulletType {
    /** 按比例扣血 (0-1), 目标血量超过 ratioStart 时启用 */
    public float ratioDamage = 0f;
    /** 比例伤害启动阈值 */
    public float ratioStart = 25000f;
    /** 流血持续时间 (阻止回血), -1=禁用 */
    public float bleedDuration = -1f;
    /** 超量伤害启动阈值 */
    public float overDamage = 1000000f;
    public float overDamageScl = 2000f;
    public float overDamagePower = 2f;
    /** 是否穿盾 (走 damagePierce 路径) */
    public boolean pierceShields = false;
    /** 防作弊模块数组 */
    public AntiCheatBulletModule[] modules;
    /** ★ 是否计入技能数量上限 (大招不算, 例如主激光不算) */
    public boolean countsAsSkill = false;
    /** ★ 该技能同时存在的数量上限 (每个技能类型独立计数) */
    public int maxActive = Integer.MAX_VALUE;
    /** 当前该技能活跃数量 */
    private int activeCount = 0;
    /** 已计数的 bullet id 集合 (避免重复计数) */
    private final IntSet countedBullets = new IntSet(64);
    private float[] moduleDataTmp;

    public AntiCheatBulletTypeBase(float speed, float damage) {
        super(speed, damage);
    }

    public AntiCheatBulletTypeBase() {}

    @Override
    public void init() {
        super.init();
        if (modules != null) moduleDataTmp = new float[modules.length];
    }

    /**
     * ★ 技能数量上限检查 (非大招技能调用)
     * - 第一帧检查活跃技能数, 超过 maxActiveSkills 则立即移除
     * - 每一帧检查子弹是否消失, 递减计数
     * - 用 bullet id + IntSet 追踪, 不占用 bullet 的 data/fdata 字段
     * @return true=正常继续, false=已超限被移除
     */
    protected boolean checkSkillLimit(Bullet b) {
        if (!countsAsSkill) return true;

        int id = b.id;
        boolean counted = countedBullets.contains(id);

        if (!counted) {
            // 第一帧: 检查该类型上限
            if (activeCount >= maxActive) {
                b.remove();
                return false;
            }
            activeCount++;
            countedBullets.add(id);
        } else {
            // 检查是否消失 (到期/被移除)
            if (!b.isAdded() || b.time >= b.lifetime) {
                activeCount = Math.max(0, activeCount - 1);
                countedBullets.remove(id);
            }
        }
        return true;
    }

    /**
     * 对单位造成防作弊伤害
     * @param b 子弹
     * @param unit 目标单位
     * @param extraDamage 额外伤害 (用于穿透场景, 用剩余伤害)
     */
    public void hitUnitAntiCheat(Bullet b, Unit unit, float extraDamage) {
        float health = unit.health * unit.healthMultiplier;
        // 数值溢出检测: NaN/Infinity/MAX_VALUE 直接强制清除
        if (health >= Float.MAX_VALUE || Float.isNaN(health) || health >= Float.POSITIVE_INFINITY) {
            unit.health = 0;
            unit.dead = true;
            return;
        }
        float score = health + unit.type.dpsEstimate;
        // 超量伤害: 目标越强, 指数追加伤害越大
        float pow = score > overDamage ? Mathf.pow((score - overDamage) / overDamageScl, overDamagePower) : 0f;
        // 比例伤害: 目标血量超过 ratioStart 时, 按比例扣血
        float ratio = health > ratioStart ? ratioDamage * Math.max(unit.maxHealth, unit.health) : 0f;
        // 最终伤害 = max(比例伤害, 弹丸伤害 + 超量伤害)
        float damage = Math.max(ratio, ((b.damage + extraDamage) * b.damageMultiplier()) + pow);

        // 模块: 削甲/削盾/破力场
        if (modules != null) {
            int i = 0;
            for (AntiCheatBulletModule mod : modules) {
                moduleDataTmp[i] = mod.getUnitData(unit);
                mod.hitUnit(unit, b);
                i++;
            }
            for (Ability ability : unit.abilities) {
                for (AntiCheatBulletModule mod : modules) {
                    mod.handleAbility(ability, unit, b);
                }
            }
        }

        // 造成伤害
        if (pierceShields) {
            unit.damagePierce(damage);
        } else {
            unit.damage(damage);
        }

        // 击退
        Tmp.v3.set(unit).sub(b).nor().scl(knockback * 80f);
        if (impact) Tmp.v3.setAngle(b.rotation() + (knockback < 0 ? 180f : 0f));
        unit.impulse(Tmp.v3);
        unit.apply(status, statusDuration);

        // 模块后处理 (破盾特效等)
        if (modules != null) {
            for (int i = 0; i < modules.length; i++) {
                modules[i].handleUnitPost(unit, b, moduleDataTmp[i]);
            }
        }
    }

    public void hitUnitAntiCheat(Bullet b, Unit unit) {
        hitUnitAntiCheat(b, unit, 0f);
    }

    /**
     * 对建筑造成防作弊伤害
     */
    public void hitBuildingAntiCheat(Bullet b, Building building, float extraDamage) {
        if (building.health >= Float.MAX_VALUE || Float.isNaN(building.health) || building.health >= Float.POSITIVE_INFINITY) {
            building.health = 0;
            return;
        }
        boolean col = !(collidesTiles && collides);
        float pow = building.health > overDamage ? Mathf.pow((building.health - overDamage) / overDamageScl, overDamagePower) : 0f;
        if (col || pow > 0f || ratioDamage > 0f) {
            float ratio = building.health > ratioStart ? ratioDamage * Math.max(building.maxHealth, building.health) : 0f;
            float damage = Math.max(ratio, (col ? (b.damage + extraDamage) * b.damageMultiplier() * buildingDamageMultiplier : 0f) + pow);
            building.damage(damage);
        }
    }

    public void hitBuildingAntiCheat(Bullet b, Building building) {
        hitBuildingAntiCheat(b, building, 0f);
    }

    @Override
    public void hitEntity(Bullet b, Hitboxc entity, float health) {
        if (entity instanceof Unit) {
            hitUnitAntiCheat(b, (Unit) entity);
        } else {
            super.hitEntity(b, entity, health);
        }
    }

    @Override
    public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
        hitBuildingAntiCheat(b, build);
        super.hitTile(b, build, x, y, initialHealth, direct);
    }
}
