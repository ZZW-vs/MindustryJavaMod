package zzw.content.units.anticheat;

import mindustry.entities.abilities.Ability;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Unit;

/**
 * PU132 防作弊模块接口 (简化移植)
 * - hitUnit: 命中时直接修改目标属性 (削甲/盾)
 * - hitBuilding: 命中建筑时
 * - handleAbility: 遍历目标所有 Ability, 削弱能力 (力场/修复)
 * - getUnitData/handleUnitPost: 命中前记录数据, 命中后做后续处理 (破盾特效)
 * 参考: PU132 main/src/unity/entities/bullet/anticheat/modules/AntiCheatBulletModule.java
 */
public interface AntiCheatBulletModule {
    default void hitUnit(Unit unit, Bullet bullet) {}
    default void hitBuilding(Building build, Bullet bullet) {}
    default void handleAbility(Ability ability, Unit unit, Bullet bullet) {}
    default float getUnitData(Unit unit) { return 0f; }
    default void handleUnitPost(Unit unit, Bullet bullet, float data) {}
}
