package zzw.content.blocks.effect;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Items;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
import mindustry.world.Block;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeItems;
import mindustry.type.ItemStack;

import static mindustry.Vars.tilesize;

/**
 * 强化器 (PU132 unity.world.blocks.effect.Reinforcer 完整移植)
 *
 * <p>功能: 持续搜寻范围内未装甲化的友方单位, 旋转炮口对准后发射激光,
 * 为其施加永久的"装甲化"状态 (plated: 血量x2 / 伤害x1.5 / 装填x1.2 / 速度x0.75),
 * 每次强化消耗一次物品配方。</p>
 *
 * <p>移植适配 v155.4:
 * <ul>
 *   <li>UnityStatusEffects.plated → 类内静态注册 (构造时创建, permanent 状态)</li>
 *   <li>UnityFx.plated → zzw.content.graphics.UnityFx.platedFx</li>
 *   <li>"unity-pointy-laser" → "create-pointy-laser" (mod 贴图前缀规则)</li>
 *   <li>consValid() → efficiency > 0 (v155.4 消费者效率机制)</li>
 *   <li>consumes.getItem().items → init() 时从 consumers 数组提取 ConsumeItems.items</li>
 *   <li>acceptsItems 字段 → v155.4 由消费者自动推导, 移除显式赋值</li>
 * </ul></p>
 */
public class Reinforcer extends Block {
    /** 装甲化状态效果 (PU132 UnityStatusEffects.plated): 永久, 移动时随机粒子 */
    public static StatusEffect plated;

    /** 搜寻范围 */
    public float range = 60f;
    public TextureRegion baseRegion, laserRegion, laserEndRegion;
    /** 激光发射点离方块中心的距离 (默认居中偏移半个方块) */
    public float laserLength = -1f;
    /** 激光颜色 (默认电涌合金色) */
    public Color laserColor = Pal.thoriumPink;
    /** 炮口旋转速度 (度/tick) */
    public float rotateSpeed = 2f;
    /** 强化充能阈值 (load 达到后才能强化) */
    public float loadThreshold = 1f;
    /** 对准判定锥角 (度) */
    public float cone = 2f;

    /** 注册时填充: 强化一次需要移除的物品 (来自 ConsumeItems 消费者) */
    protected ItemStack[] reinforceItems = ItemStack.empty;

    public Reinforcer(String name) {
        super(name);
        update = true;
        hasItems = true;
        outlineIcon = true;

        // 装甲化状态效果 (仅创建一次, PU132 UnityStatusEffects.plated)
        if (plated == null) {
            plated = new StatusEffect("plated") {{
                speedMultiplier = 0.75f;
                damageMultiplier = 1.5f;
                healthMultiplier = 2f;
                reloadMultiplier = 1.2f;
                permanent = true;
                effect = zzw.content.graphics.UnityFx.platedFx;
                effectChance = 0.4f;
            }};
        }
    }


    @Override
    public void init() {
        super.init();
        if (laserLength < 0) laserLength = size * tilesize / 2f;

        // 提取 ConsumeItems 的物品列表 (强化一次的消耗)
        for (Consume c : consumers) {
            if (c instanceof ConsumeItems ci) {
                reinforceItems = ci.items;
                break;
            }
        }
    }

    @Override
    public void load() {
        super.load();

        baseRegion = Core.atlas.find("block-" + size);
        laserRegion = Core.atlas.find("create-pointy-laser");
        laserEndRegion = Core.atlas.find("create-pointy-laser-end");
    }

