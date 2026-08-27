package zzw.content.exp;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.blocks.production.Incinerator;

/**
 * 经验焚化炉 (PU 经验系统扩展方块)
 *
 * <p>基于原版焚化炉 (Incinerator) 改造: 焚化任意物品与液体,
 * 每焚化 {@link #itemsPerOrb} 个物品 (可为不同种类) 或
 * {@link #liquidPerOrb} 单位液体, 弹射出一个经验球 (10 Exp.),
 * 附近经验持有者 (炮台/储罐/墙) 飞过拾取即可获得经验。</p>
 *
 * <p>与原版差异:
 * <ul>
 *   <li>耗电: 0.5 → 0.7 (每 tick, 比原版多 0.2)</li>
 *   <li>液体: 接受所有液体 (原版仅 incinerable), 容量 20</li>
 *   <li>产出: 焚化积累到阈值时弹出经验球</li>
 * </ul></p>
 *
 * <p>注: 经验球飞行时不会被本方块回收 (见 ExpOrbs.ExpOrb.update 的排除判断),
 * 避免刚弹出就被自身吞掉。</p>
 */
public class ExpIncinerator extends Incinerator{
    /** 每焚化多少个物品产出一个经验球 (物品可为不同种类) */
    public int itemsPerOrb = 5;
    /** 每焚化多少液体单位产出一个经验球 */
    public float liquidPerOrb = 10f;

    public ExpIncinerator(String name){
        super(name);

        // 经验绿火焰
        flameColor = Color.valueOf("84ff00");
        effect = Fx.fuelburn;
    }

    public class ExpIncineratorBuild extends IncineratorBuild{
        /** 已焚化物品计数 (累计到 itemsPerOrb 弹球后清零) */
        private int itemCount;
        /** 已焚化液体量累计 (单位: 液体单位) */
        private float liquidAmount;

        @Override
        public void handleItem(Building source, Item item){
            super.handleItem(source, item);

            // 计数累计, 达到阈值弹出经验球 (10 Exp./球)
            if(++itemCount >= itemsPerOrb){
                itemCount = 0;
                ExpOrbs.spreadExp(x, y, ExpOrbs.expAmount);
            }
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            super.handleLiquid(source, liquid, amount);

            // 液体按单位量累计, 每 liquidPerOrb 单位弹一球 (循环处理单次大量注入)
            liquidAmount += amount;
            while(liquidAmount >= liquidPerOrb){
                liquidAmount -= liquidPerOrb;
                ExpOrbs.spreadExp(x, y, ExpOrbs.expAmount);
            }
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            // 覆写原版: 接受所有液体 (原版仅 liquid.incinerable), 仍需通电升温
            return heat > 0.5f && enabled;
        }
    }
}
