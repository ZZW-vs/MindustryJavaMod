package zzw.content.units.util;

import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Cons2;
import arc.func.Floatc;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.IntSet;
import mindustry.gen.Building;
import mindustry.world.Tile;

import static mindustry.Vars.world;
import static mindustry.Vars.indexer;
import static mindustry.Vars.tilesize;

/**
 * PU_V8 unity.util.Utils 移植版 (仅移植治疗子弹所需的方法)
 *
 * 移植方法:
 * - shotgunRange: 在 [-range, +range] 范围内均匀分布 points 个角度
 * - angleDistSigned: 带符号角度差 (-180 ~ 180)
 * - angleDist: 绝对角度差 (0 ~ 180)
 * - castCircle: 圆形射线扫描, 返回每个角度的最大可达距离, 并对圆内建筑回调
 * - castConeTile: 锥形射线扫描, 在锥形 tile 范围内回调建筑
 *
 * 参考: PU_V8 main/src/unity/util/Utils.java
 */
public final class UnityUtils {
    private static final Vec2 tV = new Vec2();
    private static final Rect rect = new Rect(), rectAlt = new Rect();
    private static final IntSet collidedBlocks = new IntSet();
    private static int idx = 0;

    private UnityUtils() {}

    /** 在 [-range, +range] 范围内均匀分布 points 个角度, 对每个角度调用 cons */
    public static void shotgunRange(int points, float range, float angle, Floatc cons) {
        if (points <= 1) {
            cons.get(angle);
            return;
        }
        for (int i = 0; i < points; i++) {
            float in = Mathf.lerp(-range, range, i / (points - 1f));
            cons.get(in + angle);
        }
    }

    /** 带符号角度差 (-180 ~ 180), 与 PU_V8 angleDistSigned 行为一致 */
    public static float angleDistSigned(float a, float b) {
        a += 360f;
        a %= 360f;
        b += 360f;
        b %= 360f;
        float d = Math.abs(a - b) % 360f;
        int sign = (a - b >= 0f && a - b <= 180f) || (a - b <= -180f && a - b >= -360f) ? 1 : -1;
        return (d > 180f ? 360f - d : d) * sign;
    }

    /** 绝对角度差 (0 ~ 180) */
    public static float angleDist(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return (d > 180f ? 360f - d : d);
    }

    /**
     * 圆形射线扫描 (移植 PU_V8 Utils.castCircle)
     *
     * @param wx 中心 x
     * @param wy 中心 y
     * @param range 最大半径
     * @param rays 射线数量
     * @param filter 建筑过滤谓词 (true 才会被回调)
     * @param cons 命中建筑回调
     * @param insulator 障碍 tile 谓词 (如 absorbLasers), 返回 true 则射线在此处截断
     * @return 每个角度的最大可达距离 (线性距离, 非 squared)
     */
    public static float[] castCircle(float wx, float wy, float range, int rays,
                                      Boolf<Building> filter, Cons<Building> cons, Boolf<Tile> insulator) {
        collidedBlocks.clear();
        float[] cast = new float[rays];

        for (int i = 0; i < cast.length; i++) {
            cast[i] = range;
            float ang = i * (360f / cast.length);
            tV.trns(ang, range).add(wx, wy);
            final int s = i;
            world.raycastEachWorld(wx, wy, tV.x, tV.y, (cx, cy) -> {
                Tile t = world.tile(cx, cy);
                if (t != null && t.block() != null && insulator.get(t)) {
                    float dst = t.dst(wx, wy);
                    cast[s] = dst;
                    return true;
                }
                return false;
            });
        }

        indexer.allBuildings(wx, wy, range, build -> {
            if (!filter.get(build)) return;
            float ang = Angles.angle(wx, wy, build.x, build.y);
            float dst = build.dst2(wx, wy) - ((build.hitSize() * build.hitSize()) / 2f);
            int i = Mathf.mod(Mathf.round((ang % 360f) / (360f / cast.length)), cast.length);
            float d = cast[i];
            if (dst <= d * d) {
                cons.get(build);
            }
        });

        return cast;
    }

    /**
     * 锥形射线扫描 (移植 PU_V8 Utils.castConeTile)
     * 在 (angle-cone, angle+cone) 锥形范围内进行 raycast, 然后对锥形 tile 范围内的建筑回调
     *
     * @param wx 中心 x
     * @param wy 中心 y
     * @param range 最大长度
     * @param angle 中心角度
     * @param cone 半锥角 (总锥宽 = 2*cone)
     * @param consBuilding 命中建筑回调 (building 可能为 null)
     * @param insulator 障碍 tile 谓词 (如 absorbLasers)
     * @param ref 预分配的射线数据数组 (长度 = rays, 存储每个角度的 squared 距离)
     * @return ref 数组本身
     */
    public static float[] castConeTile(float wx, float wy, float range, float angle, float cone,
                                        Cons2<Building, Tile> consBuilding, Boolf<Tile> insulator, float[] ref) {
        collidedBlocks.clear();
        idx = 0;
        float expand = 3;
        rect.setCentered(wx, wy, expand);
        shotgunRange(3, cone, angle, con -> {
            tV.trns(con, range).add(wx, wy);
            rectAlt.setCentered(tV.x, tV.y, expand);
            rect.merge(rectAlt);
        });
        if (insulator != null) {
            shotgunRange(ref.length, cone, angle, con -> {
                tV.trns(con, range).add(wx, wy);
                ref[idx] = range * range;
                world.raycastEachWorld(wx, wy, tV.x, tV.y, (x, y) -> {
                    Tile tile = world.tile(x, y);
                    if (tile != null && insulator.get(tile)) {
                        ref[idx] = Mathf.dst2(wx, wy, x * tilesize, y * tilesize);
                        return true;
                    }
                    return false;
                });
                idx++;
            });
        }
        int tx = Mathf.round(rect.x / tilesize);
        int ty = Mathf.round(rect.y / tilesize);
        int tw = tx + Mathf.round(rect.width / tilesize);
        int th = ty + Mathf.round(rect.height / tilesize);
        for (int x = tx; x <= tw; x++) {
            for (int y = ty; y <= th; y++) {
                float ofX = (x * tilesize) - wx, ofY = (y * tilesize) - wy;
                int angIdx = Mathf.clamp(Mathf.round(((angleDistSigned(Mathf.angle(ofX, ofY), angle) + cone) / (cone * 2f)) * (ref.length - 1)), 0, ref.length - 1);
                float dst = ref[angIdx];
                float dst2 = Mathf.dst2(ofX, ofY);
                if (dst2 < dst && dst2 < range * range && angleDist(Mathf.angle(ofX, ofY), angle) < cone) {
                    Tile tile = world.tile(x, y);
                    Building building = null;
                    if (tile != null) {
                        Building b = world.build(x, y);
                        if (b != null && !collidedBlocks.contains(b.id)) {
                            building = b;
                            collidedBlocks.add(b.id);
                        }
                        consBuilding.get(building, tile);
                    }
                }
            }
        }
        collidedBlocks.clear();
        return ref;
    }

