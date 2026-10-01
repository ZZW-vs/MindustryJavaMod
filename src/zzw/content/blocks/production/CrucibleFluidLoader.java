package zzw.content.blocks.production;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import mindustry.gen.Building;
import mindustry.type.Liquid;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.meta.CrucibleRecipes;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleLiquid;

/**
 * 坩埚液体装载器 (PU_V8 unity.world.blocks.production.CrucibleFluidLoader 移植)
 * <p>把 (普通) 液体输入转换成坩埚网络中的熔融原料。
 */
public class CrucibleFluidLoader extends GraphBlock{
    TextureRegion[] top;
    TextureRegion bottomRegion;

    public CrucibleFluidLoader(String name){
        super(name);
        hasLiquids = true;
    }

    @Override
    public void load(){
        super.load();
        top = new TextureRegion[4];
        top[0] = atlas(name + "-top1");
        top[1] = atlas(name + "-top2");
        top[2] = atlas(name + "-top3");
        top[3] = atlas(name + "-top4");
        bottomRegion = atlas(name + "-bottom");
    }

    static TextureRegion atlas(String name){
        return arc.Core.atlas.find(name);
    }

    public class CrucibleFluidLoaderBuild extends GraphBuild{
        @Override
        public void updatePost(){
            Liquid cur = liquids.current();
            CrucibleLiquid cr = CrucibleRecipes.liquids.get(cur);
            float am = liquids.get(cur);
            if(cr != null && am > 0f && crucible() != null && crucible().getNetwork() != null){
                float wentin = crucible().getNetwork().addLiquidIngredient(cr, am);
                liquids.remove(cur, wentin);
            }
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            return hasLiquids
                && (liquids.current() == liquid || liquids.currentAmount() < 0.2f)
                && liquids.get(liquid) < liquidCapacity
                && CrucibleRecipes.liquids.containsKey(liquid);
        }

        @Override
        public void draw(){
            Draw.rect(bottomRegion, x, y);
            if(crucible() != null && crucible().getNetwork() != null){
                Draw.color(crucible().getNetwork().color);
            }
            Fill.rect(x, y, size * 8f, size * 8f);
            Draw.color();
            Draw.rect(top[rotation], x, y);

            drawTeamTop();
        }
    }
}