    @Override
    public TextureRegion[] icons() {
        return new TextureRegion[]{baseRegion, region};
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        mindustry.graphics.Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range, Pal.placing);
    }

    public class ReinforcerBuilding extends Building {
        /** 当前充能 (达到 loadThreshold 后可强化) */
        public float load = loadThreshold;
        /** 激光宽度动画 (强化后从 0 恢复到 0.3) */
        public float laserWidth = 0.3f;
        /** 炮口当前朝向 */
        public float rotation = 90f;
        /** 目标朝向 */
        public float targetRot = 90f;
        /** 激光发射点偏移 */
        public Vec2 posOffset = new Vec2(0, 0);
        /** 当前锁定的目标单位 */
        public Unit unit;
        /** 是否对准可强化 */
        public boolean canReinforce = false;
        /** 上一个强化过的单位 (激光动画残留) */
        public Unit prevUnit;

        @Override
        public void updateTile() {
            // 适配 v155.4: consValid() → efficiency > 0 (电力+物品均满足)
            if (efficiency > 0) {
                // 搜寻范围内未装甲化且非核心生成的友方单位 (PU132 原版谓词)
                unit = Units.closest(this.team, x, y, range,
                    u -> u != null && !u.hasEffect(plated) && !u.spawnedByCore);

                if (unit != null) {
                    prevUnit = unit;

                    turnToTarget(Angles.angle(x, y, unit.x, unit.y));

                    targetRot = Angles.angle(x, y, unit.x, unit.y);

                    canReinforce = Angles.angleDist(rotation, targetRot) <= cone;

                    if (canReinforce && load >= loadThreshold) {
                        unit.apply(plated);
                        // 装甲粒子特效 (PU132 plated.update 原版逻辑)
                        Tmp.v1.rnd(unit.type.hitSize / 2f);
                        zzw.content.graphics.UnityFx.platedFx.at(unit.x + Tmp.v1.x, unit.y + Tmp.v1.y, 0,
                            Mathf.chance(0.5f) ? Pal.accent : Items.surgeAlloy.color,
                            Mathf.random() + 0.1f);
                        load = 0f;
                        laserWidth = 0f;
                        // 适配 v155.4: consumes.getItem().items → reinforceItems
                        items.remove(reinforceItems);
                    }
                }
            }

            load += 0.01f * Time.delta;
            laserWidth += 0.01f * Time.delta;
            if (load > loadThreshold) load = loadThreshold;
            if (laserWidth > 0.3f) laserWidth = 0.3f;

            // 已装甲单位移动时持续喷出粒子 (PU132 原版 StatusEffect.update 逻辑,
            // 因 v155.4 匿名类无法覆写 update, 在强化器侧对锁定单位模拟)
            if (unit != null && unit.hasEffect(plated) && Mathf.chanceDelta(0.4f)
                && (!unit.isFlying() || unit.moving())) {
                Tmp.v1.rnd(unit.type.hitSize / 2f);
                zzw.content.graphics.UnityFx.platedFx.at(unit.x + Tmp.v1.x, unit.y + Tmp.v1.y, 0,
                    Mathf.chance(0.5f) ? Pal.accent : Items.surgeAlloy.color,
                    Mathf.random() + 0.1f);
            }
        }

        @Override
        public void drawSelect() {
            mindustry.graphics.Drawf.dashCircle(x, y, range, team.color);
        }

        @Override
        public void draw() {
            Draw.rect(baseRegion, x, y);
            Draw.z(Layer.block);
            Draw.rect(region, x, y, rotation - 90);

            if (prevUnit != null && laserWidth < 0.3f) {
                Draw.color(laserColor);
                Draw.z(Layer.effect);
                if (laserLength > 0f) posOffset.trns(rotation, laserLength);
                // 适配 v155.4: Drawf.laser 移除 Team 参数, 颜色由当前 Draw.color 决定 (上方已设 laserColor)
                mindustry.graphics.Drawf.laser(laserRegion, laserEndRegion,
                    x + posOffset.x, y + posOffset.y, prevUnit.x, prevUnit.y,
                    (0.3f - laserWidth) / 0.3f);
                Draw.color();
            }
        }

        public void turnToTarget(float targetRot) {
            rotation = Angles.moveToward(rotation, targetRot, rotateSpeed * Time.delta);
        }

        @Override
        public void write(arc.util.io.Writes write) {
            super.write(write);
            write.f(rotation);
        }

        @Override
        public void read(arc.util.io.Reads read, byte revision) {
            super.read(read, revision);
            rotation = read.f();
        }
    }
}