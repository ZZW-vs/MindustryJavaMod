package zzw.content.mechanics;

import arc.struct.IntSet;
import arc.struct.Seq;
import mindustry.gen.Building;

/**
 * 机械网络类 - 机械动力风格
 * 实现类似我的世界机械动力的应力和转速传输机制
 * 特性：
 * 1. 应力网络计算：计算整个机械网络的总应力和转速
 * 2. 动力源优先级：多个动力源时使用最高转速
 * 3. 应力过载保护：当应力超过组件承受能力时停止工作
 * 4. 转速转换：不同组件可以转换转速和应力比例
 */
public class MechanicalNetwork {
    // 网络中的所有组件
    private final Seq<MechanicalComponentBuild> components = new Seq<>();
    private final IntSet visitedSet = new IntSet();

    // 常量定义
    private static final float STRESS_OVERLOAD_THRESHOLD = 0.95f; // 应力过载阈值
    private static final int MAX_NETWORK_SIZE = 100;              // 最大网络大小

    /**
     * 更新整个机械网络
     */
    public void updateNetwork(MechanicalComponentBuild starter) {
        // 重置网络
        components.clear();
        visitedSet.clear();

        // 收集所有网络组件
        collectNetworkComponents(starter);

        // 计算网络状态
        if (components.size > 0) {
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
        components.add(component);

        // 防止网络过大
        if (components.size >= MAX_NETWORK_SIZE) return;

        // 递归处理邻居
        for (int i = 0; i < 4; i++) {
            Building other = component.nearby(i);
            if (other instanceof MechanicalComponentBuild) {
                collectNetworkComponents((MechanicalComponentBuild) other);
            }
        }
    }

    /**
     * 计算网络状态
     */
    private void calculateNetworkState() {
        // 找出所有动力源
        Seq<MechanicalComponentBuild> sources = components.select(c -> c.isSource);

        // 如果没有动力源，所有组件停止工作
        if (sources.size == 0) {
            for (MechanicalComponentBuild component : components) {
                component.rotationSpeed = 0;
                component.stress = 0;
            }
            return;
        }

        // 找出最大转速的动力源
        float maxSpeed = 0;
        MechanicalComponentBuild primarySource = null;
        for (MechanicalComponentBuild source : sources) {
            if (source.rotationSpeed > maxSpeed) {
                maxSpeed = source.rotationSpeed;
                primarySource = source;
            }
        }

        // 计算总应力需求
        float totalStressDemand = 0;
        for (MechanicalComponentBuild component : components) {
            if (!component.isSource) {
                totalStressDemand += component.stressImpact;
            }
        }

        // 检查是否过载
        boolean isOverloaded = totalStressDemand > (primarySource != null ? primarySource.maxStress : 0f);

        // 更新所有组件
        for (MechanicalComponentBuild component : components) {
            if (component.isSource) {
                // 动力源保持原有状态
                continue;
            }

            // 计算到动力源的距离
            int distance = getDistanceToSource(component, primarySource);

            // 计算效率损失
            float efficiency = Math.max(0.1f, 1.0f - (distance * 0.05f));

            // 应用转速和应力
            if (isOverloaded) {
                // 过载时降低转速
                component.rotationSpeed = maxSpeed * efficiency * 0.5f;
                component.stress = component.maxStress * STRESS_OVERLOAD_THRESHOLD;
            } else {
                // 正常情况
                component.rotationSpeed = maxSpeed * component.speedRatio * efficiency;
                component.stress = component.stressImpact;
            }
        }
    }

    /**
     * 获取组件到动力源的距离
     */
    private int getDistanceToSource(MechanicalComponentBuild component, MechanicalComponentBuild source) {
        if (component == source) return 0;

        // 使用广度优先搜索计算最短距离
        IntSet visited = new IntSet();
        Seq<MechanicalComponentBuild> currentLevel = new Seq<>();
        Seq<MechanicalComponentBuild> nextLevel = new Seq<>();

        currentLevel.add(component);
        visited.add(component.pos());

        int distance = 0;
        while (currentLevel.size > 0) {
            distance++;
            for (MechanicalComponentBuild current : currentLevel) {
                for (int i = 0; i < 4; i++) {
                    Building other = current.nearby(i);
                    if (other instanceof MechanicalComponentBuild neighbor) {
                        if (neighbor == source) {
                            return distance;
                        }

                        int neighborPos = neighbor.pos();
                        if (!visited.contains(neighborPos)) {
                            visited.add(neighborPos);
                            nextLevel.add(neighbor);
                        }
                    }
                }
            }

            // 交换层级
            Seq<MechanicalComponentBuild> temp = currentLevel;
            currentLevel = nextLevel;
            nextLevel = temp;
            nextLevel.clear();

            // 防止无限循环
            if (distance > 50) break;
        }

        return distance;
    }

    /**
     * 获取网络中的所有组件
     */
    public Seq<MechanicalComponentBuild> getComponents() {
        return components;
    }
}
