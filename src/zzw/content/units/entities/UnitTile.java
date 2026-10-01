package zzw.content.units.entities;

import arc.func.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

/**
 * 子世界专用 Tile (PU132 unity.entities.UnitTile 移植).
 *
 * <p>与原版 Tile 的差异:
 * <ul>
 *   <li>setFloor/recache 等渲染缓存操作全部跳过 (子世界 tile 不进主世界渲染缓存)</li>
 *   <li>fireChanged 为空 (不触发主世界 tile 变更事件)</li>
 *   <li>changeBuild: 新创建的建筑调用 init 完整初始化但不加入主世界 Groups
 *       (子世界建筑由 WorldUnitEntity 手动驱动更新, 不能被主世界逻辑重复更新)</li>
 * </ul></p>
 *
 * <p>★ v155.4 崩溃修复: 原版 Tile.changeBuild 会调用
 * {@code entityprov.get().init(...)} 初始化 block/team/health 等字段;
 * PU132 原版 UnitTile 直接 {@code build = entityprov.get()} 跳过了 init ——
 * 旧版游戏 changed() 不触发 updateProximity 所以不崩, v155.4 会在
 * placeSub → setBlock → changed → updateProximity 处因 block 为 null 崩溃。
 * 现在区分两条路径:
 * <ul>
 *   <li>新建筑 (block == null, placeSub 放置): init(this, team, false, rotation) 完整初始化</li>
 *   <li>已有建筑 (吸收/读档, block 非 null): 只绑定 tile 和朝向, 保持原状态</li>
 * </ul></p>
 */
public class UnitTile extends Tile{
    public UnitTile(int x, int y){
        super(x, y);
    }

    @Override
    public void setFloor(Floor type){
        floor = type;
    }

    public void setBlockQuiet(Block block){
        this.block = block;
    }

    @Override
    protected void changeBuild(Team team, Prov<Building> entityprov, int rotation){
        // ★ 清理旧 build (原版 Tile.changeBuild 逻辑): 无论新 block 是否有 building,
        //   旧 build 都要移除 —— 拆除完成 setBlock(air) 时若不清, tile.build 会残留
        //   死建筑 (悬停查询返回幽灵建筑 / 误交互)
        if(build != null){
            build.remove();
            build = null;
        }
        if(block.hasBuilding()){
            build = entityprov.get();
            // ★ 新建筑与已存在建筑 (吸收主世界建筑 / 读档恢复) 一律走完整 init:
            //   已存在建筑 initialized=true, init 内部会跳过 create() —— 物品/液体/电力等
            //   模块状态原样保留; 但 init 一定会调用 created(), 这是关键修复点 ——
            //   图结构方块 (导热管/扭矩/温度等) 的图模块 gms 只在 created() 里重建。
            //   旧实现只绑定 tile/rotation 不调 init, 导致吸收来的方块 gms 仍停留在
            //   主世界 remove() 之后的 dead 状态 (onUpdate 直接 return):
            //   表现为贴图不拼接 + 扭矩/热量网络完全不工作; 读档恢复同理 (gms 甚至为 null)。
            //   shouldAdd=false 保证子世界建筑不进主世界 Groups (仍由单位手动驱动更新)。
            build.init(this, team, false, rotation);
        }
    }

    @Override
    protected void fireChanged(){

    }

    /** 屏蔽 tile 变更前事件: BlockIndexer/Pathfinder/FogControl/BlockRenderer/Minimap
     *  监听该事件并操作主世界缓存 —— 子世界 tile 坐标 (0~19) 会错误更新主世界
     *  对应区域, 全部屏蔽 (子世界不参与主世界的任何缓存系统) */
    @Override
    protected void firePreChanged(){

    }

    @Override
    public void recache(){

    }

    @Override
    public void recacheWall(){

    }
}
