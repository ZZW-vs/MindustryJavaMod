package zzw.content.units;

import arc.func.Floatp;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.gen.Drawc;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;
import mindustry.gen.Posc;
import mindustry.gen.Rotc;
import mindustry.graphics.Layer;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.content.Blocks;
import mindustry.Vars;

/**
 * PU132 SlowLightning 实体移植版 (适配 v150.1)
 * - 实现 Drawc 接口, 通过 Groups.draw 管理绘制
 * - 节点树管理 (splitChance 分裂, maxNodes 上限)
 * - 旋转速度按距离插值 (minRotationSpeed ~ maxRotationSpeed)
 * 参考: PU132 unity.gen.SlowLightning (注解生成) + unity.entities.comp.SlowLightningComp
 */
public class SlowLightningEntity implements Drawc {
    public static final Vec2 tv = new Vec2();
    public static boolean collided = false;

    public float rotation;
    @Nullable public Posc parent;
    public boolean rotWithParent;
    public float offsetX, offsetY, offsetPos, offsetRot;

    private transient boolean added = false;
    // v150.1: EntityGroup.nextId() 不存在, 用 AtomicInteger 生成唯一 id
    private static final java.util.concurrent.atomic.AtomicInteger ID_GEN = new java.util.concurrent.atomic.AtomicInteger(1);
    public transient int id = ID_GEN.getAndIncrement();
    public Team team = Team.derelict;
    public Position target;
    public Bullet bullet;
    public Floatp liveDamage;
    public SlowLightningType type;
    public Seq<SlowLightningType.SlowLightningNode> nodes = new Seq<>(SlowLightningType.SlowLightningNode.class);
    public int layer = 0, seed = 1, bulletId = -1;
    public float time, distance, timer;
    public float lastX, lastY;
    public boolean ended = false, passed = false;

    public float x, y;

    protected SlowLightningEntity() {}

    public static SlowLightningEntity create() {
        return new SlowLightningEntity();
    }

    @Override
    public int classId() {
        return ZEntityRegister.classId(SlowLightningEntity.class);
    }

    @Override
    public String toString() {
        return "SlowLightningEntity#" + id;
    }

    @Override
    public float getX() { return x; }

    @Override
    public float getY() { return y; }

    @Override
    public void draw() {
        float fin = Math.min(type.lifetime - time, type.fadeTime) / type.fadeTime;
        float z = Draw.z();
        // ★ 渲染在飞行单位之上 (用户要求子弹/激光显示在最上层)
        // PU132 原版用 Layer.effect (110), 但会被 flyingUnit (115) 覆盖
        // 改为 Layer.flyingUnit + 1f (116), 确保显示在单位上方
        Draw.z(Layer.flyingUnit + 1f);
        Lines.stroke(type.lineWidth * fin);
        for (SlowLightningType.SlowLightningNode n : nodes) {
            n.draw();
        }
        Draw.reset();
        Draw.z(z);
    }

    public void updateLastPosition() {
        lastX = x;
        lastY = y;
    }

    @Override
    public void update() {
        updateLastPosition();
        // child: parent 跟随
        if (parent != null) {
            if (rotWithParent && parent instanceof Rotc r) {
                x = parent.getX() + Angles.trnsx(r.rotation() + offsetPos, offsetX, offsetY);
                y = parent.getY() + Angles.trnsy(r.rotation() + offsetPos, offsetX, offsetY);
                rotation = r.rotation() + offsetRot;
            } else {
                x = parent.getX() + offsetX;
                y = parent.getY() + offsetY;
            }
        }
        // slowlightning: 核心更新逻辑 (PU132 SlowLightningComp.update)
        if (parent != null) {
            float dx = x - lastX, dy = y - lastY;
            for (SlowLightningType.SlowLightningNode n : nodes) {
                n.move(layer, dx, dy);
            }
        }
        if (bullet != null && bullet.id != bulletId) {
            bullet = null;
        }
        if (type.continuous && (timer += Time.delta) >= 5f) {
            for (SlowLightningType.SlowLightningNode n : nodes) {
                n.collide();
            }
            timer = 0f;
        }
        for (int i = 0; i < nodes.size; i++) {
            nodes.items[i].update();
        }
        if (time >= type.lifetime) {
            remove();
        }
        time += Time.delta;
    }

    public boolean isNull() { return false; }

    public mindustry.world.blocks.environment.Floor floorOn() {
        Tile tile = tileOn();
        return tile == null || tile.block() != Blocks.air ? (Floor) Blocks.air : tile.floor();
    }

    public Block blockOn() {
        Tile tile = tileOn();
        return tile == null ? Blocks.air : tile.block();
    }

    /** v150.1 Posc 需要 buildOn() */
    public mindustry.gen.Building buildOn() {
        Tile tile = tileOn();
        return tile == null ? null : tile.build;
    }

    public boolean isRemote() {
        return false;
    }

    @Override
    public void set(Position pos) { set(pos.getX(), pos.getY()); }

    @Override
    public void afterRead() {}

    /** v150.1 Entityc 需要 afterReadAll() */
    @Override
    public void afterReadAll() {}

    /** v150.1 Entityc 需要 beforeWrite() */
    @Override
    public void beforeWrite() {}

    @Override
    public void write(arc.util.io.Writes write) {}

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entityc> T self() { return (T) this; }

    @Override
    public void read(arc.util.io.Reads read) { afterRead(); }

    @Override
    public void trns(float x, float y) { set(this.x + x, this.y + y); }

    public float nextRange(float range) {
        float r = Mathf.randomSeed(seed, -range, range);
        seed = Mathf.randomSeed(seed, 63, 2147483647);
        return r;
    }

