package zzw.content.blocks.production;

import arc.Core;
import arc.func.Cons;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.world.blocks.production.GenericCrafter;
import zzw.content.blocks.soul.ISoulTurret;

/**
 * 灵魂地板抽取器 (PU132 @Merge(FloorExtractor + Soulc) 生成的 SoulFloorExtractor 手动移植)
 *
 * <p>在 FloorExtractor 基础上加入灵魂持有机制 (Soulc):
 * <ul>
 *   <li>建筑持有灵魂数 (souls, 上限 maxSouls), 由灵魂灌注器 (SoulInfuser) 链接输送</li>
 *   <li>效率随灵魂数缩放: soulf * (efficiencyTo - efficiencyFrom) + efficiencyFrom</li>
 *   <li>requireSoul=true 时无灵魂完全停转; =false 时灵魂只提供 0.3~1.0 倍增益</li>
 * </ul>
 * 碎屑提取器 (debris-extractor) 从灵魂地板 (灌注锐板岩/远古锐板岩/远古能量) 提取远古碎屑。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>@Merge 织入 → 继承 FloorExtractor + Build 实现 ISoulTurret (项目简化灵魂体系)</li>
 *   <li>StemData.floatValue → soulWarmup 字段 (热度动画数据)</li>
 *   <li>draw/update 回调 → drawStem/updateStem (同 StemGenericCrafter 模式)</li>
 *   <li>Regions.debrisExtractorHeat1/2Region → name + "-heat1/-heat2" 贴图查找</li>
 * </ul></p>
 */
public class SoulFloorExtractor extends FloorExtractor {
    /** 灵魂容量 */
    public int maxSouls = 3;
    /** 灵魂效率缩放起止 */
    public float efficiencyFrom = 0.3f, efficiencyTo = 1f;
    /** 无灵魂时是否完全停转 */
    public boolean requireSoul = true;
    /** 热度贴图 (debris-extractor-heat1/2) */
    public TextureRegion heatRegion1, heatRegion2;
    /** 热度贴图颜色 (默认 monolith 系) */
    public arc.graphics.Color heatColor = zzw.content.graphics.UnityPal.monolith;
    public arc.graphics.Color heatColorLight = zzw.content.graphics.UnityPal.monolithLight;

    /** 自定义绘制回调 (PU132 注册处 draw(...) 织入) */
    public Cons<SoulFloorExtractorBuild> drawStem = e -> {};
    /** 自定义更新回调 (PU132 注册处 update(...) 织入) */
    public Cons<SoulFloorExtractorBuild> updateStem = e -> {};
    /** 灵魂环特效计时器索引 */
    protected final int effectTimer = timers++;

    public SoulFloorExtractor(String name) {
        super(name);
    }

    public void draw(Cons<SoulFloorExtractorBuild> draw) {
        this.drawStem = draw;
    }

    public void update(Cons<SoulFloorExtractorBuild> update) {
        this.updateStem = update;
    }

    @Override
    public void load() {
        super.load();
        heatRegion1 = Core.atlas.find(name + "-heat1");
        heatRegion2 = Core.atlas.find(name + "-heat2");
    }

    @Override
    public void setStats() {
        super.setStats();
        zzw.content.util.StatUtils.roundOutputStats(this);
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


    public class SoulFloorExtractorBuild extends FloorExtractorBuild implements ISoulTurret {
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
         * <p>★ v155.4 适配: efficiency 是 BuildingComp 的字段而非可覆写方法,
         * 改为覆写 getProgressIncrease 让灵魂倍率作用于制作进度 (与炮台侧
         * soulEfficiency() 乘算 reload 的做法一致)。</p>
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