package zzw.content.exp;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.Fires;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static arc.Core.atlas;
import static mindustry.Vars.*;

/**
 * PU_V8 OmniLiquidTurret 移植版 (单一 shootType 液体炮台)
 * 参考: PU_V8 main/src/unity/world/blocks/exp/turrets/OmniLiquidTurret.java
 *
 * 与 ExpLiquidTurret 区别:
 * - 使用单一 shootType (而非 ammoTypes map)
 * - bullet() 将当前液体作为 Bullet.data 传入子弹, 让子弹根据液体调整伤害/特效
 * - 任何液体都可接受 (无 ammoTypes 限制)
 *
 * 简化: stats.ammo() 只显示基础信息, 不显示每种液体详细数据 (PU_V8 有复杂 StatValue 表格)
 */
public class OmniLiquidTurret extends ExpTurret {
    public TextureRegion liquidRegion;
    public TextureRegion topRegion;
    public boolean extinguish = true;
    public BulletType shootType;
    public float shootAmount = 0.5f;

    public OmniLiquidTurret(String name) {
        super(name);
        // v158 无 acceptCoolant 字段, 用 hasLiquids + consumeLiquid 即可
        hasLiquids = true;
        loopSound = V7Sounds.spray;  // v158 无 Sounds.spray, 用项目 V7Sounds.spray
        shootSound = Sounds.none;
        smokeEffect = Fx.none;
        shootEffect = Fx.none;
        outlinedIcon = 1;
    }

    @Override
    public void setStats() {
        super.setStats();
        // 简化: 只显示基础 ammo 信息
        stats.add(Stat.ammo, "@laser-kelvin-ammo");
    }

    @Override
    public void load() {
        super.load();
        liquidRegion = atlas.find(name + "-liquid");
        topRegion = atlas.find(name + "-top");
    }

    @Override
    public TextureRegion[] icons() {
        if (topRegion.found()) {
            // 使用基类的 drawer (DrawTurret) 的 base
            if (drawer instanceof mindustry.world.draw.DrawTurret dt) {
                return new TextureRegion[]{dt.base, region, topRegion};
            }
            return new TextureRegion[]{region, topRegion};
        }
        return super.icons();
    }

    /** 判断液体是否为友方 (治疗类) 液体 */
    public static boolean friendly(Liquid l) {
        return l.effect != StatusEffects.none && l.effect.damage <= 0.1f
                && (l.effect.damage < -0.01f || l.effect.healthMultiplier > 1.01f || l.effect.damageMultiplier > 1.01f);
    }

    public class OmniLiquidTurretBuild extends ExpTurretBuild {
        @Override
        public void draw() {
            super.draw();
            float dX = x + Angles.trnsx(rotation - 90, shootX, shootY),
                    dY = y + Angles.trnsy(rotation - 90, shootX, shootY);
            if (liquidRegion.found()) {
                Drawf.liquid(liquidRegion, dX, dY, liquids.currentAmount() / liquidCapacity, liquids.current().color, rotation - 90);
            }
            if (topRegion.found()) Draw.rect(topRegion, dX, dY, rotation - 90);
        }

        @Override
        public boolean shouldActiveSound() {
            return wasShooting && enabled;
        }

        @Override
        public void updateTile() {
            unit.ammo(unit.type().ammoCapacity * liquids.currentAmount() / liquidCapacity);
            super.updateTile();
        }

        @Override
        protected void findTarget() {
            if (extinguish && liquids.current().canExtinguish()) {
                int tx = World.toTile(x), ty = World.toTile(y);
                Fire result = null;
                float mindst = 0f;
                int tr = (int) (range / tilesize);
                for (int x = -tr; x <= tr; x++) {
                    for (int y = -tr; y <= tr; y++) {
                        Tile other = world.tile(x + tx, y + ty);
                        var fire = Fires.get(x + tx, y + ty);
                        float dst = fire == null ? 0 : dst2(fire);
                        if (other != null && fire != null && Fires.has(other.x, other.y) && dst <= range * range
                                && (result == null || dst < mindst) && (other.build == null || other.team() == team)) {
                            result = fire;
                            mindst = dst;
                        }
                    }
                }
                if (result != null) {
                    target = result;
                    return;
                }
            }
            super.findTarget();
        }

        @Override
        protected boolean canHeal() {
            return liquids.current() != null && friendly(liquids.current());
        }

        @Override
        public BulletType useAmmo() {
            if (cheating()) return shootType;
            liquids.remove(liquids.current(), shootAmount);
            return shootType;
        }

        @Override
        public BulletType peekAmmo() {
            return shootType;
        }

        @Override
        public boolean hasAmmo() {
            // v158 无 liquids.total(), 用 currentAmount()
            return liquids.currentAmount() >= shootAmount;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            return false;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            if (!hasLiquids) return false;
            // 只接受同种液体, 或当前液体量很少时接受新液体
            return liquids.current() == liquid || liquids.currentAmount() < 0.2f;
        }

        // ★ 不重写 bullet(): v158 的 bullet() 签名是 bullet(type, xOffset, yOffset, angleOffset, mover)
        // 子弹通过 b.owner 访问本炮台, 在 GeyserLaserBulletType.getLiquid() 中读取当前液体
    }
}
