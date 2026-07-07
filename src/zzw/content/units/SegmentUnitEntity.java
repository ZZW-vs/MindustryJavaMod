package zzw.content.units;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.layout.Table;
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

    /** 贴图前缀 (由头部 createSegments 时设置, 等于头部 type.name + "-")
     *  arcnelidia 头部 → "arcnelidia-"
     *  toxobyte 头部 → "toxobyte-" */
    public String texturePrefix = "arcnelidia-";

    /** 调试标志: update 状态只打印一次 */
    private static boolean debugUpdateLogged = false;

    @Override
    public void update() {
        // ★ 不调用 super.update() 的移动/AI 部分 ★
        // 只保留必要的更新: 状态效果、武器、血量

        // ★ 关键: 段身 vel 必须每帧清零, 否则 physics=true 会让段身推动头部移动
        // (arcnelidia 段身没武器, 之前不进武器循环导致 vel 残留, 推动头部"待机自己向前走")
        vel.setZero();

        // 调试: 只打一次, 确认段身 update 在跑
        if (!debugUpdateLogged) {
            debugUpdateLogged = true;
            System.out.println("[段身] 启动: #" + segmentIndex
                + " 头部=" + (head == null ? "无" : "正常")
                + " 武器=" + (mounts == null ? 0 : mounts.length));
        }

        // 更新状态效果时间 (让 buff 仍然生效)
        if (statuses.size > 0) {
            statuses.each(s -> s.time = Math.max(s.time - arc.util.Time.delta, 0f));
        }

        // hitTime 衰减 (受伤闪烁)
        hitTime = Math.max(0f, hitTime - arc.util.Time.delta / 10f);

        // ★ 状态效果转移给头部 (借鉴 PU132 WormSegmentUnit.updateStatus L261-265)
        updateStatus();

        // ★ 段身武器 + 同步头部状态 (借鉴 PU132 WormSegmentUnit.wormSegmentUpdate L192-214)
        if (head != null && head.isAdded() && !head.dead) {
            // 同步头部 hitTime/ammo
            hitTime = head.hitTime;
            ammo = head.ammo;

            // 段身武器: 跟随头部 aim 开火
            if (mounts != null && mounts.length > 0 && head.mounts != null && head.mounts.length > 0) {
                float aimX = head.mounts[0].aimX;
                float aimY = head.mounts[0].aimY;
                boolean headShooting = head.isShooting;
                for (int i = 0; i < mounts.length; i++) {
                    mounts[i].aimX = aimX;
                    mounts[i].aimY = aimY;
                    // ★ 关键: 跟随头部开火状态, 头部不射击时段身也不射击
                    mounts[i].shoot = headShooting;
                    mounts[i].rotate = headShooting;
                    // v154.3 Weapon.update(Unit, WeaponMount) 是 public, 可直接调用
                    mounts[i].weapon.update(this, mounts[i]);
                }
            }
        }

        // ★ 非分裂模式: 段身血量同步为头部血量 (参考 PU132 WormComp.update L249-250)
        // 这样鼠标悬停任意段身时, 显示的血量都与头部相同, 所有节段"共用一个血条"
        if (head != null && head.isAdded() && !head.splittable) {
            health = head.health;
            maxHealth = head.maxHealth;
        }

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

    /**
     * ★ 分裂模式 (splittable=true): 段身有独立血量, 自己承受伤害, 死亡时触发分裂
     * ★ 非分裂模式 (splittable=false): 伤害转移给头部 (PU132 WormComp.damage L176-178)
     *
     * 借鉴 PU132 WormSegmentUnit.damage:
     * if(wormType.splittable) segmentHealth -= amount * wormType.segmentDamageScl;
     * trueParentUnit.damage(amount);
     *
     * ★ segmentDamageScl: 段身伤害缩放 (splittable 模式下有效)
     *   越大 = 段身越脆, 越容易死亡分裂
     *   toxobyte 原版 8f, catenapede 原版 12f
     */
    @Override
    public void damage(float amount) {
        if (head != null && head.isAdded() && !head.splittable) {
            // 非分裂模式: 伤害转移给头部
            head.damage(amount);
            return;
        }
        // 分裂模式: 自己承受伤害 (应用 segmentDamageScl 缩放)
        float scl = (head != null) ? head.segmentDamageScl : 1f;
        super.damage(amount * scl);
        // 头部也受到原始伤害 (PU132 原版行为: 段身受击时头部也掉血)
        if (head != null && head.isAdded()) {
            head.damage(amount);
        }
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
        // 段身的 AI 状态跟随头部 (借鉴 PU132 WormSegmentUnit.isAI L124-127)
        if (head == null) return false;
        return head.controller() instanceof mindustry.entities.units.AIController;
    }

    @Override
    public boolean isPlayer() {
        // 段身是否被玩家控制 = 头部是否被玩家控制 (借鉴 PU132 WormSegmentUnit.isPlayer L118-121)
        if (head == null) return false;
        return head.isPlayer();
    }

    @Override
    public mindustry.gen.Player getPlayer() {
        // 段身的玩家 = 头部的玩家 (借鉴 PU132 WormSegmentUnit.getPlayer L135-138)
        if (head == null) return null;
        return isPlayer() ? (mindustry.gen.Player) head.controller() : null;
    }

    /**
     * ★ 关键1: 段身重写 controller() 无参方法, 返回头部的 controller
     *
     * v154.3 指挥模式 (CommandAI) 下令时 (InputHandler L314):
     *   if(unit.controller() instanceof CommandAI ai) ai.commandPosition(pos)
     * 如果段身返回自己的 controller, targetPos 会设在段身上, 段身不移动
     * 重写后返回头部的 controller, 下令时设置头部的 targetPos, 头部移动 ✓
     *
     * ★ 这是 "选中段身也能控制整体移动" 的核心实现
     */
    @Override
    public mindustry.entities.units.UnitController controller() {
        if (head != null && head.isAdded()) {
            return head.controller();
        }
        return super.controller();
    }

    /**
     * ★ 关键2: 段身重写 controller(UnitController next), 把玩家控制转给头部
     * (借鉴 PU132 WormSegmentUnit.controller L107-115)
     *
     * 玩家进入段身 (controller(player)) 时, 把 Player 转给头部
     * 段身自己不持有 Player controller (避免段身独立移动)
     */
    @Override
    public void controller(mindustry.entities.units.UnitController next) {
        if (!(next instanceof mindustry.gen.Player)) {
            // AI controller: 调用父类方法设置自己的 controller
            super.controller(next);
        } else if (head != null && head.isAdded()) {
            // Player controller: 转给头部 (用 head.controller(next) 设置头部 controller)
            head.controller(next);
            System.out.println("[段身] 控制转移: #" + segmentIndex + " → 头部");
        }
    }

    // 注意: 不重写 controller(UnitController), 让父类正常初始化 controller
    // 否则 controller 为 null, remove() 调用 controller.removed() 会 NPE
    // 段身不需要 AI: 我们在 update() 中不调用 super.update() 的 AI 部分

    /**
     * 让头部直接设置位置和朝向 (跳过物理)
     * 由 SegmentWormEntity.update() 每帧调用
     *
     * 注: 不重置 vel, 因为 updateSegmentVLocal 会把段身速度同步到 segments[i].vel
     * 段身 vel 用于碰撞/受击效果, 头部 update() 会重新覆盖
     */
    public void syncToHead(float x, float y, float rotation) {
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        // vel 不清零, 由头部 updateSegmentVLocal 每帧覆盖
    }

    /**
     * ★ 鼠标悬停时, 段身显示头部的贴图和名称 (参考 PU132 WormComp.icon + 自定义 display)
     *
     * v154.3 UnitType.display(unit, table) 内部:
     *   - 用 unit.type.uiIcon 显示贴图
     *   - 用 unit.type.localizedName 显示名称
     *   - 用 unit::healthf 显示血量
     *
     * 我们重写后, 让段身调用头部 type.display(head, table),
     * 这样贴图/名称/血量都跟头部一致
     */
    @Override
    public void display(Table table) {
        if (head != null && head.isAdded() && head.type != null) {
            // 调用头部的 type.display, 用头部的贴图/名称, 血量也跟头部一致
            head.type.display(head, table);
        } else {
            // 兜底: 没有头部时, 调用默认显示
            super.display(table);
        }
    }

    @Override
    public void add() {
        super.add();
        // ★ 如果 head==null (单独生成, 如 Spawner 召唤或作弊): 直接自爆
        //   段身只能由头部 createSegments 创建, 单独生成没有意义
        if (head == null) {
            health = 0f;
            dead = true;
            remove();
            return;
        }
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

        // 调试: 只打一次贴图查找结果
        if (!debugDrawLogged) {
            debugDrawLogged = true;
            String p = texturePrefix;
            String mp = "create-" + p;
            String[] names = {
                p + "segment", mp + "segment",
                p + "tail", mp + "tail",
                p + "segment-outline", mp + "segment-outline",
                p + "tail-outline", mp + "tail-outline",
                p + "cell", mp + "cell",
                p + "segment-cell", mp + "segment-cell"
            };
            StringBuilder found = new StringBuilder();
            for (String n : names) {
                try {
                    if (arc.Core.atlas.find(n).found()) {
                        if (found.length() > 0) found.append(", ");
                        found.append(n);
                    }
                } catch (Throwable ignored) {}
            }
            System.out.println("[段身] 贴图: 前缀=" + p + " 尾部=" + isTail + " 找到=" + found);
        }

        // 保存 type 原本的 region 字段
        mindustry.type.UnitType t = type;
        TextureRegion oldRegion = t.region;
        TextureRegion oldOutline = t.outlineRegion;
        TextureRegion oldCell = t.cellRegion;

        // 临时切换 region (借鉴 PU132 UnityUnitType.draw 第344-347行)
        // 尝试两种名字: 不带前缀 / 带 mod 前缀 (Mindustry 会给 mod 贴图加 modname- 前缀)
        String p = texturePrefix;  // 头部名字 + "-" (如 "arcnelidia-" / "toxobyte-")
        String modP = "create-" + p;  // 加 mod 前缀 (如 "create-arcnelidia-")
        if (isTail) {
            t.region = findRegion(p + "tail", modP + "tail");
            t.outlineRegion = findRegion(p + "tail-outline", modP + "tail-outline");
        } else {
            t.region = findRegion(p + "segment", modP + "segment");
            t.outlineRegion = findRegion(p + "segment-outline", modP + "segment-outline");
            // ★ cell 贴图查找: 两种命名都试
            //   arcnelidia 原版: "arcnelidia-cell" (PU132 风格, 文件名 arcnelidia-cell.png)
            //   toxobyte 原版: "toxobyte-segment-cell" (文件名 toxobyte-segment-cell.png)
            // ★ 规则: 修改时不能只改一种而破坏另一种, 必须两种都兼容 ★
            TextureRegion cellR = findRegion(p + "cell", modP + "cell");
            if (!cellR.found()) {
                cellR = findRegion(p + "segment-cell", modP + "segment-cell");
            }
            t.cellRegion = cellR;
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
