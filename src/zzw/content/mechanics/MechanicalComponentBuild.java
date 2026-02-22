package zzw.content.mechanics;

import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.gen.Building;

import java.util.HashSet;
import java.util.Set;

/**
 * 机械组件基类
 * 实现基础的应力和转速传输机制
 * 将连接在一起的传动组件视为一个整体系统
 */
public class MechanicalComponentBuild extends Building {
    // 机械属性
    public float rotationSpeed = 0f;  // 当前转速 (RPM)
    public float stress = 0f;         // 当前应力 (单位应力)
    public boolean isSource = false;   // 是否为动力源

    // 缓存相关
    private final MechanicalComponentBuild[] neighbors = new MechanicalComponentBuild[4];
    private int lastCacheFrame = -1;

    // 传动网络相关
    private int networkId = -1;  // 所属网络ID，-1表示未分配
    private static int nextNetworkId = 0;  // 下一个可用的网络ID
    private boolean needsNetworkUpdate = false;  // 是否需要网络更新

    // 常量定义
    protected static final float SPEED_THRESHOLD = 0.01f;
    protected static final float EFFICIENCY_LOSS_PER_BLOCK = 0.05f;  // 每格效率损失
    protected static final float MIN_EFFICIENCY = 0.1f; // 最小效率

    @Override
    public void update() {
        // 更新邻居缓存
        updateNeighborCache();

        // 如果需要网络更新，立即执行更新，不使用队列机制
        if (needsNetworkUpdate) {
            needsNetworkUpdate = false;
            if (tile != null) {
                updateTransmissionNetwork(this);
            }
        }

        // 普通更新（不更新转速和应力，这些由网络更新处理）
    }

    /**
     * 更新传动网络
     * @param sourceComponent 网络中的起始组件
     */
    private void updateTransmissionNetwork(MechanicalComponentBuild sourceComponent) {
        // 查找整个网络
        Set<MechanicalComponentBuild> network = findNetwork(sourceComponent);

        // 如果网络为空，返回
        if (network.isEmpty()) return;

        // 为网络分配ID（如果还没有）
        if (sourceComponent.networkId == -1) {
            sourceComponent.networkId = nextNetworkId++;
        }

        // 查找网络中的动力源
        MechanicalComponentBuild powerSource = null;
        float maxSpeed = 0;
        float totalStress = 0;  // 改为应力和，而不是最大应力

        // 收集所有连接的网络
        Set<Integer> connectedNetworkIds = new HashSet<>();
        for (MechanicalComponentBuild component : network) {
            // 检查邻居的网络ID，收集所有不同的网络ID
            for (MechanicalComponentBuild neighbor : component.neighbors) {
                if (neighbor != null && neighbor.networkId != -1 && neighbor.networkId != sourceComponent.networkId) {
                    connectedNetworkIds.add(neighbor.networkId);
                }
            }
        }

        // 如果有连接的其他网络，需要合并它们
        if (!connectedNetworkIds.isEmpty()) {
            // 找到所有连接网络中的组件
            Set<MechanicalComponentBuild> allComponents = new HashSet<>(network);

            // 遍历所有组件，找出属于连接网络的组件
            for (MechanicalComponentBuild component : network) {
                for (MechanicalComponentBuild neighbor : component.neighbors) {
                    if (neighbor != null && connectedNetworkIds.contains(neighbor.networkId)) {
                        // 找到属于连接网络的组件，将其加入网络
                        Set<MechanicalComponentBuild> connectedNetwork = findNetwork(neighbor);
                        allComponents.addAll(connectedNetwork);
                    }
                }
            }

            // 更新网络为所有组件的集合
            network = allComponents;
        }

        // 重新计算网络参数
        for (MechanicalComponentBuild component : network) {
            // 分配相同的网络ID
            component.networkId = sourceComponent.networkId;

            // 查找动力源
            if (component.isSource) {
                powerSource = component;
            }

            // 记录最大转速和最大应力
            maxSpeed = Math.max(maxSpeed, component.rotationSpeed);
            totalStress = Math.max(totalStress, component.stress);  // 使用最大应力而不是累加
        }

        // 同步整个网络的数据
        if (powerSource != null) {
            // 使用动力源的数据
            for (MechanicalComponentBuild component : network) {
                component.rotationSpeed = powerSource.rotationSpeed;
                component.stress = powerSource.stress;
            }
        } else if (maxSpeed > SPEED_THRESHOLD) {
            // 如果没有动力源但有转速，使用最大转速和累计应力
            for (MechanicalComponentBuild component : network) {
                component.rotationSpeed = maxSpeed;
                component.stress = totalStress;  // 使用累计应力
            }
        } else {
            // 没有动力源也没有转速，全部归零
            for (MechanicalComponentBuild component : network) {
                component.rotationSpeed = 0f;
                component.stress = 0f;
            }
        }

        // 通知所有邻居网络需要更新，确保连接的网络能及时响应变化
        for (MechanicalComponentBuild component : network) {
            for (MechanicalComponentBuild neighbor : component.neighbors) {
                if (neighbor != null && neighbor.networkId != sourceComponent.networkId) {
                    neighbor.markNetworkForUpdate();
                }
            }
        }
    }

