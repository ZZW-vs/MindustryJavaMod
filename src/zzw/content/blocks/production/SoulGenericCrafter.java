package zzw.content.blocks.production;

import arc.func.Cons;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.world.blocks.production.GenericCrafter;
import zzw.content.blocks.soul.ISoulTurret;
import zzw.content.util.StatUtils;

/**
 * 灵魂工厂 (PU132 @Merge(GenericCrafter + Soulc) 生成的 SoulGenericCrafter 手动移植)
 *
 * <p>在 GenericCrafter 基础上加入灵魂持有机制 (Soulc), 与 SoulFloorExtractor 相同:
 * 灵魂灌注器 (SoulInfuser) 链接输送灵魂, 效率随灵魂数缩放 (0.3~1.0)。
 * 巨石合金锻造厂 (monolith-alloy-forge) 消耗远古碎屑+巨石合成巨石合金。</p>
 *
 * <p>适配说明: 同 SoulFloorExtractor (@Merge 织入 → ISoulTurret 实现 + draw/update 回调)。</p>
 */
public class SoulGenericCrafter extends GenericCrafter {
    /** 灵魂容量 */
    public int maxSouls = 3;
    /** 灵魂效率缩放起止 */
    public float efficiencyFrom = 0.3f, efficiencyTo = 1f;
    /** 无灵魂时是否完全停转 */
    public boolean requireSoul = true;

    /** 自定义绘制回调 (PU132 注册处 draw(...) 织入) */
    public Cons<SoulGenericCrafterBuild> drawStem = e -> {};
    /** 自定义更新回调 (PU132 注册处 update(...) 织入) */
    public Cons<SoulGenericCrafterBuild> updateStem = e -> {};
    /** 灵魂环特效计时器索引 */
    protected final int effectTimer = timers++;

    public SoulGenericCrafter(String name) {
        super(name);
    }

    public void draw(Cons<SoulGenericCrafterBuild> draw) {
        this.drawStem = draw;
    }

    public void update(Cons<SoulGenericCrafterBuild> update) {
        this.updateStem = update;
    }

    @Override
    public void setStats() {
        super.setStats();
        StatUtils.roundOutputStats(this);
        // 灵魂系统信息面板 (PU132 SoulComp.setStats: 需要/可选灵魂 + 容量)
        stats.add(mindustry.world.meta.Stat.abilities, cont -> {
            cont.row();
            cont.table(bt -> {
                bt.left().defaults().padRight(3f).left();

                bt.row();
                bt.add(arc.Core.bundle.get(requireSoul ? "soul.require" : "soul.optional"));

                if (maxSouls > 0) {
                    bt.row();
                    bt.add(arc.Core.bundle.format("soul.max", maxSouls));
                }
            });
        });
    }


    public class SoulGenericCrafterBuild extends GenericCrafterBuild implements ISoulTurret {
        /** 当前灵魂数 */
        public int souls;
        /** 灵魂热度动画 (PU132 StemData.floatValue) */
        public float soulWarmup;

        @Override
        public int souls() {
            return souls;
        }

        @Override
        public int maxSouls() {
            return maxSouls;
        }

        @Override
        public boolean joinSoul() {
            if (souls < maxSouls) {
                souls++;
                return true;
            }
            return false;
        }

        @Override
        public boolean unjoinSoul() {
            if (souls > 0) {
                souls--;
                return true;
            }
            return false;
        }

        @Override
        public boolean requireSoul() {
            return requireSoul;
        }

        @Override
        public float efficiencyFrom() {
            return efficiencyFrom;
        }

        @Override
        public float efficiencyTo() {
            return efficiencyTo;
        }

        /**
         * 灵魂效率乘算 (PU132 SoulComp.efficiency 织入语义):
         * 进度增速 * (soulf * (to - from) + from), requireSoul 且无灵魂时为 0
         * <p>★ v155.4 适配: efficiency 是字段, 覆写 getProgressIncrease 作用于制作进度。</p>
         */
        @Override
        public float getProgressIncrease(float baseTime) {
            return super.getProgressIncrease(baseTime) * soulEfficiency();
        }

        @Override
        public void updateTile() {
            super.updateTile();
            updateStem.get(this);
        }

        @Override
        public void draw() {
            super.draw();
            drawStem.get(this);
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