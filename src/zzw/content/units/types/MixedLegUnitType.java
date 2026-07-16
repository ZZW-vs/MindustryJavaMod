package zzw.content.units.types;

import mindustry.entities.abilities.Ability;
import mindustry.gen.Legsc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import zzw.content.units.abilities.CustomLegsAbility;
import zzw.content.units.abilities.TriJointLegsAbility;

/**
 * 自定义腿型 UnitType (支持 CustomLegsAbility 和 TriJointLegsAbility 两种委托模式)
 *
 * 本类仅负责在 drawLegs() 时查找挂载的腿 Ability 并委托渲染, 不持有腿数据.
 * drawLegs() 在 v158 的 drawBody() 之前调用, 确保腿在身体下方.
 *
 * 支持的 Ability 类型:
 * - CustomLegsAbility: PU132 CLegGroup 系统 (toxoswarmer 用, 多组腿, 两节腿 IK)
 * - TriJointLegsAbility: PU132 TriJointLegsComp 系统 (exowalker 用, 三节腿 IK)
 *
 * 单位仍使用 LegsUnit::create 构造 (保持 Legsc 接口 + legsSolid 碰撞),
 * 但原生腿位置不参与渲染, 仅用于碰撞和地面交互.
 *
 * 参考: PU132 CLegComp + CLegGroup + BasicLeg / TriJointLegsComp + TriJointInverseKinematics
 */
public class MixedLegUnitType extends UnitType {

    public MixedLegUnitType(String name) {
        super(name);
    }

    @Override
    public <T extends Unit & Legsc> void drawLegs(T unit) {
        // 查找腿 Ability 并委托渲染 (优先 CustomLegsAbility, 再 TriJointLegsAbility)
        if (unit.abilities != null) {
            for (Ability a : unit.abilities) {
                if (a instanceof CustomLegsAbility) {
                    ((CustomLegsAbility) a).drawLegs(unit);
                    return;
                }
                if (a instanceof TriJointLegsAbility) {
                    ((TriJointLegsAbility) a).drawLegs(unit);
                    return;
                }
            }
        }
        // 未找到腿 Ability, 回退到原生腿渲染
        super.drawLegs(unit);
    }
}
