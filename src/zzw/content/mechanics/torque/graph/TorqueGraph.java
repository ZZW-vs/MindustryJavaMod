package zzw.content.mechanics.torque.graph;

import arc.util.*;
import zzw.content.mechanics.torque.graphs.*;
import zzw.content.mechanics.torque.modules.*;

//rotGraph
public class TorqueGraph<T extends GraphTorque> extends BaseGraph<GraphTorqueModule<T>, TorqueGraph<T>>{
    public float lastInertia, lastGrossForceApplied, lastNetForceApplied, lastVelocity, lastFrictionCoefficient;
    /** 全网最小非负转速上限 (内部单位); < 0 表示无限制. 由 updateDirect 汇聚. */
    public float lastSpeedLimit = -1f;

    @Override
    public TorqueGraph<T> create(){
        return new TorqueGraph<>();
    }

    @Override
    void copyGraphStatsFrom(TorqueGraph<T> graph){
        lastVelocity = graph.lastVelocity;
    }

    @Override
    boolean canConnect(GraphTorqueModule<T> b1, GraphTorqueModule<T> b2){
        return b1.parent.build.team() == b2.parent.build.team();
    }

    @Override
    void updateOnGraphChanged(){}

    @Override
    void updateGraph(){
        float netForce = lastGrossForceApplied - lastFrictionCoefficient;
        lastNetForceApplied = netForce;
        float acceleration = lastInertia == 0f ? 0f : netForce / lastInertia;
        lastVelocity += acceleration * Time.delta;
        // 调速: 把网络转速压在各调速模块给出的上限 (取最小非负值) 之下.
        // 必须在 updateProps (累计转角) 之前钳制, 否则本帧转角会使用超调后的速度, 显示与转动不一致.
        if(lastSpeedLimit >= 0f && lastVelocity > lastSpeedLimit) lastVelocity = lastSpeedLimit;
        lastVelocity = Math.max(0f, lastVelocity);
        // 防呆: 图合并瞬间可能产生 0/0 导致 NaN, 一旦 NaN 会永久污染转速
        // (Math.max(0f, NaN) 仍是 NaN), 进而让传动杆等绘制整层失效
        if(Float.isNaN(lastVelocity) || Float.isInfinite(lastVelocity)) lastVelocity = 0f;
    }

    @Override
    void updateDirect(){
        float forceApply = 0f;
        float fricCoeff = 0f;
        float iner = 0f;
        float limit = -1f;
        for(var module : connected){//building, GraphTorqueModule
            forceApply += module.force;
            fricCoeff += module.friction();
            iner += module.inertia;
            float l = module.speedLimit();
            if(l >= 0f && (limit < 0f || l < limit)) limit = l;
        }
        lastFrictionCoefficient = fricCoeff;
        lastGrossForceApplied = forceApply;
        lastInertia = iner;
        lastSpeedLimit = limit;
    }

    @Override
    void addMergeStats(GraphTorqueModule<T> module){}

    @Override
    void mergeStats(TorqueGraph<T> graph){
        float momentumA = lastVelocity * lastInertia;
        float mementumB = graph.lastVelocity * graph.lastInertia;
        // 防呆: 两边转动惯量都为 0 时 0/0 会得到 NaN, 需要兜底为 0
        float inertiaSum = lastInertia + graph.lastInertia;
        lastVelocity = inertiaSum == 0f ? 0f : (momentumA + mementumB) / inertiaSum;
    }

    public void injectInertia(float iner){
        float inerSum = lastInertia + iner;
        lastVelocity *= inerSum == 0f ? 0f : lastInertia / inerSum;
    }
}
