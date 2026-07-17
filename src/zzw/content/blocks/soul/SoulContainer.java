package zzw.content.blocks.soul;

import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;

/**
 * 灵魂容器 (v158 简化版, 替代 PU_V8 SoulContainer)
 *
 * - 存储 SoulInfuser 注入的灵魂
 * - 自身不带注入能力, 只作为存储节点
 * - 炮台扫描时, 会优先从附近的 SoulContainer 提取灵魂 (本简化版不实现此功能, 炮台直接由 SoulInfuser 注入)
 *
 * 完整 PU_V8 版本还实现了附近炮台从容器拉取灵魂的机制, 这里省略以保持简单
 * 参考: PU_V8 unity/world/blocks/effect/SoulContainer.java
 */
public class SoulContainer extends Wall {
    /** 最大灵魂数量 */
    public int maxSouls = 12;

    public SoulContainer(String name) {
        super(name);
        update = true;
        solid = true;
        sync = true;
    }

    public class SoulContainerBuild extends WallBuild {
        public int souls = 0;

        /** 接受多少灵魂 (返回剩余可接受数量) */
        public int acceptSoul(int count) {
            return Math.max(0, maxSouls - souls - (count - 1) > 0 ? count : maxSouls - souls);
        }

        public boolean joinSoul() {
            if (souls < maxSouls) {
                souls++;
                return true;
            }
            return false;
        }

        public boolean unjoinSoul() {
            if (souls > 0) {
                souls--;
                return true;
            }
            return false;
        }

        public int souls() {
            return souls;
        }

        public int maxSouls() {
            return SoulContainer.this.maxSouls;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(souls);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            souls = read.i();
        }
    }
}
