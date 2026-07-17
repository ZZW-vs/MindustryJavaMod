package zzw.content.blocks.soul;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.core.World;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import arc.struct.Seq;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * 灵魂注入器 (v158 简化版, 替代 PU_V8 SoulInfuser)
 *
 * - 简化版: 不依赖 FloorExtractor (地板抽取器), 直接消耗物品+电力产生灵魂
 * - 产生灵魂时, 直接注入附近炮台
 *
 * 参考: PU_V8 unity/world/blocks/production/SoulInfuser.java
 */
public class SoulInfuser extends GenericCrafter {
    /** 每次注入产生的灵魂数量 */
    public int amount = 1;
    /** 扫描范围 (单位: tile) */
    public float range = 15f;
    /** 注入特效 */
    public Effect injectEffect = null;

    public SoulInfuser(String name) {
        super(name);
        hasPower = true;
        consumesPower = true;
        hasItems = true;
        outputItem = null;
        sync = true;
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range * tilesize, Pal.accent);
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.range, range, StatUnit.blocks);
    }

    @Override
    public boolean outputsItems() {
        return false;
    }

    public class SoulInfuserBuild extends GenericCrafterBuild {

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
            Draw.reset();
        }

        @Override
        public boolean shouldConsume() {
            return findNearbyTurretNeedingSoul() != null;
        }

        @Override
        public void craft() {
            super.craft();
            int sent = 0;
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
    }
}
