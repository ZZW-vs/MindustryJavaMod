package zzw.content.exp;

import arc.graphics.g2d.*;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.draw.DrawTurret;
import mindustry.world.meta.*;

import static arc.Core.atlas;
import static mindustry.Vars.*;

/**
 * PU_V8 ExpLiquidTurret (经验液体炮台)
 * 参考: PU_V8 main/src/unity/world/blocks/exp/turrets/ExpLiquidTurret.java
 */
public class ExpLiquidTurret extends ExpTurret {
    public ObjectMap<Liquid, BulletType> ammoTypes = new ObjectMap<>();
    public TextureRegion liquidRegion;
    public TextureRegion topRegion;
    public boolean extinguish = true;

    public ExpLiquidTurret(String name){
        super(name);
        // v155.4: 无 acceptCoolant 字段, 通过设 coolant=null 禁用 (init 中会重新查找)
        // 实际等效: 液体炮台不使用 coolant 消费器, 直接用液体作 ammo
        hasLiquids = true;
        loopSound = V7Sounds.spray;
        shootSound = Sounds.none;
        smokeEffect = Fx.none;
        shootEffect = Fx.none;
        outlinedIcon = 1;
    }

    /** Initializes accepted ammo map. Format: [liquid1, bullet1, liquid2, bullet2...] */
    public void ammo(Object... objects){
        ammoTypes = ObjectMap.of(objects);
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.ammo, StatValues.ammo(ammoTypes));
    }

    @Override
    public void init(){
        consume(new ConsumeLiquidFilter(i -> ammoTypes.containsKey(i), 1f){
            @Override
            public float efficiency(Building build){
                //PU_V8 valid(): liquids.total() > 0.001f
                // v155.4: liquids.total() 不存在, 用 currentAmount() 替代
                return build.liquids.currentAmount() > 0.001f ? 1f : 0f;
            }

            @Override
            public void update(Building build){
            }

            @Override
            public void display(Stats stats){
            }
        });

        super.init();
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

    public class ExpLiquidTurretBuild extends ExpTurretBuild{
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
            //PU_V8: unit.ammo(unit.type().ammoCapacity * liquids.currentAmount() / liquidCapacity)
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

        /** v158 无 effects() 方法, 通过覆写 bullet() 实现 PU_V8 effects() 的液体颜色特效 */
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

            handleBullet(type.create(this, team, bulletX, bulletY, shootAngle, -1f, (1f - velocityRnd) + Mathf.random(velocityRnd), lifeScl, null, mover, targetPos.x, targetPos.y), xOffset, yOffset, shootAngle - rotation);

            //PU_V8: 使用 liquids.current().color 替代 type.hitColor
            arc.graphics.Color liquidColor = liquids.current().color;
            (shootEffect == null ? type.shootEffect : shootEffect).at(bulletX, bulletY, rotation + angleOffset, liquidColor);
            (smokeEffect == null ? type.smokeEffect : smokeEffect).at(bulletX, bulletY, rotation + angleOffset, liquidColor);
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
            if(cheating()) return ammoTypes.get(liquids.current());
            BulletType type = ammoTypes.get(liquids.current());
            liquids.remove(liquids.current(), 1f / type.ammoMultiplier);
            return type;
        }

        @Override
        public BulletType peekAmmo(){
            return ammoTypes.get(liquids.current());
        }

        @Override
        public boolean hasAmmo(){
            return ammoTypes.get(liquids.current()) != null && liquids.currentAmount() >= 1f / ammoTypes.get(liquids.current()).ammoMultiplier;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return false;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            return ammoTypes.get(liquid) != null
                    && (liquids.current() == liquid || (ammoTypes.containsKey(liquid)
                    && (!ammoTypes.containsKey(liquids.current()) || liquids.get(liquids.current()) <= 1f / ammoTypes.get(liquids.current()).ammoMultiplier + 0.001f)));
        }
    }
}
