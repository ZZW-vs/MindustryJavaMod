package zzw.content.blocks.units;

// 适配: 合并 unity.world.modules 包到 zzw.content.blocks.units (同包, 无需 import)
import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.units.UnitFactory.*;
import mindustry.world.consumers.*;
// 适配: 显式导入内部类 (同包但内部类不会自动导入)
import zzw.content.blocks.units.ModularConstructorModule.ModularConstructorModuleInterface;
import zzw.content.blocks.units.ModularConstructorPart.ModularConstructorPartBuild;
// 适配: 以下 unity.* import 已移除 (UnityPal 用 Color.valueOf 替代, 其余同包)
// import unity.graphics.*;
// import unity.world.blocks.units.ModularConstructorPart.*;
// import unity.world.modules.*;
// import unity.world.modules.ModularConstructorModule.*;

import java.util.*;

/**
 * 模块化构造器 - 通过连接模块部件 (ModularConstructorPart) 提升等级, 解锁更高 tier 的单位建造
 *
 * 移植自 PU132 unity.world.blocks.units.ModularConstructor
 * 适配 v155.4:
 * - UnityPal.advance → Color.valueOf("ff9b75")
 * - consumes.add(new ConsumeItemDynamic(...)) → consume(new ConsumeItemDynamic(...))
 * - config(UnitType.class, (UnitFactoryBuild ...) → (ModularConstructorBuild ...)
 * - consValid() 不再 @Override (Building 中已移除), 改为本地方法:
 *   super.consValid() → potentialEfficiency > 0
 *   build.cons.canConsume() → build.canConsume()
 * - cons.trigger() → consume()
 * - consumes.has(ConsumeType.item) / consumes.get(ConsumeType.item).valid(this) → 简化
 *   (v155.4 的 potentialEfficiency 已自动包含 item 消费者效率)
 * - Styles.clearToggleTransi → Styles.clearTogglei (v155.4 重命名)
 * - 模块链式连接逻辑 (placed/updateBack/removePart) 完整保留
 * - graph 的 tier 计算逻辑完整保留
 */
public class ModularConstructor extends Block{
    public TextureRegion[] topRegions;
    public float minSize = 24.5f - 7f;
    public Seq<ModularConstructorPlan> plans = new Seq<>(4);
    public float efficiencyPerTier = 80f, maxEfficiency = 5f * 60f;
    // 适配: UnityPal.advance → Color.valueOf("ff9b75")
    public Color buildColor = Color.valueOf("ff9b75");
    public Vec2[] moduleNodes = {new Vec2(3.5f, 9.5f)};
    public boolean mirrorNodes = true;
    public int moduleSize = 6, moduleConnections = 8;
    public Block moduleBlock;
    protected int maxTier = 0;
    protected int[] capacities;
    protected Seq<ModularConstructorPlan> sortedPlans;

    public ModularConstructor(String name){
        super(name);
        update = true;
        sync = true;
        solid = false;
        hasPower = true;
        hasItems = true;
        configurable = true;

        config(Integer.class, (ModularConstructorBuild tile, Integer i) -> {
            tile.currentPlan = i < 0 || i >= plans.size ? -1 : i;
            tile.progress = 0;
        });

        // 适配: UnitFactoryBuild → ModularConstructorBuild
        config(UnitType.class, (ModularConstructorBuild tile, UnitType val) -> {
            tile.currentPlan = plans.indexOf(p -> p.unit == val);
            tile.progress = 0;
        });

        // 适配 v155.4: consumes.add(...) → consume(...)
        consume(new ConsumeItemDynamic((ModularConstructorBuild e) -> e.currentPlan != -1 ? plans.get(e.currentPlan).requirements : ItemStack.empty));
    }

    @Override
    public void init(){
        if(mirrorNodes && moduleNodes != null && moduleNodes.length > 0){
            Vec2 point = moduleNodes[0];
            int amount = Math.abs(point.x) > 0 ? 8 : 4;
            moduleNodes = Arrays.copyOf(moduleNodes, amount);
            int i = 0;
            for(int j = 0; j < 4; j++){
                moduleNodes[i++] = point.cpy().rotate(j * 90f);
                if(Math.abs(point.x) > 0){
                    Vec2 p = point.cpy();
                    moduleNodes[i++] = p.set(-p.x, p.y).rotate(j * 90f);
                }
            }
        }

        capacities = new int[Vars.content.items().size];
        sortedPlans = new Seq<>(plans);

        int i = 0;
        for(ModularConstructorPlan plan : plans){
            plan.index = i++;

            maxTier = Math.max(maxTier, plan.tier);
            for(ItemStack stack : plan.requirements){
                capacities[stack.item.id] = Math.max(capacities[stack.item.id], stack.amount * 2);
                itemCapacity = Math.max(itemCapacity, stack.amount * 2);
            }
        }
        sortedPlans.sort(p -> p.tier);
        super.init();
    }

