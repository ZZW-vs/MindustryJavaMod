package zzw.content.units;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.entities.Units;
import mindustry.type.Weapon;
import mindustry.entities.units.WeaponMount;

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
            // 同步头部 hitTime
            // ★ v158 移除了 ammo 字段, 段身武器系统会自行处理弹药
            hitTime = head.hitTime;

            // ★ 段身武器: 参照 PU132 WormSegmentUnit.updateWeapon 重写索敌部分
            // 发射/冷却/旋转交给 v154.3 原版 Weapon.update() 处理
            if (mounts != null && mounts.length > 0) {
                boolean can = canShoot();

                // ★ PU132 弹幕同步机制 (WormAI.updateWeapons L48-63):
                // 当头部被玩家控制且正在射击, 段身在 barrageRange 内时,
                // 段身复制头部的 aimX/aimY, 跟随玩家瞄准方向齐射
                boolean barrageSync = head.isPlayer() && head.isShooting
                    && within(head, head.barrageRange + hitSize / 2f);

                for (WeaponMount mount : mounts) {
                    Weapon weapon = mount.weapon;

                    if (barrageSync && weapon.controllable) {
                        // ★ 弹幕模式: 复制头部瞄准目标, 跟随玩家射击
                        mount.aimX = head.aimX;
                        mount.aimY = head.aimY;
                        mount.shoot = true;
                        mount.rotate = true;
                    } else {
                        // ★ 自动索敌: 搜索自己射程内的目标 (单位 + 建筑)
                        // 之前只搜 Units.closestEnemy (只找单位), 导致段身不打建筑
                        // 改用 Units.closestTarget (返回 Teamc, 包含单位+建筑)
                        mindustry.gen.Teamc tgt = Units.closestTarget(team, x, y, weapon.range(),
                            u -> !u.dead,
                            t -> true);
                        if (tgt != null) {
                            mount.aimX = tgt.getX();
                            mount.aimY = tgt.getY();
                            mount.shoot = true;
                            mount.rotate = true;
                        } else {
                            mount.shoot = false;
                            mount.rotate = false;
                        }
                    }

                    // ★ 交给原版 Weapon.update 处理冷却、旋转、发射
                    weapon.update(this, mount);
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
        // ★ 段身 z 层级: 段身依次低于头部
        //   头部 z = headZ + 0.001 (通过 Draw.draw() 推迟绘制, 最高)
        //   段身 0 z = headZ - 0.0001 (最高段身, 紧贴头部, 最后画)
        //   段身 1 z = headZ - 0.0002 (第2节)
        //   ...
        //   段身 n-1 z = headZ - 0.0001 * n (最低段身, 尾部, 最先画)
        //   渲染顺序: 尾部 → 第2节 → 第1节 → 头部
        //   覆盖关系: 头部覆盖第1节, 第1节覆盖第2节, 依次向下
        Draw.z(z - (segmentIndex + 1f) * 0.0001f);

        // 调试: 只打一次贴图状态
        if (!debugDrawLogged) {
            debugDrawLogged = true;
            String p = texturePrefix;
            System.out.println("[段身] 贴图调试: 前缀=" + p + " 尾部=" + isTail
                + " type.region.found=" + type.region.found()
                + " type.outline.found=" + type.outlineRegion.found()
                + " type.cell.found=" + type.cellRegion.found());
        }

        // 保存 type 原本的字段
        mindustry.type.UnitType t = type;
        TextureRegion oldRegion = t.region;
        TextureRegion oldOutline = t.outlineRegion;
        TextureRegion oldCell = t.cellRegion;
        boolean oldDrawCell = t.drawCell;

        // ★ 非尾部: 段身 UnitType 自身的 region/outline/cell 已经是正确的段身贴图
        //    (Mindustry 在 UnitType.load() 时已加载 xxx-segment / xxx-segment-outline / xxx-segment-cell)
        //    不需要额外替换, 避免 atlas.find 失败导致 error
        // ★ 尾部: 才需要查找 tail 贴图替换
        if (isTail) {
            String p = texturePrefix;
            String modP = "create-" + p;
            TextureRegion tailR = findRegion(p + "tail", modP + "tail");
            if (tailR.found()) {
                t.region = tailR;
            }
            TextureRegion tailO = findRegion(p + "tail-outline", modP + "tail-outline");
            if (tailO.found()) {
                t.outlineRegion = tailO;
            }
            // 尾部不绘制 cell (PU132 原版行为)
            t.drawCell = false;
        }

        // 按 segmentIndex 过滤段身武器
        mindustry.entities.units.WeaponMount[] oldMounts = mounts;
        mindustry.entities.units.WeaponMount[] filteredMounts = filterMountsForSegment(oldMounts);
        mounts = filteredMounts;

        // 调用 super.draw() 绘制 (非尾部使用 UnitType 自身已加载的段身贴图)
        super.draw();

        // 恢复 mounts
        mounts = oldMounts;

        // 恢复 type 的字段
        t.region = oldRegion;
        t.outlineRegion = oldOutline;
        t.cellRegion = oldCell;
        t.drawCell = oldDrawCell;

        // ★ 液压装饰: 恢复原版 PU132 绘制方式, 不分层
        if (head != null && head.isAdded() && head.type != null) {
            WormDecal decal = SegmentWormEntity.wormDecals.get(head.type.name);
            if (decal != null) {
                mindustry.gen.Unit parent = getParentSegment();
                if (parent != null) {
                    decal.draw(this, parent);
                }
            }
        }

        // 恢复 z
        Draw.z(z);
    }

    /**
     * 获取当前段身的父段 (PU132 unit.parent())
     * - segmentIndex == 0: 父段是头部 (head)
     * - segmentIndex > 0: 父段是前一段身 (head.segments[segmentIndex - 1])
     * 用于 WormDecal.draw(this, parent) 绘制段身到父段的液压杆
     */
    private mindustry.gen.Unit getParentSegment() {
        if (head == null || !head.isAdded() || head.segments == null) return null;
        if (segmentIndex == 0) return head;
        int parentIdx = segmentIndex - 1;
        if (parentIdx < head.segments.length) {
            return head.segments[parentIdx];
        }
        return null;
    }

    /**
     * 按 segmentIndex 过滤段身武器 (PU132 weaponIdx 机制简化版)
     * PU132: segmentWeapons = Seq<Weapon>[] {组0, 组1, 组2, 空组}
     *   段 i 的武器组 idx = i >= length-1 ? 组数-1 : i % max(1, 组数-1)
     * 当前项目: 段身 type.weapons 包含所有武器, 按 groupSize 分组
     *   段 i 的组 idx = i % groupCount (非尾部), 尾部=空组(不画武器)
     *
     * 配置: 通过 SegmentConfig.segmentWeaponGroupSize 指定每组武器数
     *   oppression: 6 个段身武器, 分 3 组 (每组 2 个), 尾部空组
     *   devourer: 3 个段身武器, 1 组 (所有武器), 尾部空组
     *   arcnelidia/toxobyte/catenapede: 1 个段身武器, 1 组
     */
    private mindustry.entities.units.WeaponMount[] filterMountsForSegment(mindustry.entities.units.WeaponMount[] allMounts) {
        if (allMounts == null || allMounts.length == 0) return allMounts;
        // 查头部 SegmentConfig 获取武器组配置
        if (head == null) return allMounts;
        SegmentWormEntity.SegmentConfig cfg = SegmentWormEntity.configs.get(head.type.name);
        if (cfg == null) return allMounts;
        int groupSize = cfg.segmentWeaponGroupSize;
        int totalWeapons = allMounts.length;
        if (groupSize <= 0 || groupSize >= totalWeapons) return allMounts;

        // 计算组数 (最后一个组是空组, 用于尾部)
        int groupCount = (int) Math.ceil((float) totalWeapons / groupSize);
        if (groupCount <= 1) return allMounts;

        // PU132: idx = i >= segmentLength - 1 ? groupCount : i % max(1, groupCount - 1)
        // 但我们不知道 segmentLength, 用 isTail 判断
        int idx;
        if (isTail) {
            // 尾部: 空组 (不画武器)
            return new mindustry.entities.units.WeaponMount[0];
        } else {
            // 非尾部: 按 segmentIndex 分组 (mod groupCount-1, 因为最后一组是尾部空组)
            int effectiveGroups = groupCount - 1;
            if (effectiveGroups <= 0) return allMounts;
            idx = segmentIndex % effectiveGroups;
        }

        // 提取对应组的 mounts
        int start = idx * groupSize;
        int end = Math.min(start + groupSize, totalWeapons);
        mindustry.entities.units.WeaponMount[] result = new mindustry.entities.units.WeaponMount[end - start];
        System.arraycopy(allMounts, start, result, 0, end - start);
        return result;
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
     * - 段身 vs 自己的头部 → 不碰撞
     * - 段身 vs 同头部的相邻段身 → 不碰撞 (避免移动时抖动)
     * - 段身 vs 同头部的非相邻段身 → 碰撞 (生成时重叠会弹开)
     * - 段身 vs 其他单位 → 正常碰撞 (其他单位不能穿过段身)
     *
     * 借鉴 PU132 WormSegmentUnit.collides + 原版碰撞挤压弹开效果
     * 允许非相邻段身碰撞, 这样生成很多段时会因为重叠而互相推开, 形成自然散开效果
     */
    @Override
    public boolean collides(Hitboxc other) {
        // 段身 vs 自己的头部 → 不碰撞
        if (other == head) return false;
        // 段身 vs 同头部的其他段身 → 相邻的不碰撞, 非相邻的碰撞
        if (head != null && other instanceof SegmentUnitEntity) {
            SegmentUnitEntity o = (SegmentUnitEntity) other;
            if (o.head == head) {
                // ★ 扩大不碰撞范围: index 差 ≤ 2 的段身都不碰撞
                //   之前只排除差1的相邻段, 导致快速转向时非相邻段互相挤压抖动
                int indexDiff = Math.abs(segmentIndex - o.segmentIndex);
                return indexDiff > 2;
            }
        }
        // 段身 vs 其他单位 → 正常碰撞 (其他单位不能穿过段身)
        return super.collides(other);
    }
}
