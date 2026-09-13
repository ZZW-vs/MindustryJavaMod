package zzw.util;

import arc.graphics.g2d.*;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.*;
import arc.func.Boolf;
import arc.func.Boolf2;
import arc.func.Boolf3;
import arc.func.Floatf;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.pooling.Pools;
import arc.util.pooling.Pool.Poolable;
import arc.util.Tmp;
import mindustry.Vars;
import arc.math.geom.Point2;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.game.Team;
import mindustry.gen.Unit;

import java.util.Arrays;

/**
 * 精确线段碰撞扫描 (移植自 PU132 util/Utils 的 collideLineRaw 系列完整版)
 *
 * <p>与 Mindustry 原版 Damage.collideLine 的区别:</p>
 * <p>1. 不自动造成伤害/击退/状态 — 全部通过回调交给调用方 (防作弊子弹自己算伤害);
 * <br>2. 建筑判定基于 world.raycastEachWorld 逐格光线步进 + tileWidth 宽度的
 *     BFS 洪泛扩展 (低血量建筑/吸激光方块等"非阻挡"目标也会被命中,
 *     光束继续延伸, 直到首个"阻挡"目标才截断 — 由回调返回值决定);
 * <br>3. 单位判定用沿线的分段矩形扫描 (rectAlt 前瞻防漏);
 * <br>4. 回调返回 true = 停止延伸 (stopSort=true 时按距离排序, 逐个触发直到
 *     首个 true 为止, 用于"激光停在第一个阻挡者")。</p>
 *
 * <p>本移植把 PU132 散落在 Utils 里的静态临时字段封装为独立类,
 * 对外提供 End 系激光子弹 (奇异点/切割激光等) 需要的三个入口:</p>
 * <ul>
 *   <li>{@link #collideLineRawEnemy(Team, float, float, float, float, float, BuildingCons, UnitCons, PointHandler, boolean)}
 *       — PU132 collideLineRawEnemy(宽度版);</li>
 *   <li>{@link #collideLineRawEnemyRatio(...)} — 带距离衰减 ratio 的版本;</li>
 *   <li>{@link #collideLineRawNew(...)} — 底层完整版。</li>
 * </ul>
 */
public final class LineCollide{
    private LineCollide(){}

    // ===== 临时静态字段 (PU32 Utils 同名, 单线程渲染/更新安全) =====
    private static final Vec2 tV = new Vec2(), tV2 = new Vec2();
    private static final IntSet collidedBlocks = new IntSet(), collidedEntities = new IntSet(204);
    private static final Rect rect = new Rect(), rectAlt = new Rect(), hitRect = new Rect();
    private static final BoolGrid collideLineCollided = new BoolGrid();
    private static final IntSeq lineCast = new IntSeq(), lineCastNext = new IntSeq();
    private static final Seq<Hit> hitEffects = new Seq<>();
    private static boolean hitB;

    /** 命中回调: 返回 true = 光束在此目标处停止延伸 */
    public interface HitHandler{
        boolean get(float x, float y, Healthc ent, boolean direct);
    }

    /** 命中点回调 (PU132 Floatc2): 触发命中特效 */
    public interface PointHandler{
        void get(float x, float y);
    }

    //region 对外入口

    /**
     * 敌方线段碰撞 (PU132 Utils.collideLineRawEnemy 宽度版, L625-627)。
     *
     * @param buildingCons 建筑 (建筑, 直接命中) → 是否阻挡; null = 不判定建筑
     * @param unitCons 单位 → 是否阻挡; null = 不判定单位
     */
    public static void collideLineRawEnemy(Team team, float x, float y, float x2, float y2, float width,
                                           Boolf2<Building, Boolean> buildingCons, Boolf<Unit> unitCons, PointHandler effectHandler, boolean stopSort){
        collideLineRawNew(x, y, x2, y2, width, width,
            build -> build.team != team, unit -> unit.team != team,
            buildingCons != null, unitCons != null,
            healthc -> healthc.dst2(x, y),
            (ex, ey, ent, direct) -> {
                boolean hit = false;
                if(unitCons != null && direct && ent instanceof Unit u){
                    hit = unitCons.get(u);
                }
                if(buildingCons != null && ent instanceof Building b){
                    hit = buildingCons.get(b, direct);
                }
                if(effectHandler != null && direct) effectHandler.get(ex, ey);
                return hit;
            }, stopSort);
    }