    @Override
    public void load(){
        super.load();
        topRegions = new TextureRegion[2];
        for(int i = 0; i < 2; i++) topRegions[i] = Core.atlas.find(name + "-top-" + i);
    }

    @Override
    protected TextureRegion[] icons(){
        return new TextureRegion[]{region, Core.atlas.find(name + "-top")};
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    /**
     * 放置预览：绘制模块挂载点位置框（橙色虚线框）
     * 主方块的蓝色框由 InputHandler 绘制，这里只画模块位置提示
     */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Color c = valid ? buildColor : Pal.remove;
        // 镜像展开 moduleNodes 得到所有挂载点
        float tw = moduleSize * Vars.tilesize;
        for(int i = 0; i < moduleNodes.length; i++){
            Vec2 node = moduleNodes[i];
            // 四个镜像位置
            float[][] offsets = mirrorNodes ? new float[][]{
                {node.x, node.y}, {-node.x, node.y}, {node.x, -node.y}, {-node.x, -node.y}
            } : new float[][]{{node.x, node.y}};
            for(float[] o : offsets){
                float wx = (x + o[0]) * Vars.tilesize;
                float wy = (y + o[1]) * Vars.tilesize;
                Drawf.dashRect(c, wx - tw / 2f, wy - tw / 2f, tw, tw);
            }
        }
    }

    public static class ModularConstructorPlan{
        public UnitType unit;
        public ItemStack[] requirements;
        public int tier;
        public float time;
        int index;

        public ModularConstructorPlan(UnitType unit, float time, int tier, ItemStack[] requirements){
            this.unit = unit;
            this.time = time;
            this.tier = tier;
            this.requirements = requirements;
        }
    }

    public class ModularConstructorBuild extends Building implements ModularConstructorModuleInterface{
        public int currentPlan = -1, tier = 0;
        public float progress, topOffset;
        public Seq<ModularConstructorPartBuild> parts = new Seq<>();
        ModularConstructorModule module = new ModularConstructorModule(this);
        Building[] occupied = new Building[moduleNodes.length];


        public int moduleConnections(){
            return moduleConnections;
        }

        @Override
        public ModularConstructorModule consModule(){
            return module;
        }

        @Override
        public boolean consConnected(Building other){
            int ang = Mathf.mod(Mathf.round(other.angleTo(this) / 90f), 4);
            if(moduleBlock == null || moduleBlock == other.block){
                int i = 0;
                for(Vec2 node : moduleNodes){
                    Tmp.r1.setCentered((node.x * Vars.tilesize) + x, (node.y * Vars.tilesize) + y, moduleSize * Vars.tilesize);
                    Tmp.r2.setCentered(other.x, other.y, (other.block.size * Vars.tilesize) - 1f);
                    // 适配 v155.4: other.rotation() (方法) → other.rotation (字段)
                    if(ang == other.rotation && other.block.size == moduleSize && Tmp.r1.contains(Tmp.r2)){
                        occupied[i] = other;
                        return true;
                    }
                    i++;
                }
            }
            return false;
        }

        @Override
        public void buildConfiguration(Table table){
            ButtonGroup<ImageButton> group = new ButtonGroup<>();
            int lastTier = -1;
            table.setBackground(Styles.black3);
            Table cont = null;
            table.add(Core.bundle.format("stat.unity.currentTier", tier + 1));
            table.row();
            for(ModularConstructorPlan plan : sortedPlans){
                if(!plan.unit.unlockedNow() || plan.tier > tier) continue;
                if(plan.tier != lastTier){
                    if(lastTier != -1) table.row();
                    lastTier = plan.tier;
                    table.add("[lightgray]T" + (plan.tier + 1) + ":");
                    table.row();
                    cont = new Table();
                    cont.defaults().size(40);
                    table.add(cont);
                }
                if(cont != null){
                    // 适配 v155.4: Styles.clearToggleTransi → Styles.clearTogglei (重命名)
                    // 适配 v155.4: input.frag.config → input.config (frag 已移除)
                    ImageButton button = cont.button(Tex.whiteui, Styles.clearTogglei, 24, () ->
                    Vars.control.input.config.hideConfig()).group(group).get();
                    button.changed(() -> currentPlan = button.isChecked() ? plan.index : -1);
                    button.getStyle().imageUp = new TextureRegionDrawable(plan.unit.uiIcon);
                    button.update(() -> button.setChecked(currentPlan == plan.index));
                }
            }
        }

