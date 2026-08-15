package zzw.content.units.entities;

import zzw.content.units.ZEntityRegister;
import zzw.content.type.WorldUnitType;

import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Tmp;
import arc.util.io.Writes;
import arc.util.io.Reads;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.UnitEntity;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.defense.turrets.BaseTurret.BaseTurretBuild;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;
import mindustry.world.blocks.power.PowerNode.PowerNodeBuild;

/**
 * 世界单位 Entity (PU132 unity.entities.comp.WorldComp 简化移植, 方案A)
 *
 * 设计: extends UnitEntity, 直接继承 UnitEntity 的所有 Unit 接口默认行为
 * - 携带一个子世界 (unitWorld), 子世界中的建筑物跟随单位移动和旋转
 * - setup(): 创建子世界 + 扫描迁移附近建筑物 (原样搬 WorldComp.setup())
 * - update(): 临时切换 Vars.world + 旋转坐标更新建筑物 (原样搬 WorldComp.update())
 * - cwX/cwY/conX/conY: 主世界坐标 ↔ 子世界坐标转换
 * - validPlace/valid: 边界检查
 *
 * 适配 v155.4 改动:
 * - 去掉 @EntityComponent / @Import / @MethodPriority 注解 (PU132 注解处理器依赖)
 * - classId() 重写: 返回 ZEntityRegister 注册的 id (参考 SegmentWormEntity)
 * - super.update() 先调用, 再执行子世界更新 (PU132 中 @MethodPriority(100) 保证晚于物理更新)
 * - TimeReflect: 反射替换 Time.runs 队列, 让建筑物 Time.run 进入单位自己的队列 (同 PU132 原版)
 *
 * 原 PU132 WorldComp 是 mixin 组件 (@EntityComponent), 通过注解处理器织入 UnitEntity;
 * 这里直接继承 UnitEntity, 字段和方法直接写在类里.
 */
public class WorldUnitEntity extends UnitEntity {

    // ===== 静态临时变量 (原 WorldComp 静态字段) =====
    final static Vec2 vec = new Vec2();
    final static Seq<Building> tmp = new Seq<>();
    final static Seq<Runnable> tmpr = new Seq<>();
    final static IntMap<IntSeq> tmpLinks = new IntMap<>(102);
    final static IntSet tmpAdded = new IntSet(102);

    // ===== 实例字段 (原 WorldComp transient 字段) =====
    /** TimeReflect 延迟队列 (建筑物 Time.run 进入此队列, 由 updateDelays 推进) */
    protected transient Seq<arc.util.Time.DelayRun> runs = new Seq<>();

    /** 子世界 (单位携带的独立世界) */
    public transient World unitWorld;
    /** 子世界中的建筑物列表 */
    public transient Seq<Building> buildings = new Seq<>(16);
    /** 子世界中的炮台列表 (用于玩家控制时同步射击) */
    public transient Seq<TurretBuild> turrets = new Seq<>();
    /** 建筑物位置缓存 (update 前保存原始位置, update 后恢复) */
    public transient FloatSeq positions = new FloatSeq();

    /** 工厂方法 (UnitType.constructor 用) */
    public static WorldUnitEntity create() {
        return new WorldUnitEntity();
    }

    /** 返回注册的 classId (绕过 v155.4 的 checkEntityMapping 检查) */
    @Override
    public int classId() {
        return ZEntityRegister.classId(WorldUnitEntity.class);
    }

    // ===== update() (原 WorldComp.update, 去掉 @MethodPriority, 保留 TimeReflect) =====

