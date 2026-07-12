package zzw.content.mechanics;

import arc.Events;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.IntSet;
import arc.struct.Seq;
import mindustry.game.EventType;
import mindustry.gen.Building;

/**
 * 机械组件基类 - 参考 Minecraft Create 模组的源驱动传播树模型
 * 
 * 核心设计 (参考 Create 的 KineticBlockEntity + RotationPropagator):
 * 1. 每个组件持有一个 source 指针, 指向驱动它的邻居 → 构成传播树
 * 2. 网络 ID = 源方块的 pos() → 天然去重
 * 3. 传播只在放置/拆除/变速时触发, 正常运行时零开销
 * 4. 应力 = baseImpact × |speed|, 容量 = baseCapacity × |speed|
 * 5. 过载时 getSpeed() 返回 0 (虚拟停转), speed 字段不变 (可恢复)
 * 6. 每 60 tick 做一次轻量验证, 防止状态漂移
 */
public class MechanicalComponentBuild extends Building {
    // —— 实例字段 ——
    public float speed = 0f;              // 理论转速 (RPM, 可正可负)
    public float stress = 0f;            // 网络总应力
    public float capacity = 0f;          // 网络总容量
    public boolean overStressed = false; // 是否过载
    public boolean isSource = false;     // 是否是动力源

    // source 指针: 指向驱动我的邻居 (传播树的父亲)
    // null 表示自己是源或未连接
    public int sourcePos = -1;           // source 的 pos(), -1 = 无源
    public int networkId = -1;           // 所属网络 ID (= 源的 pos())

    // —— 全局静态字段 ——
    private static final IntMap<MechanicalComponentBuild> allComponents = new IntMap<>();
    private static boolean networkDirty = false;          // 脏标记: 下个 tick 需要重算
    private static boolean listenersRegistered = false;

    // 周期验证计数器 (每 60 tick ≈ 1 秒验证一次 source 有效性)
    private transient int validationCountdown = 60;

    // —— 常量 ——
    protected static final float SPEED_THRESHOLD = 0.01f;
    protected static final float INFINITY_STRESS = 10000f;
    protected static final int VALIDATION_INTERVAL = 60;

    // 每种方块的基础应力影响 (1 RPM 时). 子类可覆盖
    protected float baseImpact = 1f;
    // 每种源的基础容量 (1 RPM 时). 子类可覆盖
    protected float baseCapacity = 0f;

    private static void ensureListeners() {
        if (listenersRegistered) return;
        listenersRegistered = true;
        Events.on(EventType.ResetEvent.class, e -> {
            allComponents.clear();
            networkDirty = false;
        });
    }

    @Override
    public void created() {
        ensureListeners();
        super.created();
        allComponents.put(pos(), this);
        networkDirty = true;
    }

    /**
     * 基类 update:
     * 1. 若 networkDirty, 重新传播网络 (从所有源出发)
     * 2. 周期性验证 source 有效性 (兜底)
     */
    @Override
    public void update() {
        if (networkDirty) {
            propagateNetworks();
            networkDirty = false;
        }

        // 周期验证: 防止状态漂移 (如 source 被意外删除但事件没触发)
        if (--validationCountdown <= 0) {
            validationCountdown = VALIDATION_INTERVAL;
            validateSource();
        }
    }

    @Override
    public void onRemoved() {
        allComponents.remove(pos());
        // 标记需要刷新: 下个 tick 会重新传播
        networkDirty = true;
        super.onRemoved();
    }

    /** 请求网络刷新 */
    protected static void requestNetworkRefresh() {
        networkDirty = true;
    }

    /**
     * 获取实际转速: 过载时返回 0 (虚拟停转, 可恢复)
     * 子类和外部代码应使用此方法而非直接访问 speed
     */
    public float getSpeed() {
        return overStressed ? 0f : speed;
    }

    /**
     * 计算此组件在当前转速下的应力贡献
     * Create 公式: stress = baseImpact × |speed|
     */
    public float calculateStressApplied() {
        return baseImpact * Math.abs(speed);
    }