    /**
     * 带距离衰减 ratio 的敌方线段碰撞 (PU132 Utils.collideLineRawEnemyRatio L604-615)。
     *
     * <p>ratio = 目标到线段的距离衰减: 紧贴光束 ratio→1, 远离→最低 0.05。
     * 用于穿透计分 (pierceScore) 等场景。</p>
     */
    public static void collideLineRawEnemyRatio(Team team, float x, float y, float x2, float y2, float width,
                                                Boolf3<Building, Float, Boolean> buildingCons, Boolf2<Unit, Float> unitCons, PointHandler effectHandler){
        float minRatio = 0.05f;
        collideLineRawEnemy(team, x, y, x2, y2, width, (building, direct) -> {
            float size = (building.block.size * Vars.tilesize / 2f);
            float ratio = Mathf.clamp(1f - ((Intersector.distanceSegmentPoint(x, y, x2, y2, building.x, building.y) - width) / size), minRatio, 1f);
            return buildingCons.get(building, ratio, direct);
        }, unit -> {
            float size = (unit.hitSize / 2f);
            float ratio = Mathf.clamp(1f - ((Intersector.distanceSegmentPoint(x, y, x2, y2, unit.x, unit.y) - width) / size), minRatio, 1f);
            return unitCons.get(unit, ratio);
        }, effectHandler, true);
    }

    //endregion

