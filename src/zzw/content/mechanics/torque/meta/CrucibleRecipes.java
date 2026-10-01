package zzw.content.mechanics.torque.meta;

import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.entities.Fires;
import mindustry.game.Team;
import mindustry.content.Bullets;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.type.Liquid;
import zzw.content.Z_Items;
import zzw.content.Z_Liquids;
import zzw.content.mechanics.torque.blocks.GraphBlockBase.GraphBuildBase;

/**
 * 坩埚熔化配方与元数据 (PU_V8 unity.world.meta.CrucibleRecipes 移植)
 * <p>
 * 相比老的 PU132 版 {@code MeltInfo}/{@code CrucibleRecipe}, 本版把"固/液/汽"三态
 * 与相变能量统一到 {@link CrucibleIngredient} 体系:
 * <ul>
 *   <li>{@link CrucibleIngredient} - 通用原料 (物品或液体), 记录熔点/熔速/相变能/沸点/沸速</li>
 *   <li>{@link CrucibleItem} - 物品原料</li>
 *   <li>{@link CrucibleLiquid} - 液体原料 (额外处理汽化特效: 可燃起火/易爆伤害/低温蒸汽)</li>
 *   <li>{@link CrucibleRecipe} - 合金配方 (多种原料 → 一种产物, 有最低温度门槛)</li>
 * </ul>
 * <p>
 * 使用静态注册表 {@link #ingredients}/{@link #items}/{@link #liquids}/{@link #recipes},
 * 由 {@link #load()} 在物品加载完成后填充 (见 TestMod 加载顺序)。
 * <p>
 * 温度: 游戏内部使用开尔文 (K), 配方用摄氏度书写, 通过 {@code celsiusZero + tempC} 转换。
 */
public class CrucibleRecipes{
    /** 摄氏度零点在开尔文下的数值 (0°C = 273.15K) */
    public static final float celsiusZero = 273.15f;

    /** 所有原料 (按 id 索引) */
    public static IntMap<CrucibleIngredient> ingredients = new IntMap<>();
    /** 物品 → 原料 映射 */
    public static ObjectMap<Item, CrucibleItem> items = new ObjectMap<>();
    /** 液体 → 原料 映射 */
    public static ObjectMap<Liquid, CrucibleLiquid> liquids = new ObjectMap<>();
    /** 所有合金配方 */
    public static Seq<CrucibleRecipe> recipes = new Seq<>();

    private static boolean loaded;

