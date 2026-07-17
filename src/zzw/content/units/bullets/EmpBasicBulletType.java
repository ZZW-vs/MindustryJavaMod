package zzw.content.units.bullets;

import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.struct.IntSet;
import arc.struct.IntSeq;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.world.Edges;
import mindustry.world.blocks.power.PowerGraph;

import static mindustry.Vars.*;

/**
 * EMP 基础子弹类型 (移植 PU_V8 EmpBasicBulletType + Emp)
 *
 * 功能:
 * - 命中后对范围内电力建筑造成 EMP 效果:
 *   1. 清空电池电量 (useBatteries)
 *   2. 停止发电机产能 (productionEfficiency=0)
 *   3. 重置需要电力的炮台 reload
 *   4. 禁用建筑 (enabled=false, 持续 duration)
 * - 可选: 沿电网传播 (powerGridIteration 次迭代)
 * - 可选: 断开电力连接 (empDisconnectRange 范围内)
 * - 可选: 损坏逻辑处理器 (empLogicDamage>0 时篡改 logic/memory 代码)
 * - 命中产生冲击波特效 (empRange/empDisconnectRange/empMaxRange 三个)
 *
 * 简化: 不完全还原 PU_V8 Emp.handleBuilding 的 ImpactReactor.warmup 逻辑
 *       (ImpactReactorBuild 在 v158 中为包私有, 跨包无法访问)
 *
 * 参考: PU_V8 main/src/unity/entities/bullet/energy/EmpBasicBulletType.java
 *       PU_V8 main/src/unity/entities/Emp.java
 */
public class EmpBasicBulletType extends BasicBulletType {
    /** EMP 影响电力建筑的范围 (起始扫描) */
    public float empRange = 100f;
    /** 沿电网传播扫描的最大范围 */
    public float empMaxRange = 470f;
    /** EMP 禁用持续时间 (tick) */
    public float empDuration = 120f;
    /** 断开电力连接的范围 (0=不断开) */
    public float empDisconnectRange = 0f;
    /** 逻辑处理器损坏强度 (>0 时篡改 logic 代码) */
    public float empLogicDamage = 0f;
    /** 篡改 logic 指令数量 */
    public int empLogicInstructions = 10;
    /** 电池电量损失值 */
    public float empBatteryDamage = 7000f;
    /** 沿电网传播的迭代次数 */
    public int powerGridIteration = 7;

    public EmpBasicBulletType(float speed, float damage) {
        this(speed, damage, "create-electric-shell");
        trailLength = 7;
    }

    public EmpBasicBulletType(float speed, float damage, String sprite) {
        super(speed, damage, sprite);
        trailLength = 7;
    }

    @Override
    public void hit(Bullet b, float x, float y) {
        super.hit(b, x, y);
        boolean[] hitResults = hitTile(x, y, b.team, empRange, empDuration, empBatteryDamage,
                empLogicDamage, empLogicInstructions, empDisconnectRange, empMaxRange, powerGridIteration);
        boolean hitPowerGrid = hitResults[0];
        boolean hitDisconnect = hitResults[1];

        // 冲击波特效 (与 PU_V8 UnityFx.empShockwave 等价: 简化为 rings 的 laser 圆环)
        empShockwaveEffect(b.x, b.y, empRange);
        if (hitDisconnect) empShockwaveEffect(b.x, b.y, empDisconnectRange);
        if (hitPowerGrid) empShockwaveEffect(b.x, b.y, empMaxRange);
    }

    /** 简化的 empShockwave 特效: 用 Fx.circle 圆环替代 PU_V8 自定义特效 */
    private void empShockwaveEffect(float x, float y, float range) {
        if (range <= 0f) return;
        Fx.pointHit.at(x, y, range, Pal.surge);
        Fx.spawnShockwave.at(x, y, range, Pal.surge);
    }

