package zzw.content.units.graphics;

import arc.func.Cons;
import arc.graphics.Color;
import arc.math.Angles;
import arc.util.Tmp;
import mindustry.gen.Rotc;
import mindustry.graphics.Trail;

/**
 * 多重拖尾 (PU132 unity.graphics.MultiTrail 移植)
 *
 * <p>一个 {@link MultiTrail} 内部持有若干条子拖尾 ({@link TrailHold})，
 * 每条子拖尾可以有自己的偏移量 (x, y)、宽度倍率与颜色覆盖。
 * 调用 {@link #update} 时，所有子拖尾会围绕同一点、按各自偏移同步更新，
 * 从而实现"多条丝带缠绕飞行"的效果 (Monolith 灵魂单位的招牌拖尾)。</p>
 *
 * <p>v132 → v155 适配要点:</p>
 * <ul>
 *   <li>基类从 {@code arc.graphics.g2d.Trail} 改为 {@code mindustry.graphics.Trail}
 *       (v7 中 Trail 已从 arc 移入 mindustry)。</li>
 *   <li>v155 的两参 {@code update(x, y)} 会转发到三参 {@code update(x, y, width)}，
 *       因此只需覆写三参版本即可拦截所有更新。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
@SuppressWarnings("unchecked")
public class MultiTrail extends Trail{
    /** 子拖尾列表，每项包含一条 Trail 与它的偏移/宽度/颜色参数。 */
    public TrailHold[] trails;
    /** 旋转处理器: 决定 update 时拖尾的"朝向角"如何计算。 */
    public RotationHandler rotation = MultiTrail::calcRot;

    protected float lastX, lastY;

    /**
     * 构造一条多重拖尾。
     *
     * @param rotation 旋转处理器
     * @param trails   任意数量的子拖尾
     */
    public MultiTrail(RotationHandler rotation, TrailHold... trails){
        this(trails);
        this.rotation = rotation;
    }

    /**
     * 构造一条多重拖尾。
     * <p>逐个检查子拖尾，把 {@link #length} 取所有子拖尾长度的最大值，
     * 这样基类裁剪逻辑不会提前截断较长的子拖尾。</p>
     */
    public MultiTrail(TrailHold... trails){
        super(0);
        this.trails = trails;

        for(TrailHold trail : trails) length = Math.max(length, trail.trail.length);
    }

    /**
     * 生成一个"跟随实体朝向"的旋转处理器。
     * <p>实体仍在场上时直接使用其朝向角，实体已移除时退化为
     * 用上一帧位置到当前位置的连线角度，避免拖尾方向突变。</p>
     */
    public static RotationHandler rot(Rotc e){
        return (trail, x, y) -> e.isAdded() ? e.rotation() : trail.calcRot(x, y);
    }

    /**
     * 遍历所有叶子拖尾 (会递归展开嵌套的 MultiTrail)。
     * <p>用于对每一条实际 Trail 批量执行操作。</p>
     */
    public <T extends Trail> void each(Cons<T> cons){
        for(TrailHold hold : trails){
            Trail t = hold.trail;
            if(t instanceof MultiTrail m){
                m.each(cons);
            }else{
                cons.get((T)t);
            }
        }
    }

    /** 深拷贝: 每条子拖尾都 copy 一份，共享同一旋转处理器。 */
    @Override
    public MultiTrail copy(){
        TrailHold[] mapped = new TrailHold[trails.length];
        for(int i = 0; i < mapped.length; i++) mapped[i] = trails[i].copy();

        MultiTrail out = new MultiTrail(rotation, mapped);
        out.lastX = lastX;
        out.lastY = lastY;
        return out;
    }

    /** 清空所有子拖尾的点。 */
    @Override
    public void clear(){
        for(TrailHold trail : trails) trail.trail.clear();
    }

    /** 返回所有子拖尾中最大的点数。 */
    @Override
    public int size(){
        int size = 0;
        for(TrailHold trail : trails) size = Math.max(size, trail.trail.size());

        return size;
    }

    /** 逐条绘制拖尾端帽; 子拖若有颜色覆盖则优先使用其颜色。 */
    @Override
    public void drawCap(Color color, float width){
        for(TrailHold trail : trails) trail.trail.drawCap(trail.color == null ? color : trail.color, width);
    }

    /** 逐条绘制拖尾主体; 子拖若有颜色覆盖则优先使用其颜色。 */
    @Override
    public void draw(Color color, float width){
        for(TrailHold trail : trails) trail.trail.draw(trail.color == null ? color : trail.color, width);
    }

    /** 逐条截短拖尾 (让旧点逐渐消失)。 */
    @Override
    public void shorten(){
        for(TrailHold trail : trails) trail.trail.shorten();
    }

    /**
     * 同步更新所有子拖尾。
     * <p>步骤:</p>
     * <ol>
     *   <li>用旋转处理器算出当前朝向角 angle (减 90° 转为"指向后方");</li>
     *   <li>把每条子拖的偏移 (trail.x, trail.y) 沿 angle 旋到世界方向上;</li>
     *   <li>以"当前位置 + 偏移"为锚点更新子拖尾，宽度乘上子拖的宽度倍率。</li>
     * </ol>
     */
    @Override
    public void update(float x, float y, float width){
        float angle = rotation.get(this, x, y) - 90f;
        for(TrailHold trail : trails){
            Tmp.v1.trns(angle, trail.x, trail.y);
            trail.trail.update(x + Tmp.v1.x, y + Tmp.v1.y, width * trail.width);
        }

        lastX = x;
        lastY = y;
    }

    /** 用上一次更新点与本次点的连线角度作为朝向角。 */
    public float calcRot(float x, float y){
        return Angles.angle(lastX, lastY, x, y);
    }

    /**
     * 子拖尾条目: 一条 Trail 加上相对主体的偏移、宽度倍率与可选颜色。
     */
    public static class TrailHold{
        public Trail trail;
        public float x;
        public float y;
        public float width;
        public Color color;

        public TrailHold(Trail trail){
            this(trail, 0f, 0f, 1f, null);
        }

        public TrailHold(Trail trail, Color color){
            this(trail, 0f, 0f, 1f, color);
        }

        public TrailHold(Trail trail, float x, float y){
            this(trail, x, y, 1f, null);
        }

        public TrailHold(Trail trail, float x, float y, float width){
            this.trail = trail;
            this.x = x;
            this.y = y;
            this.width = width;
        }

        public TrailHold(Trail trail, float x, float y, float width, Color color){
            this.trail = trail;
            this.x = x;
            this.y = y;
            this.width = width;
            this.color = color;
        }

        public TrailHold copy(){
            return new TrailHold(trail.copy(), x, y, width, color);
        }
    }

    /** 旋转处理器: 根据拖尾自身与当前位置返回朝向角 (度)。 */
    public interface RotationHandler{
        float get(MultiTrail trail, float x, float y);
    }
}