    /**
     * 底层完整版 (PU132 Utils.collideLineRawNew L683-812 逐步移植)。
     *
     * <p>步骤:</p>
     * <p>1. 建筑阶段 (hitTile): world.raycastEachWorld 沿线逐格,
     *    每格对 8 邻域做 tileWidth 宽度的洪泛扩展 (lineCast/lineCastNext 队列),
     *    命中的建筑回调; 阻挡 (返回 true) 时把光束末端 tV 截断到该建筑;
     * <br>2. 单位阶段 (hitUnit): 在 (可能已截断的) 线段矩形内扫描敌方单位,
     *    raycastRect 求光线与单位碰撞箱的交点, 命中回调;
     * <br>3. sort 非空时按到起点距离排序, 逐个触发直到首个 true (stopSort)。</p>
     */
    public static void collideLineRawNew(float x, float y, float x2, float y2, float unitWidth, float tileWidth,
                                         Boolf<Building> buildingFilter, Boolf<Unit> unitFilter,
                                         boolean hitTile, boolean hitUnit,
                                         Floatf<Healthc> sort, HitHandler hitHandler, boolean stopSort){
        hitEffects.clear();
        lineCast.clear();
        lineCastNext.clear();
        collidedBlocks.clear();

        tV.set(x2, y2);
        if(hitTile){
            collideLineCollided.updateSize(Vars.world.width(), Vars.world.height());
            collideLineCollided.clear();
            Runnable cast = () -> {
                hitB = false;

                lineCast.each(i -> {
                    int tx = Point2.x(i),
                    ty = Point2.y(i);
                    Building build = Vars.world.build(tx, ty);
                    boolean hit = false;
                    if(build != null && (buildingFilter == null || buildingFilter.get(build)) && collidedBlocks.add(build.pos())){
                        if(sort == null){
                            hit = hitHandler.get(tx * Vars.tilesize, ty * Vars.tilesize, build, true);
                        }else{
                            hit = hitHandler.get(tx * Vars.tilesize, ty * Vars.tilesize, build, false);
                            Hit he = Pools.obtain(Hit.class, Hit::new);
                            he.ent = build;
                            he.x = tx * Vars.tilesize;
                            he.y = ty * Vars.tilesize;

                            hitEffects.add(he);
                        }
                        if(hit && !hitB){
                            tV.trns(Angles.angle(x, y, x2, y2), Mathf.dst(x, y, build.x, build.y)).add(x, y);
                            hitB = true;
                        }
                    }

                    Vec2 segment = Intersector.nearestSegmentPoint(x, y, tV.x, tV.y, tx * Vars.tilesize, ty * Vars.tilesize, tV2);
                    if(!hit && tileWidth > 0f){
                        for(Point2 p : Geometry.d8){
                            int newX = (p.x + tx);
                            int newY = (p.y + ty);
                            boolean within = !hitB || Mathf.within(x / Vars.tilesize, y / Vars.tilesize, newX, newY, tV.dst(x, y) / Vars.tilesize);
                            if(segment.within(newX * Vars.tilesize, newY * Vars.tilesize, tileWidth) && collideLineCollided.within(newX, newY) && !collideLineCollided.get(newX, newY) && within){
                                lineCastNext.add(Point2.pack(newX, newY));
                                collideLineCollided.set(newX, newY, true);
                            }
                        }
                    }
                });
                lineCast.clear();
                lineCast.addAll(lineCastNext);
                lineCastNext.clear();
            };

            Vars.world.raycastEachWorld(x, y, x2, y2, (cx, cy) -> {
                if(collideLineCollided.within(cx, cy) && !collideLineCollided.get(cx, cy)){
                    lineCast.add(Point2.pack(cx, cy));
                    collideLineCollided.set(cx, cy, true);
                }
                cast.run();
                return hitB;
            });

            while(!lineCast.isEmpty()){
                cast.run();
            }
        }
        if(hitUnit){
            rect.setPosition(x, y).setSize(tV.x - x, tV.y - y);

            if(rect.width < 0){
                rect.x += rect.width;
                rect.width *= -1;
            }
            if(rect.height < 0){
                rect.y += rect.height;
                rect.height *= -1;
            }

            rect.grow(unitWidth * 2f);

            Groups.unit.intersect(rect.x, rect.y, rect.width, rect.height, unit -> {
                if(unitFilter == null || unitFilter.get(unit)){
                    unit.hitbox(hitRect);
                    hitRect.grow(unitWidth * 2f);

                    Vec2 vec = Geometry.raycastRect(x, y, tV.x, tV.y, hitRect);

                    if(vec != null){
                        float scl = (unit.hitSize - unitWidth) / unit.hitSize;
                        vec.sub(unit.x, unit.y).scl(scl).add(unit.x, unit.y);
                        if(sort == null){
                            hitHandler.get(vec.x, vec.y, unit, true);
                        }else{
                            Hit he = Pools.obtain(Hit.class, Hit::new);
                            he.ent = unit;
                            he.x = vec.x;
                            he.y = vec.y;
                            hitEffects.add(he);
                        }
                    }
                }
            });
        }
        if(sort != null){
            hitB = false;
            hitEffects.sort(he -> sort.get(he.ent)).each(he -> {
                if(!stopSort || !hitB){
                    hitB = hitHandler.get(he.x, he.y, he.ent, true);
                }
                Pools.free(he);
            });
        }

        hitEffects.clear();
    }

    /** PU132 Hit: 待排序的命中实体 + 命中点 (对象池复用) */
    static class Hit implements Poolable{
        Healthc ent;
        float x, y;

        @Override
        public void reset(){
            ent = null;
            x = y = 0f;
        }
    }

    /** PU132 BoolGrid: 地图尺寸的布尔网格 (洪泛去重) */
    static class BoolGrid{
        boolean[] array;
        int width, height;

        public void updateSize(int newWidth, int newHeight){
            if(newWidth != width || newHeight != height){
                array = new boolean[newWidth * newHeight];
            }
            width = newWidth;
            height = newHeight;
        }

        public void clear(){
            if(array != null) Arrays.fill(array, false);
        }

        public boolean within(int x, int y){
            return x >= 0 && y >= 0 && x < width && y < height;
        }

        public boolean get(int x, int y){
            return array[x + (y * width)];
        }

        public void set(int x, int y, boolean b){
            array[x + (y * width)] = b;
        }
    }
}
