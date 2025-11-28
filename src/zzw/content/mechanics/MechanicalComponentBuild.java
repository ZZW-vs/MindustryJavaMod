package zzw.content.mechanics;

import arc.scene.ui.layout.Table;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.gen.Building;

/**
 * 机械组件基类
 * 实现应力和转速传输机制
 */
public class MechanicalComponentBuild extends Building {
    // 机械属性
    public float rotationSpeed = 0f;  // 当前转速 (RPM)
    public float stress = 0f;         // 当前应力 (单位应力)
    public boolean isSource = false;   // 是否为动力源
    public boolean isSink = false;     // 是否为终端用户

    // 组件特性
    // 移除最大承受应力限制，允许无限应力
    public float stressImpact = 1f;    // 应力影响系数
    // 移除转速比例，简化系统

    // 网络计算相关
    private int lastUpdateFrame = -1;
    private static final IntSet visitedSet = new IntSet();
    private static final Seq<MechanicalComponentBuild> networkComponents = new Seq<>();

    // 缓存相关
    private final MechanicalComponentBuild[] neighbors = new MechanicalComponentBuild[4];
    private int lastCacheFrame = -1;
    protected boolean needsUpdate = true;

    // 路径查找优化
    private int lastPathCheckFrame = -1;
    private Boolean hasPathToSourceCache;

    // 常量定义
    protected static final float SPEED_THRESHOLD = 0.01f;
    private static final int MAX_NETWORK_SIZE = 100;              // 最大网络大小
    private static final float EFFICIENCY_LOSS_PER_BLOCK = 0.05f;  // 每格效率损失
    private static final int MAX_SEARCH_DEPTH = 15;               // 最大搜索深度

    @Override
    public void update() {
        // 每隔一段时间更新网络
        int currentFrame = (int)Time.time;
        if (currentFrame - lastUpdateFrame > 10) {
            lastUpdateFrame = currentFrame;
            updateNetwork();
        }

        // 移除应力过载检测，允许无限应力

        // 如果是终端用户且有动力，执行操作
        if (isSink && rotationSpeed > SPEED_THRESHOLD) {
            handleMechanicalOperation();
        }
    }

    /**
     * 更新整个机械网络
     */
    private void updateNetwork() {
        // 重置网络
        networkComponents.clear();
        visitedSet.clear();

        // 收集所有网络组件
        collectNetworkComponents(this);

        // 计算网络状态
        if (!networkComponents.isEmpty()) {
            calculateNetworkState();
        }
    }

    /**
     * 递归收集网络中的所有组件
     */
    private void collectNetworkComponents(MechanicalComponentBuild component) {
        if (component == null) return;

        int posId = component.pos();
        if (visitedSet.contains(posId)) return;
        visitedSet.add(posId);

        // 添加到网络
        networkComponents.add(component);

        // 防止网络过大
        if (networkComponents.size >= MAX_NETWORK_SIZE) return;

        // 递归处理邻居
        for (int i = 0; i < 4; i++) {
            Building other = component.nearby(i);
            if (other instanceof MechanicalComponentBuild mechComponent) {
                collectNetworkComponents(mechComponent);
            }
        }
    }

    /**
     * 计算网络状态
     */
    private void calculateNetworkState() {
        float sourceSpeed = 0f, sourceStress = 0f;
        boolean hasValidSource = false;
        boolean hasSource = false;
        int distanceFromSource = Integer.MAX_VALUE;

        // 更新邻居缓存
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
            if (component == null) continue;

            if (component.isSource) {
                // 直接使用动力源的值，而不是取最大值
                sourceSpeed = component.rotationSpeed;
                sourceStress = component.stress;
                hasValidSource = true;
                hasSource = true;
                distanceFromSource = 1;
            } else if (component.rotationSpeed > SPEED_THRESHOLD && hasValidPathToSource(component)) {
                // 如果没有动力源，使用非动力源邻居的值
                if (!hasSource) {
                    float distance = getDistanceToSource(component);
                    float efficiency = Math.max(0.1f, 1.0f - (distance * EFFICIENCY_LOSS_PER_BLOCK));

                    sourceSpeed = Math.max(sourceSpeed, component.rotationSpeed * efficiency);
                    sourceStress = Math.max(sourceStress, component.stress * efficiency);
                    hasValidSource = true;
                    distanceFromSource = Math.min(distanceFromSource, (int)distance + 1);
                }
            }
        }

        // 更新旋转速度和应力
        float oldSpeed = rotationSpeed, oldStress = stress;

        if (hasValidSource) {
            float efficiency = Math.max(0.1f, 1.0f - (distanceFromSource * EFFICIENCY_LOSS_PER_BLOCK));
            rotationSpeed = sourceSpeed * efficiency;
            stress = sourceStress * efficiency;
        } else {
            rotationSpeed = stress = 0f;
        }

        // 仅在值变化时更新
        if (Math.abs(oldSpeed - rotationSpeed) > SPEED_THRESHOLD ||
            Math.abs(oldStress - stress) > SPEED_THRESHOLD) {
            notifyNeighborsNeedUpdate();
        }
    }

    /**
     * 检查是否有通往动力源的有效路径
     */
    private boolean hasValidPathToSource(MechanicalComponentBuild start) {
        if (start.isSource) return true;

        int currentFrame = (int)Time.time;
        if (lastPathCheckFrame == currentFrame && hasPathToSourceCache != null) {
            return hasPathToSourceCache;
        }

        lastPathCheckFrame = currentFrame;
        visitedSet.clear();
        hasPathToSourceCache = hasValidPathToSourceRecursive(start, 0);
        return hasPathToSourceCache;
    }

    /**
     * 获取到动力源的距离
     */
    private float getDistanceToSource(MechanicalComponentBuild start) {
        if (start.isSource) return 0;
        visitedSet.clear();
        return getDistanceToSourceRecursive(start, 0);
    }

    private boolean hasValidPathToSourceRecursive(MechanicalComponentBuild current, int depth) {
        if (depth > MAX_SEARCH_DEPTH || current.isSource) return current.isSource;

        int posId = current.pos();
        if (visitedSet.contains(posId)) return false;
        visitedSet.add(posId);

        for (int i = 0; i < 4; i++) {
            Building other = current.nearby(i);
            if (other instanceof MechanicalComponentBuild component &&
                hasValidPathToSourceRecursive(component, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private float getDistanceToSourceRecursive(MechanicalComponentBuild current, int depth) {
        if (depth > MAX_SEARCH_DEPTH) return Float.MAX_VALUE;
        if (current.isSource) return depth;

        int posId = current.pos();
        if (visitedSet.contains(posId)) return Float.MAX_VALUE;
        visitedSet.add(posId);

        float minDistance = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            Building other = current.nearby(i);
            if (other instanceof MechanicalComponentBuild component) {
                minDistance = Math.min(minDistance, getDistanceToSourceRecursive(component, depth + 1));
            }
        }
        return minDistance;
    }

    /**
     * 通知邻居需要更新
     */
    protected void notifyNeighborsNeedUpdate() {
        for (MechanicalComponentBuild component : neighbors) {
            if (component != null) component.needsUpdate = true;
        }
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

    protected void handleMechanicalOperation() {
        // 由子类实现
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        notifyNeighborsNeedUpdate();
    }

    @Override
    public void onProximityAdded() {
        super.onProximityAdded();
        notifyNeighborsNeedUpdate();
    }
}
