package zzw.content.optics;

import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Cons2;
import arc.func.Longf;
import arc.graphics.Color;
import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.QuadTree;

import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.ObjectFloatMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import zzw.content.util.AtomicPair;
import zzw.content.util.Float2;
import zzw.content.util.SColor;
import zzw.content.util.SVec2;
import arc.util.Tmp;
import mindustry.core.World;
import mindustry.graphics.Layer;
import mindustry.world.Tile;

import static mindustry.Vars.*;

/**
 * 光照实体 (PU132 unity.entities.comp.LightComp 移植)
 *
 * <p>光传播算法 (与 PU132 原版一致):
 * <ol>
 *   <li>snap(): 同步输入 (queueStrength/queueRotation/queuePosition/queueSource)</li>
 *   <li>cast(): 沿 rotation 射线检测 (world.raycast), 距离 = strength * yield;
 *       命中 LightHoldBuild → acceptLight 判定 → queuePoint 注册并 interact</li>
 *   <li>interact 中持有者可注册 child (反射镜: 反射向量变换), cast 末尾为
 *       child 建实体/合并已有光 (QuadTree 终点查重, 父子强度倍率传递)</li>
 *   <li>draw(): 加色混合的渐隐光束 quad</li>
 * </ol></p>
 *
 * <p>★ PU132 用实体池+异步线程; 本移植为普通对象 + 同步 cast
 * (LightProcess.update 驱动), parents/children 仍保留线程安全同步块
 * (结构照搬原版, 虽然单线程下非必需)。</p>
 */
public class Light implements QuadTree.QuadTreeObject {
    /** 光传播距离系数: strength=1 传 50 格 */
    public static final float yield = 50f * tilesize;
    /** 光束宽度 */
    public static final float width = 1.5f;
    /** 旋转角步进 (22.5°, 16 方向) */
    public static final float rotationInc = 22.5f;

    /** 全部活跃光 (对象池替代品: 复用 removed 的实例) */
    static final Seq<Light> active = new Seq<>();
    static final Seq<Light> pool = new Seq<>();

    // ===== cast 输入 (queue 前缀) 与快照 (cast 用) =====
    public float x, y;
    public volatile float endX, endY;

    public volatile float strength = 0f;
    public volatile float queueStrength = 0f;

    public volatile float rotation = 0f;
    public volatile float queueRotation = 0f;
    public volatile long queuePosition = 0;

    public volatile LightHoldBlock.LightHoldBuild source = null;
    public volatile LightHoldBlock.LightHoldBuild queueSource = null;

    public volatile int color = Color.whiteRgba;
    public volatile int queueColor = SColor.a(Color.whiteRgba, 0f);

    public volatile boolean casted = false;
    public volatile boolean valid = false;
    public volatile boolean removed = true;

    public volatile LightHoldBlock.LightHoldBuild pointed;
    public volatile boolean rotationChanged = false;

    /** 父光 → 强度倍率 */
    private final ObjectFloatMap<Light> parents = new ObjectFloatMap<>(2);
    /** child 规划键 (rotation+strength 打包) → (直接child, 间接child) */
    private final ObjectMap<Longf<Light>, AtomicPair<Light, Light>> children = new ObjectMap<>(2);

    private static final Color tmpCol = new Color();

    /** 从池创建 */
    public static Light create() {
        Light l = pool.isEmpty() ? new Light() : pool.pop();
        l.reset0();
        return l;
    }

    private void reset0() {
        x = y = endX = endY = 0f;
        strength = queueStrength = rotation = queueRotation = 0f;
        queuePosition = 0;
        source = queueSource = null;
        pointed = null;
        color = Color.whiteRgba;
        queueColor = SColor.a(Color.whiteRgba, 0f);
        casted = false;
        valid = false;
        removed = false;
        rotationChanged = false;
        parents.clear();
        children.clear();
        active.add(this);
    }

    /** cast 前同步快照 (PU132 snap) */
    void snap() {
        strength = queueStrength + recStrength();
        source = queueSource;
        color = combinedCol(queueColor);

        float rot = fixRot(queueRotation);
        if (!Mathf.equal(rotation, rot)) rotationChanged = true;
        rotation = rot;

        x = SVec2.x(queuePosition);
        y = SVec2.y(queuePosition);
    }

