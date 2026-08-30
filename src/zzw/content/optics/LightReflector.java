package zzw.content.optics;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import zzw.content.util.Float2;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

/**
 * 光反射镜 (PU132 unity.world.blocks.light.LightReflector 移植)
 *
 * <p>把射入的光按镜面反射 (lightRot 为镜面法线朝向):
 * 反射向量 = v - 2(v·n)n; 分光镜 (light-divisor) fallthrough=0.5 额外
 * 透射一半强度的原方向光。</p>
 */
public class LightReflector extends LightHoldBlock {
    private static final Vec2 v1 = new Vec2(), v2 = new Vec2();

    /** 透射光强比例 (>=0 启用分光); 0 = 纯反射镜 */
    public float fallthrough = 0f;

    public TextureRegion baseRegion;

    public LightReflector(String name) {
        super(name);
        solid = true;
        configurable = true;
        outlineIcon = true;

        // 1x1 全格受光槽, 不需要光 (required=-1, 效率恒定)
        acceptors.add(new LightAcceptorType() {{
            x = 0;
            y = 0;
            width = 1;
            height = 1;
            required = -1f;
        }});

        config(Boolean.class, (LightReflectorBuild tile, Boolean value) ->
            tile.lightRot = Mathf.mod(tile.lightRot + (value ? Light.rotationInc : -Light.rotationInc) / 2f, 360f));
    }

    @Override
    public void load() {
        super.load();
        baseRegion = Core.atlas.find(name + "-base");
    }

    @Override
    public float getRotation(Building build) {
        return build instanceof LightReflectorBuild b ? b.lightRot : 0f;
    }

    @Override
    public TextureRegion[] icons() {
        return new TextureRegion[]{baseRegion, region};
    }

    public class LightReflectorBuild extends LightHoldBuild {
        public float lightRot = 90f;

        @Override
        public Object config() {
            return lightRot;
        }

        @Override
        public void interact(Light light) {
            super.interact(light);

            // 反射 child: 镜面反射向量变换 (PU132 原版)
            light.child(l -> {
                synchronized (LightReflector.class) {
                    v1.trnsExact(lightRot, 1f);
                    return Float2.construct(Light.fixRot(v2
                        .trnsExact(l.rotation, 1f)
                        .sub(v1.scl(2 * v2.dot(v1)))
                        .angle()), 1f - fallthrough
                    );
                }
            });

            // 分光镜: 透射 child (原方向, fallthrough 比例)
            if (!Mathf.zero(fallthrough)) {
                light.child(l -> Float2.construct(l.rotation, fallthrough));
            }
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
        public void draw() {
            Draw.rect(baseRegion, x, y);
            Draw.rect(region, x, y, lightRot - 90f);
        }
    }
}