    @Override
    public void update() {
        // ★ 先执行正常的 Unit 更新 (移动/物理/武器等), 对应 PU132 中 @MethodPriority(100) 晚于默认优先级
        super.update();

        // 子世界未初始化时跳过 (setup() 尚未调用)
        if (unitWorld == null) return;

        float cx = unitWorld.width() * Vars.tilesize / 2f, cy = unitWorld.height() * Vars.tilesize / 2f;
        float r = rotation - 90f;
        positions.clear();

        // ★ TimeReflect: 把 Time.runs 替换为单位自己的队列, 建筑物 Time.run 进入单位队列而非主世界
        TimeReflect.swapRuns(runs);
        World ow = Vars.world;
        Vars.world = unitWorld;

        if (isPlayer()) {
            for (TurretBuild t : turrets) {
                t.logicControlTime = 5f;
                t.logicShooting = isShooting();
                t.targetPos.set(aimX(), aimY());
            }
        }

        for (int i = 0; i < buildings.size; i++) {
            Building b = buildings.get(i);
            positions.add(b.x, b.y);

            if (b instanceof BaseTurretBuild) {
                BaseTurretBuild t = (BaseTurretBuild) b;
                t.rotation += r;
            }

            vec.set(b.x - cx, b.y - cy).rotate(r).add(this);

            b.set(vec);
            b.update();
        }

        // ★ TimeReflect: 推进单位队列中的延迟任务 (建筑物 Time.run 到期执行)
        TimeReflect.updateDelays(runs);

        for (int i = 0; i < buildings.size; i++) {
            Building b = buildings.get(i);
            b.x = positions.get(i * 2);
            b.y = positions.get(i * 2 + 1);

            if (b instanceof BaseTurretBuild) {
                BaseTurretBuild t = (BaseTurretBuild) b;
                t.rotation -= r;
            }
        }

        // ★ TimeReflect: 恢复 Time.runs 为原始主世界队列
        TimeReflect.resetRuns();
        Vars.world = ow;
    }

    // ===== setup() (原样搬 WorldComp.setup(), 创建子世界 + 迁移建筑物) =====

    public void setup() {
        WorldUnitType uType = (WorldUnitType) type;
        int w = uType.worldWidth, h = uType.worldHeight;
        unitWorld = new World();
        unitWorld.tiles = new Tiles(w, h);
        for (int i = 0; i < w * h; i++) {
            unitWorld.tiles.set(i % w, i / w, new UnitTile(i % w, i / w));
        }
        unitWorld.tiles.eachTile(tile -> tile.setFloor(Blocks.metalFloor.asFloor()));

        tmp.clear();
        tmpr.clear();
        TeamData data = team().data();
        if (data.buildingTree != null) {
            Tmp.r1.setCentered(x, y, w * Vars.tilesize, h * Vars.tilesize);
            data.buildingTree.intersect(Tmp.r1, tmp);
        }

        tmpLinks.clear();
        tmpAdded.clear();
        for (Building building : tmp) {
            if (validPlace(building.tile) && tmpAdded.add(building.id)) {
                int tx = conX(building.tile.x);
                int ty = conY(building.tile.y);

                if (building.power != null && building instanceof PowerNodeBuild && !building.power.links.isEmpty()) {
                    IntSeq seq = building.power.links, nseq = new IntSeq();
                    for (int i = 0; i < seq.size; i++) {
                        int pos = seq.get(i);
                        int cx = conX(Point2.x(pos)), cy = conY(Point2.y(pos));
                        if (valid(cx, cy)) {
                            nseq.add(Point2.pack(cx, cy));
                        }
                    }
                    if (!nseq.isEmpty()) {
                        tmpLinks.put(building.id, nseq);
                    }
                }

                tmpr.add(() -> {
                    building.x = cwX(building.x);
                    building.y = cwY(building.y);
                    unitWorld.tile(tx, ty).setBlock(building.block, building.team, building.rotation, () -> building);
                });

                building.tile.remove();
                buildings.add(building);
            }
        }

        World ow = Vars.world;
        Vars.world = unitWorld;

        for (Runnable r : tmpr) {
            r.run();
        }
        for (Building b : buildings) {
            if (b instanceof PowerNodeBuild) {
                IntSeq seq = tmpLinks.get(b.id);
                if (seq != null && b.power != null) {
                    b.power.links.clear();
                    for (int i = 0; i < seq.size; i++) {
                        int pos = seq.get(i);
                        b.configureAny(pos);
                    }
                }
            }
            b.updateProximity();
            if (b instanceof TurretBuild) {
                TurretBuild tb = (TurretBuild) b;
                turrets.add(tb);
            }
        }

        Vars.world = ow;
        tmpr.clear();
        tmp.clear();
        tmpLinks.clear();
    }

    // ===== 坐标转换 (原 WorldComp) =====

    /** 主世界坐标 → 子世界坐标 (float) */
    public float cwX(float x) {
        return (x - this.x) + (unitWorld.width() * Vars.tilesize / 2f);
    }

    /** 主世界坐标 → 子世界坐标 (float) */
    public float cwY(float y) {
        return (y - this.y) + (unitWorld.height() * Vars.tilesize / 2f);
    }

    /** 主世界 tile 坐标 → 子世界 tile 坐标 (int) */
    public int conX(int x) {
        return (x - (int) (this.x / Vars.tilesize)) + (unitWorld.width() / 2) - 1;
    }

