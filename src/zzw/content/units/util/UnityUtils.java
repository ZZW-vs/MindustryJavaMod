package zzw.content.units.util;

import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Cons2;
import arc.func.Floatc;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.IntSet;
import mindustry.Vars;
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
}
