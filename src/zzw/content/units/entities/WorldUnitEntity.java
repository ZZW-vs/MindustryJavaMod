package zzw.content.units.entities;

import zzw.content.units.ZEntityRegister;
import zzw.content.type.WorldUnitType;

import arc.Core;
import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.FloatSeq;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Tmp;
import arc.util.Time;
import arc.util.io.Writes;
import arc.util.io.Reads;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.game.EventType.BlockBuildBeginEvent;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.BuildRotateEvent;
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
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import mindustry.world.blocks.defense.turrets.BaseTurret.BaseTurretBuild;
import mindustry.world.blocks.defense.turrets.ReloadTurret.ReloadTurretBuild;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;
import mindustry.world.blocks.power.PowerNode.PowerNodeBuild;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import zzw.content.blocks.units.TerraCore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 世界单位 Entity (PU132 unity.entities.comp.WorldComp 简化移植)
 *
 * 设计: extends UnitEntity, 直接继承 UnitEntity 的所有 Unit 接口默认行为
 * - 携带一个子世界 (unitWorld), 子世界中的建筑物跟随单位自由移动和旋转 (PU132 原版行为)
 * - setup(): 创建子世界 + 扫描迁移附近建筑物 (原样搬 WorldComp.setup())
 * - update(): 临时切换 Vars.world + 旋转坐标更新建筑物 (原样搬 WorldComp.update())
 * - 子世界网格偏移: 9x19 多出的一行一列放世界左方/最下方, 对齐偏左下的甲板贴图区域 (修复方块错位)
 * - 长方形碰撞箱 (hitbox 重写): 与平台同样大小, 悬停检测覆盖全平台 + 平台整体挡弹
 * - display 重写: 悬停信息委托给光标下的子世界建筑 (原版右下角信息面板显示建筑信息)
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

    
    /** 子世界建筑 id 集合 (子弹归属判断用, O(1) 查询) */
    protected transient IntSet buildingIds = new IntSet(16);
    /** 上一帧位置/朝向 (计算本帧位移, 驱动子弹跟随) */
    protected transient float lastX, lastY, lastRotation;
    /** 子世界平台尺寸 (世界像素, createWorld 时计算; 长方形碰撞箱用) */
    protected transient float platW, platH;
    /** 子世界网格偏移 (0=居中; TerraCore 2x2 吸收后落子世界正中心, 大地核心居中) */
    protected transient float gridOffX = 0f, gridOffY = 0f;
    /** 主大地核心 (召唤本单位的 TerraCore, 被吸收进子世界; 不可拆除, 子世界唯一) */
    public transient Building mainCore;
    /** 建造模式: 点击主核心按钮激活, 激活后才能对子世界建造/拆除 (边缘虚线框提示) */
    public transient boolean buildMode = false;

    /** 子世界平台宽 (世界像素, 渲染虚线框用) */
    public float platW() {
        return platW;
    }

    /** 子世界平台高 (世界像素, 渲染虚线框用) */
    public float platH() {
        return platH;
    }

    /** 返回注册的 classId (绕过 v155.4 的 checkEntityMapping 检查) */
    @Override
    public int classId() {
        return ZEntityRegister.classId(WorldUnitEntity.class);
    }

    /**
     * 建造能力判定 (物品栏明暗 + 建造输入开关的总闸).
     *
     * <p>原版链路: {@code PlacementFragment} 按钮颜色 = 核心资源够 && {@code player.isBuilder()};
     * 而 {@code Player.isBuilder()} 直接返回 {@code unit.canBuild()}, 基类实现要求
     * {@code type.buildSpeed > 0} —— terra 的 buildSpeed=0 (不能在主世界自行施工),
     * 因此默认全灰. 这里覆写为"附生本单位 && 建造模式激活"才放行:
     * 开启建造模式后物品栏方块按主世界核心资源正常变亮/变暗,
     * 主世界的实际放置屏蔽由 {@code sweepPlans()} 清扫计划兜底.</p>
     */
    @Override
    public boolean canBuild() {
        return buildMode && Vars.player.unit() == this;
    }

    // ===== update() (原 WorldComp.update, 去掉 @MethodPriority, 保留 TimeReflect) =====

    @Override
    public void update() {
        // ★ 先执行正常的 Unit 更新 (移动/物理/武器等), 对应 PU132 中 @MethodPriority(100) 晚于默认优先级
        super.update();

        // 子世界未初始化时跳过 (setup() 尚未调用)
        if (unitWorld == null) return;

        // 正常更新: 子世界中的建筑物跟随单位移动和旋转 (平台可自由移动和旋转,
        // 放置预览通过连续坐标映射吸附到子世界自己的网格)
        float cx = subCX(), cy = subCY();
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

        // ★ 子世界建造推进: Terra 自当 builder, 推进 ConstructBlock 脚手架进度并扣主世界核心资源;
        //   完成的脚手架被 constructFinish 替换为新建筑 → 在此注册进 buildings 列表
        // (放在 positions 恢复循环之后: 本方法会增删 buildings 列表, 先恢复旧建筑坐标再处理)
        tickConstructions();

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

    // ===== setup / absorb (召唤初始化 + 吸收建筑到子世界) =====

    /**
     * 召唤时初始化: 创建空白子世界并吸收脚下建筑 (PU132 WorldComp.setup 原逻辑).
     */
    public void setup() {
        // ★ 创建空白子世界 (与读档恢复共用)
        createWorld();
        absorb();
    }

    /**
     * 吸收单位范围内的主世界建筑到子世界 (召唤时由 setup() 调用).
     * <p>朝向归整到 90 度倍数保证 tile 映射对齐。</p>
     *
     * @return 本次吸收的建筑数量
     */
    public int absorb() {
        if (unitWorld == null) createWorld();

        // ★ 朝向归整到 90 度倍数: 任意角度时 tile 映射无法对齐网格
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
                // ★ 大地核心: 第一个被吸收的 TerraCore 记录为主核心 (不可拆除);
                //   其余 TerraCore 跳过 (子世界唯一 —— 召唤前的 TerraCore.buildConfiguration
                //   检查已拦截多核心情况, 此处双保险)
                if (building.block instanceof TerraCore) {
                    if (mainCore != null) continue;
                    mainCore = building;
                }

                // 向量法映射建筑中心像素 (含单位朝向旋转, 任意 90 度朝向正确)
                vec.set(building.x, building.y).sub(x, y).rotate(-(rotation - 90f)).add(subCX(), subCY());
                // ★ offset 校正 + round 得 tile 参考点 (奇数尺寸=中心tile, 偶数尺寸=原点tile, 与原版约定一致)
                int tx = Math.round((vec.x - building.block.offset) / Vars.tilesize);
                int ty = Math.round((vec.y - building.block.offset) / Vars.tilesize);

                // ★ 大地核心网格位置: 不做额外校正 —— gridOffX=-0.5 格已把网格左移半格,
                //   核心天然落点即平台正中 (此前的 ±1 校正是旧 gridOff 时代的历史遗留,
                //   叠加后反而偏右 1 格, 已移除)
                final int ftx = tx;

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
                    Tile st = unitWorld.tile(ftx, ty);
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

    /** 按当前 buildings 列表重建 turrets 和 buildingIds (吸收/读档后调用) */
    protected void rebuildFromBuildings() {
        turrets.clear();
        buildingIds.clear();
        for (Building b : buildings) {
            buildingIds.add(b.id);
            if (b instanceof TurretBuild tb) turrets.add(tb);
        }
    }

    /**
     * 恢复主大地核心引用 (读档后调用).
     * <p>mainCore 是 transient 字段, 读档后为 null —— 子世界里 TerraCore 唯一
     * (放置被 placeSub 拦截、多余吸收被 absorb 拦截), 扫描 buildings 找到
     * TerraCore 建筑即为唯一主核心, 无需存档字段 (v3 格式不变)。</p>
     */
    protected void restoreMainCore() {
        if (mainCore == null || mainCore.dead || !buildingIds.contains(mainCore.id)) {
            mainCore = null;
            for (Building b : buildings) {
                if (b.block instanceof TerraCore) {
                    mainCore = b;
                    break;
                }
            }
        }
    }

    /** 判断建筑是否属于本单位的子世界 (子弹归属/渲染过滤用, O(1)) */
    public boolean ownsBuilding(Building b) {
        return buildingIds.contains(b.id);
    }

    // ===== 坐标转换 (统一向量旋转法, 任意 90 度朝向正确) =====

    /** 子世界中心像素 X (8x18 偶数尺寸网格居中, gridOff=0) */
    public float subCX() {
        return unitWorld.width() * Vars.tilesize / 2f + gridOffX;
    }

    /** 子世界中心像素 Y (含网格偏移) */
    public float subCY() {
        return unitWorld.height() * Vars.tilesize / 2f + gridOffY;
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

    /**
     * 子世界能否放置方块 (多方块按尺寸铺开检查边界和占用).
     * <p>子世界地板全是 metalFloor 可建, 无需地形检查; 子世界里也没有单位, 无需重叠检查。</p>
     */
    public boolean canBuildSub(Block block, int tx, int ty) {
        if (unitWorld == null || block == null) return false;
        int offset = -(block.size - 1) / 2;
        for (int dx = 0; dx < block.size; dx++) {
            for (int dy = 0; dy < block.size; dy++) {
                int x2 = tx + offset + dx, y2 = ty + offset + dy;
                if (!valid(x2, y2)) return false;
                if (unitWorld.tile(x2, y2).build != null) return false;
            }
        }
        return true;
    }

    /**
     * 在子世界放置方块 (复用原版 Build.beginPlace 的核心流程, 但 tile 查询落在子世界).
     * <p>流程:
     * <ol>
     *   <li>quickRotate: 同方块同队 → 只旋转已有建筑</li>
     *   <li>instantBuild 方块 → 直接完成 (setBlock + 配置 + 特效 + 事件)</li>
     *   <li>普通方块 → 放 ConstructBlock 脚手架, 由 {@link #tickConstructions()} 推进建造</li>
     * </ol>
     * 不走 Build.validPlace —— 那会查主世界的迷雾可见性/单位重叠等, 对子世界坐标无意义;
     * 边界与占用检查由 {@link #canBuildSub} 完成。</p>
     *
     * @param rot 子世界内部朝向 (调用方需把主世界朝向换算成子世界朝向)
     * @param config 放置配置 (蓝图粘贴等场景携带)
     */
    public boolean placeSub(Block block, int tx, int ty, int rot, Object config) {
        if (unitWorld == null || block == null) return false;
        // ★ 大地核心保护: 子世界只允许存在一个大地核心 (召唤时被吸收的主核心),
        //   禁止玩家往子世界里放新的 TerraCore (防止无限套娃召唤)
        if (block instanceof TerraCore) return false;
        Tile tile = unitWorld.tile(tx, ty);
        if (tile == null) return false;

        // quickRotate: 同方块同队 → 旋转已有建筑 (原版 beginPlace 行为)
        if (tile.team() == team && tile.block() == block && tile.build != null && block.quickRotate) {
            int previous = tile.build.rotation;
            tile.build.rotation = Mathf.mod(rot, 4);
            tile.build.updateProximity();
            tile.build.noSleep();
            arc.Events.fire(new BuildRotateEvent(tile.build, this, previous));
            return true;
        }

        if (!canBuildSub(block, tx, ty)) return false;

        World ow = Vars.world;
        Vars.world = unitWorld;
        try {
            // instantBuild: 直接完成 (原版 ConstructBlock.constructFinish 的核心步骤)
            if (block.instantBuild) {
                arc.Events.fire(new BlockBuildBeginEvent(tile, team, this, false));
                block.placeBegan(tile, tile.block(), this);
                tile.setBlock(block, team, rot);
                if (tile.build != null) {
                    if (config != null) tile.build.configured(this, config);
                    registerBuilding(tile.build);
                    tile.build.updateProximity();
                    tile.build.noSleep();
                }
                block.placeEffect.at(tile.drawx(), tile.drawy(), block.size);
                arc.Events.fire(new BlockBuildEndEvent(tile, this, team, false, config));
                block.placeEnded(tile, this, rot, config);
                return true;
            }

            // 普通方块: 放 ConstructBlock 脚手架 (原版 beginPlace 的核心步骤)
            Block sub = ConstructBlock.get(block.size);
            tile.setBlock(sub, team, rot);
            ConstructBuild cons = (ConstructBuild) tile.build;
            cons.setConstruct(Blocks.air, block);
            cons.lastConfig = config;
            registerBuilding(cons);
            arc.Events.fire(new BlockBuildBeginEvent(tile, team, this, false));
            block.placeBegan(tile, Blocks.air, this);
            return true;
        } finally {
            Vars.world = ow;
        }
    }

    /**
     * 拆除子世界建筑 (复用原版 Build.beginBreak 的核心流程).
     * <p>instantDeconstruct 方块直接移除; 普通建筑放拆除脚手架,
     * 由 {@link #tickConstructions()} 推进拆除并按阶段返还资源到主世界核心。</p>
     */
    public boolean breakSub(int tx, int ty) {
        if (unitWorld == null) return false;
        Tile tile = unitWorld.tile(tx, ty);
        if (tile == null || tile.build == null) return false;
        // ★ 主大地核心不可拆除 (召唤本单位的 TerraCore, 拆掉会破坏单位与核心的绑定)
        if (tile.build == mainCore) return false;
        // 只能拆己方/无主建筑 (原版 validBreak 的队伍检查)
        if (tile.team() != team && tile.team() != Team.derelict) return false;
        // 已在拆除中的脚手架不重复发起 (即时拆除 + 原版拆键 plan 转译会同时触发)
        if (tile.build instanceof ConstructBuild cb && cb.current == cb.previous) return false;

        float prevPercent = tile.build.healthf();
        int rotation = tile.build.rotation;
        Block previous = tile.block();

        // 即拆方块: 直接移除 (原版 ConstructBlock.deconstructFinish 的核心步骤)
        if (previous.instantDeconstruct) {
            World ow = Vars.world;
            Vars.world = unitWorld;
            try {
                previous.breakEffect.at(tile.drawx(), tile.drawy(), previous.size, previous.mapColor);
                arc.Events.fire(new BlockBuildEndEvent(tile, this, team, true, null));
                tile.remove();
            } finally {
                Vars.world = ow;
            }
            return true;
        }

        World ow = Vars.world;
        Vars.world = unitWorld;
        try {
            Block sub = ConstructBlock.get(previous.size);
            tile.build.onDeconstructed(this);
            tile.build.dead = true;
            tile.setBlock(sub, team, rotation);
            ConstructBuild cons = (ConstructBuild) tile.build;
            cons.setDeconstruct(previous);
            cons.health = cons.maxHealth * prevPercent;
            registerBuilding(cons);
            arc.Events.fire(new BlockBuildBeginEvent(tile, team, this, true));
            return true;
        } finally {
            Vars.world = ow;
        }
    }

    /** 注册建筑到子世界列表 (放置/建造完成/恢复后调用), 并刷新邻近关系 (电力/传送带连接) */
    protected void registerBuilding(Building b) {
        buildings.add(b);
        buildingIds.add(b.id);
        if (b instanceof TurretBuild tb) turrets.add(tb);
        b.updateProximity();
    }

    /**
     * 子世界建造推进: Terra 自当 builder (原版 BuilderComp.updateBuild 的子世界版).
     * <p>职责:
     * <ol>
     *   <li>推进每个 ConstructBlock 脚手架的建造/拆除进度 (资源自动扣主世界核心/返还)</li>
     *   <li>清理 dead 脚手架; 建造完成时 constructFinish 在同 tile 生成新建筑 → 注册进列表</li>
     * </ol>
     * 方向判定: current == previous 为拆除 (setDeconstruct 两者相同), 否则为建造 ——
     * 对读档恢复的脚手架同样准确 (两者都参与序列化)。</p>
     * <p>必须在 Vars.world = unitWorld 上下文中调用 (constructFinish → setBlock 的多方块邻格查询)。</p>
     */
    protected void tickConstructions() {
        if (buildings.isEmpty()) return;
        Building coreBuild = team.core();
        CoreBuild core = coreBuild instanceof CoreBuild ? (CoreBuild) coreBuild : null;

        for (int i = buildings.size - 1; i >= 0; i--) {
            Building b = buildings.get(i);

            // ★ 完成/移除检测: constructFinish/deconstructFinish 内部不设置 dead 标记 ——
            //   脚手架建造完成时 tile 上的建筑被替换 (tile.build != b), 拆除完成时 tile.build 为 null;
            //   dead 标记仅在 breakSub 发起拆除时手动设置 (普通建筑换脚手架的中间态)。
            //   只检测 dead 会导致: 建造完成后脚手架残留并反复 constructFinish 重建新建筑
            //   (表现为黄色脚手架贴图+新建筑无功能), 拆除完成后红色脚手架贴图永久残留
            if (b.dead || b.tile.build != b) {
                buildings.remove(i);
                buildingIds.remove(b.id);
                if (b instanceof TurretBuild) turrets.remove((TurretBuild) b, true);

                // 建造完成的产物: 同 tile 上出现新建筑 → 注册 (可在主世界投影位置补特效反馈)
                Building nb = b.tile.build;
                if (nb != null && nb != b && !buildingIds.contains(nb.id)) {
                    registerBuilding(nb);
                    vec.set(nb.x, nb.y).sub(subCX(), subCY()).rotate(rotation - 90f).add(x, y);
                    nb.block.placeEffect.at(vec.x, vec.y, nb.block.size);
                }
                continue;
            }

            if (b instanceof ConstructBuild cons) {
                float amount = Time.delta / Math.max(cons.buildCost, 1f);
                if (cons.current == cons.previous) {
                    cons.deconstruct(this, core, amount);
                } else {
                    cons.construct(this, core, amount, cons.lastConfig);
                }
            }
        }
    }

    // ===== 鼠标悬停: 查找单位上的建筑 =====

    /**
     * 给定主世界坐标，返回该坐标在单位平台上对应的建筑（如果有的话）。
     * <p>原理：把主世界坐标逆旋转平移到子世界像素坐标，在子世界中查找该位置的 Tile.build。</p>
     *
     * @param worldX 主世界 X 像素坐标
     * @param worldY 主世界 Y 像素坐标
     * @return 该位置上的建筑，或 null
     */
    public Building buildingAt(float worldX, float worldY) {
        if (unitWorld == null) return null;
        // 主世界坐标 → 子世界像素 (逆旋转 + 平移到子世界原点), 再换算 tile
        float r = -(rotation - 90f);
        vec.set(worldX - x, worldY - y).rotate(r).add(subCX(), subCY());
        int tx = World.toTile(vec.x), ty = World.toTile(vec.y);
        if (valid(tx, ty)) {
            Tile tile = unitWorld.tile(tx, ty);
            return tile != null ? tile.build : null;
        }
        return null;
    }

    /**
     * 主世界坐标 → 子世界像素坐标 (连续映射, 供建造接管使用).
     * <p>平台可自由移动和旋转 —— 光标落在平台范围内时, 通过本映射换算到子世界网格,
     * 放置预览自动吸附到子世界的网格 (与主世界网格无关)。</p>
     *
     * @param out 输出子世界像素坐标 (复用调用方的缓冲避免分配)
     * @return false = 坐标不在子世界范围内
     */
    public boolean worldToSubPixel(float worldX, float worldY, Vec2 out) {
        if (unitWorld == null) return false;
        float r = -(rotation - 90f);
        out.set(worldX - x, worldY - y).rotate(r).add(subCX(), subCY());
        return out.x >= 0f && out.x < platW && out.y >= 0f && out.y < platH;
    }

    /**
     * 长方形碰撞箱: 与子世界平台同样大小的矩形, 随单位旋转取轴对齐包围盒.
     * <p>作用:
     * <ul>
     *   <li>敌方子弹打在整个平台范围被要塞吸收 (而非穿过平台)</li>
     *   <li>原版悬停检测覆盖全平台 —— PlacementFragment.hovered() 通过单位碰撞箱命中本单位
     *       后调用 display(), 由 {@link #display(Table)} 委托给光标下的子世界建筑</li>
     * </ul></p>
     */
    @Override
    public void hitbox(Rect rect) {
        if (platW <= 0f || platH <= 0f) {
            super.hitbox(rect);
            return;
        }
        float rad = (rotation - 90f) * Mathf.degRad;
        float cos = Math.abs(Mathf.cos(rad)), sin = Math.abs(Mathf.sin(rad));
        rect.setCentered(x, y, platW * cos + platH * sin, platW * sin + platH * cos);
    }

    /**
     * 悬停信息显示委托: 光标在子世界建筑上时, 右下角原版信息面板显示该建筑的信息.
     * <p>原版 PlacementFragment.hovered() 命中本单位后调用 display() ——
     * 这里转交给光标下的子世界建筑, 悬停体验与原世界完全一致 (血量/电力/物品条全是原版 UI)。
     * 光标在平台空白处时回落为显示单位自身信息 (原版悬停单位的行为)。</p>
     */
    @Override
    public void display(Table table) {
        if (!Vars.headless) {
            Building b = buildingAt(Core.input.mouseWorldX(), Core.input.mouseWorldY());
            if (b != null && b.displayable()) {
                b.display(table);
                return;
            }
        }
        super.display(table);
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

        // ★ 平台尺寸 (长方形碰撞箱用) + 网格偏移:
        //   Y 方向: gridOffY 增大 → 视觉向下 (与直觉相反, 已实测),
        //   故上移 2 格 = 1.5 - 2 = -0.5 格; X 方向: -0.5 格 (已对准)
        //   渲染投影 / 交互映射 / 建筑跟随全部走 subCX()/subCY(), 偏移全环节一致;
        //   实体化的大地核心在 absorb() 中额外向左移 1 格, 落在平台正中间
        //   (8x18 网格中心 tile 附近, 2x2 核心正跨中轴线)
        platW = w * Vars.tilesize;
        platH = h * Vars.tilesize;
        gridOffX = -Vars.tilesize / 2f;
        gridOffY = -Vars.tilesize / 2f;
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
     * @param ver 存档区块格式版本 (v3+ 建筑数据含精确 x/y)
     */
    public void readSubWorld(Reads read, int count, byte ver) throws IOException {
        if (unitWorld == null) createWorld();
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
            restoreMainCore();
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

        /**
         * 读档入口 (带区块长度版本, v158+ 调用路径).
         * <p>★ 关键修复: {@code readChunk} 要求本方法消费的字节数严格等于区块长度,
         * 否则主流错位 → 后续区块全部读废 → 整个存档无法加载 (EOFException)。
         * 这里先把整个区块数据全量读入内存缓冲, 再在缓冲上解析 ——
         * 无论解析结果如何, 主流消费恒等于 length: 单个区块数据损坏只损失
         * 子世界内容, 不再炸掉整个存档。</p>
         * <p>注: 编译目标 v155.4 的 CustomChunk 接口无此签名, 故不加 @Override;
         * v158 运行时 readCustomChunks 的 chunk::read 方法引用动态分派到此方法。</p>
         */
        public void read(DataInput stream, int length) throws IOException {
            byte[] data = new byte[length];
            stream.readFully(data);
            try (ByteArrayInputStream bin = new ByteArrayInputStream(data)) {
                parseChunk(new Reads(new DataInputStream(bin)));
            } catch (Exception e) {
                Log.err("zzw-world-units chunk parse failed, sub-world data skipped", e);
            }
        }

        /**
         * 读档入口 (无长度版本, v155.4 抽象方法实现 / v158 default 转发兜底).
         * <p>直接在主流上解析 (无法缓冲保护), 版本识别修复后各版本数据
         * 均能正确对齐, 消费字节数正确。</p>
         */
        @Override
        public void read(DataInput stream) throws IOException {
            parseChunk(new Reads(stream));
        }

        /**
         * 解析区块数据 (版本识别 + 按单位恢复子世界).
         * <p>★ 版本识别修复: 存档流是 InflaterInputStream 包装的,
         * {@code markSupported()} 恒为 false —— 旧版 mark/reset 检测从未生效,
         * v3 存档读档时版本字节 [03] 被当成 units int 的高字节读入
         * (= 0x03000000 个单位), 流彻底错位 → EOFException, 存档损坏。
         * 现改为无条件读首字节判断:
         * <ul>
         *   <li>2 / 3 → v2 / v3 显式版本号</li>
         *   <li>其他 (v1 旧格式首字节 = units int 最高字节, 小数量时恒 0) →
         *       手动拼回剩余 3 字节还原 units</li>
         * </ul></p>
         */
        private void parseChunk(Reads read) throws IOException {
            int first = read.b() & 0xFF;
            byte ver;
            int units;
            if (first == 3 || first == 2) {
                // v2/v3: 首字节是显式版本号
                ver = (byte) first;
                units = read.i();
            } else {
                // v1: 无版本字节, first 是 units int 的最高字节 (单位数量小时恒 0)
                ver = 0;
                int b1 = read.b() & 0xFF, b2 = read.b() & 0xFF, b3 = read.b() & 0xFF;
                units = (first << 24) | (b1 << 16) | (b2 << 8) | b3;
            }

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
                target.readSubWorld(read, count, ver);
            }
        }
    }

    /** 注册存档区块 (在 Z_Units.load() 中调用一次) */
    public static void registerSaveChunk() {
        SaveVersion.addCustomChunk("zzw-world-units", new SaveChunk());
    }
}
