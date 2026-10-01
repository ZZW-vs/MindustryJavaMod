package zzw.content.optics;

import mindustry.gen.Building;

/**
 * 光持有建筑通用接口 (PU132 @Merge(LightHoldc) 的接口化移植)
 *
 * <p>PU132 用 @Merge 注解把受光能力织入任意基类 (GenericCrafter / Wall 等);
 * 本移植改为接口抽象, 使不同基类的建筑 (GenericCrafter 系的光源/反射镜/光工厂,
 * Wall 系的玻璃墙) 都能持有受光槽并参与光路传播。</p>
 *
 * <p>实现者须同时是 {@link Building}: 接口方法本身访问不到 Building 的字段,
 * 统一通过 {@link #building()} 获取建筑本体。</p>
 */
public interface LightHolder {
    /** 建筑本体 (用于访问 x/y/block 等 Building 字段) */
    Building building();

    /** 受光槽数组 (created 时按 block.acceptors 建立) */
    LightAcceptor[] lightSlots();

    /** 光射到 (x,y) 时是否接收 (判定槽边界) */
    boolean acceptLight(Light light, int x, int y);

    /** 把光加入对应槽 */
    void addLight(Light light, int x, int y);

    /** 从所有槽移除该光 */
    void removeLight(Light light);

    /** 光触到本建筑时同步回调 (反射镜/玻璃墙在此注册 child) */
    void interact(Light light);

    /** 当前受光比例 [0..1] */
    float lightStatus();

    /** 全部槽是否都需要光 (决定是否按光照缩放效率) */
    boolean requiresLight();

    /** 是否需要重新交互 (转动后) */
    boolean needsReinteract();
}