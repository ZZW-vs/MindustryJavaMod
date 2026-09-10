package zzw.content.units.entities;

import arc.util.Log;
import mindustry.gen.UnitEntity;
import zzw.content.type.UnityUnitType;
import zzw.content.units.ZEntityRegister;
import zzw.content.units.type.decal.UnitDecorationType;
import zzw.content.units.type.decal.UnitDecorationType.UnitDecoration;

/**
 * 装饰单位实体 (PU132 unity.gen.DecorationUnit 移植)
 *
 * <p>PU132 通过注解处理器生成 Decorationc 接口 + DecorationUnit 实体,
 * 本 mod 直接继承 UnitEntity 内嵌装饰状态字段 (与 CopterUnitEntity 同模式)。</p>
 *
 * <p>工作流程 (对照 PU132 生成代码):
 * <br>1. add(): 从 UnityUnitType.decorations 配置创建 UnitDecoration[] 实例,
 *     并调用每个 decor.added(this) 初始化 (对应生成代码 added() 的 decoration: 段);
 * <br>2. update(): 先执行原版单位逻辑, 再驱动每个 decor.update(this)
 *     (对应生成代码 update() 的 decoration: 段);
 * <br>3. 绘制由 UnityUnitType.drawOutline/drawBody 负责 (类型侧, 非实体侧)。</p>
 *
 * <p>★ 调试: add() 和 update() 首帧会打印装饰初始化/驱动日志, 方便排查翅膀不显示问题。</p>
 */
public class DecorationUnitEntity extends UnitEntity{
    /** 装饰状态实例列表 (翅膀等), add() 时根据类型配置创建 */
    public UnitDecoration[] decors = {};

    /** 实体工厂 (constructor 引用) */
    public static DecorationUnitEntity create() {
        return new DecorationUnitEntity();
    }

    @Override
    public int classId() {
        return ZEntityRegister.classId(DecorationUnitEntity.class);
    }

    /**
     * 添加到世界时初始化装饰实例 (PU132 生成代码 added() 的 decoration: 段)。
     */
    @Override
    public void add() {
        super.add();
        // 从 UnityUnitType.decorations 配置创建装饰状态实例
        if(type instanceof UnityUnitType uType && uType.decorations.size > 0){
            decors = new UnitDecoration[uType.decorations.size];
            for(int i = 0; i < decors.length; i++){
                UnitDecorationType decoType = uType.decorations.get(i);
                decors[i] = decoType.decalType.get(decoType);
            }
            Log.info("[deco-debug] @ 装饰初始化完成, 数量: @ (type=@)",
                type.name, decors.length, getClass().getSimpleName());
        }else{
            Log.warn("[deco-warn] @ 的类型不是 UnityUnitType 或 decorations 为空, 装饰未创建!", type != null ? type.name : "null");
        }
        // 装饰初始化回调 (翅膀记录初始朝向等)
        for(UnitDecoration decor : decors){
            decor.added(this);
        }
    }

    /**
     * 每帧更新装饰状态 (PU132 生成代码 update() 的 decoration: 段)。
     */
    @Override
    public void update() {
        super.update();
        // 驱动翅膀扇动动画等
        for(UnitDecoration decor : decors){
            decor.update(this);
        }
    }
}
