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
    }

    public EmpBasicBulletType(float speed, float damage, String sprite) {
        super(speed, damage, sprite);
        trailLength = 7;
        // ★ EMP 弹是范围效果弹: 飞完全程自然消散时也在终点爆发 EMP + 光圈
        //   (PU 原版观感 —— 子弹最终"变成一个光圈"消散;
        //   v155 despawnHit 默认 false, 消散时完全静默)
        despawnHit = true;
    }

    @Override
    public void hit(Bullet b, float x, float y) {
        hit(b, x, y, true);
    }

    /**
     * ★ EMP 逻辑必须覆写 4 参版本: 子弹飞完全程自然消散时
     * BulletType.despawned() 直接调用 hit(b, x, y, false) (4 参),
     * 不经过 3 参 hit(b, x, y) —— 之前只覆写 3 参版本导致消散时
     * EMP 逻辑与 empShockwave 光圈 (显示影响范围的扩散圆环) 完全不触发。
     */
    @Override
    public void hit(Bullet b, float x, float y, boolean createFrags) {
        super.hit(b, x, y, createFrags);

        // ★ 冲击波特效先于 EMP 逻辑执行: hitTile 扫描电力网络较重,
        //   若中途抛异常也不吞掉原版 empShockwave 光圈 (子弹命中终点的蓝色扩散圆环)
        if (empRange > 0f) zzw.content.graphics.UnityFx.empShockwave.at(b.x, b.y, empRange);

        boolean[] hitResults = {false, false};
        try {
            hitResults = hitTile(x, y, b.team, empRange, empDuration, empBatteryDamage,
                    empLogicDamage, empLogicInstructions, empDisconnectRange, empMaxRange, powerGridIteration);
        } catch (Exception e) {
            arc.util.Log.err("EMP hitTile error", e);
        }
        boolean hitPowerGrid = hitResults[0];
        boolean hitDisconnect = hitResults[1];

        // 命中电网/断连的额外大范围光圈 (PU_V8 原版行为)
        if (hitDisconnect && empDisconnectRange > 0f) zzw.content.graphics.UnityFx.empShockwave.at(b.x, b.y, empDisconnectRange);
        if (hitPowerGrid && empMaxRange > 0f) zzw.content.graphics.UnityFx.empShockwave.at(b.x, b.y, empMaxRange);
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
                    // ★ 记忆体数据篡改 (兼容 158~160)
                    //   158/159: MemoryBuild.memory 是 public double[];
                    //   160  起: 改为 private double[] numberMemory + private Object[] objectMemory,
                    //           直接访问会编译失败, 因此统一用反射读取 (见 getMemoryArray)
                    if (building instanceof mindustry.world.blocks.logic.MemoryBlock.MemoryBuild) {
                        if (corruptMemory(building, logicIntensity, logicInstructions)) {
                            hitDisconnect[0] = true;
                        }
                    }
                    // LogicBuild: 篡改代码 (简化版, 仅随机修改数字常量)
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

    /** 缓存反射到的"数字记忆体数组"字段 (160+ 为 numberMemory, 158/159 为 memory) */
    private static java.lang.reflect.Field memoryField;
    /** 是否已经尝试过解析字段 (避免每次命中都做一次 failed 查找) */
    private static boolean memoryFieldResolved = false;

    /**
     * 随机篡改逻辑处理器的记忆体数字 (EMP 效果)
     * <p>
     * ★ 版本差异说明:
     * <ul>
     *   <li>158/159: MemoryBlock.MemoryBuild 有 {@code public double[] memory},
     *       可直接访问;</li>
     *   <li>160 起: 记忆体改为 {@code private double[] numberMemory} +
     *       {@code private Object[] objectMemory} (支持存对象), 字段变私有,
     *       直接访问会编译失败。</li>
     * </ul>
     * 因此这里统一走反射, 按新→旧顺序查找字段名, 两个版本都能工作。
     *
     * @return 是否真的篡改到了数据 (true 表示命中逻辑处理器)
     */
    private static boolean corruptMemory(Building building, float logicIntensity, int logicInstructions) {
        double[] mem = getMemoryArray(building);
        if (mem == null || mem.length == 0) return false;
        for (int i = 0; i < logicInstructions; i++) {
            int index = Mathf.random(0, mem.length - 1);
            mem[index] += Mathf.range(logicIntensity);
        }
        return true;
    }

    /** 反射取出记忆体的 double[] (兼容 160 的 numberMemory 与 158/159 的 memory) */
    private static double[] getMemoryArray(Building building) {
        try {
            if (!memoryFieldResolved) {
                memoryFieldResolved = true;
                Class<?> c = building.getClass();
                // 新→旧顺序尝试: 160+ 的 numberMemory, 158/159 的 memory
                for (String name : new String[]{"numberMemory", "memory"}) {
                    try {
                        java.lang.reflect.Field f = c.getDeclaredField(name);
                        if (f.getType() == double[].class) {
                            f.setAccessible(true);
                            memoryField = f;
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                        // 该字段名不存在, 继续尝试下一个
                    }
                }
            }
            if (memoryField == null) return null;
            return (double[]) memoryField.get(building);
        } catch (Throwable t) {
            return null;
        }
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