    /** 光传播 (PU132 cast, 同步执行) */
    void cast() {
        clearInvalid();

        // 无光源且无父光 → 移除
        if ((source == null || !source.isValid()) && parentsAny(p -> p.size <= 0)) {
            queueRemove();
            return;
        }

        float
            targetX = x + Angles.trnsx(rotation, strength * yield),
            targetY = y + Angles.trnsy(rotation, strength * yield);

        boolean hit = world.raycast(World.toTile(x), World.toTile(y), World.toTile(targetX), World.toTile(targetY), (tx, ty) -> {
            Tile tile = world.tile(tx, ty);
            if (tile == null) { // 出界
                LightProcess.lights.queuePoint(this, null);
                endX = tx * tilesize;
                endY = ty * tilesize;
                return true;
            }

            if (tile.build instanceof LightHoldBlock.LightHoldBuild hold) {
                // 光源自身或父光指向的建筑: 穿过
                if (hold == source || parentsAny(parents -> {
                    for (var e : parents.entries()) {
                        if (hold == e.key.pointed) return true;
                    }
                    return false;
                })) return false;

                // 父光的源头建筑: 停止但不处理
                if (parentsAny(parents -> {
                    for (var e : parents.entries()) {
                        Light l = e.key;
                        if (l.parentsAny(p -> {
                            for (var f : p.entries()) {
                                if (hold == f.key.pointed) return true;
                            }
                            return false;
                        })) return true;
                    }
                    return false;
                })) {
                    LightProcess.lights.queuePoint(this, null);
                    endX = tx * tilesize;
                    endY = ty * tilesize;
                    return true;
                }

                // 持有者接受光 → 注册交互; 或实心方块 → 停止
                if (hold.acceptLight(this, tx, ty)) {
                    LightProcess.lights.queuePoint(this, hold);
                    endX = tile.worldx();
                    endY = tile.worldy();
                    return true;
                } else if (tile.solid()) {
                    LightProcess.lights.queuePoint(this, null);
                    endX = tile.worldx();
                    endY = tile.worldy();
                    return true;
                }
            } else if (tile.solid()) {
                LightProcess.lights.queuePoint(this, null);
                endX = tile.worldx();
                endY = tile.worldy();
                return true;
            }

            return false;
        });

        if (!hit) {
            endX = Mathf.round(targetX / tilesize) * tilesize;
            endY = Mathf.round(targetY / tilesize) * tilesize;
        }

        Tile tile = world.tileWorld(endX, endY);
        if (tile != null) {
            // 终点查重: 已有同角度光 → 收为间接 child (合并), 否则池化新直接 child
            children(children -> {
                for (var e : children.entries()) {
                    Longf<Light> key = e.key;
                    AtomicPair<Light, Light> pair = e.value;
                    long res = key.get(this);

                    float rot = Float2.x(res);

                    LightProcess.lights.quad(quad -> quad.intersect(tile.worldx() - tilesize / 2f, tile.worldy() - tilesize / 2f, tilesize, tilesize, l -> {
                        if (l.valid && pair.key != l && pair.value != l && !isParent(l) && Angles.near(rot, l.rotation, 1f)) {
                            if (pair.key != null) {
                                pair.key.queueRemove();
                                pair.key = null;
                            }

                            if (pair.value != null) pair.value.detachParent(this);
                            pair.value = l;
                            pair.value.parent(this, Float2.y(res));
                        }
                    }));

                    if (pair.key == null && (pair.value == null || !Angles.near(rot, pair.value.rotation, 1f))) {
                        if (pair.value != null) {
                            pair.value.detachParent(this);
                            pair.value = null;
                        }

                        Light l = Light.create();
                        l.set(endX, endY);
                        l.parent(this, Float2.y(res));
                        l.queueAdd();

                        pair.key = l;
                    }
                }
            });
        }

        // 为直接 child 赋 queue 值
        children(children -> {
            for (var e : children.entries()) {
                Light l = e.value.key;
                if (l != null) {
                    l.queuePosition = SVec2.construct(endX, endY);

                    long res = e.key.get(this);
                    l.queueRotation = Float2.x(res);
                    l.parent(this, Float2.y(res));
                }
            }
        });

        casted = true;
        valid = true;
    }

    float recStrength() {
        float str = 0f;
        synchronized (parents) {
            for (var p : parents.entries()) {
                str += p.key.endStrength() * p.value;
            }
        }
        return str;
    }

    int combinedCol(int baseCol) {
        synchronized (tmpCol) {
            tmpCol.set(1f, 1f, 1f, 1f);
            synchronized (parents) {
                for (var e : parents.entries()) {
                    int col = e.key.color;
                    tmpCol.r += SColor.r(col);
                    tmpCol.g += SColor.g(col);
                    tmpCol.b += SColor.b(col);
                }

                int size = parents.size;
                if (size > 0) {
                    tmpCol.r /= size;
                    tmpCol.g /= size;
                    tmpCol.b /= size;
                }

                tmpCol.lerp(
                    SColor.r(baseCol), SColor.g(baseCol), SColor.b(baseCol), 1f,
                    SColor.a(baseCol) / Math.min(size + 1f, 2f)
                );
            }

            return tmpCol.rgba();
        }
    }

    public float endStrength() {
        return Math.max(strength - Mathf.dst(x, y, endX, endY) / yield, 0f);
    }

    public void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    // ===== QuadTreeObject =====

    @Override
    public void hitbox(Rect out) {
        out.set(x, y, 0f, 0f);
    }

    public void add() {
        LightProcess.lights.quad(quad -> quad.insert(this));
    }

    public void queueAdd() {
        LightProcess.lights.queueAdd(this);
    }

    public void remove() {
        LightProcess.lights.quad(quad -> quad.remove(this));
        removed = true;
        active.remove(this, true);
        if (pool.size < 512) pool.add(this);
    }

