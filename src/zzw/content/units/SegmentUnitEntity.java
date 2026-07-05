package zzw.content.units;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import mindustry.gen.Hitboxc;
import mindustry.gen.UnitEntity;

/**
 * 段身 Entity (借鉴 PU132 WormSegmentUnit)
 *
 * 设计:
 * - 禁用 AI 控制 (段身不需要自己思考)
 * - 禁用自身移动 (段身位置由头部 SegmentWormEntity 控制)
 * - 不参与计数 (isCounted = false)
 * - 死亡时通知头部 (头部会重新分配段身列表)
 *
 * 这样段身就只是个"贴图载体", 不会乱跑
 */
public class SegmentUnitEntity extends UnitEntity {

    /** 工厂方法 (UnitType.constructor 用) */
    public static SegmentUnitEntity create() {
        return new SegmentUnitEntity();
    }

    /** 返回注册的 classId (绕过 v154.3 的 checkEntityMapping 检查) */
    @Override
    public int classId() {
        return ZEntityRegister.classId(SegmentUnitEntity.class);
    }

    /** 引用头部 (用于死亡通知) */
    public SegmentWormEntity head = null;

    /** 段身在头部段身数组中的索引 (0=第一节, count-1=尾节), 用于 z 层级控制 */
    public int segmentIndex = 0;

    /** 是否为尾部 (最后一节段身, 用 tail 贴图而不是 segment 贴图) */
    public boolean isTail = false;

    @Override
    public void update() {
        // ★ 不调用 super.update() 的移动/AI 部分 ★
        // 只保留必要的更新: 状态效果、武器、血量

        // 更新状态效果时间 (让 buff 仍然生效)
        if (statuses.size > 0) {
            statuses.each(s -> s.time = Math.max(s.time - arc.util.Time.delta, 0f));
        }

        // hitTime 衰减 (受伤闪烁)
        hitTime = Math.max(0f, hitTime - arc.util.Time.delta / 10f);

        // 不调用 controller.updateUnit() - 段身不需要 AI
        // 不调用 moveAt - 段身位置由头部设置
        // 不应用 drag - 头部直接 set 位置

        // ★ 状态效果转移给头部 (借鉴 PU132 WormSegmentUnit.updateStatus L261-265)
        // 段身被施加 buff 时, 把 buff 转移到头部, 段身本身不持有
        updateStatus();

        // 检查头部是否还活着
        if (head == null || head.dead || !head.isAdded()) {
            // 头部死了, 段身也跟着死
            health = 0f;
            dead = true;
        }
    }

    /**
     * 段身状态效果转移给头部 (借鉴 PU132 WormSegmentUnit.updateStatus L261-265)
     * 段身不持有 buff, 被施加的状态全部转给头部
     */
    protected void updateStatus() {
        if (head == null || head.dead || !head.isAdded()) return;
        if (!statuses.isEmpty()) {
            statuses.each(s -> head.apply(s.effect, s.time));
            statuses.clear();
        }
    }

    /** 段身不参与存档 (借鉴 PU132 WormSegmentUnit.serialize L176-178) */
    @Override
    public boolean serialize() {
        return false;
    }

    @Override
    public void kill() {
        if (dead) return;
        dead = true;
        // 通知头部: 我死了, 请重新分配段身列表
        if (head != null && head.isAdded()) {
            head.onSegmentDied(this);
        }
        remove();
    }

    // isCounted 在 v150.1 中不存在, 移除 (改用 hidden=true 隐藏段身)

    @Override
    public boolean isAI() {
        // 段身不算 AI 单位
        return false;
    }

    @Override
    public boolean isPlayer() {
        // 段身永远不是玩家
        return false;
    }

    // 注意: 不重写 controller(UnitController), 让父类正常初始化 controller
    // 否则 controller 为 null, remove() 调用 controller.removed() 会 NPE
    // 段身不需要 AI: 我们在 update() 中不调用 super.update() 的 AI 部分

    /**
     * 让头部直接设置位置和朝向 (跳过物理)
     * 由 SegmentWormEntity.update() 每帧调用
     */
    public void syncToHead(float x, float y, float rotation) {
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        // 重置速度 (避免段身被推开)
        this.vel.setZero();
    }

    @Override
    public void add() {
        super.add();
        try {
            arc.util.Log.info("[arcnelidia-seg] segment added at " + x + "," + y + " head=" + (head == null ? "null" : "ok") + " isTail=" + isTail);
        } catch (Throwable ignored) {}
    }

