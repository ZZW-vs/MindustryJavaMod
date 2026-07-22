package zzw.content.blocks.distribution;

import arc.Core;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;

/**
 * ShadowedConveyor 机械传送带 (移植自 PU_V8 unity.world.blocks.distribution.ShadowedConveyor)
 *
 * 机制完全照搬原版:
 * - 继承自原版 Conveyor
 * - 额外渲染一层 shadowRegion (影子贴图) 用于视觉过渡
 * - 若前方无同种传送带 (nextc == null || block != nextc.block), 正方向画一层影子
 * - 若 back/left/right 没有同种朝向的传送带, 反向 (rotdeg()+180f) 再画一层影子
 *
 * 注册: mechanical-conveyor (机械传送带)
 * 参考: PU_V8 UnityBlocks.java L2910
 */
public class ShadowedConveyor extends Conveyor {
    public TextureRegion shadowRegion;

    public ShadowedConveyor(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        shadowRegion = Core.atlas.find(name + "-shadow");
    }

    public class ShadowedConveyorBuild extends ConveyorBuild {
        boolean looking;

        @Override
        public void draw() {
            super.draw();
            Draw.z(Layer.block);
            // 前方无同种传送带 -> 正方向画影子
            if (nextc == null || block != nextc.block) Draw.rect(shadowRegion, x, y, rotdeg());
            // back/left/right 无同种朝向传送带 -> 反向画影子
            if (!looking) Draw.rect(shadowRegion, x, y, rotdeg() + 180f);
        }

        @Override
        public void onProximityUpdate() {
            super.onProximityUpdate();
            // PU_V8 原版: back/left/right 是局部变量, 通过 back()/left()/right() 方法获取
            Building backBuilding = back();
            Building leftBuilding = left();
            Building rightBuilding = right();
            Tile back = backBuilding != null ? backBuilding.tile : tile;
            Tile left = leftBuilding != null ? leftBuilding.tile : tile;
            Tile right = rightBuilding != null ? rightBuilding.tile : tile;

            // 检查 back/left/right 三向是否有同种朝向的传送带
            looking = ((back.relativeTo(tile) - back.build.rotation) == 0 && back.build.block == block)
                   || ((left.relativeTo(tile) - left.build.rotation) == 0 && left.build.block == block)
                   || ((right.relativeTo(tile) - right.build.rotation) == 0 && right.build.block == block);
        }
    }
}
