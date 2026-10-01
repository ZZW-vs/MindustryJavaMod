package zzw.content.blocks.production;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.meta.CrucibleRecipes;
import zzw.content.mechanics.torque.meta.CrucibleRecipes.CrucibleIngredient;

/**
 * 坩埚源 (PU_V8 unity.world.blocks.production.CrucibleSource 移植)
 * <p>沙盒/调试用: 持续把网络中指定原料填充到满 (固态清零, 熔融量 = 网络容量)。
 */
public class CrucibleSource extends GraphBlock{

    public CrucibleSource(String name){
        super(name);
        configurable = true;
        config(Item.class, (CrucibleSourceBuild build, Item item) -> build.config = CrucibleRecipes.items.get(item));
        config(Liquid.class, (CrucibleSourceBuild build, Liquid liquid) -> build.config = CrucibleRecipes.liquids.get(liquid));
        config(Integer.class, (CrucibleSourceBuild build, Integer id) -> build.config = CrucibleRecipes.ingredients.get(id));
        configClear((CrucibleSourceBuild build) -> build.config = null);
    }

    public class CrucibleSourceBuild extends GraphBuild{
        CrucibleIngredient config;

        @Override
        public void updatePost(){
            if(config != null && crucible() != null && crucible().getNetwork() != null){
                var net = crucible().getNetwork();
                net.getFluid(config).solid = 0f;
                net.getFluid(config).melted = net.totalCapacity();
            }
        }

        @Override
        public void buildConfiguration(Table table){
            table.table(t -> {
                int i = 0;
                for(var ing : CrucibleRecipes.ingredients.values()){
                    t.button(new TextureRegionDrawable(ing.icon), Styles.clearNonei, () -> configure(ing.id)).size(40f).pad(2f);
                    if(++i % 8 == 0) t.row();
                }
            }).grow().pad(4f).row();
            table.button(Icon.cancel, Styles.clearNonei, () -> configure(-1)).size(40f).padTop(4f);
        }

        @Override
        public Integer config(){
            return config == null ? -1 : config.id;
        }

        @Override
        public void writeExt(Writes write){
            write.s(config == null ? -1 : config.id);
        }

        @Override
        public void readExt(Reads read, byte revision){
            config = CrucibleRecipes.ingredients.get(read.s());
        }

        @Override
        public void draw(){
            Draw.color(config == null ? Pal.gray : config.color);
            Fill.rect(x, y, 8f, 8f);
            Draw.color();
            Draw.rect(region, x, y);
            drawTeamTop();
        }
    }
}