    /**
     * 段身自己绘制: 临时切换 type 的 region/outlineRegion/cellRegion, 然后调用 super.draw()
     *
     * 借鉴 PU132 UnityUnitType.draw 第342-347行:
     * - 保存 type 原本的 region/outlineRegion/cellRegion
     * - 临时切换为 segment/tail 贴图
     * - 调用 super.draw() (会用切换后的 region 画)
     * - 恢复
     *
     * z 层级控制 (借鉴 PU132 UnityUnitType.drawBody 第669行):
     * - 段身 0 z = 头部z - 1/10000 (头部覆盖第1节)
     * - 段身 1 z = 头部z - 2/10000 (第1节覆盖第2节)
     * - ...
     * - 段身 n-1 z = 头部z - n/10000 (最低)
     */
    @Override
    public void draw() {
        float z = Draw.z();
        // 每段降低 z (segmentIndex+1)/10000, 头部 z 最高, 段身递减
        // 这样头部覆盖第1节, 第1节覆盖第2节, ...
        Draw.z(z - (segmentIndex + 1f) / 10000f);

        // 调试: 只打一次, 确认 atlas 里贴图的实际名字 (运行时 atlas 已加载)
        if (!debugDrawLogged) {
            debugDrawLogged = true;
            String[] names = {
                "arcnelidia-segment", "create-arcnelidia-segment",
                "arcnelidia-tail", "create-arcnelidia-tail",
                "arcnelidia-segment-outline", "create-arcnelidia-segment-outline",
                "arcnelidia-tail-outline", "create-arcnelidia-tail-outline",
                "arcnelidia-cell", "create-arcnelidia-cell",
                "arcnelidia", "create-arcnelidia"
            };
            for (String n : names) {
                try {
                    System.out.println("[ARCNELIDIA-DEBUG] draw atlas.find('" + n + "') = "
                        + (arc.Core.atlas.find(n).found() ? "OK" : "MISSING"));
                } catch (Throwable t) {
                    System.out.println("[ARCNELIDIA-DEBUG] draw atlas.find('" + n + "') ERR: " + t);
                }
            }
        }

        // 保存 type 原本的 region 字段
        mindustry.type.UnitType t = type;
        TextureRegion oldRegion = t.region;
        TextureRegion oldOutline = t.outlineRegion;
        TextureRegion oldCell = t.cellRegion;

        // 临时切换 region (借鉴 PU132 UnityUnitType.draw 第344-347行)
        // 尝试两种名字: 不带前缀 / 带 mod 前缀 (Mindustry 会给 mod 贴图加 modname- 前缀)
        if (isTail) {
            t.region = findRegion("arcnelidia-tail", "create-arcnelidia-tail");
            t.outlineRegion = findRegion("arcnelidia-tail-outline", "create-arcnelidia-tail-outline");
        } else {
            t.region = findRegion("arcnelidia-segment", "create-arcnelidia-segment");
            t.outlineRegion = findRegion("arcnelidia-segment-outline", "create-arcnelidia-segment-outline");
            // 非尾段身用头部的 cell 贴图
            t.cellRegion = findRegion("arcnelidia-cell", "create-arcnelidia-cell");
        }

        // 调用 super.draw() 会用切换后的 region 画
        super.draw();

        // 恢复 type 的 region 字段 (避免影响其他段身或头部)
        t.region = oldRegion;
        t.outlineRegion = oldOutline;
        t.cellRegion = oldCell;

        // 恢复 z
        Draw.z(z);
    }

    /** 调试标志 */
    private static boolean debugDrawLogged = false;

    /**
     * 查找贴图: 先试 name, 找不到再试 prefixedName (带 mod 前缀)
     * Mindustry 给 mod 贴图加 modname- 前缀, 但 UnitType.load 用不带前缀的名字查找
     */
    private static TextureRegion findRegion(String name, String prefixedName) {
        TextureRegion r = arc.Core.atlas.find(name);
        if (r.found()) return r;
        return arc.Core.atlas.find(prefixedName);
    }

    /**
     * 段身之间的碰撞过滤:
     * - 段身 vs 同头部的其他段身/头部 → 不碰撞 (避免互相推开抖动)
     * - 段身 vs 其他单位 → 正常碰撞 (其他单位不能穿过段身)
     *
     * 借鉴 PU132 WormSegmentUnit.collides (第45-52行)
     */
    @Override
    public boolean collides(Hitboxc other) {
        // 段身 vs 自己的头部 → 不碰撞
        if (other == head) return false;
        // 段身 vs 同头部的其他段身 → 不碰撞 (避免互相推开抖动)
        if (head != null && other instanceof SegmentUnitEntity) {
            SegmentUnitEntity o = (SegmentUnitEntity) other;
            if (o.head == head) return false;
        }
        // 段身 vs 其他单位 → 正常碰撞 (其他单位不能穿过段身)
        return super.collides(other);
    }
}
