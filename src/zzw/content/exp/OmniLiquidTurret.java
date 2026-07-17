package zzw.content.exp;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.draw.DrawTurret;
import mindustry.world.meta.*;
import zzw.content.units.bullets.GeyserBulletType;
import zzw.content.units.bullets.GeyserLaserBulletType;

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
 */
public class OmniLiquidTurret extends ExpTurret {
    public TextureRegion liquidRegion;
    public TextureRegion topRegion;
    public boolean extinguish = true;
    public BulletType shootType;
    public float shootAmount = 0.5f;

    public OmniLiquidTurret(String name){
        super(name);
        // v155.4: 无 acceptCoolant 字段, 液体炮台不使用 coolant 消费器
        hasLiquids = true;
        loopSound = V7Sounds.spray;
        shootSound = Sounds.none;
        smokeEffect = Fx.none;
        shootEffect = Fx.none;
        outlinedIcon = 1;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.ammo, ammo(0));
    }

    @Override
    public void load(){
        super.load();
        liquidRegion = atlas.find(name + "-liquid");
        topRegion = atlas.find(name + "-top");
    }

    @Override
    public TextureRegion[] icons(){
        if(topRegion.found()) return new TextureRegion[]{((DrawTurret)drawer).base, region, topRegion};
        return super.icons();
    }

    public static boolean friendly(Liquid l){
        return l.effect != StatusEffects.none && l.effect.damage <= 0.1f && (l.effect.damage < -0.01f || l.effect.healthMultiplier > 1.01f || l.effect.damageMultiplier > 1.01f);
    }

    /** PU_V8: 按液体类型生成详细伤害/击退/燃烧/闪电/状态效果表格 */
    public StatValue ammo(int indent){
        if(!(shootType instanceof GeyserLaserBulletType g)) return table -> {};
        GeyserBulletType type = (GeyserBulletType) g.geyser;
        return table -> {
            table.row();

            for(Liquid t : content.liquids()){
                boolean compact = indent > 0;

                table.image(t.uiIcon).size(3 * 8).padRight(4).right().top();
                table.add(t.localizedName).padRight(10).left().top();

                table.table(bt -> {
                    bt.left().defaults().padRight(3).left();

                    //damage of geyser
                    float damage = type.damage * GeyserBulletType.damageScale(t) * 60f;
                    if(damage > 0f) sep(bt, Core.bundle.format("bullet.splashdamage", Strings.autoFixed(damage, 1), Strings.fixed(type.radius / tilesize, 1)));
                    else sep(bt, Core.bundle.format("bullet.splashheal", Strings.autoFixed(-damage, 1), Strings.fixed(type.radius / tilesize, 1)));

                    float kn = GeyserBulletType.knockbackScale(t) * type.knockback;
                    if(kn > 0){
                        sep(bt, Core.bundle.format("bullet.knockback", Strings.autoFixed(kn, 2)));
                    }

                    if(t.temperature > 0.8f){
                        sep(bt, "@bullet.incendiary");
                    }

                    if(GeyserBulletType.hasLightning(t)){
                        sep(bt, Core.bundle.format("bullet.lightning", (int)(1 + t.heatCapacity * 5), damage * 0.5f));
                    }

                    if(t.effect != StatusEffects.none){
                        sep(bt, (t.effect.minfo.mod == null ? t.effect.emoji() : "") + "[stat]" + t.effect.localizedName);
                    }
                }).padTop(compact ? 0 : -9).padLeft(indent * 8).left().get().background(compact ? null : Tex.underline);

                table.row();
            }
        };
    }

    //for AmmoListValue
    private static void sep(Table table, String text){
        table.row();
        table.add(text);
    }

    public class OmniLiquidTurretBuild extends ExpTurretBuild{
        @Override
        public void draw(){
            super.draw();

            float
                    dX = x + Angles.trnsx(rotation - 90, shootX, shootY),
                    dY = y + Angles.trnsy(rotation - 90, shootX, shootY);

            if(liquidRegion.found()){
                Drawf.liquid(liquidRegion, dX, dY, liquids.currentAmount() / liquidCapacity, liquids.current().color, rotation - 90);
            }
            if(topRegion.found()) Draw.rect(topRegion, dX, dY, rotation - 90);
        }

        @Override
        public boolean shouldActiveSound(){
            return wasShooting && enabled;
        }

        @Override
        public void updateTile(){
            unit.ammo(unit.type().ammoCapacity * liquids.currentAmount() / liquidCapacity);

            super.updateTile();
        }

        @Override
        protected void findTarget(){
            if(extinguish && liquids.current().canExtinguish()){
                int tx = World.toTile(x), ty = World.toTile(y);
                Fire result = null;
                float mindst = 0f;
                int tr = (int)(range / tilesize);
                for(int x = -tr; x <= tr; x++){
                    for(int y = -tr; y <= tr; y++){
                        Tile other = world.tile(x + tx, y + ty);
                        var fire = Fires.get(x + tx, y + ty);
                        float dst = fire == null ? 0 : dst2(fire);
                        //do not extinguish fires on other team blocks
                        if(other != null && fire != null && Fires.has(other.x, other.y) && dst <= range * range && (result == null || dst < mindst) && (other.build == null || other.team() == team)){
                            result = fire;
                            mindst = dst;
                        }
                    }
                }

                if(result != null){
                    target = result;
                    //don't run standard targeting
                    return;
                }
            }

            super.findTarget();
        }

        @Override
        protected boolean canHeal(){
            return liquids.current() != null && friendly(liquids.current());
        }

        /** v158 无 effects() 方法 (PU_V8 v159 有), 通过覆写 bullet() 实现 PU_V8 的液体颜色特效 + 液体作为 data 传入子弹 */
        @Override
        protected void bullet(BulletType type, float xOffset, float yOffset, float angleOffset, Mover mover){
            queuedBullets--;
            if(dead || (!consumeAmmoOnce && !hasAmmo())) return;

            float
            xSpread = Mathf.range(xRand),
            bulletX = x + Angles.trnsx(rotation - 90, shootX + xOffset + xSpread, shootY + yOffset),
            bulletY = y + Angles.trnsy(rotation - 90, shootX + xOffset + xSpread, shootY + yOffset),
            shootAngle = rotation + angleOffset + Mathf.range(inaccuracy + type.inaccuracy);

            float lifeScl = type.scaleLife ? Mathf.clamp((1 + scaleLifetimeOffset) * Mathf.dst(bulletX, bulletY, targetPos.x, targetPos.y) / type.range, minRange() / type.range, range() / type.range) : 1f;

            //PU_V8: 将 liquids.current() 作为 data 传入子弹, 让子弹冻结液体类型
            Liquid currentLiquid = liquids.current();
            handleBullet(type.create(this, team, bulletX, bulletY, shootAngle, -1f, (1f - velocityRnd) + Mathf.random(velocityRnd), lifeScl, currentLiquid, mover, targetPos.x, targetPos.y), xOffset, yOffset, shootAngle - rotation);

            //PU_V8: 使用 liquids.current().color 替代 type.hitColor
            (shootEffect == null ? type.shootEffect : shootEffect).at(bulletX, bulletY, rotation + angleOffset, currentLiquid.color);
            (smokeEffect == null ? type.smokeEffect : smokeEffect).at(bulletX, bulletY, rotation + angleOffset, currentLiquid.color);
            (type.shootSound != Sounds.none ? type.shootSound : shootSound).at(bulletX, bulletY, Mathf.random(soundPitchMin, soundPitchMax), shootSoundVolume);

            ammoUseEffect.at(
                x - Angles.trnsx(rotation, ammoEjectBack),
                y - Angles.trnsy(rotation, ammoEjectBack),
                rotation * Mathf.sign(xOffset)
            );

            if(shake > 0){
                Effect.shake(shake, shake, this);
            }

            curRecoil = 1f;
            if(recoils > 0){
                curRecoils[barrelCounter % recoils] = 1f;
            }
            heat = 1f;
            totalShots++;

            if(!consumeAmmoOnce){
                useAmmo();
            }
        }

        @Override
        public BulletType useAmmo(){
            if(cheating()) return shootType;
            liquids.remove(liquids.current(), shootAmount);
            return shootType;
        }

        @Override
        public BulletType peekAmmo(){
            return shootType;
        }

        @Override
        public boolean hasAmmo(){
            return liquids.currentAmount() >= shootAmount;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return false;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            if(!hasLiquids) return false;
            return liquids.current() == liquid || liquids.currentAmount() < 0.2f;
        }
    }
}
