package zzw.content.optics;

import arc.scene.ui.layout.Table;
import zzw.content.util.SVec2;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Icon;
import mindustry.gen.Building;
import mindustry.ui.Styles;

/**
 * 光源方块 (PU132 unity.world.blocks.light.LightSource 移植)
 *
 * <p>灯: 产生一束可旋转 (22.5° 步进) 的光, 光强 = 效率 * lightProduction。
 * created() 创建 Light 实体并 queueAdd; onRemoved 时 queueRemove;
 * updateTile 持续同步位置/朝向/强度。</p>
 */
public class LightSource extends LightHoldBlock {
    /** 产生的基础光强 */
    public float lightProduction = 1f;

    public LightSource(String name) {
        super(name);
        solid = true;
        configurable = true;
        outlineIcon = true;

        config(Boolean.class, (LightSourceBuild tile, Boolean value) ->
            tile.lightRot = Light.fixRot(tile.lightRot + (value ? Light.rotationInc : -Light.rotationInc)));
    }

    @Override
    public float getRotation(Building build) {
        return build instanceof LightSourceBuild b ? b.lightRot : 0f;
    }

    public class LightSourceBuild extends LightHoldBuild {
        public Light light;
        public float lightRot = 90f;

        @Override
        public Object config() {
            return lightRot;
        }

        @Override
        public void created() {
            super.created();

            light = Light.create();
            light.queuePosition = SVec2.construct(x, y);
            light.queueRotation = lightRot;
            light.queueSource = this;

            light.queueAdd();
        }

        @Override
        public void onRemoved() {
            light.queueRemove();
            super.onRemoved();
        }

        @Override
        public void updateTile() {
            super.updateTile();

            light.queuePosition = SVec2.construct(x, y);
            light.queueRotation = lightRot;
            light.queueSource = this;
            light.queueStrength = efficiency * lightProduction;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.button(Icon.left, Styles.cleari, () -> configure(true)).size(40f);
            table.button(Icon.right, Styles.cleari, () -> configure(false)).size(40f);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(lightRot);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            lightRot = read.f();
        }

        @Override
        public void afterRead() {
            super.afterRead();
            // 读档后光源实体未创建 (created 不触发), 补建
            if (light == null) {
                light = Light.create();
                light.queuePosition = SVec2.construct(x, y);
                light.queueRotation = lightRot;
                light.queueSource = this;
                light.queueAdd();
            }
        }
    }
}