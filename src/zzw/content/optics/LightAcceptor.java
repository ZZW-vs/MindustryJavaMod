package zzw.content.optics;

import arc.struct.Seq;
import mindustry.core.World;
import mindustry.gen.Building;

import static mindustry.Vars.*;

/**
 * 光接收槽 (PU132 unity.world.LightAcceptor 移植)
 * <p>表示建筑的一个受光面: 光落入槽边界内即被接收, status() = 接收强度/需求强度。</p>
 */
public class LightAcceptor {
    public final LightAcceptorType type;
    public final LightHolder hold;

    /** 接收状态数据 (原版 StemData, 这里用 float 字段) */
    public float dataFloat;
    public Seq<Light> sources = new Seq<>(2);

    public LightAcceptor(LightAcceptorType type, LightHolder hold) {
        this.type = type;
        this.hold = hold;
    }

    public float status() {
        return type.required <= 0f ? 1f : (sources.sumf(Light::endStrength) / type.required);
    }

    public boolean fulfilled() {
        return !requires() || sources.sumf(Light::endStrength) >= type.required;
    }

    public boolean requires() {
        return type.required > 0f;
    }

    /** 光的落点 tile 是否在本槽范围内 (PU132 原版算法) */
    public boolean accepts(Light light, int x, int y) {
        Building b = hold.building();
        int dx = World.toTile((x * tilesize) - (b.x - b.block.size * tilesize / 2f + tilesize / 2f)),
            dy = -World.toTile((y * tilesize) - (b.y + b.block.size * tilesize / 2f - tilesize / 2f));

        return
            dx >= type.x && dx < type.x + type.width &&
            dy >= type.y && dy < type.y + type.height;
    }

    public void add(Light light) {
        sources.add(light);
    }

    public void remove(Light light) {
        sources.remove(light, true);
    }

    public void draw() {
        type.draw.get(hold, this);
    }

    public void update() {
        type.update.get(hold, this);
    }
}