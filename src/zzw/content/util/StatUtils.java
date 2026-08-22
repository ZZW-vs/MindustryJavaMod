package zzw.content.util;

import arc.util.Strings;
import mindustry.type.ItemStack;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.iconXLarge;
import mindustry.ui.Styles;

/**
 * 方块统计工具 (PU 工厂移植辅助)
 *
 * <p>v158 原版输出物品速率用 autoFixed(v, 3) 最多显示 3 位小数
 * (如致密熔炉 1.299/秒), 用户要求四舍五入到最多 2 位小数。
 * 原版格式化在 {@code StatValues.displayItem} 内部写死, 无法按方块定制,
 * 这里提供替换用的自定义 StatValue (显示格式与原版一致, 仅小数位数不同)。</p>
 */
public class StatUtils{

    /**
     * 替换工厂的输出物品统计为 2 位小数版本.
     * <p>在 {@code setStats()} 中调用 {@code super.setStats()} 之后调用:
     * 先移除原版自动添加的 3 位小数输出统计, 再以 2 位小数重新添加。</p>
     */
    public static void roundOutputStats(GenericCrafter block){
        block.stats.remove(Stat.output);

        if(block.outputItems != null && block.outputItems.length > 0){
            GenericCrafter c = block;
            block.stats.add(Stat.output, table -> {
                for(ItemStack s : c.outputItems){
                    table.image(s.item.uiIcon).size(iconXLarge).padRight(4).right();
                    table.add(s.item.localizedName + "\n[lightgray]" + Strings.autoFixed(s.amount / (c.craftTime / 60f), 2) + StatUnit.perSecond.localized()).padLeft(2).padRight(5).style(Styles.outlineLabel);
                }
            });
        }
    }
}