    /** 主世界 tile 坐标 → 子世界 tile 坐标 (int) */
    public int conY(int y) {
        return (y - (int) (this.y / Vars.tilesize)) + (unitWorld.height() / 2) - 1;
    }

    /** 检查 tile 上的方块是否能放入子世界 (考虑多方块) */
    public boolean validPlace(Tile tile) {
        Block block = tile.block();
        int offset = -(block.size - 1) / 2;
        int tx = conX(tile.x);
        int ty = conY(tile.y);

        boolean valid = tx >= 0 && tx < unitWorld.width() && ty >= 0 && ty < unitWorld.height();
        if (tile.block().isMultiblock()) {
            for (int dx = 0; dx < block.size; dx++) {
                for (int dy = 0; dy < block.size; dy++) {
                    int wx = dx + offset + tx, wy = dy + offset + ty;
                    valid &= wx >= 0 && wx < unitWorld.width() && wy >= 0 && wy < unitWorld.height();
                }
            }
        }
        return valid;
    }

    /** 检查子世界 tile 坐标是否在范围内 */
    public boolean valid(int x, int y) {
        return x >= 0 && x < unitWorld.width() && y >= 0 && y < unitWorld.height();
    }

    // ===== 鼠标悬停: 查找单位上的建筑 =====

    /**
     * 给定主世界坐标，返回该坐标在单位上对应的建筑（如果有的话）。
     * <p>原理：把主世界坐标转换为子世界坐标，在子世界中查找该位置的 Tile.build。</p>
     *
     * @param worldX 主世界 X 像素坐标
     * @param worldY 主世界 Y 像素坐标
     * @return 该位置上的建筑，或 null
     */
    public Building buildingAt(float worldX, float worldY) {
        if (unitWorld == null) return null;
        // 主世界坐标 → 子世界像素坐标 (反向 cwX/cwY)
        float subX = (worldX - x) + unitWorld.width() * Vars.tilesize / 2f;
        float subY = (worldY - y) + unitWorld.height() * Vars.tilesize / 2f;
        // 反向旋转
        float r = -(rotation - 90f);
        vec.set(subX - unitWorld.width() * Vars.tilesize / 2f, subY - unitWorld.height() * Vars.tilesize / 2f).rotate(r);
        float sx = vec.x + unitWorld.width() * Vars.tilesize / 2f;
        float sy = vec.y + unitWorld.height() * Vars.tilesize / 2f;
        // 子世界像素 → tile 坐标
        int tx = mindustry.core.World.toTile(sx);
        int ty = mindustry.core.World.toTile(sy);
        if (valid(tx, ty)) {
            Tile tile = unitWorld.tile(tx, ty);
            return tile != null ? tile.build : null;
        }
        return null;
    }

    /**
     * 返回子世界中所有建筑的只读列表（用于UI显示状态）。
     */
    public Seq<Building> getBuildings() {
        return buildings;
    }

    /**
     * 保存子世界状态到数据流 (修复重进地图子世界物品消失的问题)
     */
    @Override
    public void write(Writes write) {
        super.write(write);
        
        // 保存子世界尺寸
        write.s(unitWorld != null ? (short) unitWorld.width() : 0);
        write.s(unitWorld != null ? (short) unitWorld.height() : 0);
        
        // 保存建筑物数量
        write.i(buildings.size);
        
        if (unitWorld != null) {
            // 保存每个建筑物的状态
            for (Building building : buildings) {
                // 写入建筑类型、位置、团队、旋转等基本信息
                write.b((byte) Vars.content.indexOf(building.block));
                write.f(building.x);
                write.f(building.y);
                write.b((byte) building.team.id);
                write.b((byte) building.rotation);
                
                // 保存建筑物的特定状态
                write.bool(building.health > 0);
                if (building.health > 0) {
                    write.f(building.health);
                }
                
                // 保存炮台状态
                write.bool(building instanceof TurretBuild);
                if (building instanceof TurretBuild) {
                    write.f(((TurretBuild) building).rotation);
                    write.f(((TurretBuild) building).reload);
                }
                
                // 保存电力节点链接状态
                write.bool(building.power != null && building instanceof PowerNodeBuild);
                if (building.power != null && building instanceof PowerNodeBuild) {
                    write.i(building.power.links.size);
                    for (int link : building.power.links) {
                        write.i(link);
                    }
                }
                
                // 保存建筑物的物品/液体状态
                if (building instanceof mindustry.world.blocks.storage.StorageBlock.StorageBuild) {
                    mindustry.world.blocks.storage.StorageBlock.StorageBuild storage = (mindustry.world.blocks.storage.StorageBlock.StorageBuild) building;
                    write.i(storage.items.total());
                    for (int i = 0; i < storage.items.size; i++) {
                        if (storage.items.get(i) > 0) {
                            write.b((byte) i);
                            write.i(storage.items.get(i));
                        }
                    }
                    write.b((byte) -1); // 结束标记
                } else if (building instanceof mindustry.world.blocks.storage.CoreBlock.CoreBuild) {
                    mindustry.world.blocks.storage.CoreBlock.CoreBuild core = (mindustry.world.blocks.storage.CoreBlock.CoreBuild) building;
                    write.i(core.items.total());
                    for (int i = 0; i < core.items.size; i++) {
                        if (core.items.get(i) > 0) {
                            write.b((byte) i);
                            write.i(core.items.get(i));
                        }
                    }
                    write.b((byte) -1); // 结束标记
                }
            }
        }
    }
    
