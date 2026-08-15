package zzw.content.blocks.production;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.IntSeq;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import zzw.content.blocks.soul.ISoulTurret;

import static mindustry.Vars.*;

/**
 * 灵魂灌注器 (PU132 unity.world.blocks.production.SoulInfuser 移植)
 * <p>继承 FloorExtractor, 手动实现 Soulc 功能。可配置, 链接范围内的灵魂炮台
 * (实现了 ISoulTurret 接口的建筑), 制作完成时向它们输送灵魂。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>@Merge(FloorExtractor + Soulc) → extends FloorExtractor + 手动实现灵魂逻辑</li>
 *   <li>SoulContainer 引用 → 简化为 ISoulTurret (zzw.content.blocks.soul),
 *       因为 SoulContainer 不存在于本项目</li>
 *   <li>acceptSoul()/join() (Soulc 接口) → ISoulTurret.souls()/maxSouls()/joinSoul()</li>
 *   <li>自身不再实现 Soulc (生产者不需要接受灵魂), shouldConsume 仅检查链接炮台</li>
 * </ul></p>
 */
public class SoulInfuser extends FloorExtractor{
    /** 每次制作产生的灵魂数量 */
    public int amount = 1;
    /** 最大链接炮台数 */
    public int maxContainers = 3;
    /** 链接范围 (格) */
    public float range = 15f;

    public SoulInfuser(String name){
        super(name);

        configurable = true;
        outputItem = null;
        outputLiquid = null;

        // 注册配置处理器: 点击炮台时切换链接状态
        config(Integer.class, (SoulInfuserBuild build, Integer value) -> {
            if(build.containers.contains(value)){
                build.containers.removeValue(value);
            }else if(build.containers.size < maxContainers){
                build.containers.add(value);
            }
        });
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range * tilesize, Pal.accent);
    }

    public class SoulInfuserBuild extends FloorExtractorBuild{
        /** 链接的炮台位置列表 */
        public IntSeq containers = new IntSeq();

        @Override
        public void placed(){
            if(net.client()) return;
            super.placed();
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            // 点击范围内的灵魂炮台时切换链接
            if(other instanceof ISoulTurret && within(other, range * tilesize)){
                configure(other.pos());
                return false;
            }
            return true;
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            Lines.stroke(1f);
            Draw.color(Pal.accent);
            Drawf.circles(x, y, range * tilesize);
            Draw.reset();
        }

        @Override
        public void drawConfigure(){
            // 绘制自身选择圈和范围圈
            Drawf.circles(x, y, block.size * tilesize / 2f + 1f + Mathf.absin(Time.time, 4f, 1f));
            Drawf.circles(x, y, range * tilesize);

            // 绘制已链接炮台的标记
            for(int i = 0; i < containers.size; i++){
                Building build = world.build(containers.get(i));
                if(build != null && build.isValid()){
                    Drawf.square(build.x, build.y, build.block.size * tilesize / 2f + 1f, Pal.place);
                }
            }

            Draw.reset();
        }

        @Override
        public boolean shouldConsume(){
            // 有任何链接炮台需要灵魂时才消耗
            for(int i = 0; i < containers.size; i++){
                Building build = world.build(containers.items[i]);
                if(build instanceof ISoulTurret t && t.souls() < t.maxSouls()){
                    return true;
                }
            }
            return false;
        }

        @Override
        public void consume(){
            super.consume();

            // 向链接的炮台输送灵魂
            int sent = 0;
            for(int i = 0; i < containers.size && sent < amount; i++){
                Building build = world.build(containers.items[i]);
                if(build instanceof ISoulTurret t && t.souls() < t.maxSouls()){
                    if(t.joinSoul()) sent++;
                }
            }
        }
    }
}
