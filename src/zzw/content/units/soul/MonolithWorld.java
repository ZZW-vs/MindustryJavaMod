package zzw.content.units.soul;

import arc.Events;
import arc.func.Cons;
import arc.func.Floatf;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.struct.IntSet;
import arc.struct.Seq;
import mindustry.core.World;
import mindustry.game.EventType.TileChangeEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.world.Block;
import mindustry.world.Tile;
import zzw.content.blocks.Z_Blocks;

import static mindustry.Vars.world;

/**
 * 巨石地块分块索引 (PU132 unity.world.MonolithWorld 的纯 Java 移植)。
 *
 * <p>PU132 用一套按 chunk (10x10) 分块的索引来快速查找"巨石阵营地块"，
 * 供灵魂单位 AI ({@code MonolithSoulAI}) 在找不到容器时，飞往最近的巨石地块
 * 收集地块、自我修复并最终"实体化"。</p>
 *
 * <p>PU132 原版用 {@code FactionMeta.map(block/floor) == Faction.monolith} 判定；
 * 本模组没有 Faction 系统，改为直接比对本地巨石相关的地板/墙体方块
 * (锐板岩三件套 + 对应墙体)。</p>
 *
 * <p>用法：在模组加载阶段调用一次 {@link #get()} 完成事件注册，
 * 之后世界加载会自动重建索引，方块变化会自动增量更新。</p>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public class MonolithWorld {
    /** 分块边长 (格) */
    public static final int chunkSize = 10;

    private static MonolithWorld instance;
    private static float lastPriority;
    private static Chunk lastChunk;

    private Chunk[] chunks;
    private int width;

    /** 获取单例 (首次调用时注册世界事件) */
    public static MonolithWorld get() {
        if (instance == null) instance = new MonolithWorld();
        return instance;
    }

    private MonolithWorld() {
        Events.on(WorldLoadEvent.class, e -> reload());
        Events.on(TileChangeEvent.class, e -> changed(e.tile));
    }

    /** 判断一个地块是否属于"巨石阵营"(地板或非合成固体方块)。 */
    public static boolean isMonolith(Tile tile) {
        if (tile == null) return false;
        Block b = (tile.solid() && !tile.synthetic()) ? tile.block() : tile.floor();
        return b == Z_Blocks.sharpslate
            || b == Z_Blocks.infusedSharpslate
            || b == Z_Blocks.archaicSharpslate
            || b == Z_Blocks.sharpslateWall
            || b == Z_Blocks.infusedSharpslateWall;
    }

    /** 世界加载时重建全部分块。 */
    public void reload() {
        width = Mathf.ceil(world.width() / (float) chunkSize) * chunkSize;
        int h = Mathf.ceil(world.height() / (float) chunkSize) * chunkSize;

        chunks = new Chunk[width * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < width; x++) {
                chunks[x + y * width] = new Chunk(
                    x * chunkSize, y * chunkSize,
                    Math.min(world.width() - x * chunkSize, chunkSize),
                    Math.min(world.height() - y * chunkSize, chunkSize)
                );
            }
        }

        for (Chunk chunk : chunks) chunk.updateAll();
    }

    /** 单个地块变化时增量更新所属分块。 */
    public void changed(Tile tile) {
        if (chunks == null || tile == null) return;
        Chunk chunk = getChunk(tile.x, tile.y);
        if (chunk != null) chunk.update(tile);
    }

    public Chunk getChunk(int x, int y) {
        if (chunks == null || !world.tiles.in(x, y)) return null;
        return chunks[x / chunkSize + y / chunkSize * width];
    }

    public Chunk getChunk(float x, float y) {
        return getChunk(World.toTile(x), World.toTile(y));
    }

    /** 遍历与矩形相交的所有分块。 */
    public void intersect(int x, int y, int w, int h, Cons<Chunk> cons) {
        if (chunks == null) return;
        w = Math.min(w, this.width - x);
        h = Math.min(h, (chunks.length / this.width) - y);

        int tx = Math.max(x / chunkSize, 0), ty = Math.max(y / chunkSize, 0),
            tw = Math.min(Mathf.ceil((x + w) / (float) chunkSize) * chunkSize, this.width),
            th = Math.min(Mathf.ceil((y + h) / (float) chunkSize) * chunkSize, chunks.length / this.width);

        for (int cy = ty; cy < th; cy++) {
            int pos = cy * this.width;
            for (int cx = tx; cx < tw; cx++) {
                cons.get(chunks[cx + pos]);
            }
        }
    }

    /** 查找范围内优先级最高的分块 (PU132 MonolithWorld.nearest)。 */
    public Chunk nearest(float x, float y, float range, Floatf<Chunk> priority) {
        lastChunk = null;
        lastPriority = 0f;

        int r = World.toTile(range) * 2;
        intersect(World.toTile(x), World.toTile(y), r, r, c -> {
            float p = priority.get(c);
            if (lastChunk == null || lastPriority < p) {
                lastPriority = p;
                lastChunk = c;
            }
        });

        return lastChunk;
    }

    /** 分块：维护本块内所有巨石地块。 */
    public static class Chunk {
        public int x, y, width, height;
        public float centerX, centerY;

        public Seq<Tile> monolithTiles = new Seq<>();
        public IntSet monolithTilePos = new IntSet();

        public Chunk(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean within(Position pos) {
            return within(pos.getX(), pos.getY());
        }

        public boolean within(float x, float y) {
            return x >= World.unconv(this.x) && x <= World.unconv(this.x + width)
                && y >= World.unconv(this.y) && y <= World.unconv(this.y + height);
        }

        public void addMonolithTile(Tile tile) {
            if (monolithTilePos.add(tile.pos())) monolithTiles.add(tile);
        }

        public void removeMonolithTile(Tile tile) {
            if (monolithTilePos.remove(tile.pos())) monolithTiles.remove(tile);
        }

        public void update(Tile tile) {
            if (tile == null) return;
            if (isMonolith(tile)) addMonolithTile(tile);
            else removeMonolithTile(tile);
        }

        public void updateAll() {
            centerX = World.unconv(x) + World.unconv(width) / 2f;
            centerY = World.unconv(y) + World.unconv(height) / 2f;

            monolithTiles.clear();
            monolithTilePos.clear();

            for (int ty = y; ty < y + height; ty++) {
                for (int tx = x; tx < x + width; tx++) {
                    update(world.tile(tx, ty));
                }
            }
        }
    }
}