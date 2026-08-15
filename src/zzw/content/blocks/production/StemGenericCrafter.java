package zzw.content.blocks.production;

import arc.func.Cons;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.world.blocks.production.GenericCrafter;

/**
 * Stem 组件的手动实现 (PU132 @Merge(Stemc) 替代)
 * <p>添加 drawStem/updateStem 回调和简单的数据序列化框架。
 * 子类可以通过重写 drawStem/updateStem 添加自定义渲染/更新逻辑。</p>
 */
public class StemGenericCrafter extends GenericCrafter{
    public Cons<StemGenericCrafterBuild> drawStem = e -> {};
    public Cons<StemGenericCrafterBuild> updateStem = e -> {};

    public StemGenericCrafter(String name){
        super(name);
    }

    public void draw(Cons<StemGenericCrafterBuild> draw){
        this.drawStem = draw;
    }

    public void update(Cons<StemGenericCrafterBuild> update){
        this.updateStem = update;
    }

    public class StemGenericCrafterBuild extends GenericCrafterBuild{
        @Override
        public void draw(){
            drawStem.get(this);
        }

        @Override
        public void updateTile(){
            super.updateTile();
            updateStem.get(this);
        }

        @Override
        public void write(Writes write){
            super.write(write);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
        }
    }
}
