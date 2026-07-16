package zzw.content.exp;

import arc.graphics.g2d.*;
import arc.math.Angles;
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
            unit.ammo(1);
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
                        if(other != null && fire != null && Fires.has(other.x, other.y) && dst <= range * range && (result == null || dst < mindst) && (other.build == null || other.team() == team)){
                            result = fire;
                            mindst = dst;
                        }
                    }
                }

                if(result != null){
                    target = result;
                    return;
                }
            }

            super.findTarget();
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