    /**
     * 计算此源在当前转速下的容量贡献
     * Create 公式: capacity = baseCapacity × |speed|
     */
    public float calculateCapacity() {
        return baseCapacity * Math.abs(speed);
    }

    /**
     * 周期验证: 检查 source 是否还活着
     * 若 source 已失效, 标记网络刷新
     */
    private void validateSource() {
        if (sourcePos != -1) {
            Building src = mindustry.Vars.world.build(sourcePos);
            if (!(src instanceof MechanicalComponentBuild) || !((MechanicalComponentBuild) src).isSource) {
                // source 失效, 请求刷新
                sourcePos = -1;
                networkId = -1;
                requestNetworkRefresh();
            }
        }
    }

    /**
     * 从所有应力源出发, BFS 传播转速和应力
     * 
     * 算法 (参考 Create 的 propagateNewSource):
     * 1. 遍历所有组件: 源入队, 非源清零
     * 2. BFS 从所有源同时扩散
     * 3. 设置每个组件的 source 指针 (传播树)
     * 4. 累计网络总应力/容量, 检查过载
     */
    private static void propagateNetworks() {
        IntSet visited = new IntSet();
        Seq<MechanicalComponentBuild> queue = new Seq<>();

        // 第一步: 收集源 + 清零非源
        for (var e : allComponents.entries()) {
            MechanicalComponentBuild m = e.value;
            if (m.isSource) {
                visited.add(m.pos());
                queue.add(m);
                m.sourcePos = -1;     // 源没有 source
                m.networkId = m.pos(); // 网络 ID = 源位置
            } else {
                m.speed = 0f;
                m.stress = 0f;
                m.capacity = 0f;
                m.sourcePos = -1;
                m.networkId = -1;
                m.overStressed = false;
            }
        }

        // 第二步: BFS 从所有源同时扩散
        // 每个源的传播树中, 成员继承源的速度
        while (queue.size > 0) {
            MechanicalComponentBuild cur = queue.pop();

            for (int i = 0; i < 4; i++) {
                Building n = cur.nearby(i);
                if (n instanceof MechanicalComponentBuild m) {
                    int p = m.pos();
                    if (visited.add(p)) {
                        // 第一次被访问: 继承当前节点的值
                        if (!m.isSource) {
                            m.speed = cur.speed;
                            m.networkId = cur.networkId;
                            m.sourcePos = cur.pos(); // 设置 source 指针
                        }
                        queue.add(m);
                    } else {
                        // 已被访问过: 多源场景取更大速度
                        if (!m.isSource && Math.abs(cur.speed) > Math.abs(m.speed)) {
                            m.speed = cur.speed;
                            m.networkId = cur.networkId;
                            m.sourcePos = cur.pos();
                        }
                    }
                }
            }
        }

        // 第三步: 计算每个网络的总应力/容量, 判定过载
        // 用 IntMap 累计每个网络的总应力/容量
        IntMap<Float> networkStress = new IntMap<>();
        IntMap<Float> networkCapacity = new IntMap<>();

        for (var e : allComponents.entries()) {
            MechanicalComponentBuild m = e.value;
            if (m.networkId == -1) continue;

            float impact = m.calculateStressApplied();
            networkStress.put(m.networkId, networkStress.get(m.networkId, 0f) + impact);

            if (m.isSource) {
                float cap = m.calculateCapacity();
                networkCapacity.put(m.networkId, networkCapacity.get(m.networkId, 0f) + cap);
            }
        }

        // 第四步: 同步网络级的应力/容量到每个成员, 判定过载
        for (var e : allComponents.entries()) {
            MechanicalComponentBuild m = e.value;
            if (m.networkId == -1) continue;

            m.stress = networkStress.get(m.networkId, 0f);
            m.capacity = networkCapacity.get(m.networkId, 0f);
            m.overStressed = m.capacity < m.stress;
        }
    }
}
