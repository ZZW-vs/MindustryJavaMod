package zzw.content.units.effects;

import arc.func.Cons;
import arc.func.Prov;
import arc.graphics.Color;
import mindustry.entities.Effect;
import mindustry.gen.EffectState;
import mindustry.gen.Posc;

/**
 * 自定义状态特效 (PU132 unity.entities.effects.CustomStateEffect 移植)。
 *
 * <p>原版 {@link Effect} 的特效状态固定从 {@link EffectState} 对象池取出;
 * 本类允许外部通过 {@link Prov} 提供 <b>任意 {@link EffectState} 子类实例</b>,
 * 子类可携带自己的字段 (如拖尾数组) 并覆写 {@code remove()} 做清理,
 * 是 "带内部状态的特效" (PU132 ChargeFx.tendenceCharge 等) 的基础。</p>
 *
 * <p>★ v155 适配: {@link Effect} 的 {@code create(...)} 已改为 public 且内置
 * {@code shouldCreate()} / 惰性 {@code init()} / {@code startDelay} 处理,
 * 故本类不再覆写 {@code create} 与各 {@code at(...)} 重载 (与基类完全等价),
 * 改为覆写钩子 {@link #add(float, float, float, Color, Object)} ——
 * 基类 create 在视锥剔除通过后调用 add, 原版 v132 中挂接点即在此处。
 * {@link EffectState} 已组件化 (mindustry.gen 生成类),
 * {@code create()} 静态工厂与 {@code parent / rotWithParent} 字段仍然可用,
 * {@code followParent} 默认值在 v155 中为 true, 与原逻辑一致。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class CustomStateEffect extends Effect{
    /** 特效状态供应商: 每次触发时调用, 返回池化或新建的状态实例。 */
    public Prov<? extends EffectState> stateProvider;

    public CustomStateEffect(float lifetime, Cons<EffectContainer> container){
        this(EffectState::create, lifetime, container);
    }

    public CustomStateEffect(Prov<? extends EffectState> prov, float lifetime, Cons<EffectContainer> container){
        this(prov, lifetime, 50f, container);
    }

    public CustomStateEffect(Prov<? extends EffectState> prov, float lifetime, float clip, Cons<EffectContainer> container){
        super(lifetime, clip, container);
        this.stateProvider = prov;
    }

    /**
     * 创建特效实例: 基类 create 的挂接点, 改用 {@link #inst} 生成状态并加入世界。
     */
    @Override
    protected void add(float x, float y, float rotation, Color color, Object data){
        inst(x, y, rotation, color, data).add();
    }

    /**
     * 从供应商取出状态实例并填充基础字段。
     *
     * <p>子类可覆写本方法, 在 {@code super.inst(...)} 之后再向
     * {@code state.data} 塞入自定义数据 (如拖尾数组)。</p>
     */
    protected EffectState inst(float x, float y, float rotation, Color color, Object data){
        EffectState e = stateProvider.get();
        e.effect = this;
        e.rotation = baseRotation + rotation;
        e.data = data;
        e.lifetime = lifetime;
        e.set(x, y);
        e.color.set(color);
        if(followParent && data instanceof Posc p){
            e.parent = p;
            e.rotWithParent = rotWithParent;
        }

        return e;
    }
}
