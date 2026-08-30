package zzw.content.optics;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.GenericCrafter.GenericCrafterBuild;
import mindustry.world.meta.Stat;

/**
 * 光持有方块 (PU132 @Merge(LightHoldc) 织入体系的手动移植基类)
 *
 * <p>持有受光槽 (LightAcceptor): 光射入槽内影响建筑效率 ——
 * 全部槽 requires 时 efficiency *= min(lightStatus,1), 任一槽不 fulfilled 则
 * shouldConsume=false (原版 consValid 织入)。</p>
 *
 * <p>★ 与 PU132 的差异: 原版 @Merge 到多个基类 (GenericCrafter/Block 等),
 * 本移植固定织入 GenericCrafter (光源/反射镜/光工厂全是 crafter 系);
 * 反射镜等无需配方的方块 craftTime 不影响 (shouldConsume 恒 false 时不开工)。</p>
 */
public class LightHoldBlock extends GenericCrafter {
    public final Seq<LightAcceptorType> acceptors = new Seq<>();

    public LightHoldBlock(String name) {
        super(name);
        update = true;
        sync = true;
        destructible = true;
    }

    /** 光持有建筑的朝向 (光源/反射镜的 lightRot; 决定 DrawLightBlock 旋转) */
    public float getRotation(Building build) {
        return 0f;
    }

    /** 受光输入条颜色 (偏暗的白色) */
    public static final arc.graphics.Color LIGHT_BAR_COLOR = new arc.graphics.Color(0.72f, 0.72f, 0.72f, 1f);

    /** 受光输入条: 显示当前光照输入比例 (需光的工厂如 light-forge) */
    @Override
    public void setBars() {
        super.setBars();
        // Block 层无法预知 slots (created 时才建), 用 acceptors 判断是否有需光槽
        boolean requires = arc.util.Structs.contains(acceptors.toArray(), a -> a.required > 0f);
        if (requires) {
            addBar("light", (LightHoldBuild build) -> new mindustry.ui.Bar(
                () -> arc.Core.bundle.get("bar.light", "Light") + " " + (int)(build.lightStatus() * 100) + "%",
                () -> LIGHT_BAR_COLOR,
                build::lightStatus
            ));
        }
    }

    public class LightHoldBuild extends GenericCrafterBuild {
        public LightAcceptor[] slots;
        /** 光指向需重新交互 (转动后) */
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

        public boolean acceptLight(Light light, int x, int y) {
            return arc.util.Structs.contains(slots, e -> e.accepts(light, x, y));
        }

        public void addLight(Light light, int x, int y) {
            for (var slot : slots) {
                if (slot.accepts(light, x, y)) slot.add(light);
            }
        }

        public void removeLight(Light light) {
            for (var slot : slots) {
                slot.remove(light);
            }
        }

        /** 光触到本建筑时同步回调 (反射镜在此注册 child) */
        public void interact(Light light) {
            needsReinteract = false;
        }

        public float lightStatus() {
            if (slots.length <= 0) return 1f;

            float val = 0f;
            for (var slot : slots) {
                val += Mathf.clamp(slot.status());
            }

            return Mathf.clamp(val / slots.length);
        }

        public boolean requiresLight() {
            return !arc.util.Structs.contains(slots, e -> !e.requires());
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

        /** 光照效率 (原版 efficiency 织入: requiresLight 时乘 lightStatus) */
        @Override
        public float getProgressIncrease(float baseTime) {
            return super.getProgressIncrease(baseTime) * (requiresLight() ? Math.min(lightStatus(), 1f) : 1f);
        }

        /** 任一槽未满足则不消耗 (原版 consValid 织入) */
        @Override
        public boolean shouldConsume() {
            return super.shouldConsume() && (!requiresLight() || !arc.util.Structs.contains(slots, e -> !e.fulfilled()));
        }

        @Override
        public void write(Writes write) {
            super.write(write);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
        }
    }
}