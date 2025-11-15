package zzw.content;

import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.IntSet;
import arc.util.Time;
import arc.util.Timer;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * 机械系统类 - 优化版
 * 提供高效的机械传动系统实现
 * 优化点：
 * 1. 使用IntSet缓存已访问的方块，避免重复计算
 * 2. 优化邻居检测逻辑，减少内存分配
 * 3. 添加机械效率计算，使系统更真实
 * 4. 改进UI更新机制，减少不必要的渲染
 */
public class Z_Mechanics {
    // 机械方块定义
    public static Block stressSource;      // 应力源
    public static Block mechanicalShaft;   // 传动箱

    // 常量定义
    private static final float MAX_SPEED = 256f;                    // 最大转速
    private static final float SPEED_THRESHOLD = 0.01f;              // 转速阈值
    private static final float DEFAULT_ACCELERATION = 0.5f;          // 默认加速度
    private static final float EFFICIENCY_LOSS_PER_BLOCK = 0.05f;    // 每个传动箱的效率损失
    private static final int MAX_SEARCH_DEPTH = 15;                  // 最大搜索深度
    private static final float UI_UPDATE_INTERVAL = 1/30f;            // UI更新间隔（30fps）

    /**
     * 加载所有自定义机械
     */
    public static void load() {
        createPowerSources();
        createTransmission();
    }