    /**
     * 注册全部坩埚原料与配方。
     * <p>必须在 {@link Z_Items#load()} 之后调用, 否则引用的自定义物品仍为 null。
     */
    public static void load(){
        if(loaded) return;
        loaded = true;

        int id = 0;
        addItem(id++, Items.copper, 900, 0.1f, 30);
        addItem(id++, Items.lead, 400, 0.15f, 10);
        addItem(id++, Z_Items.nickel, 930, 0.15f, 40);
        addItem(id++, Z_Items.cupronickel, 800, 0.15f, 60);
        addItem(id++, Items.sand, 1300, 0.15f, 20);
        addItem(id++, Items.metaglass, 900, 0.15f, 25);
        addItem(id++, Items.silicon, 1000, 0.15f, 60);
        addItem(id++, Items.titanium, 1750, 0.15f, 60);
        addItem(id++, Items.plastanium, 400, 0.15f, 30);
        addItem(id++, Items.thorium, 1750, 0.15f, 30);
        addItem(id++, Items.surgeAlloy, 1500, 0.15f, 120);
        addItem(id++, Z_Items.superAlloy, 1800, 0.15f, 200);
        // 煤/石墨的熔点为 -1 (哨兵值: 不参与固→液熔化, 直接作为碳源参与反应)
        addItem(id++, Items.coal, -1f, 0.15f, 220);
        addItem(id++, Items.graphite, -1f, 0.15f, 220);
        addItem(id++, Items.pyratite, 500, 0.15f, 220);
        addLiquid(id++, Liquids.water, 0, 0.05f, 100, 0.05f, 450);
        addLiquid(id++, Liquids.cryofluid, -200, 0.1f, -190, 0.1f, 50);
        addLiquid(id++, Liquids.slag, 400, 0.1f, 2200, 0.1f, 60);
        addLiquid(id++, Liquids.oil, -150, 0.1f, 350, 0.1f, 120);
        addItem(id++, Z_Items.stone, -1f, 0.1f, 220);
        addItem(id++, Z_Items.denseAlloy, 900, 0.15f, 120);
        addItem(id++, Z_Items.steel, 1500, 0.15f, 50);
        addItem(id++, Z_Items.dirium, 2500, 0.1f, 520);
        addItem(id++, Z_Items.uranium, 1500, 0.1f, 80);
        addLiquid(id++, Z_Liquids.lava, 1300, 0.1f, 3250, 0.1f, 300);
        addLiquid(id++, Liquids.neoplasm, -10, 0.1f, 850, 0.01f, 100);

        // ===== 合金配方 (PU_V8 原版) =====
        recipes.add(new CrucibleRecipe(items.get(Z_Items.cupronickel), 0.1f, celsiusZero + 150,
            needs(Items.copper, 2, true),
            needs(Z_Items.nickel, 1, true)));
        recipes.add(new CrucibleRecipe(items.get(Items.metaglass), 0.1f, celsiusZero + 950,
            needs(Items.lead, 0.5f, true),
            needs(Items.sand, 0.5f, false)));
        recipes.add(new CrucibleRecipe(items.get(Items.silicon), 0.15f, celsiusZero + 1350,
            needs(Items.coal, 0.5f, false),
            needs(Items.sand, 0.5f, true)));
        recipes.add(new CrucibleRecipe(items.get(Items.silicon), 0.15f, celsiusZero + 1500,
            needs(Items.graphite, 0.25f, false),
            needs(Items.sand, 0.5f, true)));
        // 超高效石墨配方: 只要达到足够高的温度
        recipes.add(new CrucibleRecipe(items.get(Items.graphite), 0.02f, celsiusZero + 2000,
            needs(Items.coal, 0.5f, false)));
        recipes.add(new CrucibleRecipe(items.get(Items.surgeAlloy), 0.1f, celsiusZero + 1200,
            needs(Items.copper, 2, true),
            needs(Items.lead, 2, true),
            needs(Items.titanium, 2, true),
            needs(Items.silicon, 2, true)));
        recipes.add(new CrucibleRecipe(items.get(Z_Items.superAlloy), 0.1f, celsiusZero + 2000,
            needs(Z_Items.cupronickel, 3, true),
            needs(Items.surgeAlloy, 2, true),
            needs(Items.pyratite, 2, false)));
        // 故意非常慢, 也许某些时间操作可以加速?
        recipes.add(new CrucibleRecipe(items.get(Z_Items.dirium), 0.0002f, celsiusZero + 2000,
            needs(Z_Items.uranium, 1, true),
            needs(Z_Items.steel, 1, true),
            needs(Items.titanium, 1, true),
            needs(Items.pyratite, 2, false)));
        recipes.add(new CrucibleRecipe(items.get(Z_Items.denseAlloy), 0.02f, celsiusZero + 1500,
            needs(Z_Liquids.lava, 0.5f, true),
            needs(Items.copper, 1, true),
            needs(Items.lead, 2, true)));
        recipes.add(new CrucibleRecipe(items.get(Z_Items.steel), 0.003f, celsiusZero + 1500,
            needs(Items.graphite, 1, false),
            needs(Z_Items.denseAlloy, 2, true)));
        recipes.add(new CrucibleRecipe(liquids.get(Z_Liquids.lava), 0.05f, celsiusZero + 1500,
            needs(Z_Items.stone, 1, false)));
        recipes.add(new CrucibleRecipe(items.get(Z_Items.stone), 1f, 0,
            needs(Z_Liquids.lava, 1, false, true)));
    }

    /** 注册一个物品原料 */
    public static void addItem(int id, Item item, float meltpointCelsius, float meltspeed, float energy){
        items.put(item, new CrucibleItem(id, item, celsiusZero + meltpointCelsius, meltspeed, energy));
        ingredients.put(id, items.get(item));
    }

    /** 注册一个液体原料 (含沸点/沸速) */
    public static void addLiquid(int id, Liquid liquid, float meltpointCelsius, float meltspeed, float boilingpointCelsius, float boilingspeed, float energy){
        liquids.put(liquid, new CrucibleLiquid(id, liquid, celsiusZero + meltpointCelsius, meltspeed, celsiusZero + boilingpointCelsius, boilingspeed, energy));
        ingredients.put(id, liquids.get(liquid));
    }

    /** 通用原料: 物品/液体的共同父类, 记录相变参数 */
    public static class CrucibleIngredient{
        /** 贴图缓存 (注册阶段各 Content 尚未 load(), fullIcon 为 null, 由 {@link #icon()} 延迟解析) */
        public TextureRegion icon;
        public String name;
        public Color color = Color.pink;
        public int id;
        /** 熔点 (K), -1 表示不熔化 (哨兵值) */
        public float meltingpoint = -1;
        public float meltspeed;
        /** 相变能量 (熔化/凝固/汽化消耗的热量) */
        public float phaseChangeEnergy = 0;
        /** 沸点 (K), -1 表示不汽化 */
        public float boilpoint = -1;
        public float boilspeed;

        public CrucibleIngredient(TextureRegion icon, String name, int id){
            this.icon = icon;
            this.name = name;
            this.id = id;
        }

