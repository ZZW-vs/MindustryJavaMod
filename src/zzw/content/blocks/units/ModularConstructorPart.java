package zzw.content.blocks.units;

// 适配: 合并 unity.world.modules 包到 zzw.content.blocks.units (同包, 无需 import)
import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
// 适配: 显式导入内部类 (同包但内部类不会自动导入)
import zzw.content.blocks.units.ModularConstructor.ModularConstructorBuild;
import zzw.content.blocks.units.ModularConstructorModule.ModularConstructorModuleInterface;
// 适配: 以下 unity.* import 已移除
// import unity.graphics.*;
// import unity.world.blocks.units.ModularConstructor.*;
// import unity.world.modules.*;
// import unity.world.modules.ModularConstructorModule.*;

/**
 * 模块化构造器部件 - 链式连接到主构造器或前一个部件
 *
 * 移植自 PU132 unity.world.blocks.units.ModularConstructorPart
 * 适配 v155.4:
 * - UnityPal.advance → Color.valueOf("ff9b75")
 * - consumes.power(120f) → consumePower(120f)
 * - 包名 unity.world.blocks.units → zzw.content.blocks.units
 * - 链式连接逻辑 (placed/updateBack/removePart) 完整保留
 */
public class ModularConstructorPart extends Block{
    // 适配: UnityPal.advance → Color.valueOf("ff9b75")
    public Color effectColor = Color.valueOf("ff9b75");
    public TextureRegion topRegion, frontRegion, backRegion;

    public ModularConstructorPart(String name){
        super(name);
        rotate = true;
        solid = false;
        update = true;
        sync = true;
        hasPower = true;
        hasItems = true;

        // 适配 v155.4: consumes.power(120f) → consumePower(120f)
        consumePower(120f);
    }

    @Override
    public void load(){
        super.load();
        topRegion = Core.atlas.find(name + "-top");
        frontRegion = Core.atlas.find(name + "-front");
        backRegion = Core.atlas.find(name + "-back");
    }

    // drawPlace 使用 Mindustry 默认行为，不额外覆盖

    public class ModularConstructorPartBuild extends Building implements ModularConstructorModuleInterface{
        public ModularConstructorPartBuild back, front;
        public ModularConstructorModule module = new ModularConstructorModule();

        @Override
        public ModularConstructorModule consModule(){
            return module;
        }

        @Override
        public boolean consConnected(Building other){
            if(other.rotation != rotation || other.block != block) return false;
            Tmp.v1.trns(rotdeg() + 180f, size * Vars.tilesize).add(this);
            Tmp.r1.setCentered(Tmp.v1.x, Tmp.v1.y, size * Vars.tilesize);
            other.hitbox(Tmp.r2);
            Tmp.r2.grow(-2f);
            return Tmp.r1.contains(Tmp.r2);
        }

        @Override
        public void draw(){
            Draw.rect(block.region, x, y, 0);
            drawTeamTop();

            Draw.rect(frontRegion, this, rotdeg());
            if(back != null){
                Draw.rect(backRegion, this, rotdeg());
            }else{
                Tmp.v1.trns(rotdeg() + 180f, size * Vars.tilesize).add(this);
                Tmp.r1.setCentered(Tmp.v1.x, Tmp.v1.y, size * Vars.tilesize);
                Draw.color(Tmp.c1.set(effectColor).a(0.3f));
                Fill.crect(Tmp.r1.x, Tmp.r1.y, Tmp.r1.width, Tmp.r1.height);
                Draw.reset();
            }
            Draw.rect(topRegion, this, 0f);
        }

        @Override
        public boolean shouldConsume(){
            if(module.graph != null && module.graph.main != null) return module.graph.main.shouldConsume();
            return false;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(module.graph.main != null) return module.graph.main.acceptItem(source, item);
            return super.acceptItem(source, item);
        }

        @Override
        public void handleItem(Building source, Item item){
            if(module.graph.main != null){
                module.graph.main.handleItem(source, item);
                return;
            }
            super.handleItem(source, item);
        }

        @Override
        public void placed(){
            if(Vars.net.client()) return;
            super.placed();
            Building front = front();
            if(front instanceof ModularConstructorBuild mod && mod.consConnected(this)){
                module.graph = mod.consModule().graph;
                module.graph.all.add(this);
                Fx.healBlockFull.at(mod.x, mod.y, mod.block.size, effectColor);
                Fx.healBlockFull.at(x, y, size, effectColor);
            }
            if(front instanceof ModularConstructorPartBuild mod && mod.module.graph != null && mod.consConnected(this)){
                module.graph = mod.module.graph;
                module.graph.all.add(this);
                mod.back = this;
                this.front = mod;
                Fx.healBlockFull.at(mod.x, mod.y, mod.block.size, effectColor);
                Fx.healBlockFull.at(x, y, size, effectColor);
            }

            updateBack();
        }

        public void updateBack(){
            Building back = back();
            if(back instanceof ModularConstructorPartBuild mod && consConnected(mod)){
                mod.module.graph = module.graph;
                if(mod.module.graph != null) mod.module.graph.all.add(mod);
                mod.front = this;
                this.back = mod;
                mod.updateBack();
            }
        }

        public void removePart(){
            if(module.graph != null){
                module.graph.remove(this);
                if(back != null) back.removePart();
            }
        }

        @Override
        public void remove(){
            super.remove();
            if(front != null){
                front.back = null;
            }
        }
    }
}