    /**
     * PU132 Utils.seedr: 全局复用的随机数发生器。
     *
     * <p>特效渲染中大量使用确定性随机 (每帧根据种子重放同一随机序列),
     * 复用三个静态实例可避免每帧分配新对象。三个实例分别在不同嵌套层级使用,
     * 防止外层循环的随机状态被内层覆盖。</p>
     */
    public static final Rand seedr = new Rand(), seedr2 = new Rand(), seedr3 = new Rand();

    /** randLenVectors 专用的独立随机源 (对应 PU132 MathU.seedr, 与 seedr 隔离避免状态交叉)。 */
    private static final Rand vecSeedr = new Rand();
    /** randLenVectors 的临时向量 (对应 PU132 MathU.vec)。 */
    private static final Vec2 vec = new Vec2();

    /**
     * PU132 Utils.with: 对对象应用一段配置代码后返回原对象。
     *
     * <p>用于在表达式内部完成 "创建 + 配置", 例如:
     * {@code with(new Trail(...), t -> t.width = 5f)}。</p>
     */
    public static <T> T with(T inst, Cons<T> cons){
        cons.get(inst);
        return inst;
    }

    /**
     * PU132 MathU.slope: 非对称三角波。
     *
     * <p>fin 在 {@code [0, bias]} 区间从 0 线性升至峰值 1,
     * 在 {@code [bias, 1]} 区间从 1 线性降回 0。
     * bias 越小, 上升沿越陡 (特效粒子 "快进慢出" 的核心曲线)。</p>
     */
    public static float slope(float fin, float bias){
        return (fin < bias ? (fin / bias) : 1f - (fin - bias) / (1f - bias));
    }

    /**
     * PU132 MathU.randLenVectors: 带个体生命曲线的辐射粒子生成器。
     *
     * <p>渲染算法 (逐步):</p>
     * <ol>
     *   <li>每个粒子先随机一个 "入场延迟窗口宽度" r ∈ [inRandMin, inRandMax],
     *       再随机窗口内偏移 offset;</li>
     *   <li>用 {@code Mathf.curve(in, offset, (1-r)+offset)} 把特效总进度 in
     *       映射为该粒子的个体进度 fin —— 窗口不同, 粒子先后绽放;</li>
     *   <li>径向距离 f = length(fin) 的返回值 (可任意整形, 如 f³*90),
     *       并按 lengthRand 随机缩放;</li>
     *   <li>角度全随机, 把 (x, y, fin) 回调给消费者绘制。</li>
     * </ol>
     *
     * @param seed       确定性随机种子 (通常为 e.id * 常数)
     * @param amount     粒子数量
     * @param in         特效总进度 (0~1)
     * @param inRandMin  粒子窗口宽度随机下限
     * @param inRandMax  粒子窗口宽度随机上限
     * @param lengthRand 径向距离随机缩放幅度 (<=0 时不缩放)
     * @param length     个体进度 → 径向距离 的映射函数
     * @param cons       粒子消费者 (相对坐标 x, y + 个体进度 fin)
     */
    public static void randLenVectors(long seed, int amount, float in, float inRandMin, float inRandMax,
                                      float lengthRand, FloatFloatf length, UParticleConsumer cons){
        vecSeedr.setSeed(seed);
        for(int i = 0; i < amount; i++){
            float r = vecSeedr.random(inRandMin, inRandMax);
            float offset = r > 0 ? vecSeedr.nextFloat() * r : 0f;

            float fin = Mathf.curve(in, offset, (1f - r) + offset);
            float f = length.get(fin) * (lengthRand <= 0f ? 1f : vecSeedr.random(1f - lengthRand, 1f));
            vec.trns(vecSeedr.random(360f), f);
            cons.get(vec.x, vec.y, fin);
        }
    }

    /** PU132 MathU.FloatFloatf: float → float 函数接口。 */
    public interface FloatFloatf{
        float get(float value);
    }

    /** PU132 MathU.UParticleConsumer: 粒子消费者 (相对坐标 + 个体进度)。 */
    public interface UParticleConsumer{
        void get(float x, float y, float fin);
    }
}
