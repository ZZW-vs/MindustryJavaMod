package zzw.content.mechanics.torque.meta;

/**
 * 坩埚合金配方 (PU132 unity.world.meta.CrucibleRecipe 移植)
 * <p>
 * 定义坩埚中多种熔融物品合成为新物品的配方。
 * <p>
 * 使用静态数组 {@link #all} 全局注册所有合金配方。
 */
public class CrucibleRecipe{
    /** 所有合金配方 */
    public static final CrucibleRecipe[] all = new CrucibleRecipe[5];
    private static byte total;

    /** 合金产物 */
    public final MeltInfo melt;
    /** 输入原料列表 */
    public final InputRecipe[] input;
    /** 合金速度 */
    public final float alloySpeed;

    public CrucibleRecipe(MeltInfo melt, float alloySpeed, InputRecipe... input){
        this.melt = melt;
        this.alloySpeed = alloySpeed;
        this.input = input;

        all[total++] = this;
    }

    public static byte total(){
        return total;
    }

    /**
     * 输入原料定义
     */
    public static class InputRecipe{
        /** 原料类型 */
        public final MeltInfo material;
        /** 所需数量 */
        public final float amount;
        /** 是否需要液态 (true=需要熔融状态, false=固态即可) */
        public final boolean needsLiquid;

        public InputRecipe(MeltInfo material, float amount, boolean needsLiquid){
            this.material = material;
            this.amount = amount;
            this.needsLiquid = needsLiquid;
        }

        public InputRecipe(MeltInfo material, float amount){
            this(material, amount, true);
        }
    }
}
