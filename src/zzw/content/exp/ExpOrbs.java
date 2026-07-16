package zzw.content.exp;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.graphics.Layer;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.Conveyor.ConveyorBuild;
import mindustry.world.blocks.production.Incinerator;
import mindustry.world.blocks.production.Incinerator.IncineratorBuild;

import static mindustry.Vars.*;

/**
 * PU_V8 ExpOrbs 经验球 (BulletType实现, 飞行收集后填充经验)
 * 参考: PU_V8 main/src/unity/entities/ExpOrbs.java
 */
public class ExpOrbs {
    public static final int expAmount = 10;

    private static final Color expColor = Color.valueOf("84ff00");
    private static final int[] d4x = new int[]{1, 0, -1, 0};
    private static final int[] d4y = new int[]{0, 1, 0, -1};
    private static final ExpOrb expOrb = new ExpOrb();

    public static void spreadExp(float x, float y, int amount){
        spreadExp(x, y, amount, 4f);
    }

    public static void spreadExp(float x, float y, int amount, float v){
        if(net.server() || !net.active()){
            v *= 1000f;
            int n = amount / expAmount;
            for(int i = 0; i < n; i++){
                expOrb.createNet(Team.derelict, x, y, Mathf.random() * 360f, 0f, v, 1f);
            }
        }
    }

    public static void spreadExp(float x, float y, float amount, float v){
        spreadExp(x, y, Mathf.ceilPositive(amount), v);
    }

    public static void dropExp(float x, float y, float rotation){
        dropExp(x, y, rotation, 4f, expAmount);
    }

    public static void dropExp(float x, float y, float rotation, float v, int amount){
        if(net.server() || !net.active()){
            v *= 1000f;
            int n = amount / expAmount;
            for(int i = 0; i < n; i++){
                expOrb.createNet(Team.derelict, x, y, rotation, 0f, v, 1f);
            }
        }
    }

    public static int orbs(int exp){
        return exp / expAmount;
    }

    public static int convertedExp(int exp){
        return (exp / expAmount) * expAmount;
    }

    public static int oneOrb(int exp){
        return exp < expAmount ? 0 : expAmount;
    }

    public static final class ExpOrb extends BulletType{
        {
            absorbable = false;
            damage = 8f;
            drag = 0.05f;
            lifetime = 180f;
            speed = 0.0001f;
            keepVelocity = false;
            pierce = true;
            hitSize = 2f;

            hittable = false;
            collides = false;
            collidesTiles = false;
            collidesAir = false;
            collidesGround = false;

            lightColor = expColor;
            hitEffect = Fx.none;
            shootEffect = Fx.none;
            despawnEffect = UnityFx.orbDespawn;
            layer = Layer.bullet - 0.01f;
        }

        private ExpOrb(){}

        @Override
        public void draw(Bullet b){
            if((b.fin() > 0.5f) && Time.time % 14f < 7f) return;

            Draw.color(expColor, Color.white, 0.1f + 0.1f * Mathf.sin(Time.time * 0.03f + b.id * 2f));

            Fill.circle(b.x, b.y, 1.5f);
            Lines.stroke(0.5f);
            for(var i = 0; i < 4; i++){
                mindustry.graphics.Drawf.tri(b.x, b.y, 4f, 4f + 1.5f * Mathf.sin(Time.time * 0.12f + b.id * 3f), i * 90 + Mathf.sin(Time.time * 0.04f + b.id * 5f) * 28f);
            }
            Draw.color();
        }

        @Override
        public void update(Bullet b){
            if(!b.vel.isZero(0.01F)) b.time(0f);

            Tile tile = world.tileWorld(b.x, b.y);
            if(tile == null || tile.build == null) return;

            if(tile.build instanceof ExpHolder exp && exp.acceptOrb() && exp.handleOrb(expAmount)){
                b.remove();
            }
            else if(tile.block() instanceof Conveyor conv){
                conveyor(b, conv, (ConveyorBuild)tile.build);
            }
            else if(tile.block() instanceof Incinerator && ((IncineratorBuild)tile.build).heat > 0.5f){
                b.remove();
            }
            else if(tile.solid()){
                b.trns(-1.1f * b.vel.x, -1.1f * b.vel.y);
                b.vel.scl(0f);
            }
        }

        private void conveyor(Bullet b, Conveyor block, ConveyorBuild build){
            if(build.clogHeat > 0.5f || !build.enabled) return;
            float speed = block.speed / 3f;
            b.vel.add(d4x[build.rotation] * speed * build.delta(), d4y[build.rotation] * speed * build.delta());
        }
    }
}
