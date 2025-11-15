package zzw.content.mechanics;

import arc.scene.ui.layout.Table;
import arc.struct.IntSet;
import arc.util.Time;
import arc.util.Timer;
import mindustry.gen.Building;

/**
 * 传动箱实现 - 网络版
 * 连接的传动箱视为一个整体，共享相同的转速和应力
 * 每个传动箱都可以是主节点，多个转速输入时取最高值，应力则累加
 * 网络特性：
 * 1. 传动箱会自动形成网络，连接在一起的传动箱共享转速和应力
 * 2. 网络中的任意传动箱都可以作为主节点进行计算
 * 3. 网络合并和拆分时自动处理状态转换
 * 4. 效率随网络规模增大而降低，模拟真实机械系统
 */
public class TransmissionBoxBuild extends MechanicalComponentBuild {
    // 网络相关属性
    private int networkId = -1; // 所属网络ID，-1表示未分配网络
    private boolean isNetworkMaster = false; // 是否为网络主节点（负责计算）
    private static int nextNetworkId = 0; // 下一个可用的网络ID
    private static final IntSet visitedSet = new IntSet(); // 用于网络搜索的访问记录

    // 网络值缓存
    private float networkSpeed = 0f; // 网络共享转速
    private float networkStress = 0f; // 网络共享应力
    private boolean needsNetworkUpdate = true; // 是否需要更新网络值

    // 常量定义
    private static final float SPEED_THRESHOLD = 0.01f;              // 转速阈值
    private static final float EFFICIENCY_LOSS_PER_BLOCK = 0.05f;    // 每个传动箱的效率损失
    private static final float UI_UPDATE_INTERVAL = 1/30f;            // UI更新间隔（30fps）

    @Override
    public void update() {
        // 每个传动箱都可以是主节点，进行网络计算
        if (networkId != -1) {
            // 确保网络完整性
            validateNetwork();

            // 如果需要更新网络值，则计算并传播
            if (needsNetworkUpdate) {
                calculateNetworkValues();
                propagateNetworkValues();
                needsNetworkUpdate = false;
            }

            // 更新自身值
            rotationSpeed = networkSpeed;
            stress = networkStress;

            // 检查是否有应力和转速的方块连接，更新主节点状态
            checkAndUpdateMasterNode();
        } else {
            // 未分配网络的传动箱，尝试加入网络或成为新网络的主节点
            findOrCreateNetwork();
        }
    }

    /**
     * 检查是否有应力和转速的方块连接，并更新主节点状态
     */
    private void checkAndUpdateMasterNode() {
        boolean hasPowerSource = false;

        // 检查四个方向的邻居
        for (int i = 0; i < 4; i++) {
            Building other = nearby(i);
            if (other instanceof MechanicalComponentBuild component) {
                // 如果邻居是动力源或者有转速和应力，则标记为有动力源
                if (component.isSource || (component.rotationSpeed > SPEED_THRESHOLD && component.stress > 0)) {
                    hasPowerSource = true;
                    break;
                }
            }
        }

        // 如果有动力源连接且当前不是主节点，则设为主节点
        if (hasPowerSource && !isNetworkMaster) {
            isNetworkMaster = true;
            needsNetworkUpdate = true; // 标记需要更新网络值
        }
        // 如果没有动力源连接且当前是主节点，则取消主节点状态
        else if (!hasPowerSource && isNetworkMaster) {
            isNetworkMaster = false;
            needsNetworkUpdate = true; // 标记需要更新网络值
        }
    }

    /**
     * 验证网络完整性，确保所有传动箱都在同一网络中
     */
    private void validateNetwork() {
        if (networkId == -1) return;

        visitedSet.clear();
        validateNetworkRecursive(this);
    }

    /**
     * 递归验证网络
     */
    private void validateNetworkRecursive(TransmissionBoxBuild current) {
        int posId = current.pos();
        if (visitedSet.contains(posId)) return;
        visitedSet.add(posId);

        // 检查所有邻居
        for (int i = 0; i < 4; i++) {
            Building other = current.nearby(i);
            if (other instanceof TransmissionBoxBuild neighbor) {
                // 如果邻居有不同网络ID，将其合并到当前网络
                if (neighbor.networkId != networkId) {
                    mergeNetworks(this, neighbor);
                }
                validateNetworkRecursive(neighbor);
            }
        }
    }