    /**
     * 从数据流加载子世界状态 (修复重进地图子世界物品消失的问题)
     */
    @Override
    public void read(Reads read, boolean legacy) {
        super.read(read, legacy);
        
        // 读取子世界尺寸
        short width = read.s();
        short height = read.s();
        
        if (width > 0 && height > 0) {
            // 重新创建子世界
            unitWorld = new World();
            unitWorld.tiles = new Tiles(width, height);
            for (int i = 0; i < width * height; i++) {
                unitWorld.tiles.set(i % width, i / height, new UnitTile(i % width, i / height));
            }
            unitWorld.tiles.eachTile(tile -> tile.setFloor(Blocks.metalFloor.asFloor()));
            
            // 读取建筑物数量
            int buildingCount = read.i();
            buildings.clear();
            
            // 重新创建建筑物
            for (int i = 0; i < buildingCount; i++) {
                int blockIndex = read.b() & 0xFF;
                float x = read.f();
                float y = read.f();
                byte teamId = (byte) (read.b() & 0xFF);
                byte rotation = (byte) (read.b() & 0xFF);
                
                Block block = Vars.content.block(blockIndex);
                if (block != null) {
                    // 在子世界中创建建筑
                    int tx = (int) (x / Vars.tilesize);
                    int ty = (int) (y / Vars.tilesize);
                    if (tx >= 0 && tx < width && ty >= 0 && ty < height) {
                        Building building = unitWorld.tile(tx, ty).setBlock(block, Vars.teams.get(teamId), rotation, () -> {
                            Building b = unitWorld.tile(tx, ty).build;
                            // 恢复建筑状态
                            if (read.bool()) {
                                b.health = read.f();
                            }
                            
                            // 恢复炮台状态
                            if (read.bool() && b instanceof TurretBuild) {
                                TurretBuild turret = (TurretBuild) b;
                                turret.rotation = read.f();
                                turret.reload = read.f();
                            }
                            
                            // 恢复电力节点链接
                            if (read.bool() && b.power != null && b instanceof PowerNodeBuild) {
                                PowerNodeBuild node = (PowerNodeBuild) b;
                                int linkCount = read.i();
                                for (int j = 0; j < linkCount; j++) {
                                    node.configureAny(read.i());
                                }
                            }
                            
                            // 恢复存储状态
                            if (b instanceof mindustry.world.blocks.storage.StorageBlock.StorageBuild) {
                                mindustry.world.blocks.storage.StorageBlock.StorageBuild storage = (mindustry.world.blocks.storage.StorageBlock.StorageBuild) b;
                                int itemCount = read.b() & 0xFF;
                                while (itemCount != 0xFF) { // -1 转换为 255
                                    storage.items.set(itemCount, read.i());
                                    itemCount = read.b() & 0xFF;
                                }
                            } else if (b instanceof mindustry.world.blocks.storage.CoreBlock.CoreBuild) {
                                mindustry.world.blocks.storage.CoreBlock.CoreBuild core = (mindustry.world.blocks.storage.CoreBlock.CoreBuild) b;
                                int itemCount = read.b() & 0xFF;
                                while (itemCount != 0xFF) { // -1 转换为 255
                                    core.items.set(itemCount, read.i());
                                    itemCount = read.b() & 0xFF;
                                }
                            }
                            
                            return b;
                        });
                        
                        if (building != null) {
                            buildings.add(building);
                            if (building instanceof TurretBuild) {
                                turrets.add((TurretBuild) building);
                            }
                        }
                    }
                }
            }
        }
    }
}
