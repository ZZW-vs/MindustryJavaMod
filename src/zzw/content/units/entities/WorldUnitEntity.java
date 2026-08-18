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
import arc.util.Log;
import arc.util.Tmp;
import arc.util.io.Writes;
import arc.util.io.Reads;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.io.SaveVersion;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.defense.turrets.BaseTurret.BaseTurretBuild;
import mindustry.world.blocks.defense.turrets.ReloadTurret.ReloadTurretBuild;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;
import mindustry.world.blocks.power.PowerNode.PowerNodeBuild;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

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

        // ★ 去掉子世界炮台的红温动画 (heat 是 TurretBuild 的开火热度,
        //   渲染为炮管上的红色 additive 发光; 每帧清零后不再显示,
        //   射击/索敌/装填等其他行为不受影响)
        for (int i = 0; i < turrets.size; i++) {
            turrets.get(i).heat = 0f;
        }

        // ★ TimeReflect: 恢复 Time.runs 为原始主世界队列
        TimeReflect.resetRuns();
        Vars.world = ow;
    }

    // ===== setup() (原样搬 WorldComp.setup(), 创建子世界 + 迁移建筑物) =====

    public void setup() {
        // ★ 创建空白子世界 (与读档恢复共用)
        createWorld();

        tmp.clear();
        tmpr.clear();
        WorldUnitType uType = (WorldUnitType) type;
        TeamData data = team().data();
        if (data.buildingTree != null) {
            Tmp.r1.setCentered(x, y, uType.worldWidth * Vars.tilesize, uType.worldHeight * Vars.tilesize);
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
     * 创建空白子世界 (setup 迁移建筑 / 读档恢复建筑共用).
     * <p>步骤:
     * <ol>
     *   <li>按 UnitType 的 worldWidth/worldHeight 创建 Tiles</li>
     *   <li>每个 tile 用 UnitTile (无事件无渲染缓存的轻量 tile)</li>
     *   <li>地板统一铺 metalFloor</li>
     * </ol></p>
     */
    protected void createWorld() {
        WorldUnitType uType = (WorldUnitType) type;
        int w = uType.worldWidth, h = uType.worldHeight;
        unitWorld = new World();
        unitWorld.tiles = new Tiles(w, h);
        for (int i = 0; i < w * h; i++) {
            unitWorld.tiles.set(i % w, i / w, new UnitTile(i % w, i / w));
        }
        unitWorld.tiles.eachTile(tile -> tile.setFloor(Blocks.metalFloor.asFloor()));
    }

    // ===== 存档序列化 (修复重进地图子世界内容消失的问题) =====
    //
    // 方案: SaveVersion CustomChunk (官方为 mod 提供的自定义存档区块)
    // <p>不修改 UnitEntity 的 write/read 字节流 —— 单位数据在存档中是定长区块,
    // 直接追加字段会导致旧存档读档时字节错位、整个存档损坏。
    // CustomChunk 独立于单位数据, 旧存档没有该区块时自动跳过, 完全向前兼容。</p>
    // <p>时序: 存档时 custom 区块在 entities 之后写入;
    // 读档时 custom 区块也在 entities 之后读取 —— 此时所有单位已加载进 Groups.unit,
    // 可按 unitId 找回单位并重建其子世界。</p>

    /**
     * 把子世界建筑数据写入数据流 (由 {@link SaveChunk} 在存档时调用).
     * <p>格式: [单位id][建筑数量] + 每个建筑 [数据长度][blockId tileX tileY rotation teamId revision writeBase+write数据]</p>
     *
     * @param write 存档输出流
     */
    public void writeSubWorld(Writes write) throws IOException {
        write.i(id);
        if (unitWorld == null || buildings.isEmpty()) {
            write.i(0);
            return;
        }
        write.i(buildings.size);
        for (int i = 0; i < buildings.size; i++) {
            writeBuilding(write, buildings.get(i));
        }
    }

    /**
     * 写入单个建筑: 先缓冲到内存再写入, 附带 4 字节长度前缀.
     * <p>数据块内格式: [blockId tileX tileY rotation teamId revision] + writeBase + write 的输出。
     * 元数据必须先于建筑数据写入 —— 恢复时才能先重建方块再 readAll 恢复状态。</p>
     * <p>长度前缀的作用: 读档时单个建筑解析失败 (如 mod 方块被移除) 可以整体跳过,
     * 不会污染后续数据 —— 与原版 readMap 的容错机制一致。</p>
     */
    private void writeBuilding(Writes write, Building b) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(128);
        Writes bw = new Writes(new DataOutputStream(out));
        // ★ 元数据 (与 restoreBuilding 的读取顺序严格一致)
        bw.s(b.block.id);
        bw.s(b.tile.x);
        bw.s(b.tile.y);
        bw.b(b.rotation);
        bw.b(b.team.id);
        bw.b(b.version());
        // 建筑本体数据 (血量/物品/电力/液体/进度等)
        b.writeBase(bw);
        b.write(bw);
        write.i(out.size());
        write.b(out.toByteArray());
    }

    /**
     * 从数据流恢复子世界 (由 {@link SaveChunk} 在读档时调用).
     * <p>步骤:
     * <ol>
     *   <li>createWorld() 重建空白子世界</li>
     *   <li>逐个建筑: newBuilding + setBlock 放置 → readAll 恢复状态</li>
     *   <li>第二遍统一 updateProximity, 建立电力网络和物品传输邻近关系</li>
     * </ol>
     * 全程临时切换 Vars.world = unitWorld, 保证建筑的 world 查询都落在子世界内
     * (与 setup() 的做法一致)。</p>
     *
     * @param read 存档输入流
     * @param count 建筑数量 (由调用方从流中读出)
     */
    public void readSubWorld(Reads read, int count) throws IOException {
        if (count <= 0) return;
        createWorld();
        buildings.clear();
        turrets.clear();

        World ow = Vars.world;
        Vars.world = unitWorld;
        try {
            for (int i = 0; i < count; i++) {
                readBuilding(read);
            }
            // ★ 第二遍: 所有建筑就位后统一刷新邻近关系 (电力图/物品传输在此建立)
            for (int i = 0; i < buildings.size; i++) {
                buildings.get(i).updateProximity();
            }
        } finally {
            Vars.world = ow;
        }
    }

    /**
     * 读取单个建筑的数据块: [长度][字节数组], 字节数组内为
     * [blockId tileX tileY rotation teamId revision writeBase+write数据].
     * <p>先完整读出字节数组再解析 —— 即使解析抛异常, 外层流位置也不会错位。</p>
     */
    private void readBuilding(Reads read) throws IOException {
        int len = read.i();
        byte[] data = new byte[len];
        read.input.readFully(data);
        try (ByteArrayInputStream bin = new ByteArrayInputStream(data)) {
            restoreBuilding(new Reads(new DataInputStream(bin)));
        } catch (Exception e) {
            Log.err("WorldUnit sub-world building restore failed", e);
        }
    }

    /**
     * 在子世界中重建一个建筑 (从已缓冲的 Reads 解析).
     * <p>步骤:
     * <ol>
     *   <li>读取方块 id / tile 坐标 / 朝向 / 队伍 / 数据版本</li>
     *   <li>block.newBuilding().create() 创建建筑并初始化物品/电力/液体模块</li>
     *   <li>tile.setBlock 放置 (UnitTile.changeBuild 负责绑定 tile 和 rotation)</li>
     *   <li>readAll 恢复血量/物品/电力/进度等全部状态</li>
     * </ol>
     * 方块缺失或越界时丢弃该建筑 (数据块已隔离, 不影响后续)。</p>
     */
    private void restoreBuilding(Reads br) {
        Block block = Vars.content.block(br.s());
        int tx = br.s(), ty = br.s();
        byte rotation = br.b();
        Team team = Team.get(br.b());
        byte revision = br.b();

        if (block == null || !block.hasBuilding() || !valid(tx, ty)) return;

        Tile tile = unitWorld.tile(tx, ty);
        Building nb = block.newBuilding().create(block, team);
        try {
            tile.setBlock(block, team, rotation, () -> nb);
            nb.set(tile.drawx(), tile.drawy());
            nb.readAll(br, revision);
            nb.checkAllowUpdate();
        } catch (Exception e) {
            // 回滚半初始化的建筑, 避免残留在 tile 上
            tile.setBlock(Blocks.air);
            Log.err("WorldUnit sub-world building place failed: @", block.name);
            return;
        }

        buildings.add(nb);
        if (nb instanceof TurretBuild tb) turrets.add(tb);
    }

    /**
     * 世界单位存档区块 (注册到 SaveVersion 的 CustomChunk).
     * <p>区块名 "zzw-world-units", 存档格式:
     * [单位数量] + 每个单位 {@link #writeSubWorld} 的输出。</p>
     * <p>读档时按 unitId 在 Groups.unit 中找回单位 (此时单位已全部加载),
     * 找不到则按长度前缀跳过该单位的全部建筑数据。</p>
     */
    public static class SaveChunk implements SaveVersion.CustomChunk {
        @Override
        public boolean shouldWrite() {
            for (Unit u : Groups.unit) {
                if (u instanceof WorldUnitEntity w && w.unitWorld != null && !w.buildings.isEmpty()) return true;
            }
            return false;
        }

        /** 单人存档专用: 不写入联机同步流 (联机不同步子世界, 与 PU132 原版一致) */
        @Override
        public boolean writeNet() {
            return false;
        }

        @Override
        public void write(DataOutput stream) throws IOException {
            Writes write = new Writes(stream);
            Seq<WorldUnitEntity> list = new Seq<>(4);
            for (Unit u : Groups.unit) {
                if (u instanceof WorldUnitEntity w && w.unitWorld != null) list.add(w);
            }
            write.i(list.size);
            for (WorldUnitEntity w : list) {
                w.writeSubWorld(write);
            }
        }

        @Override
        public void read(DataInput stream) throws IOException {
            Reads read = new Reads(stream);
            int units = read.i();
            for (int u = 0; u < units; u++) {
                int unitId = read.i();
                int count = read.i();

                WorldUnitEntity target = null;
                for (Unit un : Groups.unit) {
                    if (un.id == unitId && un instanceof WorldUnitEntity w) {
                        target = w;
                        break;
                    }
                }

                if (target == null) {
                    // 找不到对应单位 (已死亡或内容变动): 按长度前缀逐个跳过建筑数据块
                    for (int i = 0; i < count; i++) {
                        int len = read.i();
                        read.input.skipBytes(len);
                    }
                    continue;
                }
                target.readSubWorld(read, count);
            }
        }
    }

    /** 注册存档区块 (在 Z_Units.load() 中调用一次) */
    public static void registerSaveChunk() {
        SaveVersion.addCustomChunk("zzw-world-units", new SaveChunk());
    }
}