    /**
     * 查找连接的传动网络
     * @param startComponent 起始组件
     * @return 网络中的所有组件
     */
    private Set<MechanicalComponentBuild> findNetwork(MechanicalComponentBuild startComponent) {
        Set<MechanicalComponentBuild> network = new HashSet<>();
        Set<MechanicalComponentBuild> visited = new HashSet<>();
        java.util.Queue<MechanicalComponentBuild> queue = new java.util.LinkedList<>();

        // 初始化队列
        queue.offer(startComponent);

        // 使用广度优先搜索查找所有连接的组件
        while (!queue.isEmpty()) {
            MechanicalComponentBuild component = queue.poll();
            
            // 如果已访问或组件无效，跳过
            if (visited.contains(component) || component.tile == null) continue;

            // 标记为已访问
            visited.add(component);

            // 添加到网络
            network.add(component);

            // 更新邻居缓存
            component.updateNeighborCache();

            // 访问所有邻居
            for (MechanicalComponentBuild neighbor : component.neighbors) {
                if (neighbor != null && !visited.contains(neighbor)) {
                    queue.offer(neighbor);
                }
            }
        }

        return network;
    }

    /**
     * 更新邻居缓存
     */
    private void updateNeighborCache() {
        int currentFrame = (int)Time.time;
        if (lastCacheFrame != currentFrame) {
            lastCacheFrame = currentFrame;
            for (int i = 0; i < 4; i++) {
                Building other = nearby(i);
                neighbors[i] = (other instanceof MechanicalComponentBuild) ?
                    (MechanicalComponentBuild) other : null;
            }
        }
    }

    /**
     * 查找动力源信息
     * @return 动力源信息
     */
    protected PowerSourceInfo findPowerSource() {
        PowerSourceInfo info = new PowerSourceInfo();

        for (MechanicalComponentBuild component : neighbors) {
            if (component == null) continue;

            if (component.isSource) {
                // 直接使用动力源的值
                info.speed = component.rotationSpeed;
                info.stress = component.stress;
                info.hasValidSource = true;
                info.hasDirectSource = true;
                info.distance = 1;
                break; // 找到直接动力源，立即返回
            } else if (component.rotationSpeed > SPEED_THRESHOLD && !info.hasDirectSource) {
                // 如果没有动力源，使用非动力源邻居的值
                info.speed = Math.max(info.speed, component.rotationSpeed); // 转速无损耗
                info.stress = Math.max(info.stress, component.stress); // 应力无损耗
                info.hasValidSource = true;
                info.distance = 2;
            }
        }

        return info;
    }

    /**
     * 动力源信息封装类
     */
    protected static class PowerSourceInfo {
        public float speed = 0f;
        public float stress = 0f;
        public boolean hasValidSource = false;
        public boolean hasDirectSource = false;
        public int distance = 1;
    }

    @Override
    public void draw() {
        super.draw(); // 基础组件不显示旋转动画
    }

    @Override
    public void display(Table table) {
        super.display(table);
        // 转速和应力的显示由子类实现
    }

    @Override
    public void onRemoved() {
        // 标记网络需要更新
        markNetworkForUpdate();
        super.onRemoved();
    }

    @Override
    public void onProximityAdded() {
        // 当附近有新方块时，标记网络需要更新
        markNetworkForUpdate();
        super.onProximityAdded();
    }

    /**
     * 标记网络需要更新
     */
    public void markNetworkForUpdate() {
        needsNetworkUpdate = true;

        // 通知所有邻居也需要更新
        updateNeighborCache();
        for (MechanicalComponentBuild neighbor : neighbors) {
            if (neighbor != null) {
                neighbor.needsNetworkUpdate = true;
            }
        }
    }
}