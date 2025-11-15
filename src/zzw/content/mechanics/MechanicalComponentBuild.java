package zzw.content.mechanics;

import arc.scene.ui.layout.Table;
import arc.struct.IntSet;
import arc.util.Time;
import arc.util.Timer;
import mindustry.gen.Building;

/**
 * 机械组件基类 - 优化版
 * 使用缓存和批量处理提高性能
 */
public class MechanicalComponentBuild extends Building {
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

    // 常量定义
    private static final float SPEED_THRESHOLD = 0.01f;              // 转速阈值
    private static final float EFFICIENCY_LOSS_PER_BLOCK = 0.05f;    // 每个传动箱的效率损失
    private static final int MAX_SEARCH_DEPTH = 15;                  // 最大搜索深度
    private static final float UI_UPDATE_INTERVAL = 1/30f;            // UI更新间隔（30fps）

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
