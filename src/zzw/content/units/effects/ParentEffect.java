package zzw.content.units.effects;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.math.geom.Rect;
import arc.util.Tmp;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.EffectState;
import mindustry.gen.Posc;
import mindustry.gen.Rotc;
import mindustry.world.blocks.defense.turrets.BaseTurret.BaseTurretBuild;

/**
 * 跟随父实体的特效 (PU132 unity.entities.effects.ParentEffect 移植)。
 *
 * <p>与原版 {@link Effect} 的区别: {@code at(x, y, rotation, data)} 时只要
 * data 是 {@link Posc} (单位 / 建筑 / 子弹), 特效状态就会持续跟随该父实体
 * 的位置与旋转, 用于 "挂在炮口上的充能环" 之类的效果。</p>
 *
 * <p>★ v155 适配要点: v155 的 {@link EffectState} 已组件化 (EffectStateComp),
 * {@code parent / offsetX / offsetY} 等字段由 ChildComp 生成且为 public,
 * {@code Effect.add()} 加入世界时会自动记录 offsetX/offsetY 初值,
 * {@link ParentEffectState} 覆写 {@code update()} 后按 PU132 原公式
 * (父旋转角 - positionRotation 方向重投影) 重新计算位置。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class ParentEffect extends Effect{
    public ParentEffect(float life, Cons<EffectContainer> renderer){
        super(life, renderer);
    }

    public ParentEffect(float life, float clipSize, Cons<EffectContainer> renderer){
        super(life, clipSize, renderer);
    }

    @Override
    public void at(float x, float y, float rotation, Object data){
        at(x, y, rotation, Color.white, data);
    }

    @Override
    public void at(float x, float y, float rotation, Color color, Object data){
        create(this, x, y, rotation, color, data);
    }

    /**
     * 创建跟随父实体的特效状态。
     *
     * <p>步骤:</p>
     * <ol>
     *   <li>headless / 关闭特效设置时直接跳过;</li>
     *   <li>视锥剔除: 特效包围盒与相机包围盒不相交则跳过;</li>
     *   <li>从对象池取出 {@link ParentEffectState}, 填入 effect / 旋转 / data / 寿命 / 颜色;</li>
     *   <li>从 data 解析父实体的初始旋转角 (Rotc 优先, 炮塔建筑其次),
     *       并记录 positionRotation (特效相对父的方位角 - 父旋转角),
     *       供 update 时还原特效的环绕位置;</li>
     *   <li>加入世界。</li>
     * </ol>
     */
    public static void create(Effect effect, float x, float y, float rotation, Color color, Object data){
        if(Vars.headless || effect == Fx.none) return;
        if(Core.settings.getBool("effects")){
            Rect view = Core.camera.bounds(Tmp.r1);
            Rect pos = Tmp.r2.setSize(effect.clip).setCenter(x, y);

            if(view.overlaps(pos)){
                ParentEffectState entity = createState();
                entity.effect = effect;
                entity.rotation = rotation;
                entity.originalRotation = rotation;
                entity.data = (data);
                entity.lifetime = (effect.lifetime);
                entity.set(x, y);
                entity.color.set(color);
                float rotationA = 0f;
                if(data instanceof Rotc){
                    rotationA = ((Rotc)data).rotation();
                }else if(data instanceof BaseTurretBuild){
                    rotationA = ((BaseTurretBuild)data).rotation;
                }
                if(data instanceof Posc){
                    entity.parent = ((Posc)data);
                    entity.positionRotation = (((Posc)data).angleTo(entity) - rotationA);
                }
                entity.add();
            }
        }
    }

    public static ParentEffectState createState(){
        return Pools.obtain(ParentEffectState.class, ParentEffectState::new);
    }

    /**
     * 跟随父实体的特效状态: 每帧根据父实体当前旋转与初始相对方位
     * 重新投影特效位置, 并把父旋转量累进特效自身旋转。
     */
    public static class ParentEffectState extends EffectState{
        /** 创建时的特效原始旋转 (父旋转清零时的基准)。 */
        public float originalRotation = 0f;
        /** 特效相对父实体的方位角 (已扣除创建时的父旋转)。 */
        public float positionRotation = 0f;

        @Override
        public void update(){
            super.update();

            if(parent != null){
                // 父实体当前旋转角: Rotc 优先, 炮塔建筑取其 rotation 字段
                float rotationA = 0f;
                if(parent instanceof Rotc){
                    rotationA = ((Rotc)parent).rotation();
                }else if(parent instanceof BaseTurretBuild){
                    rotationA = ((BaseTurretBuild)parent).rotation;
                }
                // 特效旋转 = 父当前旋转 - 原始旋转 (父转多少, 特效跟着转多少)
                rotation = rotationA - originalRotation;
                // 初始偏移向量的模长 (offsetX/offsetY 在 add() 时由基类记录)
                float len = (float)Math.sqrt(offsetX * offsetX + offsetY * offsetY);
                // 按新旋转角重投影偏移, 再叠加父实体当前位置
                Tmp.v1.trns(rotationA - positionRotation, len).add(parent);
                x = Tmp.v1.x;
                y = Tmp.v1.y;
            }
        }
    }
}