    public void queueRemove() {
        valid = false;
        clearParents();
        clearChildren();
        LightProcess.lights.queueRemove(this);
    }

    // ===== 绘制 (PU132 draw: 加色渐隐光束) =====

    public void draw() {
        if (!valid) return;

        float z = Draw.z();
        Draw.z(Layer.blockOver);
        Draw.blend(Blending.additive);

        float
            stroke = width / 2f,
            rot = Angles.angle(x, y, endX, endY),
            op = strength - 1f,
            dst2 = Mathf.dst2(x, y, endX, endY),

            startc = Tmp.c1.set(color).a(Mathf.clamp(strength)).toFloatBits(),
            endc = Tmp.c1.set(color).a(Mathf.clamp(endStrength())).toFloatBits();

        if (op > 0f) {
            Tmp.v1.trns(rot, op * yield).limit2(dst2).add(x, y);
            float
                x2 = Tmp.v1.x,
                y2 = Tmp.v1.y;

            float
                len = Mathf.len(x2 - x, y2 - y),
                diffx = (x2 - x) / len * stroke,
                diffy = (y2 - y) / len * stroke;

            Fill.quad(
                x - diffx - diffy, y - diffy + diffx, startc,
                x - diffx + diffy, y - diffy - diffx, startc,
                x2 + diffx + diffy, y2 + diffy - diffx, startc,
                x2 + diffx - diffy, y2 + diffy + diffx, startc
            );
        }

        Tmp.v1.trns(rot, Math.max(op, 0f) * yield).limit2(dst2).add(x, y);
        if (!Mathf.zero(Tmp.v1.len2())) {
            float
                x2 = Tmp.v1.x,
                y2 = Tmp.v1.y,

                len = Mathf.len(endX - x2, endY - y2),
                diffx = (endX - x2) / len * stroke,
                diffy = (endY - y2) / len * stroke;

            Fill.quad(
                x2 - diffx - diffy, y2 - diffy + diffx, startc,
                x2 - diffx + diffy, y2 - diffy - diffx, startc,
                endX + diffx + diffy, endY + diffy - diffx, endc,
                endX + diffx - diffy, endY + diffy + diffx, endc
            );
        }

        Draw.blend();
        Draw.z(z);
    }

    // ===== 父子关系管理 (PU132 原版逻辑) =====

    void children(Cons<ObjectMap<Longf<Light>, AtomicPair<Light, Light>>> cons) {
        synchronized (children) {
            cons.get(children);
        }
    }

    void parents(Cons<ObjectFloatMap<Light>> cons) {
        synchronized (parents) {
            cons.get(parents);
        }
    }

    boolean parentsAny(Boolf<ObjectFloatMap<Light>> cons) {
        synchronized (parents) {
            return cons.get(parents);
        }
    }

    void clearChildren() {
        children(children -> {
            for (var e : children.entries()) {
                AtomicPair<Light, Light> pair = e.value;
                if (pair.key != null) {
                    pair.key.queueRemove();
                    pair.key = null;
                }
                if (pair.value != null) {
                    pair.value.detachParent(this);
                    pair.value = null;
                }
            }
            children.clear();
        });
    }

    void clearParents() {
        parents(parents -> {
            for (var l : parents.entries()) {
                l.key.detachChild(this);
            }
            parents.clear();
        });
    }

    void clearInvalid() {
        parents(parents -> {
            var it = parents.entries();
            while (it.hasNext) {
                Light l = it.next().key;
                if (l != null && ((l.casted && !l.valid) || !(Mathf.equal(x, l.endX) && Mathf.equal(y, l.endY)))) {
                    l.detachChild(this);
                    it.remove();
                }
            }
        });

        children(children -> {
            for (var e : children.entries()) {
                AtomicPair<Light, Light> pair = e.value;
                if (pair.key != null && pair.key.casted && !pair.key.valid) {
                    pair.key.detachParent(this);
                    pair.key = null;
                }
                if (pair.value != null && pair.value.casted && !pair.value.valid) {
                    pair.value.detachParent(this);
                    pair.value = null;
                }
            }
        });
    }

    boolean isParent(Light light) {
        return parentsAny(parents -> parents.containsKey(light));
    }

    void parent(Light light, float mult) {
        parents(parents -> parents.put(light, mult));
    }

    /** 注册一个 child 规划 (持有者 interact 调用); key 返回 (rotation, strengthMult) 打包 */
    public void child(Longf<Light> child) {
        children(children -> children.get(child, AtomicPair::new).reset());
    }

    void detachChild(Light light) {
        children(children -> {
            for (var e : children.entries()) {
                AtomicPair<Light, Light> pair = e.value;
                if (pair.key == light) pair.key = null;
                if (pair.value == light) pair.value = null;
            }
        });
    }

    void detachParent(Light light) {
        parents(parents -> parents.remove(light, 0f));
    }

    /** 角度修正到 22.5° 步进 */
    public static float fixRot(float rotation) {
        return Mathf.mod(Mathf.round(rotation / rotationInc) * rotationInc, 360f);
    }
}