package zzw.content.exp;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static arc.Core.atlas;

public class ExpTank extends Block {
    public int expCapacity = 600;
    public TextureRegion topRegion, expRegion;

    public ExpTank(String name){
        super(name);
        update = solid = sync = true;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.itemCapacity, "@", Core.bundle.format("exp.expAmount", expCapacity));
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("exp", (ExpTankBuild entity) -> new Bar(() -> Core.bundle.get("bar.exp"), () -> UnityPal.exp, entity::expf));
    }

    @Override
    public void load(){
        super.load();
        topRegion = atlas.find(name + "-top");
        expRegion = atlas.find(name + "-exp");
    }

    @Override
    protected TextureRegion[] icons(){
        return new TextureRegion[]{region, topRegion};
    }

    public class ExpTankBuild extends Building implements ExpHolder{
        public int exp = 0;

        // v155.4 适配: 经验储罐现在可被 exp-output (ExpHub) 链接并主动抽取经验
        // 原 PU_V8 中储罐 hubbable() 默认返回 false, 无法被链接; 此处改为 true 以支持取出经验
        public @Nullable ExpHub.ExpHubBuild hub = null;

        @Override
        public int getExp(){
            return exp;
        }

        @Override
        public int handleExp(int amount){
            if(amount > 0){
                int e = Math.min(expCapacity - exp, amount);
                exp += e;
                return e;
            }
            else{
                int e = Math.min(-amount, exp);
                exp -= e;
                return -e;
            }
        }

        public float expf(){
            return exp / (float)expCapacity;
        }

        @Override
        public int unloadExp(int amount){
            int e = Math.min(amount, exp);
            exp -= e;
            return e;
        }

        @Override
        public boolean acceptOrb(){
            return true;
        }

        @Override
        public boolean handleOrb(int orbExp){
            return handleExp(orbExp) > 0;
        }

        // ===== Hub linkage (允许 exp-output 链接并抽取经验) =====
        @Override
        public boolean hubbable(){
            return true;
        }

        @Override
        public boolean canHub(Building build){
            return !hubValid() || (build != null && build == hub);
        }

        @Override
        public void setHub(ExpHub.ExpHubBuild hub){
            this.hub = hub;
        }

        public boolean hubValid(){
            boolean val = hub != null && hub.isValid() && !hub.dead && hub.links.contains(pos());
            if(!val) hub = null;
            return val;
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);
            Draw.color(UnityPal.exp, Color.white, Mathf.absin(20, 0.6f));
            Draw.alpha(expf());
            Draw.rect(expRegion, x, y);
            Draw.color();
            Draw.rect(topRegion, x, y);
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            drawPlaceText(exp + "/" + expCapacity, tile.x, tile.y, exp > 0);
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, 25f + 25f * expf(), UnityPal.exp, 0.5f * expf());
        }

        @Override
        public void onDestroyed(){
            ExpOrbs.spreadExp(x, y, exp * 0.8f, 3 * size);
            super.onDestroyed();
        }

        @Override
        public double sense(LAccess sensor){
            return switch(sensor){
                case itemCapacity -> expCapacity;
                case totalItems -> exp;
                default -> super.sense(sensor);
            };
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(exp);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            exp = read.i();
        }
    }
}