    /**
     * 创建动力源 - 应力源
     */
    private static void createPowerSources() {
        stressSource = new Block("stress_source") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 100, Items.copper, 80));
            size = 1;
            health = 500;
            solid = true;
            update = true;
            configurable = true;
            buildVisibility = BuildVisibility.sandboxOnly;

            config(Float.class, (StressSourceBuild build, Float value) ->
                build.setTargetSpeed(Mathf.clamp(value, -MAX_SPEED, MAX_SPEED)));
        }};

        stressSource.buildType = StressSourceBuild::new;
    }

    /**
     * 创建传输组件 - 传动箱
     */
    private static void createTransmission() {
        mechanicalShaft = new Block("transmission_box") {{
            requirements(Category.crafting, ItemStack.with(Items.lead, 10, Z_Items.Iron, 5));
            size = 1;
            health = 80;
            solid = true;
            update = true;
        }};

        mechanicalShaft.buildType = TransmissionBoxBuild::new;
    }

    /**
     * 机械组件基类 - 优化版
     * 使用缓存和批量处理提高性能
     */
    public static class MechanicalComponentBuild extends Building {
        // 机械属性
        public float rotationSpeed = 0f;     // 旋转速度
        public float stress = 0f;            // 机械应力
        public boolean isSource = false;     // 是否为动力源
        public boolean isSink = false;       // 是否为动力消耗者

        // 性能优化：缓存相关
        private final MechanicalComponentBuild[] neighbors = new MechanicalComponentBuild[4];  // 邻居缓存数组
        private int lastCacheFrame = -1;     // 上次缓存更新帧
        protected boolean needsUpdate = true; // 是否需要更新，改为protected以便子类访问

        // 路径查找优化
        private static final IntSet visitedSet = new IntSet();  // 已访问方块集合
        private int lastPathCheckFrame = -1;  // 上次路径检查帧
        private Boolean hasPathToSourceCache = null; // 是否有到动力源的路径缓存

        @Override
        public void update() {
            // 使用needsUpdate标志避免不必要的计算
            if (!needsUpdate && !isSource) return;

            if (!isSource) {
                updateFromNeighbors();
            }

            if (isSink && rotationSpeed > 0) {
                handleMechanicalOperation();
            }

            needsUpdate = false;
        }

        /**
         * 从相邻组件更新动力状态
         * 优化了邻居检测逻辑和动力传递效率
         * 工作流程：
         * 1. 更新邻居缓存（仅在必要时）
         * 2. 检查所有邻居组件，寻找有效动力源
         * 3. 计算基于距离的效率损失
         * 4. 更新自身旋转速度和应力
         * 5. 通知邻居需要更新（仅在值变化时）
         */
        private void updateFromNeighbors() {
            float maxInputSpeed = 0f;           // 最大输入转速
            float maxInputStress = 0f;          // 最大输入应力
            boolean hasValidSource = false;     // 是否有有效动力源
            int distanceFromSource = Integer.MAX_VALUE; // 到动力源的最小距离

            // 仅在必要时更新邻居缓存
            int currentFrame = (int)Time.time;
            if (lastCacheFrame != currentFrame) {
                lastCacheFrame = currentFrame;
                for (int i = 0; i < 4; i++) {
                    Building other = nearby(i);
                    neighbors[i] = (other instanceof MechanicalComponentBuild) ?
                        (MechanicalComponentBuild) other : null;
                }
            }

            // 检查邻居组件
            for (MechanicalComponentBuild component : neighbors) {
                if (component != null) {
                    if (component.isSource) {
                        // 直接从动力源获取动力
                        maxInputSpeed = Math.max(maxInputSpeed, component.rotationSpeed);
                        maxInputStress = Math.max(maxInputStress, component.stress);
                        hasValidSource = true;
                        distanceFromSource = Math.min(distanceFromSource, 1);
                    } else if (component.rotationSpeed > SPEED_THRESHOLD) {
                        // 检查是否有通往动力源的路径
                        boolean pathValid = hasValidPathToSource(component);
                        if (pathValid) {
                            // 计算基于距离的效率损失
                            float distance = getDistanceToSource(component);
                            float efficiency = Math.max(0.1f, 1.0f - (distance * EFFICIENCY_LOSS_PER_BLOCK));

                            // 应用效率损失
                            float effectiveSpeed = component.rotationSpeed * efficiency;
                            float effectiveStress = component.stress * efficiency;

                            maxInputSpeed = Math.max(maxInputSpeed, effectiveSpeed);
                            maxInputStress = Math.max(maxInputStress, effectiveStress);
                            hasValidSource = true;
                            distanceFromSource = Math.min(distanceFromSource, (int)distance + 1);
                        }
                    }
                }
            }

            // 更新旋转速度和应力
            float oldSpeed = rotationSpeed;
            float oldStress = stress;

            if (hasValidSource) {
                // 应用基于距离的效率损失
                float efficiency = Math.max(0.1f, 1.0f - (distanceFromSource * EFFICIENCY_LOSS_PER_BLOCK));
                rotationSpeed = maxInputSpeed * efficiency;
                stress = maxInputStress * efficiency;
            } else {
                // 如果没有有效动力源，清除旋转速度和应力
                rotationSpeed = 0f;
                stress = 0f;
            }

            // 仅在值变化时更新
            if (Math.abs(oldSpeed - rotationSpeed) > SPEED_THRESHOLD || Math.abs(oldStress - stress) > SPEED_THRESHOLD) {
                notifyNeighborsNeedUpdate();
            }
        }

        /**
         * 检查是否有通往动力源的有效路径
         * 使用优化的深度优先搜索算法和缓存
         */
        private boolean hasValidPathToSource(MechanicalComponentBuild start) {
            if (start.isSource) return true;

            // 使用缓存避免重复计算
            int currentFrame = (int)Time.time;
            if (lastPathCheckFrame == currentFrame && hasPathToSourceCache != null) {
                return hasPathToSourceCache;
            }

            lastPathCheckFrame = currentFrame;

            // 清空访问记录
            visitedSet.clear();

            // 执行搜索
            hasPathToSourceCache = hasValidPathToSourceRecursive(start, 0);
            return hasPathToSourceCache;
        }

        /**
         * 获取到动力源的距离
         */
        private float getDistanceToSource(MechanicalComponentBuild start) {
            if (start.isSource) return 0;

            // 清空访问记录
            visitedSet.clear();

            // 执行搜索并返回距离
            return getDistanceToSourceRecursive(start, 0);
        }

        private boolean hasValidPathToSourceRecursive(MechanicalComponentBuild current, int depth) {
            // 检查深度限制
            if (depth > MAX_SEARCH_DEPTH) return false;

            // 如果是动力源，返回true
            if (current.isSource) return true;

            // 使用位置ID标记已访问的方块
            int posId = current.pos();
            if (visitedSet.contains(posId)) return false;
            visitedSet.add(posId);

            // 检查所有邻居
            for (int i = 0; i < 4; i++) {
                Building other = current.nearby(i);
                if (other instanceof MechanicalComponentBuild component) {
                    // 递归检查
                    if (hasValidPathToSourceRecursive(component, depth + 1)) {
                        return true;
                    }
                }
            }

            return false;
        }

        private float getDistanceToSourceRecursive(MechanicalComponentBuild current, int depth) {
            // 检查深度限制
            if (depth > MAX_SEARCH_DEPTH) return Float.MAX_VALUE;

            // 如果是动力源，返回距离
            if (current.isSource) return depth;

            // 使用位置ID标记已访问的方块
            int posId = current.pos();
            if (visitedSet.contains(posId)) return Float.MAX_VALUE;
            visitedSet.add(posId);

            float minDistance = Float.MAX_VALUE;

            // 检查所有邻居
            for (int i = 0; i < 4; i++) {
                Building other = current.nearby(i);
                if (other instanceof MechanicalComponentBuild component) {
                    // 递归检查
                    float distance = getDistanceToSourceRecursive(component, depth + 1);
                    minDistance = Math.min(minDistance, distance);
                }
            }

            return minDistance;
        }

        /**
         * 通知邻居需要更新
         * 减少不必要的更新传播
         */
        private void notifyNeighborsNeedUpdate() {
            for (MechanicalComponentBuild component : neighbors) {
                if (component != null) {
                    component.needsUpdate = true;
                }
            }
        }

        @Override
        public void draw() {
            super.draw();
            // 基础组件不显示旋转动画
        }

        @Override
        public void display(Table table) {
            super.display(table);

            // 创建可更新的应力显示标签
            var stressLabel = table.add("[accent]应力: [white]" + (int)stress + " us").get();
            table.row();

            // 创建可更新的转速显示标签
            var speedLabel = table.add("[accent]转速: [white]" + (int)rotationSpeed + " bpm").get();

            // 添加更新任务，每帧更新显示值
            Time.runTask(0f, () -> {
                // 使用定时器持续更新UI显示
                Timer.schedule(() -> {
                    if (this.isAdded() && stressLabel != null && speedLabel != null) {
                        stressLabel.setText("[accent]应力: [white]" + (int)stress + " us");
                        speedLabel.setText("[accent]转速: [white]" + (int)rotationSpeed + " bpm");
                    }
                }, 0, UI_UPDATE_INTERVAL); // 使用优化的更新间隔
            });
        }

        protected void handleMechanicalOperation() {
            // 由子类实现
        }

        /**
         * 当方块被破坏时通知邻居更新
         */
        @Override
        public void onRemoved() {
            super.onRemoved();
            notifyNeighborsNeedUpdate();
        }

        /**
         * 当方块被放置时通知邻居更新
         */
        @Override
        public void onProximityAdded() {
            super.onProximityAdded();
            notifyNeighborsNeedUpdate();
        }
    }

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
    public static class TransmissionBoxBuild extends MechanicalComponentBuild {
        // 网络相关属性
        private int networkId = -1; // 所属网络ID，-1表示未分配网络
        private boolean isNetworkMaster = false; // 是否为网络主节点（负责计算）
        private static int nextNetworkId = 0; // 下一个可用的网络ID
        private static final IntSet visitedSet = new IntSet(); // 用于网络搜索的访问记录

        // 网络值缓存
        private float networkSpeed = 0f; // 网络共享转速
        private float networkStress = 0f; // 网络共享应力
        private boolean needsNetworkUpdate = true; // 是否需要更新网络值

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
            } else {
                // 未分配网络的传动箱，尝试加入网络或成为新网络的主节点
                findOrCreateNetwork();
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

            // 收集网络中的所有传动箱
            java.util.List<TransmissionBoxBuild> networkBoxes = new java.util.ArrayList<>();
            collectNetworkBoxes(this, networkBoxes);

            // 计算网络值
            for (TransmissionBoxBuild box : networkBoxes) {
                // 从邻居获取输入
                float localMaxSpeed = 0f;
                float localTotalStress = 0f;

                for (int i = 0; i < 4; i++) {
                    Building other = box.nearby(i);
                    if (other instanceof MechanicalComponentBuild component && !(other instanceof TransmissionBoxBuild)) {
                        // 只考虑非传动箱的机械组件作为输入
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
            if (current != this) {
                current.networkSpeed = this.networkSpeed;
                current.networkStress = this.networkStress;
                current.rotationSpeed = this.networkSpeed;
                current.stress = this.networkStress;
                current.needsNetworkUpdate = false; // 避免重复更新
            }

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
            super.display(table);

            // 创建可更新的应力显示标签
            var stressLabel = table.add("[accent]应力: [white]" + (int)stress + " us").width(160).get();
            table.row();
            
            // 创建可更新的转速显示标签
            var speedLabel = table.add("[accent]转速: [white]" + (int)rotationSpeed + " rpm").width(160).get();
            table.row();
            
            // 显示网络信息
            if (networkId != -1) {
                var networkLabel = table.add("[accent]网络ID: [white]" + networkId).width(160).get();
                table.row();
                var masterLabel = table.add("[accent]主节点: [white]" + (isNetworkMaster ? "是" : "否")).width(160).get();
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

    /**
     * 应力源实现 - 优化版
     * 功能特性：
     * 1. 可配置的转速，支持正反转
     * 2. 平滑的转速变化，避免突变
     * 3. 作为动力源，向连接的机械组件提供动力
     */
    public static class StressSourceBuild extends MechanicalComponentBuild {
        private float targetSpeed = 0f;          // 目标转速
        // 使用静态常量ACCELERATION替代实例变量，节省内存
        private static final float ACCELERATION = DEFAULT_ACCELERATION;
        private float drillSpeedMultiplier = 1.0f; // 钻速倍率，随时间增长
        private static final float DRILL_SPEED_GROWTH_RATE = 0.001f; // 钻速增长率

        @Override
        public void update() {
            super.update();

            // 标记为动力源
            isSource = true;
            
            // 设置应力源为无限应力
            stress = Float.MAX_VALUE;

            // 平滑调整到目标速度
            adjustSpeed();
            
            // 增长转速倍率（内部使用）
            if (rotationSpeed > 0) {
                drillSpeedMultiplier += DRILL_SPEED_GROWTH_RATE * Time.delta;
                drillSpeedMultiplier = Math.min(drillSpeedMultiplier, 10.0f); // 限制最大倍率为10
            }
        }

        /**
         * 平滑调整速度
         */
        private void adjustSpeed() {
            float speedDifference = targetSpeed - rotationSpeed;
            if (Math.abs(speedDifference) > SPEED_THRESHOLD) {
                rotationSpeed += speedDifference * ACCELERATION * Time.delta / 60f;
                needsUpdate = true; // 速度变化时通知邻居更新
            } else if (rotationSpeed != targetSpeed) {
                rotationSpeed = targetSpeed;
                needsUpdate = true;
            }
        }

        /**
         * 设置目标速度
         */
        public void setTargetSpeed(float speed) {
            if (targetSpeed != speed) {
                targetSpeed = speed;
                needsUpdate = true;
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.slider(-MAX_SPEED, MAX_SPEED, 1f, targetSpeed, this::configure).row();
            

        }

        @Override
        public Float config() {
            return targetSpeed;
        }
        
        @Override
        public void display(Table table) {
            super.display(table);

            // 创建可更新的应力显示标签，确保与转速对齐
            var stressLabel = table.add("[accent]应力: [white]∞ us").width(160).get();
            table.row();
            
            // 创建可更新的转速显示标签
            var speedLabel = table.add("[accent]转速: [white]" + (int)rotationSpeed + " rpm").width(160).get();
            table.row();

            // 添加更新任务，每帧更新显示值
            Time.runTask(0f, () -> {
                // 使用定时器持续更新UI显示
                Timer.schedule(() -> {
                    if (this.isAdded() && stressLabel != null && speedLabel != null) {
                        stressLabel.setText("[accent]应力: [white]∞ us");
                        speedLabel.setText("[accent]转速: [white]" + (int)rotationSpeed + " rpm");
                    }
                }, 0, UI_UPDATE_INTERVAL); // 使用优化的更新间隔
            });
        }
    }
}
