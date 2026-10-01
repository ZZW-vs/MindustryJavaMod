package zzw.content.blocks;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Structs;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.Wall;
import zzw.content.optics.Light;
import zzw.content.optics.LightAcceptor;
import zzw.content.optics.LightAcceptorType;
import zzw.content.optics.LightHolder;
import zzw.content.util.Float2;

/**
 * 玻璃墙 (PU132 unity.world.blocks.defense.LightWall 移植)
 *
 * <p>原版 {@code @Merge(base=Wall.class, value=LightHoldc.class)}: 墙体 + 受光能力。
 * 整块墙是一个 "不需要光" 的受光槽 (required=-1), 光射到墙上时会注册一条
 * child 透射光 —— 即玻璃墙让光穿过, 但强度按 {@code suppression} 衰减 (0.8)。</p>
 *
 * <p>★ 与 PU132 的差异: 原版把受光能力织入 Wall 基类;
 * 本移植用 {@link LightHolder} 接口实现 (墙体必须继承 {@link Wall}, 无法同时继承
 * GenericCrafter 系的光持有基类)。</p>
 */
public class LightWall extends Wall {
    /** 透射光强比例 (0.8 = 衰减 20%) */
    public float suppression = 0.8f;
    /** 受光槽定义 (init 时加入一个整块槽) */
    public final Seq<LightAcceptorType> acceptors = new Seq<>();

    public LightWall(String name) {
        super(name);
    }

    @Override
    public void init() {
        super.init();
        // 整块受光槽, required=-1 (不需要光, 仅用于光穿过)
        acceptors.add(new LightAcceptorType() {{
            x = 0;
            y = 0;
            width = size;
            height = size;
            required = -1f;
        }});
    }

    public class LightWallBuild extends WallBuild implements LightHolder {
        public LightAcceptor[] slots;
        public transient boolean needsReinteract;

        @Override
        public void created() {
            super.created();
            int len = acceptors.size;

            slots = new LightAcceptor[len];
            for (int i = 0; i < len; i++) {
                slots[i] = acceptors.get(i).create(this);
            }
        }

        @Override
        public Building building() {
            return this;
        }

        @Override
        public LightAcceptor[] lightSlots() {
            return slots;
        }

        @Override
        public boolean needsReinteract() {
            return needsReinteract;
        }

        @Override
        public boolean acceptLight(Light light, int x, int y) {
            return Structs.contains(slots, e -> e.accepts(light, x, y));
        }

        @Override
        public void addLight(Light light, int x, int y) {
            for (var slot : slots) {
                if (slot.accepts(light, x, y)) slot.add(light);
            }
        }

        @Override
        public void removeLight(Light light) {
            for (var slot : slots) {
                slot.remove(light);
            }
        }

        @Override
        public void interact(Light light) {
            needsReinteract = false;
            // 玻璃墙: 光透射, 强度衰减 suppression (原版 LightWall.interact)
            light.child(l -> Float2.construct(l.rotation, suppression));
        }

        @Override
        public float lightStatus() {
            if (slots.length <= 0) return 1f;

            float val = 0f;
            for (var slot : slots) {
                val += Mathf.clamp(slot.status());
            }

            return Mathf.clamp(val / slots.length);
        }

        @Override
        public boolean requiresLight() {
            return !Structs.contains(slots, e -> !e.requires());
        }

        @Override
        public void draw() {
            super.draw();
            for (var slot : slots) {
                slot.draw();
            }
        }

        @Override
        public void updateTile() {
            super.updateTile();
            for (var slot : slots) {
                slot.update();
            }
        }
    }
}