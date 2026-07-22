package zzw.content.blocks.distribution;

import mindustry.world.blocks.distribution.*;

/**
 * KoruhConveyor 钢制传送带 (移植自 PU_V8 unity.world.blocks.distribution.KoruhConveyor)
 *
 * 机制完全照搬原版:
 * - 继承自原版 Conveyor
 * - absorbLasers = true 作为"区分 exp 传送带"的内部标志, 同时让激光对 koruh 阵营效果减弱
 * - drawMultiplier: 仅在 draw() 时把 speed 临时放大, 影响视觉动画速度
 *   实际物品运输速度 (realSpeed) 不变
 *
 * 注册: steel-conveyor (钢制传送带)
 * 参考: PU_V8 UnityBlocks.java L1686-1694
 */
public class KoruhConveyor extends Conveyor {
    protected float realSpeed, drawMultiplier;

    public KoruhConveyor(String name) {
        super(name);
        // absorbLasers: 内部标志, 用于 ExpOrbs 区分 exp 传送带 (ExpOrbs.expConveyor 检查此标志)
        // 同时让激光对 koruh 阵营效果减弱
        absorbLasers = true;
    }

    @Override
    public void load() {
        super.load();
        realSpeed = speed;
    }

    public class KoruhConveyorBuild extends ConveyorBuild {
        @Override
        public void draw() {
            // draw 时临时放大 speed 影响动画, 实际运输速度不变
            speed = realSpeed * drawMultiplier;
            super.draw();
            speed = realSpeed;
        }
    }
}
