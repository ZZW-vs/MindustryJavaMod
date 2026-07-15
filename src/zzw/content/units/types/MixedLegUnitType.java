package zzw.content.units.types;

import mindustry.entities.abilities.Ability;
import mindustry.gen.Legsc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import zzw.content.units.abilities.CustomLegsAbility;

/**
 * 自定义腿型 UnitType (移植 PU132 CLegGroup 系统)
 *
 * 使用 CustomLegsAbility 管理腿数据 (IK + 步态), 本类仅负责在 drawLegs() 时
 * 委托给 CustomLegsAbility.drawLegs() 进行渲染.
 *
 * drawLegs() 在 v158 的 drawBody() 之前调用, 确保腿在身体下方.
 *
 * 单位仍使用 LegsUnit::create 构造 (保持 Legsc 接口 + legsSolid 碰撞),
 * 但原生腿位置不参与渲染, 仅用于碰撞和地面交互.
 *
 * 参考: PU132 CLegComp + CLegGroup + BasicLeg
 */
public class MixedLegUnitType extends UnitType {

    public MixedLegUnitType(String name) {
        super(name);
    }

    @Override
    public <T extends Unit & Legsc> void drawLegs(T unit) {
        // 查找 CustomLegsAbility 并委托渲染
        if (unit.abilities != null) {
            for (Ability a : unit.abilities) {
                if (a instanceof CustomLegsAbility) {
                    ((CustomLegsAbility) a).drawLegs(unit);
                    return;
                }
            }
        }
        // 未找到 CustomLegsAbility, 回退到原生腿渲染
        super.drawLegs(unit);
    }
}
