package zzw.content.exp;

import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import mindustry.Vars;
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
 *   <li>产出: 焚化积累到阈值时弹出经验球, 弹出速率有上限
 *       (每秒最多 {@link #maxOrbsPerSecond} 个)</li>
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
    /** 产出速率上限: 每秒最多弹出多少个经验球 */
    public float maxOrbsPerSecond = 1.2f;

    public ExpIncinerator(String name){
        super(name);

        // 可旋转: 经验球朝方块朝向弹出 (放置时 R 键调整方向)
        rotate = true;

        // 经验绿火焰
        flameColor = Color.valueOf("84ff00");
        effect = Fx.fuelburn;
    }

    public class ExpIncineratorBuild extends IncineratorBuild{
        /** 已焚化物品计数 (累计到 itemsPerOrb 折算为 1 颗粒进度) */
        private int itemCount;
        /** 已焚化液体量累计 (单位: 液体单位) */
        private float liquidAmount;
        /** 待弹出颗粒数 (焚化折算完成, 因速率上限暂未弹出的部分) */
        private float orbProgress;
        /** 弹球冷却计时 (秒, <=0 时才允许弹球) */
        private float spawnCooldown;

        @Override
        public void updateTile(){
            super.updateTile();

            // 冷却计时递减
            if(spawnCooldown > 0f) spawnCooldown -= delta();

            // 有待弹出颗粒且冷却结束 → 朝方块朝向弹出 1 个, 重置冷却为 1/速率
            // (每次只弹 1 个, 下一帧继续, 保证不超过 maxOrbsPerSecond;
            //  dropExp 按指定角度发射, 颗粒飞向朝向一侧的拾取者而非随机散开)
            if(orbProgress >= 1f && spawnCooldown <= 0f){
                orbProgress -= 1f;
                spawnCooldown = 1f / maxOrbsPerSecond;
                // ★ 从方块朝向一侧 2 格外生成 (tilesize=8, 2格=16px):
                //   之前 offset = tilesize/2-1 = 3px 仍在 1x1 方块内部 (半宽 4px),
                //   颗粒生成在工厂自身 tile 上被 solid 判定弹回卡住;
                //   2 格外生成确保完全离开工厂方块, 弹出距离明显
                float offset = Vars.tilesize * 2f;
                float ex = x + Angles.trnsx(rotdeg(), offset);
                float ey = y + Angles.trnsy(rotdeg(), offset);
                // 弹出速度 6 (默认 4), 颗粒飞得更远更容易被拾取者接住
                ExpOrbs.dropExp(ex, ey, rotdeg(), 6f, ExpOrbs.expAmount);
            }
        }

        @Override
        public void handleItem(Building source, Item item){
            super.handleItem(source, item);

            // 计数累计, 达到阈值折算为 1 颗粒进度 (实际弹出由 updateTile 限速)
            if(++itemCount >= itemsPerOrb){
                itemCount = 0;
                orbProgress = Mathf.clamp(orbProgress + 1f, 0f, 100f);
            }
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            super.handleLiquid(source, liquid, amount);

            // 液体按单位量累计, 每 liquidPerOrb 单位折算为 1 颗粒进度 (循环处理单次大量注入)
            liquidAmount += amount;
            while(liquidAmount >= liquidPerOrb){
                liquidAmount -= liquidPerOrb;
                orbProgress = Mathf.clamp(orbProgress + 1f, 0f, 100f);
            }
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            // 覆写原版: 接受所有液体 (原版仅 liquid.incinerable), 仍需通电升温
            return heat > 0.5f && enabled;
        }
    }
}
