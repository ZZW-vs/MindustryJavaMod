package zzw.content.units.weapons;

import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.Weapon;

/**
 * 隐形武器 (无贴图武器基类)。
 *
 * <p>PU132 中大量单位武器是 {@code new Weapon(){{...}}} 无名武器 —— 仅作为
 * 射击挂点/弹道来源, 本体不绘制 (如 opticaecus 的中央激光炮、zena 的磁轨炮)。</p>
 *
 * <p>★ v158 行为差异: 原版 {@link Weapon#load()} 对空名武器也会执行
 * {@code atlas.find("")}, 得到紫黑 error 贴图且 {@code region.found()==true},
 * 导致 {@code Weapon.draw} 在单位身上画出一块错误贴图 (PU132 的自研渲染
 * 会跳过空贴图, 故原版看不出来)。本类覆写全部绘制方法为空, 恢复
 * "无名 = 不绘制" 的原版视觉语义, 射击/音效/弹道不受影响。</p>
 *
 * @author zzw
 */
public class BlankWeapon extends Weapon{

    public BlankWeapon(){
        super();
    }

    /** 不绘制武器本体 (无贴图)。 */
    @Override
    public void draw(Unit unit, WeaponMount mount){
    }

    /** 不绘制武器轮廓。 */
    @Override
    public void drawOutline(Unit unit, WeaponMount mount){
    }
}