    /**
     * EMP 命中处理 (移植 PU_V8 Emp.hitTile)
     * @return [0]=hitPowerGrid, [1]=hitDisconnect
     */
    public static boolean[] hitTile(float x, float y, Team team, float validRange, float duration,
                                     float amount, float logicIntensity, int logicInstructions,
                                     float disconnectRange, float scanRange, int scans) {
        IntSet collided = new IntSet(409);
        ObjectSet<PowerGraph> graphs = new ObjectSet<>();
        Seq<Building> last = new Seq<>(), next = new Seq<>();
        // ★ 用单元素数组包装, 让 lambda 可修改
        boolean[] hit = {false};
        boolean[] hitPowerGrid = {false};
        boolean[] hitDisconnect = {false};

        if (validRange > 0f) {
            indexer.eachBlock(null, x, y, validRange, b -> b.team != team && !collided.contains(b.pos()) && b.block.hasPower, building -> {
                if (building.power != null) {
                    if (graphs.add(building.power.graph)) {
                        building.power.graph.useBatteries(amount);
                        handleBuilding(building, duration);
                        last.add(building);
                        collided.add(building.pos());
                        for (int i = 0; i < scans; i++) {
                            for (Building b : last) {
                                if (b.power != null) {
                                    IntSeq links = b.power.links;
                                    Point2[] nearby = Edges.getEdges(b.block.size);
                                    for (Point2 point : nearby) {
                                        Building other = world.build(b.tile.x + point.x, b.tile.y + point.y);
                                        if (other != null && other.block != null && other.block.hasPower && other.within(x, y, scanRange) && collided.add(other.pos())) {
                                            next.add(other);
                                            handleBuilding(other, duration);
                                        }
                                    }
                                    for (int j = 0; j < links.size; j++) {
                                        int pos = links.get(j);
                                        Building other = world.build(pos);
                                        if (other != null && other.within(x, y, scanRange) && collided.add(other.pos())) {
                                            next.add(other);
                                            handleBuilding(other, duration);
                                        }
                                    }
                                }
                            }
                            last.set(next);
                            next.clear();
                        }
                    }
                    hitPowerGrid[0] = true;
                    hit[0] = true;
                }
            });
        }
        last.clear();
        graphs.clear();

        if (disconnectRange > 0f && (hit[0] || (logicIntensity > 0f && logicInstructions > 0))) {
            indexer.eachBlock(null, x, y, disconnectRange, b -> b.team != team, building -> {
                if (((building.block.hasPower || building.block.outputsPower) && building.power != null && hit[0])) {
                    for (int i = 0; i < building.power.links.size; i++) {
                        int p = building.power.links.get(i);
                        Building s = world.build(p);
                        if (s != null && s.power != null) {
                            s.power.links.removeValue(building.pos());
                            last.add(s);
                        }
                    }
                    building.power.links.clear();
                    PowerGraph origin = new PowerGraph();
                    origin.reflow(building);
                    graphs.add(origin);
                    for (Building build : last) {
                        if (!graphs.contains(build.power.graph)) {
                            PowerGraph n = new PowerGraph();
                            n.reflow(build);
                            graphs.add(n);
                        }
                    }
                    last.clear();
                    graphs.clear();

                    hitDisconnect[0] = true;
                }
                // 损坏逻辑处理器代码 (简化版: 仅随机篡改 memory, 不篡改 logic 代码)
                if (logicIntensity > 0f && logicInstructions > 0) {
                    // v158 MemoryBuild.memory: 双精度数组, 通过 instanceof 判断访问
                    if (building instanceof mindustry.world.blocks.logic.MemoryBlock.MemoryBuild mb && mb.memory != null && mb.memory.length > 0) {
                        for (int i = 0; i < logicInstructions; i++) {
                            int index = Mathf.random(0, mb.memory.length - 1);
                            mb.memory[index] += Mathf.range(logicIntensity);
                        }
                        hitDisconnect[0] = true;
                    }
                    // v158 LogicBuild: 篡改代码 (简化版, 仅随机修改数字常量)
                    if (building instanceof mindustry.world.blocks.logic.LogicBlock.LogicBuild lb) {
                        corruptLogicCode(lb, logicIntensity, logicInstructions);
                        hitDisconnect[0] = true;
                    }
                }
            });
        }
        graphs.clear();
        last.clear();
        next.clear();
        collided.clear();

        return new boolean[]{hitPowerGrid[0], hitDisconnect[0]};
    }

    /** 简化版的 logic 代码篡改 (仅随机修改数字常量) */
    private static void corruptLogicCode(mindustry.world.blocks.logic.LogicBlock.LogicBuild lb, float intensity, int instructions) {
        StringBuilder build = new StringBuilder();
        String[] lines = lb.code.split("\n");
        for (int i = 0; i < instructions; i++) {
            int index = Mathf.random(0, lines.length - 1);
            String[] line = lines[index].split("\\s+");
            for (int j = 0; j < line.length; j++) {
                String s = line[j];
                if (Strings.canParseFloat(s)) {
                    float par = Strings.parseFloat(s, 0f);
                    par += Mathf.range(intensity);
                    line[j] = par + "";
                }
            }
            StringBuilder builder = new StringBuilder();
            for (int j = 0; j < line.length; j++) {
                builder.append(line[j]);
                if (j < line.length - 1) builder.append(" ");
            }
            lines[index] = builder.toString();
        }
        for (int i = 0; i < lines.length; i++) {
            build.append(lines[i]);
            if (i < lines.length - 1) build.append("\n");
        }
        lb.code = build.toString();
        lb.updateCode(lb.code);
    }

    /**
     * 处理 EMP 命中的建筑 (移植 PU_V8 Emp.handleBuilding)
     * 简化: 不处理 ImpactReactor.warmup (包私有, 无法访问)
     *       v158 无 enabledControlTime 字段 (PU_V8 自定义), 改用 Time.run 延迟恢复 enabled
     */
    public static void handleBuilding(Building build, float duration) {
        if (!build.block.hasPower) return;
        // ★ v158 GeneratorBuild.productionEfficiency 字段存在, 可访问
        if (build instanceof mindustry.world.blocks.power.PowerGenerator.GeneratorBuild gb) {
            gb.productionEfficiency = 0f;
        }
        // ★ 重置需要电力的炮台 reloadCounter (ReloadTurretBuild 字段, 不是 ReloadTurret.reload)
        if (build instanceof mindustry.world.blocks.defense.turrets.ReloadTurret.ReloadTurretBuild rtb) {
            rtb.reloadCounter = 0f;
        }
        // ★ 禁用建筑 (v158 无 enabledControlTime, 用 Time.run 延迟恢复)
        build.enabled = false;
        arc.util.Time.run(duration, () -> {
            if (build.isAdded()) build.enabled = true;
        });
    }
}
