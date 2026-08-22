package zzw.content.blocks.production;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.Item;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import zzw.content.graphics.UnityDrawf;
import zzw.content.util.GraphicUtils;

import static arc.Core.*;

/**
 * 孢子农场 (PU132 unity.world.blocks.production.SporeFarm 移植)
 * <p>在水面地板上种植孢子, 5 帧生长动画。生长完成后产出孢子荚。
 * 栅栏根据 8 邻居位掩码自动连接 (tileMap 索引)。</p>
 *
 * <p>适配说明:
 * <ul>
 *   <li>unity.graphics.UnityDrawf.tileMap → zzw.content.graphics.UnityDrawf.tileMap</li>
 *   <li>unity.util.GraphicUtils → zzw.content.util.GraphicUtils</li>
 *   <li>f.variantRegions() (方法) → f.variantRegions (字段, v155.4 Block 公开字段)</li>
 *   <li>variantRegions() (无限定符) → f.variantRegions (统一引用地板变体)</li>
 * </ul></p>
 */
public class SporeFarm extends Block{
    /** 生长帧数 */
    static final int frames = 5;
    /** 生长计时器索引 */
    int gTimer;

    /** 孢子贴图 (5 帧生长阶段) */
    public final TextureRegion[] sporeRegions = new TextureRegion[frames];
    /** 地面贴图 (5 帧生长阶段) */
    public final TextureRegion[] groundRegions = new TextureRegion[frames];
    /** 栅栏贴图 (48 张, 由 GraphicUtils.getRegions 切片) */
    public TextureRegion[] fenceRegions;
    /** 笼底贴图 */
    public TextureRegion cageFloor;

    public SporeFarm(String name){
        super(name);
        update = true;
        hasItems = true;
        gTimer = timers++;
    }

    @Override
    public void load(){
        super.load();

        for(int i = 0; i < 5; i++){
            sporeRegions[i] = atlas.find(name + "-spore" + (i + 1));
            groundRegions[i] = atlas.find(name + "-ground" + (i + 1));
        }

        // 栅栏贴图切片: 12 列 × 4 行 = 48 张
        fenceRegions = GraphicUtils.getRegions(atlas.find(name + "-fence"), 12, 4);
        cageFloor = atlas.find(name + "-floor");
    }

    public class SporeFarmBuild extends Building{
        /** 当前生长进度 (0~4) */
        float growth;
        /** 随机延迟 (-1 表示未初始化) */
        float delay = -1;
        /** 栅栏位掩码索引 (-1 表示需要更新) */
        int tileIndex = -1;
        /** 是否需要更新栅栏索引 */
        boolean needsTileUpdate;

        /** 检查附近随机位置是否为水面 */
        boolean randomChk(){
            Tile cTile = Vars.world.tile(tileX() + Mathf.range(3), tileY() + Mathf.range(3));
            return cTile != null && cTile.floor().liquidDrop == Liquids.water;
        }

        /** 计算 8 邻居位掩码 */
        void updateTilings(){
            tileIndex = 0;
            for(int i = 0; i < 8; i++){
                Tile other = tile.nearby(Geometry.d8(i));
                if(other == null || !(other.build instanceof SporeFarmBuild)) continue;
                tileIndex += 1 << i;
            }
        }

        /** 通知邻居更新栅栏索引 */
        void updateNeighbours(){
            for(int i = 0; i < 8; i++){
                Tile other = tile.nearby(Geometry.d8(i));
                if(other == null || !(other.build instanceof SporeFarmBuild b)) continue;
                b.needsTileUpdate = true;
            }
        }

        @Override
        public void onProximityRemoved(){
            super.onProximityRemoved();
            updateNeighbours();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            // 允许接收孢子荚 (相邻孢子农场整体共享输出时互相传递;
            // 本方块不消耗物品, 原版默认 acceptItem 会因 consumesItem=false 拒绝)
            return item == Items.sporePod && items.get(item) < getMaximumAccepted(item);
        }

        @Override
        public void updateTile(){
            // 首次放置时计算栅栏索引
            if(tileIndex == -1){
                updateTilings();
                updateNeighbours();
            }
            if(needsTileUpdate){
                updateTilings();
                needsTileUpdate = false;
            }
            // 生长计时
            if(timer(gTimer, (60f + delay) * 5f)){
                if(delay == -1){
                    delay = (tileX() * 89f + tileY() * 13f) % 21f;
                }else{
                    boolean chk = randomChk();
                    // 水面生长快, 非水面衰减
                    if(growth == 0f && !chk) return;
                    growth += chk ? growth > frames - 2 ? 0.1f : 0.45f : -0.1f;

                    if(growth >= frames){
                        growth = frames - 1f;
                        if(items.total() < 1) offload(Items.sporePod);
                    }
                    if(growth < 0f) growth = 0f;
                }
            }
            if(timer(timerDump, 15f)){
                // ★ 整体共享输出: 先尝试直接输出到相邻建筑;
                // 失败则把孢子荚转移给相邻孢子农场 (物品在整体内流动, 任意出口即可全部输出)
                if(!dump(Items.sporePod)){
                    for(int i = 0; i < proximity.size; i++){
                        Building other = proximity.get((i + cdump) % proximity.size);
                        if(other instanceof SporeFarmBuild b && other.team == team && b.acceptItem(this, Items.sporePod)){
                            b.handleItem(this, Items.sporePod);
                            items.remove(Items.sporePod, 1);
                            break;
                        }
                    }
                }
            }
        }

        @Override
        public void draw(){
            float rrot = (tileX() * 89f + tileY() * 13f) % 4f;
            float rrot2 = (tileX() * 69f + tileY() * 42f) % 4f;

            // 未完全生长时, 绘制地板和笼底
            if(growth < frames - 0.5f){
                Tile t = Vars.world.tileWorld(x, y);
                if(t != null && t.floor() != Blocks.air){
                    Floor f = t.floor();
                    // v155.4: variantRegions 是 Block 的公开字段 (非方法)
                    if(f.variantRegions != null && f.variantRegions.length > 0){
                        Mathf.rand.setSeed(t.pos());
                        Draw.rect(f.variantRegions[Mathf.randomSeed(t.pos(), 0, f.variantRegions.length - 1)], x, y);
                    }
                }
                Draw.rect(cageFloor, x, y);
            }

            // 绘制生长阶段贴图
            if(growth != 0f){
                Draw.rect(groundRegions[Mathf.floor(growth)], x, y, rrot * 90f);
                Draw.rect(sporeRegions[Mathf.floor(growth)], x, y, rrot2 * 90f);
            }

            // 绘制栅栏 (根据邻居位掩码选择贴图)
            Draw.rect(fenceRegions[UnityDrawf.tileMap[tileIndex]], x, y, 8f, 8f);
            drawTeamTop();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(growth);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            growth = read.f();
        }
    }
}