        @Override
        public void draw(){
            super.draw();
            for(int i = 0; i < occupied.length; i++){
                if(occupied[i] == null){
                    Vec2 node = moduleNodes[i];
                    Tmp.r1.setCentered((node.x * Vars.tilesize) + x, (node.y * Vars.tilesize) + y, moduleSize * Vars.tilesize);
                    Draw.color(Tmp.c1.set(buildColor).a(0.3f));
                    Fill.crect(Tmp.r1.x, Tmp.r1.y, Tmp.r1.width, Tmp.r1.height);
                    Draw.reset();
                }
            }
            ModularConstructorPlan plan = currentPlan != -1 ? plans.get(currentPlan) : null;
            if(plan != null && plan.tier <= tier){
                float time = progressTime(plan);
                float prog = Mathf.clamp(progress / time);
                Draw.mixcol(buildColor, 1f);
                Draw.alpha(0.35f);
                Draw.rect(plan.unit.fullIcon, this, 0);
                Draw.alpha(1f);
                Draw.mixcol();
                if(progress < time || Units.canCreate(team, plan.unit)){
                    if(progress > 0.001f){
                        Draw.draw(Draw.z(), () -> {
                            Draw.shader(Shaders.blockbuild);
                            Draw.color(buildColor);
                            Shaders.blockbuild.region = plan.unit.fullIcon;
                            Shaders.blockbuild.progress = prog;
                            Draw.rect(plan.unit.fullIcon, this, 0f);
                            Draw.flush();
                            Draw.color();
                            Draw.shader();
                        });
                    }
                }else{
                    Draw.color(0.7f, 0.7f, 0.7f);
                    Draw.rect(plan.unit.fullIcon, this, 0);
                }
                Draw.reset();
            }
            for(int i = 0; i < 4; i++){
                TextureRegion tex = topRegions[Mathf.clamp(i / 2, 0, 1)];
                float ang = (i * 90f);
                Tmp.v1.trns(ang, topOffset).add(this);
                Draw.rect(tex, Tmp.v1, ang);
            }
        }

        @Override
        public void placed(){
            super.placed();
            module.graph.added(this);
        }

        float progressTime(ModularConstructorPlan plan){
            return Math.max(plan.time - Math.max(module.graph.tier - plan.tier, 0f) * efficiencyPerTier, maxEfficiency);
        }

        @Override
        public void updateTile(){
            module.update();

            for(int i = 0; i < occupied.length; i++){
                // 适配 v155.4: occupied[i].added (private) → occupied[i].isAdded()
                if(occupied[i] != null && !occupied[i].isAdded()) occupied[i] = null;
            }

            ModularConstructorPlan plan = currentPlan != -1 ? plans.get(currentPlan) : null;
            if(plan != null && plan.tier <= tier){
                float time = progressTime(plan);
                if(progress >= time){
                    if(Units.canCreate(team, plan.unit) && consValid()){
                        Unit unit = plan.unit.spawn(team, x, y);
                        unit.rotation = 90f;
                        // 适配 v155.4: cons.trigger() → consume()
                        consume();
                        progress = 0f;
                    }
                }else if(consValid()){
                    progress += Time.delta;
                }
                topOffset = Mathf.lerpDelta(topOffset, Math.max(0f, (plan.unit.hitSize / 2f) - minSize), 0.1f);
            }else{
                topOffset = Mathf.lerpDelta(topOffset, 0f, 0.1f);
            }
        }

        // 适配 v155.4: consValid() 不再是 Building 的方法 (已移除), 改为本地方法
        // super.consValid() → potentialEfficiency > 0 (主构造器自身消耗是否满足)
        // build.cons.canConsume() → build.canConsume() (部件消耗是否满足)
        public boolean consValid(){
            boolean valid = true;
            for(ModularConstructorPartBuild build : module.graph.all){
                valid &= build.canConsume();
            }
            return potentialEfficiency > 0 && valid;
        }

        // 适配 v155.4: consumes.has(ConsumeType.item) / consumes.get(ConsumeType.item).valid(this) 已移除
        // v155.4 的 potentialEfficiency 已自动包含 item 消费者效率 (ConsumeItemDynamic.efficiency),
        // 此处只需检查 currentPlan 和 enabled, item 不足时 potentialEfficiency 自动为 0
        @Override
        public boolean shouldConsume(){
            return currentPlan != -1 && enabled;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return currentPlan != -1 && items.get(item) < getMaximumAccepted(item) &&
            Structs.contains(plans.get(currentPlan).requirements, stack -> stack.item == item);
        }

        @Override
        public int getMaximumAccepted(Item item){
            return capacities[item.id];
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.s(currentPlan);
            write.s(tier);
            write.f(progress);

            module.write(write);
        }

        @Override
        public void read(Reads read){
            super.read(read);
            currentPlan = read.s();
            tier = read.s();
            progress = read.f();

            module.read(read);
        }
    }
}