    public boolean nextBoolean(float chance) {
        boolean b = Mathf.randomSeed(seed, 1f) < chance;
        seed = Mathf.randomSeed(seed, 63, 2147483647);
        return b;
    }

    public float nextRand() {
        float r = Mathf.randomSeed(seed, 1f);
        seed = Mathf.randomSeed(seed, 63, 2147483647);
        return r;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() { return (T) this; }

    @Override
    public void add() {
        if (added) return;
        Groups.all.add(this);
        Groups.draw.add(this);
        // child: 计算 offset
        if (parent != null) {
            offsetX = x - parent.getX();
            offsetY = y - parent.getY();
            if (rotWithParent && parent instanceof Rotc r) {
                offsetPos = -r.rotation();
                offsetRot = rotation - r.rotation();
            }
        }
        added = true;
        // slowlightning: 初始化
        lastX = x;
        lastY = y;
        if (bullet != null) {
            bulletId = bullet.id;
        }
        end(null);
    }

    @Override
    public void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int tileX() { return mindustry.core.World.toTile(x); }

    @Override
    public int tileY() { return mindustry.core.World.toTile(y); }

    @Override
    public Tile tileOn() { return Vars.world.tileWorld(x, y); }

    @Override
    public void remove() {
        if (!added) return;
        Groups.all.remove(this);
        Groups.draw.remove(this);
        added = false;
        // slowlightning: 释放节点
        for (SlowLightningType.SlowLightningNode n : nodes) {
            SlowLightningType.nodes.free(n);
        }
        nodes.clear();
    }

    @Override
    public float clipSize() { return Float.MAX_VALUE; }

    @Override
    public void trns(Position pos) { trns(pos.getX(), pos.getY()); }

    @Override
    public boolean isAdded() { return added; }

    @Override
    public boolean onSolid() {
        Tile tile = tileOn();
        return tile == null || tile.solid();
    }

    /** PU132 SlowLightningComp.end: 创建新节点 (含分裂逻辑) */
    public void end(SlowLightningType.SlowLightningNode node) {
        boolean split = nextBoolean(type.splitChance);
        for (int i = 0; i < (split ? 2 : 1); i++) {
            float r = nextRange(split ? type.splitRandSpacing : type.randSpacing);
            float tr = node != null ? node.rotation + node.rotRand : rotation;
            if (target != null) {
                float scl = 1f - Mathf.clamp(dst(target) / type.rotationDistance);
                tr = Angles.moveToward(tr, angleTo(target), ((type.maxRotationSpeed - type.minRotationSpeed) * scl) + type.minRotationSpeed);
            }
            float rr = tr + r;

            collided = false;
            float nl = type.nodeLength;
            Vec2 v2 = Tmp.v2.set(node == null ? this : node);
            Vec2 v = Tmp.v1.trns(rr, Math.min(type.nodeLength, type.range - nl)).add(v2);
            float l = SlowLightningUtils.findLaserLength(v2.x, v2.y, v.x, v.y, tile -> {
                collided |= (tile.team() != team && tile.block() != null && tile.block().absorbLasers);
                return collided;
            });
            if (l < type.nodeTime) {
                v.sub(v2).scl(l / type.nodeLength).add(v2);
            }

            SlowLightningType.SlowLightningNode n = SlowLightningType.nodes.obtain();
            n.main = this;
            n.parent = node;
            n.rotation = rr;
            n.x = v.x;
            n.y = v.y;
            if (node != null) {
                n.rotRand = -node.rotRand + (-r + node.rotRand) * nextRand();
                n.layer = node.layer + 1;
                n.dist = node.dist + l;
            } else {
                n.rotRand = -r;
                n.layer = layer + 1;
                n.dist = l;
            }
            n.ended = collided || n.dist >= type.range;
            distance = Math.max(distance, n.dist);
            layer = Math.max(layer, n.layer);
            nodes.add(n);
        }
    }

    /** 距离计算 */
    public float dst(Position pos) {
        return dst(pos.getX(), pos.getY());
    }

    public float dst(float ox, float oy) {
        return Mathf.dst(x, y, ox, oy);
    }

    /** 角度计算 */
    public float angleTo(Position pos) {
        return Angles.angle(x, y, pos.getX(), pos.getY());
    }

    @Override
    public int id() { return id; }

    @Override
    public void id(int id) { this.id = id; }

    @Override
    public boolean serialize() { return false; }

    @Override
    public boolean isLocal() { return false; }

    // ===== 以下为 Childc/Rotc/Teamc 接口方法 (v150.1 需要) =====

    public Posc parent() { return parent; }
    public void parent(Posc p) { this.parent = p; }
    public boolean rotWithParent() { return rotWithParent; }
    public void rotWithParent(boolean v) { this.rotWithParent = v; }
    public float offsetX() { return offsetX; }
    public void offsetX(float v) { this.offsetX = v; }
    public float offsetY() { return offsetY; }
    public void offsetY(float v) { this.offsetY = v; }
    public float offsetPos() { return offsetPos; }
    public void offsetPos(float v) { this.offsetPos = v; }
    public float offsetRot() { return offsetRot; }
    public void offsetRot(float v) { this.offsetRot = v; }
    public float rotation() { return rotation; }
    public void rotation(float r) { this.rotation = r; }
    public float x() { return x; }
    public void x(float v) { this.x = v; }
    public float y() { return y; }
    public void y(float v) { this.y = v; }
    public Team team() { return team; }
    public void team(Team t) { this.team = t; }

    /** 静态注册方法 (在 Z_Units.load 中调用) */
    public static void register() {
        ZEntityRegister.register(SlowLightningEntity.class, SlowLightningEntity::create);
    }
}
