package zzw.content.mechanics;

import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.gen.Building;

/**
 * 机械组件基类
 * 实现基础的应力和转速传输机制
 */
public class MechanicalComponentBuild extends Building {
    // 机械属性
    public float rotationSpeed = 0f;  // 当前转速 (RPM)
    public float stress = 0f;         // 当前应力 (单位应力)
    public boolean isSource = false;   // 是否为动力源

    // 缓存相关
    private final MechanicalComponentBuild[] neighbors = new MechanicalComponentBuild[4];
    private int lastCacheFrame = -1;

    // 常量定义
    protected static final float SPEED_THRESHOLD = 0.01f;
    protected static final float EFFICIENCY_LOSS_PER_BLOCK = 0.05f;  // 每格效率损失
    protected static final float MIN_EFFICIENCY = 0.1f; // 最小效率
    private static final float SECONDARY_EFFICIENCY = 0.9f; // 次级效率

    @Override
    public void update() {
        // 更新邻居缓存
        updateNeighborCache();

        // 获取动力源信息
        PowerSourceInfo sourceInfo = findPowerSource();

        // 更新自身值
        if (sourceInfo.hasValidSource) {
            // 转速无损耗，直接使用源转速
            rotationSpeed = sourceInfo.speed;
            
            // 应力有损耗，计算效率损失
            float efficiency = Math.max(MIN_EFFICIENCY, 1.0f - (sourceInfo.distance * EFFICIENCY_LOSS_PER_BLOCK));
            stress = sourceInfo.stress * efficiency;
        } else {
            rotationSpeed = 0f;
            stress = 0f;
        }
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
                info.speed = Math.max(info.speed, component.rotationSpeed * SECONDARY_EFFICIENCY);
                info.stress = Math.max(info.stress, component.stress * SECONDARY_EFFICIENCY);
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
        super.onRemoved();
    }

    @Override
    public void onProximityAdded() {
        super.onProximityAdded();
    }

    /**
     * 机械网络类 - 简化版
     * 实现基础的机械动力传输机制
     */
    public static class MechanicalNetwork {
        // 网络中的所有组件
        private final Seq<MechanicalComponentBuild> components = new Seq<>();
        
        // 常量
        private static final float EFFICIENCY_PER_DISTANCE = 0.05f; // 每单位距离的效率损失
        private static final float MIN_EFFICIENCY = 0.1f; // 最小效率

        /**
         * 更新整个机械网络
         */
        public void updateNetwork(MechanicalComponentBuild starter) {
            // 重置网络
            components.clear();

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

            // 添加到网络
            components.add(component);

            // 递归处理邻居
            for (int i = 0; i < 4; i++) {
                Building other = component.nearby(i);
                if (other instanceof MechanicalComponentBuild mechComponent) {
                    if (!components.contains(mechComponent)) {
                        collectNetworkComponents(mechComponent);
                    }
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
                resetAllComponents();
                return;
            }

            // 找出最大转速的动力源
            PrimarySourceInfo sourceInfo = findPrimarySource(sources);

            // 更新所有组件
            updateAllComponents(sourceInfo);
        }
        
        /**
         * 重置所有组件状态
         */
        private void resetAllComponents() {
            for (MechanicalComponentBuild component : components) {
                component.rotationSpeed = 0;
                component.stress = 0;
            }
        }
        
        /**
         * 查找主要动力源
         */
        private PrimarySourceInfo findPrimarySource(Seq<MechanicalComponentBuild> sources) {
            PrimarySourceInfo info = new PrimarySourceInfo();
            
            for (MechanicalComponentBuild source : sources) {
                if (source.rotationSpeed > info.maxSpeed) {
                    info.maxSpeed = source.rotationSpeed;
                    info.primarySource = source;
                }
            }
            
            return info;
        }
        
        /**
         * 更新所有组件
         */
        private void updateAllComponents(PrimarySourceInfo sourceInfo) {
            for (MechanicalComponentBuild component : components) {
                if (component.isSource) {
                    // 动力源保持原有状态
                    continue;
                }

                // 计算到动力源的距离
                int distance = getDistanceToSource(component, sourceInfo.primarySource);

                // 计算效率损失，仅应用于应力
                float efficiency = Math.max(MIN_EFFICIENCY, 1.0f - (distance * EFFICIENCY_PER_DISTANCE));

                // 转速无损耗，直接使用源转速
                component.rotationSpeed = sourceInfo.maxSpeed;
                if (sourceInfo.primarySource != null) {
                    component.stress = sourceInfo.primarySource.stress * efficiency;
                } else {
                    component.stress = 0f;
                }
            }
        }

        /**
         * 获取组件到动力源的距离
         */
        private int getDistanceToSource(MechanicalComponentBuild component, MechanicalComponentBuild source) {
            if (component == source) return 0;

            // 简单的距离计算
            int dx = Math.abs(component.tileX() - source.tileX());
            int dy = Math.abs(component.tileY() - source.tileY());
            return dx + dy;
        }
        
        /**
         * 主要动力源信息封装类
         */
        private static class PrimarySourceInfo {
            float maxSpeed = 0;
            MechanicalComponentBuild primarySource = null;
        }
    }
}
