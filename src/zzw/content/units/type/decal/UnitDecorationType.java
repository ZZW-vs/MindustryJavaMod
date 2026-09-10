package zzw.content.units.type.decal;

import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import mindustry.gen.*;

/**
 * 单位装饰类型基类 (PU132 unity.type.decal.UnitDecorationType 完整移植)
 *
 * <p>装饰系统工作原理 (对照 PU132):
 * <br>1. UnitType 侧: UnityUnitType.decorations 保存装饰配置,
 *     load() 时加载贴图, drawOutline/drawBody 时驱动绘制;
 * <br>2. 实体侧: 自定义实体 (DecorationUnitEntity) 持有 UnitDecoration[] decors,
 *     add() 时根据类型配置创建实例, update() 时驱动动画;
 * <br>3. top=false 的装饰 (如翅膀) 绘制在单位身体<b>下方</b> (drawOutline 阶段),
 *     top=true 的装饰绘制在身体<b>上方</b> (drawBody 阶段)。</p>
 *
 * @see WingDecorationType 翅膀装饰 (deviation 使用)
 */
public abstract class UnitDecorationType{
    /** 装饰实例工厂: 通过类型创建对应的状态实例 (PU132 原版字段) */
    public Func<UnitDecorationType, UnitDecoration> decalType = UnitDecoration::new;
    /** 是否绘制在单位身体上方 (false = 绘制在身体下方, 翅膀为 false) */
    public boolean top = false;

    /**
     * 每帧更新装饰状态 (由实体的 update() 调用)。
     *
     * @param unit 持有该装饰的单位
     * @param deco 该装饰的状态实例
     */
    public void update(Unit unit, UnitDecoration deco){}

    /**
     * 单位被添加到世界时调用一次 (由实体的 add() 调用)。
     *
     * @param unit 持有该装饰的单位
     * @param deco 该装饰的状态实例
     */
    public void added(Unit unit, UnitDecoration deco){}

    /**
     * 绘制装饰 (由 UnityUnitType 的 drawOutline/drawBody 调用)。
     *
     * @param unit 持有该装饰的单位
     * @param deco 该装饰的状态实例
     */
    public void draw(Unit unit, UnitDecoration deco){}

    /**
     * 绘制单位图标时把装饰贴图合成进去 (PU132 原版)。
     *
     * @param prov 贴图 → Pixmap 转换器
     * @param icon 目标图标 Pixmap
     * @param outliner 贴图描边处理器
     */
    public void drawIcon(Func<TextureRegion, Pixmap> prov, Pixmap icon, Func<TextureRegion, TextureRegion> outliner){}

    /**
     * 加载装饰所需贴图 (由 UnityUnitType.load() 调用)。
     */
    public void load(){}

    /**
     * 装饰状态实例 (PU132 原版)
     *
     * <p>每个装饰类型对应一个状态子类 (如 WingDecoration),
     * 保存该装饰每单位独立的动画状态。</p>
     */
    public static class UnitDecoration{
        /** 该状态实例对应的装饰类型 */
        public UnitDecorationType type;

        /**
         * @param type 该状态实例对应的装饰类型
         */
        public UnitDecoration(UnitDecorationType type){
            this.type = type;
        }

        /**
         * 每帧更新 (转发给类型实现)。
         *
         * @param unit 持有该装饰的单位
         */
        public void update(Unit unit){
            type.update(unit, this);
        }

        /**
         * 单位添加时调用一次 (转发给类型实现)。
         *
         * @param unit 持有该装饰的单位
         */
        public void added(Unit unit){
            type.added(unit, this);
        }
    }
}
