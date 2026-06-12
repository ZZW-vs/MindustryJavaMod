package zzw.content.mechanics;

import arc.Events;
import mindustry.game.EventType;
import mindustry.gen.Building;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MechanicalComponentBuild extends Building {
    public float rotationSpeed = 0f;
    public float stress = 0f;
    public boolean isSource = false;

    private int networkId = -1;
    private static int nextNetworkId = 0;
    private boolean needsNetworkUpdate = false;

    protected static final float SPEED_THRESHOLD = 0.01f;
    protected static final float EFFICIENCY_LOSS_PER_BLOCK = 0.05f;
    protected static final float MIN_EFFICIENCY = 0.1f;

    // 全局静态初始化：仅在第一次有组件被创建时注册监听
    private static boolean listenersRegistered = false;
    private static void ensureListeners() {
        if (listenersRegistered) return;
        listenersRegistered = true;
        // 世界加载或重置时清空网络ID，避免跨局数据污染
        Events.on(EventType.ResetEvent.class, e -> {
            nextNetworkId = 0;
        });
    }

    @Override
    public void created() {
        ensureListeners();
        super.created();
        markNetworkForUpdate();
    }

    @Override
    public void update() {
        if (needsNetworkUpdate && tile != null) {
            needsNetworkUpdate = false;
            updateTransmissionNetwork();
        }
    }

    // 使用 BFS 找到连通的机械网络
    private List<MechanicalComponentBuild> findNetwork() {
        List<MechanicalComponentBuild> network = new ArrayList<>();
        Set<MechanicalComponentBuild> visited = new HashSet<>();
        ArrayDeque<MechanicalComponentBuild> queue = new ArrayDeque<>();
        queue.offer(this);
        visited.add(this);

        while (!queue.isEmpty()) {
            MechanicalComponentBuild current = queue.poll();
            network.add(current);

            for (int i = 0; i < 4; i++) {
                Building neighbor = current.nearby(i);
                if (neighbor instanceof MechanicalComponentBuild m && visited.add(m)) {
                    queue.offer(m);
                }
            }
        }
        return network;
    }

    private void updateTransmissionNetwork() {
        List<MechanicalComponentBuild> network = findNetwork();

        // 收集并合并相邻的其他网络ID
        Set<Integer> connectedIds = new HashSet<>();
        for (MechanicalComponentBuild c : network) {
            for (int i = 0; i < 4; i++) {
                Building n = c.nearby(i);
                if (n instanceof MechanicalComponentBuild m && m.networkId != -1) {
                    connectedIds.add(m.networkId);
                }
            }
        }

        // 如果有其他网络，扩展 BFS 包含它们
        if (!connectedIds.isEmpty()) {
            Set<MechanicalComponentBuild> all = new HashSet<>(network);
            ArrayDeque<MechanicalComponentBuild> queue = new ArrayDeque<>(network);
            while (!queue.isEmpty()) {
                MechanicalComponentBuild current = queue.poll();
                for (int i = 0; i < 4; i++) {
                    Building n = current.nearby(i);
                    if (n instanceof MechanicalComponentBuild m && all.add(m)) {
                        queue.offer(m);
                    }
                }
            }
            network = new ArrayList<>(all);
        }

        // 分配统一的网络ID
        int id = nextNetworkId++;
        MechanicalComponentBuild source = null;
        float sourceSpeed = 0f;
        float sourceStress = 0f;
        for (MechanicalComponentBuild c : network) {
            c.networkId = id;
            if (c.isSource) {
                source = c;
                sourceSpeed = Math.max(sourceSpeed, c.rotationSpeed);
                sourceStress = Math.max(sourceStress, c.stress);
            }
        }

        // 同步转速 / 应力
        float outSpeed = source != null ? sourceSpeed : 0f;
        float outStress = source != null ? sourceStress : 0f;
        for (MechanicalComponentBuild c : network) {
            c.rotationSpeed = outSpeed;
            c.stress = outStress;
        }
    }

    @Override
    public void onRemoved() {
        markNetworkForUpdate();
        super.onRemoved();
    }

    @Override
    public void onProximityAdded() {
        markNetworkForUpdate();
        super.onProximityAdded();
    }

    public void markNetworkForUpdate() {
        needsNetworkUpdate = true;
    }
}