    /**
     * 计算网络值
     * 多个转速输入时取最高值，应力则累加
     */
    private void calculateNetworkValues() {
        if (networkId == -1) return;

        visitedSet.clear();
        float maxSpeed = 0f;
        float totalStress = 0f;
        boolean hasMaster = false; // 检查网络中是否有主节点

        // 收集网络中的所有传动箱
        java.util.List<TransmissionBoxBuild> networkBoxes = new java.util.ArrayList<>();
        collectNetworkBoxes(this, networkBoxes);

        // 检查网络中是否有主节点
        for (TransmissionBoxBuild box : networkBoxes) {
            if (box.isNetworkMaster) {
                hasMaster = true;
                break;
            }
        }

        // 如果没有主节点，清零转速和应力
        if (!hasMaster) {
            networkSpeed = 0f;
            networkStress = 0f;
            return;
        }

        // 计算网络值
        for (TransmissionBoxBuild box : networkBoxes) {
            // 从邻居获取输入
            float localMaxSpeed = 0f;
            float localTotalStress = 0f;

            for (int i = 0; i < 4; i++) {
                Building other = box.nearby(i);
                if (other instanceof MechanicalComponentBuild component) {
                    // 考虑所有机械组件作为输入，包括传动箱
                    if (component.rotationSpeed > SPEED_THRESHOLD) {
                        localMaxSpeed = Math.max(localMaxSpeed, component.rotationSpeed);
                        localTotalStress += component.stress;
                    }
                }
            }

            // 更新网络值
            maxSpeed = Math.max(maxSpeed, localMaxSpeed);
            totalStress += localTotalStress;
        }

        // 应用效率损失
        int networkSize = networkBoxes.size();
        float efficiency = Math.max(0.1f, 1.0f - (networkSize - 1) * EFFICIENCY_LOSS_PER_BLOCK);

        networkSpeed = maxSpeed * efficiency;
        networkStress = totalStress * efficiency;
    }

    /**
     * 收集网络中的所有传动箱
     */
    private void collectNetworkBoxes(TransmissionBoxBuild current, java.util.List<TransmissionBoxBuild> boxes) {
        int posId = current.pos();
        if (visitedSet.contains(posId)) return;
        visitedSet.add(posId);

        boxes.add(current);

        // 检查所有邻居
        for (int i = 0; i < 4; i++) {
            Building other = current.nearby(i);
            if (other instanceof TransmissionBoxBuild neighbor && neighbor.networkId == networkId) {
                collectNetworkBoxes(neighbor, boxes);
            }
        }
    }

    /**
     * 将网络值传播到所有传动箱
     */
    private void propagateNetworkValues() {
        if (networkId == -1) return;

        visitedSet.clear();
        propagateValuesRecursive(this);
    }

    /**
     * 递归传播网络值
     */
    private void propagateValuesRecursive(TransmissionBoxBuild current) {
        int posId = current.pos();
        if (visitedSet.contains(posId)) return;
        visitedSet.add(posId);

        // 更新当前传动箱的值
        current.networkSpeed = this.networkSpeed;
        current.networkStress = this.networkStress;
        current.rotationSpeed = this.networkSpeed;
        current.stress = this.networkStress;
        current.needsNetworkUpdate = false; // 避免重复更新

        // 检查所有邻居
        for (int i = 0; i < 4; i++) {
            Building other = current.nearby(i);
            if (other instanceof TransmissionBoxBuild neighbor && neighbor.networkId == networkId) {
                propagateValuesRecursive(neighbor);
            }
        }
    }

    /**
     * 查找或创建网络
     */
    private void findOrCreateNetwork() {
        visitedSet.clear();

        // 检查是否有相邻的传动箱网络
        for (int i = 0; i < 4; i++) {
            Building other = nearby(i);
            if (other instanceof TransmissionBoxBuild neighbor && neighbor.networkId != -1) {
                // 加入现有网络
                networkId = neighbor.networkId;
                isNetworkMaster = false;
                needsNetworkUpdate = true; // 标记自己需要更新网络值
                neighbor.validateNetwork(); // 通知验证网络
                neighbor.needsNetworkUpdate = true; // 标记需要更新网络值
                return;
            }
        }

        // 没有找到现有网络，创建新网络
        networkId = nextNetworkId++;
        isNetworkMaster = true;
        needsNetworkUpdate = true; // 标记需要更新网络值
    }

