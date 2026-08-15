package zzw.content.graphs;

import zzw.content.modules.GraphModules;

/** 图建筑接口 (PU132 unity.world.blocks.GraphBuildBase 移植) */
public interface GraphBuildBase{
    GraphModules gms();

    default zzw.content.modules.GraphTorqueModule torque(){
        return gms().getGraphConnector(zzw.content.graphs.GraphType.torque);
    }

    default zzw.content.modules.GraphHeatModule heat(){
        return gms().getGraphConnector(zzw.content.graphs.GraphType.heat);
    }

    default zzw.content.modules.GraphCrucibleModule crucible(){
        return gms().getGraphConnector(zzw.content.graphs.GraphType.crucible);
    }

    default zzw.content.modules.GraphFluxModule flux(){
        return gms().getGraphConnector(zzw.content.graphs.GraphType.flux);
    }
}
