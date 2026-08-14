package zzw.content.blocks.units;

// 适配: 合并 unity.world.modules 包到 zzw.content.blocks.units (同包, 无需 import)
import arc.struct.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.world.modules.*;
// 适配: 显式导入内部类 (同包但内部类不会自动导入)
import zzw.content.blocks.units.ModularConstructor.ModularConstructorBuild;
import zzw.content.blocks.units.ModularConstructorPart.ModularConstructorPartBuild;
// 适配: 以下 unity.world.blocks.units.* import 已移除 (同包)
// import unity.world.blocks.units.ModularConstructor.*;
// import unity.world.blocks.units.ModularConstructorPart.*;

/**
 * 模块化构造器图模块 - 管理 constructor 与 part 之间的连接图
 *
 * 移植自 PU132 unity.world.modules.ModularConstructorModule
 * 适配 v155.4:
 * - build.added (private) → build.isAdded() (公开方法)
 * - 包名 unity.world.modules → zzw.content.blocks.units (合并到同包)
 * - 其余 API (BlockModule/Buildingc/Seq/IntSet) 在 v155.4 中保持不变
 */
public class ModularConstructorModule extends BlockModule{
    public ModularConstructorGraph graph;
    boolean main = false;

    public ModularConstructorModule(){

    }

    public ModularConstructorModule(ModularConstructorBuild build){
        graph = new ModularConstructorGraph();
        graph.main = build;
        main = true;
    }

    public void update(){
        if(graph != null) graph.update();
    }

    @Override
    public void write(Writes write){
    }

    @Override
    public void read(Reads read){
        if(main && graph != null && graph.main != null){
            graph.queueAdded = true;
        }
    }

    public interface ModularConstructorModuleInterface extends Buildingc{
        ModularConstructorModule consModule();

        boolean consConnected(Building other);
    }

    public static class ModularConstructorGraph{
        public Seq<ModularConstructorPartBuild> all = new Seq<>(), toRemove = new Seq<>();
        public IntSet toRemoveSet = new IntSet(), tmp = new IntSet();
        public float tier = 0f;
        public ModularConstructorBuild main;
        public boolean queueAdded = false;

        public void added(ModularConstructorBuild b){
            all.clear();
            b.updateProximity();
            for(Building other : b.proximity){
                if(other instanceof ModularConstructorPartBuild mod && b.consConnected(other) && tmp.add(other.pos())){
                    mod.module.graph = this;
                    all.add(mod);
                    mod.updateBack();
                }
            }
            tmp.clear();
        }

        public void remove(ModularConstructorPartBuild build){
            if(toRemoveSet.add(build.pos())){
                toRemove.add(build);
                build.module.graph = null;
            }
        }

        void update(){
            if(queueAdded && main != null){
                added(main);
                queueAdded = false;
            }
            for(ModularConstructorPartBuild build : all){
                // 适配 v155.4: build.added (private) → build.isAdded()
                if(!build.isAdded()) build.removePart();
            }
            all.removeAll(toRemove);
            toRemove.clear();
            toRemoveSet.clear();
            tier = all.size / (float)main.moduleConnections();
            main.tier = all.size / main.moduleConnections();
        }
    }
}