    /**
     * 合并两个网络
     */
    private static void mergeNetworks(TransmissionBoxBuild master, TransmissionBoxBuild other) {
        int oldNetworkId = other.networkId;

        // 将other的网络合并到master的网络
        visitedSet.clear();
        mergeNetworksRecursive(master, other, oldNetworkId);
    }

    /**
     * 递归合并网络
     */
    private static void mergeNetworksRecursive(TransmissionBoxBuild master, TransmissionBoxBuild current, int oldNetworkId) {
        int posId = current.pos();
        if (visitedSet.contains(posId)) return;
        visitedSet.add(posId);

        // 更新网络ID和主节点状态
        current.networkId = master.networkId;
        current.isNetworkMaster = false;

        // 递归处理邻居
        for (int i = 0; i < 4; i++) {
            Building other = current.nearby(i);
            if (other instanceof TransmissionBoxBuild neighbor && neighbor.networkId == oldNetworkId) {
                mergeNetworksRecursive(master, neighbor, oldNetworkId);
            }
        }
    }

    @Override
    public void onRemoved() {
        super.onRemoved();

        // 通知网络中的其他传动箱需要更新
        if (networkId != -1) {
            notifyNetworkNeedsUpdate();
        }
    }

    /**
     * 通知网络中的所有传动箱需要更新
     */
    private void notifyNetworkNeedsUpdate() {
        if (networkId == -1) return;

        visitedSet.clear();
        notifyNetworkNeedsUpdateRecursive(this);
    }

    /**
     * 递归通知网络中的传动箱需要更新
     */
    private void notifyNetworkNeedsUpdateRecursive(TransmissionBoxBuild current) {
        int posId = current.pos();
        if (visitedSet.contains(posId)) return;
        visitedSet.add(posId);

        // 标记需要更新
        current.needsNetworkUpdate = true;

        // 检查所有邻居
        for (int i = 0; i < 4; i++) {
            Building other = current.nearby(i);
            if (other instanceof TransmissionBoxBuild neighbor && neighbor.networkId == networkId) {
                notifyNetworkNeedsUpdateRecursive(neighbor);
            }
        }
    }

    @Override
    public void draw() {
        super.draw();

        // 如果是网络主节点，可以添加特殊视觉效果
        if (isNetworkMaster) {
            // TODO: 这里可以添加主节点的特殊视觉效果
            drawNetworkMasterEffect();
        }
    }

    /**
     * 绘制网络主节点的特殊效果
     */
    private void drawNetworkMasterEffect() {
        // 预留方法，用于绘制网络主节点的特殊视觉效果
    }

    @Override
    public void onProximityAdded() {
        super.onProximityAdded();

        // 如果传动箱连接到现有网络，需要更新网络
        if (networkId != -1) {
            needsNetworkUpdate = true;
        } else {
            // 尝试加入网络
            findOrCreateNetwork();
        }
    }

    @Override
    public void display(Table table) {
        // 不调用 super.display(table) 以避免重复显示应力和转速

        // 创建可更新的应力显示标签
        var stressLabel = table.add("[accent]应力: [white]" + (int)stress + " us").width(160).get();
        table.row();

        // 创建可更新的转速显示标签
        var speedLabel = table.add("[accent]转速: [white]" + (int)rotationSpeed + " rpm").width(160).get();
        table.row();

        // 显示网络信息
        if (networkId != -1) {
            table.add("[accent]网络ID: [white]" + networkId).width(160);
            table.row();
            table.add("[accent]主节点: [white]" + (isNetworkMaster ? "是" : "否")).width(160);
        }

        // 添加更新任务，每帧更新显示值
        Time.runTask(0f, () -> {
            // 使用定时器持续更新UI显示
            Timer.schedule(() -> {
                if (this.isAdded() && stressLabel != null && speedLabel != null) {
                    stressLabel.setText("[accent]应力: [white]" + (int)stress + " us");
                    speedLabel.setText("[accent]转速: [white]" + (int)rotationSpeed + " rpm");
                }
            }, 0, UI_UPDATE_INTERVAL); // 使用优化的更新间隔
        });
    }
}
