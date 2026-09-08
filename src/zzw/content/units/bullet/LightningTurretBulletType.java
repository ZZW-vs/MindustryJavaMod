package zzw.content.units.bullet;

import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;
import zzw.content.units.effects.TrailFx;

public class LightningTurretBulletType extends BulletType{
    public float range = 100f, reload = 15f, duration = 120f, size = 9f;
    public Effect lightningEffect = TrailFx.trailFadeLow;
    public Sound lightningSound = Sounds.none;
    public Color color = Pal.lancerLaser;
    private static Healthc tmp;

    public LightningTurretBulletType(float speed, float damage){
        super(speed, damage);
        pierce = true;
    }

    @Override
    public void update(Bullet b){
        if(b.fdata <= 0f){
            super.update(b);
        }else{
            if(b.timer(1, reload)){
                final float[] bestDst = {range * range};
                final Healthc[] target = {null};
                
                Units.nearbyEnemies(b.team, b.x, b.y, range, u -> {
                    float dst = u.dst2(b.x, b.y);
                    if(dst < bestDst[0]){
                        bestDst[0] = dst;
                        target[0] = u;
                    }
                });
                
                if(target[0] != null){
                    tmp = target[0];
                    Building block = Vars.world.buildWorld(tmp.x(), tmp.y());
                    if(block != null && block.block.absorbLasers){
                        tmp = block;
                    }
                    
                    lightningSound.at(b.x, b.y, Mathf.random(0.9f, 1.1f));
                    lightningEffect.at(b.x, b.y, 0f, color, tmp);
                    tmp.damage(damage);
                    hit(b, tmp.x(), tmp.y());
                    if(tmp instanceof Unit u){
                        u.apply(status, statusDuration);
                    }
                }
            }
        }
    }

    @Override
    public void despawned(Bullet b){
        if(b.fdata > 0f){
            super.despawned(b);
        }else{
            hit(b, b.x, b.y);
        }
    }

    @Override
    public void hit(Bullet b, float x, float y){
        hitEffect.at(x, y, b.rotation(), hitColor);
        hitSound.at(x, y, hitSoundPitch, hitSoundVolume);
        Effect.shake(hitShake, hitShake, b);

        if(b.fdata > 0f){
            if(fragBullet != null){
                for(int i = 0; i < fragBullets; i++){
                    float len = Mathf.random(1f, 7f);
                    float a = b.rotation() + Mathf.range(10f);
                    fragBullet.create(b, x + Angles.trnsx(a, len), y + Angles.trnsy(a, len), a, Mathf.random(0.8f, 1.2f), lifetime * Mathf.random(0.8f, 1.2f));
                }
            }

            if(puddleLiquid != null && puddles > 0){
                for(int i = 0; i < puddles; i++){
                    Tile tile = Vars.world.tileWorld(x + Mathf.range(puddleRange), y + Mathf.range(puddleRange));
                    Puddles.deposit(tile, puddleLiquid, puddleAmount);
                }
            }

            if(incendChance > 0 && Mathf.chance(incendChance)){
                Damage.createIncend(x, y, incendSpread, incendAmount);
            }

            if(splashDamageRadius > 0 && !b.absorbed){
                Damage.damage(b.team, x, y, splashDamageRadius, splashDamage * b.damageMultiplier(), collidesAir, collidesGround);

                if(status != StatusEffects.none){
                    Damage.status(b.team, x, y, splashDamageRadius, status, statusDuration, collidesAir, collidesGround);
                }

                if(healPercent > 0f){
                    Vars.indexer.eachBlock(b.team, x, y, splashDamageRadius, Building::damaged, other -> {
                        Fx.healBlockFull.at(other.x, other.y, other.block.size, Pal.heal);
                        other.heal(healPercent / 100f * other.maxHealth());
                    });
                }

                if(makeFire){
                    Vars.indexer.eachBlock(null, x, y, splashDamageRadius, other -> other.team != b.team, other -> Fires.create(other.tile));
                }
            }
        }else{
            Bullet n = create(b, b.x, b.y, b.rotation());
            n.vel.setZero();
            n.fdata = 1f;
            n.lifetime = duration;
        }
    }

    @Override
    public void draw(Bullet b){
        super.draw(b);
        Draw.color(color);
        Fill.circle(b.x, b.y, size);
        if(b.fdata <= 0f){
            Draw.color(Color.white);
            Fill.circle(b.x, b.y, size / 2f);
        }else{
            float in = Mathf.clamp(b.time / 15f) * range,
            fin = ((b.time % reload) / reload) * size;
            Lines.stroke(1.5f);
            Lines.circle(b.x, b.y, in);

            Draw.color(Color.white);
            Lines.circle(b.x, b.y, fin);
            Fill.circle(b.x, b.y, size * 0.05f);
        }
    }
}