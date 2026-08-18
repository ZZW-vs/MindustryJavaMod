package zzw.content.units.entities;

import zzw.content.units.ZEntityRegister;
import zzw.content.type.WorldUnitType;

import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.ObjectIntMap;
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
import mindustry.gen.Bullet;
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

    // ===== 部署系统字段 =====
    /** 部署状态: true = 建筑落在主世界真实 tile 上 (原版输入全功能可用), 单位锁定不动 */
    public boolean deployed = false;
    /** 子世界建筑 id 集合 (子弹归属判断用, O(1) 查询) */
    protected transient IntSet buildingIds = new IntSet(16);
    /** 上一帧位置/朝向 (计算本帧位移, 驱动子弹跟随) */
    protected transient float lastX, lastY, lastRotation;
    /** 部署前的 elevation (收起时恢复飞行高度; -1 = 未部署过) */
    protected transient float savedElevation = -1f;

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

        // ★ 部署状态: 建筑已挂主世界 (由原版系统更新), 单位锁定不动
        if (deployed) {
            vel.setZero();
            // ★ 单位下沉: elevation=0.5 时渲染层判定 (elevation > 0.5f) 不成立,
            //   单位画在 groundLayer (darkness+1, 所有建筑之下), 建筑完整可见可交互;
            //   同时 isFlying() (>= 0.09) 仍为 true, 命中判定等行为与飞行态一致
            elevation = 0.5f;
            followBullets();
            return;
        }

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

        // ★ 子弹跟随: 子世界炮台发射的子弹 (含蓄力激光) 每帧跟随单位移动旋转,
        //   否则激光突刺类长寿命子弹会停在发射位置不随单位走
        followBullets();
    }

    /**
     * 让子世界建筑发射的子弹跟随单位本帧的位移和旋转.
     * <p>子弹是独立实体, 生成后位置固定; 蓄力激光 (LaserBulletType 等长寿命子弹)
     * 会一直停留在发射位置 —— 单位移动时看起来"激光留在原地"。
     * 这里按上一帧位置差计算 delta, 对归属子弹做平移 + 绕单位中心旋转。</p>
     * <p>归属判断: bullet.owner 是本单位的子世界建筑 (buildingIds 集合 O(1) 查询)。</p>
     */
    protected void followBullets() {
        float dx = x - lastX, dy = y - lastY;
        float dr = rotation - lastRotation;

        if ((dx != 0f || dy != 0f || dr != 0f) && buildingIds.size > 0) {
            for (Bullet blt : Groups.bullet) {
                if (blt.owner instanceof Building ob && buildingIds.contains(ob.id)) {
                    if (dr != 0f) {
                        // 绕单位中心旋转 (与子世界渲染投影公式一致)
                        Tmp.v1.set(blt.x, blt.y).sub(this).rotate(dr).add(this);
                        blt.set(Tmp.v1.x, Tmp.v1.y);
                        blt.rotation(blt.rotation() + dr);
                    } else {
                        blt.set(blt.x + dx, blt.y + dy);
                    }
                }
            }
        }
        lastX = x;
        lastY = y;
        lastRotation = rotation;
    }

    // ===== setup / absorb / deploy (召唤初始化 + 收起/部署 双向迁移) =====

    /**
     * 召唤时初始化: 创建空白子世界并吸收脚下建筑 (PU132 WorldComp.setup 原逻辑).
     */
    public void setup() {
        // ★ 创建空白子世界 (与读档恢复共用)
        createWorld();
        absorb();
    }

    /**
     * 收起: 吸收单位范围内的主世界建筑到子世界.
     * <p>部署后玩家在单位区域内新建的建筑也在此一并吸收。
     * 与 deploy() 互为逆操作; 朝向自动归整到 90 度倍数保证 tile 网格对齐。</p>
     *
     * @return 本次吸收的建筑数量
     */
    public int absorb() {
        if (unitWorld == null) createWorld();
        deployed = false;
        // ★ 恢复飞行高度 (deploy 时记录的 elevation, 哨兵 -1 表示从未部署)
        if (savedElevation > 0) {
            elevation = savedElevation;
            savedElevation = -1f;
        }
        // ★ 朝向归整到 90 度倍数: 任意角度时 tile 映射无法对齐主世界网格
        rotation = Math.round(rotation / 90f) * 90f;

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
        int absorbed = 0;
        for (Building building : tmp) {
            if (validPlace(building.tile) && tmpAdded.add(building.id)) {
                // 向量法映射建筑中心像素 (含单位朝向旋转, 任意 90 度朝向正确)
                vec.set(building.x, building.y).sub(x, y).rotate(-(rotation - 90f)).add(subCX(), subCY());
                // ★ offset 校正 + round 得 tile 参考点 (奇数尺寸=中心tile, 偶数尺寸=原点tile, 与原版约定一致)
                int tx = Math.round((vec.x - building.block.offset) / Vars.tilesize);
                int ty = Math.round((vec.y - building.block.offset) / Vars.tilesize);

                if (building.power != null && building instanceof PowerNodeBuild && !building.power.links.isEmpty()) {
                    IntSeq seq = building.power.links, nseq = new IntSeq();
                    for (int i = 0; i < seq.size; i++) {
                        int pos = seq.get(i);
                        // 链接目标 tile 中心像素 (= 坐标*8) → 子世界 tile (同样向量旋转)
                        vec.set(Point2.x(pos) * Vars.tilesize, Point2.y(pos) * Vars.tilesize)
                            .sub(x, y).rotate(-(rotation - 90f)).add(subCX(), subCY());
                        int cx = Math.round(vec.x / Vars.tilesize), cy = Math.round(vec.y / Vars.tilesize);
                        if (valid(cx, cy)) {
                            nseq.add(Point2.pack(cx, cy));
                        }
                    }
                    if (!nseq.isEmpty()) {
                        tmpLinks.put(building.id, nseq);
                    }
                }

                tmpr.add(() -> {
                    // ★ 建筑像素坐标对齐到子世界 tile 中心 (offset + tile*8):
                    //   与原版世界建筑完全一致的网格行为, 消除向量旋转的亚 tile 相位误差,
                    //   存档(tile 坐标)与读档(drawx 重算)的位置天然一致, 不再错位
                    Tile st = unitWorld.tile(tx, ty);
                    st.setBlock(building.block, building.team, building.rotation, () -> building);
                    building.x = st.drawx();
                    building.y = st.drawy();
                });

                building.tile.remove();
                buildings.add(building);
                buildingIds.add(building.id);
                absorbed++;
            }
        }

        World ow = Vars.world;
        Vars.world = unitWorld;

        for (Runnable r : tmpr) {
            r.run();
        }
        rebuildFromBuildings();

        Vars.world = ow;
        tmpr.clear();
        tmp.clear();
        tmpLinks.clear();
        return absorbed;
    }

    /**
     * 部署: 子世界建筑落到主世界真实 tile 上 (deploy 的正向操作).
     * <p>部署后建筑由原版系统全权管理 —— 建造菜单放方块、点击配置、
     * 框选、修理、悬停面板等原版输入全部可用 (这就是"子世界=全局世界"形态)。
     * 单位锁定不动, 移动前需先收起 (absorb)。</p>
     * <p>落点被占 (地形/敌建筑) 的建筑留在子世界, 不影响其余建筑部署。</p>
     *
     * @return 成功部署到主世界的建筑数量
     */
    public int deploy() {
        if (unitWorld == null || buildings.isEmpty()) return 0;
        // ★ 朝向归整: 部署几何只在 90 度倍数时与主世界网格对齐
        rotation = Math.round(rotation / 90f) * 90f;
        float r = rotation - 90f;
        int steps = Math.round(r / 90f) & 3;

        tmp.clear();
        tmpr.clear();
        tmpLinks.clear();
        tmpAdded.clear();

        // 第一遍: 计算落点, 收集可部署建筑 (主世界上下文查询)
        for (Building b : buildings) {
            // ★ offset 校正 + round: 建筑中心像素 → tile 参考点
            //   (奇数尺寸 offset=0 得中心 tile; 偶数尺寸 offset=4 得原点 tile, 与原版约定一致)
            vec.set(b.x, b.y).sub(subCX(), subCY()).rotate(r).add(x, y);
            int mx = Math.round((vec.x - b.block.offset) / Vars.tilesize);
            int my = Math.round((vec.y - b.block.offset) / Vars.tilesize);
            int newRot = (b.rotation + steps) & 3;
            if (!canDeployAt(b.block, mx, my)) continue;

            // 电力链接: 子世界 pos → 主世界 pos (向量旋转)
            if (b.power != null && b instanceof PowerNodeBuild && !b.power.links.isEmpty()) {
                IntSeq seq = b.power.links, nseq = new IntSeq();
                for (int i = 0; i < seq.size; i++) {
                    int pos = seq.get(i);
                    vec.set(Point2.x(pos) * Vars.tilesize, Point2.y(pos) * Vars.tilesize)
                        .sub(subCX(), subCY()).rotate(r).add(x, y);
                    int cx = Math.round(vec.x / Vars.tilesize), cy = Math.round(vec.y / Vars.tilesize);
                    nseq.add(Point2.pack(cx, cy));
                }
                if (!nseq.isEmpty()) tmpLinks.put(b.id, nseq);
            }

            final int fmx = mx, fmy = my, frot = newRot;
            tmpr.add(() -> {
                Tile mt = Vars.world.tile(fmx, fmy);
                mt.setBlock(b.block, b.team, frot, () -> b);
                // 主世界位置由 tile 语义决定 (与原版建筑完全一致)
                b.x = mt.drawx();
                b.y = mt.drawy();
            });
            tmp.add(b);
        }

        // 第二遍: 从子世界摘除 (切到子世界上下文, 多方块邻格查询才正确)
        World ow = Vars.world;
        Vars.world = unitWorld;
        for (Building b : tmp) {
            b.tile.remove();
        }
        Vars.world = ow;

        // 第三遍: 主世界放置 + 电力重连 + 邻近关系
        for (Runnable run : tmpr) {
            run.run();
        }
        for (Building b : tmp) {
            if (b instanceof PowerNodeBuild) {
                IntSeq seq = tmpLinks.get(b.id);
                if (seq != null && b.power != null) {
                    b.power.links.clear();
                    for (int i = 0; i < seq.size; i++) {
                        b.configureAny(seq.get(i));
                    }
                }
            }
            b.updateProximity();
        }

        // 子世界列表只保留未部署的建筑
        buildings.removeAll(tmp);
        rebuildFromBuildings();
        int deployedCount = tmp.size;
        if (deployedCount > 0) {
            deployed = true;
            // ★ 记录飞行高度并下沉: 单位画到建筑之下, 玩家可正常查看/点击/框选全部建筑
            if (savedElevation < 0) savedElevation = elevation;
        }

        tmpr.clear();
        tmp.clear();
        tmpLinks.clear();
        return deployedCount;
    }

    /** 收起 (面板按钮入口, 语义化命名) */
    public int undeploy() {
        return absorb();
    }

    /**
     * 检查主世界指定位置能否放置方块 (deploy 用).
     * <p>遍历方块占据的全部 tile: 越界 / 已有建筑 / 地形不可建造则不可放置。
     * 不使用 Build.validPlace —— 那会触发放置事件且规则更严, 此处只需基础检查。</p>
     */
    protected boolean canDeployAt(Block block, int x, int y) {
        int offset = -(block.size - 1) / 2;
        for (int dx = 0; dx < block.size; dx++) {
            for (int dy = 0; dy < block.size; dy++) {
                Tile t = Vars.world.tile(dx + offset + x, dy + offset + y);
                if (t == null || t.build != null || t.solid() || !t.floor().placeableOn) return false;
            }
        }
        return true;
    }

    /** 按当前 buildings 列表重建 turrets 和 buildingIds (吸收/部署/读档后调用) */
    protected void rebuildFromBuildings() {
        turrets.clear();
        buildingIds.clear();
        for (Building b : buildings) {
            buildingIds.add(b.id);
            if (b instanceof TurretBuild tb) turrets.add(tb);
        }
    }

    /** 判断建筑是否属于本单位的子世界 (子弹归属/渲染过滤用, O(1)) */
    public boolean ownsBuilding(Building b) {
        return buildingIds.contains(b.id);
    }

    // ===== 坐标转换 (统一向量旋转法, 任意 90 度朝向正确) =====

    /** 子世界中心像素 X */
    protected float subCX() {
        return unitWorld.width() * Vars.tilesize / 2f;
    }

    /** 子世界中心像素 Y */
    protected float subCY() {
        return unitWorld.height() * Vars.tilesize / 2f;
    }

    /**
     * 检查主世界 tile 上的方块是否能完整放入子世界 (考虑多方块旋转后的落位).
     * <p>先按向量旋转算出中心子世界 tile, 再按方块尺寸铺开检查边界。</p>
     */
    public boolean validPlace(Tile tile) {
        Block block = tile.block();
        vec.set(tile.worldx(), tile.worldy()).sub(x, y).rotate(-(rotation - 90f)).add(subCX(), subCY());
        int tx = mindustry.core.World.toTile(vec.x);
        int ty = mindustry.core.World.toTile(vec.y);

        int offset = -(block.size - 1) / 2;
        boolean valid = tx >= 0 && tx < unitWorld.width() && ty >= 0 && ty < unitWorld.height();
        if (block.isMultiblock()) {
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
     * <p>格式: [单位id][部署状态][建筑数量] + 每个建筑 [数据长度][blockId tileX tileY rotation teamId revision writeBase+write数据]</p>
     * <p>部署状态时建筑在主世界 (由原版存档管理), 此处只写标志不写建筑数据。</p>
     *
     * @param write 存档输出流
     */
    public void writeSubWorld(Writes write) throws IOException {
        write.i(id);
        write.b(deployed ? (byte) 1 : (byte) 0);
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
     * <p>数据块内格式 (v3): [blockId tileX tileY rotation teamId revision x y] + writeBase + write 的输出。
     * 元数据必须先于建筑数据写入 —— 恢复时才能先重建方块再 readAll 恢复状态。</p>
     * <p>★ 精确 x/y (v3 新增): writeBase 不含坐标, 若只存 tile 坐标,
     * 读档后位置由 tile.drawx() 重算, 与存档前的连续坐标存在亚 tile 相位差 → 方块错位。
     * 直接保存浮点坐标, 读档后精确还原。</p>
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
        // ★ 精确像素坐标 (v3, 修复读档错位)
        bw.f(b.x);
        bw.f(b.y);
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
     *   <li>createWorld() 重建空白子世界 (即使建筑为 0 也保证 unitWorld 有效)</li>
     *   <li>逐个建筑: newBuilding + setBlock 放置 → readAll 恢复状态</li>
     *   <li>第二遍统一 updateProximity, 建立电力网络和物品传输邻近关系</li>
     * </ol>
     * 全程临时切换 Vars.world = unitWorld, 保证建筑的 world 查询都落在子世界内
     * (与 setup() 的做法一致)。</p>
     *
     * @param read 存档输入流
     * @param count 建筑数量 (由调用方从流中读出)
     * @param deployed 读出的部署状态 (部署态: 建筑由原版存档恢复, 子世界保持空)
     * @param ver 存档区块格式版本 (v3+ 建筑数据含精确 x/y)
     */
    public void readSubWorld(Reads read, int count, boolean deployed, byte ver) throws IOException {
        if (unitWorld == null) createWorld();
        this.deployed = deployed;
        if (count <= 0) return;

        World ow = Vars.world;
        Vars.world = unitWorld;
        try {
            for (int i = 0; i < count; i++) {
                readBuilding(read, ver);
            }
            // ★ 第二遍: 所有建筑就位后统一刷新邻近关系 (电力图/物品传输在此建立)
            for (int i = 0; i < buildings.size; i++) {
                buildings.get(i).updateProximity();
            }
            rebuildFromBuildings();
        } finally {
            Vars.world = ow;
        }
    }

    /**
     * 读取单个建筑的数据块: [长度][字节数组], 字节数组内为
     * [blockId tileX tileY rotation teamId revision (x y)] + writeBase+write数据.
     * <p>先完整读出字节数组再解析 —— 即使解析抛异常, 外层流位置也不会错位。</p>
     */
    private void readBuilding(Reads read, byte ver) throws IOException {
        int len = read.i();
        byte[] data = new byte[len];
        read.input.readFully(data);
        try (ByteArrayInputStream bin = new ByteArrayInputStream(data)) {
            restoreBuilding(new Reads(new DataInputStream(bin)), ver);
        } catch (Exception e) {
            Log.err("WorldUnit sub-world building restore failed", e);
        }
    }

    /**
     * 在子世界中重建一个建筑 (从已缓冲的 Reads 解析).
     * <p>步骤:
     * <ol>
     *   <li>读取方块 id / tile 坐标 / 朝向 / 队伍 / 数据版本 / 精确坐标(v3+)</li>
     *   <li>block.newBuilding().create() 创建建筑并初始化物品/电力/液体模块</li>
     *   <li>tile.setBlock 放置 (UnitTile.changeBuild 负责绑定 tile 和 rotation)</li>
     *   <li>readAll 恢复血量/物品/电力/进度等全部状态</li>
     * </ol>
     * 方块缺失或越界时丢弃该建筑 (数据块已隔离, 不影响后续)。</p>
     */
    private void restoreBuilding(Reads br, byte ver) {
        Block block = Vars.content.block(br.s());
        int tx = br.s(), ty = br.s();
        byte rotation = br.b();
        Team team = Team.get(br.b());
        byte revision = br.b();
        float px = 0f, py = 0f;
        boolean hasPos = false;
        if (ver >= 3) {
            // v3+: 精确像素坐标 (修复读档错位 —— writeBase 不含坐标, tile 重算有亚 tile 相位差)
            px = br.f();
            py = br.f();
            hasPos = true;
        }

        if (block == null || !block.hasBuilding() || !valid(tx, ty)) return;

        Tile tile = unitWorld.tile(tx, ty);
        Building nb = block.newBuilding().create(block, team);
        try {
            tile.setBlock(block, team, rotation, () -> nb);
            // ★ 位置优先级: v3+ 用存档精确坐标; 旧版本用 tile 中心重算
            if (hasPos) {
                nb.set(px, py);
            } else {
                nb.set(tile.drawx(), tile.drawy());
            }
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
     * [版本=3][单位数量] + 每个单位 {@link #writeSubWorld} 的输出。</p>
     * <p>读档时按 unitId 在 Groups.unit 中找回单位 (此时单位已全部加载),
     * 找不到则按长度前缀跳过该单位的全部建筑数据。</p>
     * <p>版本字节兼容: v1 (无版本前缀) 首字节是"单位数量 int"的高字节, 恒为 0;
     * v2 = deployed 部署状态; v3 = 建筑数据追加精确 x/y 坐标 (修复读档错位)。</p>
     */
    public static class SaveChunk implements SaveVersion.CustomChunk {
        /** 当前存档格式版本 (3: 建筑数据追加精确 x/y) */
        static final byte VERSION = 3;

        @Override
        public boolean shouldWrite() {
            for (Unit u : Groups.unit) {
                if (u instanceof WorldUnitEntity w && w.unitWorld != null) return true;
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
            write.b(VERSION);
            write.i(list.size);
            for (WorldUnitEntity w : list) {
                w.writeSubWorld(write);
            }
        }

        @Override
        public void read(DataInput stream) throws IOException {
            Reads read = new Reads(stream);

            // 版本链识别: 3/2 为显式版本号; 0 为 v1 旧格式 (单位数量 int 的高字节)
            byte ver = 0;
            if (stream instanceof DataInputStream dis && dis.markSupported()) {
                dis.mark(1);
                int first = dis.read();
                if (first == 3 || first == 2) {
                    ver = (byte) first;
                } else {
                    dis.reset();
                }
            }

            int units = read.i();
            for (int u = 0; u < units; u++) {
                int unitId = read.i();
                boolean deployed = ver >= 2 && read.b() != 0;
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
                target.readSubWorld(read, count, deployed, ver);
            }
        }
    }

    /** 注册存档区块 (在 Z_Units.load() 中调用一次) */
    public static void registerSaveChunk() {
        SaveVersion.addCustomChunk("zzw-world-units", new SaveChunk());
    }
}