        /**
         * 获取贴图 (延迟解析)。
         * <p>坩埚配方在 {@code loadContent()} 阶段注册, 此时各 Content 的 {@code load()} 尚未调用,
         * {@code item/liquid.fullIcon} 仍为 null (注册时捕获到的贴图即为 null)。
         * 因此这里在首次真正需要时再从 item/liquid 重新读取, 避免 UI 面板因 null 贴图崩溃。</p>
         */
        public TextureRegion icon(){
            if(icon == null){
                if(this instanceof CrucibleItem it){
                    icon = it.item.fullIcon;
                }else if(this instanceof CrucibleLiquid lq){
                    icon = lq.liquid.fullIcon;
                }
            }
            return icon;
        }

        public void onVapourise(GraphBuildBase cgn, float am){}
        public void onMelt(GraphBuildBase cgn, float am){}
        public void onSolidify(GraphBuildBase cgn, float am){}
        public void onTemperature(GraphBuildBase cgn, float temp){}
    }

    /** 物品原料 */
    public static class CrucibleItem extends CrucibleIngredient{
        public Item item;

        public CrucibleItem(int id, Item item, float meltingpoint, float meltspeed, float phaseChangeEnergy){
            super(item.fullIcon, item.name, id);
            this.item = item;
            this.meltingpoint = meltingpoint;
            this.meltspeed = meltspeed;
            this.phaseChangeEnergy = phaseChangeEnergy;
            color = item.color;
        }
    }

    /** 液体原料: 汽化时触发可燃/易爆/低温特效 */
    public static class CrucibleLiquid extends CrucibleIngredient{
        public Liquid liquid;

        public CrucibleLiquid(int id, Liquid liquid, float meltingpoint, float meltspeed, float boilingpoint, float boilingspeed, float phaseChangeEnergy){
            super(liquid.fullIcon, liquid.name, id);
            this.liquid = liquid;
            this.meltingpoint = meltingpoint;
            this.meltspeed = meltspeed;
            this.boilpoint = boilingpoint;
            this.boilspeed = boilingspeed;
            this.phaseChangeEnergy = phaseChangeEnergy;
            color = liquid.color;
        }

        @Override
        public void onVapourise(GraphBuildBase cgn, float am){
            Building b = cgn.asBuilding();
            // 可燃液体汽化 → 起火 / 火球
            if(liquid.flammability > 0.5f){
                if(Mathf.random() < am && Math.random() > 0.9){
                    Fires.create(b.tile);
                }
                if(Mathf.random(3) < am && Math.random() > 0.97){
                    Bullets.fireball.createNet(Team.derelict, b.x, b.y, Mathf.random(360f), -1f, 1, 1);
                }
            }
            // 易爆液体汽化 → 对建筑造成伤害
            if(liquid.explosiveness > 0.5f){
                b.damage(am * liquid.explosiveness);
            }
            // 低温液体汽化 → 蒸汽/水泡
            if(liquid.temperature <= 0.6f){
                float s = cgn.asBuilding().block.size * 2.5f;
                if(Mathf.random() < am && Math.random() > 0.3){
                    Fx.steam.at(b.x + Mathf.range(s), b.y + Mathf.range(s));
                }
            }
        }
    }

    /** 合金配方中的单个输入项 */
    public static class RecipeIngredient{
        public CrucibleIngredient ingredient;
        public float amount;
        /** true=需要熔融态输入, false=固/液皆可 */
        public boolean melted;
        /** true=必须是固态输入 */
        public boolean requiresSolid;

        public RecipeIngredient(CrucibleIngredient item, float amount, boolean melted){
            this.ingredient = item;
            this.amount = amount;
            this.melted = melted;
        }

        public RecipeIngredient(CrucibleIngredient item, float amount, boolean melted, boolean requiresSolid){
            this.ingredient = item;
            this.amount = amount;
            this.melted = melted;
            this.requiresSolid = requiresSolid;
        }
    }

    static RecipeIngredient needs(Item item, float amount, boolean melted){
        return new RecipeIngredient(items.get(item), amount, melted);
    }

    static RecipeIngredient needs(Item item, float amount, boolean melted, boolean solid){
        return new RecipeIngredient(items.get(item), amount, melted, solid);
    }

    static RecipeIngredient needs(Liquid liquid, float amount, boolean melted){
        return new RecipeIngredient(liquids.get(liquid), amount, melted);
    }

    static RecipeIngredient needs(Liquid liquid, float amount, boolean melted, boolean solid){
        return new RecipeIngredient(liquids.get(liquid), amount, melted, solid);
    }

    /** 合金配方: 多输入 → 单产物 */
    public static class CrucibleRecipe{
        public RecipeIngredient[] items;
        public CrucibleIngredient output;
        /** 触发配方的最低温度 (K) */
        public float minTemp = 0;
        public float speed = 0.1f;

        CrucibleRecipe(CrucibleIngredient output, float speed, float minTemp, RecipeIngredient... items){
            this.output = output;
            this.items = items;
            this.speed = speed;
            this.minTemp = minTemp;
        }
    }
}
