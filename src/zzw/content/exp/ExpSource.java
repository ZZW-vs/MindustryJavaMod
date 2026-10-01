package zzw.content.exp;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static arc.Core.atlas;

/**
 * 经验源方块 - 产生经验值并注入经验网络
 * 继承 Block，作为经验系统的经验值产生源头
 */
public class ExpSource extends Block {
    public int produceTimer = timers++;

    public float reload = 60f;
    public int amount = 100;
    public TextureRegion topRegion;

    public ExpSource(String name){
        super(name);
        update = true;
        solid = rotate = false;
        configurable = true;

        config(Boolean.class, (ExpSourceBuild entity, Boolean b) -> {
            if(b) entity.clicked();
        });
    }

    @Override
    public void load(){
        super.load();
        topRegion = atlas.find(name + "-top");
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.itemCapacity, "@", Core.bundle.format("exp.expAmount", "-Infinity"));
    }

    public class ExpSourceBuild extends Building {
        @Override
        public void updateTile(){
            if(enabled && timer.get(produceTimer, reload)){
                // 与 PU_V8 一致: 仅向相邻经验方块注入 (原版 spreadExp 调用被注释),
                // 手动点按产生的经验球见 configTapped()/clicked()
                for(Building b : proximity){
                    if(b instanceof ExpHolder exp) exp.handleExp(99999999);
                }
            }
        }

        @Override
        public void draw(){
            super.draw();
            Draw.blend(Blending.additive);
            Draw.color(Color.white);
            Draw.alpha(Mathf.absin(Time.time, 20, 0.4f));
            Draw.rect(topRegion, x, y);
            Draw.blend();
            Draw.reset();
        }

        @Override
        public void onDestroyed(){
            ExpOrbs.spreadExp(x, y, amount * 5, 8f);
            super.onDestroyed();
        }

        @Override
        public boolean configTapped(){
            configure(true);
            return false;
        }

        /** 点按放出一次经验球 (PU_V8 clicked) */
        public void clicked(){
            ExpOrbs.spreadExp(x, y, amount, 6f);
        }
    }
}
