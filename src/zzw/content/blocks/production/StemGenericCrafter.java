package zzw.content.blocks.production;

import arc.func.Cons;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.world.blocks.production.GenericCrafter;
import zzw.content.util.StatUtils;

/**
 * Stem 组件的手动实现 (PU132 @Merge(Stemc) 替代)
 * <p>添加 drawStem/updateStem 回调和简单的数据序列化框架。
 * 子类可以通过重写 drawStem/updateStem 添加自定义渲染/更新逻辑。</p>
 *
 * <p>★ draw() 修复: PU132 的 @Merge 是把 Stemc 组件织入 GenericCrafterBuild,
 * drawStem 回调在【原版绘制之后】追加执行; 之前的移植错误地用 drawStem
 * 完全替换了原版 draw(), 导致所有带 drawer 的 Stem 系工厂 (暗合金/火花合金/
 * 终焉锻造厂/固化器/熔化器) 放置后完全不渲染。正确行为: super.draw() 先画
 * drawer, 再执行 drawStem 叠加。</p>
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

    @Override
    public void setStats(){
        super.setStats();
        // 输出物品速率四舍五入到最多 2 位小数 (原版 3 位)
        StatUtils.roundOutputStats(this);
    }

    public class StemGenericCrafterBuild extends GenericCrafterBuild{
        @Override
        public void draw(){
            // 先执行原版绘制 (drawer), 再叠加 stem 回调 (PU132 织入语义)
            super.draw();
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
