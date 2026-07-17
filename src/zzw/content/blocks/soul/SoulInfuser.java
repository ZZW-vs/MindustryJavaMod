package zzw.content.blocks.soul;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.core.World;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Posc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.logic.LAccess;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * 灵魂注入器 (v158 简化版, 替代 PU_V8 SoulInfuser)
 *
 * - 简化版: 不依赖 FloorExtractor (地板抽取器), 直接消耗物品+电力产生灵魂
 * - 产生灵魂时, 优先注入到附近 SoulContainer (灵魂容器), 没有则直接注入附近炮台
 * - SoulInfuser 接受 SoulContainer 配置, 通过点击配置链接容器
 *
 * 参考: PU_V8 unity/world/blocks/production/SoulInfuser.java
 */
public class SoulInfuser extends GenericCrafter {
    /** 每次注入产生的灵魂数量 */
    public int amount = 1;
    /** 最大容器链接数 */
    public int maxContainers = 3;
    /** 扫描范围 (单位: tile) */
    public float range = 15f;
    /** 注入特效 */
    public Effect injectEffect = null;

    public SoulInfuser(String name) {
        super(name);
        configurable = true;
        hasPower = true;
        consumesPower = true;
        hasItems = true;
        outputItem = null;
        sync = true;

        config(Integer.class, (SoulInfuserBuild build, Integer value) -> {
            if (build.containers.contains(value)) {
                build.containers.removeValue(value);
            } else if (build.containers.size < maxContainers) {
                build.containers.add(value);
            }
        });
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range * tilesize, Pal.accent);
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.range, range, StatUnit.blocks);  // v158 无 StatUnit.tiles, 用 blocks 替代
    }

    @Override
    public boolean outputsItems() {
        return false;
    }

    public class SoulInfuserBuild extends GenericCrafterBuild {
        public IntSeq containers = new IntSeq();

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (other instanceof SoulContainer.SoulContainerBuild && other.within(this, range * tilesize)) {
                configure(other.pos());
                return false;
            }
            return true;
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            Lines.stroke(1f);
            Draw.color(Pal.accent);
            Drawf.circles(x, y, range * tilesize);
            Draw.reset();
        }

        @Override
        public void drawConfigure() {
            Drawf.circles(x, y, block.size * tilesize / 2f + 1f + Mathf.absin(Time.time, 4f, 1f));
            Drawf.circles(x, y, range * tilesize);

            for (int i = 0; i < containers.size; i++) {
                Building build = world.build(containers.get(i));
                if (build != null && build.isValid()) {
                    Drawf.square(build.x, build.y, build.block.size * tilesize / 2f + 1f, Pal.place);
                }
            }
            Draw.reset();
        }

        @Override
        public boolean shouldConsume() {
            // 优先检查链接的容器
            for (int i = 0; i < containers.size; i++) {
                Building build = world.build(containers.get(i));
                if (build instanceof SoulContainer.SoulContainerBuild cont && cont.acceptSoul(1) > 0) {
                    return true;
                }
            }
            // 检查附近未满灵魂的炮台
            return findNearbyTurretNeedingSoul() != null;
        }

        @Override
        public void craft() {
            super.craft();
            int sent = 0;

            // 先尝试发送给链接的容器
            for (int i = 0; i < containers.size && sent < amount; i++) {
                Building build = world.build(containers.get(i));
                if (build instanceof SoulContainer.SoulContainerBuild cont && cont.acceptSoul(amount - sent) > 0) {
                    while (cont.acceptSoul(1) > 0 && sent < amount) {
                        cont.joinSoul();
                        sent++;
                    }
                }
            }

            // 容器未填满, 直接注入附近炮台
            while (sent < amount) {
                ISoulTurret target = findNearbyTurretNeedingSoul();
                if (target instanceof Building b && b.isValid()) {
                    if (((ISoulTurret) b).joinSoul()) {
                        sent++;
                        if (injectEffect != null) {
                            injectEffect.at(b.x, b.y);
                        }
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        /** 扫描附近需要灵魂的炮台 */
        public ISoulTurret findNearbyTurretNeedingSoul() {
            float r = range * tilesize;
            Seq<Building> nearby = new Seq<>();
            // 用 world.build 扫描附近格子
            int tx = World.toTile(x), ty = World.toTile(y);
            int tr = (int) (range);
            for (int dx = -tr; dx <= tr; dx++) {
                for (int dy = -tr; dy <= tr; dy++) {
                    Building b = world.build(tx + dx, ty + dy);
                    if (b != null && !nearby.contains(b) && b.within(this, r) && b instanceof ISoulTurret) {
                        ISoulTurret st = (ISoulTurret) b;
                        if (st.souls() < st.maxSouls()) {
                            return st;
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(containers.size);
            for (int i = 0; i < containers.size; i++) {
                write.i(containers.get(i));
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int size = read.i();
            containers.clear();
            for (int i = 0; i < size; i++) {
                containers.add(read.i());
            }
        }
    }
}
